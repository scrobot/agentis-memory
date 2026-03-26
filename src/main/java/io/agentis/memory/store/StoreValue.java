package io.agentis.memory.store;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Polymorphic value stored in the KV store.
 * Each permitted type corresponds to a Redis data type.
 */
public sealed interface StoreValue permits StoreValue.StringValue, StoreValue.ListValue {

    record StringValue(byte[] raw) implements StoreValue {}

    /**
     * Redis list: doubly-linked semantics, head = index 0.
     * Wraps a CopyOnWriteArrayList for safe concurrent reads; mutations are
     * coordinated by KvStore via synchronized blocks on the list object.
     */
    record ListValue(CopyOnWriteArrayList<byte[]> list) implements StoreValue {}
}
