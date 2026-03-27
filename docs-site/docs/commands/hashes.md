# Hash Commands

Redis hashes — maps of field-value pairs stored under a single key. Useful for storing structured agent configuration, session state, or metadata.

## HSET

```
HSET key field value [field value ...]
```

Set one or more field-value pairs. Returns the number of new fields added.

```bash
redis-cli -p 6399 HSET agent:config model gpt-4 temperature 0.7 max_tokens 4096
```

## HGET

```
HGET key field
```

Get the value of a field.

## HDEL

```
HDEL key field [field ...]
```

Delete one or more fields. Returns the number of fields removed.

## HGETALL

```
HGETALL key
```

Get all field-value pairs as a flat array: `[field1, value1, field2, value2, ...]`.

## HEXISTS

```
HEXISTS key field
```

Returns `1` if the field exists, `0` otherwise.

## HKEYS

```
HKEYS key
```

Get all field names.

## HVALS

```
HVALS key
```

Get all values.

## HLEN

```
HLEN key
```

Get the number of fields.

## HMGET

```
HMGET key field [field ...]
```

Get values of multiple fields. Returns `nil` for non-existent fields.

## HSETNX

```
HSETNX key field value
```

Set field only if it does not exist. Returns `1` if set, `0` if field already existed.

## HINCRBY

```
HINCRBY key field increment
```

Increment the integer value of a field. Returns the new value.

## HINCRBYFLOAT

```
HINCRBYFLOAT key field increment
```

Increment the float value of a field. Returns the new value as a string.

## HSCAN

```
HSCAN key cursor [MATCH pattern] [COUNT hint]
```

Incrementally iterate field-value pairs.
