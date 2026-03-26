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

class ZCardCommandTest {

    private KvStore kvStore;
    private ZAddCommand zadd;
    private ZCardCommand zcard;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        zadd = new ZAddCommand(kvStore);
        zcard = new ZCardCommand(kvStore);
    }

    @Test
    void returnsCount() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c"));
        assertEquals(3L, ((RespMessage.RespInteger) zcard.handle(null, args("ZCARD", "z"))).value());
    }

    @Test
    void returnsZeroForMissingKey() {
        assertEquals(0L, ((RespMessage.RespInteger) zcard.handle(null, args("ZCARD", "nokey"))).value());
    }

    @Test
    void wrongTypeError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, zcard.handle(null, args("ZCARD", "str")));
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
