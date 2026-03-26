# Scripting Commands

**Status: NOT SUPPORTED**

Agentis Memory does not implement Lua scripting. These commands return `-ERR unsupported command '<name>'`.

---

## EVAL

**Syntax:** `EVAL script numkeys [key [key ...]] [arg [arg ...]]`
**Return:** Varies — depends on the Lua script return value
**Description:** Evaluates a Lua script server-side. The script has access to Redis commands via `redis.call()` and `redis.pcall()`.

## EVALSHA

**Syntax:** `EVALSHA sha1 numkeys [key [key ...]] [arg [arg ...]]`
**Return:** Varies — same as EVAL
**Errors:** NOSCRIPT if the script SHA is not cached
**Description:** Evaluates a cached Lua script by its SHA1 hash.

## EVALRO

**Syntax:** `EVALRO script numkeys [key [key ...]] [arg [arg ...]]`
**Return:** Varies
**Description:** Read-only variant of EVAL. Script cannot execute write commands.

## EVALSHA_RO

**Syntax:** `EVALSHA_RO sha1 numkeys [key [key ...]] [arg [arg ...]]`
**Return:** Varies
**Description:** Read-only variant of EVALSHA.

## SCRIPT EXISTS

**Syntax:** `SCRIPT EXISTS sha1 [sha1 ...]`
**Return:** Array of integers — 1 if script exists, 0 if not

## SCRIPT FLUSH

**Syntax:** `SCRIPT FLUSH [ASYNC|SYNC]`
**Return:** Simple String — `OK`
**Description:** Removes all cached scripts.

## SCRIPT LOAD

**Syntax:** `SCRIPT LOAD script`
**Return:** Bulk String — the SHA1 hash of the script
**Description:** Loads a script into the script cache without executing it.

## SCRIPT KILL

**Syntax:** `SCRIPT KILL`
**Return:** Simple String — `OK`
**Description:** Kills the currently executing script if it has not yet performed a write.

## FUNCTION CREATE / FUNCTION CALL / FUNCTION LIST / FUNCTION DELETE / FUNCTION DUMP / FUNCTION RESTORE / FUNCTION STATS

**Description:** Redis 7.0+ function API for persistent server-side scripting. Not supported.
