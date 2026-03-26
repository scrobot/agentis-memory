package io.agentis.memory.vector;

/**
 * A single chunk produced by splitting a MEMSAVE document.
 *
 * @param parentKey the key that was passed to MEMSAVE
 * @param index     0-based ordinal of this chunk within the parent document
 * @param text      the raw text of this chunk
 * @param vector    384-dim L2-normalized embedding
 * @param namespace prefix before the first ':' in parentKey, or "default"
 */
public record Chunk(
        String parentKey,
        int index,
        String text,
        float[] vector,
        String namespace
) {}
