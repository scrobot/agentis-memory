# Memory Commands

These commands provide semantic storage and retrieval — the core differentiator of Agentis Memory.

## MEMSAVE

```
MEMSAVE key text
```

Store text in the KV store and asynchronously chunk, embed, and index it for semantic search.

**Behavior:**

1. Text is stored in the KV store immediately (accessible via `GET`)
2. Returns `+OK` synchronously
3. In the background: text is split into chunks (~200–300 tokens), each chunk is embedded (384-dim vector), and vectors are inserted into the HNSW index

**Key overwrite:** if the key already exists, the old value and chunks are replaced. Old chunks remain searchable until new indexation completes (atomic swap).

**Errors:**

| Error | Cause |
|---|---|
| `ERR value exceeds max-value-size limit` | Text exceeds `--max-value-size` (default 1MB) |
| `ERR value too large, exceeds max chunk count` | Chunking produces more than `--max-chunks-per-key` (default 100) chunks |

**Examples:**

```bash
redis-cli -p 6399 MEMSAVE "agent:fact:stack" "We use Java 26 with GraalVM native-image"
# → OK

redis-cli -p 6399 MEMSAVE "shared:policy:deploy" "All deploys must go through staging first"
# → OK
```

---

## MEMQUERY

```
MEMQUERY namespace query count
```

Embed the query and return the top-K semantically similar results from the given namespace.

**Parameters:**

| Parameter | Description |
|---|---|
| `namespace` | Key prefix to search within. Use `ALL` for cross-namespace search |
| `query` | Natural language search query |
| `count` | Maximum results to return (1–1000) |

**Response:** array of `[key, text, score]` sorted by relevance. Score is a float 0.0–1.0 (cosine similarity, higher = more similar).

```
*2
*3
$15
agent:fact:stack
$40
We use Java 26 with GraalVM native-image
$4
0.91
*3
$19
shared:policy:deploy
$44
All deploys must go through staging first
$4
0.42
```

**Behavior notes:**

- If the namespace has fewer matching results than `count`, fewer results are returned (not an error)
- Unknown namespaces return an empty array (not an error)
- The search over-fetches 3x from HNSW and post-filters by namespace

**Errors:**

| Error | Cause |
|---|---|
| `ERR K must be between 1 and 1000` | Invalid count |
| `ERR query must not be empty` | Empty query string |

**Examples:**

```bash
# Search within a namespace
redis-cli -p 6399 MEMQUERY agent "what language do we use" 5

# Search across all namespaces
redis-cli -p 6399 MEMQUERY ALL "deployment process" 10

# Search shared policies
redis-cli -p 6399 MEMQUERY shared "coding standards" 3
```

---

## MEMSTATUS

```
MEMSTATUS key
```

Check the indexing status of a key that was saved with `MEMSAVE`.

**Response:** array `[status, chunk_count, dimensions, last_updated_ms]`

| Field | Type | Description |
|---|---|---|
| `status` | string | `indexed`, `pending`, or `error` |
| `chunk_count` | integer | Number of chunks created |
| `dimensions` | integer | Vector dimensions (384) |
| `last_updated_ms` | integer | Unix timestamp in milliseconds |

**Errors:**

| Error | Cause |
|---|---|
| `ERR no such key` | Key does not exist |
| `ERR key is not indexed` | Key exists in KV but was not saved via MEMSAVE |

**Example:**

```bash
redis-cli -p 6399 MEMSTATUS "agent:fact:stack"
# → 1) "indexed"
#    2) (integer) 1
#    3) (integer) 384
#    4) (integer) 1711451234567
```

---

## MEMDEL

```
MEMDEL key
```

Delete a key from both the KV store and the vector index. If indexation is still pending, it is cancelled.

**Response:** integer — `1` if the key existed, `0` otherwise.

**Behavior:**

1. KV entry is deleted immediately
2. Pending indexation job is cancelled
3. If chunks were already committed to HNSW, they are removed by a background cleanup sweep

**Example:**

```bash
redis-cli -p 6399 MEMDEL "agent:fact:stack"
# → (integer) 1

redis-cli -p 6399 MEMDEL "nonexistent"
# → (integer) 0
```

---

## Key naming convention

Keys follow the pattern `namespace:category:topic`:

| Category | Purpose | Example |
|---|---|---|
| `fact` | Learned facts | `agent:fact:tech-stack` |
| `obs` | Observations | `agent:obs:deploy-duration` |
| `pref` | User preferences | `agent:pref:code-style` |
| `fix` | Bug resolutions | `agent:fix:oom-in-worker` |
| `ctx` | Session context | `agent:ctx:current-task` |
| `policy` | Cross-agent rules | `shared:policy:pr-review` |

The namespace (everything before the first `:`) determines search scope in `MEMQUERY`.
