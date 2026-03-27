# String Commands

Standard Redis string operations. These store raw values — for semantic search, use [MEMSAVE](memory.md#memsave) instead.

## SET

```
SET key value [EX seconds] [PX milliseconds] [NX | XX]
```

Set a key to a string value.

| Option | Description |
|---|---|
| `EX seconds` | Set expiry in seconds |
| `PX milliseconds` | Set expiry in milliseconds |
| `NX` | Only set if key does not exist |
| `XX` | Only set if key already exists |

```bash
redis-cli -p 6399 SET mykey "hello"
redis-cli -p 6399 SET session "token" EX 3600
redis-cli -p 6399 SET lock "owner" NX
```

## GET

```
GET key
```

Get the value of a key. Returns `nil` if the key does not exist or has expired.

## DEL

```
DEL key [key ...]
```

Delete one or more keys. Returns the number of keys deleted. If a key has a vector index (created via MEMSAVE), the index entry is also removed.

## EXISTS

```
EXISTS key
```

Returns `1` if the key exists, `0` otherwise.

## APPEND

```
APPEND key value
```

Append a value to an existing key. Returns the length of the string after appending. Creates the key if it doesn't exist.

## INCR

```
INCR key
```

Increment the integer value of a key by 1. Returns the new value. Creates the key with value `1` if it doesn't exist.

## MGET

```
MGET key [key ...]
```

Get the values of multiple keys. Returns an array of values (nil for non-existent keys).

## MSET

```
MSET key value [key value ...]
```

Set multiple keys to multiple values atomically.

## SETNX

```
SETNX key value
```

Set key to value only if the key does not exist. Returns `1` if set, `0` if key already existed.

## GETSET

```
GETSET key value
```

Set key to value and return the old value. Returns `nil` if the key did not exist.

## GETDEL

```
GETDEL key
```

Get the value of a key and delete it.

## GETEX

```
GETEX key [EX seconds] [PX milliseconds] [PERSIST]
```

Get the value of a key and optionally set or remove its expiry.

## STRLEN

```
STRLEN key
```

Returns the length of the string value stored at key.

## RENAME

```
RENAME key newkey
```

Rename a key. Returns an error if the source key does not exist.

## UNLINK

```
UNLINK key [key ...]
```

Delete keys asynchronously. Same as `DEL` in behavior (Agentis Memory performs deletion inline).

## RANDOMKEY

```
RANDOMKEY
```

Return a random key from the keyspace.

## Expiry commands

### EXPIRE

```
EXPIRE key seconds
```

Set a timeout on a key in seconds. Returns `1` if set, `0` if key does not exist.

### PEXPIRE

```
PEXPIRE key milliseconds
```

Set a timeout on a key in milliseconds.

### TTL

```
TTL key
```

Get the remaining time to live of a key in seconds. Returns `-1` if no expiry, `-2` if key does not exist.

### PTTL

```
PTTL key
```

Get the remaining time to live in milliseconds.

### PERSIST

```
PERSIST key
```

Remove the expiry from a key, making it persistent. Returns `1` if the timeout was removed, `0` if the key does not exist or has no timeout.

## Iteration

### KEYS

```
KEYS pattern
```

Find all keys matching a glob pattern. Use `*` for all keys.

!!! warning
    `KEYS` scans the entire keyspace and should only be used for debugging. Use `SCAN` in production.

### SCAN

```
SCAN cursor [MATCH pattern] [COUNT hint]
```

Incrementally iterate keys. Returns `[next_cursor, [key, ...]]`. When next_cursor is `0`, iteration is complete.

```bash
redis-cli -p 6399 SCAN 0 MATCH "agent:*" COUNT 100
```
