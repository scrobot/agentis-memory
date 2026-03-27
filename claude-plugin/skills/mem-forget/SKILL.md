---
name: mem-forget
description: "Delete a key from Agentis Memory. Use when user says /mem-forget."
argument-hint: "<key>"
allowed-tools:
  - Bash
  - Read
---

# /mem-forget

Delete a key from semantic working memory.

## Usage

`/mem-forget <key>`

## Execution

1. Read settings from `.claude/agentis-memory.local.md`
2. Run:
   ```bash
   ${CLAUDE_PLUGIN_ROOT}/scripts/redis.sh <host> <port> MEMDEL "<key>"
   ```
3. Report result:
   - `1` → "Deleted: `<key>`"
   - `0` → "Key not found: `<key>`"
