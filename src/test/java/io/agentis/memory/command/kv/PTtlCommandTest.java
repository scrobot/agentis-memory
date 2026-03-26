package io.agentis.memory.command.kv;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PTtlCommandTest {

    private KvStore kvStore;
    private PTtlCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new PTtlCommand(kvStore);
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }

    @Test
    void returnsNegativeTwoForMissingKey() {
        assertEquals(-2L, ((RespMessage.RespInteger) cmd.handle(null, args("PTTL", "missing"))).value());
    }

    @Test
    void returnsNegativeOneForKeyWithoutTtl() {
        kvStore.set("k", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertEquals(-1L, ((RespMessage.RespInteger) cmd.handle(null, args("PTTL", "k"))).value());
    }

    @Test
    void returnsPositiveMillisecondsForKeyWithTtl() {
        kvStore.set("k", "v".getBytes(StandardCharsets.UTF_8), -1);
        kvStore.expire("k", 10); // 10 seconds = 10000ms
        long pttl = ((RespMessage.RespInteger) cmd.handle(null, args("PTTL", "k"))).value();
        assertTrue(pttl > 0 && pttl <= 10000, "PTTL should be between 0 and 10000ms, got: " + pttl);
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("PTTL")));
    }
}
