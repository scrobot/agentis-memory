# Agentis Memory

**Working memory for AI agents. Redis protocol. Single binary.**

Agentis Memory is an in-memory service that gives AI agents fast key-value cache and semantic vector search in one process. Any Redis client connects out of the box — redis-cli, Jedis, redis-py, ioredis, go-redis. No SDKs, no REST wrappers, no dependencies.

```
redis-cli -p 6399 MEMSAVE "agent:fact:stack" "Project uses Java 26 with GraalVM"
redis-cli -p 6399 MEMQUERY agent "what language" 5
```

## Quick Start

### Docker (recommended)

```bash
docker compose up -d
```

Verify:

```bash
redis-cli -p 6399 PING
# → PONG
```

Save something to memory:

```bash
redis-cli -p 6399 MEMSAVE "myagent:fact:stack" "We use Python 3.12 with FastAPI"
```

Search by meaning:

```bash
redis-cli -p 6399 MEMQUERY myagent "what web framework do we use" 5
# → 1) 1) "myagent:fact:stack"
#      2) "We use Python 3.12 with FastAPI"
#      3) "0.89"
```

All standard Redis commands work too:

```bash
redis-cli -p 6399 SET session:id "abc-123"
redis-cli -p 6399 HSET config model gpt-4 temperature 0.7
redis-cli -p 6399 DBSIZE
```

### Native binary

```bash
./agentis-memory --port 6399 --data-dir ./data
```

## What Makes It Different

Every existing agent memory solution (Mem0, Zep, Letta, Redis Agent Memory Server) wraps a REST API or requires a custom SDK. Agentis Memory speaks **Redis wire protocol** — the most widely supported database protocol in existence. Your agents already know how to talk to it.

| | Agentis Memory | Mem0 | Zep | Redis + Vector |
|---|---|---|---|---|
| Protocol | RESP (Redis) | REST | REST | RESP |
| Semantic search | Built-in | Built-in | Built-in | Requires modules |
| Embedding | Bundled (ONNX) | External API | External API | External API |
| Deployment | Single binary | Docker + deps | Docker + Postgres | Redis Stack |
| Client libraries | Any Redis client | Custom SDK | Custom SDK | Any Redis client |

## Features

- **Redis-compatible** — 90+ commands: strings, hashes, lists, sets, sorted sets, TTL, SCAN, pub/sub basics
- **Semantic memory** — `MEMSAVE`/`MEMQUERY`: text is chunked, embedded (384-dim), and indexed in HNSW graph. Search returns results ranked by cosine similarity
- **Built-in embeddings** — all-MiniLM-L6-v2 runs locally via ONNX Runtime. No API keys, no network calls, no latency spikes
- **Namespaces** — isolate agents by key prefix (`agent1:`, `agent2:`, `shared:`). Query one namespace or `ALL`
- **Persistence** — AOF (append-only file) + periodic snapshots. Survives restarts with full recovery including re-embedding
- **Single binary** — GraalVM native-image. Download, run, done. ~150MB including the embedding model
- **SIMD-accelerated** — Java Vector API for cosine similarity. Fast on modern CPUs
- **Redis Insight compatible** — connect Redis Insight to `localhost:6399` for a GUI

## Architecture

```
+----------------------------------------------+
|            RESP Protocol Layer               |
|     TCP :6399 — any Redis client connects    |
+----------------------------------------------+
|            Command Router (90+ cmds)         |
|   SET/GET/DEL/HSET/...  →  KV Store         |
|   MEMSAVE/MEMQUERY/...  →  Vector Engine     |
+------------------------+---------------------+
|      KV Store          |   Vector Engine     |
|                        |                     |
|  ConcurrentHashMap     |  Sentence chunker   |
|  TTL & expiry          |  ONNX embedding     |
|  LRU eviction          |  (all-MiniLM-L6-v2) |
|  AOF + snapshots       |  HNSW index         |
|                        |  (jvector + SIMD)   |
+------------------------+---------------------+
```

`MEMSAVE` is async: the KV write is synchronous (`+OK` returns immediately), chunking + embedding + HNSW indexation runs in the background. Typical latency: 5–10ms per chunk.

## Commands

### Memory commands

| Command | Description |
|---|---|
| `MEMSAVE key text` | Save text — chunks, embeds, and indexes for semantic search |
| `MEMQUERY namespace query count` | Search by meaning — returns `[key, text, score]` ranked by similarity |
| `MEMSTATUS key` | Check indexing status: `indexed`, `pending`, or `error` |
| `MEMDEL key` | Delete from KV store and vector index |

### Standard Redis commands

