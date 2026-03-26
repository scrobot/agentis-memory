# Agentis Memory Benchmark Module — Design Spec

## Overview

Отдельный Gradle-модуль `benchmark/` для сравнения производительности Agentis Memory с Redis. Один и тот же набор тестов прогоняется на обоих серверах, результаты выводятся side-by-side.

## Принцип

Бенчмарк не должен знать к чему он подключён. Он подключается по RESP-протоколу к `host:port` и гоняет команды. Два прогона — один на Agentis Memory, один на Redis — одинаковые сценарии, одинаковые данные. Сравниваем.

## Tech Stack

- Java 26
- JMH (Java Microbenchmark Harness) — для микробенчмарков
- Jedis — Redis/RESP клиент
- Testcontainers — поднять Redis в Docker автоматически
- Gradle модуль: `benchmark/`

## Архитектура

```
benchmark/
├── build.gradle.kts
├── src/main/java/io/agentis/memory/benchmark/
│   ├── BenchmarkRunner.java          # main(), CLI, оркестрация
│   ├── BenchmarkConfig.java          # конфиг: host, port, iterations, warmup
│   ├── BenchmarkResult.java          # результаты: ops/sec, latency p50/p95/p99
│   ├── BenchmarkReport.java          # форматирование side-by-side отчёта
│   │
│   ├── scenarios/                    # сценарии бенчмарков
│   │   ├── BenchmarkScenario.java    # интерфейс: setup(), run(), teardown(), name()
│   │   ├── StringsScenario.java      # SET/GET throughput
│   │   ├── HashScenario.java         # HSET/HGET/HGETALL
│   │   ├── ListScenario.java         # LPUSH/RPUSH/LRANGE
│   │   ├── SortedSetScenario.java    # ZADD/ZRANGE/ZRANGEBYSCORE
│   │   ├── SetScenario.java          # SADD/SMEMBERS/SINTER
│   │   ├── MixedWorkloadScenario.java # реалистичный микс операций
│   │   ├── PipelineScenario.java     # пайплайнинг N команд за раз
│   │   └── MemoryCommandsScenario.java # MEMSAVE/MEMQUERY (только Agentis)
│   │
│   └── infra/                        # инфраструктура
│       ├── ServerTarget.java         # enum: AGENTIS, REDIS
│       ├── ServerManager.java        # запуск/остановка серверов (Testcontainers)
│       └── LatencyRecorder.java      # HDR Histogram для записи latency
```

## Сценарии

### 1. StringsScenario — SET/GET throughput

```
Setup:  нет
Run:    N итераций:
        - SET random_key random_value (100 байт)
        - GET random_key
        - SET random_key random_value (1KB)
        - GET random_key
Metric: ops/sec для SET и GET отдельно, latency p50/p95/p99
```

### 2. HashScenario — HSET/HGET/HGETALL

```
Setup:  создать 1000 хешей по 10 полей каждый
Run:    N итераций:
        - HSET random_hash random_field random_value
        - HGET random_hash random_field
        - HGETALL random_hash
        - HDEL random_hash random_field
Metric: ops/sec per command type, latency
```

### 3. ListScenario — LPUSH/RPUSH/LRANGE

```
Setup:  создать 100 списков по 100 элементов
Run:    N итераций:
        - LPUSH random_list random_value
        - RPUSH random_list random_value
        - LRANGE random_list 0 9 (первые 10)
        - LPOP random_list
Metric: ops/sec, latency
```

### 4. SortedSetScenario — ZADD/ZRANGE/ZRANGEBYSCORE

```
Setup:  создать 100 sorted sets по 1000 членов, рандомные score 0-10000
Run:    N итераций:
        - ZADD random_zset random_score random_member
        - ZRANGE random_zset 0 9 WITHSCORES
        - ZRANGEBYSCORE random_zset min max LIMIT 0 10
        - ZCARD random_zset
        - ZSCORE random_zset random_member
Metric: ops/sec, latency
```

### 5. SetScenario — SADD/SMEMBERS/SINTER

```
Setup:  создать 100 sets по 100 членов
Run:    N итераций:
        - SADD random_set random_member
        - SISMEMBER random_set random_member
        - SMEMBERS random_set (для маленьких)
        - SINTER set1 set2
Metric: ops/sec, latency
```

### 6. MixedWorkloadScenario — реалистичный микс

Эмулирует реальную нагрузку агента:

```
Setup:  предзаполнить 10000 string ключей, 100 хешей, 50 списков
Run:    N итераций, каждая случайно выбирает операцию по весам:
        - 40% GET (string)
        - 20% SET (string)
        - 15% HGET/HSET
        - 10% LPUSH/LRANGE
        - 10% ZADD/ZRANGE
        - 5%  DEL/EXISTS/TTL
Metric: aggregate ops/sec, latency per operation type, overall p50/p95/p99
```

