# Agentis Memory Benchmark Suite

Compares Agentis Memory against Redis 7.4, Dragonfly, and Lux using
[memtier_benchmark](https://github.com/RedisLabs/memtier_benchmark) — the
same tool used by Redis, Dragonfly, and Garnet in their own published benchmarks.

## Targets

| Server | Port | Notes |
|---|---|---|
| **Agentis Memory** | 6399 | Local build (`../Dockerfile`) |
| **Redis 7.4** | 6379 | `redis:7.4` official image |
| **Dragonfly** | 6380 | `docker.dragonflydb.io/dragonflydb/dragonfly:latest` |
| **Lux** | 6381 | Built from `github.com/lux-db/lux` (`lux/Dockerfile`) |

## Prerequisites

**Remote server** (where benchmarks run):
- Docker with Docker Compose v2
- Python 3.11+ with pip (for report generation)
- ~4 GB RAM free (all four servers + memtier container)
- SSH access from your local machine

**Local machine:**
- `rsync` and `ssh`

> **Note:** Building Lux from source takes several minutes on first run.
> Subsequent runs use the cached Docker image layer.

## Quick Start

```bash
# Full run on remote server, results downloaded locally
./benchmark/run.sh --ssh bench@10.0.0.5

# Results appear in ./bench_20260330_141500/
open bench_*/report/report.html
```

## Options

```bash
# Only specific scenarios
./benchmark/run.sh --ssh user@host -s strings -s hashes

# Only specific servers
./benchmark/run.sh --ssh user@host -S agentis -S redis

# Only pipeline benchmarks
./benchmark/run.sh --ssh user@host --no-scenario -p 50 -p 100

# Skip warmup / report / teardown
./benchmark/run.sh --ssh user@host --no-warmup --no-report --no-teardown
```

Run `./benchmark/run.sh --help` for full usage.

## Agentis-Specific Benchmarks

MEMSAVE and MEMQUERY are not measurable with memtier. Use the dedicated scripts:

```bash
cd agentis-only
pip install -r requirements.txt

# MEMSAVE sync latency + indexation time + MEMQUERY latency
python memsave_bench.py --host localhost --port 6399

# Recall@K vs brute-force ground truth
python recall_bench.py --host localhost --port 6399 \
  --model-dir ../../models --corpus-size 500 --top-k 10
```

## Scenarios

| File | Description |
|---|---|
| `scenarios/strings.cfg` | SET/GET, ratio 1:10, 256B values, random keys |
| `scenarios/hashes.cfg` | HSET / HGET / HGETALL |
| `scenarios/lists.cfg` | LPUSH / LRANGE / LPOP |
| `scenarios/sorted-sets.cfg` | ZADD / ZRANGE / ZSCORE |
| `scenarios/sets.cfg` | SADD / SISMEMBER / SMEMBERS |
| `scenarios/mixed-workload.cfg` | SET/GET ratio 1:4, Gaussian key distribution |
| `scenarios/pipeline.cfg` | Base config for pipeline scaling (1/10/50/100) |

## Output

```
benchmark/
├── results/            # Raw memtier JSON (gitignored)
│   ├── agentis_memory/
│   ├── redis/
│   ├── dragonfly/
│   └── lux/
└── reports/            # Generated (gitignored)
    ├── report.html     # Interactive Plotly report (dark theme)
    ├── throughput.png
    ├── latency_p99.png
    └── pipeline_scaling.png
```

## Report Contents

- **Summary heatmap** — throughput ratio vs Redis (green = faster)
- **Throughput bar chart** — ops/sec per scenario, 4 servers grouped
- **Latency p99 / p95** — grouped bars per scenario
- **Pipeline scaling** — ops/sec vs pipeline depth (1, 10, 50, 100)
- **Per-scenario CDF** — approximate latency distribution
- **Raw numbers table** — all metrics with vs-Redis ratio
