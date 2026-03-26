# Agentis Memory — Remaining Tasks

## Status Legend
- ✅ Done
- 🔲 To do
- 🔀 Can be parallelized with other tasks

---

## ✅ Step 1: RESP Protocol + SET/GET/PING
## ✅ Step 1b: Embedder (ONNX Runtime)

---

## ✅ Step 2: Data Type Support

All data types share a unified key namespace. Each key has exactly one type. Commands against the wrong type return `WRONGTYPE Operation against a key holding the wrong kind of value`.

The KV Store must evolve from `ConcurrentHashMap<String, Entry>` (string-only) to a polymorphic store where each key maps to a typed value (string, hash, list, set, zset). Consider a `StoreValue` sealed interface with implementations per type.

TYPE command must return correct type. SCAN/KEYS must iterate all types. DEL/EXISTS/EXPIRE/TTL/RENAME/PERSIST/PEXPIRE/PTTL/UNLINK must work across all types.

### ✅ 2a: Refactor KV Store for multi-type support 🔀
**Spec:** Refactor `Entry` and `KvStore` to support multiple value types. `Entry.value` changes from `byte[]` to a polymorphic type. All existing string commands continue to work. TYPE returns `string` for existing keys.
**Test:** All existing tests pass. TYPE returns `string`.

### ✅ 2b: Hash commands 🔀 (after 2a)
**Spec:** [docs/commands/hashes.md](commands/hashes.md) — 14 commands
**Test:** Unit test per command. Integration test: HSET/HGET/HGETALL/HDEL via Jedis. TYPE returns `hash`.

### ✅ 2c: List commands 🔀 (after 2a)
**Spec:** [docs/commands/lists.md](commands/lists.md) — 11 commands
**Test:** Unit test per command. Integration test: LPUSH/RPUSH/LPOP/LRANGE via Jedis. TYPE returns `list`.

### ✅ 2d: Sorted Set commands 🔀 (after 2a)
**Spec:** [docs/commands/sorted-sets.md](commands/sorted-sets.md) — 14 commands
**Test:** Unit test per command. Integration test: ZADD/ZSCORE/ZRANGE/ZRANGEBYSCORE via Jedis. TYPE returns `zset`.

### ✅ 2e: Set commands 🔀 (after 2a)
**Spec:** [docs/commands/sets.md](commands/sets.md) — 11 commands
**Test:** Unit test per command. Integration test: SADD/SMEMBERS/SINTER via Jedis. TYPE returns `set`.

### ✅ 2f: Extended String commands 🔀 (after 2a)
**Spec:** [docs/commands/strings-extended.md](commands/strings-extended.md) — 13 commands
**Test:** Unit test per command. INCR/DECR overflow tests. MSET/MGET integration test.

### ✅ 2g: Extended Key commands 🔀 (after 2a)
**Spec:** [docs/commands/keys-basic.md](commands/keys-basic.md) — RENAME, RENAMENX, PERSIST, PEXPIRE, PTTL, RANDOMKEY, UNLINK, OBJECT
**Test:** Unit test per command. RENAME across types. PERSIST removes TTL.

### ✅ 2h: Extended Server commands 🔀 (after 2a)
**Spec:** [docs/commands/server.md](commands/server.md) — SELECT, FLUSHDB, FLUSHALL, ECHO, TIME
**Test:** FLUSHDB clears all data. TIME returns valid timestamp. SELECT 0 → OK.

### ✅ 2i: Extend SET command with NX/XX/PX/GET options
**Spec:** [docs/commands/strings-basic.md](commands/strings-basic.md) — full SET syntax
**Test:** SET with NX (set only if not exists), XX (set only if exists), PX (ms TTL), GET (return old value).

### ✅ 2j: Redis Insight sample data validation
**Test:** Connect Redis Insight, load default sample data. No unknown command errors. All data visible in UI. This is the integration gate for Step 2.