### 7. PipelineScenario — пайплайнинг

```
Setup:  нет
Run:    Отправить N команд SET в одном pipeline (без ожидания ответа на каждую)
        Batch sizes: 10, 50, 100, 500, 1000
Metric: ops/sec при разных batch sizes, сравнение с non-pipelined
```

### 8. MemoryCommandsScenario — MEMSAVE/MEMQUERY (только Agentis)

Не сравнивается с Redis (у Redis нет таких команд). Отдельная секция отчёта.

```
Setup:  подготовить 100 текстов разной длины (100, 500, 2000, 10000 символов)
Run:
        - MEMSAVE key short_text (100 chars) — замерить время ответа (sync) + время индексации (через MEMSTATUS polling)
        - MEMSAVE key medium_text (2000 chars)
        - MEMSAVE key long_text (10000 chars)
        - MEMQUERY namespace "search query" 10 — после индексации 1000 записей
        - MEMQUERY ALL "search query" 10 — cross-namespace
Metric:
        - MEMSAVE response latency (sync part)
        - Indexation time (async, от OK до indexed)
        - MEMQUERY latency vs corpus size (100, 500, 1000, 5000 записей)
        - MEMQUERY recall — сравнить результаты с brute-force cosine search
```

## Параметры запуска

```bash
# Полный бенчмарк — автоматически поднимает Redis через Testcontainers
./gradlew :benchmark:run

# Только конкретный сценарий
./gradlew :benchmark:run --args="--scenario strings"

# Против внешних серверов (без Testcontainers)
./gradlew :benchmark:run --args="--agentis-host localhost --agentis-port 6399 --redis-host localhost --redis-port 6379"

# Настройки
./gradlew :benchmark:run --args="--warmup 1000 --iterations 100000 --threads 1"
./gradlew :benchmark:run --args="--threads 4"  # multi-client concurrency
```

**CLI параметры:**

| Параметр | Дефолт | Описание |
|---|---|---|
| `--agentis-host` | `localhost` | Agentis Memory host |
| `--agentis-port` | `6399` | Agentis Memory port |
| `--redis-host` | (auto) | Redis host. Если не задан, поднимается через Testcontainers |
| `--redis-port` | (auto) | Redis port |
| `--scenario` | `all` | Конкретный сценарий: `strings`, `hash`, `list`, `zset`, `set`, `mixed`, `pipeline`, `memory` |
| `--warmup` | `5000` | Количество warmup итераций (не учитываются в метриках) |
| `--iterations` | `100000` | Количество измеряемых итераций |
| `--threads` | `1` | Количество клиентских потоков |
| `--value-size` | `100` | Размер значений в байтах для string-тестов |
| `--output` | `console` | Формат вывода: `console`, `json`, `csv` |

## Формат отчёта

### Console output (по умолчанию)

```
╔══════════════════════════════════════════════════════════════════════╗
║                    Agentis Memory Benchmark v0.1                    ║
║         Agentis Memory 0.1.0 vs Redis 7.4.2                        ║
║         Threads: 1 | Iterations: 100,000 | Value size: 100B        ║
╠══════════════════════════════════════════════════════════════════════╣
║                                                                      ║
║  STRINGS (SET/GET)                                                   ║
║  ┌──────────┬───────────────┬───────────────┬───────────┐            ║
║  │ Command  │ Agentis (ops) │ Redis (ops/s) │ Ratio     │            ║
║  ├──────────┼───────────────┼───────────────┼───────────┤            ║
║  │ SET      │    85,432     │   112,345     │  0.76x    │            ║
║  │ GET      │    92,100     │   125,000     │  0.74x    │            ║
║  └──────────┴───────────────┴───────────────┴───────────┘            ║
║                                                                      ║
║  Latency (ms)                                                        ║
║  ┌──────────┬────────┬────────┬────────┬────────┬────────┬────────┐  ║
║  │ Command  │  p50 A │  p50 R │  p95 A │  p95 R │  p99 A │  p99 R│  ║
║  ├──────────┼────────┼────────┼────────┼────────┼────────┼────────┤  ║
║  │ SET      │  0.011 │  0.008 │  0.025 │  0.018 │  0.052 │  0.035│  ║
║  │ GET      │  0.010 │  0.007 │  0.022 │  0.015 │  0.048 │  0.030│  ║
║  └──────────┴────────┴────────┴────────┴────────┴────────┴────────┘  ║
║                                                                      ║
║  HASHES (HSET/HGET/HGETALL)                                         ║
║  ... (same format)                                                   ║
║                                                                      ║
║  AGENTIS-ONLY: MEMSAVE/MEMQUERY                                     ║
║  ┌─────────────────────────────┬────────────────┐                    ║
║  │ Operation                   │ Latency        │                    ║
║  ├─────────────────────────────┼────────────────┤                    ║
║  │ MEMSAVE (100 chars, sync)   │  0.015ms       │                    ║
║  │ MEMSAVE (100 chars, index)  │  12ms          │                    ║
║  │ MEMSAVE (10K chars, sync)   │  0.018ms       │                    ║
║  │ MEMSAVE (10K chars, index)  │  185ms         │                    ║
║  │ MEMQUERY (1K corpus, top-10)│  8ms           │                    ║
║  │ MEMQUERY (5K corpus, top-10)│  15ms          │                    ║
║  │ MEMQUERY recall@10          │  94.2%         │                    ║
║  └─────────────────────────────┴────────────────┘                    ║
║                                                                      ║
║  SUMMARY                                                             ║
║  Average throughput ratio: 0.75x (Agentis vs Redis)                  ║
║  Average latency overhead: +35% p50, +40% p95                        ║
║                                                                      ║
╚══════════════════════════════════════════════════════════════════════╝
```

