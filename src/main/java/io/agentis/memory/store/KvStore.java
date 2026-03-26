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

    public record Entry(byte[] value, long createdAt, long expireAt, boolean hasVectorIndex) {
        public boolean isExpired() {
            return expireAt != -1 && System.currentTimeMillis() > expireAt;
        }
    }

    public void set(String key, byte[] value, long ttlSeconds) {
        long expireAt = ttlSeconds > 0 ? System.currentTimeMillis() + (ttlSeconds * 1000) : -1;
        store.put(key, new Entry(value, System.currentTimeMillis(), expireAt, false));
    }

    public byte[] get(String key) {
        Entry e = store.get(key);
        if (e == null) return null;
        if (e.isExpired()) {
            store.remove(key);
            return null;
        }
        return e.value;
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
