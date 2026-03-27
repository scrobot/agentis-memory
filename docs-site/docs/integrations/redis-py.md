# Python (redis-py)

Connect to Agentis Memory using the standard [redis-py](https://github.com/redis/redis-py) library.

## Install

```bash
pip install redis
```

## Connect

```python
import redis

mem = redis.Redis(host="localhost", port=6399, decode_responses=True)

# With auth
mem = redis.Redis(host="localhost", port=6399, password="mysecret", decode_responses=True)
```

## Memory commands

```python
# Save to semantic memory
mem.execute_command("MEMSAVE", "agent:fact:stack", "Project uses FastAPI with PostgreSQL")

# Search by meaning
results = mem.execute_command("MEMQUERY", "agent", "what database do we use", "5")
# → [["agent:fact:stack", "Project uses FastAPI with PostgreSQL", "0.94"], ...]

# Check indexing status
status = mem.execute_command("MEMSTATUS", "agent:fact:stack")
# → ["indexed", 1, 384, 1711451234567]

# Delete
mem.execute_command("MEMDEL", "agent:fact:stack")
```

## Standard commands

```python
# Strings
mem.set("session:id", "abc-123")
value = mem.get("session:id")

# Hashes
mem.hset("agent:config", mapping={"model": "gpt-4", "temperature": "0.7"})
config = mem.hgetall("agent:config")

# Lists
mem.lpush("agent:history", "completed auth refactor")
history = mem.lrange("agent:history", 0, -1)

# Sets
mem.sadd("agent:tags", "python", "fastapi")
tags = mem.smembers("agent:tags")

# Expiry
mem.set("cache:response", "...", ex=3600)  # expires in 1 hour
```

## Async (redis-py with asyncio)

```python
import redis.asyncio as aioredis

mem = aioredis.Redis(host="localhost", port=6399, decode_responses=True)

await mem.execute_command("MEMSAVE", "agent:fact:db", "Uses PostgreSQL 16")
results = await mem.execute_command("MEMQUERY", "agent", "database", "5")

await mem.close()
```

## Helper class

```python
import redis


class AgentisMemory:
    def __init__(self, host="localhost", port=6399, password=None, namespace="default"):
        self.client = redis.Redis(host=host, port=port, password=password, decode_responses=True)
        self.ns = namespace

    def save(self, category: str, topic: str, text: str):
        key = f"{self.ns}:{category}:{topic}"
        self.client.execute_command("MEMSAVE", key, text)

    def query(self, query: str, count: int = 5, namespace: str = None):
        ns = namespace or self.ns
        return self.client.execute_command("MEMQUERY", ns, query, str(count))

    def status(self, category: str, topic: str):
        key = f"{self.ns}:{category}:{topic}"
        return self.client.execute_command("MEMSTATUS", key)

    def delete(self, category: str, topic: str):
        key = f"{self.ns}:{category}:{topic}"
        return self.client.execute_command("MEMDEL", key)

    def query_all(self, query: str, count: int = 5):
        return self.client.execute_command("MEMQUERY", "ALL", query, str(count))


# Usage
memory = AgentisMemory(namespace="myagent")
memory.save("fact", "stack", "Project uses FastAPI with PostgreSQL")
results = memory.query("what database")
```
