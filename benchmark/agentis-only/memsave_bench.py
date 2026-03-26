#!/usr/bin/env python3
"""
MEMSAVE / MEMQUERY latency benchmark for Agentis Memory.

Measures:
  1. MEMSAVE command latency (the synchronous KV write) across text sizes
  2. Indexation time (time until MEMSTATUS returns "indexed")
  3. MEMQUERY latency across corpus sizes

Usage:
    python memsave_bench.py [--host HOST] [--port PORT]
"""

import argparse
import statistics
import time

import redis


# ─── Helpers ──────────────────────────────────────────────────────────────────

def percentile(data: list[float], pct: float) -> float:
    sorted_data = sorted(data)
    idx = int(pct / 100 * len(sorted_data))
    idx = min(idx, len(sorted_data) - 1)
    return sorted_data[idx]


def make_text(char_count: int) -> str:
    """Generate deterministic text of approximately char_count characters."""
    word = "word "
    return (word * (char_count // len(word) + 1))[:char_count]


def wait_indexed(r: redis.Redis, key: str, timeout_s: float = 60.0) -> float:
    """Poll MEMSTATUS until key is indexed. Returns elapsed ms."""
    start = time.perf_counter_ns()
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        status = r.execute_command("MEMSTATUS", key)
        # MEMSTATUS returns [status, chunk_count, dimensions, last_updated_ms]
        if isinstance(status, list) and len(status) >= 1:
            state = status[0]
            if isinstance(state, bytes):
                state = state.decode()
            if state == "indexed":
                break
            if state == "error":
                raise RuntimeError(f"MEMSTATUS reported error for key {key}")
        time.sleep(0.001)
    elapsed_ms = (time.perf_counter_ns() - start) / 1e6
    return elapsed_ms


# ─── Benchmark 1: MEMSAVE latency (sync part) ────────────────────────────────

def bench_memsave_latency(r: redis.Redis, text_sizes: list[int], iterations: int = 200):
    print("\n── MEMSAVE Latency (synchronous KV write) ──────────────────────────────")
    print(f"{'Text size':>12}  {'p50 (ms)':>10}  {'p95 (ms)':>10}  {'p99 (ms)':>10}  {'mean (ms)':>10}")
    print("─" * 60)

    results = {}
    for text_size in text_sizes:
        text = make_text(text_size)
        latencies = []

        for i in range(iterations):
            key = f"memsave_lat:{text_size}:{i}"
            start = time.perf_counter_ns()
            r.execute_command("MEMSAVE", key, text)
            end = time.perf_counter_ns()
            latencies.append((end - start) / 1e6)

        p50 = statistics.median(latencies)
        p95 = percentile(latencies, 95)
        p99 = percentile(latencies, 99)
        mean = statistics.mean(latencies)

        results[text_size] = {"p50": p50, "p95": p95, "p99": p99, "mean": mean}
        print(f"{text_size:>12}  {p50:>10.3f}  {p95:>10.3f}  {p99:>10.3f}  {mean:>10.3f}")

    return results


# ─── Benchmark 2: Indexation time ────────────────────────────────────────────

def bench_indexation_time(r: redis.Redis, text_sizes: list[int]):
    print("\n── Indexation Time (time until MEMSTATUS = indexed) ────────────────────")
    print(f"{'Text size':>12}  {'index ms':>12}  {'chunks':>8}")
    print("─" * 40)

    results = {}
    for text_size in text_sizes:
        text = make_text(text_size)
        key = f"indexbench:{text_size}"

        r.execute_command("MEMSAVE", key, text)
        elapsed_ms = wait_indexed(r, key)

        # Get chunk count from MEMSTATUS
        status = r.execute_command("MEMSTATUS", key)
        chunks = int(status[1]) if isinstance(status, list) and len(status) > 1 else "?"

        results[text_size] = {"index_ms": elapsed_ms, "chunks": chunks}
        print(f"{text_size:>12}  {elapsed_ms:>12.1f}  {chunks:>8}")

    return results


# ─── Benchmark 3: MEMQUERY latency ───────────────────────────────────────────

def bench_memquery_latency(r: redis.Redis, corpus_size: int, top_k: int = 10,
                           iterations: int = 100):
    print(f"\n── MEMQUERY Latency (corpus={corpus_size}, top-{top_k}) ──────────────────────")

    # Populate corpus if needed
    print(f"  Populating {corpus_size} entries...")
    existing_count = 0
    for i in range(corpus_size):
        key = f"corpus:{i}"
        # Quick check — MEMSTATUS returns error/pending if not indexed
        try:
            status = r.execute_command("MEMSTATUS", key)
            if isinstance(status, list) and len(status) >= 1:
                s = status[0]
                if isinstance(s, bytes):
                    s = s.decode()
                if s in ("indexed", "pending"):
                    existing_count += 1
                    continue
        except Exception:
            pass
        text = make_text(500) + f" entry number {i} topic {i % 20}"
        r.execute_command("MEMSAVE", key, text)

    if existing_count < corpus_size:
        print(f"  Waiting for indexation of {corpus_size - existing_count} new entries...")
        # Wait for a sample to be indexed (not all — that would take too long for large corpora)
        sample_key = f"corpus:{corpus_size - 1}"
        try:
            wait_indexed(r, sample_key, timeout_s=120)
        except RuntimeError as e:
            print(f"  WARNING: {e}")

    print(f"  Running {iterations} MEMQUERY iterations...")
    queries = [
        "search query about something important",
        "agent memory retrieval semantic",
        "recent observations from environment",
        "task planning and execution steps",
        "error in previous action",
    ]

    latencies = []
    for i in range(iterations):
        query = queries[i % len(queries)]
        start = time.perf_counter_ns()
        r.execute_command("MEMQUERY", "corpus", query, str(top_k))
        end = time.perf_counter_ns()
        latencies.append((end - start) / 1e6)

    p50 = statistics.median(latencies)
    p95 = percentile(latencies, 95)
    p99 = percentile(latencies, 99)

    print(f"  p50={p50:.3f}ms  p95={p95:.3f}ms  p99={p99:.3f}ms")
    return {"corpus_size": corpus_size, "top_k": top_k, "p50": p50, "p95": p95, "p99": p99}


def bench_memquery_vs_corpus(r: redis.Redis, corpus_sizes: list[int]):
    print("\n── MEMQUERY Latency vs Corpus Size ─────────────────────────────────────")
    print(f"{'corpus':>10}  {'top-K':>6}  {'p50 (ms)':>10}  {'p95 (ms)':>10}  {'p99 (ms)':>10}")
    print("─" * 55)

    results = []
    for size in corpus_sizes:
        stats = bench_memquery_latency(r, size)
        print(f"{stats['corpus_size']:>10}  {stats['top_k']:>6}  "
              f"{stats['p50']:>10.3f}  {stats['p95']:>10.3f}  {stats['p99']:>10.3f}")
        results.append(stats)
    return results


# ─── Main ─────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="MEMSAVE/MEMQUERY latency benchmark")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=6399)
    parser.add_argument("--skip-corpus", action="store_true",
                        help="Skip MEMQUERY corpus benchmarks (faster)")
    args = parser.parse_args()

    print(f"Connecting to {args.host}:{args.port}...")
    r = redis.Redis(host=args.host, port=args.port, socket_timeout=30)

    try:
        r.ping()
    except Exception as e:
        print(f"ERROR: Cannot connect to Agentis Memory at {args.host}:{args.port}: {e}")
        raise SystemExit(1)

    # Flush before benchmarks
    r.execute_command("FLUSHALL")
    print("Flushed server state.")

    # ── 1. MEMSAVE latency ──
    text_sizes = [100, 500, 2_000, 10_000]
    bench_memsave_latency(r, text_sizes, iterations=300)

    # ── 2. Indexation time ──
    bench_indexation_time(r, text_sizes=[100, 500, 2_000, 10_000])

    # ── 3. MEMQUERY latency ──
    if not args.skip_corpus:
        bench_memquery_vs_corpus(r, corpus_sizes=[100, 500, 1_000, 5_000])
    else:
        print("\n(MEMQUERY corpus benchmarks skipped with --skip-corpus)")

    print("\nDone.")


if __name__ == "__main__":
    main()
