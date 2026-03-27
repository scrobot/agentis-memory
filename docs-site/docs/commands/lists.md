# List Commands

Redis lists — ordered sequences of strings. Useful for agent conversation history, task queues, or event logs.

## LPUSH

```
LPUSH key value [value ...]
```

Insert values at the head of the list. Returns the list length after insertion.

```bash
redis-cli -p 6399 LPUSH agent:history "completed auth refactor" "started API review"
```

## RPUSH

```
RPUSH key value [value ...]
```

Insert values at the tail of the list.

## LPOP

```
LPOP key
```

Remove and return the first element.

## RPOP

```
RPOP key
```

Remove and return the last element.

## LRANGE

```
LRANGE key start stop
```

Get a range of elements (0-based, inclusive). Use `-1` for the last element.

```bash
redis-cli -p 6399 LRANGE agent:history 0 -1
```

## LINDEX

```
LINDEX key index
```

Get the element at a specific index.

## LLEN

```
LLEN key
```

Get the list length.

## LSET

```
LSET key index value
```

Set the value at a specific index.

## LREM

```
LREM key count value
```

Remove `count` occurrences of `value`. If count > 0, removes from head; if count < 0, from tail; if count = 0, removes all.

## LINSERT

```
LINSERT key BEFORE|AFTER pivot value
```

Insert a value before or after an existing element.

## LTRIM

```
LTRIM key start stop
```

Trim the list to the specified range.

```bash
# Keep only the last 100 entries
redis-cli -p 6399 LTRIM agent:history 0 99
```
