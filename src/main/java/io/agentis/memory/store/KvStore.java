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

    /** Sets TTL in milliseconds. Returns false if key does not exist. */
    public boolean pexpire(String key, long millis) {
        Entry e = store.get(key);
        if (e == null || e.isExpired()) {
            store.remove(key);
            return false;
        }
        long expireAt = System.currentTimeMillis() + millis;
        store.put(key, new Entry(e.value(), e.createdAt(), expireAt, e.hasVectorIndex()));
        return true;
    }

    /** Sets expireAt to an absolute epoch-ms timestamp. Returns false if key does not exist. */
    public boolean expireAt(String key, long epochMs) {
        Entry e = store.get(key);
        if (e == null || e.isExpired()) {
            store.remove(key);
            return false;
        }
        store.put(key, new Entry(e.value(), e.createdAt(), epochMs, e.hasVectorIndex()));
        return true;
    }

    /** Removes TTL (makes key persistent). Returns false if key does not exist. */
    public boolean persist(String key) {
        Entry e = store.get(key);
        if (e == null || e.isExpired()) {
            store.remove(key);
            return false;
        }
        store.put(key, new Entry(e.value(), e.createdAt(), -1, e.hasVectorIndex()));
        return true;
    }

    /**
     * Sets only if key does not exist. Returns true if set, false if already existed.
     */
    public boolean setNx(String key, byte[] value) {
        long now = System.currentTimeMillis();
        Entry newEntry = new Entry(new StoreValue.StringValue(value), now, -1, false);
        return store.putIfAbsent(key, newEntry) == null;
    }

    /**
     * Atomically gets the old value and sets a new one for a string key.
     * Returns old raw bytes or null if key did not exist or was wrong type.
     * Throws WrongTypeException if key exists but holds a non-string type.
     */
    public byte[] getAndSet(String key, byte[] newValue) {
        long now = System.currentTimeMillis();
        Entry newEntry = new Entry(new StoreValue.StringValue(newValue), now, -1, false);
        Entry old = store.put(key, newEntry);
        if (old == null || old.isExpired()) return null;
        if (old.value() instanceof StoreValue.StringValue sv) return sv.raw();
        // wrong type — key was replaced, which matches Redis GETSET behaviour for string-only store,
        // but we must restore & signal error — handled in command layer via getEntry check.
        store.put(key, old); // restore
        throw new WrongTypeException();
    }

    /**
     * Gets and deletes string key atomically.
     * Returns raw bytes or null. Throws WrongTypeException if wrong type.
     */
    public byte[] getAndDelete(String key) {
        Entry e = store.get(key);
        if (e == null || e.isExpired()) {
            store.remove(key);
            return null;
        }
        if (!(e.value() instanceof StoreValue.StringValue sv)) {
            throw new WrongTypeException();
        }
        store.remove(key);
        return sv.raw();
    }

    /**
     * Appends bytes to a string key. Creates key if absent.
     * Returns new length. Throws WrongTypeException if wrong type.
     */
    public int append(String key, byte[] suffix) {
        while (true) {
            Entry e = store.get(key);
            if (e == null || e.isExpired()) {
                Entry newEntry = new Entry(new StoreValue.StringValue(suffix), System.currentTimeMillis(), -1, false);
                if (store.putIfAbsent(key, newEntry) == null) return suffix.length;
                // lost race — retry
                continue;
            }
            if (!(e.value() instanceof StoreValue.StringValue sv)) throw new WrongTypeException();
            byte[] combined = new byte[sv.raw().length + suffix.length];
            System.arraycopy(sv.raw(), 0, combined, 0, sv.raw().length);
            System.arraycopy(suffix, 0, combined, sv.raw().length, suffix.length);
            Entry updated = new Entry(new StoreValue.StringValue(combined), e.createdAt(), e.expireAt(), e.hasVectorIndex());
            if (store.replace(key, e, updated)) return combined.length;
            // lost race — retry
        }
    }

    /** Runtime exception used internally to signal WRONGTYPE. */
    public static final class WrongTypeException extends RuntimeException {
        public WrongTypeException() { super(null, null, true, false); }
    }

    public int size() {
        return store.size();
    }

    public ConcurrentHashMap<String, Entry> getStore() {
        return store;
    }
}
