# Sorted Set Commands

Storage: each sorted set needs both a score→member mapping and a member→score mapping. Suggested: `ConcurrentSkipListMap<Double, Set<String>>` for score ordering + `ConcurrentHashMap<String, Double>` for member→score lookup. Or a custom `SortedSetEntry` wrapping both.

TYPE returns `zset` for sorted set keys.

---

## ZADD

**Syntax:** `ZADD key [NX|XX] [GT|LT] [CH] [INCR] score member [score member ...]`
**Return:** Without INCR: Integer — number of elements added (or changed if CH). With INCR: Bulk String — new score, or nil if aborted.
**Options:**
- `NX` — only add new, don't update existing
- `XX` — only update existing, don't add new
- `GT` — only update when new score > current
- `LT` — only update when new score < current
- `CH` — return count of added + updated (not just added)
- `INCR` — act like ZINCRBY (one score-member pair only)

**Errors:** WRONGTYPE; ERR if score not valid float; ERR if NX+XX or NX+GT/LT combined
**Note:** Creates sorted set if key does not exist.

## ZREM

**Syntax:** `ZREM key member [member ...]`
**Return:** Integer — number of members removed
**Errors:** WRONGTYPE
**Note:** If sorted set becomes empty, delete key.

## ZSCORE

**Syntax:** `ZSCORE key member`
**Return:** Bulk String — score as string, or nil if member/key does not exist
**Errors:** WRONGTYPE

## ZRANK

**Syntax:** `ZRANK key member`
**Return:** Integer — 0-based rank (ascending score), or nil if member/key does not exist
**Errors:** WRONGTYPE

## ZREVRANK

**Syntax:** `ZREVRANK key member`
**Return:** Integer — 0-based rank (descending score), or nil
**Errors:** WRONGTYPE

## ZRANGE

**Syntax:** `ZRANGE key min max [BYSCORE|BYLEX] [REV] [LIMIT offset count] [WITHSCORES]`
**Return:** Array — members (with scores interleaved if WITHSCORES), or empty array
**Modes:**
- Default (no BYSCORE/BYLEX): min/max are 0-based indices (negative allowed)
- `BYSCORE`: min/max are score boundaries. `-inf`/`+inf` valid. `(` prefix = exclusive.
- `BYLEX`: min/max use `[value`/`(value`/`-`/`+` syntax
- `REV`: reverse order (swap min/max semantics)
- `LIMIT offset count`: pagination (only with BYSCORE/BYLEX)

**Errors:** WRONGTYPE; ERR syntax error on invalid options

## ZREVRANGE

**Syntax:** `ZREVRANGE key start stop [WITHSCORES]`
**Return:** Array — members in descending score order. Indices 0-based, negative allowed.
**Errors:** WRONGTYPE
**Note:** Deprecated (use ZRANGE with REV), but must be supported.

## ZRANGEBYSCORE

**Syntax:** `ZRANGEBYSCORE key min max [WITHSCORES] [LIMIT offset count]`
**Return:** Array — members with scores in [min, max] range, ascending order. `-inf`/`+inf` valid. `(` prefix = exclusive.
**Errors:** WRONGTYPE; ERR if min/max not valid
**Note:** Deprecated (use ZRANGE BYSCORE), but must be supported.

## ZREVRANGEBYSCORE

**Syntax:** `ZREVRANGEBYSCORE key max min [WITHSCORES] [LIMIT offset count]`
**Return:** Array — same as ZRANGEBYSCORE but descending. Note: max before min in args.
**Errors:** WRONGTYPE
**Note:** Deprecated, but must be supported.

## ZCARD

**Syntax:** `ZCARD key`
**Return:** Integer — number of members, or 0
**Errors:** WRONGTYPE

## ZCOUNT

**Syntax:** `ZCOUNT key min max`
**Return:** Integer — count of members with scores in [min, max]. `-inf`/`+inf` valid. `(` = exclusive.
**Errors:** WRONGTYPE

## ZINCRBY

**Syntax:** `ZINCRBY key increment member`
**Return:** Bulk String — new score. Creates member with increment as score if not exists.
**Errors:** WRONGTYPE; ERR if increment not valid float

## ZSCAN

**Syntax:** `ZSCAN key cursor [MATCH pattern] [COUNT count]`
**Return:** Array — `[next_cursor, [member1, score1, member2, score2, ...]]`. Cursor `"0"` = complete.
**Errors:** WRONGTYPE

## ZRANGEBYLEX

**Syntax:** `ZRANGEBYLEX key min max [LIMIT offset count]`
**Return:** Array — members in lexicographic order. min/max: `[value`/`(value`/`-`/`+`.
**Errors:** WRONGTYPE; ERR if min/max not valid range spec
**Note:** Deprecated (use ZRANGE BYLEX), but must be supported. Assumes all members have same score.
