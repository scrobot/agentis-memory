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
        set(key, value, ttlSeconds, false);
    }

    public void set(String key, byte[] value, long ttlSeconds, boolean hasVectorIndex) {
        long expireAt = ttlSeconds > 0 ? System.currentTimeMillis() + (ttlSeconds * 1000) : -1;
        store.put(key, new Entry(new StoreValue.StringValue(value), System.currentTimeMillis(), expireAt, hasVectorIndex));
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

    /**
     * Returns the SortedSetValue for a key, or null if missing/expired.
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
        return null;
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

    // ── Set operations ───────────────────────────────────────────────────────

    /**
     * Returns the SetValue for a key, or null if the key does not exist.
     * Throws WrongTypeException if the key holds a non-set value.
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

    /** Removes the key if the set is empty. */
    public void removeIfEmptySet(String key) {
        store.computeIfPresent(key, (k, e) -> {
            if (e.value() instanceof StoreValue.SetValue sv && sv.members().isEmpty()) return null;
            return e;
        });
    }

    // ── Extended string operations ───────────────────────────────────────────

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
                continue;
            }
            if (!(e.value() instanceof StoreValue.StringValue sv)) throw new WrongTypeException();
            byte[] combined = new byte[sv.raw().length + suffix.length];
            System.arraycopy(sv.raw(), 0, combined, 0, sv.raw().length);
            System.arraycopy(suffix, 0, combined, sv.raw().length, suffix.length);
            Entry updated = new Entry(new StoreValue.StringValue(combined), e.createdAt(), e.expireAt(), e.hasVectorIndex());
            if (store.replace(key, e, updated)) return combined.length;
        }
    }

    public boolean exists(String key) {
        Entry e = store.get(key);
        if (e == null) return false;
        if (e.isExpired()) {
            store.remove(key);
            return false;
        }
        return true;
    }

    public long size() {
        return store.size();
    }

    public ConcurrentHashMap<String, Entry> getStore() {
        return store;
    }

    // ── Hash operations ───────────────────────────────────────────────────────

    private static final String WRONGTYPE = "WRONGTYPE Operation against a key holding the wrong kind of value";

    /**
     * Returns the HashValue for the given key, creating an empty hash if the key doesn't exist.
     * Returns null + sets errorOut[0] if the key holds a non-hash type.
     */
    private StoreValue.HashValue getOrCreateHash(String key, String[] errorOut) {
        Entry e = store.get(key);
        if (e != null && e.isExpired()) {
            store.remove(key);
            e = null;
        }
        if (e == null) {
            StoreValue.HashValue hv = new StoreValue.HashValue();
            store.put(key, new Entry(hv, System.currentTimeMillis(), -1, false));
            return hv;
        }
        if (e.value() instanceof StoreValue.HashValue hv) {
            return hv;
        }
        errorOut[0] = WRONGTYPE;
        return null;
    }

    /**
     * Returns the HashValue for an existing key (read-only).
     * Returns null without error if the key doesn't exist.
     * Sets errorOut[0] if wrong type.
     */
    private StoreValue.HashValue getHashReadOnly(String key, String[] errorOut) {
        Entry e = store.get(key);
        if (e == null) return null;
        if (e.isExpired()) {
            store.remove(key);
            return null;
        }
        if (e.value() instanceof StoreValue.HashValue hv) {
            return hv;
        }
        errorOut[0] = WRONGTYPE;
        return null;
    }

    /** HSET: sets multiple fields; returns number of new fields added. */
    public Object hset(String key, java.util.List<byte[]> fieldValues) {
        String[] err = {null};
        StoreValue.HashValue hv = getOrCreateHash(key, err);
        if (err[0] != null) return err[0];
        int added = 0;
        for (int i = 0; i < fieldValues.size() - 1; i += 2) {
            String field = new String(fieldValues.get(i));
            byte[] value = fieldValues.get(i + 1);
            if (hv.fields().put(field, value) == null) added++;
        }
        if (hv.fields().isEmpty()) store.remove(key);
        return (long) added;
    }

    /** HGET: returns the value, null if field/key absent, WRONGTYPE string on type error. */
    public Object hget(String key, String field) {
        String[] err = {null};
        StoreValue.HashValue hv = getHashReadOnly(key, err);
        if (err[0] != null) return err[0];
        if (hv == null) return null;
        return hv.fields().get(field);
    }

    /** HGETALL: returns flat [field,value,...] list, empty list if key absent. */
    public Object hgetall(String key) {
        String[] err = {null};
        StoreValue.HashValue hv = getHashReadOnly(key, err);
        if (err[0] != null) return err[0];
        if (hv == null) return java.util.List.of();
        java.util.List<byte[]> result = new java.util.ArrayList<>();
        hv.fields().forEach((f, v) -> {
            result.add(f.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            result.add(v);
        });
        return result;
    }

    /** HDEL: deletes fields; returns count of removed fields. Removes key if hash becomes empty. */
    public Object hdel(String key, java.util.List<String> fields) {
        String[] err = {null};
        StoreValue.HashValue hv = getHashReadOnly(key, err);
        if (err[0] != null) return err[0];
        if (hv == null) return 0L;
        long removed = fields.stream().filter(f -> hv.fields().remove(f) != null).count();
        if (hv.fields().isEmpty()) store.remove(key);
        return removed;
    }

    /** HEXISTS: returns 1 if field exists, 0 otherwise. */
    public Object hexists(String key, String field) {
        String[] err = {null};
        StoreValue.HashValue hv = getHashReadOnly(key, err);
        if (err[0] != null) return err[0];
        if (hv == null) return 0L;
        return hv.fields().containsKey(field) ? 1L : 0L;
    }

    /** HKEYS: returns all field names. */
    public Object hkeys(String key) {
        String[] err = {null};
        StoreValue.HashValue hv = getHashReadOnly(key, err);
        if (err[0] != null) return err[0];
        if (hv == null) return java.util.List.of();
        return hv.fields().keySet().stream()
                .map(f -> f.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.toList());
    }

    /** HVALS: returns all values. */
    public Object hvals(String key) {
        String[] err = {null};
        StoreValue.HashValue hv = getHashReadOnly(key, err);
        if (err[0] != null) return err[0];
        if (hv == null) return java.util.List.of();
        return new java.util.ArrayList<>(hv.fields().values());
    }

    /** HLEN: returns number of fields. */
    public Object hlen(String key) {
        String[] err = {null};
        StoreValue.HashValue hv = getHashReadOnly(key, err);
        if (err[0] != null) return err[0];
        if (hv == null) return 0L;
        return (long) hv.fields().size();
    }

    /** HMGET: returns list of values (null for missing fields). */
    public Object hmget(String key, java.util.List<String> fields) {
        String[] err = {null};
        StoreValue.HashValue hv = getHashReadOnly(key, err);
        if (err[0] != null) return err[0];
        return fields.stream()
                .map(f -> hv == null ? null : hv.fields().get(f))
                .collect(java.util.stream.Collectors.toList());
    }

    /** HINCRBY: increments field by delta, returns new value. */
    public Object hincrby(String key, String field, long delta) {
        String[] err = {null};
        StoreValue.HashValue hv = getOrCreateHash(key, err);
        if (err[0] != null) return err[0];
        byte[] current = hv.fields().get(field);
        long val = 0;
        if (current != null) {
            try {
                val = Long.parseLong(new String(current));
            } catch (NumberFormatException e) {
                return "ERR hash value is not an integer";
            }
        }
        // overflow check
        if (delta > 0 && val > Long.MAX_VALUE - delta) return "ERR increment or decrement would overflow";
        if (delta < 0 && val < Long.MIN_VALUE - delta) return "ERR increment or decrement would overflow";
        val += delta;
        hv.fields().put(field, Long.toString(val).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return val;
    }

    /**
     * HINCRBYFLOAT: increments field by float delta.
     * Returns byte[] with the new value on success, or a String starting with "ERR"/"WRONGTYPE" on error.
     */
    public Object hincrbyfloat(String key, String field, double delta) {
        String[] err = {null};
        StoreValue.HashValue hv = getOrCreateHash(key, err);
        if (err[0] != null) return err[0];
        byte[] current = hv.fields().get(field);
        double val = 0.0;
        if (current != null) {
            try {
                val = Double.parseDouble(new String(current));
            } catch (NumberFormatException e) {
                return "ERR hash value is not a float";
            }
        }
        val += delta;
        if (Double.isInfinite(val) || Double.isNaN(val)) return "ERR increment would produce NaN or Infinity";
        byte[] result = stripTrailingZeros(val).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        hv.fields().put(field, result);
        return result;
    }

    private static String stripTrailingZeros(double val) {
        String s = Double.toString(val);
        // Remove trailing zeros after decimal point (Redis style)
        if (s.contains(".") && !s.contains("e") && !s.contains("E")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    /** HSETNX: sets field only if it does not exist. Returns 1 if set, 0 if already existed. */
    public Object hsetnx(String key, String field, byte[] value) {
        String[] err = {null};
        StoreValue.HashValue hv = getOrCreateHash(key, err);
        if (err[0] != null) return err[0];
        byte[] existing = hv.fields().putIfAbsent(field, value);
        return existing == null ? 1L : 0L;
    }

    /**
     * HSCAN: returns [cursor, [field1, value1, ...]] snapshot.
     * cursor is offset-based (same as SCAN for keys).
     */
    public Object hscan(String key, int cursor, String matchPattern, int count) {
        String[] err = {null};
        StoreValue.HashValue hv = getHashReadOnly(key, err);
        if (err[0] != null) return err[0];
        if (hv == null) return java.util.List.of("0", java.util.List.of());

        java.util.List<java.util.Map.Entry<String, byte[]>> all = new java.util.ArrayList<>(hv.fields().entrySet());
        java.util.regex.Pattern pat = matchPattern != null ? globToPattern(matchPattern) : null;

        java.util.List<byte[]> items = new java.util.ArrayList<>();
        int nextCursor = 0;
        int end = Math.min(cursor + count, all.size());
        for (int i = cursor; i < end; i++) {
            java.util.Map.Entry<String, byte[]> e = all.get(i);
            if (pat == null || pat.matcher(e.getKey()).matches()) {
                items.add(e.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                items.add(e.getValue());
            }
        }
        if (end < all.size()) nextCursor = end;
        return java.util.List.of(String.valueOf(nextCursor), items);
    }

    private static java.util.regex.Pattern globToPattern(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                case '.' -> sb.append("\\.");
                case '[', ']', '(', ')', '{', '}', '^', '$', '+', '|', '\\' -> sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        sb.append("$");
        return java.util.regex.Pattern.compile(sb.toString());
    }

    // ── List operations ──────────────────────────────────────────────────────

    /**
     * Returns the ListValue for {@code key}, throwing if the key holds a non-list value.
     * Returns null if the key does not exist or has expired.
     */
    private StoreValue.ListValue getListValue(String key) {
        Entry e = store.get(key);
        if (e == null) return null;
        if (e.isExpired()) { store.remove(key); return null; }
        if (!(e.value() instanceof StoreValue.ListValue lv)) {
            throw new WrongTypeException(WRONGTYPE);
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
            throw new WrongTypeException(WRONGTYPE);
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
        public WrongTypeException() {
            super("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
        public WrongTypeException(String message) { super(message); }
    }
}