| Category | Commands |
|---|---|
| **Strings** | `SET` `GET` `DEL` `EXISTS` `APPEND` `INCR` `MGET` `MSET` `SETNX` `GETSET` `GETDEL` `GETEX` `STRLEN` `RENAME` `UNLINK` `RANDOMKEY` |
| **Hashes** | `HSET` `HGET` `HDEL` `HGETALL` `HEXISTS` `HKEYS` `HVALS` `HLEN` `HMGET` `HSETNX` `HINCRBY` `HINCRBYFLOAT` `HSCAN` |
| **Lists** | `LPUSH` `RPUSH` `LPOP` `RPOP` `LRANGE` `LINDEX` `LLEN` `LSET` `LREM` `LINSERT` `LTRIM` |
| **Sets** | `SADD` `SREM` `SMEMBERS` `SISMEMBER` `SCARD` `SPOP` `SRANDMEMBER` `SDIFF` `SINTER` `SUNION` `SSCAN` |
| **Sorted sets** | `ZADD` `ZREM` `ZRANGE` `ZRANGEBYSCORE` `ZRANGEBYLEX` `ZRANK` `ZSCORE` `ZCARD` `ZCOUNT` `ZINCRBY` `ZSCAN` |
| **Expiry** | `EXPIRE` `PEXPIRE` `TTL` `PTTL` `PERSIST` |
| **Iteration** | `KEYS` `SCAN` `TYPE` |
| **Server** | `PING` `QUIT` `AUTH` `SELECT` `ECHO` `INFO` `DBSIZE` `TIME` `BGSAVE` `FLUSHDB` `FLUSHALL` `CLIENT` `CONFIG` `COMMAND` `HELLO` `OBJECT` |

## Configuration

| Parameter | Default | Description |
|---|---|---|
| `--port` | `6399` | TCP port |
| `--bind` | `127.0.0.1` | Bind address (localhost only by default) |
| `--requirepass` | _(none)_ | Password for AUTH |
| `--data-dir` | `./data` | AOF + snapshot directory |
| `--max-memory` | `256mb` | KV value bytes limit |
| `--max-value-size` | `1mb` | Max size per key |
| `--max-chunks-per-key` | `100` | Max chunks from one MEMSAVE |
| `--aof-fsync` | `everysec` | AOF fsync: `always` / `everysec` / `no` |
| `--snapshot-interval` | `300` | Auto-snapshot interval (seconds, 0 = off) |
| `--snapshot-after-changes` | `1000` | Snapshot after N writes |
| `--embedding-threads` | `2` | ONNX inference threads |
| `--hnsw-m` | `16` | HNSW M parameter |
| `--hnsw-ef-construction` | `100` | HNSW efConstruction |
| `--log-level` | `info` | Log level |

Optional config file (`agentis-memory.conf`):

```
port 6399
bind 127.0.0.1
requirepass mysecret
max-memory 512mb
aof-fsync everysec
```

## Integrations

Agentis Memory works with any Redis client. For language-specific examples and AI framework tool definitions, see the **[Integration Guide](docs/integrations.md)**.

| Integration | How |
|---|---|
| **Any Redis client** | Connect to `localhost:6399`, use `MEMSAVE`/`MEMQUERY` as custom commands |
| **Claude Code** | Install the [Agentis Memory plugin](claude-plugin/) — adds `/mem-save`, `/mem-query` commands and automatic memory |
| **Vercel AI SDK** | Define `MEMSAVE`/`MEMQUERY` as tools — [example](docs/integrations.md#vercel-ai-sdk) |
| **LangChain** | Use `@tool` decorators — [example](docs/integrations.md#langchain-python) |
| **System prompt** | Include [agent-prompt.md](docs/agent-prompt.md) in your LLM's system message for autonomous memory use |
| **Redis Insight** | Connect GUI to `localhost:6399` — browse keys, run commands, monitor stats |

## Building from Source

Requirements: Java 25+, GraalVM (for native binary)

```bash
# Run tests
./gradlew test

# Run integration tests (requires Docker for Testcontainers)
./gradlew integrationTest

# Build JAR
./gradlew build

# Build native binary (requires GraalVM)
./gradlew nativeCompile
# → build/native/nativeCompile/agentis-memory

# Docker
docker build -t agentis-memory .
```

The ONNX model (`models/model.onnx`) must be present for embedding to work. During development it's loaded from the `models/` directory; the native binary bundles it.

## Roadmap

- [ ] Web UI for memory exploration and search
- [ ] Benchmarks vs Redis Stack, Dragonfly, Qdrant
- [ ] TLS support
- [ ] ACL-based namespace isolation
- [ ] Pluggable embedding models
- [ ] Storing pre-computed vectors in AOF (faster recovery)
- [ ] Partitioned HNSW index per namespace

## Contributing

Contributions welcome! Please open an issue to discuss before submitting large PRs.

## License

Apache-2.0
