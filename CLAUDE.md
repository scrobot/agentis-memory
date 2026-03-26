# Agentis Memory — CLAUDE.md

## Project Overview

Self-contained in-memory service providing "working memory" for AI agents. Combines a fast key-value cache with semantic vector search in a single process, accessible via Redis-compatible RESP protocol.

**Core principle:** One binary, one process, one port. Zero dependencies. Download and run.

**Key differentiator:** Redis-compatible wire protocol for agent memory. Any Redis client (redis-cli, Jedis, Lettuce, redis-py) works out of the box.

## Tech Stack

- **Language:** Java 26
- **Build:** GraalVM native-image → single binary (~100-150MB)
- **Embedding:** ONNX Runtime via Panama FFI, all-MiniLM-L6-v2 (~80MB, 384 dim), bundled in binary
- **Vector index:** jvector (DataStax, Apache 2.0) — HNSW index, cosine similarity
- **SIMD:** Java Vector API for cosine similarity acceleration
- **Network:** Netty with io_uring (Linux) / kqueue (macOS)
- **Protocol:** RESP v2 (Redis wire protocol)

## Repository Structure

```
agentis-memory/
├── CLAUDE.md
├── build.gradle.kts
├── gradle.properties                                  # dependency versions
├── agentis-memory.conf.example
├── docs/
│   └── superpowers/
│       └── specs/
│           └── 2026-03-26-agentis-memory-design.md   # Full design spec
├── models/                                            # Bundled ONNX model artifacts
│   ├── model.onnx                                     # all-MiniLM-L6-v2 (~80MB)
│   ├── tokenizer.json
│   └── tokenizer_config.json
└── src/
    ├── main/java/io/agentis/memory/
    │   ├── AgentisMemory.java              # main(), startup, shutdown hook
    │   ├── config/
    │   │   └── ServerConfig.java           # CLI args + conf file parsing
    │   ├── resp/
    │   │   ├── RespMessage.java            # sealed interface: SimpleString, Error, Integer, BulkString, Array
    │   │   ├── RespDecoder.java            # Netty ByteToMessageDecoder
    │   │   ├── RespEncoder.java            # Netty MessageToByteEncoder
    │   │   ├── RespServer.java             # Netty bootstrap, pipeline setup
    │   │   └── CommandDispatcher.java      # Netty handler → CommandRouter
    │   ├── command/
    │   │   ├── CommandHandler.java         # interface: handle(ctx, args) → RespMessage
    │   │   ├── CommandRouter.java          # command name → handler dispatch
    │   │   ├── kv/                         # standard Redis commands
    │   │   │   ├── SetCommand.java
    │   │   │   ├── GetCommand.java
    │   │   │   ├── DelCommand.java
    │   │   │   ├── ExistsCommand.java
    │   │   │   ├── ExpireCommand.java
    │   │   │   ├── TtlCommand.java
    │   │   │   ├── KeysCommand.java
    │   │   │   ├── ScanCommand.java
    │   │   │   ├── TypeCommand.java
    │   │   │   ├── DbSizeCommand.java
    │   │   │   ├── BgSaveCommand.java
    │   │   │   ├── PingCommand.java
    │   │   │   ├── QuitCommand.java
    │   │   │   ├── AuthCommand.java
    │   │   │   ├── InfoCommand.java
    │   │   │   ├── ClientCommand.java
    │   │   │   ├── ConfigCommand.java
    │   │   │   └── CommandMetaCommand.java
    │   │   └── mem/                        # custom memory commands
    │   │       ├── MemSaveCommand.java
    │   │       ├── MemQueryCommand.java
    │   │       ├── MemDelCommand.java
    │   │       └── MemStatusCommand.java
    │   ├── store/
    │   │   ├── Entry.java                  # record: value, createdAt, expireAt, hasVectorIndex
    │   │   ├── KvStore.java                # ConcurrentHashMap<String, Entry>
    │   │   ├── ExpiryManager.java          # lazy + active expiry (Redis-style 25% threshold)
    │   │   └── EvictionManager.java        # volatile-lru eviction
    │   ├── vector/
    │   │   ├── Chunk.java                  # record: parentKey, index, text, vector, namespace
    │   │   ├── Chunker.java                # sentence-boundary splitting with overlap
    │   │   ├── Embedder.java               # ONNX Runtime inference, batching
    │   │   ├── HnswIndex.java              # jvector HNSW wrapper, namespace post-filter
    │   │   └── VectorEngine.java           # coordinator + per-key IndexStatus tracking
    │   └── persistence/
    │       ├── AofWriter.java              # append-only file, configurable fsync
    │       ├── AofReader.java              # AOF replay for recovery
    │       ├── SnapshotWriter.java         # periodic KV + HNSW snapshots (AGMM format)
    │       └── SnapshotReader.java         # recovery coordinator
    └── test/java/io/agentis/memory/
        ├── resp/RespDecoderTest.java
        ├── store/KvStoreTest.java
        ├── vector/ChunkerTest.java
        ├── vector/EmbedderTest.java
        ├── vector/HnswIndexTest.java
        └── integration/
            ├── RedisClientCompatTest.java  # Jedis against running server
            └── MemoryE2eTest.java          # MEMSAVE → MEMQUERY end-to-end
```

The project is in **early implementation phase**. Design spec is complete and reviewed. All source file stubs are in place; logic is pending.

