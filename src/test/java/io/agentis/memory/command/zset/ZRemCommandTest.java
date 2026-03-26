package io.agentis.memory.command.zset;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZRemCommandTest {

    private KvStore kvStore;
    private ZAddCommand zadd;
    private ZRemCommand zrem;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        zadd = new ZAddCommand(kvStore);
        zrem = new ZRemCommand(kvStore);
    }

    @Test
    void removesSingleMember() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b"));
        RespMessage result = zrem.handle(null, args("ZREM", "z", "a"));
        assertEquals(1L, ((RespMessage.RespInteger) result).value());
        assertNull(kvStore.getEntry("z").value() instanceof io.agentis.memory.store.StoreValue.SortedSetValue sv
                ? sv.memberToScore().get("a") : null);
    }

    @Test
    void removesMultipleMembers() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c"));
        RespMessage result = zrem.handle(null, args("ZREM", "z", "a", "c"));
        assertEquals(2L, ((RespMessage.RespInteger) result).value());
    }

    @Test
    void returnsZeroForNonexistentMember() {
        zadd.handle(null, args("ZADD", "z", "1", "a"));
        RespMessage result = zrem.handle(null, args("ZREM", "z", "missing"));
        assertEquals(0L, ((RespMessage.RespInteger) result).value());
    }

    @Test
    void returnsZeroForNonexistentKey() {
        RespMessage result = zrem.handle(null, args("ZREM", "nokey", "a"));
        assertEquals(0L, ((RespMessage.RespInteger) result).value());
    }

    @Test
    void deletesKeyWhenEmpty() {
        zadd.handle(null, args("ZADD", "z", "1", "a"));
        zrem.handle(null, args("ZREM", "z", "a"));
        assertNull(kvStore.getEntry("z"));
    }

    @Test
    void wrongTypeError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = zrem.handle(null, args("ZREM", "str", "a"));
        assertInstanceOf(RespMessage.Error.class, result);
        assertTrue(((RespMessage.Error) result).message().contains("WRONGTYPE"));
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
