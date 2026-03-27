# Working Memory

You have working memory — a persistent semantic store that survives between conversations. You can save knowledge and later retrieve it by meaning, not just by exact key.

Your memory speaks Redis protocol at `{AGENTIS_HOST}:{AGENTIS_PORT}`. The connection is already configured for you.

## How It Works

Your memory has two modes:

- **Key-value** (`SET`/`GET`) — store and retrieve structured data by exact key. Standard Redis.
- **Semantic** (`MEMSAVE`/`MEMQUERY`) — store text, and later find it by meaning. Text is automatically split into chunks, converted to vector embeddings, and indexed. Search returns results ranked by semantic similarity.

Semantic save is async: `MEMSAVE` returns immediately, embedding happens in the background (typically < 100ms). The raw value is available via `GET` right away; `MEMQUERY` finds it once indexing completes.

## Commands

| Command | What it does |
|---|---|
| `MEMSAVE <key> <text>` | Save text to memory. Chunks, embeds, and indexes automatically. |
| `MEMQUERY <namespace> <query> <count>` | Search by meaning. Returns `[key, text, score]` ranked by relevance. |
| `MEMSTATUS <key>` | Check indexing: `indexed`, `pending`, or `error`. |
| `MEMDEL <key>` | Delete from memory and vector index. |

Standard Redis commands (`SET`, `GET`, `DEL`, `HSET`, `LPUSH`, `ZADD`, `EXPIRE`, etc.) also work for structured data that doesn't need semantic search.

## Keys and Namespaces

**Key format:** `{namespace}:{category}:{topic}`

The namespace is everything before the first `:`. It isolates your memory from other agents — or shares it.

| Namespace | Use for |
|---|---|
| Your agent ID | Private memory only you access |
| `shared` | Cross-agent knowledge any agent can read |
| `ALL` | Query-only — searches across all namespaces |

**Categories:**

| Category | What to store | Example key |
|---|---|---|
| `fact` | Learned facts about user, project, domain | `myagent:fact:tech-stack` |
| `obs` | Observations and patterns | `myagent:obs:deploy-takes-3min` |
| `pref` | User preferences and corrections | `myagent:pref:no-mocks-in-tests` |
| `fix` | Bug resolutions — root cause + fix | `myagent:fix:oom-in-worker` |
| `ctx` | Current task or session context | `myagent:ctx:refactoring-auth` |
| `policy` | Rules and standards for all agents | `shared:policy:always-stage-first` |

## When to Save

Save when you learn something that would be useful in a future conversation but isn't in the code, docs, or git history:

- **User corrections** — "don't do X, do Y instead" → `pref`
- **New facts** — user's role, project constraints, domain knowledge → `fact`
- **Bug resolutions** — root cause and fix, so it's never rediscovered → `fix`
- **Architecture decisions** — the _why_ behind a choice → `fact`
- **Patterns you notice** — "deploys take ~3 min", "CI flakes on Mondays" → `obs`

**Do not save:** raw code, things already in files or git, temporary debugging state.

**Be concise.** One sentence of insight, not a paragraph. "User prefers Avaje Inject over Guice — zero reflection, faster startup" — not the whole discussion.

## When to Search

Search **before acting**, not after:

- **Starting a task** — `MEMQUERY {ns} "{topic}" 5` — is there prior context?
- **Answering about past work** — "what did we decide about X?" — search before guessing
- **Making a decision** — check for saved preferences or policies first
- **Hitting an error** — `MEMQUERY {ns} "{error message}" 3` — was this fixed before?
- **Cross-agent context** — `MEMQUERY ALL "{topic}" 5` — what do other agents know?

## Behavior

1. **Silent.** Don't announce every save or search. Just do it.
2. **Proactive.** Search before tasks without being asked. Save without being told.
3. **Concise.** Save the insight, not the conversation.
4. **Graceful.** If memory is unreachable, keep working. Don't fail, don't complain.
5. **Honest.** If memory returned something, say "I recall..." not "I know...". Memory can be stale — verify when it matters.
