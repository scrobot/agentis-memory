package io.agentis.memory.vector;

import io.agentis.memory.config.ServerConfig;
import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.GraphSearcher;
import io.github.jbellis.jvector.graph.ListRandomAccessVectorValues;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
import io.github.jbellis.jvector.graph.SearchResult;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe HNSW vector index backed by jvector.
 * <p>
 * Design notes:
 * <ul>
 *   <li>Write operations (add/remove) are serialised via the write lock and rebuild the index.</li>
 *   <li>Read operations (search) acquire the read lock and snapshot the current index reference.</li>
 *   <li>Namespace filtering over-fetches k×3 from the HNSW graph, then post-filters by namespace.</li>
 * </ul>
 */
@Singleton
public class HnswIndex {

    private static final Logger log = LoggerFactory.getLogger(HnswIndex.class);
    private static final int DIMENSIONS = 384;
    private static final VectorTypeSupport VTS =
            VectorizationProvider.getInstance().getVectorTypeSupport();

    private final int hnswM;
    private final int hnswEfConstruction;

    // Guarded by lock
    /** All chunks in insertion order. ordinal == index in this list. */
    private final List<Chunk> chunks = new ArrayList<>();
    /** ordinal → chunk, for fast node-score lookup during search */
    private final List<VectorFloat<?>> vectors = new ArrayList<>();
    /** parentKey → set of ordinals, for fast removal */
    private final Map<String, List<Integer>> keyToOrdinals = new HashMap<>();
    /** Ordinals marked as logically deleted (not yet compacted). */
    private final Set<Integer> deletedOrdinals =
            Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /** Current live graph index; rebuilt after every structural change. */
    private volatile OnHeapGraphIndex graphIndex = null;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public HnswIndex(ServerConfig config) {
        this.hnswM = config.hnswM;
        this.hnswEfConstruction = config.hnswEfConstruction;
    }

    // -------------------------------------------------------------------------
    // Write operations
    // -------------------------------------------------------------------------

