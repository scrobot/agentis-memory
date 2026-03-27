# Benchmarks

!!! note "Coming soon"
    Formal benchmarks are in progress. This page will be updated with results comparing Agentis Memory against Redis Stack, Dragonfly, and dedicated vector databases.

## What we're measuring

### KV throughput

Standard SET/GET operations per second, compared to:

- Redis 7.x
- Dragonfly
- KeyDB

### Semantic memory

- MEMSAVE throughput (end-to-end: store + chunk + embed + index)
- MEMQUERY latency (embed query + HNSW search + filter)
- Embedding latency: p50 / p95 / p99 per chunk
- HNSW recall: top-K accuracy vs brute-force

### Resource usage

- Memory footprint vs key/chunk count
- Startup time (cold start + AOF recovery)
- Native binary size

## Preliminary numbers

Based on internal testing on Apple M3 Pro:

| Operation | Latency | Notes |
|---|---|---|
| `SET` / `GET` | < 0.1ms | Single key, pipelined |
| `MEMSAVE` (response) | < 0.1ms | Synchronous `+OK`, embedding is async |
| Embedding per chunk | ~5–10ms | ~200 tokens, all-MiniLM-L6-v2 |
| `MEMQUERY` (10K index) | ~10–20ms | Embed query + HNSW search + filter |

## Run your own

```bash
# Redis benchmark tool works out of the box
redis-benchmark -p 6399 -t set,get -n 100000 -q
```
