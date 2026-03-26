# Agentis Memory — Command Reference

Every Redis command is documented here — both supported and unsupported. Each file contains exact syntax, return values, and error cases.

## Supported Commands

### Currently Implemented

- [strings-basic.md](strings-basic.md) — SET, GET, DEL, EXISTS, PING, QUIT, ECHO
- [keys-basic.md](keys-basic.md) — EXPIRE, TTL, KEYS, SCAN, TYPE
- [server.md](server.md) — AUTH, INFO, DBSIZE, CONFIG, CLIENT, COMMAND, BGSAVE
- [custom.md](custom.md) — MEMSAVE, MEMQUERY, MEMDEL, MEMSTATUS

### To Implement

- [strings-extended.md](strings-extended.md) — MSET, MGET, INCR, DECR, INCRBY, DECRBY, INCRBYFLOAT, APPEND, STRLEN, SETNX, GETSET, GETDEL, GETEX (13 commands)
- [hashes.md](hashes.md) — HSET, HGET, HGETALL, HDEL, HEXISTS, HKEYS, HVALS, HLEN, HMSET, HMGET, HINCRBY, HINCRBYFLOAT, HSETNX, HSCAN (14 commands)
- [lists.md](lists.md) — LPUSH, RPUSH, LPOP, RPOP, LLEN, LRANGE, LINDEX, LSET, LREM, LINSERT, LTRIM (11 commands)
- [sorted-sets.md](sorted-sets.md) — ZADD, ZREM, ZSCORE, ZRANK, ZREVRANK, ZRANGE, ZREVRANGE, ZRANGEBYSCORE, ZREVRANGEBYSCORE, ZCARD, ZCOUNT, ZINCRBY, ZSCAN, ZRANGEBYLEX (14 commands)
- [sets.md](sets.md) — SADD, SREM, SMEMBERS, SISMEMBER, SCARD, SRANDMEMBER, SPOP, SUNION, SINTER, SDIFF, SSCAN (11 commands)
- [keys-basic.md](keys-basic.md) (extensions) — RENAME, RENAMENX, PERSIST, PEXPIRE, PTTL, RANDOMKEY, UNLINK, OBJECT (8 commands)
- [server.md](server.md) (extensions) — SELECT, FLUSHDB, FLUSHALL, ECHO, TIME (5 commands)

## Not Supported

Full specifications for unsupported commands are documented for reference. All return `-ERR unsupported command '<name>'`.

- [pubsub.md](pubsub.md) — SUBSCRIBE, UNSUBSCRIBE, PUBLISH, PSUBSCRIBE, PUNSUBSCRIBE, PUBSUB (6 commands)
- [transactions.md](transactions.md) — MULTI, EXEC, DISCARD, WATCH, UNWATCH (5 commands)
- [scripting.md](scripting.md) — EVAL, EVALSHA, EVALRO, EVALSHA_RO, SCRIPT EXISTS/FLUSH/LOAD/KILL, FUNCTION * (10+ commands)
- [streams.md](streams.md) — XADD, XREAD, XRANGE, XREVRANGE, XLEN, XINFO, XGROUP, XACK, XCLAIM, XAUTOCLAIM, XDEL, XTRIM, XPENDING, XSETID (14+ commands)
- [hyperloglog.md](hyperloglog.md) — PFADD, PFCOUNT, PFMERGE (3 commands)
- [geo.md](geo.md) — GEOADD, GEODIST, GEOHASH, GEOPOS, GEORADIUS, GEORADIUSBYMEMBER, GEOSEARCH, GEOSEARCHSTORE (8 commands)
- [bitmap.md](bitmap.md) — SETBIT, GETBIT, BITCOUNT, BITOP, BITPOS, BITFIELD, BITFIELD_RO (7 commands)
- [cluster.md](cluster.md) — CLUSTER *, READONLY, READWRITE (15+ commands)
- [acl.md](acl.md) — ACL CAT/SETUSER/DELUSER/GETUSER/LIST/USERS/WHOAMI/LOG/LOAD/SAVE/GENPASS/DRYRUN (12 commands)

## Total Command Count

| Category | Supported | To Implement | Not Supported |
|---|---|---|---|
| Strings | 7 | 13 | — |
| Keys | 5 | 8 | — |
| Hashes | — | 14 | — |
| Lists | — | 11 | — |
| Sorted Sets | — | 14 | — |
| Sets | — | 11 | — |
| Server | 7 | 5 | — |
| Custom (Agentis) | 4 | — | — |
| Pub/Sub | — | — | 6 |
| Transactions | — | — | 5 |
| Scripting | — | — | 10+ |
| Streams | — | — | 14+ |
| HyperLogLog | — | — | 3 |
| Geo | — | — | 8 |
| Bitmap | — | — | 7 |
| Cluster | — | — | 15+ |
| ACL | — | — | 12 |
| **Total** | **23** | **76** | **80+** |

## Implementation Notes

- **WRONGTYPE error** text: `WRONGTYPE Operation against a key holding the wrong kind of value`
- All **SCAN-family** commands: cursor `"0"` = complete. Duplicates are possible. Full iteration retrieves every element present from start to end that was not deleted.
- **Nil returns** in RESP2: `$-1\r\n` (bulk string nil) or `*-1\r\n` (array nil)
- **Negative indices** in list commands: -1 = last element, -2 = second to last, etc.
- **Score boundaries** in sorted sets: `+inf`, `-inf` are valid. Prefix `(` = exclusive (e.g., `(1.5` means > 1.5).
- **Deprecated commands** (HMSET, ZREVRANGE, ZRANGEBYSCORE, ZREVRANGEBYSCORE, GETSET, ZRANGEBYLEX): still supported in all Redis versions, we implement them for compatibility.
- **Empty collections → delete key**: when a hash, list, set, or sorted set becomes empty after element removal, the key itself should be deleted (Redis behavior).
