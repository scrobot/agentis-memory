# Agentis Memory — Launch Plan

## 1. Claude Code Plugin

Плагин для Claude Code, позволяющий использовать Agentis Memory как рабочую память прямо из сессии.

**Структура:**
```
claude-plugin/
├── plugin.json              # manifest: name, description, commands, hooks
├── commands/
│   ├── mem-save.md          # /mem-save <key> <text> — сохранить в память
│   ├── mem-query.md         # /mem-query <query> — семантический поиск
│   ├── mem-status.md        # /mem-status — показать статистику
│   └── mem-forget.md        # /mem-forget <key> — удалить из памяти
├── hooks/
│   └── session-start.sh     # SessionStart hook: проверить что сервер доступен
├── skills/
│   └── memory-aware.md      # Skill: инструкции как агенту работать с памятью
└── CLAUDE.md                # Автоматические инструкции при подключении плагина
```

**Как работает:**
- Плагин подключается через `.claude/plugins/` или git URL
- `CLAUDE.md` в плагине объясняет агенту что есть Agentis Memory на `localhost:6399`
- Skill `memory-aware` учит агента: когда сохранять факты, как формировать ключи с namespace, когда искать
- Commands дают пользователю slash-команды: `/mem-save`, `/mem-query`
- Hook `session-start` проверяет доступность сервера через PING

**Коммуникация с сервером:**
- Через `redis-cli` (если установлен) или raw TCP в bash:
  ```bash
  exec 3<>/dev/tcp/localhost/6399
  printf '*3\r\n$7\r\nMEMSAVE\r\n...' >&3
  read -t 2 response <&3
  ```

## 2. Universal Agent Prompt (для любого LLM)

Документ-промпт который можно включить в system prompt любого агента — Claude API, OpenAI, Vercel AI SDK, LangChain.

**Файл:** `docs/agent-prompt.md`

**Содержание:**
```markdown
# Working Memory Instructions

You have access to a semantic working memory service at {AGENTIS_HOST}:{AGENTIS_PORT}.
It speaks Redis protocol. Use any Redis client library to interact with it.

## Commands

### Save to memory
MEMSAVE <namespace>:<key> "<text>"
- namespace: your agent ID or "shared" for cross-agent memory
- Returns: OK (indexing happens in background)

### Search by meaning
MEMQUERY <namespace> "<natural language query>" <count>
- namespace: your agent ID, or ALL for cross-agent search
- Returns: [[key, text, score], ...] sorted by relevance

### Check status
MEMSTATUS <key>
- Returns: [status, chunk_count, dimensions, timestamp]

### Delete
MEMDEL <key>

## When to save
- New facts learned during conversation
- User preferences and context
- Task outcomes and observations
- Errors encountered and resolutions

## When to search
- Before answering questions that may relate to prior knowledge
- When user references past conversations
- When starting a new task that may have prior context

## Key naming convention
- {agent_id}:fact:{topic} — learned facts
- {agent_id}:obs:{topic} — observations
- {agent_id}:pref:{topic} — user preferences
- shared:policy:{topic} — cross-agent policies
```

**Для Vercel AI SDK:**
```typescript
import { createClient } from 'redis';

const memory = createClient({ url: 'redis://localhost:6399' });

// В system prompt добавить agent-prompt.md
// В tools добавить:
tools: {
  memSave: { execute: (key, text) => memory.sendCommand(['MEMSAVE', key, text]) },
  memQuery: { execute: (ns, query, k) => memory.sendCommand(['MEMQUERY', ns, query, k]) },
}
```

## 3. README.md

**Секции:**
- Hero: одно предложение + animated GIF/screenshot
- What is it: working memory for AI agents, Redis protocol, single binary
- Quick Start: `docker run`, `redis-cli PING`, `MEMSAVE`, `MEMQUERY`
- Features: KV cache + semantic search, namespaces, RESP protocol, native binary
- Architecture: ASCII diagram
- Commands: таблица supported/unsupported
- Configuration: key parameters
- Benchmarks: throughput comparison vs Redis/Dragonfly/Lux (когда будут результаты)
- Integrations: Claude Code plugin, Vercel AI SDK, LangChain, redis-py
- Building from source
- Contributing: CONTRIBUTING.md, code of conduct, issue templates
- License: Apache 2.0 (или MIT — решить)
- Roadmap: Web UI, modular transport, shared memory protocol

