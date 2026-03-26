# Server Commands

---

## AUTH (implemented)

**Syntax:** `AUTH password`
**Return:** Simple String — `OK` on success
**Errors:** ERR invalid password; ERR Client sent AUTH, but no password is set

## INFO (implemented)

**Syntax:** `INFO [section]`
**Return:** Bulk String — server info in Redis INFO format
**Sections to support:** server, memory, keyspace, clients, stats

## DBSIZE (implemented)

**Syntax:** `DBSIZE`
**Return:** Integer — total key count (all types)

## CONFIG (implemented, stub)

**Syntax:** `CONFIG GET pattern` | `CONFIG SET parameter value` | `CONFIG RESETSTAT`
**Return:** GET: Array of key-value pairs. SET: `OK`. RESETSTAT: `OK`.
**Note:** Stub with basic values for Redis Insight compatibility.

## CLIENT (implemented, stub)

**Syntax:** `CLIENT SETNAME name` | `CLIENT GETNAME` | `CLIENT INFO` | `CLIENT LIST` | `CLIENT ID`
**Return:** Varies. SETNAME: `OK`. GETNAME: Bulk String/nil. INFO/LIST: Bulk String. ID: Integer.

## COMMAND (implemented, stub)

**Syntax:** `COMMAND` | `COMMAND DOCS` | `COMMAND COUNT` | `COMMAND INFO`
**Return:** Array or Integer. Stubs for Redis Insight compatibility.

## BGSAVE (implemented)

**Syntax:** `BGSAVE`
**Return:** Simple String — `Background saving started`

## SELECT

**Syntax:** `SELECT index`
**Return:** Simple String — `OK`
**Errors:** ERR DB index is out of range
**Implementation:** For MVP, support only `SELECT 0` (single database). `SELECT 1..15` returns OK but all databases share the same keyspace. Or return error for non-zero index — simpler and more honest.

## FLUSHDB

**Syntax:** `FLUSHDB [ASYNC|SYNC]`
**Return:** Simple String — `OK`
**Description:** Deletes all keys. ASYNC option ignored for MVP (always sync).

## FLUSHALL

**Syntax:** `FLUSHALL [ASYNC|SYNC]`
**Return:** Simple String — `OK`
**Description:** Same as FLUSHDB for us (single database).

## ECHO

**Syntax:** `ECHO message`
**Return:** Bulk String — the message

## TIME

**Syntax:** `TIME`
**Return:** Array — `[unix_seconds_string, microseconds_string]`
**Example:** `["1711451234", "567890"]`
