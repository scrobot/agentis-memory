package io.agentis.memory.store;

/**
 * Polymorphic value stored in the KV store.
 * Each permitted type corresponds to a Redis data type.
 */
public sealed interface StoreValue permits StoreValue.StringValue, StoreValue.HashValue {

    record StringValue(byte[] raw) implements StoreValue {}

    record HashValue(java.util.concurrent.ConcurrentHashMap<String, byte[]> fields) implements StoreValue {
        public HashValue() {
            this(new java.util.concurrent.ConcurrentHashMap<>());
        }
    }
}
