# Vector Engine

How Agentis Memory turns raw text into searchable semantic vectors — from tokenization to HNSW search.

## The MEMSAVE pipeline

When you run `MEMSAVE agent:fact:stack "We use Java 26 with GraalVM native-image"`, here's what happens:

```
 MEMSAVE key text
       │
       ▼
 ┌─────────────┐     Synchronous — returns +OK immediately
 │  KV Store    │     store(key, text, hasVectorIndex=true)
 └──────┬───────┘
        │
        ▼              Async — background thread pool
 ┌─────────────┐
 │  Chunker     │     Split text into sentence-based chunks (~200–300 tokens)
 │              │     with 1-sentence overlap between consecutive chunks
 └──────┬───────┘
        │
        ▼
 ┌─────────────┐
 │  Tokenizer   │     Pure Java BERT WordPiece tokenizer
 │              │     normalize → pre-tokenize → subword split
 │              │     Produces input_ids, attention_mask, token_type_ids
 └──────┬───────┘
        │
        ▼
 ┌─────────────┐
 │  ONNX        │     all-MiniLM-L6-v2 inference
 │  Runtime     │     [batch, seq_len] → [batch, seq_len, 384]
 │              │     Mean pooling + L2 normalization → [batch, 384]
 └──────┬───────┘
        │
        ▼
 ┌─────────────┐
 │  HNSW Index  │     Insert vectors into jvector graph
 │  (jvector)   │     Cosine similarity, M=16, efConstruction=100
 └──────────────┘
```

The split between synchronous and asynchronous is the key design decision: the agent gets `+OK` in < 0.1ms, while the embedding pipeline runs in a background thread pool (`--embedding-threads`, default 2).

## Step 1: Chunking

**Class:** `io.agentis.memory.vector.Chunker`

The chunker splits text into chunks that fit within the model's 512-token context window.

**Algorithm:**

1. If text is <= 1500 characters (~300 tokens), return it as a single chunk
2. Split on sentence boundaries: `.` `!` `?` followed by whitespace, or blank lines (`\n\n`)
3. Group sentences into chunks of up to 1500 characters
4. Add 1-sentence overlap between consecutive chunks (the last sentence of chunk N becomes the first sentence of chunk N+1)

**Why 1500 characters?** It's a safe proxy for ~200–300 BERT tokens. The all-MiniLM-L6-v2 model has a 512-token window. Staying well under that limit avoids truncation and keeps embeddings high quality.

**Why overlap?** Without overlap, a fact split across two chunks might not be found by either. The 1-sentence overlap provides continuity — if a key sentence sits on a boundary, both chunks capture it.

**Example:**

```
Input (2500 chars):
  "The auth system uses JWT tokens. They expire after 24 hours.
   Refresh tokens last 30 days. The frontend stores tokens in
   httpOnly cookies. Never store tokens in localStorage. ..."

Output:
  Chunk 0: "The auth system uses JWT tokens. They expire after 24 hours.
            Refresh tokens last 30 days. The frontend stores tokens in
            httpOnly cookies."
  Chunk 1: "The frontend stores tokens in httpOnly cookies. Never store
            tokens in localStorage. ..."
                ↑ overlap sentence
```

## Step 2: Tokenization

**Class:** `io.agentis.memory.vector.BertTokenizer`

A pure Java implementation of the BERT WordPiece tokenizer. Replaces the DJL HuggingFace tokenizer (Rust JNI) which crashes under GraalVM native-image.

**Vocabulary:** 30,522 tokens from `bert-base-uncased`, loaded from `tokenizer.json` (HuggingFace format).

**Pipeline:**

```
 Raw text
   │
   ▼
 Normalize
   │  lowercase
   │  remove control characters
   │  NFD decompose → strip accents (é → e)
   │
   ▼
 Pre-tokenize
   │  split on whitespace
   │  split on punctuation (each punct char = separate token)
   │  add spaces around Chinese characters
   │
   ▼
 WordPiece
   │  greedy longest-match-first subword split
   │  continuation tokens prefixed with "##"
   │  unknown characters → [UNK]
   │
   ▼
 Encode
      wrap with [CLS] ... [SEP]
      produce input_ids, attention_mask, token_type_ids
      pad to batch max length
```

