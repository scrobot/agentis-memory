package io.agentis.memory.store;

import io.agentis.memory.config.ServerConfig;
import java.util.concurrent.ConcurrentHashMap;

public class KvStore {
    private final ServerConfig config;
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    public KvStore(ServerConfig config) {
        this.config = config;
    }

    public record Entry(byte[] value, long createdAt, long expireAt, boolean hasVectorIndex) {}

    public void set(String key, byte[] value) {
        store.put(key, new Entry(value, System.currentTimeMillis(), -1, false));
    }

    public byte[] get(String key) {
        Entry e = store.get(key);
        return e != null ? e.value : null;
    }
}
