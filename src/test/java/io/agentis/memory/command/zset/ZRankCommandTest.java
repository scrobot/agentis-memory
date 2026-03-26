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

class ZRankCommandTest {

    private KvStore kvStore;
    private ZAddCommand zadd;
    private ZRankCommand zrank;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        zadd = new ZAddCommand(kvStore);
        zrank = new ZRankCommand(kvStore);
    }

    @Test
    void returnsRankAscending() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c"));
        assertEquals(0L, rankOf("z", "a"));
        assertEquals(1L, rankOf("z", "b"));
        assertEquals(2L, rankOf("z", "c"));
    }

    @Test
    void returnsRevRank() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c"));
        assertEquals(2L, revRankOf("z", "a"));
        assertEquals(1L, revRankOf("z", "b"));
        assertEquals(0L, revRankOf("z", "c"));
    }

    @Test
    void nilForMissingMember() {
        zadd.handle(null, args("ZADD", "z", "1", "a"));
        assertInstanceOf(RespMessage.NullBulkString.class, zrank.handle(null, args("ZRANK", "z", "missing")));
    }

    @Test
    void nilForMissingKey() {
        assertInstanceOf(RespMessage.NullBulkString.class, zrank.handle(null, args("ZRANK", "nokey", "a")));
    }

    @Test
    void wrongTypeError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, zrank.handle(null, args("ZRANK", "str", "a")));
    }

    private long rankOf(String key, String member) {
        return ((RespMessage.RespInteger) zrank.handle(null, args("ZRANK", key, member))).value();
    }

    private long revRankOf(String key, String member) {
        return ((RespMessage.RespInteger) zrank.handle(null, args("ZREVRANK", key, member))).value();
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
