package io.agentis.memory.command.kv;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DelCommandTest {

    private KvStore kvStore;
    private DelCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new DelCommand(kvStore);
    }

    @Test
    void deletesExistingKey() {
        kvStore.set("k1", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = cmd.handle(null, args("DEL", "k1"));
        assertEquals(1L, ((RespMessage.RespInteger) result).value());
        assertNull(kvStore.get("k1"));
    }

    @Test
    void returnsZeroForMissingKey() {
        RespMessage result = cmd.handle(null, args("DEL", "missing"));
        assertEquals(0L, ((RespMessage.RespInteger) result).value());
    }

    @Test
    void deletesMultipleKeys() {
        kvStore.set("k1", "v".getBytes(StandardCharsets.UTF_8), -1);
        kvStore.set("k2", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = cmd.handle(null, args("DEL", "k1", "k2", "missing"));
        assertEquals(2L, ((RespMessage.RespInteger) result).value());
    }

    @Test
    void errorOnMissingArgs() {
        RespMessage result = cmd.handle(null, args("DEL"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
