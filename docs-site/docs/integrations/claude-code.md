# Claude Code

The [Agentis Memory plugin](https://github.com/scrobot/agentis-memory/tree/main/claude-plugin) gives Claude Code semantic working memory — save facts and search by meaning, directly from your coding session.

## Install

### 1. Start the server

```bash
git clone https://github.com/scrobot/agentis-memory.git
cd agentis-memory
docker compose up -d
```

Verify:

```bash
redis-cli -p 6399 PING
# → PONG
```

### 2. Install the plugin

In Claude Code, open **Plugins** and add:

```
scrobot/agentis-memory-plugin
```

Or from a local clone:

```bash
claude --plugin /path/to/agentis-memory/claude-plugin
```

### 3. Done

Claude will now automatically save and search memory. You'll see "Agentis Memory: connected" at session start.

## Slash commands

| Command | What it does | Example |
|---|---|---|
| `/mem-save` | Save a fact or observation | `/mem-save Alex prefers Avaje Inject over Guice` |
| `/mem-query` | Search memory by meaning | `/mem-query dependency injection preferences` |
| `/mem-query ALL ...` | Search all namespaces | `/mem-query ALL deployment process` |
| `/mem-status` | Show server stats | `/mem-status` |
| `/mem-status <key>` | Check indexing status | `/mem-status project:fact:stack` |
| `/mem-forget` | Delete a key | `/mem-forget project:fact:outdated-info` |

## Automatic behavior

With the plugin installed, Claude silently:

**Saves** when it encounters:

- New facts about you, your project, or your domain
- Your corrections ("don't do X, do Y instead")
- Bug resolutions (root cause + fix)
- Architecture decisions and their reasoning

**Searches** before:

- Starting a new task (checks for prior context)
- Answering questions about past work
- Making decisions (checks for saved preferences)
- Encountering errors (checks if this was fixed before)

## Configuration

Create `.claude/agentis-memory.local.md` in your project root:

```yaml
---
host: localhost
port: 6399
namespace: auto
---
```

| Setting | Default | Description |
|---|---|---|
| `host` | `localhost` | Server host |
| `port` | `6399` | Server port |
| `namespace` | `auto` | Key prefix strategy |

**Namespace options:**

- `auto` — project directory name as prefix (each project gets its own memory)
- `shared` — no prefix (all projects share one memory space)
- Any string — custom prefix (e.g., `team-alpha`)

## Troubleshooting

**"Agentis Memory: not available"** at session start

- Is the server running? `docker compose up -d`
- Check port: `docker ps | grep agentis`
- Test connectivity: `redis-cli -p 6399 PING`

**Slow first MEMSAVE**

- First call loads the ONNX model (~80MB). Subsequent calls are fast (5–10ms per chunk).