**Example:**

```
Input:  "GraalVM native-image"
                ↓ normalize
        "graalvm native-image"
                ↓ pre-tokenize
        ["graalvm", "native", "-", "image"]
                ↓ WordPiece
        ["gr", "##aal", "##vm", "native", "-", "image"]
                ↓ encode
        [CLS] gr ##aal ##vm native - image [SEP]
 IDs:   [101, 24665, 11057, 2213, 3128, 1011, 3746, 102]
 Mask:  [1,    1,     1,    1,    1,    1,    1,    1   ]
```

**Max sequence length:** 512 tokens. Text exceeding this is truncated (but the chunker ensures chunks stay well under this limit).

## Step 3: Embedding

**Class:** `io.agentis.memory.vector.Embedder`

**Model:** [all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) — a sentence-transformer fine-tuned for semantic similarity.

| Property | Value |
|---|---|
| Architecture | 6-layer MiniLM (distilled from BERT) |
| Parameters | ~22M |
| Output dimensions | 384 |
| Max input tokens | 512 |
| Model format | ONNX (~80 MB) |
| Runtime | ONNX Runtime (via Java Panama FFI) |
| File | `models/model.onnx` |

**Inference pipeline:**

1. **Tokenize** each chunk → `[input_ids, attention_mask, token_type_ids]`
2. **Batch** all chunks into padded tensors of shape `[batch_size, max_seq_len]`
3. **Run ONNX inference** → `last_hidden_state` of shape `[batch_size, seq_len, 384]`
4. **Mean pooling** — average all token embeddings, weighted by attention mask (ignores padding tokens)
5. **L2 normalize** — each vector is unit length, so cosine similarity = dot product

```
 Tokenized input:          [batch, seq_len]
         ↓
 ONNX Runtime inference
         ↓
 last_hidden_state:        [batch, seq_len, 384]
         ↓
 Mean pooling (mask-aware)
         ↓
 Sentence embedding:       [batch, 384]
         ↓
 L2 normalize
         ↓
 Final vector:             [batch, 384]   (unit length)
```

**Batching:** when multiple MEMSAVE commands arrive concurrently, their chunks are grouped into a single ONNX inference pass. This is significantly faster than embedding one chunk at a time.

**Latency:**

| Operation | Typical latency |
|---|---|
| Single chunk (~200 tokens) | 5–10 ms |
| 50 chunks (batch) | 100–200 ms |
| First call (model load) | 500–1000 ms |

**Threading:** controlled by `--embedding-threads` (default 2). This sets both the ONNX Runtime intra-op thread count and the VectorEngine thread pool size.

## Step 4: HNSW Indexing

**Class:** `io.agentis.memory.vector.HnswIndex`