## 4. Documentation Site (GitHub Pages)

**Инструмент:** MkDocs + Material theme (Python, стандарт для OSS docs)

```
docs-site/
├── mkdocs.yml
├── docs/
│   ├── index.md              # landing page
│   ├── getting-started.md    # quick start guide
│   ├── configuration.md      # all parameters
│   ├── commands/
│   │   ├── index.md          # command reference overview
│   │   ├── strings.md
│   │   ├── hashes.md
│   │   ├── lists.md
│   │   ├── sets.md
│   │   ├── sorted-sets.md
│   │   ├── memory.md         # MEMSAVE/MEMQUERY/MEMDEL/MEMSTATUS
│   │   └── server.md
│   ├── integrations/
│   │   ├── claude-code.md
│   │   ├── vercel-ai-sdk.md
│   │   ├── langchain.md
│   │   └── redis-py.md
│   ├── architecture.md       # design overview
│   ├── benchmarks.md         # performance comparison
│   ├── deployment.md         # Docker, native binary, production
│   └── contributing.md
```

**Публикация:** GitHub Actions → `mkdocs gh-deploy` → GitHub Pages

## 5. CI/CD + Docker Hub + Production Compose

### GitHub Actions

**`.github/workflows/ci.yml`:**
```yaml
name: CI
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: graalvm/setup-graalvm@v1
        with:
          java-version: '25'
          distribution: 'graalvm-community'
      - run: ./gradlew test
      - run: ./gradlew integrationTest
```

**`.github/workflows/release.yml`:**
```yaml
name: Release
on:
  push:
    tags: ['v*']
jobs:
  build-and-push:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}
      - uses: docker/build-push-action@v5
        with:
          push: true
          tags: |
            agentismemory/agentis-memory:latest
            agentismemory/agentis-memory:${{ github.ref_name }}
          platforms: linux/amd64,linux/arm64

  docs:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: pip install mkdocs-material
      - run: mkdocs gh-deploy --force
```

### Docker Hub

- Org: `agentismemory` (или `agentis`)
- Image: `agentismemory/agentis-memory:latest`, `agentismemory/agentis-memory:v0.1.0`
- Multi-arch: `linux/amd64` + `linux/arm64`
- Description синхронизируется из README

### Production Docker Compose

**`docker-compose.production.yml`:**
```yaml
services:
  agentis-memory:
    image: agentismemory/agentis-memory:latest  # from Docker Hub, not local build
    container_name: agentis-memory
    ports:
      - "6399:6399"
    volumes:
      - agentis-data:/data
    environment:
      - AGENTIS_OPTS=--requirepass ${AGENTIS_PASSWORD:-}
    healthcheck:
      test: ["CMD-SHELL", "exec 3<>/dev/tcp/localhost/6399 && printf '*1\\r\\n$$4\\r\\nPING\\r\\n' >&3 && read -t 2 line <&3 && [[ \"$$line\" == *PONG* ]]"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 1G

  redis-insight:
    image: redis/redisinsight:2.66
    container_name: redis-insight
    ports:
      - "5540:5540"
    depends_on:
      agentis-memory:
        condition: service_healthy
    restart: unless-stopped

volumes:
  agentis-data:
```

Использование:
```bash
# Production — тянет готовый образ с Docker Hub
AGENTIS_PASSWORD=mysecret docker compose -f docker-compose.production.yml up -d

# Development — билдит локально
docker compose up -d
```

## Порядок выполнения

```
1. Claude Plugin     ──┐
2. Agent Prompt      ──┤ можно параллельно
3. README            ──┤
                       │
4. Docs Site         ──┘ после README (переиспользует контент)
5. CI/CD + Docker Hub   после всего (нужен рабочий тест suite)
```

## Лицензия

Предлагаю **Apache 2.0** — совместим с enterprise, разрешает модификации, требует attribution. Стандарт для инфраструктурных проектов (Redis OSS тоже был на BSD, Dragonfly на BSL, jvector на Apache 2.0).
