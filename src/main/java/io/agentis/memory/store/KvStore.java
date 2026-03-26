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

    public int size() {
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
}
