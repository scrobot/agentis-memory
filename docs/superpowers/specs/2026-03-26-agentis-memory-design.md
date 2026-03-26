# Agentis Memory — Design Spec

## Overview

Self-contained in-memory service providing "working memory" for AI agents. Combines fast key-value cache with semantic vector search in a single process, accessible via Redis-compatible RESP protocol.

**Core principle:** One binary, one process, one port. Zero dependencies. Like Whisper — download, run, done.

**Key differentiator:** No existing solution exposes Redis-compatible wire protocol for agent memory. All competitors (Mem0, Zep, Letta, Redis Agent Memory Server) wrap REST/SDK layers. Agentis Memory lets any Redis client become an agent memory client.

## Tech Stack

- Java 26 + GraalVM native-image (single binary)
- ONNX Runtime via Panama FFI — embedded all-MiniLM-L6-v2 (~80MB, 384 dim)
- jvector (DataStax, Apache 2.0) — HNSW index for vector search
- Java Vector API — SIMD-accelerated cosine similarity
- Netty — TCP server for RESP protocol

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

### Two Storage Modes

- **KV Store** (raw cache): `SET key value`, `GET key`. Standard cache for raw data (Slack threads, JSON, anything). Fast key-based access.
- **Vector Engine** (semantic memory): `MEMSAVE key value`. Text is chunked, each chunk embedded via ONNX, indexed in HNSW. Original stored in KV. `MEMQUERY "query" K` searches top-K by semantic similarity.

### Namespaces for Multi-Agent Support

Namespace is extracted from key prefix (before first `:`). Keys without `:` belong to the `default` namespace.

```
MEMSAVE agent1:obs "user prefers dark theme"
MEMSAVE agent2:bug "auth module has a bug"
MEMSAVE shared:fact "project deadline April 15"

MEMQUERY agent1 "user preferences" 5        # search only agent1
MEMQUERY shared "deadline" 5                # search only shared
MEMQUERY ALL "auth" 5                       # search all namespaces
```

One instance, one HNSW index, namespace filtering at query time. Agents decide what's private vs shared by convention.

## RESP Protocol Layer

### Supported RESP v2 Types

- Simple Strings: `+OK\r\n`
- Errors: `-ERR unknown command\r\n`
- Integers: `:1\r\n`
- Bulk Strings: `$5\r\nhello\r\n`
- Arrays: `*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n`

### Network Layer

- Netty with `io_uring` on Linux (epoll fallback), `kqueue` on macOS
- Pipeline: `RespDecoder -> CommandParser -> CommandRouter -> RespEncoder`

### Command Router

```
Incoming command
       |
       +-- PING/QUIT/INFO/COMMAND  ->  built-in response
       |
       +-- SET/GET/DEL/EXISTS/     ->  KV Store
       |   EXPIRE/TTL/KEYS/SCAN
       |
       +-- MEMSAVE/MEMDEL          ->  KV Store + Vector Engine (async)
       |
       +-- MEMQUERY/MEMSTATUS      ->  Vector Engine
       |
       +-- everything else         ->  -ERR unknown command
```

### MEMSAVE Async Model

`MEMSAVE key value` is a hybrid operation:
1. **Synchronous:** stores original in KV Store, responds `+OK` immediately
2. **Asynchronous:** background chunking -> embedding -> HNSW indexation

Client does not wait for embedding to complete. ONNX inference on chunks may take 10-50ms; agent must not block.

**Key overwrite:** If the key already exists, old chunks are marked for deletion from HNSW index, the KV value is replaced, and new chunking + embedding is triggered. Old chunks are removed once new indexation completes (atomic swap). During the transition, MEMQUERY returns old chunks until new ones are ready.

`MEMSTATUS key` returns indexation status for verification (see Custom Commands section for response format).

## Supported Commands (MVP)

### Standard Redis Commands

| Command | Description |
|---|---|
| `SET key value [EX seconds]` | Store in KV Store |
| `GET key` | Retrieve from KV Store |
| `DEL key [key ...]` | Delete key(s) |
| `EXISTS key` | Check key existence |
| `EXPIRE key seconds` | Set TTL |
| `TTL key` | Get remaining TTL |
| `KEYS pattern` | Search keys by glob pattern (debug only, prefer SCAN) |
| `SCAN cursor [MATCH pattern] [COUNT n]` | Iterate keys safely |
| `PING` | Health check |
| `QUIT` | Close connection |
| `AUTH password` | Authenticate (if --requirepass is set) |
| `INFO [section]` | Server info (Redis Insight compat) |
| `DBSIZE` | Key count |
| `TYPE key` | Key type (always "string" for MVP) |
| `CLIENT SETNAME / INFO` | Connection management (stub) |
| `CONFIG GET` | Config read (stub with basic values) |
| `COMMAND` / `COMMAND DOCS` | Command metadata (stub) |
| `BGSAVE` | Trigger manual snapshot |

