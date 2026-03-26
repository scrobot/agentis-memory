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

class SRemCommandTest {

    private KvStore kvStore;
    private SAddCommand addCmd;
    private SRemCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        addCmd = new SAddCommand(kvStore);
        cmd = new SRemCommand(kvStore);
    }

    @Test
    void removesExistingMembers() {
        addCmd.handle(null, args("SADD", "s", "a", "b", "c"));
        RespMessage result = cmd.handle(null, args("SREM", "s", "a", "b"));
        assertEquals(2L, ((RespMessage.RespInteger) result).value());
    }

    @Test
    void ignoresNonExistingMembers() {
        addCmd.handle(null, args("SADD", "s", "a"));
        RespMessage result = cmd.handle(null, args("SREM", "s", "x", "y"));
        assertEquals(0L, ((RespMessage.RespInteger) result).value());
    }

    @Test
    void returnsZeroForMissingKey() {
        RespMessage result = cmd.handle(null, args("SREM", "missing", "m"));
        assertEquals(0L, ((RespMessage.RespInteger) result).value());
    }

    @Test
    void deletesKeyWhenSetBecomesEmpty() {
        addCmd.handle(null, args("SADD", "s", "only"));
        cmd.handle(null, args("SREM", "s", "only"));
        assertNull(kvStore.getEntry("s"));
    }

    @Test
    void wrongTypeReturnsError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("SREM", "str", "m")));
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("SREM", "s")));
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
