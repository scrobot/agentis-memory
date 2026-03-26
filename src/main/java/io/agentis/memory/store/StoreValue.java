package io.agentis.memory.store;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Polymorphic value stored in the KV store.
 * Each permitted type corresponds to a Redis data type.
 */
public sealed interface StoreValue permits StoreValue.StringValue, StoreValue.HashValue, StoreValue.ListValue, StoreValue.SortedSetValue, StoreValue.SetValue {

    record StringValue(byte[] raw) implements StoreValue {}

    record HashValue(ConcurrentHashMap<String, byte[]> fields) implements StoreValue {
        public HashValue() {
            this(new ConcurrentHashMap<>());
        }
    }

    /**
     * Redis list: doubly-linked semantics, head = index 0.
     * Wraps a CopyOnWriteArrayList for safe concurrent reads; mutations are
     * coordinated by KvStore via synchronized blocks on the list object.
     */
    record ListValue(CopyOnWriteArrayList<byte[]> list) implements StoreValue {}

    /**
     * Sorted set value. Maintains two parallel structures for O(log n) operations:
     * - memberToScore: member → score lookup
     * - scoreToMembers: ordered score → set-of-members (handles ties)
     */
    record SortedSetValue(
            ConcurrentHashMap<String, Double> memberToScore,
            ConcurrentSkipListMap<Double, java.util.TreeSet<String>> scoreToMembers
    ) implements StoreValue {}

    record SetValue(Set<String> members) implements StoreValue {
        public SetValue() {
            this(ConcurrentHashMap.newKeySet());
        }
    }
}