### Custom Commands

| Command | Description |
|---|---|
| `MEMSAVE key value` | Chunk + embed + index, store original. Overwrites if key exists. |
| `MEMQUERY namespace query K` | Semantic search top-K in namespace. namespace=`ALL` for cross-namespace. K must be 1..1000. |
| `MEMDEL key` | Delete from vector index + KV. Cancels pending indexation if in progress. |
| `MEMSTATUS key` | Indexation status (see format below) |

**MEMQUERY response** — RESP Array of arrays, each `[key, text, score]`:
```
*2
*3
$10
agent1:obs
$35
user prefers dark theme
$4
0.92
*3
$12
shared:fact
$22
project deadline April 15
$4
0.87
```

If fewer than K results match the namespace filter, fewer results are returned. This is expected — the caller can increase K or use `ALL` for broader search.

**MEMSTATUS response** — RESP Array `[status, chunk_count, dimensions, last_updated_ms]`:
```
*4
$7
indexed
:3
:384
:1711451234567
```

Status values: `indexed`, `pending`, `error`. If key does not exist: `-ERR no such key`.

**MEMQUERY validation:**
- K must be a positive integer 1..1000. Invalid K returns `-ERR K must be between 1 and 1000`.
- Empty query string returns `-ERR query must not be empty`.
- Unknown namespace (not `ALL` and no keys with that prefix exist) returns empty array (not an error).

## KV Store

### Data Structure

```java
record Entry(
    byte[] value,
    long createdAt,
    long expireAt,          // -1 = no expiry
    boolean hasVectorIndex  // linked chunks in HNSW
)
```

Core: `ConcurrentHashMap<String, Entry>` with lock-striping, O(1) read/write.

### TTL & Expiry

Two mechanisms (same as Redis):
- **Lazy expiry:** on every GET/EXISTS, check `expireAt`. Expired -> delete, return nil.
- **Active expiry:** background thread samples ~20 random keys with TTL per second. If >25% expired, repeat. Prevents dead key accumulation.

When a key with `hasVectorIndex=true` is deleted, associated chunks are also removed from HNSW.

### Memory Limit

`--max-memory` governs KV Store value bytes only (sum of all `Entry.value` byte arrays). HNSW index and ONNX runtime memory are additional overhead — see Memory Accounting section below.

Eviction policy:
- **volatile-lru** (default): evict LRU among keys with TTL. Keys without TTL are untouched.
- Additional policies (allkeys-lru, volatile-ttl) deferred to post-MVP.

### Memory Accounting

Approximate memory budget per component:
- **KV Store:** governed by `--max-memory` (default 256MB)
- **HNSW index:** ~1.5KB per vector (384 dim * 4 bytes + graph overhead). 100K chunks ≈ 150MB.
- **ONNX Runtime:** ~200MB resident (model + inference buffers)
- **Netty / JVM overhead:** ~50-100MB

Rule of thumb: total process RSS ≈ `--max-memory` + (chunk_count * 1.5KB) + 300MB baseline. This is documented in the operator guide (post-MVP).

### Value Size Limit

- `--max-value-size` (default `1mb`) — maximum size of a single value for both SET and MEMSAVE
- MEMSAVE additionally enforces `--max-chunks-per-key` (default `100`) — if chunking produces more chunks, return `-ERR value too large, exceeds max chunk count`
- Values exceeding `--max-value-size` return `-ERR value exceeds max-value-size limit`

### Persistence

**AOF (Append-Only File):**
- Every write operation appended to log file
- fsync strategy: `always` / `everysec` (default) / `no`

**Snapshots (RDB-style):**
- Full dump of ConcurrentHashMap to disk
- Triggered by: interval (`--snapshot-interval`), change threshold (`--snapshot-after-changes`), or manual `BGSAVE`
- Binary format with version header: `[magic: 4 bytes "AGMM"] [version: uint32] [entry_count: uint64] [entries...]`
- Forward compatibility: unknown version → refuse to load with clear error message

**HNSW Snapshots:**
- Periodic snapshot of HNSW index to disk (same trigger schedule as KV snapshots)
- Stored alongside KV snapshot in `--data-dir`

