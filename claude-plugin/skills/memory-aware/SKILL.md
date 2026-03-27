---
name: memory-aware
description: Use when starting any session to enable semantic working memory. Automatically activates when Agentis Memory plugin is installed. Teaches the agent to save and retrieve knowledge using MEMSAVE/MEMQUERY commands via Redis-compatible protocol.
---

# Working Memory with Agentis Memory

You have access to a **semantic working memory** service. It stores facts, observations, and knowledge as vector embeddings and retrieves them by meaning — not just by key.

## Connection

Read settings from `.claude/agentis-memory.local.md` if it exists. Defaults:
- **Host**: `localhost`
- **Port**: `6399`

Use `${CLAUDE_PLUGIN_ROOT}/scripts/redis.sh <host> <port> <command> [args...]` for all communication.

## Namespace

Determine your namespace prefix from settings:
- `auto` (default): use the current project directory basename (e.g., `agentis-memory`)
- `shared`: no prefix — keys go directly (e.g., `fact:topic`)
- Custom value: use it as-is (e.g., `team-alpha`)

All keys you create MUST use the format: `{namespace}:{category}:{topic}`

Categories:
- `fact` — learned facts ("user prefers dark theme")
- `obs` — observations ("deploy takes ~3 minutes")
- `fix` — bug fixes and resolutions ("OOM caused by memory limit 256mb")
- `pref` — user preferences ("always use TypeScript strict mode")
- `ctx` — session context ("currently working on auth module refactor")

## Commands

### Save to memory
```bash
${CLAUDE_PLUGIN_ROOT}/scripts/redis.sh <host> <port> MEMSAVE "<namespace>:<category>:<topic>" "<text>"
```
Returns `OK`. Indexing happens asynchronously in the background.

### Search by meaning
```bash
${CLAUDE_PLUGIN_ROOT}/scripts/redis.sh <host> <port> MEMQUERY "<namespace>" "<natural language query>" "<count>"
```
Returns array of `[key, text, score]` sorted by relevance. Use `ALL` as namespace for cross-namespace search.

### Check indexing status
```bash
${CLAUDE_PLUGIN_ROOT}/scripts/redis.sh <host> <port> MEMSTATUS "<key>"
```
Returns `[status, chunk_count, dimensions, timestamp]`.

### Delete from memory
```bash
${CLAUDE_PLUGIN_ROOT}/scripts/redis.sh <host> <port> MEMDEL "<key>"
```

### Standard Redis commands also work
```bash
${CLAUDE_PLUGIN_ROOT}/scripts/redis.sh <host> <port> SET "<key>" "<value>"
${CLAUDE_PLUGIN_ROOT}/scripts/redis.sh <host> <port> GET "<key>"
${CLAUDE_PLUGIN_ROOT}/scripts/redis.sh <host> <port> PING
${CLAUDE_PLUGIN_ROOT}/scripts/redis.sh <host> <port> INFO
```

## When to Save

Save automatically when you encounter:

1. **New facts** about the user, project, or domain — things not derivable from code
2. **User corrections** — "don't do X, instead do Y" → save as `pref`
3. **Bug resolutions** — root cause + fix → save as `fix`
4. **Architecture decisions** — why something was done a certain way → save as `fact`
5. **Session context** — what you're currently working on → save as `ctx`

Do NOT save:
- Things derivable from `git log`, file contents, or CLAUDE.md
- Temporary debugging state
- Raw code snippets (save the insight, not the code)

## When to Search

Search memory BEFORE:

1. **Starting a new task** — check if there's prior context: `MEMQUERY <ns> "what do I know about <topic>" 5`
2. **Answering questions about past work** — user asks "what did we decide about X?"
3. **Making decisions** — check if there's a saved preference or policy
4. **Encountering an error** — check if this was fixed before: `MEMQUERY <ns> "error <message>" 3`

## Behavior Rules

- **Silent by default**: Don't announce every save/search. Just do it.
- **Save concisely**: One sentence of insight, not paragraphs. "User prefers Avaje Inject over Guice because zero reflection" — not the entire conversation.
- **Search broadly**: Use natural language queries, not exact key names.
- **Respect namespaces**: Don't read from other namespaces unless using `ALL` for explicit cross-namespace search.
- **Fail gracefully**: If server is unreachable, continue working without memory. Don't error out.