### JSON output (`--output json`)

```json
{
  "metadata": {
    "agentis_version": "0.1.0",
    "redis_version": "7.4.2",
    "threads": 1,
    "iterations": 100000,
    "value_size_bytes": 100,
    "timestamp": "2026-03-26T16:00:00Z",
    "os": "Linux 6.1",
    "java_version": "26",
    "cpu": "Apple M3 Pro",
    "memory_gb": 36
  },
  "scenarios": {
    "strings": {
      "SET": {
        "agentis": { "ops_per_sec": 85432, "p50_ms": 0.011, "p95_ms": 0.025, "p99_ms": 0.052 },
        "redis":   { "ops_per_sec": 112345, "p50_ms": 0.008, "p95_ms": 0.018, "p99_ms": 0.035 },
        "ratio": 0.76
      },
      "GET": { ... }
    },
    "memory_commands": {
      "MEMSAVE_100_sync":  { "p50_ms": 0.015, "p95_ms": 0.022 },
      "MEMSAVE_100_index": { "p50_ms": 12, "p95_ms": 18 },
      "MEMQUERY_1k_top10": { "p50_ms": 8, "p95_ms": 14 },
      "recall_at_10":      0.942
    }
  },
  "summary": {
    "avg_throughput_ratio": 0.75,
    "avg_p50_overhead_pct": 35,
    "avg_p95_overhead_pct": 40
  }
}
```

## Latency Recording

Используем HdrHistogram (High Dynamic Range Histogram):
- Точность: 3 significant digits
- Диапазон: 1 microsecond — 10 seconds
- Каждая операция записывается в наносекундах через `System.nanoTime()`
- После прогона: extract p50, p95, p99, p999, max, mean

Зависимость: `org.hdrhistogram:HdrHistogram:2.2.2`

## Recall Measurement (для MEMQUERY)

Для оценки качества векторного поиска:

1. Загрузить N текстов через MEMSAVE, дождаться индексации
2. Для каждого тестового запроса:
   - Получить top-K через MEMQUERY
   - Получить ground truth: embed запрос через Embedder, brute-force cosine similarity по всем чанкам, взять top-K
   - Recall@K = |intersection(MEMQUERY_results, ground_truth)| / K
3. Средний Recall@K по всем запросам

## Инфраструктура запуска

### Testcontainers

```java
// ServerManager.java
GenericContainer<?> redis = new GenericContainer<>("redis:7.4")
    .withExposedPorts(6379);
redis.start();
int redisPort = redis.getMappedPort(6379);
```

Agentis Memory запускается отдельно (предполагается что уже запущен на `--agentis-port`). Или можно тоже в контейнере если есть Docker image.

### Warmup

Перед каждым сценарием — warmup фаза:
- Прогнать `--warmup` итераций без записи метрик
- Цель: прогреть JIT (для Agentis), TCP connection pool, OS page cache

### Cleanup

После каждого сценария — `FLUSHDB` на обоих серверах.

## Gradle модуль

```kotlin
// benchmark/build.gradle.kts
plugins {
    java
    application
}

application {
    mainClass.set("io.agentis.memory.benchmark.BenchmarkRunner")
}

dependencies {
    implementation("redis.clients:jedis:5.2.0")
    implementation("org.hdrhistogram:HdrHistogram:2.2.2")
    implementation("org.testcontainers:testcontainers:1.20.4")

    // Для recall measurement — нужен Embedder из основного модуля
    implementation(project(":"))
}
```

## Критерий готовности

1. `./gradlew :benchmark:run` автоматически поднимает Redis, прогоняет все сценарии, выводит side-by-side отчёт
2. JSON output содержит все метрики, воспроизводим
3. MEMQUERY recall@10 >= 90%
4. Никаких ошибок или unknown commands при прогоне (все используемые команды реализованы)
