# Agentis Memory

**Working memory for AI agents. Redis protocol. Single binary.**

Agentis Memory is an in-memory service that gives AI agents fast key-value cache and semantic vector search in one process. Any Redis client connects out of the box.

<div class="grid cards" markdown>

- :material-flash: **Redis Protocol**

    Any Redis client works — redis-cli, Jedis, redis-py, ioredis, go-redis. No SDKs, no REST wrappers.

- :material-brain: **Semantic Search**

    `MEMSAVE` chunks text, embeds it, and indexes in an HNSW graph. `MEMQUERY` finds results by meaning, not keywords.

- :material-chip: **Built-in Embeddings**

    all-MiniLM-L6-v2 runs locally via ONNX Runtime. No API keys, no network calls, no latency spikes.

- :material-package-variant-closed: **Single Binary**

    GraalVM native-image. Download, run, done. ~150MB including the embedding model. Zero dependencies.

</div>

## Quick example

```bash
# Save a fact to memory
redis-cli -p 6399 MEMSAVE "agent:fact:stack" "Project uses Java 26 with GraalVM"

# Search by meaning
redis-cli -p 6399 MEMQUERY agent "what language does the project use" 5
# → 1) 1) "agent:fact:stack"
#      2) "Project uses Java 26 with GraalVM"
#      3) "0.91"

# Standard Redis commands work too
redis-cli -p 6399 SET session:id "abc-123"
redis-cli -p 6399 HSET config model gpt-4
```

## Why not just Redis?

| | Agentis Memory | Mem0 | Zep | Redis + Vector |
|---|---|---|---|---|
| Protocol | RESP (Redis) | REST | REST | RESP |
| Semantic search | Built-in | Built-in | Built-in | Requires modules |
| Embedding | Bundled (ONNX) | External API | External API | External API |
| Deployment | Single binary | Docker + deps | Docker + Postgres | Redis Stack |
| Client libraries | Any Redis client | Custom SDK | Custom SDK | Any Redis client |

Every existing agent memory solution wraps a REST API or requires a custom SDK. Agentis Memory speaks the Redis wire protocol — the most widely supported database protocol in existence.

## Next steps

- [Getting Started](getting-started.md) — run your first instance in 60 seconds
- [Memory Commands](commands/memory.md) — `MEMSAVE`, `MEMQUERY`, `MEMSTATUS`, `MEMDEL`
- [Architecture](architecture.md) — how it works under the hood
- [Integrations](integrations/redis-py.md) — Python, TypeScript, Vercel AI SDK, LangChain, Claude Code
