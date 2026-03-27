# Agentis Memory Plugin for Claude Code

Gives Claude Code semantic working memory — save facts and search by meaning.

## Prerequisites

- [Agentis Memory](https://github.com/agentis-memory/agentis-memory) server running (default: `localhost:6399`)
- `redis-cli` recommended (falls back to bash `/dev/tcp` if not available)

## Installation

```bash
# From git
claude plugins add /path/to/agentis-memory/claude-plugin

# Or copy to your project
cp -r claude-plugin/.claude-plugin .claude-plugin
```

## Configuration

Create `.claude/agentis-memory.local.md` in your project:

```yaml
---
host: localhost
port: 6399
namespace: auto
---
```

**Namespace options:**
- `auto` (default) — uses project directory name as prefix
- `shared` — no prefix, global namespace
- Any string — custom prefix (e.g., `team-alpha`)

## Slash Commands

| Command | Description |
|---|---|
| `/mem-save <key> <text>` | Save a fact to memory |
| `/mem-query <query>` | Search memory by meaning |
| `/mem-status [key]` | Server status or key status |
| `/mem-forget <key>` | Delete from memory |

## Automatic Behavior

With the plugin installed, Claude will automatically:
- **Save** new facts, user preferences, bug fixes, and architecture decisions
- **Search** memory before starting tasks, answering questions about past work, or encountering errors
- All silently — no announcements unless you ask

## Quick Start

```bash
# Start the server
docker compose up -d

# In Claude Code
/mem-save "project uses Java 25 with GraalVM"
/mem-query "what language does this project use"
/mem-status
```