## Architecture

```
+------------------------------------------+
|          RESP Protocol Layer             |
|   TCP :6399, parses Redis commands       |
+------------------------------------------+
|          Command Router                  |
|   SET/GET/DEL/TTL  -> KV Store           |
|   MEMSAVE/MEMQUERY -> Vector Engine      |
+-------------------+----------------------+
|    KV Store       |   Vector Engine      |
|                   |                      |
| ConcurrentHashMap | Chunker (sentences)  |
| TTL / Expiry      | ONNX Embedding       |
| AOF + Snapshots   | HNSW Index (jvector) |
+-------------------+----------------------+
```

### Key Design Decisions

- **MEMSAVE is async:** KV write is synchronous (returns `+OK` immediately), chunking + embedding + HNSW indexation runs in background. Use `MEMSTATUS key` to check indexation status.
- **Namespace isolation by convention:** prefix before first `:` (e.g. `agent1:obs`). Not a security boundary — any authenticated client can read/write any namespace. True tenant isolation requires separate server instances, each with its own `--requirepass` and port. ACL-based namespace isolation is post-MVP; see the design spec's Security section.
- **Single HNSW index with post-filter:** namespace filtering over-fetches K×3 from HNSW, then filters. Fewer than K results is normal for sparse namespaces.
- **Memory accounting:** `--max-memory` governs KV value bytes only. Total RSS ≈ `--max-memory` + (chunk_count × 1.5KB) + 300MB baseline.

## Commands

### Standard Redis Commands
`SET`, `GET`, `DEL`, `EXISTS`, `EXPIRE`, `TTL`, `KEYS`, `SCAN`, `PING`, `QUIT`, `AUTH`, `INFO`, `DBSIZE`, `TYPE`, `CLIENT SETNAME/INFO`, `CONFIG GET`, `COMMAND`, `BGSAVE`

### Custom Commands
| Command | Description |
|---|---|
| `MEMSAVE key value` | Chunk + embed + index; stores original in KV. Async. |
| `MEMQUERY namespace query K` | Semantic top-K search. `namespace=ALL` for cross-namespace. K: 1–1000. |
| `MEMDEL key` | Delete from vector index + KV. Cancels pending indexation. |
| `MEMSTATUS key` | Returns `[status, chunk_count, dimensions, last_updated_ms]`. Status: `indexed`/`pending`/`error`. |

## Configuration

Key CLI parameters (see design spec for full list):

| Parameter | Default | Notes |
|---|---|---|
| `--port` | `6399` | TCP port |
| `--bind` | `127.0.0.1` | Localhost only by default |
| `--requirepass` | (none) | Redis AUTH semantics |
| `--data-dir` | `./data` | AOF + snapshots |
| `--max-memory` | `256mb` | KV value bytes only |
| `--max-value-size` | `1mb` | Per key limit for SET and MEMSAVE |
| `--max-chunks-per-key` | `100` | Max chunks from one MEMSAVE |
| `--aof-fsync` | `everysec` | `always`/`everysec`/`no` |
| `--embedding-threads` | `2` | ONNX inference threads |
| `--hnsw-m` | `16` | HNSW M parameter |
| `--hnsw-ef-construction` | `100` | HNSW efConstruction |

Optional config file: `agentis-memory.conf` (Redis-style key-value format).

## Persistence & Recovery

1. **AOF:** every write appended; fsync configurable
2. **Snapshots:** KV + HNSW, triggered by interval / change count / `BGSAVE`
3. **Snapshot format:** `[magic: "AGMM"][version: uint32][entry_count: uint64][entries...]`
4. **Recovery order:** load KV snapshot → load HNSW snapshot → replay AOF delta (MEMSAVE entries re-embed). The server responds to PING with `-LOADING server is loading data` for the entire recovery window, including AOF re-embedding. The server does not become available until re-embedding is complete, so `MEMQUERY` results are never stale due to partial startup.

**Graceful shutdown (SIGTERM/SIGINT):** drains in-flight commands (5s timeout), cancels pending embeddings, flushes AOF, writes final snapshots.

## Testing Strategy

- **RESP conformance:** test against Jedis, Lettuce, redis-py, redis-cli
- **KV concurrency:** parallel SET/GET/DEL; verify no lost updates
- **Vector recall:** known corpus, verify top-K recall ≥ 90% vs brute-force
- **Persistence round-trip:** write → kill → restart → verify full recovery
- **Embedding latency:** p50/p95/p99 benchmarks, target 5–10ms/chunk
- **Redis Insight compatibility:** INFO/SCAN/DBSIZE/TYPE

## Distribution

- GraalVM native binary; platforms: linux-amd64/arm64, macos-amd64/arm64
- Docker: `FROM scratch` + binary
- ONNX model lookup order: bundled binary resource → `--model-path <dir>` CLI flag → `./models/` adjacent directory. During local development, the `models/` directory in this repo is used. The final native binary embeds the model directly.
- Healthcheck: `PING` → `+PONG`

## Design Spec

Full specification: [docs/superpowers/specs/2026-03-26-agentis-memory-design.md](fleet-file://ass9vel866jun393uhgd/Users/alex/projects/agentis-memory/docs/superpowers/specs/2026-03-26-agentis-memory-design.md?type=file&root=%252F)
