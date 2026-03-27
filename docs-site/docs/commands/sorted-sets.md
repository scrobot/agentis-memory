# Sorted Set Commands

Redis sorted sets — members with associated scores, ordered by score. Useful for ranked results, priority queues, leaderboards, or time-series data.

## ZADD

```
ZADD key score member [score member ...]
```

Add members with scores. Returns the number of new members added.

```bash
redis-cli -p 6399 ZADD agent:priorities 1 "fix-auth-bug" 2 "add-logging" 3 "refactor-api"
```

## ZREM

```
ZREM key member [member ...]
```

Remove members. Returns the number removed.

## ZRANGE

```
ZRANGE key start stop [WITHSCORES]
```

Return members in a range by index (0-based, inclusive). Add `WITHSCORES` to include scores.

```bash
redis-cli -p 6399 ZRANGE agent:priorities 0 -1 WITHSCORES
```

## ZRANGEBYSCORE

```
ZRANGEBYSCORE key min max [WITHSCORES] [LIMIT offset count]
```

Return members with scores between min and max. Use `-inf` and `+inf` for unbounded.

## ZRANGEBYLEX

```
ZRANGEBYLEX key min max [LIMIT offset count]
```

Return members in a lexicographical range (all members must have the same score). Use `[` for inclusive, `(` for exclusive, `-` and `+` for unbounded.

## ZRANK

```
ZRANK key member
```

Return the rank (0-based) of a member, ordered by score ascending. Returns `nil` if member doesn't exist.

## ZSCORE

```
ZSCORE key member
```

Return the score of a member.

## ZCARD

```
ZCARD key
```

Return the number of members.

## ZCOUNT

```
ZCOUNT key min max
```

Count members with scores between min and max.

## ZINCRBY

```
ZINCRBY key increment member
```

Increment the score of a member. Returns the new score.

## ZSCAN

```
ZSCAN key cursor [MATCH pattern] [COUNT hint]
```

Incrementally iterate members and scores.
