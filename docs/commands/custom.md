# Custom Commands (Agentis Memory)

These are Agentis Memory-specific commands not present in Redis.

---

## MEMSAVE

**Syntax:** `MEMSAVE key value`
**Return:** Simple String — `OK` (immediate, indexation is async)
**Errors:** ERR value exceeds max-value-size limit; ERR value too large, exceeds max chunk count
**Behavior:**
1. Stores original value in KV Store with `hasVectorIndex=true`
2. Asynchronously: chunks text → embeds via ONNX → indexes in HNSW
3. On overwrite: old chunks marked for deletion, atomic swap when new chunks ready

## MEMQUERY

**Syntax:** `MEMQUERY namespace query K`
**Return:** Array of arrays — `[[key, text, score], ...]`
**Errors:** ERR K must be between 1 and 1000; ERR query must not be empty
**Behavior:**
- `namespace` — search only in this namespace (extracted from key prefix before `:`)
- `ALL` — search across all namespaces
- May return fewer than K results if namespace has fewer matches
- Score is cosine similarity (0.0 to 1.0, higher = more similar)

## MEMDEL

**Syntax:** `MEMDEL key`
**Return:** Integer — 1 if key existed, 0 if not
**Behavior:** Deletes from KV Store + cancels pending indexation + removes chunks from HNSW. Orphan cleanup handles race conditions.

## MEMSTATUS

**Syntax:** `MEMSTATUS key`
**Return:** Array — `[status, chunk_count, dimensions, last_updated_ms]`
**Status values:** `indexed`, `pending`, `error`
**Errors:** ERR no such key
**Example:** `["indexed", 3, 384, 1711451234567]`
