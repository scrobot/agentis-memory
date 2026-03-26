package io.agentis.memory.store;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Polymorphic value stored in the KV store.
 * Each permitted type corresponds to a Redis data type.
 */
public sealed interface StoreValue permits StoreValue.StringValue, StoreValue.SetValue {

    record StringValue(byte[] raw) implements StoreValue {}

    record SetValue(Set<String> members) implements StoreValue {
        public SetValue() {
            this(ConcurrentHashMap.newKeySet());
        }
    }
}
