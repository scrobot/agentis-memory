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

- Docker with Docker Compose v2
- Python 3.11+ (for visualization and agentis-only benchmarks)
- ~4 GB RAM free (all four servers + memtier container)

> **Note:** Building Lux from source takes several minutes on first run.
> Subsequent runs use the cached Docker image layer.

## Quick Start

```bash
cd benchmark

# Full run: build servers, run all scenarios, generate report
./run.sh

# Open the report
open reports/report.html
```

## Manual Steps

```bash
# 1. Start all servers
docker compose up -d --build

# 2. Run a single scenario manually
docker compose exec memtier memtier_benchmark \
  -s redis -p 6379 \
  --protocol=resp2 --threads=4 --clients=50 --requests=100000 \
  --ratio=1:10 --data-size=256 --hide-histogram

# 3. Generate report from existing results
cd visualize
pip install -r requirements.txt
python generate_report.py ../results/ ../reports/

# 4. Tear down
docker compose down
```

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
