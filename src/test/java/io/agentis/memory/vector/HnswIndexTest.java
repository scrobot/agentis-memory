package io.agentis.memory.vector;

import io.agentis.memory.config.ServerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class HnswIndexTest {

    private HnswIndex index;

    @BeforeEach
    void setUp() {
        ServerConfig config = new ServerConfig();
        config.hnswM = 16;
        config.hnswEfConstruction = 100;
        index = new HnswIndex(config);
    }

    // -------------------------------------------------------------------------
    // Basic add + search
    // -------------------------------------------------------------------------

    @Test
    void add100Chunks_searchReturnsTopK() {
        Random rng = new Random(42);
        // Chunks 0..99 are random
        for (int i = 0; i < 100; i++) {
            Chunk c = randomChunk("key:" + i, i, "default", rng);
            index.add(c);
        }

        // Use the vector of chunk 0 as the query — it should be the top result
        float[] queryVector = randomNormalized(384, new Random(42)); // same seed → same as chunk 0
        List<HnswIndex.SearchResult_> results = index.search(queryVector, "default", 5);

        assertFalse(results.isEmpty(), "Expected at least 1 result");
        assertTrue(results.size() <= 5, "Expected at most 5 results");
        // Top result should be chunk 0 (exact match)
        assertEquals("key:0", results.get(0).parentKey(),
                "Top result should be the exact query vector (chunk 0)");
    }

    @Test
    void emptyIndex_returnsEmptyList() {
        float[] query = randomNormalized(384, new Random(1));
        List<HnswIndex.SearchResult_> results = index.search(query, "ALL", 5);
        assertTrue(results.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Namespace filtering
    // -------------------------------------------------------------------------

    @Test
    void namespaceFilter_excludesOtherNamespaces() {
        Random rng = new Random(7);
        // Add 20 chunks to "agent1" and 20 chunks to "agent2"
        for (int i = 0; i < 20; i++) {
            index.add(randomChunk("agent1:key" + i, i, "agent1", rng));
            index.add(randomChunk("agent2:key" + i, i, "agent2", rng));
        }

        float[] query = randomNormalized(384, new Random(99));
        List<HnswIndex.SearchResult_> results = index.search(query, "agent1", 10);

        assertFalse(results.isEmpty());
        for (HnswIndex.SearchResult_ r : results) {
            assertTrue(r.parentKey().startsWith("agent1:"),
                    "Expected only agent1 results, got parentKey=" + r.parentKey());
        }
    }

    @Test
    void namespaceALL_returnsChunksFromAllNamespaces() {
        Random rng = new Random(13);
        for (int i = 0; i < 10; i++) {
            index.add(randomChunk("agent1:k" + i, i, "agent1", rng));
            index.add(randomChunk("agent2:k" + i, i, "agent2", rng));
        }

        float[] query = randomNormalized(384, new Random(77));
        List<HnswIndex.SearchResult_> results = index.search(query, "ALL", 20);

        Set<String> namespaces = results.stream()
                .map(r -> r.parentKey().split(":")[0])
                .collect(Collectors.toSet());
        assertTrue(namespaces.contains("agent1"), "ALL search should include agent1");
        assertTrue(namespaces.contains("agent2"), "ALL search should include agent2");
    }

    // -------------------------------------------------------------------------
    // remove()
    // -------------------------------------------------------------------------

    @Test
    void remove_deletedKeyNotReturnedInSearch() {
        Random rng = new Random(3);
        // Add a well-known chunk
        float[] targetVector = randomNormalized(384, new Random(999));
        Chunk target = new Chunk("agent1:target", 0, "target text", targetVector, "agent1");
        index.add(target);

        // Add 20 other random chunks
        for (int i = 0; i < 20; i++) {
            index.add(randomChunk("agent1:other" + i, i, "agent1", rng));
        }

        // Verify target is found before removal
        List<HnswIndex.SearchResult_> before = index.search(targetVector, "ALL", 5);
        assertTrue(before.stream().anyMatch(r -> "agent1:target".equals(r.parentKey())),
                "Target should be in results before removal");

        // Remove the target key
        index.remove("agent1:target");

        // Verify target is NOT found after removal
        List<HnswIndex.SearchResult_> after = index.search(targetVector, "ALL", 5);
        assertTrue(after.stream().noneMatch(r -> "agent1:target".equals(r.parentKey())),
                "Target should NOT be in results after removal");
    }

    @Test
    void remove_nonExistentKey_noException() {
        assertDoesNotThrow(() -> index.remove("does:not:exist"));
    }

    @Test
    void remove_multipleChunksForKey_allRemovedFromSearch() {
        float[] vec1 = randomNormalized(384, new Random(11));
        float[] vec2 = randomNormalized(384, new Random(12));
        float[] vec3 = randomNormalized(384, new Random(13));

        index.add(new Chunk("mykey:doc", 0, "chunk 0", vec1, "mykey"));
        index.add(new Chunk("mykey:doc", 1, "chunk 1", vec2, "mykey"));
        index.add(new Chunk("mykey:doc", 2, "chunk 2", vec3, "mykey"));

        // Add padding so the index is not trivially empty after removal
        Random rng = new Random(42);
        for (int i = 0; i < 10; i++) {
            index.add(randomChunk("other:k" + i, i, "other", rng));
        }

        index.remove("mykey:doc");

        for (float[] v : new float[][]{vec1, vec2, vec3}) {
            List<HnswIndex.SearchResult_> results = index.search(v, "ALL", 10);
            assertTrue(results.stream().noneMatch(r -> "mykey:doc".equals(r.parentKey())),
                    "Removed chunks should not appear in search");
        }
    }

    // -------------------------------------------------------------------------
    // hasPendingOrIndexed()
    // -------------------------------------------------------------------------

    @Test
    void hasPendingOrIndexed_trueAfterAdd_falseAfterRemove() {
        Random rng = new Random(5);
        Chunk c = randomChunk("ns:key", 0, "ns", rng);
        assertFalse(index.hasPendingOrIndexed("ns:key"));

        index.add(c);
        assertTrue(index.hasPendingOrIndexed("ns:key"));

        index.remove("ns:key");
        assertFalse(index.hasPendingOrIndexed("ns:key"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Chunk randomChunk(String parentKey, int idx, String namespace, Random rng) {
        float[] vector = randomNormalized(384, rng);
        return new Chunk(parentKey, idx, "text for " + parentKey + " chunk " + idx, vector, namespace);
    }

    private float[] randomNormalized(int dim, Random rng) {
        float[] v = new float[dim];
        double norm = 0.0;
        for (int i = 0; i < dim; i++) {
            v[i] = rng.nextFloat() * 2 - 1;
            norm += (double) v[i] * v[i];
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < dim; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }
}
