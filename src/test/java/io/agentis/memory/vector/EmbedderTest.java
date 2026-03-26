package io.agentis.memory.vector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmbedderTest {
    private Embedder embedder;
    // Resolve models/ relative to project root. Gradle runs tests from the project root.
    private static final Path MODELS_DIR = Paths.get("models");
    @BeforeAll
    void setUp() throws Exception {
        embedder = new Embedder(MODELS_DIR, 1);
    }
    @AfterAll
    void tearDown() throws Exception {
        if (embedder != null) {
            embedder.close();
        }
    }
    @Test
    void modelLoadsWithoutException() {
        assertNotNull(embedder);
    }
    @Test
    void embedReturns384DimVector() throws Exception {
        float[] vec = embedder.embed("hello world");
        assertNotNull(vec);
        assertEquals(384, vec.length);
    }
    @Test
    void embeddingIsL2Normalized() throws Exception {
        float[] vec = embedder.embed("hello world");
        double norm = 0.0;
        for (float v : vec) {
            norm += (double) v * v;
        }
        norm = Math.sqrt(norm);
        assertEquals(1.0, norm, 1e-5, "L2 norm should be ~1.0");
    }
    @Test
    void semanticallySimilarTextsHaveHighCosine() throws Exception {
        float[] a = embedder.embed("the cat sat on the mat");
        float[] b = embedder.embed("a kitten was sitting on the rug");
        double similarity = cosineSimilarity(a, b);
        assertTrue(similarity > 0.6,
            "Similar texts should have cosine > 0.6, got: " + similarity);
    }
    @Test
    void semanticallyDissimilarTextsHaveLowCosine() throws Exception {
        float[] a = embedder.embed("the cat sat on the mat");
        float[] b = embedder.embed("stock market crashed today");
        double similarity = cosineSimilarity(a, b);
        assertTrue(similarity < 0.3,
            "Dissimilar texts should have cosine < 0.3, got: " + similarity);
    }
    @Test
    void embedBatchReturnsCorrectCount() throws Exception {
        List<float[]> result = embedder.embedBatch(List.of("hello", "world"));
        assertEquals(2, result.size());
        assertEquals(384, result.get(0).length);
        assertEquals(384, result.get(1).length);
    }
    @Test
    void batchEmbeddingConsistentWithSingleEmbed() throws Exception {
        String text = "hello";
        float[] single = embedder.embed(text);
        float[] batch = embedder.embedBatch(List.of(text)).get(0);
        assertEquals(single.length, batch.length);
        for (int i = 0; i < single.length; i++) {
            assertEquals(single[i], batch[i], 1e-5f,
                "Batch embedding differs from single at index " + i);
        }
    }
    // --- utility ---
    static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom < 1e-12 ? 0.0 : dot / denom;
    }
}
