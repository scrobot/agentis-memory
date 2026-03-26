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

class ZRangeCommandTest {

    private KvStore kvStore;
    private ZAddCommand zadd;
    private ZRangeCommand zrange;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        zadd = new ZAddCommand(kvStore);
        zrange = new ZRangeCommand(kvStore);
    }

    @Test
    void indexRangeAll() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c"));
        List<String> members = members(zrange.handle(null, args("ZRANGE", "z", "0", "-1")));
        assertEquals(List.of("a", "b", "c"), members);
    }

    @Test
    void indexRangeSlice() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c", "4", "d"));
        List<String> members = members(zrange.handle(null, args("ZRANGE", "z", "1", "2")));
        assertEquals(List.of("b", "c"), members);
    }

    @Test
    void withScores() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b"));
        RespMessage result = zrange.handle(null, args("ZRANGE", "z", "0", "-1", "WITHSCORES"));
        List<String> items = members(result);
        assertEquals(List.of("a", "1", "b", "2"), items);
    }

    @Test
    void byScore() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c", "4", "d"));
        List<String> members = members(zrange.handle(null, args("ZRANGE", "z", "2", "3", "BYSCORE")));
        assertEquals(List.of("b", "c"), members);
    }

    @Test
    void byScoreWithInf() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c"));
        List<String> members = members(zrange.handle(null, args("ZRANGE", "z", "-inf", "+inf", "BYSCORE")));
        assertEquals(List.of("a", "b", "c"), members);
    }

    @Test
    void byScoreRev() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c"));
        // REV + BYSCORE: max first, min second in args
        List<String> members = members(zrange.handle(null, args("ZRANGE", "z", "3", "1", "BYSCORE", "REV")));
        assertEquals(List.of("c", "b", "a"), members);
    }

    @Test
    void zrevrange() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c"));
        List<String> members = members(zrange.handle(null, args("ZREVRANGE", "z", "0", "-1")));
        assertEquals(List.of("c", "b", "a"), members);
    }

    @Test
    void emptyForOutOfRange() {
        zadd.handle(null, args("ZADD", "z", "1", "a"));
        List<String> members = members(zrange.handle(null, args("ZRANGE", "z", "5", "10")));
        assertTrue(members.isEmpty());
    }

    @Test
    void emptyForMissingKey() {
        List<String> members = members(zrange.handle(null, args("ZRANGE", "nokey", "0", "-1")));
        assertTrue(members.isEmpty());
    }

    @Test
    void wrongTypeError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, zrange.handle(null, args("ZRANGE", "str", "0", "-1")));
    }

    private List<String> members(RespMessage msg) {
        assertInstanceOf(RespMessage.RespArray.class, msg);
        return ((RespMessage.RespArray) msg).elements().stream()
                .map(e -> new String(((RespMessage.BulkString) e).value(), StandardCharsets.UTF_8))
                .toList();
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