**Parallelization for Step 2:** 2a must be done first. After 2a, tasks 2b through 2i can all run in parallel (separate data types, separate files, no conflicts). 2j is the final validation after all are merged.

---

## ✅ Step 3: Chunker + HNSW Index

### ✅ 3a: Chunker
**Spec:** Sentence splitting with overlap. See design spec section "Vector Engine > Chunker".
**Files:** `Chunk.java`, `Chunker.java`
**Test:** Short text → 1 chunk. Long text → multiple. Overlap verified. `extractNamespace()` works.

### ✅ 3b: HNSW Index
**Spec:** jvector-based HNSW. See design spec section "Vector Engine > HNSW Index".
**Files:** `HnswIndex.java`
**Test:** Add/remove/search. Namespace filtering. Over-fetch 3x. Random vectors (no Embedder needed).

**Parallelization:** 3a and 3b can run in parallel. Both are independent of Step 2.

---

## ✅ Step 4: MEMSAVE / MEMQUERY / MEMDEL / MEMSTATUS

**Depends on:** Steps 2a (multi-type store) and 3 (Chunker + HNSW)
**Spec:** [docs/commands/custom.md](commands/custom.md)

### ✅ 4a: VectorEngine coordinator
**Files:** `VectorEngine.java`
**Behavior:** Async indexation pipeline (chunker → embedder → HNSW), status tracking, cancellation, orphan cleanup.

### ✅ 4b: Custom command handlers
**Files:** `MemSaveCommand.java`, `MemQueryCommand.java`, `MemDelCommand.java`, `MemStatusCommand.java`
**Test:** E2E via Jedis — MEMSAVE → wait → MEMQUERY finds result. Namespace filtering. MEMDEL → MEMQUERY empty.

### ✅ 4c: VectorEngine & Model Integration Tests
**Test:** `VectorModelIntegrationTest.java` — verifies full async pipeline with real ONNX model.

---

## 🔲 Step 5: AOF Persistence

**Depends on:** Step 4

### 🔲 5a: AOF Writer
**Files:** `AofWriter.java`
**Behavior:** Append write-commands in RESP format. fsync strategies (always/everysec/no).

### 🔲 5b: AOF Reader + Recovery
**Files:** `AofReader.java`, updates to `AgentisMemory.java`
**Behavior:** Parse AOF, replay commands via CommandRouter (without writing back to AOF). MEMSAVE triggers re-indexation. Server shows `-LOADING` during recovery.

### 🔲 5c: Integrate with CommandRouter
**Behavior:** After successful write-command execution, append to AOF. `CommandHandler.isWriteCommand()` flag.

**Test:** Write data → restart → data recovered. MEMSAVE → restart → MEMQUERY works.

---

## 🔲 Step 6: Snapshots

**Depends on:** Step 5

### 🔲 6a: Snapshot Writer/Reader for KV Store
**Files:** `SnapshotWriter.java`, `SnapshotReader.java`
**Format:** `[magic "AGMM"][version uint32][entry_count uint64][entries...]`. Atomic write (temp file → rename).

### 🔲 6b: HNSW Snapshot persistence
**Behavior:** Serialize/deserialize jvector index to disk.

### 🔲 6c: Snapshot Scheduler + BGSAVE
**Files:** `SnapshotScheduler.java`, `BgSaveCommand.java`
**Behavior:** Auto-snapshot by interval and change threshold. BGSAVE triggers manual snapshot.

### 🔲 6d: Recovery update
**Behavior:** New recovery order: KV snapshot → HNSW snapshot → AOF delta.

### 🔲 6e: Graceful Shutdown
**Behavior:** Stop connections → wait in-flight (5s) → cancel pending embeddings → flush AOF → final snapshot → exit.

**Test:** Write 1000 keys + MEMSAVE 10 → BGSAVE → add 10 more → restart → all present.

---

## 🔲 Step 8: RESP3 Protocol Support

