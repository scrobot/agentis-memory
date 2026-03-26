# Transaction Commands

**Status: NOT SUPPORTED**

Agentis Memory does not implement transactions. These commands return `-ERR unsupported command '<name>'`.

---

## MULTI

**Syntax:** `MULTI`
**Return:** Simple String — `OK`
**Description:** Marks the start of a transaction block. Subsequent commands are queued and executed atomically on EXEC.

## EXEC

**Syntax:** `EXEC`
**Return:** Array — results of all queued commands, or nil if WATCH detected a change
**Description:** Executes all queued commands in the transaction and restores normal connection state.

## DISCARD

**Syntax:** `DISCARD`
**Return:** Simple String — `OK`
**Description:** Discards all queued commands in the transaction and restores normal connection state.

## WATCH

**Syntax:** `WATCH key [key ...]`
**Return:** Simple String — `OK`
**Description:** Marks keys to be watched for changes. If any watched key is modified before EXEC, the transaction is aborted (EXEC returns nil). Provides optimistic locking / check-and-set (CAS).

## UNWATCH

**Syntax:** `UNWATCH`
**Return:** Simple String — `OK`
**Description:** Removes all watched keys for the current connection.
