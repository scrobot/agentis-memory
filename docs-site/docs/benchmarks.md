# Benchmarks

Benchmark results comparing Agentis Memory against Redis 7.4, Dragonfly, and Lux on standard KV throughput scenarios. Benchmarks were run on 2026-03-30 using memtier_benchmark 2.1.0.

**[View the interactive report with charts](benchmarks/report.html)** for visual exploration of all results.

---

## Methodology

### Test environment

| Item | Detail |
|---|---|
| Host | Hetzner Cloud shared vCPU instance, Ubuntu |
| Deployment | All servers running in Docker containers on the same host via Docker Compose |
| Tool | memtier_benchmark 2.1.0 |
| memtier config | 4 threads, 50 clients, 100,000 requests per scenario |
| Date | 2026-03-30 |

### Servers under test

| Server | Version / Image |
|---|---|
| Agentis Memory | `scrobot/agentis-memory:v1.0.1` |
| Redis | `redis:7.4` (persistence disabled) |
| Dragonfly | `docker.dragonflydb.io/dragonflydb/dragonfly:latest` |
| Lux | Built from source — [github.com/lux-db/lux](https://github.com/lux-db/lux) |

### Why memtier_benchmark

memtier_benchmark is the same tool used in Redis, Dragonfly, and Garnet's own published benchmarks. Using the same tool and reporting format makes results directly comparable to vendor claims.

### Fairness considerations

!!! warning "Important caveats"
    - **Shared vCPU:** Hetzner Cloud shared instances experience variable CPU allocation. Numbers may be 10–20% lower than bare-metal results.
    - **Same-host networking:** All containers ran on the same host. Loopback latency is not representative of production network conditions.
    - **Dragonfly multi-threading:** Dragonfly is designed to use multiple cores. On a shared-vCPU instance with limited core availability, Dragonfly may underperform relative to its own published benchmarks.
    - **Lux feature set:** Lux does not offer built-in persistence or semantic search. It is included as a throughput reference point only.
    - **Snapshot in time:** Docker image tags (especially `latest`) can change. Results reflect the versions pulled on the test date.

These numbers are useful for directional comparison. For absolute performance targets in your environment, reproduce the benchmark on your own hardware (see [How to reproduce](#how-to-reproduce)).

---

## Results

### Scenario throughput (ops/sec)

4 threads, 50 clients, 100,000 requests per scenario.

| Scenario | Agentis Memory | Redis 7.4 | Dragonfly | Lux |
|---|---|---|---|---|
| Strings (SET/GET) | **168,628** | 123,657 | 135,763 | 200,020 |
| Hashes | 93,659 | 89,726 | **95,298** | 103,732 |
| Lists | 90,736 | 90,256 | 93,619 | **103,702** |
| Sorted Sets | 90,373 | 88,574 | **94,841** | 101,900 |
| Sets | 91,907 | 86,994 | 96,794 | **102,978** |
| Mixed Workload | **167,782** | 120,222 | 136,993 | 194,564 |

Bold indicates the highest result among Agentis Memory, Redis, and Dragonfly. Lux wins every row outright.

!!! note "Hashes, Lists, Sorted Sets, Sets"
    Agentis Memory currently implements Hashes, Lists, Sorted Sets, and Sets as a Redis-compatible layer for protocol compatibility. These data structures are not the primary use case — agents primarily use Strings (`SET`/`GET`) and the memory commands (`MEMSAVE`/`MEMQUERY`). The throughput gap on these types relative to Dragonfly and Lux is expected and not a priority concern.

### Pipeline throughput (ops/sec)

Pipeline depth measures throughput when clients send multiple commands without waiting for a response between each one. High pipeline depth is a strong indicator of single-connection throughput capacity.

| Pipeline Depth | Agentis Memory | Redis 7.4 | Dragonfly | Lux |
|---|---|---|---|---|
| 1 | 176,127 | 120,989 | 143,319 | 203,504 |
| 10 | 878,633 | 868,078 | 458,457 | 1,314,386 |
| 50 | 2,254,000 | 1,633,373 | 1,005,030 | 3,267,978 |
| 100 | 3,193,729 | 1,862,750 | 1,622,856 | 4,750,323 |

---

## Analysis

### vs. Redis 7.4

Agentis Memory is consistently faster than Redis across all scenarios:

- **1.36x faster** on Strings (SET/GET): 168,628 vs 123,657 ops/sec
- **1.40x faster** on Mixed Workload: 167,782 vs 120,222 ops/sec
- **1.71x faster** at pipeline depth 100: 3.19M vs 1.86M ops/sec

This is a meaningful result because any Redis client (redis-py, Jedis, Lettuce, redis-cli) works with Agentis Memory without modification. Migrating from Redis for agent workloads gives a throughput improvement without a client change.

### vs. Dragonfly

Agentis Memory beats Dragonfly on Strings and Mixed Workload, which are the dominant patterns for agent KV usage. Dragonfly has an edge on collection types (Hashes, Sets, Sorted Sets), but underperforms significantly on pipeline depth — at depth 50 and 100, Agentis Memory is roughly 2.2x faster than Dragonfly.

Dragonfly's pipeline numbers are notably lower than Redis on this hardware, likely due to its multi-threaded architecture not scaling well on a shared-vCPU host with limited core availability.

### vs. Lux

Lux (written in Rust) is the fastest server in the group across all scenarios. Agentis Memory does not beat Lux on raw throughput. If maximum KV throughput is the only requirement and semantic search is not needed, Lux is a strong choice.

However, Lux does not offer:

- Built-in text embedding and vector search (`MEMSAVE` / `MEMQUERY`)
- Persistence (AOF + snapshots)
- A path to agent memory primitives

Agentis Memory's throughput is competitive enough that for agent workloads — which spend significant time on embedding and retrieval — the KV layer is not the bottleneck.

### Summary

!!! success "Key takeaways"
    - Agentis Memory is **faster than Redis** on the workloads that matter most for agents (Strings, Mixed).
    - Agentis Memory **scales well under pipelining**, reaching 3.2M ops/sec at depth 100.
    - Lux is faster on raw throughput but offers no semantic search or persistence.
    - Agentis Memory is the **only server in this comparison** that bundles text embedding and vector search in the same process, with no external dependencies.

---

## How to reproduce

Benchmark scripts and configuration live in the `benchmark/` directory of the repository.

```bash
git clone https://github.com/scrobot/agentis-memory.git
cd agentis-memory/benchmark
```

The directory contains:

- `docker-compose.yml` — starts Agentis Memory, Redis, Dragonfly, Lux, and memtier in containers
- `run.sh` — runs all memtier scenarios on a remote server and collects results
- `report.sh` — regenerates the HTML report from downloaded results
- `bench_20260330_165644/` — raw output and HTML report from the 2026-03-30 run

To run:

```bash
# Run the full benchmark suite on a remote server
./benchmark/run.sh --ssh user@host

# Results are downloaded to bench_<timestamp>/
# Open bench_<timestamp>/report/report.html for interactive charts

# Re-generate report from existing results
./benchmark/report.sh bench_<timestamp>/
```

!!! tip "Reproducing on your own hardware"
    Results on dedicated hardware (bare metal or reserved-CPU VMs) will be higher than the shared-vCPU numbers above. Running on the same machine you plan to deploy on gives the most relevant numbers for your use case.

---

## Semantic memory performance

The benchmark above covers KV throughput only. Semantic memory (`MEMSAVE` / `MEMQUERY`) performance depends on the embedding model and HNSW index configuration.

| Operation | Typical latency | Notes |
|---|---|---|
| `MEMSAVE` (response) | < 0.1ms | Returns `+OK` immediately; embedding runs async |
| Embedding per chunk | 5–10ms | ~200 tokens, all-MiniLM-L6-v2, CPU inference |
| `MEMQUERY` (10K chunks) | 10–20ms | Embed query + HNSW search + namespace filter |

Formal `MEMSAVE` / `MEMQUERY` throughput and recall benchmarks (including HNSW recall vs brute-force) are in progress and will be added here when available.
