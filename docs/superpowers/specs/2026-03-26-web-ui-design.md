# Agentis Memory Web UI — Design Spec

## Статус: Future Work

Реализовать после MVP (шаги 2-7). Отдельный lightweight web UI для визуализации векторной памяти.

## Почему не Redis Insight плагин

Redis Insight v2 убрал plugin API (был в v1, deprecated). Форк Redis Insight — SSPL лицензия, тяжёлый стек (React + NestJS + Electron). Не стоит того для одной панели.

## Идея

Встроенный web UI прямо в Agentis Memory — отдельный HTTP порт (`:8080` по дефолту). Как Dragonfly Dashboard или Redis Insight, но наш, лёгкий, заточен под векторную память.

Включается флагом `--ui-enabled` (выключен по дефолту). Не добавляет зависимостей в runtime если выключен.

## Архитектура

```
┌──────────────────────────────────────┐
│         Agentis Memory               │
│                                      │
│  :6399  RESP (Redis protocol)        │  ← агенты, redis-cli, Redis Insight
│  :8080  HTTP (Web UI)                │  ← браузер
│                                      │
│  Оба порта обслуживаются одним       │
│  процессом, общий доступ к KvStore,  │
│  VectorEngine, HnswIndex             │
└──────────────────────────────────────┘
```

HTTP сервер — минимальный. Варианты:
- **Javalin** (~1MB, встраивается, поддерживает SPA static serving + REST API)
- **Нативный `com.sun.net.httpserver`** — zero deps, но примитивный
- **Vert.x Web** — если к тому моменту перейдём на Vert.x transport

Frontend — SPA, static файлы bundled в jar. React/Svelte/vanilla — решим позже.

## Страницы / Фичи

### 1. Dashboard (главная)

- Количество ключей (total, по типам: string, hash, list, set, zset)
- Количество indexed чанков, pending, error
- Количество namespace-ов
- Memory usage (KV store, HNSW index, ONNX runtime)
- Uptime, ops/sec, connections
- Данные получаем из тех же структур что INFO command

### 2. Namespaces Browser

- Список всех namespace-ов (extracted from key prefixes)
- Для каждого: количество ключей, чанков, last updated
- Клик → просмотр ключей внутри namespace
- Для каждого ключа: значение (raw), статус индексации, количество чанков

### 3. Semantic Search Playground

- Текстовое поле для запроса
- Dropdown для выбора namespace (или ALL)
- Slider для K (1-100)
- Результаты: ключ, текст чанка, score (визуализация в виде прогресс-бара)
- Highlight совпадающих слов в тексте чанка (fuzzy)

### 4. Embedding Explorer (killer-фича)

2D/3D проекция всех эмбеддингов через dimensionality reduction.

**Как работает:**
1. Backend: HNSW индекс уже содержит все вектора (384 dim)
2. Backend: при запросе `/api/embeddings/projection` запускает UMAP или t-SNE на всех векторах, кеширует результат
3. Frontend: рендерит 2D scatter plot (Plotly.js или D3.js)
4. Точки цветом кодируют namespace
5. Hover → показывает текст чанка и parent key
6. Клик → подсвечивает все чанки одного parent key
7. Поле поиска → embed запрос, показать его позицию на карте + подсветить top-K ближайших

**Dimensionality reduction:**
- UMAP предпочтительнее t-SNE (быстрее, лучше сохраняет глобальную структуру)
- Java библиотека: `com.tagbio.umap:umap-java` (Apache 2.0)
- Кешировать проекцию, пересчитывать при значительных изменениях индекса (>10% новых чанков)
- Для больших корпусов (>10K чанков) — субсэмплинг

### 5. Key Inspector

- Просмотр любого ключа: тип, значение, TTL, размер
- Для MEMSAVE-ключей: оригинальный текст + список чанков с их векторами
- Для чанков: показать ближайших соседей в HNSW (nearest neighbors graph)
- Мини-визуализация вектора (heatmap 384 значений)

### 6. Operations Log

- Последние N команд (ring buffer в памяти)
- Фильтр по типу: read/write/mem/admin
- Фильтр по namespace
- Latency для каждой команды

## REST API

Все данные UI получает через REST API. Этот же API можно использовать программно.

```
GET  /api/stats                         → dashboard metrics
GET  /api/namespaces                    → list namespaces with counts
GET  /api/keys?namespace=X&cursor=0     → paginated key list
GET  /api/key/:key                      → key details + chunks
POST /api/search                        → semantic search (body: {namespace, query, k})
GET  /api/embeddings/projection         → 2D UMAP projection of all embeddings
GET  /api/embeddings/neighbors/:key     → nearest neighbors for key's chunks
GET  /api/ops/log?limit=100             → recent operations
GET  /api/health                        → health check
```

## Конфигурация

```bash
./agentis-memory --ui-enabled --ui-port 8080

# Или в конфиге
ui-enabled true
ui-port 8080
```

| Параметр | Дефолт | Описание |
|---|---|---|
| `--ui-enabled` | `false` | Включить web UI |
| `--ui-port` | `8080` | HTTP порт |
| `--ui-bind` | `127.0.0.1` | Bind address для UI |

## Docker Compose интеграция

```yaml
agentis-memory:
  ...
  ports:
    - "6399:6399"   # RESP
    - "8080:8080"   # Web UI
  command: ["bin/agentis-memory", "--port", "6399", "--bind", "0.0.0.0",
            "--ui-enabled", "--ui-port", "8080", "--ui-bind", "0.0.0.0"]
```

Заменяет необходимость в Redis Insight для наших кастомных команд. Стандартные Redis-команды по-прежнему видны через Redis Insight.

## Технические решения (отложены)

- Какой HTTP-сервер: Javalin vs com.sun.net.httpserver vs Vert.x
- Какой frontend фреймворк: React vs Svelte vs vanilla + htmx
- UMAP: реализация на Java vs вызов Python subprocess
- Аутентификация UI: привязка к `--requirepass` или отдельный механизм
- SSE/WebSocket для live обновлений dashboard

## Зависимости (оценка)

- HTTP сервер: ~1MB (Javalin) или 0 (com.sun.net.httpserver)
- UMAP Java: ~200KB
- Frontend bundle: ~500KB-2MB (зависит от фреймворка и графиков)
- Plotly.js (для embedding explorer): ~3MB (можно подгружать с CDN)
