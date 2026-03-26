# Set Commands

Storage: `ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<String, Boolean>>` or `ConcurrentHashMap<String, Set<byte[]>>` per set key.

TYPE returns `set` for set keys.

---

## SADD

**Syntax:** `SADD key member [member ...]`
**Return:** Integer — number of members added (excludes already-existing)
**Errors:** WRONGTYPE if key holds a non-set value
**Note:** Creates set if key does not exist.

## SREM

**Syntax:** `SREM key member [member ...]`
**Return:** Integer — number of members removed (ignores non-existing)
**Errors:** WRONGTYPE
**Note:** If set becomes empty, delete key.

## SMEMBERS

**Syntax:** `SMEMBERS key`
**Return:** Array — all members, or empty array
**Errors:** WRONGTYPE

## SISMEMBER

**Syntax:** `SISMEMBER key member`
**Return:** Integer — 1 if member exists, 0 otherwise
**Errors:** WRONGTYPE

## SCARD

**Syntax:** `SCARD key`
**Return:** Integer — number of members, or 0
**Errors:** WRONGTYPE

## SRANDMEMBER

**Syntax:** `SRANDMEMBER key [count]`
**Return:** Without count: Bulk String or nil. With positive count: Array of up to `count` distinct members. With negative count: Array of `abs(count)` members (may contain duplicates).
**Errors:** WRONGTYPE

## SPOP

**Syntax:** `SPOP key [count]`
**Return:** Without count: Bulk String or nil. With count: Array of removed members.
**Errors:** WRONGTYPE
**Note:** If set becomes empty, delete key.

## SUNION

**Syntax:** `SUNION key [key ...]`
**Return:** Array — union of all sets. Non-existing keys = empty set.
**Errors:** WRONGTYPE if any key holds wrong type

## SINTER

**Syntax:** `SINTER key [key ...]`
**Return:** Array — intersection of all sets. Non-existing key = empty result.
**Errors:** WRONGTYPE if any key holds wrong type

## SDIFF

**Syntax:** `SDIFF key [key ...]`
**Return:** Array — members in first set not in any subsequent sets
**Errors:** WRONGTYPE if any key holds wrong type

## SSCAN

**Syntax:** `SSCAN key cursor [MATCH pattern] [COUNT count]`
**Return:** Array — `[next_cursor, [member1, member2, ...]]`. Cursor `"0"` = complete.
**Errors:** WRONGTYPE
