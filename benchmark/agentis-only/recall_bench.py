#!/usr/bin/env python3
"""
Recall@K benchmark for Agentis Memory MEMQUERY.

Measures how well MEMQUERY's HNSW approximate search compares to
exact brute-force cosine similarity on the same corpus.

Algorithm:
  1. Populate N texts via MEMSAVE, wait for full indexation.
  2. Embed each test query locally with ONNX (same model as the server).
  3. Brute-force cosine similarity against all chunk embeddings to get ground truth top-K.
  4. MEMQUERY top-K via network.
  5. Recall@K = |MEMQUERY results ∩ brute-force results| / K.

Dependencies:
    pip install redis onnxruntime numpy

Usage:
    python recall_bench.py [--host HOST] [--port PORT] [--model-dir DIR]
                           [--corpus-size N] [--top-k K]
"""

import argparse
import time
import numpy as np
import redis

# Optional: sentence-transformers for a second ground-truth embedding source
try:
    from sentence_transformers import SentenceTransformer
    HAS_ST = True
except ImportError:
    HAS_ST = False


# ─── ONNX local embedder ──────────────────────────────────────────────────────

class LocalEmbedder:
    """Wraps ONNX Runtime to embed text locally — same model as the server."""

    def __init__(self, model_dir: str):
        import onnxruntime as ort
        import json
        import os

        model_path = os.path.join(model_dir, "model.onnx")
        tokenizer_path = os.path.join(model_dir, "tokenizer.json")

        if not os.path.exists(model_path):
            raise FileNotFoundError(f"model.onnx not found in {model_dir}. "
                                    "Set --model-dir to the path containing model.onnx.")

        self.session = ort.InferenceSession(
            model_path,
            providers=["CPUExecutionProvider"],
        )

        # Load tokenizer (Hugging Face tokenizers format)
        try:
            from tokenizers import Tokenizer
            self.tokenizer = Tokenizer.from_file(tokenizer_path)
            self.tokenizer.enable_padding(pad_id=0, pad_token="[PAD]", length=128)
            self.tokenizer.enable_truncation(max_length=128)
            self._use_hf_tokenizer = True
        except ImportError:
            print("WARNING: `tokenizers` package not found. Install it for accurate embeddings:")
            print("  pip install tokenizers")
            self._use_hf_tokenizer = False

    def embed(self, texts: list[str]) -> np.ndarray:
        """Returns (N, 384) float32 array of L2-normalised embeddings."""
        if not self._use_hf_tokenizer:
            # Fallback: random embeddings (for testing structure only)
            vecs = np.random.randn(len(texts), 384).astype(np.float32)
            norms = np.linalg.norm(vecs, axis=1, keepdims=True)
            return vecs / np.maximum(norms, 1e-9)

        encodings = self.tokenizer.encode_batch(texts)
        input_ids = np.array([e.ids for e in encodings], dtype=np.int64)
        attention_mask = np.array([e.attention_mask for e in encodings], dtype=np.int64)
        token_type_ids = np.zeros_like(input_ids)

        outputs = self.session.run(None, {
            "input_ids": input_ids,
            "attention_mask": attention_mask,
            "token_type_ids": token_type_ids,
        })

        # Mean pooling over token embeddings
        token_embeddings = outputs[0]  # (N, seq_len, 384)
        mask = attention_mask[:, :, np.newaxis].astype(np.float32)
        pooled = (token_embeddings * mask).sum(axis=1) / mask.sum(axis=1).clip(min=1e-9)

        # L2 normalise
        norms = np.linalg.norm(pooled, axis=1, keepdims=True)
        return (pooled / np.maximum(norms, 1e-9)).astype(np.float32)

    def embed_one(self, text: str) -> np.ndarray:
        return self.embed([text])[0]


# ─── Helpers ──────────────────────────────────────────────────────────────────

def cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    """Cosine similarity between two L2-normalised vectors."""
    return float(np.dot(a, b))


def brute_force_top_k(query_vec: np.ndarray, corpus_vecs: list[tuple[str, np.ndarray]],
                      k: int) -> list[str]:
    """Returns top-K chunk keys by cosine similarity."""
    scores = [(key, cosine_similarity(query_vec, vec)) for key, vec in corpus_vecs]
    scores.sort(key=lambda x: -x[1])
    return [key for key, _ in scores[:k]]


def wait_all_indexed(r: redis.Redis, keys: list[str], timeout_s: float = 300.0):
    """Poll until all keys are indexed (or error out)."""
    deadline = time.time() + timeout_s
    pending = set(keys)
    while pending and time.time() < deadline:
        still_pending = set()
        for key in list(pending):
            try:
                status = r.execute_command("MEMSTATUS", key)
                if isinstance(status, list) and len(status) >= 1:
                    state = status[0]
                    if isinstance(state, bytes):
                        state = state.decode()
                    if state == "indexed":
                        continue
                    if state == "error":
                        print(f"  WARNING: key {key} has error status")
                        continue
            except Exception:
                pass
            still_pending.add(key)
        pending = still_pending
        if pending:
            time.sleep(0.5)

    if pending:
        print(f"  WARNING: {len(pending)} keys did not index within timeout.")


# ─── Corpus generation ────────────────────────────────────────────────────────

