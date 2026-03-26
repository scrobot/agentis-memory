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

class SCardCommandTest {

    private KvStore kvStore;
    private SAddCommand addCmd;
    private SCardCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        addCmd = new SAddCommand(kvStore);
        cmd = new SCardCommand(kvStore);
    }

    @Test
    void returnsCardinalityOfSet() {
        addCmd.handle(null, args("SADD", "s", "a", "b", "c"));
        assertEquals(3L, ((RespMessage.RespInteger) cmd.handle(null, args("SCARD", "s"))).value());
    }

    @Test
    void returnsZeroForMissingKey() {
        assertEquals(0L, ((RespMessage.RespInteger) cmd.handle(null, args("SCARD", "missing"))).value());
    }

    @Test
    void wrongTypeReturnsError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("SCARD", "str")));
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("SCARD")));
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
