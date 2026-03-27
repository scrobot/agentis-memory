---
name: mem-query
description: "Search working memory by meaning. Use when user says /mem-query."
argument-hint: "<query>"
allowed-tools:
  - Bash
  - Read
---

# /mem-query

Search Agentis Memory by semantic similarity. Finds relevant facts, observations, and knowledge.

## Usage

`/mem-query <natural language query>`

Optional: `/mem-query ALL <query>` to search across all namespaces.

## Execution

1. Read settings from `.claude/agentis-memory.local.md` (host, port, namespace)
2. Determine namespace: use configured namespace unless user specifies `ALL`
3. Run:
   ```bash
   ${CLAUDE_PLUGIN_ROOT}/scripts/redis.sh <host> <port> MEMQUERY "<namespace>" "<query>" "10"
   ```
4. Parse results and present to user in a readable format:
   ```
   Found 3 results:
   1. [0.87] project:fact:stack — "Project uses Java 25 with GraalVM native-image"
   2. [0.72] project:fix:tokenizer — "Replaced DJL with pure Java BertTokenizer"
   3. [0.65] project:obs:netty — "Netty jctools crashes in native-image"
   ```

## Examples

- `/mem-query what language does this project use` → searches in project namespace
- `/mem-query ALL deployment process` → searches across all namespaces
