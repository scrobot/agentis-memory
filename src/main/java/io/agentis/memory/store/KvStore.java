package io.agentis.memory.store;

import io.agentis.memory.config.ServerConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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

    public int size() {
        return store.size();
    }

    public ConcurrentHashMap<String, Entry> getStore() {
        return store;
    }

    // -------------------------------------------------------------------------
    // List operations
    // -------------------------------------------------------------------------

    private static final String WRONGTYPE_MSG =
            "WRONGTYPE Operation against a key holding the wrong kind of value";

    /**
     * Returns the ListValue for {@code key}, throwing if the key holds a non-list value.
     * Returns null if the key does not exist or has expired.
     */
    private StoreValue.ListValue getListValue(String key) {
        Entry e = store.get(key);
        if (e == null) return null;
        if (e.isExpired()) { store.remove(key); return null; }
        if (!(e.value() instanceof StoreValue.ListValue lv)) {
            throw new WrongTypeException(WRONGTYPE_MSG);
        }
        return lv;
    }

    /** Gets or creates a list for {@code key}. Throws WrongTypeException if the key holds another type. */
    private StoreValue.ListValue getOrCreateList(String key) {
        Entry e = store.get(key);
        if (e != null && e.isExpired()) { store.remove(key); e = null; }
        if (e == null) {
            StoreValue.ListValue lv = new StoreValue.ListValue(new CopyOnWriteArrayList<>());
            store.put(key, new Entry(lv, System.currentTimeMillis(), -1, false));
            return lv;
        }
        if (!(e.value() instanceof StoreValue.ListValue lv)) {
            throw new WrongTypeException(WRONGTYPE_MSG);
        }
        return lv;
    }

    /**
     * LPUSH: prepend elements left-to-right (so last element ends up at head).
     * Returns new list length.
     */
    public long lpush(String key, List<byte[]> elements) {
        synchronized (store) {
            StoreValue.ListValue lv = getOrCreateList(key);
            synchronized (lv.list()) {
                for (byte[] el : elements) {
                    lv.list().add(0, el);
                }
                return lv.list().size();
            }
        }
    }

    /**
     * RPUSH: append elements to the tail.
     * Returns new list length.
     */
    public long rpush(String key, List<byte[]> elements) {
        synchronized (store) {
            StoreValue.ListValue lv = getOrCreateList(key);
            synchronized (lv.list()) {
                lv.list().addAll(elements);
                return lv.list().size();
            }
        }
    }

    /**
     * LPOP: removes and returns up to {@code count} elements from the head.
     * Deletes the key if the list becomes empty.
     * Returns null if the key does not exist.
     */
    public List<byte[]> lpop(String key, int count) {
        synchronized (store) {
            StoreValue.ListValue lv = getListValue(key);
            if (lv == null) return null;
            synchronized (lv.list()) {
                if (lv.list().isEmpty()) { store.remove(key); return null; }
                int n = Math.min(count, lv.list().size());
                List<byte[]> result = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    result.add(lv.list().remove(0));
                }
                if (lv.list().isEmpty()) store.remove(key);
                return result;
            }
        }
    }

    /**
     * RPOP: removes and returns up to {@code count} elements from the tail.
     * Deletes the key if the list becomes empty.
     * Returns null if the key does not exist.
     */
    public List<byte[]> rpop(String key, int count) {
        synchronized (store) {
            StoreValue.ListValue lv = getListValue(key);
            if (lv == null) return null;
            synchronized (lv.list()) {
                if (lv.list().isEmpty()) { store.remove(key); return null; }
                int n = Math.min(count, lv.list().size());
                List<byte[]> result = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    result.add(lv.list().remove(lv.list().size() - 1));
                }
                return result;
            }
        }
    }

    /** LLEN: list length, or 0 if key does not exist. */
    public long llen(String key) {
        StoreValue.ListValue lv = getListValue(key);
        if (lv == null) return 0;
        return lv.list().size();
    }

    /** LRANGE: returns elements from start to stop (inclusive), negative indices resolved. */
    public List<byte[]> lrange(String key, int start, int stop) {
        StoreValue.ListValue lv = getListValue(key);
        if (lv == null) return List.of();
        synchronized (lv.list()) {
            int size = lv.list().size();
            int s = resolveIndex(start, size);
            int e = resolveIndex(stop, size);
            if (s > e || s >= size) return List.of();
            e = Math.min(e, size - 1);
            return new ArrayList<>(lv.list().subList(s, e + 1));
        }
    }

    /** LINDEX: element at index (negative allowed), or null if out of range. */
    public byte[] lindex(String key, int index) {
        StoreValue.ListValue lv = getListValue(key);
        if (lv == null) return null;
        synchronized (lv.list()) {
            int size = lv.list().size();
            int i = resolveIndex(index, size);
            if (i < 0 || i >= size) return null;
            return lv.list().get(i);
        }
    }

    /**
     * LSET: sets element at index. Returns false if key does not exist.
     * Throws IndexOutOfBoundsException if index is out of range.
     */
    public boolean lset(String key, int index, byte[] element) {
        StoreValue.ListValue lv = getListValue(key);
        if (lv == null) return false;
        synchronized (lv.list()) {
            int size = lv.list().size();
            int i = resolveIndex(index, size);
            if (i < 0 || i >= size) throw new IndexOutOfBoundsException("ERR index out of range");
            lv.list().set(i, element);
            return true;
        }
    }

    /**
     * LREM: removes elements equal to {@code element}.
     * count > 0: from head, up to count. count < 0: from tail. count == 0: all.
     * Returns number removed.
     */
    public long lrem(String key, int count, byte[] element) {
        StoreValue.ListValue lv = getListValue(key);
        if (lv == null) return 0;
        synchronized (lv.list()) {
            List<byte[]> current = new ArrayList<>(lv.list());
            int removed = 0;
            if (count == 0) {
                current.removeIf(b -> java.util.Arrays.equals(b, element));
                removed = lv.list().size() - current.size();
            } else if (count > 0) {
                int toRemove = count;
                for (int i = 0; i < current.size() && toRemove > 0; ) {
                    if (java.util.Arrays.equals(current.get(i), element)) {
                        current.remove(i);
                        toRemove--;
                        removed++;
                    } else {
                        i++;
                    }
                }
            } else {
                int toRemove = -count;
                for (int i = current.size() - 1; i >= 0 && toRemove > 0; ) {
                    if (java.util.Arrays.equals(current.get(i), element)) {
                        current.remove(i);
                        toRemove--;
                        removed++;
                        i--;
                    } else {
                        i--;
                    }
                }
            }
            lv.list().clear();
            lv.list().addAll(current);
            if (lv.list().isEmpty()) store.remove(key);
            return removed;
        }
    }

    /**
     * LINSERT: inserts element before or after pivot.
     * Returns new length, -1 if pivot not found, 0 if key does not exist.
     */
    public long linsert(String key, boolean before, byte[] pivot, byte[] element) {
        synchronized (store) {
            StoreValue.ListValue lv = getListValue(key);
            if (lv == null) return 0;
            synchronized (lv.list()) {
                for (int i = 0; i < lv.list().size(); i++) {
                    if (java.util.Arrays.equals(lv.list().get(i), pivot)) {
                        int insertAt = before ? i : i + 1;
                        lv.list().add(insertAt, element);
                        return lv.list().size();
                    }
                }
                return -1;
            }
        }
    }

    /**
     * LTRIM: trims list to [start, stop]. Deletes key if result is empty.
     */
    public void ltrim(String key, int start, int stop) {
        synchronized (store) {
            StoreValue.ListValue lv = getListValue(key);
            if (lv == null) return;
            synchronized (lv.list()) {
                int size = lv.list().size();
                int s = resolveIndex(start, size);
                int e = resolveIndex(stop, size);
                if (s > e || s >= size) {
                    lv.list().clear();
                    store.remove(key);
                    return;
                }
                e = Math.min(e, size - 1);
                List<byte[]> trimmed = new ArrayList<>(lv.list().subList(s, e + 1));
                lv.list().clear();
                lv.list().addAll(trimmed);
                if (lv.list().isEmpty()) store.remove(key);
            }
        }
    }

    /** Converts a Redis-style index (may be negative) to a concrete 0-based index. */
    private static int resolveIndex(int index, int size) {
        if (index < 0) index = size + index;
        return index;
    }

    /** Thrown when an operation is issued against a key of the wrong type. */
    public static class WrongTypeException extends RuntimeException {
        public WrongTypeException(String message) { super(message); }
    }
}
