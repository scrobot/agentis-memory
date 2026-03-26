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

Namespace is extracted from key prefix (before first `:`):

```
MEMSAVE agent1:obs "user prefers dark theme"
MEMSAVE agent2:bug "auth module has a bug"
MEMSAVE shared:fact "project deadline April 15"

MEMQUERY agent1: "user preferences" 5     # search only agent1
MEMQUERY shared: "deadline" 5             # search only shared
MEMQUERY * "auth" 5                       # search all namespaces
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

`MEMSTATUS key` returns `indexed` / `pending` / `error` for verification.

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
| `KEYS pattern` | Search keys by glob pattern |
| `SCAN cursor [MATCH pattern] [COUNT n]` | Iterate keys safely |
| `PING` | Health check |
| `QUIT` | Close connection |
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
| `MEMSAVE key value` | Chunk + embed + index, store original |
| `MEMQUERY namespace query K` | Semantic search top-K in namespace |
| `MEMDEL key` | Delete from vector index + KV |
| `MEMSTATUS key` | Indexation status: indexed/pending/error |

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

`--max-memory` with eviction policy:
- **volatile-lru** (default): evict LRU among keys with TTL. Keys without TTL are untouched.
- Additional policies (allkeys-lru, volatile-ttl) deferred to post-MVP.

### Persistence

**AOF (Append-Only File):**
- Every write operation appended to log file
- fsync strategy: `always` / `everysec` (default) / `no`
- On restart: replay AOF to restore state

**Snapshots (RDB-style):**
- Full dump of ConcurrentHashMap to disk
- Triggered by: interval (`--snapshot-interval`), change threshold (`--snapshot-after-changes`), or manual `BGSAVE`
- Binary format, fast deserialization

**Recovery strategy:**
1. Load latest snapshot (fast — entire state at once)
2. Replay AOF entries after snapshot timestamp (delta only)

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

### MEMQUERY Response Format

```
MEMQUERY namespace "query" K
```

1. Query text -> ONNX embedding -> query vector (384 dim)
2. HNSW search with namespace filter (* = no filter)
3. Return top-K as RESP Array: `[key, text, score]` per result

### Namespace Filtering

Single HNSW index, post-filter by `chunk.namespace`. For agent memory volumes (thousands to tens of thousands of chunks) this is more efficient than maintaining N separate indexes. Partitioned index is a future optimization if needed.

### HNSW Persistence

- Periodic snapshot to disk (configurable interval or write threshold)
- On restart: load snapshot + re-index entries from AOF that came after snapshot

## Configuration

### CLI Parameters

| Parameter | Default | Description |
|---|---|---|
| `--port` | `6399` | TCP port |
| `--bind` | `0.0.0.0` | Bind address |
| `--data-dir` | `./data` | Directory for AOF, snapshots, HNSW |
| `--max-memory` | `256mb` | KV Store memory limit |
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

## Usage Example

```bash
redis-cli -p 6399

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
MEMQUERY shared: "resource rules for production?" 3
# 1) shared:policy - "memory limits for production pods..." (0.94)
# 2) agent1:incident - "payments deploy crashed..." (0.68)

MEMQUERY * "payments issues" 5
# searches ALL namespaces

# --- Raw access: need full thread ---
GET slack:thread:4521
# returns all 1000 messages as-is

# --- Diagnostics ---
MEMSTATUS agent1:incident
# indexed (3 chunks, 384 dim, 12ms ago)

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
