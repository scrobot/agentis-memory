package io.agentis.memory.vector;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.*;

/**
 * Pure Java BERT WordPiece tokenizer. Zero native dependencies.
 * Loads vocabulary from HuggingFace tokenizer.json format.
 * Compatible with all-MiniLM-L6-v2 (bert-base-uncased vocab, 30522 tokens).
 *
 * <p>Replaces DJL HuggingFaceTokenizer which uses Rust JNI and crashes in GraalVM native-image.
 */
public class BertTokenizer {

    private final Map<String, Integer> vocab;
    private final int clsId;
    private final int sepId;
    private final int unkId;
    private final int padId;
    private final int maxLength;

    public BertTokenizer(Map<String, Integer> vocab, int maxLength) {
        this.vocab = vocab;
        this.clsId = vocab.getOrDefault("[CLS]", 101);
        this.sepId = vocab.getOrDefault("[SEP]", 102);
        this.unkId = vocab.getOrDefault("[UNK]", 100);
        this.padId = vocab.getOrDefault("[PAD]", 0);
        this.maxLength = maxLength;
    }

    /**
     * Load tokenizer from a HuggingFace tokenizer.json file.
     * Extracts model.vocab from the JSON.
     */
    public static BertTokenizer fromTokenizerJson(Path path, int maxLength) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        Map<String, Integer> vocab = parseVocabFromJson(json);
        return new BertTokenizer(vocab, maxLength);
    }

    /**
     * Tokenize a single text. Returns token IDs including [CLS] and [SEP].
     */
    public Encoding encode(String text) {
        List<String> tokens = tokenize(text);

        // Truncate to maxLength - 2 (leave room for [CLS] and [SEP])
        if (tokens.size() > maxLength - 2) {
            tokens = tokens.subList(0, maxLength - 2);
        }

        int seqLen = tokens.size() + 2; // +[CLS] +[SEP]
        long[] inputIds = new long[seqLen];
        long[] attentionMask = new long[seqLen];
        long[] tokenTypeIds = new long[seqLen];

        // [CLS]
        inputIds[0] = clsId;
        attentionMask[0] = 1;
        tokenTypeIds[0] = 0;

        // Tokens
        for (int i = 0; i < tokens.size(); i++) {
            inputIds[i + 1] = vocab.getOrDefault(tokens.get(i), unkId);
            attentionMask[i + 1] = 1;
            tokenTypeIds[i + 1] = 0;
        }

        // [SEP]
        inputIds[seqLen - 1] = sepId;
        attentionMask[seqLen - 1] = 1;
        tokenTypeIds[seqLen - 1] = 0;

        return new Encoding(inputIds, attentionMask, tokenTypeIds);
    }

    /**
     * Batch encode multiple texts.
     */
    public Encoding[] batchEncode(String[] texts) {
        Encoding[] encodings = new Encoding[texts.length];
        for (int i = 0; i < texts.length; i++) {
            encodings[i] = encode(texts[i]);
        }
        return encodings;
    }

    /**
     * Full BERT tokenization pipeline: normalize → pre-tokenize → WordPiece.
     */
    List<String> tokenize(String text) {
        // 1. Normalize: lowercase, clean, strip accents
        text = normalize(text);

        // 2. Pre-tokenize: split on whitespace and punctuation
        List<String> words = preTokenize(text);

        // 3. WordPiece: split each word into subword tokens
        List<String> tokens = new ArrayList<>();
        for (String word : words) {
            tokens.addAll(wordPiece(word));
        }
        return tokens;
    }

    /**
     * BERT normalizer: lowercase, clean control chars, strip accents.
     */
    private String normalize(String text) {
        // Lowercase
        text = text.toLowerCase(Locale.ROOT);

        // Clean: remove control characters, replace whitespace with space
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 0 || c == 0xFFFD || isControl(c)) continue;
            if (isWhitespace(c)) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        text = sb.toString();

        // Strip accents: NFD decompose then remove combining marks
        text = Normalizer.normalize(text, Normalizer.Form.NFD);
        sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * BERT pre-tokenizer: split on whitespace and punctuation.
     * Also adds spaces around Chinese characters.
     */
    private List<String> preTokenize(String text) {
        // Add whitespace around Chinese characters
        StringBuilder sb = new StringBuilder(text.length() + 32);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChinese(c)) {
                sb.append(' ').append(c).append(' ');
            } else {
                sb.append(c);
            }
        }
        text = sb.toString();

        // Split on whitespace, then split each token on punctuation
        List<String> result = new ArrayList<>();
        for (String word : text.strip().split("\\s+")) {
            if (word.isEmpty()) continue;
            // Split word into runs of punctuation and non-punctuation
            sb = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                if (isPunctuation(c)) {
                    if (!sb.isEmpty()) {
                        result.add(sb.toString());
                        sb = new StringBuilder();
                    }
                    result.add(String.valueOf(c));
                } else {
                    sb.append(c);
                }
            }
            if (!sb.isEmpty()) {
                result.add(sb.toString());
            }
        }
        return result;
    }

    /**
     * WordPiece: greedy longest-match-first subword tokenization.
     * Continuation tokens prefixed with "##".
     */
    private List<String> wordPiece(String word) {
        if (word.length() > 200) {
            // Safety: very long "words" are unlikely real tokens
            return List.of("[UNK]");
        }

        List<String> tokens = new ArrayList<>();
        int start = 0;
        while (start < word.length()) {
            int end = word.length();
            String found = null;
            while (start < end) {
                String substr = (start == 0)
                        ? word.substring(start, end)
                        : "##" + word.substring(start, end);
                if (vocab.containsKey(substr)) {
                    found = substr;
                    break;
                }
                end--;
            }
            if (found == null) {
                tokens.add("[UNK]");
                break; // Can't tokenize further
            }
            tokens.add(found);
            start = end;
        }
        return tokens;
    }

    // ── Character classification helpers ────────────────────────────────────

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r'
                || Character.getType(c) == Character.SPACE_SEPARATOR;
    }

    private static boolean isControl(char c) {
        if (c == '\t' || c == '\n' || c == '\r') return false;
        int type = Character.getType(c);
        return type == Character.CONTROL || type == Character.FORMAT;
    }

    private static boolean isPunctuation(char c) {
        int cp = c;
        // ASCII punctuation ranges
        if ((cp >= 33 && cp <= 47) || (cp >= 58 && cp <= 64)
                || (cp >= 91 && cp <= 96) || (cp >= 123 && cp <= 126)) {
            return true;
        }
        int type = Character.getType(cp);
        return type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.CONNECTOR_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION;
    }

    private static boolean isChinese(char c) {
        int cp = c;
        return (cp >= 0x4E00 && cp <= 0x9FFF)
                || (cp >= 0x3400 && cp <= 0x4DBF)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0x2F800 && cp <= 0x2FA1F);
    }

    // ── JSON vocab parser ───────────────────────────────────────────────────

    /**
     * Minimal JSON parser to extract model.vocab from tokenizer.json.
     * Avoids dependency on Jackson/Gson.
     */
    private static Map<String, Integer> parseVocabFromJson(String json) {
        // Find "vocab": { ... } inside "model": { ... }
        int modelIdx = json.indexOf("\"model\"");
        if (modelIdx == -1) throw new IllegalArgumentException("No 'model' key in tokenizer.json");

        int vocabIdx = json.indexOf("\"vocab\"", modelIdx);
        if (vocabIdx == -1) throw new IllegalArgumentException("No 'vocab' key in model");

        // Find the opening brace of vocab object
        int braceStart = json.indexOf('{', vocabIdx);
        if (braceStart == -1) throw new IllegalArgumentException("No vocab object found");

        // Find matching closing brace
        int depth = 1;
        int pos = braceStart + 1;
        while (pos < json.length() && depth > 0) {
            char c = json.charAt(pos);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            else if (c == '"') {
                // Skip string content
                pos++;
                while (pos < json.length() && json.charAt(pos) != '"') {
                    if (json.charAt(pos) == '\\') pos++; // skip escaped char
                    pos++;
                }
            }
            pos++;
        }

        String vocabJson = json.substring(braceStart, pos);

        // Parse key-value pairs: "token": id
        Map<String, Integer> vocab = new HashMap<>(32000);
        int i = 1; // skip opening brace
        while (i < vocabJson.length()) {
            // Find next key
            int keyStart = vocabJson.indexOf('"', i);
            if (keyStart == -1) break;
            int keyEnd = findClosingQuote(vocabJson, keyStart + 1);
            if (keyEnd == -1) break;

            String key = unescapeJson(vocabJson.substring(keyStart + 1, keyEnd));

            // Find colon then value
            int colon = vocabJson.indexOf(':', keyEnd);
            if (colon == -1) break;

            // Read integer value
            int numStart = colon + 1;
            while (numStart < vocabJson.length() && vocabJson.charAt(numStart) == ' ') numStart++;
            int numEnd = numStart;
            while (numEnd < vocabJson.length() && (Character.isDigit(vocabJson.charAt(numEnd)) || vocabJson.charAt(numEnd) == '-')) numEnd++;

            int value = Integer.parseInt(vocabJson.substring(numStart, numEnd).trim());
            vocab.put(key, value);

            i = numEnd;
        }

        return vocab;
    }

    private static int findClosingQuote(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) == '\\') { i++; continue; }
            if (s.charAt(i) == '"') return i;
        }
        return -1;
    }

    private static String unescapeJson(String s) {
        if (s.indexOf('\\') == -1) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        String hex = s.substring(i + 1, i + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    }
                    default -> { sb.append('\\'); sb.append(next); }
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ── Encoding record ─────────────────────────────────────────────────────

    public record Encoding(long[] inputIds, long[] attentionMask, long[] tokenTypeIds) {
        public long[] getIds() { return inputIds; }
        public long[] getAttentionMask() { return attentionMask; }
        public long[] getTypeIds() { return tokenTypeIds; }
    }
}