TOPICS = [
    "The agent encountered an obstacle while navigating the environment.",
    "Planning phase: determine optimal path to target location.",
    "Memory retrieval successful, found relevant context from previous session.",
    "Error occurred during tool invocation, retrying with fallback strategy.",
    "Observation logged: temperature sensor reading is above threshold.",
    "Task completed successfully, updating knowledge base with new information.",
    "Semantic search query returned no relevant results for this namespace.",
    "Coordination message sent to peer agent for collaborative task.",
    "Model inference latency exceeded acceptable bounds, switching to cache.",
    "New goal received: summarize recent activity and send report.",
    "Embedding computation finished, vector stored in HNSW index.",
    "Agent paused awaiting human confirmation before destructive action.",
    "Reward signal received from environment after successful action sequence.",
    "Context window approaching limit, compressing older memories.",
    "Skill library updated with new learned procedure from demonstration.",
    "Security check passed, proceeding with elevated privilege operation.",
    "Network request failed with timeout, using cached response instead.",
    "Long-term memory consolidation triggered after idle period.",
    "Working memory cleared, ready for new task assignment.",
    "Performance benchmark completed, results written to persistent store.",
]


def generate_corpus(n: int) -> list[tuple[str, str]]:
    """Returns list of (key, text) tuples."""
    corpus = []
    for i in range(n):
        topic = TOPICS[i % len(TOPICS)]
        # Add variation to make texts unique
        text = f"{topic} Instance {i}. Additional context: batch {i // 10}, sub-task {i % 10}."
        key = f"recall_corpus:{i}"
        corpus.append((key, text))
    return corpus


TEST_QUERIES = [
    "agent encountered an obstacle",
    "memory retrieval and semantic search",
    "error during tool invocation",
    "task completed successfully",
    "performance benchmark results",
    "long-term memory consolidation",
    "planning optimal path",
    "observation from environment sensors",
]


# ─── Main benchmark ───────────────────────────────────────────────────────────

def run_recall_benchmark(r: redis.Redis, embedder: LocalEmbedder,
                         corpus_size: int, top_k: int):
    print(f"\n── Recall@{top_k} (corpus_size={corpus_size}) ──────────────────────────────────")

    corpus = generate_corpus(corpus_size)

    # Populate
    print(f"  Saving {corpus_size} entries via MEMSAVE...")
    keys = []
    for key, text in corpus:
        r.execute_command("MEMSAVE", key, text)
        keys.append(key)

    # Wait for indexation
    print("  Waiting for indexation...")
    t0 = time.time()
    wait_all_indexed(r, keys, timeout_s=300)
    index_time = time.time() - t0
    print(f"  Indexation complete in {index_time:.1f}s")

    # Build local ground-truth embeddings
    print("  Computing ground-truth embeddings locally...")
    corpus_vecs: list[tuple[str, np.ndarray]] = []
    texts = [text for _, text in corpus]
    batch_size = 32
    for i in range(0, len(texts), batch_size):
        batch = texts[i:i + batch_size]
        vecs = embedder.embed(batch)
        for j, vec in enumerate(vecs):
            corpus_vecs.append((keys[i + j], vec))

    # Run recall test
    recalls = []
    print(f"  Testing {len(TEST_QUERIES)} queries...")
    for query in TEST_QUERIES:
        # Ground truth: brute force
        query_vec = embedder.embed_one(query)
        gt_keys = set(brute_force_top_k(query_vec, corpus_vecs, top_k))

        # Server result
        result = r.execute_command("MEMQUERY", "recall_corpus", query, str(top_k))
        if not result:
            recalls.append(0.0)
            continue

        # MEMQUERY returns list of [key, score, key, score, ...]
        # or flat list depending on implementation
        if isinstance(result, list):
            server_keys = set()
            for item in result:
                if isinstance(item, bytes):
                    decoded = item.decode()
                    # Filter out score-like floats
                    if not _is_float(decoded):
                        server_keys.add(decoded)
                elif isinstance(item, str) and not _is_float(item):
                    server_keys.add(item)
        else:
            server_keys = set()

        recall = len(gt_keys & server_keys) / top_k if top_k > 0 else 0.0
        recalls.append(recall)

    avg_recall = sum(recalls) / len(recalls) if recalls else 0.0
    min_recall = min(recalls) if recalls else 0.0
    max_recall = max(recalls) if recalls else 0.0

    print(f"  Recall@{top_k}: avg={avg_recall:.3f}  min={min_recall:.3f}  max={max_recall:.3f}")
    print(f"  Per-query: {[f'{r:.2f}' for r in recalls]}")
    return avg_recall


def _is_float(s: str) -> bool:
    try:
        float(s)
        return True
    except ValueError:
        return False


# ─── Entry point ──────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Recall@K benchmark for Agentis Memory")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=6399)
    parser.add_argument("--model-dir", default="../../models",
                        help="Path to directory containing model.onnx and tokenizer.json")
    parser.add_argument("--corpus-size", type=int, default=500)
    parser.add_argument("--top-k", type=int, default=10)
    args = parser.parse_args()

    print(f"Connecting to {args.host}:{args.port}...")
    r = redis.Redis(host=args.host, port=args.port, socket_timeout=60)
    try:
        r.ping()
    except Exception as e:
        print(f"ERROR: Cannot connect: {e}")
        raise SystemExit(1)

    print(f"Loading ONNX embedder from {args.model_dir}...")
    try:
        embedder = LocalEmbedder(args.model_dir)
    except FileNotFoundError as e:
        print(f"ERROR: {e}")
        raise SystemExit(1)

    # Flush namespace
    print("Flushing recall_corpus:* keys...")
    cursor = 0
    while True:
        cursor, keys = r.scan(cursor, match="recall_corpus:*", count=500)
        if keys:
            r.delete(*keys)
        if cursor == 0:
            break

    run_recall_benchmark(r, embedder, corpus_size=args.corpus_size, top_k=args.top_k)

    print("\nDone.")


if __name__ == "__main__":
    main()
