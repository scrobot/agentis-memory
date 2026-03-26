# List Commands

Storage: `ConcurrentHashMap<String, LinkedList<byte[]>>` (or `Deque`) per list key.

TYPE returns `list` for list keys.

---

## LPUSH

**Syntax:** `LPUSH key element [element ...]`
**Return:** Integer — length of the list after push
**Errors:** WRONGTYPE if key holds a non-list value
**Note:** Multiple elements are inserted left-to-right, so `LPUSH mylist a b c` results in `c b a` at the head. Creates list if key does not exist.

## RPUSH

**Syntax:** `RPUSH key element [element ...]`
**Return:** Integer — length of the list after push
**Errors:** WRONGTYPE if key holds a non-list value
**Note:** Creates list if key does not exist.

## LPOP

**Syntax:** `LPOP key [count]`
**Return:** Without count: Bulk String or nil. With count: Array of elements, or nil/empty array.
**Errors:** WRONGTYPE; ERR if count is negative
**Note:** If list becomes empty after pop, the key should be deleted.

## RPOP

**Syntax:** `RPOP key [count]`
**Return:** Without count: Bulk String or nil. With count: Array of elements, or nil/empty array.
**Errors:** WRONGTYPE; ERR if count is negative
**Note:** If list becomes empty after pop, the key should be deleted.

## LLEN

**Syntax:** `LLEN key`
**Return:** Integer — length of list, or 0 if key does not exist
**Errors:** WRONGTYPE if key holds a non-list value

## LRANGE

**Syntax:** `LRANGE key start stop`
**Return:** Array — elements in range (inclusive both ends), or empty array. Indices 0-based, negative allowed (-1 = last).
**Errors:** WRONGTYPE if key holds a non-list value
**Note:** Out-of-range indices are clamped, not errors.

## LINDEX

**Syntax:** `LINDEX key index`
**Return:** Bulk String — element at index, or nil if out of range. Negative indices allowed.
**Errors:** WRONGTYPE if key holds a non-list value

## LSET

**Syntax:** `LSET key index element`
**Return:** Simple String — `OK`
**Errors:** WRONGTYPE; ERR index out of range; ERR no such key
**Note:** Does not create the list if key does not exist.

## LREM

**Syntax:** `LREM key count element`
**Return:** Integer — number of removed elements
**Errors:** WRONGTYPE if key holds a non-list value
**Behavior:** count > 0: remove first `count` from head. count < 0: remove first `abs(count)` from tail. count = 0: remove all.
**Note:** If list becomes empty, delete key.

## LINSERT

**Syntax:** `LINSERT key BEFORE|AFTER pivot element`
**Return:** Integer — length after insert, -1 if pivot not found, 0 if key does not exist
**Errors:** WRONGTYPE if key holds a non-list value

## LTRIM

**Syntax:** `LTRIM key start stop`
**Return:** Simple String — `OK`
**Errors:** WRONGTYPE if key holds a non-list value
**Note:** If range results in empty list, the key is deleted. Indices are clamped.
