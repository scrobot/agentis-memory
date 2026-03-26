package io.agentis.memory.vector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkerTest {

    private Chunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new Chunker();
    }

    // -------------------------------------------------------------------------
    // chunk() — short text
    // -------------------------------------------------------------------------

    @Test
    void shortText_returnsSingleChunk() {
        String text = "Hello world. This is a short text.";
        List<String> chunks = chunker.chunk(text);
        assertEquals(1, chunks.size());
        assertEquals(text.strip(), chunks.get(0));
    }

    @Test
    void textExactlyAtLimit_returnsSingleChunk() {
        // 1500 characters exactly
        String text = "A".repeat(1500);
        List<String> chunks = chunker.chunk(text);
        assertEquals(1, chunks.size());
    }

    @Test
    void emptyText_returnsEmpty() {
        assertTrue(chunker.chunk("").isEmpty());
        assertTrue(chunker.chunk("   ").isEmpty());
    }

    @Test
    void nullText_returnsEmpty() {
        assertTrue(chunker.chunk(null).isEmpty());
    }

    // -------------------------------------------------------------------------
    // chunk() — long text
    // -------------------------------------------------------------------------

    @Test
    void longText_producesMultipleChunks() {
        String text = buildLongText(5000);
        List<String> chunks = chunker.chunk(text);

        assertTrue(chunks.size() > 1,
                "Expected multiple chunks for 5000-char text, got: " + chunks.size());
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 1500,
                    "Chunk length " + chunk.length() + " exceeds 1500 chars");
        }
    }

    @Test
    void longText_noChunkExceedsMaxLength() {
        // Generate 8000 chars with clear sentence boundaries
        String text = buildLongText(8000);
        List<String> chunks = chunker.chunk(text);

        assertFalse(chunks.isEmpty());
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 1500,
                    "Chunk too long: " + chunk.length());
        }
    }

    // -------------------------------------------------------------------------
    // chunk() — overlap
    // -------------------------------------------------------------------------

    @Test
    void overlap_lastSentenceOfChunkNIsFirstSentenceOfChunkNPlus1() {
        // Build text with well-defined sentences so overlap is predictable
        StringBuilder sb = new StringBuilder();
        // Each sentence is ~80 chars; we need >1500 chars to trigger chunking
        // After ~19 sentences we exceed 1500 chars and a chunk boundary forms
        for (int i = 1; i <= 40; i++) {
            sb.append("This is sentence number ").append(i)
              .append(" of the test document for overlap verification.");
            sb.append(" ");
        }
        String text = sb.toString().strip();
        List<String> chunks = chunker.chunk(text);

        assertTrue(chunks.size() >= 2, "Expected at least 2 chunks, got " + chunks.size());

        for (int i = 0; i < chunks.size() - 1; i++) {
            String current = chunks.get(i);
            String next = chunks.get(i + 1);

            // The last sentence of chunk[i] should appear at the start of chunk[i+1]
            // Extract last sentence from current chunk
            String lastSentence = lastSentence(current);
            assertTrue(next.startsWith(lastSentence),
                    "Chunk " + (i + 1) + " should start with the last sentence of chunk " + i
                            + ".\nLast sentence: [" + lastSentence + "]\nNext chunk start: ["
                            + next.substring(0, Math.min(next.length(), 100)) + "]");
        }
    }

    // -------------------------------------------------------------------------
    // extractNamespace()
    // -------------------------------------------------------------------------

    @Test
    void extractNamespace_withColon_returnsPrefix() {
        assertEquals("agent1", chunker.extractNamespace("agent1:obs"));
        assertEquals("agent1", chunker.extractNamespace("agent1:memory:sub"));
        assertEquals("ns", chunker.extractNamespace("ns:key"));
    }

    @Test
    void extractNamespace_noColon_returnsDefault() {
        assertEquals("default", chunker.extractNamespace("noprefix"));
        assertEquals("default", chunker.extractNamespace("simplekey"));
    }

    @Test
    void extractNamespace_colonAtStart_returnsDefault() {
        // colon is at position 0, no meaningful prefix → default
        assertEquals("default", chunker.extractNamespace(":key"));
    }

    @Test
    void extractNamespace_null_returnsDefault() {
        assertEquals("default", chunker.extractNamespace(null));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a text of approximately {@code targetLength} characters, composed of
     * short sentences separated by ". ".
     */
    private String buildLongText(int targetLength) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (sb.length() < targetLength) {
            sb.append("The quick brown fox jumps over the lazy dog, sentence ")
              .append(i++)
              .append(". ");
        }
        return sb.toString().strip();
    }

    /** Returns the last sentence from a chunk (split on '. ', '! ', '? '). */
    private String lastSentence(String chunk) {
        String[] parts = chunk.split("(?<=[.!?])\\s+");
        return parts[parts.length - 1].strip();
    }
}
