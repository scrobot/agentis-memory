package io.agentis.memory.vector;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import io.agentis.memory.config.ServerConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates 384-dim normalized float vectors from text using all-MiniLM-L6-v2 via ONNX Runtime.
 * Uses pure Java BertTokenizer (no native JNI dependencies).
 * Thread-safe for concurrent inference calls.
 */
@Singleton
public class Embedder implements AutoCloseable {

    private final OrtEnvironment env;
    private final OrtSession session;
    private final BertTokenizer tokenizer;

    @Inject
    public Embedder(ServerConfig config) throws Exception {
        this.env = OrtEnvironment.getEnvironment();

        try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
            opts.setIntraOpNumThreads(config.embeddingThreads);
            Path modelDir = config.modelPath != null ? config.modelPath : Path.of("models");
            this.session = env.createSession(modelDir.resolve("model.onnx").toString(), opts);
            this.tokenizer = BertTokenizer.fromTokenizerJson(modelDir.resolve("tokenizer.json"), 512);
        }
    }

    /**
     * @param modelDir        directory containing model.onnx, tokenizer.json
     * @param embeddingThreads number of intra-op threads for ONNX inference
     */
    public Embedder(Path modelDir, int embeddingThreads) throws Exception {
        this.env = OrtEnvironment.getEnvironment();

        try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
            opts.setIntraOpNumThreads(embeddingThreads);
            this.session = env.createSession(modelDir.resolve("model.onnx").toString(), opts);
        }

        this.tokenizer = BertTokenizer.fromTokenizerJson(modelDir.resolve("tokenizer.json"), 512);
    }

    /**
     * Embeds a single text and returns an L2-normalized 384-dim vector.
     */
    public float[] embed(String text) throws Exception {
        return embedBatch(List.of(text)).get(0);
    }

    /**
     * Embeds multiple texts in a single ONNX inference pass.
     * Returns one L2-normalized 384-dim vector per input text, in the same order.
     */
    public List<float[]> embedBatch(List<String> texts) throws Exception {
        int batchSize = texts.size();
        if (batchSize == 0) {
            return List.of();
        }

        // Tokenize all texts via batch encode
        String[] textArray = texts.toArray(new String[0]);
        BertTokenizer.Encoding[] encodings = tokenizer.batchEncode(textArray);

        // Find max sequence length in batch
        int maxLen = 0;
        for (BertTokenizer.Encoding enc : encodings) {
            maxLen = Math.max(maxLen, enc.getIds().length);
        }

        // Build padded [batchSize, maxLen] tensors
        long[] inputIds = new long[batchSize * maxLen];
        long[] attentionMask = new long[batchSize * maxLen];
        long[] tokenTypeIds = new long[batchSize * maxLen];

        for (int i = 0; i < batchSize; i++) {
            long[] ids = encodings[i].getIds();
            long[] mask = encodings[i].getAttentionMask();
            long[] typeIds = encodings[i].getTypeIds();
            int seqLen = ids.length;
            for (int j = 0; j < seqLen; j++) {
                inputIds[i * maxLen + j] = ids[j];
                attentionMask[i * maxLen + j] = mask[j];
                tokenTypeIds[i * maxLen + j] = typeIds[j];
            }
            // Remaining positions stay 0 (padding)
        }

        long[] shape = {batchSize, maxLen};

        try (
            OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape);
            OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape);
            OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypeIds), shape)
        ) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);
            inputs.put("token_type_ids", tokenTypeIdsTensor);

            try (OrtSession.Result result = session.run(inputs)) {
                // last_hidden_state output: [batchSize, seqLen, hiddenDim]
                float[][][] hiddenState = (float[][][]) result.get(0).getValue();

                List<float[]> embeddings = new ArrayList<>(batchSize);
                for (int i = 0; i < batchSize; i++) {
                    long[] mask = encodings[i].getAttentionMask();
                    float[] pooled = meanPool(hiddenState[i], mask);
                    l2Normalize(pooled);
                    embeddings.add(pooled);
                }
                return embeddings;
            }
        }
    }

    private static float[] meanPool(float[][] tokenEmbeddings, long[] attentionMask) {
        int dim = tokenEmbeddings[0].length;
        float[] result = new float[dim];
        long tokenCount = 0;
        int seqLen = Math.min(attentionMask.length, tokenEmbeddings.length);
        for (int t = 0; t < seqLen; t++) {
            if (attentionMask[t] == 1) {
                for (int d = 0; d < dim; d++) {
                    result[d] += tokenEmbeddings[t][d];
                }
                tokenCount++;
            }
        }
        if (tokenCount > 0) {
            for (int d = 0; d < dim; d++) {
                result[d] /= tokenCount;
            }
        }
        return result;
    }

    private static void l2Normalize(float[] v) {
        double norm = 0.0;
        for (float x : v) {
            norm += (double) x * x;
        }
        norm = Math.sqrt(norm);
        if (norm > 1e-12) {
            for (int i = 0; i < v.length; i++) {
                v[i] = (float) (v[i] / norm);
            }
        }
    }

    @Override
    public void close() throws Exception {
        session.close();
        env.close();
    }
}
