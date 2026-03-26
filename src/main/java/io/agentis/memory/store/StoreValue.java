package io.agentis.memory.store;

/**
 * Polymorphic value stored in the KV store.
 * Each permitted type corresponds to a Redis data type.
 */
public sealed interface StoreValue permits StoreValue.StringValue {

    record StringValue(byte[] raw) implements StoreValue {}
}
