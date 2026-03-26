# Key Commands

Commands that operate on keys regardless of their data type.

---

## EXPIRE (implemented)

**Syntax:** `EXPIRE key seconds`
**Return:** Integer — 1 if timeout set, 0 if key does not exist

## TTL (implemented)

**Syntax:** `TTL key`
**Return:** Integer — remaining TTL in seconds. -1 = no TTL. -2 = key does not exist.

## KEYS (implemented)

**Syntax:** `KEYS pattern`
**Return:** Array — matching keys. Glob-match: `*`, `?`, `[abc]`.
**Warning:** Debug only. Blocks event loop on large datasets. Prefer SCAN.

## SCAN (implemented)

**Syntax:** `SCAN cursor [MATCH pattern] [COUNT n]`
**Return:** Array — `[next_cursor, [key1, key2, ...]]`. Cursor `"0"` = start/complete.

## TYPE (implemented)

**Syntax:** `TYPE key`
**Return:** Simple String — `string`, `list`, `set`, `zset`, `hash`, or `none`
**Note:** Must return correct type for all supported data types now.

## RENAME

**Syntax:** `RENAME key newkey`
**Return:** Simple String — `OK`
**Errors:** ERR no such key
**Note:** Overwrites newkey if it exists. Preserves type and TTL.

## RENAMENX

**Syntax:** `RENAMENX key newkey`
**Return:** Integer — 1 if renamed, 0 if newkey already exists
**Errors:** ERR no such key

## PERSIST

**Syntax:** `PERSIST key`
**Return:** Integer — 1 if timeout removed, 0 if key has no timeout or does not exist

## PEXPIRE

**Syntax:** `PEXPIRE key milliseconds`
**Return:** Integer — 1 if timeout set, 0 if key does not exist
**Note:** Millisecond precision. Internally convert to expireAt timestamp.

## PTTL

**Syntax:** `PTTL key`
**Return:** Integer — remaining TTL in milliseconds. -1 = no TTL. -2 = key does not exist.

## RANDOMKEY

**Syntax:** `RANDOMKEY`
**Return:** Bulk String — random key, or nil if database is empty

## UNLINK

**Syntax:** `UNLINK key [key ...]`
**Return:** Integer — number of keys unlinked
**Note:** Semantically identical to DEL for our implementation. In Redis, UNLINK does async deletion — for us, ConcurrentHashMap.remove() is already fast enough.

## OBJECT

**Subcommands:**
- `OBJECT ENCODING key` — return encoding. Stub values: `hashtable` for hash, `linkedlist` for list, `skiplist` for zset, `hashtable` for set, `embstr`/`raw` for string.
- `OBJECT HELP` — return help text
- `OBJECT REFCOUNT key` — always return 1
- `OBJECT IDLETIME key` — return 0
- `OBJECT FREQ key` — return 0
