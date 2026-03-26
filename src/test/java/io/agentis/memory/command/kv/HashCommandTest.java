package io.agentis.memory.command.kv;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HashCommandTest {

    private KvStore kvStore;
    private HsetCommand hset;
    private HgetCommand hget;
    private HgetallCommand hgetall;
    private HdelCommand hdel;
    private HexistsCommand hexists;
    private HkeysCommand hkeys;
    private HvalsCommand hvals;
    private HlenCommand hlen;
    private HmgetCommand hmget;
    private HincrbyCommand hincrby;
    private HincrbyfloatCommand hincrbyfloat;
    private HsetnxCommand hsetnx;
    private HscanCommand hscan;
    private TypeCommand typeCmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        hset = new HsetCommand(kvStore);
        hget = new HgetCommand(kvStore);
        hgetall = new HgetallCommand(kvStore);
        hdel = new HdelCommand(kvStore);
        hexists = new HexistsCommand(kvStore);
        hkeys = new HkeysCommand(kvStore);
        hvals = new HvalsCommand(kvStore);
        hlen = new HlenCommand(kvStore);
        hmget = new HmgetCommand(kvStore);
        hincrby = new HincrbyCommand(kvStore);
        hincrbyfloat = new HincrbyfloatCommand(kvStore);
        hsetnx = new HsetnxCommand(kvStore);
        hscan = new HscanCommand(kvStore);
        typeCmd = new TypeCommand(kvStore);
    }

    // ── HSET ──────────────────────────────────────────────────────────────────

    @Test
    void hset_addsNewFields() {
        RespMessage result = hset.handle(null, args("HSET", "h1", "f1", "v1", "f2", "v2"));
        assertEquals(2L, intVal(result));
    }

    @Test
    void hset_updatesExistingField_returnsZero() {
        hset.handle(null, args("HSET", "h1", "f1", "v1"));
        RespMessage result = hset.handle(null, args("HSET", "h1", "f1", "updated"));
        assertEquals(0L, intVal(result));
    }

    @Test
    void hset_errorOnOddFieldValues() {
        RespMessage result = hset.handle(null, args("HSET", "h1", "f1"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    @Test
    void hset_wrongType() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = hset.handle(null, args("HSET", "str", "f", "v"));
        assertError(result, "WRONGTYPE");
    }

    // ── HGET ──────────────────────────────────────────────────────────────────

    @Test
    void hget_existingField() {
        hset.handle(null, args("HSET", "h1", "name", "alice"));
        RespMessage result = hget.handle(null, args("HGET", "h1", "name"));
        assertEquals("alice", bulkStr(result));
    }

    @Test
    void hget_missingField_returnsNil() {
        hset.handle(null, args("HSET", "h1", "f1", "v1"));
        RespMessage result = hget.handle(null, args("HGET", "h1", "missing"));
        assertInstanceOf(RespMessage.NullBulkString.class, result);
    }

    @Test
    void hget_missingKey_returnsNil() {
        RespMessage result = hget.handle(null, args("HGET", "nokey", "f"));
        assertInstanceOf(RespMessage.NullBulkString.class, result);
    }

    @Test
    void hget_wrongType() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertError(hget.handle(null, args("HGET", "str", "f")), "WRONGTYPE");
    }

    // ── HGETALL ───────────────────────────────────────────────────────────────

    @Test
    void hgetall_returnsAllFieldsAndValues() {
        hset.handle(null, args("HSET", "h1", "a", "1", "b", "2"));
        RespMessage result = hgetall.handle(null, args("HGETALL", "h1"));
        List<RespMessage> elements = ((RespMessage.RespArray) result).elements();
        assertEquals(4, elements.size());
    }

    @Test
    void hgetall_missingKey_emptyArray() {
        RespMessage result = hgetall.handle(null, args("HGETALL", "nokey"));
        assertEquals(0, ((RespMessage.RespArray) result).elements().size());
    }

    @Test
    void hgetall_wrongType() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertError(hgetall.handle(null, args("HGETALL", "str")), "WRONGTYPE");
    }

    // ── HDEL ──────────────────────────────────────────────────────────────────

    @Test
    void hdel_removesExistingField() {
        hset.handle(null, args("HSET", "h1", "f1", "v1", "f2", "v2"));
        RespMessage result = hdel.handle(null, args("HDEL", "h1", "f1"));
        assertEquals(1L, intVal(result));
        assertInstanceOf(RespMessage.NullBulkString.class, hget.handle(null, args("HGET", "h1", "f1")));
    }

    @Test
    void hdel_ignoresMissingField() {
        hset.handle(null, args("HSET", "h1", "f1", "v1"));
        RespMessage result = hdel.handle(null, args("HDEL", "h1", "missing"));
        assertEquals(0L, intVal(result));
    }

    @Test
    void hdel_deletesKeyWhenAllFieldsRemoved() {
        hset.handle(null, args("HSET", "h1", "f1", "v1"));
        hdel.handle(null, args("HDEL", "h1", "f1"));
        assertEquals("none", ((RespMessage.SimpleString) typeCmd.handle(null, args("TYPE", "h1"))).value());
    }

    @Test
    void hdel_wrongType() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertError(hdel.handle(null, args("HDEL", "str", "f")), "WRONGTYPE");
    }

    // ── HEXISTS ───────────────────────────────────────────────────────────────

    @Test
    void hexists_existingField_returns1() {
        hset.handle(null, args("HSET", "h1", "f1", "v1"));
        assertEquals(1L, intVal(hexists.handle(null, args("HEXISTS", "h1", "f1"))));
    }

    @Test
    void hexists_missingField_returns0() {
        hset.handle(null, args("HSET", "h1", "f1", "v1"));
        assertEquals(0L, intVal(hexists.handle(null, args("HEXISTS", "h1", "missing"))));
    }

    @Test
    void hexists_missingKey_returns0() {
        assertEquals(0L, intVal(hexists.handle(null, args("HEXISTS", "nokey", "f"))));
    }

    // ── HKEYS / HVALS / HLEN ─────────────────────────────────────────────────

    @Test
    void hkeys_returnsAllFields() {
        hset.handle(null, args("HSET", "h1", "a", "1", "b", "2"));
        RespMessage result = hkeys.handle(null, args("HKEYS", "h1"));
        assertEquals(2, ((RespMessage.RespArray) result).elements().size());
    }

    @Test
    void hvals_returnsAllValues() {
        hset.handle(null, args("HSET", "h1", "a", "1", "b", "2"));
        RespMessage result = hvals.handle(null, args("HVALS", "h1"));
        assertEquals(2, ((RespMessage.RespArray) result).elements().size());
    }

    @Test
    void hlen_returnsFieldCount() {
        hset.handle(null, args("HSET", "h1", "a", "1", "b", "2", "c", "3"));
        assertEquals(3L, intVal(hlen.handle(null, args("HLEN", "h1"))));
    }

    @Test
    void hlen_missingKey_returns0() {
        assertEquals(0L, intVal(hlen.handle(null, args("HLEN", "nokey"))));
    }

    // ── HMSET ─────────────────────────────────────────────────────────────────

    @Test
    void hmset_returnsOk() {
        RespMessage result = hset.handle(null, args("HMSET", "h1", "f1", "v1", "f2", "v2"));
        assertEquals("OK", ((RespMessage.SimpleString) result).value());
    }

    // ── HMGET ─────────────────────────────────────────────────────────────────

    @Test
    void hmget_returnsValuesIncludingNil() {
        hset.handle(null, args("HSET", "h1", "f1", "v1", "f2", "v2"));
        RespMessage result = hmget.handle(null, args("HMGET", "h1", "f1", "missing", "f2"));
        List<RespMessage> elements = ((RespMessage.RespArray) result).elements();
        assertEquals(3, elements.size());
        assertEquals("v1", bulkStr(elements.get(0)));
        assertInstanceOf(RespMessage.NullBulkString.class, elements.get(1));
        assertEquals("v2", bulkStr(elements.get(2)));
    }

    // ── HINCRBY ───────────────────────────────────────────────────────────────

    @Test
    void hincrby_incrementsFromZero() {
        assertEquals(5L, intVal(hincrby.handle(null, args("HINCRBY", "h1", "count", "5"))));
    }

    @Test
    void hincrby_incrementsExisting() {
        hset.handle(null, args("HSET", "h1", "count", "10"));
        assertEquals(13L, intVal(hincrby.handle(null, args("HINCRBY", "h1", "count", "3"))));
    }

    @Test
    void hincrby_negativeIncrement() {
        hset.handle(null, args("HSET", "h1", "count", "10"));
        assertEquals(7L, intVal(hincrby.handle(null, args("HINCRBY", "h1", "count", "-3"))));
    }

    @Test
    void hincrby_nonIntegerField_returnsError() {
        hset.handle(null, args("HSET", "h1", "f", "notanumber"));
        assertError(hincrby.handle(null, args("HINCRBY", "h1", "f", "1")), "ERR");
    }

    // ── HINCRBYFLOAT ──────────────────────────────────────────────────────────

    @Test
    void hincrbyfloat_incrementsFromZero() {
        RespMessage result = hincrbyfloat.handle(null, args("HINCRBYFLOAT", "h1", "score", "1.5"));
        assertEquals("1.5", bulkStr(result));
    }

    @Test
    void hincrbyfloat_incrementsExisting() {
        hset.handle(null, args("HSET", "h1", "score", "10.25"));
        RespMessage result = hincrbyfloat.handle(null, args("HINCRBYFLOAT", "h1", "score", "0.75"));
        assertEquals("11", bulkStr(result));
    }

    @Test
    void hincrbyfloat_invalidFloat_returnsError() {
        hset.handle(null, args("HSET", "h1", "f", "notfloat"));
        assertError(hincrbyfloat.handle(null, args("HINCRBYFLOAT", "h1", "f", "1.0")), "ERR");
    }

    // ── HSETNX ────────────────────────────────────────────────────────────────

    @Test
    void hsetnx_setsWhenFieldAbsent() {
        assertEquals(1L, intVal(hsetnx.handle(null, args("HSETNX", "h1", "f1", "v1"))));
        assertEquals("v1", bulkStr(hget.handle(null, args("HGET", "h1", "f1"))));
    }

    @Test
    void hsetnx_doesNotSetWhenFieldExists() {
        hset.handle(null, args("HSET", "h1", "f1", "original"));
        assertEquals(0L, intVal(hsetnx.handle(null, args("HSETNX", "h1", "f1", "new"))));
        assertEquals("original", bulkStr(hget.handle(null, args("HGET", "h1", "f1"))));
    }

    // ── HSCAN ─────────────────────────────────────────────────────────────────

    @Test
    void hscan_iteratesFields() {
        hset.handle(null, args("HSET", "h1", "a", "1", "b", "2", "c", "3"));
        RespMessage result = hscan.handle(null, args("HSCAN", "h1", "0"));
        RespMessage.RespArray array = (RespMessage.RespArray) result;
        assertEquals(2, array.elements().size());
        RespMessage.RespArray items = (RespMessage.RespArray) array.elements().get(1);
        assertTrue(items.elements().size() > 0);
    }

    @Test
    void hscan_missingKey_returnsEmptyWithZeroCursor() {
        RespMessage result = hscan.handle(null, args("HSCAN", "nokey", "0"));
        RespMessage.RespArray array = (RespMessage.RespArray) result;
        assertEquals("0", bulkStr(array.elements().get(0)));
        assertEquals(0, ((RespMessage.RespArray) array.elements().get(1)).elements().size());
    }

    // ── TYPE ──────────────────────────────────────────────────────────────────

    @Test
    void typeReturnsHashForHashKey() {
        hset.handle(null, args("HSET", "h1", "f", "v"));
        assertEquals("hash", ((RespMessage.SimpleString) typeCmd.handle(null, args("TYPE", "h1"))).value());
    }

    @Test
    void typeReturnsStringForStringKey() {
        kvStore.set("s", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertEquals("string", ((RespMessage.SimpleString) typeCmd.handle(null, args("TYPE", "s"))).value());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }

    private long intVal(RespMessage msg) {
        return ((RespMessage.RespInteger) msg).value();
    }

    private String bulkStr(RespMessage msg) {
        byte[] bytes = ((RespMessage.BulkString) msg).value();
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private void assertError(RespMessage msg, String prefix) {
        assertInstanceOf(RespMessage.Error.class, msg);
        assertTrue(((RespMessage.Error) msg).message().startsWith(prefix),
                "Expected error starting with '" + prefix + "' but got: " + ((RespMessage.Error) msg).message());
    }
}
