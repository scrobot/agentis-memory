---
host: localhost
port: 6399
namespace: auto
redis_cli_path: redis-cli
---

# Agentis Memory Settings

Place this file at `.claude/agentis-memory.local.md` in your project root to configure the plugin.

## Options

- **host**: Agentis Memory server host (default: `localhost`)
- **port**: Agentis Memory server port (default: `6399`)
- **namespace**: Namespace strategy for keys:
  - `auto` — uses project directory name as prefix (e.g., `myproject:fact:...`)
  - `shared` — no prefix, all keys in global namespace
  - Any other value — used as literal prefix (e.g., `team-alpha:fact:...`)
- **redis_cli_path**: Path to redis-cli binary (default: `redis-cli`). Set to `none` to force /dev/tcp fallback.
