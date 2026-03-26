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

class ZRangeByScoreCommandTest {

    private KvStore kvStore;
    private ZAddCommand zadd;
    private ZRangeByScoreCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        zadd = new ZAddCommand(kvStore);
        cmd = new ZRangeByScoreCommand(kvStore);
    }

    @Test
    void basicRange() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c", "4", "d"));
        List<String> members = members(cmd.handle(null, args("ZRANGEBYSCORE", "z", "2", "3")));
        assertEquals(List.of("b", "c"), members);
    }

    @Test
    void withInf() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c"));
        List<String> members = members(cmd.handle(null, args("ZRANGEBYSCORE", "z", "-inf", "+inf")));
        assertEquals(List.of("a", "b", "c"), members);
    }

    @Test
    void exclusive() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c"));
        List<String> members = members(cmd.handle(null, args("ZRANGEBYSCORE", "z", "(1", "(3")));
        assertEquals(List.of("b"), members);
    }

    @Test
    void withScores() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b"));
        List<String> items = members(cmd.handle(null, args("ZRANGEBYSCORE", "z", "-inf", "+inf", "WITHSCORES")));
        assertEquals(List.of("a", "1", "b", "2"), items);
    }

    @Test
    void withLimit() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c", "4", "d"));
        List<String> members = members(cmd.handle(null, args("ZRANGEBYSCORE", "z", "-inf", "+inf", "LIMIT", "1", "2")));
        assertEquals(List.of("b", "c"), members);
    }

    @Test
    void zrevrangebyscore() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c"));
        // ZREVRANGEBYSCORE: max first, min second
        List<String> members = members(cmd.handle(null, args("ZREVRANGEBYSCORE", "z", "3", "1")));
        assertEquals(List.of("c", "b", "a"), members);
    }

    @Test
    void emptyForMissingKey() {
        assertTrue(members(cmd.handle(null, args("ZRANGEBYSCORE", "nokey", "-inf", "+inf"))).isEmpty());
    }

    @Test
    void wrongTypeError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("ZRANGEBYSCORE", "str", "-inf", "+inf")));
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
