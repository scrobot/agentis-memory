package io.agentis.memory.vector;

import io.agentis.memory.config.ServerConfig;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Coordinates the indexing pipeline: Chunker -> Embedder -> HNSW Index.
 * Tracks indexing status and handles async execution.
 */
@Singleton
public class VectorEngine {
    private static final Logger log = LoggerFactory.getLogger(VectorEngine.class);

    private final Chunker chunker;
    private final Embedder embedder;
    private final HnswIndex hnswIndex;
    private final ExecutorService executor;
    private final Map<String, IndexingStatus> statusMap = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> pendingTasks = new ConcurrentHashMap<>();
    private final ServerConfig config;

    public record IndexingStatus(String status, int chunkCount, int dimensions, long lastUpdatedMs) {}

    @Inject
    public VectorEngine(Chunker chunker, Embedder embedder, HnswIndex hnswIndex, ServerConfig config) {
        this.chunker = chunker;
        this.embedder = embedder;
        this.hnswIndex = hnswIndex;
        this.config = config;
        this.executor = Executors.newFixedThreadPool(config.embeddingThreads, r -> {
            Thread t = new Thread(r, "vector-engine-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public void indexAsync(String key, String text) {
        // Cancel any existing task for this key
        Future<?> existing = pendingTasks.get(key);
        if (existing != null) {
            existing.cancel(true);
        }

        statusMap.put(key, new IndexingStatus("pending", 0, 0, System.currentTimeMillis()));

        // We need a wrapper to be able to refer to the future inside the task
        // But since executor.submit returns a Future, we can't easily refer to it inside the Runnable.
        // We use a final reference by submitting and then storing.
        
        Future<?>[] taskRef = new Future<?>[1];
        taskRef[0] = executor.submit(() -> {
            try {
                String namespace = chunker.extractNamespace(key);
                List<String> textChunks = chunker.chunk(text);

                if (textChunks.size() > config.maxChunksPerKey) {
                    log.warn("Key {} exceeds max chunk count ({} > {}). Truncating.", 
                             key, textChunks.size(), config.maxChunksPerKey);
                    textChunks = textChunks.subList(0, config.maxChunksPerKey);
                }

                if (textChunks.isEmpty()) {
                    statusMap.put(key, new IndexingStatus("indexed", 0, 0, System.currentTimeMillis()));
                    return;
                }

                // Remove old index entries before adding new ones
                hnswIndex.remove(key);

                List<float[]> embeddings = embedder.embedBatch(textChunks);
                int dims = embeddings.get(0).length;

                List<Chunk> chunks = new ArrayList<>(textChunks.size());
                for (int i = 0; i < textChunks.size(); i++) {
                    chunks.add(new Chunk(key, i, textChunks.get(i), embeddings.get(i), namespace));
                }
                hnswIndex.addBatch(chunks);

                statusMap.put(key, new IndexingStatus("indexed", textChunks.size(), dims, System.currentTimeMillis()));
            } catch (InterruptedException e) {
                log.debug("Indexing task for key {} was cancelled", key);
                // Don't update statusMap, let it be cleaned up or overwritten
            } catch (Exception e) {
                log.error("Failed to index key {}", key, e);
                statusMap.put(key, new IndexingStatus("error", 0, 0, System.currentTimeMillis()));
            } finally {
                // Ensure task is removed from pendingTasks even if failed
                pendingTasks.remove(key, taskRef[0]);
            }
        });

        pendingTasks.put(key, taskRef[0]);
    }

    public IndexingStatus getStatus(String key) {
        // If not in statusMap, check if it's already in HNSW (e.g. after restart)
        IndexingStatus status = statusMap.get(key);
        if (status == null) {
            if (hnswIndex.hasPendingOrIndexed(key)) {
                // Approximate status since we don't persist statusMap yet
                return new IndexingStatus("indexed", -1, 384, 0);
            }
            return null;
        }
        return status;
    }

    public void cancelAndRemove(String key) {
        Future<?> task = pendingTasks.remove(key);
        if (task != null) {
            task.cancel(true);
        }
        statusMap.remove(key);
        hnswIndex.remove(key);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("VectorEngine executor did not terminate gracefully");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
