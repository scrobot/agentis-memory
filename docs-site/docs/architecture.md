# Architecture

Agentis Memory combines a key-value store and a vector search engine in a single process, exposed over Redis wire protocol (RESP v2).

## Overview

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

## Two storage modes

### KV Store (standard Redis)

`SET`, `GET`, `HSET`, `LPUSH`, `ZADD`, etc. Standard cache operations with a `ConcurrentHashMap` backend. Supports TTL, expiry (lazy + active), LRU eviction, and full persistence (AOF + snapshots).

### Vector Engine (semantic memory)

`MEMSAVE` triggers a pipeline:

1. **Chunking** — text is split by sentence boundaries into chunks of ~200–300 tokens with 1-sentence overlap
2. **Embedding** — each chunk is embedded via all-MiniLM-L6-v2 (ONNX Runtime, 384 dimensions)
3. **Indexing** — vectors are inserted into an HNSW graph (jvector, cosine similarity)

`MEMQUERY` embeds the query and searches the HNSW index, returning results ranked by cosine similarity.

## MEMSAVE async model

`MEMSAVE key text` is a hybrid operation:

- **Synchronous:** stores original text in KV Store, returns `+OK` immediately
- **Asynchronous:** chunking → embedding → HNSW indexation runs in a background thread pool

The agent does not wait for embedding to complete. Use `MEMSTATUS key` to check indexation progress.

**Key overwrite:** if the key already exists, old chunks are marked for deletion, the KV value is replaced, and new indexation starts. Old chunks remain searchable until new ones are ready (atomic swap).

## Namespace filtering

A single HNSW index stores all vectors. `MEMQUERY` filters by namespace at query time, over-fetching 3x from HNSW and then filtering by the namespace prefix. This is efficient for agent-scale workloads (thousands to tens of thousands of chunks).

If a namespace has very few entries relative to the total index, fewer than K results may be returned. This is expected behavior.

## Persistence

### AOF (Append-Only File)

Every write operation is appended to the AOF log. Fsync strategy is configurable:

| `--aof-fsync` | Behavior |
|---|---|
| `always` | Fsync after every write — safest, slowest |
| `everysec` | Fsync once per second — good balance (default) |
| `no` | OS decides when to flush — fastest, risk of data loss |

### Snapshots

Periodic full dump of KV store and HNSW index to disk. Triggered by:

- Time interval (`--snapshot-interval`, default 300s)
- Write count (`--snapshot-after-changes`, default 1000)
- Manual `BGSAVE` command

Snapshot format: `[magic: "AGMM"][version: uint32][entry_count: uint64][entries...]`

### Recovery

On startup:

1. Load latest KV snapshot
2. Load latest HNSW snapshot
3. Replay AOF entries after the snapshot timestamp
    - `SET`/`DEL`/`EXPIRE` → applied to KV Store directly
    - `MEMSAVE` → applied to KV Store AND re-embedded for HNSW

During recovery, the server responds to `PING` with `-LOADING server is loading data`. It becomes available only after all re-embedding is complete.

### Graceful shutdown

On `SIGTERM` / `SIGINT`:

1. Stop accepting new connections
2. Drain in-flight commands (5s timeout)
3. Cancel pending embedding jobs
4. Flush AOF buffer
5. Write final KV + HNSW snapshots
6. Exit

Cancelled MEMSAVE operations are replayed from AOF on next startup.

## Tech stack

| Component | Technology |
|---|---|
| Language | Java 25 |
| Binary | GraalVM native-image |
| Embedding | ONNX Runtime via Panama FFI, all-MiniLM-L6-v2 (384 dim) |
| Vector index | jvector (DataStax, Apache 2.0) — HNSW, cosine similarity |
| SIMD | Java Vector API for cosine similarity |
| Network | Netty with io_uring (Linux) / kqueue (macOS) |
| Protocol | RESP v2 |
| DI | Avaje Inject |

## Memory accounting

| Component | Memory usage |
|---|---|
| KV Store | Governed by `--max-memory` (default 256MB) |
| HNSW index | ~1.5KB per vector (384 dim × 4 bytes + graph overhead) |
| ONNX Runtime | ~200MB resident (model + inference buffers) |
| Netty / JVM | ~50–100MB |

**Rule of thumb:** total RSS ≈ `--max-memory` + (chunk_count × 1.5KB) + 300MB baseline.
