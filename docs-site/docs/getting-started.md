# Getting Started

Get Agentis Memory running and store your first semantic memory in under a minute.

## 1. Start the server

=== "Docker (recommended)"

    ```bash
    git clone https://github.com/scrobot/agentis-memory.git
    cd agentis-memory
    docker compose up -d
    ```

=== "Native binary"

    ```bash
    ./agentis-memory --port 6399 --data-dir ./data
    ```

## 2. Verify it's running

```bash
redis-cli -p 6399 PING
# → PONG
```

!!! tip
    No `redis-cli`? Use any Redis client, or raw TCP:
    ```bash
    exec 3<>/dev/tcp/localhost/6399
    printf '*1\r\n$4\r\nPING\r\n' >&3
    read -t 2 line <&3 && echo "$line"
    exec 3>&-
    ```

## 3. Save to memory

```bash
redis-cli -p 6399 MEMSAVE "myagent:fact:stack" "We use Python 3.12 with FastAPI and PostgreSQL"
# → OK
```

`MEMSAVE` stores the text and kicks off background embedding. The `OK` response is immediate — embedding (5–10ms per chunk) happens asynchronously.

## 4. Search by meaning

```bash
redis-cli -p 6399 MEMQUERY myagent "what database do we use" 5
# → 1) 1) "myagent:fact:stack"
#      2) "We use Python 3.12 with FastAPI and PostgreSQL"
#      3) "0.87"
```

`MEMQUERY` embeds your natural language query and returns the most semantically similar results, ranked by cosine similarity.

## 5. Use standard Redis commands

All standard Redis commands work alongside memory commands:

```bash
redis-cli -p 6399 SET session:token "eyJ..."
redis-cli -p 6399 HSET myagent:config model gpt-4 temperature 0.7
redis-cli -p 6399 LPUSH myagent:history "completed auth refactor"
redis-cli -p 6399 DBSIZE
```

## Key naming

Keys follow the pattern `namespace:category:topic`:

```bash
# Private to this agent
redis-cli -p 6399 MEMSAVE "myagent:fact:deadline" "Release deadline is April 15"
redis-cli -p 6399 MEMSAVE "myagent:pref:style" "User prefers functional style"
redis-cli -p 6399 MEMSAVE "myagent:fix:oom" "OOM was caused by unbounded cache"

# Shared across all agents
redis-cli -p 6399 MEMSAVE "shared:policy:deploy" "All deploys go through staging first"

# Search within a namespace
redis-cli -p 6399 MEMQUERY myagent "deployment" 5

# Search across all namespaces
redis-cli -p 6399 MEMQUERY ALL "deployment" 5
```

## What's next

- [Memory Commands](commands/memory.md) — full reference for `MEMSAVE`, `MEMQUERY`, `MEMSTATUS`, `MEMDEL`
- [Configuration](configuration.md) — ports, memory limits, persistence, HNSW tuning
- [Integrations](integrations/redis-py.md) — connect from Python, TypeScript, Go, or any Redis client
- [Deployment](deployment.md) — Docker Compose, production setup, monitoring
