# Configuration

Agentis Memory is configured via CLI flags or an optional config file.

## CLI flags

| Flag | Default | Description |
|---|---|---|
| `--port` | `6399` | TCP listen port |
| `--bind` | `127.0.0.1` | Bind address. Localhost only by default — use `0.0.0.0` for network access |
| `--requirepass` | _(none)_ | Password for `AUTH`. If set, clients must authenticate before any command |
| `--data-dir` | `./data` | Directory for AOF logs and snapshots |
| `--max-memory` | `256mb` | Max KV value bytes. Accepts `mb`, `gb` suffixes |
| `--max-value-size` | `1mb` | Max size of a single value (SET and MEMSAVE) |
| `--max-chunks-per-key` | `100` | Max chunks from one MEMSAVE. Exceeding returns an error |
| `--eviction-policy` | `volatile-lru` | Eviction strategy when max-memory is reached |
| `--aof-enabled` / `--no-aof` | `true` | Enable/disable AOF persistence |
| `--aof-fsync` | `everysec` | AOF fsync: `always`, `everysec`, or `no` |
| `--snapshot-interval` | `300` | Seconds between auto-snapshots (0 = disabled) |
| `--snapshot-after-changes` | `1000` | Trigger snapshot after N write operations |
| `--embedding-threads` | `2` | Thread count for ONNX embedding inference |
| `--model-path` | _(bundled)_ | Path to custom ONNX model directory |
| `--hnsw-m` | `16` | HNSW M parameter (connections per node) |
| `--hnsw-ef-construction` | `100` | HNSW efConstruction (build-time search depth) |
| `--log-level` | `info` | Log level: `trace`, `debug`, `info`, `warn`, `error` |

## Config file

Optional file in Redis-style key-value format. Pass via `--config` or place as `agentis-memory.conf` in the working directory.

```title="agentis-memory.conf"
port 6399
bind 127.0.0.1
requirepass mysecretpassword
data-dir ./data
max-memory 512mb
max-value-size 1mb
aof-fsync everysec
snapshot-interval 300
snapshot-after-changes 1000
embedding-threads 4
hnsw-m 16
hnsw-ef-construction 200
log-level info
```

CLI flags take precedence over config file values.

## Tuning guide

### Memory

`--max-memory` governs KV value bytes only. Total process RSS is higher:

```
Total RSS ≈ --max-memory + (chunk_count × 1.5KB) + 300MB baseline
```

The 300MB baseline covers the ONNX model (~200MB), Netty, and JVM overhead.

**Example:** 256MB max-memory + 100K chunks (150MB) + 300MB baseline ≈ 706MB RSS.

### Persistence

| Scenario | Recommended settings |
|---|---|
| Maximum durability | `--aof-fsync always` |
| Balanced (default) | `--aof-fsync everysec --snapshot-interval 300` |
| Maximum throughput | `--aof-fsync no` or `--no-aof` (data loss risk) |
| Cache only (no persistence) | `--no-aof --snapshot-interval 0` |

### HNSW index

| Parameter | Effect of increasing |
|---|---|
| `--hnsw-m` | Higher recall, more memory, slower inserts |
| `--hnsw-ef-construction` | Higher recall during build, slower inserts |

Defaults (`m=16`, `efConstruction=100`) work well for up to ~1M vectors. Increase `m` to 32 and `efConstruction` to 200 for larger indexes or when recall quality matters more than insert speed.

### Embedding

`--embedding-threads` controls ONNX inference parallelism. Set to the number of CPU cores you can dedicate to embedding. The default (2) is conservative — increase for high-throughput MEMSAVE workloads.

## Environment variables

Configuration can also be passed via `AGENTIS_OPTS` environment variable (useful for Docker):

```bash
docker run -e AGENTIS_OPTS="--requirepass secret --max-memory 512mb" agentismemory/agentis-memory
```

## Security

- **Default bind is `127.0.0.1`** — localhost only. Explicitly opt-in to `--bind 0.0.0.0` for network access.
- **Use `--requirepass`** in any non-local deployment.
- **Namespaces are not a security boundary.** Any authenticated client can read/write any namespace. For true tenant isolation, run separate server instances with separate passwords.
- **TLS:** not yet supported. For production network access, run behind a TLS-terminating proxy or within a private network.
