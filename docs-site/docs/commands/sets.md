# Set Commands

Redis sets — unordered collections of unique strings. Useful for tags, capabilities, visited keys, or agent group membership.

## SADD

```
SADD key member [member ...]
```

Add members to a set. Returns the number of new members added.

```bash
redis-cli -p 6399 SADD agent:tags "python" "fastapi" "postgresql"
```

## SREM

```
SREM key member [member ...]
```

Remove members from a set. Returns the number of members removed.

## SMEMBERS

```
SMEMBERS key
```

Get all members of a set.

## SISMEMBER

```
SISMEMBER key member
```

Returns `1` if the member exists in the set, `0` otherwise.

## SCARD

```
SCARD key
```

Get the number of members in a set.

## SPOP

```
SPOP key [count]
```

Remove and return one or more random members.

## SRANDMEMBER

```
SRANDMEMBER key [count]
```

Return one or more random members without removing them.

## SDIFF

```
SDIFF key [key ...]
```

Return members in the first set that are not in any of the other sets.

## SINTER

```
SINTER key [key ...]
```

Return members that exist in all given sets.

## SUNION

```
SUNION key [key ...]
```

Return members that exist in any of the given sets.

## SSCAN

```
SSCAN key cursor [MATCH pattern] [COUNT hint]
```

Incrementally iterate set members.
