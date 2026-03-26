package io.agentis.memory.store;

public record Entry(
    byte[] value,
    long createdAt,
    long expireAt,          // -1 = no expiry
    boolean hasVectorIndex  // true if MEMSAVE'd — chunks exist in HNSW
) {
    public boolean isExpired() {
        return expireAt != -1 && System.currentTimeMillis() > expireAt;
    }
}
