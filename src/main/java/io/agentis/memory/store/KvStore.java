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
     * Returns the SetValue for a key, or null if the key does not exist.
     * Throws WrongTypeException if the key holds a non-set value.
     * Creates an empty set entry if the key is absent and create=true.
     */
    public StoreValue.SetValue getSet(String key) {
        Entry e = getEntry(key);
        if (e == null) return null;
        if (e.value() instanceof StoreValue.SetValue sv) return sv;
        throw new WrongTypeException();
    }

    /**
     * Returns the SetValue for a key, creating an empty set if the key does not exist.
     * Throws WrongTypeException if the key holds a non-set value.
     */
    public StoreValue.SetValue getOrCreateSet(String key) {
        Entry e = store.get(key);
        if (e != null && e.isExpired()) {
            store.remove(key);
            e = null;
        }
        if (e == null) {
            StoreValue.SetValue sv = new StoreValue.SetValue();
            store.put(key, new Entry(sv, System.currentTimeMillis(), -1, false));
            return sv;
        }
        if (e.value() instanceof StoreValue.SetValue sv) return sv;
        throw new WrongTypeException();
    }

    /**
     * Removes the key if the set is empty.
     */
    public void removeIfEmptySet(String key) {
        store.computeIfPresent(key, (k, e) -> {
            if (e.value() instanceof StoreValue.SetValue sv && sv.members().isEmpty()) return null;
            return e;
        });
    }

    public static class WrongTypeException extends RuntimeException {
        public WrongTypeException() {
            super("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
    }

    public int size() {
        return store.size();
    }

    public ConcurrentHashMap<String, Entry> getStore() {
        return store;
    }
}
