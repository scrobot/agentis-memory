package io.agentis.memory.command.list;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ListCommandsTest {

    private KvStore kvStore;

    // Commands under test
    private LPushCommand lpush;
    private RPushCommand rpush;
    private LPopCommand lpop;
    private RPopCommand rpop;
    private LLenCommand llen;
    private LRangeCommand lrange;
    private LIndexCommand lindex;
    private LSetCommand lset;
    private LRemCommand lrem;
    private LInsertCommand linsert;
    private LTrimCommand ltrim;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        lpush   = new LPushCommand(kvStore);
        rpush   = new RPushCommand(kvStore);
        lpop    = new LPopCommand(kvStore);
        rpop    = new RPopCommand(kvStore);
        llen    = new LLenCommand(kvStore);
        lrange  = new LRangeCommand(kvStore);
        lindex  = new LIndexCommand(kvStore);
        lset    = new LSetCommand(kvStore);
        lrem    = new LRemCommand(kvStore);
        linsert = new LInsertCommand(kvStore);
        ltrim   = new LTrimCommand(kvStore);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }

    private long integer(RespMessage msg) {
        assertInstanceOf(RespMessage.RespInteger.class, msg);
        return ((RespMessage.RespInteger) msg).value();
    }

    private String bulkString(RespMessage msg) {
        if (msg instanceof RespMessage.NullBulkString) return null;
        assertInstanceOf(RespMessage.BulkString.class, msg);
        byte[] v = ((RespMessage.BulkString) msg).value();
        return v == null ? null : new String(v, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private List<RespMessage> array(RespMessage msg) {
        assertInstanceOf(RespMessage.RespArray.class, msg);
        return (List<RespMessage>) ((RespMessage.RespArray) msg).elements();
    }

    private String elemAt(RespMessage msg, int i) {
        List<RespMessage> elems = array(msg);
        return new String(((RespMessage.BulkString) elems.get(i)).value(), StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // LPUSH / RPUSH
    // -------------------------------------------------------------------------

    @Test
    void lpush_createsListAndReturnsLength() {
        RespMessage r = lpush.handle(null, args("LPUSH", "k", "a"));
        assertEquals(1, integer(r));
    }

    @Test
    void lpush_multipleElementsInsertedLeftToRight() {
        // LPUSH k a b c → list should be [c, b, a]
        lpush.handle(null, args("LPUSH", "k", "a", "b", "c"));
        assertEquals("c", elemAt(lrange.handle(null, args("LRANGE", "k", "0", "0")), 0));
        assertEquals("a", elemAt(lrange.handle(null, args("LRANGE", "k", "2", "2")), 0));
    }

    @Test
    void rpush_appendsToTail() {
        rpush.handle(null, args("RPUSH", "k", "a", "b", "c"));
        // List should be [a, b, c]
        assertEquals("a", elemAt(lrange.handle(null, args("LRANGE", "k", "0", "0")), 0));
        assertEquals("c", elemAt(lrange.handle(null, args("LRANGE", "k", "2", "2")), 0));
    }

    @Test
    void rpush_returnsLength() {
        assertEquals(3, integer(rpush.handle(null, args("RPUSH", "k", "a", "b", "c"))));
    }

    @Test
    void lpush_wrongTypeReturnsError() {
        // Pre-populate with string key
        kvStore.set("strkey", "value".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage r = lpush.handle(null, args("LPUSH", "strkey", "a"));
        assertInstanceOf(RespMessage.Error.class, r);
        assertTrue(((RespMessage.Error) r).message().startsWith("WRONGTYPE"));
    }

    @Test
    void rpush_missingArgsReturnsError() {
        RespMessage r = rpush.handle(null, args("RPUSH", "k"));
        assertInstanceOf(RespMessage.Error.class, r);
    }

    // -------------------------------------------------------------------------
    // LPOP / RPOP
    // -------------------------------------------------------------------------

    @Test
    void lpop_returnsHeadElement() {
        rpush.handle(null, args("RPUSH", "k", "a", "b", "c"));
        assertEquals("a", bulkString(lpop.handle(null, args("LPOP", "k"))));
    }

    @Test
    void rpop_returnsTailElement() {
        rpush.handle(null, args("RPUSH", "k", "a", "b", "c"));
        assertEquals("c", bulkString(rpop.handle(null, args("RPOP", "k"))));
    }

    @Test
    void lpop_deletesKeyWhenEmpty() {
        rpush.handle(null, args("RPUSH", "k", "a"));
        lpop.handle(null, args("LPOP", "k"));
        assertEquals(0, integer(llen.handle(null, args("LLEN", "k"))));
        assertNull(kvStore.getEntry("k"));
    }

    @Test
    void lpop_nilOnMissingKey() {
        RespMessage r = lpop.handle(null, args("LPOP", "nokey"));
        assertNull(bulkString(r));
    }

    @Test
    void lpop_withCount() {
        rpush.handle(null, args("RPUSH", "k", "a", "b", "c"));
        RespMessage r = lpop.handle(null, args("LPOP", "k", "2"));
        List<RespMessage> elems = array(r);
        assertEquals(2, elems.size());
        assertEquals("a", new String(((RespMessage.BulkString) elems.get(0)).value(), StandardCharsets.UTF_8));
        assertEquals("b", new String(((RespMessage.BulkString) elems.get(1)).value(), StandardCharsets.UTF_8));
    }

    @Test
    void rpop_withCount() {
        rpush.handle(null, args("RPUSH", "k", "a", "b", "c"));
        RespMessage r = rpop.handle(null, args("RPOP", "k", "2"));
        List<RespMessage> elems = array(r);
        assertEquals(2, elems.size());
        assertEquals("c", new String(((RespMessage.BulkString) elems.get(0)).value(), StandardCharsets.UTF_8));
        assertEquals("b", new String(((RespMessage.BulkString) elems.get(1)).value(), StandardCharsets.UTF_8));
    }

    @Test
    void lpop_negativeCountReturnsError() {
        rpush.handle(null, args("RPUSH", "k", "a"));
        RespMessage r = lpop.handle(null, args("LPOP", "k", "-1"));
        assertInstanceOf(RespMessage.Error.class, r);
    }

    // -------------------------------------------------------------------------
    // LLEN
    // -------------------------------------------------------------------------

    @Test
    void llen_returnsZeroForMissingKey() {
        assertEquals(0, integer(llen.handle(null, args("LLEN", "missing"))));
    }

    @Test
    void llen_returnsCorrectLength() {
        rpush.handle(null, args("RPUSH", "k", "a", "b", "c"));
        assertEquals(3, integer(llen.handle(null, args("LLEN", "k"))));
    }

    @Test
    void llen_wrongTypeReturnsError() {
        kvStore.set("s", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, llen.handle(null, args("LLEN", "s")));
    }

    // -------------------------------------------------------------------------
    // LRANGE
    // -------------------------------------------------------------------------

    @Test
    void lrange_fullRange() {
        rpush.handle(null, args("RPUSH", "k", "a", "b", "c"));
        RespMessage r = lrange.handle(null, args("LRANGE", "k", "0", "-1"));
        List<RespMessage> elems = array(r);
        assertEquals(3, elems.size());
        assertEquals("a", new String(((RespMessage.BulkString) elems.get(0)).value(), StandardCharsets.UTF_8));
        assertEquals("c", new String(((RespMessage.BulkString) elems.get(2)).value(), StandardCharsets.UTF_8));
    }

    @Test
    void lrange_emptyOnMissingKey() {
        RespMessage r = lrange.handle(null, args("LRANGE", "nokey", "0", "-1"));
        List<RespMessage> elems = array(r);
        assertTrue(elems.isEmpty());
    }

    @Test
    void lrange_negativeIndices() {
        rpush.handle(null, args("RPUSH", "k", "a", "b", "c"));
        // LRANGE k -2 -1 → [b, c]
        List<RespMessage> elems = array(lrange.handle(null, args("LRANGE", "k", "-2", "-1")));
        assertEquals(2, elems.size());
        assertEquals("b", new String(((RespMessage.BulkString) elems.get(0)).value(), StandardCharsets.UTF_8));
    }

    @Test
    void lrange_outOfRangeClamped() {
        rpush.handle(null, args("RPUSH", "k", "a", "b"));
        // stop > size — should be clamped, not an error
        List<RespMessage> elems = array(lrange.handle(null, args("LRANGE", "k", "0", "100")));
        assertEquals(2, elems.size());
    }

    // -------------------------------------------------------------------------
    // LINDEX
    // -------------------------------------------------------------------------

    @Test
    void lindex_positiveIndex() {
        rpush.handle(null, args("RPUSH", "k", "a", "b", "c"));
        assertEquals("b", bulkString(lindex.handle(null, args("LINDEX", "k", "1"))));
    }

    @Test
    void lindex_negativeIndex() {
        rpush.handle(null, args("RPUSH", "k", "a", "b", "c"));
        assertEquals("c", bulkString(lindex.handle(null, args("LINDEX", "k", "-1"))));
    }

    @Test
    void lindex_outOfRangeReturnsNil() {
        rpush.handle(null, args("RPUSH", "k", "a"));
        assertNull(bulkString(lindex.handle(null, args("LINDEX", "k", "99"))));
    }

    // -------------------------------------------------------------------------
    // LSET
    // -------------------------------------------------------------------------

    @Test
    void lset_updatesElement() {
        rpush.handle(null, args("RPUSH", "k", "a", "b", "c"));
        RespMessage r = lset.handle(null, args("LSET", "k", "1", "X"));
        assertInstanceOf(RespMessage.SimpleString.class, r);
        assertEquals("X", bulkString(lindex.handle(null, args("LINDEX", "k", "1"))));
    }

    @Test
    void lset_outOfRangeReturnsError() {
        rpush.handle(null, args("RPUSH", "k", "a"));
        assertInstanceOf(RespMessage.Error.class, lset.handle(null, args("LSET", "k", "99", "X")));
    }

    @Test
    void lset_missingKeyReturnsError() {
        assertInstanceOf(RespMessage.Error.class, lset.handle(null, args("LSET", "nokey", "0", "X")));
    }

    // -------------------------------------------------------------------------
    // LREM
    // -------------------------------------------------------------------------

    @Test
    void lrem_removesFromHead() {
        rpush.handle(null, args("RPUSH", "k", "a", "b", "a", "c", "a"));
        // LREM k 2 a → removes first 2 'a' → [b, c, a]
        assertEquals(2, integer(lrem.handle(null, args("LREM", "k", "2", "a"))));
        assertEquals(3, integer(llen.handle(null, args("LLEN", "k"))));
    }

    @Test
    void lrem_removesFromTail() {
        rpush.handle(null, args("RPUSH", "k", "a", "b", "a", "c", "a"));
        // LREM k -2 a → removes last 2 'a' → [a, b, c]
        assertEquals(2, integer(lrem.handle(null, args("LREM", "k", "-2", "a"))));
        assertEquals(3, integer(llen.handle(null, args("LLEN", "k"))));
    }

    @Test
    void lrem_removesAll() {
        rpush.handle(null, args("RPUSH", "k", "a", "b", "a"));
        assertEquals(2, integer(lrem.handle(null, args("LREM", "k", "0", "a"))));
        assertEquals(1, integer(llen.handle(null, args("LLEN", "k"))));
    }

    @Test
    void lrem_deletesKeyWhenEmpty() {
        rpush.handle(null, args("RPUSH", "k", "a"));
        lrem.handle(null, args("LREM", "k", "0", "a"));
        assertNull(kvStore.getEntry("k"));
    }

    // -------------------------------------------------------------------------
    // LINSERT
    // -------------------------------------------------------------------------

    @Test
    void linsert_before() {
        rpush.handle(null, args("RPUSH", "k", "a", "c"));
        assertEquals(3, integer(linsert.handle(null, args("LINSERT", "k", "BEFORE", "c", "b"))));
        assertEquals("b", elemAt(lrange.handle(null, args("LRANGE", "k", "1", "1")), 0));
    }

    @Test
    void linsert_after() {
        rpush.handle(null, args("RPUSH", "k", "a", "c"));
        assertEquals(3, integer(linsert.handle(null, args("LINSERT", "k", "AFTER", "a", "b"))));
        assertEquals("b", elemAt(lrange.handle(null, args("LRANGE", "k", "1", "1")), 0));
    }

    @Test
    void linsert_pivotNotFoundReturnsMinusOne() {
        rpush.handle(null, args("RPUSH", "k", "a", "b"));
        assertEquals(-1, integer(linsert.handle(null, args("LINSERT", "k", "BEFORE", "x", "y"))));
    }

    @Test
    void linsert_missingKeyReturnsZero() {
        assertEquals(0, integer(linsert.handle(null, args("LINSERT", "nokey", "BEFORE", "a", "b"))));
    }

    @Test
    void linsert_invalidWhereReturnsError() {
        rpush.handle(null, args("RPUSH", "k", "a"));
        assertInstanceOf(RespMessage.Error.class, linsert.handle(null, args("LINSERT", "k", "INVALID", "a", "b")));
    }

    // -------------------------------------------------------------------------
    // LTRIM
    // -------------------------------------------------------------------------

    @Test
    void ltrim_trimsList() {
        rpush.handle(null, args("RPUSH", "k", "a", "b", "c", "d"));
        ltrim.handle(null, args("LTRIM", "k", "1", "2"));
        List<RespMessage> elems = array(lrange.handle(null, args("LRANGE", "k", "0", "-1")));
        assertEquals(2, elems.size());
        assertEquals("b", new String(((RespMessage.BulkString) elems.get(0)).value(), StandardCharsets.UTF_8));
        assertEquals("c", new String(((RespMessage.BulkString) elems.get(1)).value(), StandardCharsets.UTF_8));
    }

    @Test
    void ltrim_emptyRangeDeletesKey() {
        rpush.handle(null, args("RPUSH", "k", "a", "b"));
        ltrim.handle(null, args("LTRIM", "k", "5", "10"));
        assertNull(kvStore.getEntry("k"));
    }

    @Test
    void ltrim_missingKeyIsNoOp() {
        RespMessage r = ltrim.handle(null, args("LTRIM", "nokey", "0", "5"));
        assertInstanceOf(RespMessage.SimpleString.class, r);
        assertEquals("OK", ((RespMessage.SimpleString) r).value());
    }

    // -------------------------------------------------------------------------
    // TYPE command (via TypeCommand — verified through KvStore state)
    // -------------------------------------------------------------------------

    @Test
    void typeIsListAfterLPush() {
        lpush.handle(null, args("LPUSH", "k", "a"));
        KvStore.Entry entry = kvStore.getEntry("k");
        assertNotNull(entry);
        assertInstanceOf(io.agentis.memory.store.StoreValue.ListValue.class, entry.value());
    }
}