    /**
     * Adds a chunk to the index.  Rebuilds the HNSW graph.
     */
    public void add(Chunk chunk) {
        lock.writeLock().lock();
        try {
            int ordinal = chunks.size();
            chunks.add(chunk);
            vectors.add(VTS.createFloatVector(chunk.vector()));
            keyToOrdinals.computeIfAbsent(chunk.parentKey(), k -> new ArrayList<>()).add(ordinal);
            // Rebuilding after every chunk is expensive. 
            // For now it is okay, but if indexing many chunks, it's a bottleneck.
            rebuildIndex();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Adds multiple chunks for the same key. Rebuilds once.
     */
    public void addBatch(List<Chunk> batch) {
        if (batch.isEmpty()) return;
        lock.writeLock().lock();
        try {
            for (Chunk chunk : batch) {
                int ordinal = chunks.size();
                chunks.add(chunk);
                vectors.add(VTS.createFloatVector(chunk.vector()));
                keyToOrdinals.computeIfAbsent(chunk.parentKey(), k -> new ArrayList<>()).add(ordinal);
            }
            rebuildIndex();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes all chunks for the given {@code parentKey} and rebuilds the index.
     */
    public void remove(String parentKey) {
        lock.writeLock().lock();
        try {
            List<Integer> ordinals = keyToOrdinals.remove(parentKey);
            if (ordinals == null || ordinals.isEmpty()) {
                return;
            }
            deletedOrdinals.addAll(ordinals);
            // Compact when deleted fraction exceeds 25 % to keep memory bounded
            if (deletedOrdinals.size() > chunks.size() / 4) {
                compact();
            } else {
                rebuildIndex();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Read operations
    // -------------------------------------------------------------------------

    /**
     * Returns whether any chunks exist (or are pending) for the given key.
     */
    public boolean hasPendingOrIndexed(String parentKey) {
        lock.readLock().lock();
        try {
            return keyToOrdinals.containsKey(parentKey);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Searches for the top-{@code k} semantically closest chunks.
     *
     * @param queryVector 384-dim L2-normalized query embedding
     * @param namespace   namespace to restrict results to, or {@code "ALL"} for no filter
     * @param k           maximum number of results (1–1000)
     * @return list of at most k results ordered by descending score
     */
    public List<SearchResult_> search(float[] queryVector, String namespace, int k) {
        // Snapshot the live index under read lock
        final OnHeapGraphIndex idx;
        final List<VectorFloat<?>> vecSnapshot;
        final List<Chunk> chunkSnapshot;
        final Set<Integer> deletedSnapshot;

        lock.readLock().lock();
        try {
            idx = graphIndex;
            if (idx == null || chunks.isEmpty()) {
                return List.of();
            }
            vecSnapshot = List.copyOf(vectors);
            chunkSnapshot = List.copyOf(chunks);
            deletedSnapshot = Set.copyOf(deletedOrdinals);
        } finally {
            lock.readLock().unlock();
        }

        boolean filterByNamespace = !"ALL".equalsIgnoreCase(namespace);
        int overFetch = Math.min(k * 3, vecSnapshot.size());

        VectorFloat<?> queryVF = VTS.createFloatVector(queryVector);
        RandomAccessVectorValues ravv =
                new ListRandomAccessVectorValues(vecSnapshot, DIMENSIONS);

        SearchResult result = GraphSearcher.search(queryVF, overFetch, ravv,
                VectorSimilarityFunction.COSINE, idx, Bits.ALL);

        List<SearchResult_> out = new ArrayList<>(k);
        for (SearchResult.NodeScore ns : result.getNodes()) {
            int ordinal = ns.node;
            if (deletedSnapshot.contains(ordinal)) {
                continue;
            }
            Chunk c = chunkSnapshot.get(ordinal);
            if (filterByNamespace && !namespace.equals(c.namespace())) {
                continue;
            }
            out.add(new SearchResult_(c.parentKey(), c.text(), ns.score));
            if (out.size() == k) {
                break;
            }
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    public void save(Path path) throws IOException {
        lock.readLock().lock();
        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path.toFile())))) {
                out.writeInt(chunks.size());
                for (Chunk chunk : chunks) {
                    out.writeUTF(chunk.parentKey());
                    out.writeInt(chunk.index());
                    out.writeUTF(chunk.text());
                    out.writeInt(chunk.vector().length);
                    for (float f : chunk.vector()) {
                        out.writeFloat(f);
                    }
                    out.writeUTF(chunk.namespace());
                }
                out.writeInt(deletedOrdinals.size());
                for (Integer ordinal : deletedOrdinals) {
                    out.writeInt(ordinal);
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public void load(Path path) throws IOException {
        lock.writeLock().lock();
        try {
            chunks.clear();
            vectors.clear();
            keyToOrdinals.clear();
            deletedOrdinals.clear();

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(path.toFile())))) {
                int numChunks = in.readInt();
                for (int i = 0; i < numChunks; i++) {
                    String parentKey = in.readUTF();
                    int index = in.readInt();
                    String text = in.readUTF();
                    int vecLen = in.readInt();
                    float[] vector = new float[vecLen];
                    for (int j = 0; j < vecLen; j++) {
                        vector[j] = in.readFloat();
                    }
                    String namespace = in.readUTF();

                    Chunk chunk = new Chunk(parentKey, index, text, vector, namespace);
                    chunks.add(chunk);
                    vectors.add(VTS.createFloatVector(vector));
                    keyToOrdinals.computeIfAbsent(parentKey, k -> new ArrayList<>()).add(i);
                }

                int numDeleted = in.readInt();
                for (int i = 0; i < numDeleted; i++) {
                    deletedOrdinals.add(in.readInt());
                }
            }
            rebuildIndex();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Must be called under write lock. */
    private void rebuildIndex() {
        List<VectorFloat<?>> live = new ArrayList<>(vectors.size());
        Map<Integer, Integer> newOrdinal = new HashMap<>();
        int idx = 0;
        for (int i = 0; i < vectors.size(); i++) {
            if (!deletedOrdinals.contains(i)) {
                newOrdinal.put(i, idx++);
                live.add(vectors.get(i));
            }
        }
        if (live.isEmpty()) {
            graphIndex = null;
            return;
        }

        ListRandomAccessVectorValues ravv = new ListRandomAccessVectorValues(live, DIMENSIONS);
        try (GraphIndexBuilder builder = new GraphIndexBuilder(
                ravv,
                VectorSimilarityFunction.COSINE,
                hnswM,
                hnswEfConstruction,
                1.2f,
                1.4f)) {

            for (int i = 0; i < live.size(); i++) {
                builder.addGraphNode(i, live.get(i));
            }
            graphIndex = builder.getGraph();
        } catch (Exception e) {
            log.error("Failed to rebuild HNSW index", e);
        }
    }

    /**
     * Physically removes deleted entries and renumbers ordinals.
     * Must be called under write lock.
     */
    private void compact() {
        List<Chunk> newChunks = new ArrayList<>();
        List<VectorFloat<?>> newVectors = new ArrayList<>();
        Map<String, List<Integer>> newKeyToOrdinals = new HashMap<>();

        for (int i = 0; i < chunks.size(); i++) {
            if (!deletedOrdinals.contains(i)) {
                int newOrd = newChunks.size();
                Chunk c = chunks.get(i);
                newChunks.add(c);
                newVectors.add(vectors.get(i));
                newKeyToOrdinals.computeIfAbsent(c.parentKey(), k -> new ArrayList<>()).add(newOrd);
            }
        }

        chunks.clear();
        chunks.addAll(newChunks);
        vectors.clear();
        vectors.addAll(newVectors);
        keyToOrdinals.clear();
        keyToOrdinals.putAll(newKeyToOrdinals);
        deletedOrdinals.clear();

        rebuildIndex();
    }

    // -------------------------------------------------------------------------
    // Result type
    // -------------------------------------------------------------------------

    /**
     * Single search result.
     *
     * @param parentKey the MEMSAVE key that produced this chunk
     * @param chunkText the text of the matched chunk
     * @param score     cosine similarity score (higher = more similar)
     */
    public record SearchResult_(String parentKey, String chunkText, float score) {}
}
