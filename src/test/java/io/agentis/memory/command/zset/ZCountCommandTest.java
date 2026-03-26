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

class ZCountCommandTest {

    private KvStore kvStore;
    private ZAddCommand zadd;
    private ZCountCommand zcount;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        zadd = new ZAddCommand(kvStore);
        zcount = new ZCountCommand(kvStore);
    }

    @Test
    void countsInRange() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c", "4", "d"));
        assertEquals(3L, count("z", "2", "4"));
    }

    @Test
    void countsWithInf() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c"));
        assertEquals(3L, count("z", "-inf", "+inf"));
    }

    @Test
    void exclusiveBoundary() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c"));
        assertEquals(1L, count("z", "(1", "(3")); // only 2
    }

    @Test
    void returnsZeroForEmptyRange() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b"));
        assertEquals(0L, count("z", "5", "10"));
    }

    @Test
    void returnsZeroForMissingKey() {
        assertEquals(0L, count("nokey", "-inf", "+inf"));
    }

    @Test
    void wrongTypeError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, zcount.handle(null, args("ZCOUNT", "str", "-inf", "+inf")));
    }

    private long count(String key, String min, String max) {
        return ((RespMessage.RespInteger) zcount.handle(null, args("ZCOUNT", key, min, max))).value();
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