**Recovery strategy:**
1. Load latest KV snapshot (fast — entire state at once)
2. Load latest HNSW snapshot (vector index)
3. Replay AOF entries after snapshot timestamp:
   - SET/DEL/EXPIRE → apply to KV Store directly
   - MEMSAVE → apply to KV Store AND re-trigger chunking + embedding for HNSW delta
4. Re-embedding during recovery uses all `--embedding-threads`. For large AOF deltas this may take seconds to minutes — the server accepts connections only after recovery completes, responding to PING with `-LOADING server is loading data` (same as Redis).

**Storing vectors in AOF (optimization, post-MVP):** To avoid re-embedding on recovery, MEMSAVE entries in AOF could include pre-computed vectors. This eliminates recovery latency but increases AOF size. Deferred to post-MVP.

## Vector Engine

### Chunker

Sentence splitting with overlap:
- Split by sentence boundaries (`.`, `!`, `?`, `\n\n`)
- Group into chunks of ~200-300 tokens (within MiniLM 512-token window)
- 1-sentence overlap between chunks for context continuity
- Short text (<300 tokens) = single chunk, no splitting

Chunk structure:
```java
record Chunk(
    String parentKey,    // MEMSAVE key
    int index,           // chunk ordinal
    String text,         // chunk text
    float[] vector,      // embedding, 384 dim
    String namespace     // extracted from parentKey (before first ':')
)
```

### Embedding (ONNX Runtime via Panama FFI)

- Model: all-MiniLM-L6-v2, ONNX format, ~80MB, bundled with binary
- Tokenizer: bundled HuggingFace-compatible tokenizer
- Thread pool: `--embedding-threads N` (default 2)
- Batching: concurrent MEMSAVE operations group chunks into single inference batch

Expected latency:
- Single chunk (~200 tokens): ~5-10ms on modern CPU
- 1000-message Slack thread -> ~50 chunks -> ~100-200ms in background

### HNSW Index (jvector)

- HNSW graph: M=16, efConstruction=100 (configurable)
- Distance metric: cosine similarity
- Java Vector API (SIMD) acceleration — jvector supports this natively

### Namespace Filtering

Single HNSW index, post-filter by `chunk.namespace`. The search internally over-fetches by 3x multiplier (requests K*3 from HNSW, filters by namespace, returns top K). For agent memory volumes (thousands to tens of thousands of chunks) this is effective. If a namespace contains very few entries relative to the total index, the caller may receive fewer than K results — this is documented behavior, not an error.

Partitioned index per namespace is a future optimization if needed.

### HNSW Persistence

- Periodic snapshot to disk (same schedule as KV snapshots)
- On restart: load snapshot + re-index MEMSAVE entries from AOF delta (see Recovery strategy)

### MEMDEL Race Condition Handling

If `MEMDEL key` is called while async indexation is still `pending`:
1. KV entry is deleted immediately
2. Pending indexation job is cancelled via `CancellationToken`
3. If the job already committed some chunks before cancellation, a cleanup sweep removes orphaned chunks (chunks whose `parentKey` no longer exists in KV Store)
4. Cleanup sweep runs periodically (every 60s) as a background task

## Security

### Authentication

- `--requirepass <password>` — if set, clients must send `AUTH <password>` before any other command
- Without AUTH after connecting: all commands return `-NOAUTH Authentication required`
- Follows Redis AUTH semantics exactly

### Network Binding

- **Default bind: `127.0.0.1`** (localhost only). Explicitly opt-in to `--bind 0.0.0.0` for network access.
- TLS support: deferred to post-MVP. For production network access, recommend running behind a TLS-terminating proxy or within a private network.

### Namespace Isolation

Namespaces are a convention, not a security boundary. Any authenticated client can read/write any namespace. ACL-based namespace isolation is a post-MVP feature.

## Graceful Shutdown

On SIGTERM / SIGINT:
1. Stop accepting new connections
2. Wait for in-flight commands to complete (timeout: 5 seconds)
3. Cancel pending embedding jobs (do not wait for completion)
4. Flush AOF buffer to disk
5. Write final KV + HNSW snapshots
6. Exit with code 0

Pending MEMSAVE operations that were cancelled will be re-processed on next startup via AOF replay.

## Configuration

### CLI Parameters

