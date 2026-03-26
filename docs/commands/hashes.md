# Hash Commands

Storage: `ConcurrentHashMap<String, ConcurrentHashMap<String, byte[]>>` per hash key.

TYPE returns `hash` for hash keys.

---

## HSET

**Syntax:** `HSET key field value [field value ...]`
**Return:** Integer — number of fields that were *added* (not updated)
**Errors:** WRONGTYPE if key holds a non-hash value
**Description:** Sets one or more field-value pairs in the hash. Creates the hash if it does not exist.

## HGET

**Syntax:** `HGET key field`
**Return:** Bulk String — value of the field, or nil if field/key does not exist
**Errors:** WRONGTYPE if key holds a non-hash value

## HGETALL

**Syntax:** `HGETALL key`
**Return:** Array — flat list `[field1, value1, field2, value2, ...]`, or empty array if key does not exist
**Errors:** WRONGTYPE if key holds a non-hash value

## HDEL

**Syntax:** `HDEL key field [field ...]`
**Return:** Integer — number of fields removed (ignores non-existing)
**Errors:** WRONGTYPE if key holds a non-hash value
**Note:** If all fields are removed, the key itself should be deleted.

## HEXISTS

**Syntax:** `HEXISTS key field`
**Return:** Integer — 1 if field exists, 0 otherwise
**Errors:** WRONGTYPE if key holds a non-hash value

## HKEYS

**Syntax:** `HKEYS key`
**Return:** Array — all field names, or empty array
**Errors:** WRONGTYPE if key holds a non-hash value

## HVALS

**Syntax:** `HVALS key`
**Return:** Array — all values, or empty array
**Errors:** WRONGTYPE if key holds a non-hash value

## HLEN

**Syntax:** `HLEN key`
**Return:** Integer — number of fields, or 0
**Errors:** WRONGTYPE if key holds a non-hash value

## HMSET

**Syntax:** `HMSET key field value [field value ...]`
**Return:** Simple String — `OK`
**Errors:** WRONGTYPE; ERR wrong number of arguments if odd number of field/value tokens
**Note:** Deprecated in favor of HSET, but must be supported for compatibility.

## HMGET

**Syntax:** `HMGET key field [field ...]`
**Return:** Array — values for each field, nil for non-existing fields
**Errors:** WRONGTYPE if key holds a non-hash value

## HINCRBY

**Syntax:** `HINCRBY key field increment`
**Return:** Integer — value after increment
**Errors:** WRONGTYPE; ERR if field value is not an integer; ERR on 64-bit overflow
**Note:** Creates field with value 0 if it does not exist before incrementing.

## HINCRBYFLOAT

**Syntax:** `HINCRBYFLOAT key field increment`
**Return:** Bulk String — value after increment (string representation of float)
**Errors:** WRONGTYPE; ERR if value or increment is not a valid float
**Note:** Creates field with value 0 if it does not exist.

## HSETNX

**Syntax:** `HSETNX key field value`
**Return:** Integer — 1 if set, 0 if field already exists
**Errors:** WRONGTYPE if key holds a non-hash value

## HSCAN

**Syntax:** `HSCAN key cursor [MATCH pattern] [COUNT count]`
**Return:** Array — `[next_cursor, [field1, value1, field2, value2, ...]]`. Cursor `"0"` = iteration complete.
**Errors:** WRONGTYPE if key holds a non-hash value
**Implementation:** Snapshot fields, offset-based cursor (same approach as SCAN for keys).
