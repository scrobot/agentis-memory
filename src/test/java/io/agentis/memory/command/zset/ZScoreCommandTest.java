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

class ZScoreCommandTest {

    private KvStore kvStore;
    private ZAddCommand zadd;
    private ZScoreCommand zscore;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        zadd = new ZAddCommand(kvStore);
        zscore = new ZScoreCommand(kvStore);
    }

    @Test
    void returnsScoreForExistingMember() {
        zadd.handle(null, args("ZADD", "z", "3.14", "pi"));
        RespMessage result = zscore.handle(null, args("ZSCORE", "z", "pi"));
        assertInstanceOf(RespMessage.BulkString.class, result);
        assertEquals("3.14", new String(((RespMessage.BulkString) result).value(), StandardCharsets.UTF_8));
    }

    @Test
    void returnsIntegerScoreWithoutDecimal() {
        zadd.handle(null, args("ZADD", "z", "5", "a"));
        RespMessage result = zscore.handle(null, args("ZSCORE", "z", "a"));
        assertEquals("5", new String(((RespMessage.BulkString) result).value(), StandardCharsets.UTF_8));
    }

    @Test
    void returnsNilForMissingMember() {
        zadd.handle(null, args("ZADD", "z", "1", "a"));
        assertInstanceOf(RespMessage.NullBulkString.class, zscore.handle(null, args("ZSCORE", "z", "missing")));
    }

    @Test
    void returnsNilForMissingKey() {
        assertInstanceOf(RespMessage.NullBulkString.class, zscore.handle(null, args("ZSCORE", "nokey", "a")));
    }

    @Test
    void wrongTypeError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = zscore.handle(null, args("ZSCORE", "str", "a"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