| Parameter | Default | Description |
|---|---|---|
| `--port` | `6399` | TCP port |
| `--bind` | `127.0.0.1` | Bind address |
| `--requirepass` | (none) | Password for AUTH |
| `--data-dir` | `./data` | Directory for AOF, snapshots, HNSW |
| `--max-memory` | `256mb` | KV Store value bytes limit |
| `--max-value-size` | `1mb` | Maximum single value size |
| `--max-chunks-per-key` | `100` | Maximum chunks from one MEMSAVE |
| `--eviction-policy` | `volatile-lru` | Eviction strategy |
| `--aof-enabled` | `true` | Enable AOF |
| `--aof-fsync` | `everysec` | always / everysec / no |
| `--snapshot-interval` | `300` | Seconds between auto-snapshots (0=off) |
| `--snapshot-after-changes` | `1000` | Snapshot after N writes |
| `--embedding-threads` | `2` | ONNX inference threads |
| `--embedding-model` | `minilm-l6-v2` | Built-in model (one for MVP) |
| `--hnsw-m` | `16` | HNSW M parameter |
| `--hnsw-ef-construction` | `100` | HNSW efConstruction parameter |
| `--log-level` | `info` | Log level |

### Config File (optional)

`agentis-memory.conf` — Redis-style format:
```
port 6399
bind 127.0.0.1
requirepass mysecretpassword
data-dir ./data
max-memory 512mb
aof-fsync everysec
```

### Distribution

- GraalVM native-image -> single binary ~100-150MB
- Platforms: linux-amd64/arm64, macos-amd64/arm64
- Docker: `FROM scratch` + binary, minimal image
- ONNX model bundled inside binary (or in adjacent `models/` directory)
- Healthcheck: `PING` -> `+PONG`

## Testing Strategy (MVP)

- **RESP protocol conformance:** test against real Redis clients (Jedis, Lettuce, redis-py, redis-cli) — verify all supported commands parse and respond correctly
- **KV correctness under concurrency:** parallel SET/GET/DEL from multiple threads, verify no lost updates or phantom reads
- **Vector search recall:** embed a known corpus, query with known-similar texts, verify top-K recall >= 90% against brute-force baseline
- **Persistence round-trip:** write data, kill process, restart, verify all data recovered (KV + vector index)
- **Embedding latency benchmarks:** measure p50/p95/p99 for single chunk and batch inference, verify claims (5-10ms per chunk)
- **Redis Insight compatibility:** connect Redis Insight, verify INFO/SCAN/DBSIZE/TYPE work without errors

## Usage Example

```bash
redis-cli -p 6399

# --- Authenticate (if --requirepass is set) ---
AUTH mysecretpassword
# +OK

# --- Raw cache: store raw data for fast access ---
SET slack:thread:4521 "[{\"user\":\"alice\",\"text\":\"deploy crashed\"},...]" EX 3600
# +OK (instant, just cache, 1h TTL)

# --- Semantic memory: index knowledge ---
MEMSAVE agent1:incident "payments deploy crashed due to OOM.
Root cause: after memory limit update, pod restarts.
Alice reports same issue yesterday. Bob found limit is 256mb
but service consumes 400mb at peak load."
# +OK (instant, indexing in background)

MEMSAVE shared:policy "memory limits for production pods:
minimum 2x average consumption"

# --- Search: another agent queries by meaning ---
MEMQUERY shared "resource rules for production?" 3
# 1) shared:policy - "memory limits for production pods..." (0.94)
# 2) agent1:incident - "payments deploy crashed..." (0.68)

MEMQUERY ALL "payments issues" 5
# searches ALL namespaces

# --- Raw access: need full thread ---
GET slack:thread:4521
# returns all 1000 messages as-is

# --- Diagnostics ---
MEMSTATUS agent1:incident
# ["indexed", 3, 384, 1711451234567]

INFO memory
DBSIZE
SCAN 0 MATCH agent1:* COUNT 10
```

## Market Context

No existing solution provides Redis-protocol-compatible agent memory:

| Solution | Redis Protocol | KV + Semantic | Self-contained |
|---|---|---|---|
| Mem0 (51K stars) | No (REST/SDK) | No (semantic only) | No |
| Redis Agent Memory Server | No (REST/MCP) | Yes | No (needs Redis) |
| Zep/Graphiti | No (REST) | No (graph-based) | No (needs Neo4j) |
| Letta (MemGPT) | No (REST) | Partial | No (needs Postgres) |
| Motorhead | No (REST) | Yes | No (needs Redis), abandoned |
| **Agentis Memory** | **Yes** | **Yes** | **Yes** |
