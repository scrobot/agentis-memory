---
name: mem-status
description: "Show Agentis Memory server status and statistics. Use when user says /mem-status."
argument-hint: "[key]"
allowed-tools:
  - Bash
  - Read
---

# /mem-status

Show memory server status, or check indexing status of a specific key.

## Usage

- `/mem-status` — server overview (connection, key count, memory)
- `/mem-status <key>` — indexing status of a specific key

## Execution

### Without arguments (server overview)

1. Read settings from `.claude/agentis-memory.local.md`
2. Run:
   ```bash
   ${CLAUDE_PLUGIN_ROOT}/scripts/redis.sh <host> <port> PING
   ${CLAUDE_PLUGIN_ROOT}/scripts/redis.sh <host> <port> DBSIZE
   ${CLAUDE_PLUGIN_ROOT}/scripts/redis.sh <host> <port> INFO memory
   ```
3. Present:
   ```
   Agentis Memory: connected (localhost:6399)
   Keys: 42
   Memory: 12.5 MB
   ```

### With key argument

1. Run:
   ```bash
   ${CLAUDE_PLUGIN_ROOT}/scripts/redis.sh <host> <port> MEMSTATUS "<key>"
   ```
2. Present:
   ```
   Key: project:fact:stack
   Status: indexed
   Chunks: 3
   Dimensions: 384
   ```
