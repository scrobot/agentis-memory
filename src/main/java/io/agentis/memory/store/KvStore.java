package io.agentis.memory.store;

import io.agentis.memory.config.ServerConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class KvStore {
    private static final Logger log = LoggerFactory.getLogger(KvStore.class);

    private final ServerConfig config;
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Inject
    public KvStore(ServerConfig config) {
        this.config = config;
    }

    public record Entry(StoreValue value, long createdAt, long expireAt, boolean hasVectorIndex) {
        public boolean isExpired() {
            return expireAt != -1 && System.currentTimeMillis() > expireAt;
        }
    }

    public void set(String key, byte[] value, long ttlSeconds) {
        long expireAt = ttlSeconds > 0 ? System.currentTimeMillis() + (ttlSeconds * 1000) : -1;
        store.put(key, new Entry(new StoreValue.StringValue(value), System.currentTimeMillis(), expireAt, false));
    }

    /** Returns the raw bytes for a string key, or null if missing/expired/wrong type. */
    public byte[] get(String key) {
        Entry e = store.get(key);
        if (e == null) return null;
        if (e.isExpired()) {
            store.remove(key);
            return null;
        }
        if (e.value() instanceof StoreValue.StringValue sv) {
            return sv.raw();
        }
        return null;
    }

    /** Returns the live Entry (null if missing or expired). */
    public Entry getEntry(String key) {
        Entry e = store.get(key);
        if (e == null) return null;
        if (e.isExpired()) {
            store.remove(key);
            return null;
        }
        return e;
    }

    public boolean delete(String key) {
        return store.remove(key) != null;
    }

    public boolean expire(String key, long seconds) {
        Entry e = store.get(key);
        if (e == null || e.isExpired()) {
            store.remove(key);
            return false;
        }
        long expireAt = System.currentTimeMillis() + (seconds * 1000);
        store.put(key, new Entry(e.value(), e.createdAt(), expireAt, e.hasVectorIndex()));
        return true;
    }

    /**
     * Returns the SortedSetValue for a key, or null if missing/expired.
     * Throws WRONGTYPE error via returned null + caller must check type separately.
     * Use {@link #getSortedSet(String)} which handles expiry; this helper returns
     * null if the key does not exist or has the wrong type.
     */
    public StoreValue.SortedSetValue getSortedSet(String key) {
        Entry e = store.get(key);
        if (e == null) return null;
        if (e.isExpired()) {
            store.remove(key);
            return null;
        }
        if (e.value() instanceof StoreValue.SortedSetValue sv) {
            return sv;
        }
        return null;  // wrong type — caller must check with getEntry() if needed
    }

    /**
     * Creates a new empty SortedSetValue for the given key, or returns the existing one.
     * Returns null if the key exists with a different type.
     */
    public StoreValue.SortedSetValue getOrCreateSortedSet(String key) {
        Entry e = store.get(key);
        if (e != null && e.isExpired()) {
            store.remove(key);
            e = null;
        }
        if (e != null) {
            if (e.value() instanceof StoreValue.SortedSetValue sv) {
                return sv;
            }
            return null;  // WRONGTYPE
        }
        // Create new sorted set
        StoreValue.SortedSetValue sv = new StoreValue.SortedSetValue(
                new java.util.concurrent.ConcurrentHashMap<>(),
                new java.util.concurrent.ConcurrentSkipListMap<>()
        );
        store.put(key, new Entry(sv, System.currentTimeMillis(), -1, false));
        return sv;
    }

    /** Removes the key if the sorted set is empty. */
    public void deleteIfEmptySortedSet(String key) {
        Entry e = store.get(key);
        if (e != null && e.value() instanceof StoreValue.SortedSetValue sv) {
            if (sv.memberToScore().isEmpty()) {
                store.remove(key);
            }
        }
    }

    public int size() {
        return store.size();
    }

    public ConcurrentHashMap<String, Entry> getStore() {
        return store;
    }
}
