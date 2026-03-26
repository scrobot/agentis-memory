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

class ZRangeByLexCommandTest {

    private KvStore kvStore;
    private ZAddCommand zadd;
    private ZRangeByLexCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        zadd = new ZAddCommand(kvStore);
        cmd = new ZRangeByLexCommand(kvStore);
    }

    @Test
    void fullRange() {
        zadd.handle(null, args("ZADD", "z", "0", "a", "0", "b", "0", "c", "0", "d"));
        List<String> members = members(cmd.handle(null, args("ZRANGEBYLEX", "z", "-", "+")));
        assertEquals(List.of("a", "b", "c", "d"), members);
    }

    @Test
    void inclusiveRange() {
        zadd.handle(null, args("ZADD", "z", "0", "a", "0", "b", "0", "c", "0", "d"));
        List<String> members = members(cmd.handle(null, args("ZRANGEBYLEX", "z", "[b", "[c")));
        assertEquals(List.of("b", "c"), members);
    }

    @Test
    void exclusiveRange() {
        zadd.handle(null, args("ZADD", "z", "0", "a", "0", "b", "0", "c", "0", "d"));
        List<String> members = members(cmd.handle(null, args("ZRANGEBYLEX", "z", "(a", "(d")));
        assertEquals(List.of("b", "c"), members);
    }

    @Test
    void withLimit() {
        zadd.handle(null, args("ZADD", "z", "0", "a", "0", "b", "0", "c", "0", "d"));
        List<String> members = members(cmd.handle(null, args("ZRANGEBYLEX", "z", "-", "+", "LIMIT", "1", "2")));
        assertEquals(List.of("b", "c"), members);
    }

    @Test
    void emptyForMissingKey() {
        assertTrue(members(cmd.handle(null, args("ZRANGEBYLEX", "nokey", "-", "+"))).isEmpty());
    }

    @Test
    void wrongTypeError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("ZRANGEBYLEX", "str", "-", "+")));
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
