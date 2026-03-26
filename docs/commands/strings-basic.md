# String Commands (Basic) — Implemented

---

## SET (implemented)

**Syntax:** `SET key value [EX seconds | PX milliseconds | EXAT timestamp | PXAT timestamp-ms] [NX|XX] [GET]`
**Return:** Simple String `OK`, or nil if NX/XX condition not met, or Bulk String (old value) if GET option used
**Note:** Currently supports `SET key value [EX seconds]`. Extend to support PX, EXAT, PXAT, NX, XX, GET options.

## GET (implemented)

**Syntax:** `GET key`
**Return:** Bulk String or nil
**Errors:** WRONGTYPE if key holds non-string value (when we have multiple types)

## DEL (implemented)

**Syntax:** `DEL key [key ...]`
**Return:** Integer — number of keys deleted

## EXISTS (implemented)

**Syntax:** `EXISTS key [key ...]`
**Return:** Integer — number of keys that exist
**Note:** If same key listed twice, counts twice.

## PING (implemented)

**Syntax:** `PING [message]`
**Return:** Without args: Simple String `PONG`. With args: Bulk String of message.

## QUIT (implemented)

**Syntax:** `QUIT`
**Return:** Simple String `OK`, then close connection.

## ECHO

**Syntax:** `ECHO message`
**Return:** Bulk String — the message
