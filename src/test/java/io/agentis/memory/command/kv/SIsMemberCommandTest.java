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

class SIsMemberCommandTest {

    private KvStore kvStore;
    private SAddCommand addCmd;
    private SIsMemberCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        addCmd = new SAddCommand(kvStore);
        cmd = new SIsMemberCommand(kvStore);
    }

    @Test
    void returnsOneForExistingMember() {
        addCmd.handle(null, args("SADD", "s", "a"));
        assertEquals(1L, ((RespMessage.RespInteger) cmd.handle(null, args("SISMEMBER", "s", "a"))).value());
    }

    @Test
    void returnsZeroForMissingMember() {
        addCmd.handle(null, args("SADD", "s", "a"));
        assertEquals(0L, ((RespMessage.RespInteger) cmd.handle(null, args("SISMEMBER", "s", "x"))).value());
    }

    @Test
    void returnsZeroForMissingKey() {
        assertEquals(0L, ((RespMessage.RespInteger) cmd.handle(null, args("SISMEMBER", "missing", "m"))).value());
    }

    @Test
    void wrongTypeReturnsError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("SISMEMBER", "str", "m")));
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("SISMEMBER", "s")));
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
