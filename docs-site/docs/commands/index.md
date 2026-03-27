# Command Reference

Agentis Memory supports 90+ commands: 4 custom memory commands and a broad set of standard Redis commands.

## Memory commands

These are unique to Agentis Memory — semantic storage and retrieval.

| Command | Description |
|---|---|
| [`MEMSAVE key text`](memory.md#memsave) | Chunk, embed, and index text for semantic search |
| [`MEMQUERY namespace query count`](memory.md#memquery) | Search by meaning — returns results ranked by similarity |
| [`MEMSTATUS key`](memory.md#memstatus) | Check indexing status |
| [`MEMDEL key`](memory.md#memdel) | Delete from KV store and vector index |

## Standard Redis commands

| Category | Commands | Reference |
|---|---|---|
| **Strings** | `SET` `GET` `DEL` `EXISTS` `APPEND` `INCR` `MGET` `MSET` `SETNX` `GETSET` `GETDEL` `GETEX` `STRLEN` `RENAME` `UNLINK` `RANDOMKEY` | [Strings](strings.md) |
| **Hashes** | `HSET` `HGET` `HDEL` `HGETALL` `HEXISTS` `HKEYS` `HVALS` `HLEN` `HMGET` `HSETNX` `HINCRBY` `HINCRBYFLOAT` `HSCAN` | [Hashes](hashes.md) |
| **Lists** | `LPUSH` `RPUSH` `LPOP` `RPOP` `LRANGE` `LINDEX` `LLEN` `LSET` `LREM` `LINSERT` `LTRIM` | [Lists](lists.md) |
| **Sets** | `SADD` `SREM` `SMEMBERS` `SISMEMBER` `SCARD` `SPOP` `SRANDMEMBER` `SDIFF` `SINTER` `SUNION` `SSCAN` | [Sets](sets.md) |
| **Sorted sets** | `ZADD` `ZREM` `ZRANGE` `ZRANGEBYSCORE` `ZRANGEBYLEX` `ZRANK` `ZSCORE` `ZCARD` `ZCOUNT` `ZINCRBY` `ZSCAN` | [Sorted sets](sorted-sets.md) |
| **Expiry** | `EXPIRE` `PEXPIRE` `TTL` `PTTL` `PERSIST` | [Strings](strings.md) |
| **Server** | `PING` `AUTH` `INFO` `DBSIZE` `BGSAVE` `FLUSHDB` `FLUSHALL` `TIME` `HELLO` `QUIT` `SELECT` `ECHO` `CLIENT` `CONFIG` `COMMAND` `OBJECT` | [Server](server.md) |
| **Iteration** | `KEYS` `SCAN` `HSCAN` `SSCAN` `ZSCAN` | [Strings](strings.md) |

## Compatibility notes

- All commands follow standard Redis semantics unless noted otherwise
- `SELECT` accepts any database number but Agentis Memory uses a single keyspace (db 0)
- `CONFIG GET` returns a subset of configuration values
- `COMMAND` / `COMMAND DOCS` return basic metadata stubs
- `CLIENT SETNAME` / `CLIENT INFO` are supported for connection management
