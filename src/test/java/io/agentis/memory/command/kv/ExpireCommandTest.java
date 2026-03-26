package io.agentis.memory.command.kv;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExpireCommandTest {

    private KvStore kvStore;
    private ExpireCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new ExpireCommand(kvStore);
    }

    @Test
    void returnsOneForExistingKey() {
        kvStore.set("k1", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertEquals(1L, ((RespMessage.RespInteger) cmd.handle(null, args("EXPIRE", "k1", "10"))).value());
    }

    @Test
    void returnsZeroForMissingKey() {
        assertEquals(0L, ((RespMessage.RespInteger) cmd.handle(null, args("EXPIRE", "missing", "10"))).value());
    }

    @Test
    void keyExpiresAfterTtl() throws InterruptedException {
        kvStore.set("k1", "v".getBytes(StandardCharsets.UTF_8), -1);
        cmd.handle(null, args("EXPIRE", "k1", "1"));
        assertNotNull(kvStore.get("k1"));
        Thread.sleep(1100);
        assertNull(kvStore.get("k1"));
    }

    @Test
    void errorOnInvalidTtl() {
        kvStore.set("k1", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("EXPIRE", "k1", "notanumber")));
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("EXPIRE", "k1")));
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