**Library:** [jvector](https://github.com/jbellis/jvector) (DataStax, Apache 2.0) — a pure Java HNSW implementation with SIMD acceleration via Java Vector API.

**How HNSW works:**

HNSW (Hierarchical Navigable Small World) is a graph-based approximate nearest neighbor search algorithm. Each vector is a node in a multi-layer graph. Search starts at the top layer and greedily moves to closer nodes, descending layers until it reaches the bottom where the most precise neighbors are found.

| Parameter | Default | Effect |
|---|---|---|
| `--hnsw-m` | 16 | Connections per node. Higher = better recall, more memory |
| `--hnsw-ef-construction` | 100 | Search depth during build. Higher = better recall, slower inserts |

**Index structure:**

```
 Layer 2:   o ─── o ─── o                    (few nodes, long links)
            │           │
 Layer 1:   o ── o ── o ── o ── o            (more nodes, medium links)
            │    │    │    │    │
 Layer 0:   o─o─o─o─o─o─o─o─o─o─o─o─o       (all nodes, short links)
```

**Similarity metric:** cosine similarity (via `VectorSimilarityFunction.COSINE`). Since vectors are L2-normalized, this is equivalent to dot product.

**Thread safety:**
- Writes (add/remove) acquire a write lock and rebuild the graph
- Reads (search) acquire a read lock and snapshot the current graph
- This allows concurrent searches while writes are serialized

**Memory per vector:** ~1.5 KB (384 dims × 4 bytes + HNSW graph overhead)

## The MEMQUERY pipeline

When you run `MEMQUERY agent "what language" 5`:

```
 MEMQUERY namespace query K
       │
       ▼
 ┌─────────────┐
 │  Tokenizer   │     Same BertTokenizer pipeline as MEMSAVE
 └──────┬───────┘
        │
        ▼
 ┌─────────────┐
 │  ONNX        │     Embed query → single 384-dim vector
 │  Runtime     │
 └──────┬───────┘
        │
        ▼
 ┌─────────────┐
 │  HNSW Search │     Search for K×3 nearest neighbors (over-fetch)
 │  (jvector)   │
 └──────┬───────┘
        │
        ▼
 ┌─────────────┐
 │  Namespace   │     Filter results by namespace prefix
 │  Filter      │     Return top K matches
 └──────┬───────┘
        │
        ▼
  Response: [[key, chunk_text, score], ...]
```

**Namespace filtering:** a single HNSW index stores all vectors from all namespaces. At query time, the search over-fetches 3x (requests K×3 from HNSW), then filters by namespace. This is efficient for agent-scale workloads. If a namespace has very few entries, fewer than K results may be returned.

**Why not separate indexes per namespace?** For the typical agent memory workload (thousands to tens of thousands of chunks), a single index with post-filtering is simpler and fast enough. Partitioned indexes add complexity and would only help at millions of vectors per namespace. This is a future optimization path if needed.

## Key overwrite behavior

When `MEMSAVE` is called for a key that already exists:

1. Old chunks are marked as deleted in the HNSW index
2. KV value is replaced immediately
3. New chunking + embedding + indexing starts
4. Old chunks remain searchable until new ones are ready
5. Once new chunks are indexed, old deleted entries are cleaned up during compaction (triggered when >25% of entries are deleted)

This provides an atomic swap from the search perspective — there's no window where the key has zero results.

## Data structures summary

```java
// A single chunk from a MEMSAVE document
record Chunk(
    String parentKey,    // the MEMSAVE key (e.g. "agent:fact:stack")
    int index,           // 0-based chunk ordinal
    String text,         // chunk text
    float[] vector,      // 384-dim L2-normalized embedding
    String namespace     // extracted prefix before first ':'
)

// Indexing status tracked per key
record IndexingStatus(
    String status,       // "indexed", "pending", "error"
    int chunkCount,      // number of chunks produced
    int dimensions,      // vector dimensions (384)
    long lastUpdatedMs   // timestamp
)
```

## Model details

**all-MiniLM-L6-v2** is a sentence-transformer model from the [sentence-transformers](https://sbert.net) project:

- Distilled from Microsoft MiniLM, which is itself distilled from BERT
- Fine-tuned on 1B+ sentence pairs for semantic similarity
- 6 transformer layers (vs 12 in BERT-base) — 2x faster, minimal quality loss
- Trained with mean pooling (not CLS token) — hence the mean pooling step in the pipeline
- 384-dimensional output (vs 768 in BERT) — 2x less memory per vector

**Why this model?**
- Best quality-to-size ratio for general-purpose sentence embeddings
- Small enough to bundle (~80 MB ONNX) and run on CPU without GPU
- Well-tested, widely adopted (most downloaded sentence-transformer on HuggingFace)
- ONNX export works cleanly, no custom ops

**Why ONNX Runtime?**
- Cross-platform inference on CPU (no PyTorch/TensorFlow dependency)
- Optimized for server-side inference with multi-threading
- Java bindings via Panama FFI (no JNI overhead in GraalVM native-image)

**Why pure Java tokenizer?**
- The standard HuggingFace tokenizer uses Rust via JNI (the `tokenizers` crate)
- Rust JNI crashes under GraalVM native-image due to incompatible native memory management
- The pure Java `BertTokenizer` implements the same WordPiece algorithm with identical output
- Loaded from the same `tokenizer.json` format — no separate vocab file needed
