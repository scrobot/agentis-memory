package io.agentis.memory.vector;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits text into overlapping chunks suitable for embedding.
 * <p>
 * Strategy:
 * <ul>
 *   <li>Split on sentence boundaries (., !, ?, blank lines)</li>
 *   <li>Group sentences into chunks of ~1000-1500 characters (proxy for 200-300 tokens)</li>
 *   <li>1-sentence overlap between consecutive chunks</li>
 *   <li>Text shorter than 1500 characters is returned as a single chunk without splitting</li>
 * </ul>
 */
@Singleton
public class Chunker {

    private static final int MAX_CHUNK_CHARS = 1500;
    // Sentence boundary: end of ., !, ? followed by whitespace/EOL, or a blank line
    private static final Pattern SENTENCE_SPLIT = Pattern.compile(
            "(?<=[.!?])\\s+|\\n{2,}"
    );

    /**
     * Splits {@code text} into a list of overlapping chunk strings.
     */
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // Short texts are a single chunk
        if (text.length() <= MAX_CHUNK_CHARS) {
            return List.of(text.strip());
        }

        List<String> sentences = splitIntoSentences(text);
        if (sentences.isEmpty()) {
            return List.of(text.strip());
        }
        if (sentences.size() == 1) {
            return List.of(sentences.get(0));
        }

        return groupIntoChunks(sentences);
    }

    /**
     * Extracts the namespace prefix from a key: everything before the first ':', or "default".
     */
    public String extractNamespace(String key) {
        if (key == null) {
            return "default";
        }
        int colon = key.indexOf(':');
        return (colon > 0) ? key.substring(0, colon) : "default";
    }

    // -------------------------------------------------------------------------

    private List<String> splitIntoSentences(String text) {
        String[] parts = SENTENCE_SPLIT.split(text.strip());
        List<String> sentences = new ArrayList<>(parts.length);
        for (String part : parts) {
            String s = part.strip();
            if (!s.isEmpty()) {
                sentences.add(s);
            }
        }
        return sentences;
    }

    private List<String> groupIntoChunks(List<String> sentences) {
        List<String> chunks = new ArrayList<>();
        int i = 0;

        while (i < sentences.size()) {
            StringBuilder buf = new StringBuilder();
            int start = i;

            // Accumulate sentences until we'd exceed MAX_CHUNK_CHARS
            while (i < sentences.size()) {
                String sentence = sentences.get(i);
                int addedLength = (buf.isEmpty() ? 0 : 1) + sentence.length();
                if (!buf.isEmpty() && buf.length() + addedLength > MAX_CHUNK_CHARS) {
                    break;
                }
                if (!buf.isEmpty()) {
                    buf.append(' ');
                }
                buf.append(sentence);
                i++;
            }

            // Safety: if a single sentence exceeds MAX_CHUNK_CHARS, consume it anyway
            if (i == start) {
                buf.append(sentences.get(i));
                i++;
            }

            chunks.add(buf.toString());

            // 1-sentence overlap: step back one sentence for the next chunk
            if (i < sentences.size()) {
                i--;
            }
        }

        return chunks;
    }
}
