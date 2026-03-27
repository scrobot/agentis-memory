# Server Commands

Commands for server management, authentication, and diagnostics.

## PING

```
PING [message]
```

Returns `PONG` (or the message if provided). Use as a healthcheck.

During startup recovery, returns `-LOADING server is loading data`.

## AUTH

```
AUTH password
```

Authenticate with the server. Required if `--requirepass` is configured. All commands return `-NOAUTH Authentication required` until authenticated.

## INFO

```
INFO [section]
```

Return server information and statistics. Sections: `server`, `clients`, `memory`, `stats`, `keyspace`, `persistence`. Without a section, returns all.

```bash
redis-cli -p 6399 INFO server
redis-cli -p 6399 INFO memory
redis-cli -p 6399 INFO
```

Compatible with Redis Insight.

## DBSIZE

```
DBSIZE
```

Return the total number of keys.

## BGSAVE

```
BGSAVE
```

Trigger a manual snapshot (KV store + HNSW index). Returns immediately.

## TIME

```
TIME
```

Return the current server time as `[unix_seconds, microseconds]`.

## FLUSHDB

```
FLUSHDB
```

Delete all keys in the current database.

!!! warning
    This is destructive and cannot be undone.

## FLUSHALL

```
FLUSHALL
```

Same as `FLUSHDB` (Agentis Memory uses a single keyspace).

## QUIT

```
QUIT
```

Close the connection.

## SELECT

```
SELECT index
```

Select a database by index. Agentis Memory uses a single keyspace — this command is accepted for compatibility but has no effect.

## ECHO

```
ECHO message
```

Return the message. Useful for testing.

## HELLO

```
HELLO [protover]
```

Handshake command. Returns server information including protocol version and server name.

## CLIENT

```
CLIENT SETNAME connection-name
CLIENT INFO
CLIENT GETNAME
```

Connection management. `SETNAME` assigns a name to the current connection. `INFO` and `GETNAME` return connection details.

## CONFIG

```
CONFIG GET parameter
```

Read configuration values. Returns a subset of server configuration.

```bash
redis-cli -p 6399 CONFIG GET maxmemory
redis-cli -p 6399 CONFIG GET save
```

## COMMAND

```
COMMAND
COMMAND DOCS [command-name]
```

Return command metadata. Returns basic stubs for compatibility.

## OBJECT

```
OBJECT HELP
OBJECT ENCODING key
```

Inspect object internals. Returns compatibility stubs.