**Depends on:** Step 2 (all data types implemented)

### 🔲 8a: HELLO command
**Spec:** Implement `HELLO [protover [AUTH username password] [SETNAME clientname]]`. Client sends `HELLO 2` or `HELLO 3` to negotiate protocol version. Response is a map with server info (server, version, proto, id, mode, role, modules). Per-connection protocol state tracking.
**Test:** `HELLO 2` → valid response, connection stays RESP2. `HELLO 3` → valid response, connection switches to RESP3. `HELLO` without args → returns server info in current protocol.

### 🔲 8b: RESP3 encoder
**Spec:** Add RESP3 wire types to `RespMessage` sealed interface and `RespEncoder`:
- Map (`%`): `%2\r\n+key1\r\n:1\r\n+key2\r\n:2\r\n`
- Set (`~`): `~3\r\n+a\r\n+b\r\n+c\r\n`
- Null (`_\r\n`), Boolean (`#t\r\n` / `#f\r\n`), Double (`,3.14\r\n`)
- Verbatim String (`=15\r\ntxt:Some string\r\n`)
- Big Number (`(3492890328409238509324850943850943825024385\r\n`)
- Push (`>`) for pub/sub (future)

**Test:** Encode/decode round-trip for each new type. Verify wire format matches Redis spec.

### 🔲 8c: RESP3-aware command responses
**Spec:** When connection is in RESP3 mode, commands return richer types:
- HGETALL → Map instead of flat array
- SMEMBERS → Set instead of array
- SISMEMBER → Boolean instead of integer
- ZSCORE → Double instead of bulk string
- Null values → Null type instead of NullBulkString

Per-connection `protocolVersion` field in `CommandDispatcher`; command handlers check it to choose response format.
**Test:** Same command returns different wire format depending on HELLO negotiation. Jedis/Lettuce integration test in RESP3 mode.

### 🔲 8d: RESP3 decoder
**Spec:** Extend `RespDecoder` to parse incoming RESP3 types (for completeness and client-to-server maps in future commands).
**Test:** Decoder handles all RESP3 types. Mixed RESP2/RESP3 connections on same port.

---

## 🔲 Step 7: GraalVM Native Image

**Depends on:** Step 6

### 🔲 7a: Reflection/Resource configuration
**Files:** `META-INF/native-image/` configs. Run tracing agent to generate.

### 🔲 7b: Native compilation
**Task:** `./gradlew nativeCompile`. Fix issues. Target: single binary ~100-150MB.

### 🔲 7c: Smoke test
**Test:** Start binary, PING, SET/GET, MEMSAVE/MEMQUERY, startup time <1s.

### 🔲 7d: Docker image
**Files:** `Dockerfile`
**Test:** `docker build` + `docker run` + redis-cli smoke test.

---

## Dependency Graph

```
Step 2a (refactor multi-type store)
  ├── 2b Hashes ─────────────┐
  ├── 2c Lists ──────────────┤
  ├── 2d Sorted Sets ────────┤
  ├── 2e Sets ───────────────┤
  ├── 2f Extended Strings ───┤
  ├── 2g Extended Keys ──────┤
  ├── 2h Extended Server ────┤
  ├── 2i SET options ────────┤
  │                          ▼
  │                    2j Redis Insight validation
  │
Step 3a Chunker ──┐
Step 3b HNSW ─────┤
                  ▼
            Step 4 (MEMSAVE/MEMQUERY)
                  │
                  ▼
            Step 5 (AOF)
                  │
                  ▼
            Step 6 (Snapshots + Shutdown)
                  │
                  ▼
            Step 7 (GraalVM Native Image)

Step 2 (all types) ──► Step 8 (RESP3 Protocol — can run in parallel with Steps 3-7)
```

Steps 2 and 3 can run fully in parallel.
Within Step 2, tasks 2b-2i can run in parallel after 2a.
