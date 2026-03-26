# String Commands (Extended)

These extend the basic SET/GET already implemented. All operate on string-type keys.

---

## MSET

**Syntax:** `MSET key value [key value ...]`
**Return:** Simple String — always `OK` (atomic, never fails)
**Errors:** ERR wrong number of arguments if odd number of key/value tokens

## MGET

**Syntax:** `MGET key [key ...]`
**Return:** Array — values for each key, nil for non-existing or non-string keys
**Note:** Does NOT return WRONGTYPE error — non-string keys simply return nil in the array.

## INCR

**Syntax:** `INCR key`
**Return:** Integer — value after increment
**Errors:** WRONGTYPE; ERR if value is not an integer or overflows int64
**Note:** If key does not exist, sets to 0 before incrementing (result: 1).

## DECR

**Syntax:** `DECR key`
**Return:** Integer — value after decrement
**Errors:** WRONGTYPE; ERR if not integer or overflow
**Note:** If key does not exist, sets to 0 before decrementing (result: -1).

## INCRBY

**Syntax:** `INCRBY key increment`
**Return:** Integer — value after increment
**Errors:** WRONGTYPE; ERR if not integer or overflow

## DECRBY

**Syntax:** `DECRBY key decrement`
**Return:** Integer — value after decrement
**Errors:** WRONGTYPE; ERR if not integer or overflow

## INCRBYFLOAT

**Syntax:** `INCRBYFLOAT key increment`
**Return:** Bulk String — value after increment (string representation)
**Errors:** WRONGTYPE; ERR if value or increment not valid float
**Note:** If key does not exist, sets to 0 before incrementing.

## APPEND

**Syntax:** `APPEND key value`
**Return:** Integer — length of string after append
**Errors:** WRONGTYPE
**Note:** Creates key with value if it does not exist (equivalent to SET).

## STRLEN

**Syntax:** `STRLEN key`
**Return:** Integer — length of string, or 0 if key does not exist
**Errors:** WRONGTYPE

## SETNX

**Syntax:** `SETNX key value`
**Return:** Integer — 1 if set, 0 if key already exists
**Note:** Equivalent to `SET key value NX`. Does not support EX/PX options.

## GETSET

**Syntax:** `GETSET key value`
**Return:** Bulk String — old value, or nil if key did not exist
**Errors:** WRONGTYPE
**Note:** Deprecated (use `SET key value GET`), but must be supported.

## GETDEL

**Syntax:** `GETDEL key`
**Return:** Bulk String — value, or nil. Key is deleted after returning.
**Errors:** WRONGTYPE

## GETEX

**Syntax:** `GETEX key [EX seconds | PX milliseconds | EXAT timestamp | PXAT timestamp-ms | PERSIST]`
**Return:** Bulk String — value, or nil if key does not exist
**Options:**
- `EX seconds` — set TTL in seconds
- `PX milliseconds` — set TTL in milliseconds
- `EXAT timestamp` — set expire at Unix timestamp (seconds)
- `PXAT timestamp-ms` — set expire at Unix timestamp (milliseconds)
- `PERSIST` — remove existing TTL

**Errors:** WRONGTYPE; ERR if multiple options specified
