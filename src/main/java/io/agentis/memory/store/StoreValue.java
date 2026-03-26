package io.agentis.memory.store;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Polymorphic value stored in the KV store.
 * Each permitted type corresponds to a Redis data type.
 */
public sealed interface StoreValue permits StoreValue.StringValue, StoreValue.SortedSetValue {

    record StringValue(byte[] raw) implements StoreValue {}

    /**
     * Sorted set value. Maintains two parallel structures for O(log n) operations:
     * - memberToScore: member → score lookup
     * - scoreToMembers: ordered score → set-of-members (handles ties)
     */
    record SortedSetValue(
            ConcurrentHashMap<String, Double> memberToScore,
            ConcurrentSkipListMap<Double, java.util.TreeSet<String>> scoreToMembers
    ) implements StoreValue {}
}
