# Agentis Memory — Remaining Tasks

## Status Legend
- ✅ Done
- 🔲 To do
- 🔀 Can be parallelized with other tasks

---

## ✅ Step 1: RESP Protocol + SET/GET/PING
## ✅ Step 1b: Embedder (ONNX Runtime)

---

## 🔲 Step 2: Data Type Support

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

### 🔲 2j: Redis Insight sample data validation
**Test:** Connect Redis Insight, load default sample data. No unknown command errors. All data visible in UI. This is the integration gate for Step 2.

**Parallelization for Step 2:** 2a must be done first. After 2a, tasks 2b through 2i can all run in parallel (separate data types, separate files, no conflicts). 2j is the final validation after all are merged.

---

## 🔲 Step 3: Chunker + HNSW Index

### 🔲 3a: Chunker
**Spec:** Sentence splitting with overlap. See design spec section "Vector Engine > Chunker".
**Files:** `Chunk.java`, `Chunker.java`
**Test:** Short text → 1 chunk. Long text → multiple. Overlap verified. `extractNamespace()` works.

### 🔲 3b: HNSW Index
**Spec:** jvector-based HNSW. See design spec section "Vector Engine > HNSW Index".
**Files:** `HnswIndex.java`
**Test:** Add/remove/search. Namespace filtering. Over-fetch 3x. Random vectors (no Embedder needed).

**Parallelization:** 3a and 3b can run in parallel. Both are independent of Step 2.

---

## 🔲 Step 4: MEMSAVE / MEMQUERY / MEMDEL / MEMSTATUS

**Depends on:** Steps 2a (multi-type store) and 3 (Chunker + HNSW)
**Spec:** [docs/commands/custom.md](commands/custom.md)

### 🔲 4a: VectorEngine coordinator
**Files:** `VectorEngine.java`
**Behavior:** Async indexation pipeline (chunker → embedder → HNSW), status tracking, cancellation, orphan cleanup.

### 🔲 4b: Custom command handlers
**Files:** `MemSaveCommand.java`, `MemQueryCommand.java`, `MemDelCommand.java`, `MemStatusCommand.java`
**Test:** E2E via Jedis — MEMSAVE → wait → MEMQUERY finds result. Namespace filtering. MEMDEL → MEMQUERY empty.

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

## 🔲 Step 8: Benchmark Module

**Depends on:** Steps 2 (all commands) + Step 4 (MEMSAVE/MEMQUERY)
**Spec:** [docs/superpowers/specs/2026-03-26-benchmark-design.md](superpowers/specs/2026-03-26-benchmark-design.md)

### 🔲 8a: Benchmark infrastructure
**Files:** `benchmark/build.gradle.kts`, `BenchmarkRunner.java`, `BenchmarkConfig.java`, `BenchmarkResult.java`, `BenchmarkReport.java`, `LatencyRecorder.java`, `ServerManager.java`
**Task:** Gradle submodule, CLI args parsing, Testcontainers Redis setup, HdrHistogram latency recording, side-by-side console + JSON report formatting.

### 🔲 8b: Standard command scenarios
**Files:** `StringsScenario.java`, `HashScenario.java`, `ListScenario.java`, `SortedSetScenario.java`, `SetScenario.java`
**Task:** Benchmarks for each data type against both Agentis Memory and Redis.

### 🔲 8c: Mixed workload + Pipeline scenarios
**Files:** `MixedWorkloadScenario.java`, `PipelineScenario.java`
**Task:** Realistic mixed workload (40% GET, 20% SET, etc.) and pipeline batching benchmarks.

### 🔲 8d: Memory commands scenario (Agentis-only)
**Files:** `MemoryCommandsScenario.java`
**Task:** MEMSAVE latency (sync + indexation time), MEMQUERY latency vs corpus size, recall@10 measurement.

**Test:** `./gradlew :benchmark:run` completes without errors, produces side-by-side report.

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
            Step 4 (MEMSAVE/MEMQUERY) ──┐
                  │                     │
                  ▼                     ▼
            Step 5 (AOF)          Step 8 (Benchmark)
                  │
                  ▼
            Step 6 (Snapshots + Shutdown)
                  │
                  ▼
            Step 7 (GraalVM Native Image)
```

Steps 2 and 3 can run fully in parallel.
Within Step 2, tasks 2b-2i can run in parallel after 2a.
Step 8 (Benchmark) can run in parallel with Steps 5-7 (persistence).
