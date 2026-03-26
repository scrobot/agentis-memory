# Stream Commands

**Status: NOT SUPPORTED**

Agentis Memory does not implement Redis Streams. These commands return `-ERR unsupported command '<name>'`.

---

## XADD

**Syntax:** `XADD key [NOMKSTREAM] [MAXLEN|MINID [=|~] threshold] *|id field value [field value ...]`
**Return:** Bulk String — the ID of the added entry
**Description:** Appends a new entry to the stream. `*` auto-generates an ID.

## XREAD

**Syntax:** `XREAD [COUNT count] [BLOCK milliseconds] STREAMS key [key ...] id [id ...]`
**Return:** Array of arrays — `[[stream, [[id, [field, value, ...]], ...]], ...]`, or nil on timeout
**Description:** Reads entries from one or more streams starting from the given IDs. BLOCK makes it a blocking call.

## XRANGE

**Syntax:** `XRANGE key start end [COUNT count]`
**Return:** Array — entries in ID order. Use `-` and `+` for min/max IDs.
**Description:** Returns entries in a stream within a range of IDs.

## XREVRANGE

**Syntax:** `XREVRANGE key end start [COUNT count]`
**Return:** Array — entries in reverse ID order.
**Description:** Like XRANGE but returns entries in reverse order.

## XLEN

**Syntax:** `XLEN key`
**Return:** Integer — number of entries in the stream
**Description:** Returns the number of entries in a stream.

## XINFO

**Subcommands:**
- `XINFO STREAM key [FULL [COUNT count]]` — stream info
- `XINFO GROUPS key` — consumer groups info
- `XINFO CONSUMERS key groupname` — consumers in a group

## XGROUP

**Subcommands:**
- `XGROUP CREATE key groupname id|$ [MKSTREAM] [ENTRIESREAD entries-read]` — create consumer group
- `XGROUP SETID key groupname id|$ [ENTRIESREAD entries-read]` — set group last delivered ID
- `XGROUP DELCONSUMER key groupname consumername` — delete consumer
- `XGROUP DESTROY key groupname` — destroy group

## XACK

**Syntax:** `XACK key group id [id ...]`
**Return:** Integer — number of acknowledged messages
**Description:** Acknowledges messages in a consumer group's pending entries list.

## XCLAIM

**Syntax:** `XCLAIM key group consumer min-idle-time id [id ...] [IDLE ms] [TIME ms-unix-time] [RETRYCOUNT count] [FORCE] [JUSTID] [LASTID lastid]`
**Return:** Array — claimed entries (or just IDs if JUSTID)
**Description:** Claims ownership of pending messages in a consumer group.

## XAUTOCLAIM

**Syntax:** `XAUTOCLAIM key group consumer min-idle-time start [COUNT count] [JUSTID]`
**Return:** Array — `[next-start-id, claimed-entries, deleted-ids]`
**Description:** Automatically transfers ownership of idle pending messages.

## XDEL

**Syntax:** `XDEL key id [id ...]`
**Return:** Integer — number of entries deleted
**Description:** Removes entries from a stream by ID.

## XTRIM

**Syntax:** `XTRIM key MAXLEN|MINID [=|~] threshold`
**Return:** Integer — number of entries trimmed
**Description:** Trims the stream to a certain size or minimum ID.

## XPENDING

**Syntax:** `XPENDING key group [[IDLE min-idle-time] start end count [consumer]]`
**Return:** Summary or detailed pending entries list
**Description:** Returns information about pending messages in a consumer group.

## XSETID

**Syntax:** `XSETID key last-id [ENTRIESADDED entries-added]`
**Return:** Simple String — `OK`
**Description:** Sets the last ID of a stream (internal command, rarely used directly).
