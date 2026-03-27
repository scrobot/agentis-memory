# Hot Path Performance Optimization

**Date:** 2026-03-27
**Goal:** Match Redis throughput (300K+ ops/sec) and latency (p99 < 1.5ms) on SET/GET benchmark
**Baseline:** 133K ops/sec, p99 9ms (vs Redis 332K ops/sec, p99 1.1ms)

## Root Causes

| Bottleneck | Impact | Location |
|---|---|---|
| Byte-by-byte parsing + ByteArrayOutputStream per line | Millions of syscalls + allocations/sec | `RespParser.readLine()` |
| `String.getBytes(UTF_8)` + `Long.toString()` per response | 260K+ allocations/sec | `RespWriter` |
| `writer.flush()` after every command | No pipelining, syscall per command | `RespServer.handleConnection()` |
| `synchronized` AOF append + StringBuilder per write | Lock contention + allocations on every SET | `AofWriter.append()` |
| `System.currentTimeMillis()` per GET/SET | 130K+ kernel calls/sec | `KvStore.get/set()` |
| `new String(args.get(0)).toUpperCase()` per command | String allocation per command | `CommandRouter`, command handlers |
| `AtomicLong.incrementAndGet()` counters | Cache-line bouncing across virtual threads | `CommandRouter` |
| Serial GC in GraalVM CE native-image | Single-threaded STW pauses → p99 spikes | Runtime |

## Design

### 1. RespParser — Buffered Bulk Reading

Replace byte-by-byte `InputStream.read()` + `ByteArrayOutputStream` with a per-connection reusable read buffer.

**New implementation:**
- Per-connection `byte[]` buffer (16KB default)
- `refill()` reads bulk data from InputStream into buffer
- `readLine()` scans buffer for `\r\n`, returns `byte[]` slice (no String allocation)
- `readExact(int n)` reads N bytes from buffer, refilling as needed
- `hasBufferedData()` returns `pos < limit` — used by RespServer for pipelining
- Bulk strings decoded directly from buffer without intermediate copies when contiguous

**Key invariant:** Parser never creates a `String` on the hot path. Command name comparison uses byte-level matching.

### 2. RespWriter — Zero-Allocation Response Encoding

Replace `String.getBytes()` and `Long.toString()` with direct byte encoding into a reusable write buffer.

**New implementation:**
- Per-connection `byte[]` write buffer (16KB default)
- Static pre-encoded responses: `OK_RESP`, `PONG_RESP`, `NULL_BULK`, `QUEUED_RESP`, common integers 0-99
- `writeLong(long v)` encodes directly into buffer bytes without String intermediary
- `writeBulkString(byte[] data)` writes `$len\r\n` + data + `\r\n` in one shot
- `flush()` is a separate explicit call — writes buffered data to OutputStream

### 3. RespServer — Pipelining Support

Enable Redis-style pipelining by deferring flush until no more commands are buffered.

```java
while (!stopped) {
    RespMessage msg = parser.readMessage();
    if (msg == null) break;

    RespMessage response = router.dispatch(conn, msg);
    writer.write(response);

    // Flush only when client is waiting (no more pipelined commands)
    if (!parser.hasBufferedData()) {
        writer.flush();
    }
}
```

This is the single highest-impact change for throughput. memtier_benchmark uses pipelining by default — without write coalescing, we issue a syscall per response instead of batching dozens.

### 4. AofWriter — Async Batched Writes

Replace synchronized per-command append with a lock-free queue drained by a dedicated thread.

**New implementation:**
- `ConcurrentLinkedQueue<byte[]>` (MPSC pattern) — hot path just serializes to `byte[]` and enqueues
- Dedicated platform thread drains queue in batches, writes to FileChannel
- `fsync=everysec`: timer-based force() on the writer thread
- `fsync=always`: writer thread forces after each drain batch (still batched, not per-command)
- Pre-encode RESP format into the `byte[]` before enqueue to avoid allocation in writer thread
- The hot path method `append()` is non-blocking: encode + enqueue only

### 5. CommandRouter — Reduce Per-Command Overhead

**5a. Command lookup by bytes:**
- Store command names as `byte[]` in a custom lookup table
- Compare incoming command bytes directly (case-insensitive byte compare)
- Avoid `new String()` + `.toUpperCase()` per command

**5b. Counters:**
- `AtomicLong` → `LongAdder` for `commandsProcessed` / `commandErrors`
- Less cache-line contention across virtual threads

**5c. Debug logging guard:**
- Wrap `log.debug(...)` calls with `if (log.isDebugEnabled())` to avoid evaluating `describeResponse()` switch expression when debug is off

**5d. Auth fast path:**
- Cache `requirepass == null` as a `boolean noAuth` field
- Skip the entire auth check block when no password is configured

### 6. Time Caching

Replace per-operation `System.currentTimeMillis()` with a cached value.

**Implementation:**
- `CachedClock` singleton: `volatile long now` updated every 1ms by a daemon thread
- All TTL/expiry checks use `CachedClock.now()` instead of `System.currentTimeMillis()`
- 1ms resolution is sufficient for TTL (minimum Redis TTL is 1ms)
- The updater thread uses `Thread.sleep(1)` — cheap on virtual thread scheduler

### 7. Oracle GraalVM + G1 GC

Switch from GraalVM CE (Serial GC) to Oracle GraalVM (G1 GC) for the native-image build.

**Changes:**
- Dockerfile base image: `ghcr.io/graalvm/native-image-community` → `container-registry.oracle.com/graalvm/native-image:23` (or latest)
- Add `--gc=G1` to native-image build flags in `build.gradle.kts`
- G1 provides concurrent marking, parallel young gen collection, and much shorter STW pauses
- Expected: p99 latency drop from 9ms to <2ms due to elimination of long Serial GC pauses

## Files to Modify

| File | Change |
|---|---|
| `RespParser.java` | Full rewrite: buffered reading, byte[] returns, hasBufferedData() |
| `RespWriter.java` | Full rewrite: buffered writing, pre-encoded responses, manual long encoding |
| `RespServer.java` | Pipelining: conditional flush based on parser buffer state |
| `AofWriter.java` | Async: ConcurrentLinkedQueue + dedicated writer thread |
| `CommandRouter.java` | Byte-based lookup, LongAdder, debug guard, auth fast path |
| `KvStore.java` | Use CachedClock instead of System.currentTimeMillis() |
| `SetCommand.java` | Remove `new String()` for command name, use byte[] key from router |
| `GetCommand.java` | Same as SetCommand |
| `build.gradle.kts` | Oracle GraalVM + `--gc=G1` flag |
| `Dockerfile` (or docker-compose) | Oracle GraalVM base image |

New files:
| File | Purpose |
|---|---|
| `CachedClock.java` | Singleton cached time provider |

## Non-Goals

- NIO / io_uring / epoll — virtual threads with optimized blocking I/O is sufficient for target
- Off-heap memory — allocation reduction should be enough without going off-heap
- Vector API optimizations — relevant for MEMQUERY cosine similarity, not SET/GET hot path
- Connection pooling / multiplexing — client-side concern

## Success Criteria

- memtier_benchmark (50 connections, 1:10 SET:GET ratio): **300K+ ops/sec total**
- p99 latency: **< 1.5ms**
- p99.9 latency: **< 5ms**
- No correctness regressions: all existing integration tests pass
