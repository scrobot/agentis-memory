package io.agentis.memory.command.zset;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZAddCommandTest {

    private KvStore kvStore;
    private ZAddCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new ZAddCommand(kvStore);
    }

    @Test
    void addsSingleMember() {
        RespMessage result = cmd.handle(null, args("ZADD", "z", "1.5", "a"));
        assertInstanceOf(RespMessage.RespInteger.class, result);
        assertEquals(1L, ((RespMessage.RespInteger) result).value());
        assertEquals(1.5, getScore("z", "a"));
    }

    @Test
    void addsMultipleMembers() {
        RespMessage result = cmd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c"));
        assertEquals(3L, ((RespMessage.RespInteger) result).value());
    }

    @Test
    void updatesExistingMemberScore() {
        cmd.handle(null, args("ZADD", "z", "1", "a"));
        RespMessage result = cmd.handle(null, args("ZADD", "z", "5", "a"));
        // Update does not count as "added"
        assertEquals(0L, ((RespMessage.RespInteger) result).value());
        assertEquals(5.0, getScore("z", "a"));
    }

    @Test
    void chFlagCountsUpdates() {
        cmd.handle(null, args("ZADD", "z", "1", "a"));
        RespMessage result = cmd.handle(null, args("ZADD", "z", "CH", "5", "a", "2", "b"));
        assertEquals(2L, ((RespMessage.RespInteger) result).value()); // 1 updated + 1 added
    }

    @Test
    void nxOptionSkipsExisting() {
        cmd.handle(null, args("ZADD", "z", "1", "a"));
        cmd.handle(null, args("ZADD", "z", "NX", "99", "a"));
        assertEquals(1.0, getScore("z", "a")); // unchanged
    }

    @Test
    void xxOptionSkipsNew() {
        RespMessage result = cmd.handle(null, args("ZADD", "z", "XX", "1", "newmember"));
        assertEquals(0L, ((RespMessage.RespInteger) result).value());
        assertNull(getSortedSet("z"));
    }

    @Test
    void nxAndXxAreIncompatible() {
        RespMessage result = cmd.handle(null, args("ZADD", "z", "NX", "XX", "1", "a"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    @Test
    void incrMode() {
        cmd.handle(null, args("ZADD", "z", "5", "a"));
        RespMessage result = cmd.handle(null, args("ZADD", "z", "INCR", "3", "a"));
        assertInstanceOf(RespMessage.BulkString.class, result);
        assertEquals("8", new String(((RespMessage.BulkString) result).value(), StandardCharsets.UTF_8));
    }

    @Test
    void errorOnWrongType() {
        kvStore.set("str", "value".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = cmd.handle(null, args("ZADD", "str", "1", "a"));
        assertInstanceOf(RespMessage.Error.class, result);
        assertTrue(((RespMessage.Error) result).message().contains("WRONGTYPE"));
    }

    @Test
    void errorOnInvalidScore() {
        RespMessage result = cmd.handle(null, args("ZADD", "z", "notafloat", "a"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    @Test
    void errorOnTooFewArgs() {
        RespMessage result = cmd.handle(null, args("ZADD", "z", "1"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    @Test
    void typeReturnsZset() {
        cmd.handle(null, args("ZADD", "z", "1", "a"));
        KvStore.Entry entry = kvStore.getEntry("z");
        assertNotNull(entry);
        assertInstanceOf(StoreValue.SortedSetValue.class, entry.value());
    }

    private Double getScore(String key, String member) {
        KvStore.Entry e = kvStore.getEntry(key);
        if (e == null || !(e.value() instanceof StoreValue.SortedSetValue sv)) return null;
        return sv.memberToScore().get(member);
    }

    private StoreValue.SortedSetValue getSortedSet(String key) {
        KvStore.Entry e = kvStore.getEntry(key);
        if (e == null || !(e.value() instanceof StoreValue.SortedSetValue sv)) return null;
        return sv;
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
