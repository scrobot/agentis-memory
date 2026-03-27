# Deployment

## Docker Compose (recommended)

### Development

```bash
git clone https://github.com/scrobot/agentis-memory.git
cd agentis-memory
docker compose up -d
```

This builds the image locally and starts Agentis Memory on port 6399, with Redis Insight on port 5540.

### Production

Use the pre-built image from Docker Hub:

```yaml title="docker-compose.production.yml"
services:
  agentis-memory:
    image: agentismemory/agentis-memory:latest
    container_name: agentis-memory
    ports:
      - "6399:6399"
    volumes:
      - agentis-data:/data
    environment:
      - AGENTIS_OPTS=--requirepass ${AGENTIS_PASSWORD:-}
    healthcheck:
      test: ["CMD-SHELL", "exec 3<>/dev/tcp/localhost/6399 && printf '*1\\r\\n$$4\\r\\nPING\\r\\n' >&3 && read -t 2 line <&3 && [[ \"$$line\" == *PONG* ]]"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 1G

  redis-insight:
    image: redis/redisinsight:2.66
    container_name: redis-insight
    ports:
      - "5540:5540"
    depends_on:
      agentis-memory:
        condition: service_healthy
    restart: unless-stopped

volumes:
  agentis-data:
```

```bash
AGENTIS_PASSWORD=mysecret docker compose -f docker-compose.production.yml up -d
```

## Native binary

```bash
# Download (when releases are available)
curl -LO https://github.com/scrobot/agentis-memory/releases/latest/download/agentis-memory-$(uname -s | tr A-Z a-z)-$(uname -m)
chmod +x agentis-memory-*
./agentis-memory-* --port 6399 --data-dir /var/lib/agentis-memory

# Or build from source
./gradlew nativeCompile
./build/native/nativeCompile/agentis-memory --port 6399
```

## Healthcheck

Use `PING` for health checks:

```bash
redis-cli -p 6399 PING
# → PONG (healthy)
# → -LOADING server is loading data (starting up, recovering from AOF)
# → connection refused (not running)
```

## Monitoring

Connect Redis Insight to `localhost:6399` for a GUI dashboard. The `INFO` command returns server statistics:

```bash
redis-cli -p 6399 INFO
```

Key metrics:

| Section | Metric | Description |
|---|---|---|
| `server` | `uptime_in_seconds` | Server uptime |
| `clients` | `connected_clients` | Current client connections |
| `memory` | `used_memory` | KV store memory usage |
| `stats` | `total_commands_processed` | Total commands handled |
| `keyspace` | `db0:keys` | Total key count |

## Resource planning

| Workload | Memory | CPU | Storage |
|---|---|---|---|
| Small (< 10K keys, < 50K chunks) | 512MB | 1 core | 1GB |
| Medium (< 100K keys, < 500K chunks) | 2GB | 2 cores | 10GB |
| Large (< 1M keys, < 5M chunks) | 8GB | 4 cores | 50GB |

Formula: `RSS ≈ --max-memory + (chunk_count × 1.5KB) + 300MB`

## Data backup

### Snapshots

Snapshots are stored in `--data-dir`. Back them up with standard filesystem tools:

```bash
# Manual snapshot
redis-cli -p 6399 BGSAVE

# Copy snapshot files
cp /data/agentis-*.snapshot /backup/
```

### AOF

The AOF file is also in `--data-dir`. For a consistent backup, trigger a snapshot first, then copy both snapshot and AOF files.

## Graceful shutdown

Send `SIGTERM` or `SIGINT`. The server will:

1. Stop accepting new connections
2. Drain in-flight commands (5s timeout)
3. Cancel pending embedding jobs
4. Flush AOF
5. Write final snapshots
6. Exit cleanly

Pending MEMSAVE operations are recovered from AOF on next startup.
