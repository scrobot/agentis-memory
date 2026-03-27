---
name: mem-save
description: "Save a fact or observation to Agentis Memory. Use when user says /mem-save."
argument-hint: "<key> <text to remember>"
allowed-tools:
  - Bash
  - Read
---

# /mem-save

Save a fact, observation, or knowledge to semantic working memory.

## Usage

`/mem-save <key> <text>`

- If user provides a full key (e.g., `project:fact:auth`), use it as-is
- If user provides just text without key, generate a key: `{namespace}:fact:{auto-topic}`
- Namespace comes from `.claude/agentis-memory.local.md` settings (default: project dir name)

## Execution

1. Read settings from `.claude/agentis-memory.local.md` (host, port, namespace)
2. Run:
   ```bash
   ${CLAUDE_PLUGIN_ROOT}/scripts/redis.sh <host> <port> MEMSAVE "<key>" "<text>"
   ```
3. Confirm to user: "Saved to memory: `<key>`"
4. Optionally check status after 2 seconds:
   ```bash
   ${CLAUDE_PLUGIN_ROOT}/scripts/redis.sh <host> <port> MEMSTATUS "<key>"
   ```

## Examples

- `/mem-save Alex prefers Java 25 with GraalVM for this project` → saves as `{ns}:pref:java-version`
- `/mem-save project:fix:oom Increased memory limit from 256mb to 512mb, fixed OOM crash` → saves with explicit key
