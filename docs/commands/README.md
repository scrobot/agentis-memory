# Agentis Memory — Command Reference

## Supported Commands

Commands are organized by data type. Each command file contains the exact syntax, return values, error cases, and implementation notes.

### Currently Implemented

- [strings-basic.md](strings-basic.md) — SET, GET, DEL, EXISTS, PING, QUIT, ECHO
- [keys-basic.md](keys-basic.md) — EXPIRE, TTL, KEYS, SCAN, TYPE, RENAME, RENAMENX, PERSIST, PEXPIRE, PTTL, RANDOMKEY, UNLINK
- [server.md](server.md) — AUTH, INFO, DBSIZE, CONFIG, CLIENT, COMMAND, BGSAVE, SELECT, FLUSHDB, FLUSHALL, TIME
- [custom.md](custom.md) — MEMSAVE, MEMQUERY, MEMDEL, MEMSTATUS

### To Implement

- [strings-extended.md](strings-extended.md) — MSET, MGET, INCR, DECR, INCRBY, DECRBY, INCRBYFLOAT, APPEND, STRLEN, SETNX, GETSET, GETDEL, GETEX
- [hashes.md](hashes.md) — HSET, HGET, HGETALL, HDEL, HEXISTS, HKEYS, HVALS, HLEN, HMSET, HMGET, HINCRBY, HINCRBYFLOAT, HSETNX, HSCAN
- [lists.md](lists.md) — LPUSH, RPUSH, LPOP, RPOP, LLEN, LRANGE, LINDEX, LSET, LREM, LINSERT, LTRIM
- [sorted-sets.md](sorted-sets.md) — ZADD, ZREM, ZSCORE, ZRANK, ZREVRANK, ZRANGE, ZREVRANGE, ZRANGEBYSCORE, ZREVRANGEBYSCORE, ZCARD, ZCOUNT, ZINCRBY, ZSCAN, ZRANGEBYLEX
- [sets.md](sets.md) — SADD, SREM, SMEMBERS, SISMEMBER, SCARD, SRANDMEMBER, SPOP, SUNION, SINTER, SDIFF, SSCAN

### Not Supported (out of scope)

- **Pub/Sub:** SUBSCRIBE, UNSUBSCRIBE, PUBLISH, PSUBSCRIBE, PUNSUBSCRIBE
- **Transactions:** MULTI, EXEC, DISCARD, WATCH, UNWATCH
- **Scripting:** EVAL, EVALSHA, SCRIPT
- **Streams:** XADD, XREAD, XRANGE, XLEN, XGROUP, XACK, XCLAIM, XINFO
- **HyperLogLog:** PFADD, PFCOUNT, PFMERGE
- **Geo:** GEOADD, GEODIST, GEOHASH, GEOPOS, GEORADIUS, GEOSEARCH
- **Bitmap:** SETBIT, GETBIT, BITCOUNT, BITOP, BITPOS
- **Cluster:** CLUSTER, READONLY, READWRITE
- **ACL:** ACL CAT, ACL SETUSER, ACL DELUSER, ACL LIST, ACL LOG

## Implementation Notes

- **WRONGTYPE error** text: `WRONGTYPE Operation against a key holding the wrong kind of value`
- All **SCAN-family** commands: cursor `"0"` = complete. Duplicates are possible. Full iteration retrieves every element present from start to end that was not deleted.
- **Nil returns** in RESP2: `$-1\r\n` (bulk string nil) or `*-1\r\n` (array nil)
- **Negative indices** in list commands: -1 = last element, -2 = second to last, etc.
- **Score boundaries** in sorted sets: `+inf`, `-inf` are valid. Prefix `(` = exclusive (e.g., `(1.5` means > 1.5).
- **Deprecated commands** (HMSET, ZREVRANGE, ZRANGEBYSCORE, ZREVRANGEBYSCORE, GETSET, ZRANGEBYLEX): still supported in all Redis versions, we implement them for compatibility.
