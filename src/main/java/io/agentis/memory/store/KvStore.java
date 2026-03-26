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

    /**
     * Conditional set with NX/XX semantics.
     *
     * @param key       the key
     * @param value     the value to store
     * @param ttlMillis TTL in milliseconds; -1 means no expiry
     * @param nx        if true, only set when key does NOT exist (or is expired)
     * @param xx        if true, only set when key DOES exist (and is not expired)
     * @return the previous raw string value (or null if none), used for GET option;
     *         returns null also when the condition was not met — callers must
     *         distinguish via the returned {@link SetResult}
     */
    public SetResult setConditional(String key, byte[] value, long ttlMillis, boolean nx, boolean xx) {
        // We need atomicity for NX/XX — use compute to avoid TOCTOU races.
        final byte[][] oldValue = {null};
        final boolean[] conditionMet = {true};

        store.compute(key, (k, existing) -> {
            boolean exists = existing != null && !existing.isExpired();

            // Capture old value for GET option
            if (exists && existing.value() instanceof StoreValue.StringValue sv) {
                oldValue[0] = sv.raw();
            }

            if (nx && exists) {
                conditionMet[0] = false;
                return existing; // leave unchanged
            }
            if (xx && !exists) {
                conditionMet[0] = false;
                return existing; // leave unchanged (null stays null)
            }

            long expireAt = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : -1;
            return new Entry(new StoreValue.StringValue(value), System.currentTimeMillis(), expireAt, false);
        });

        return new SetResult(conditionMet[0], oldValue[0]);
    }

    /** Result of a conditional SET operation. */
    public record SetResult(boolean conditionMet, byte[] previousValue) {}

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

    public int size() {
        return store.size();
    }

    public ConcurrentHashMap<String, Entry> getStore() {
        return store;
    }
}
