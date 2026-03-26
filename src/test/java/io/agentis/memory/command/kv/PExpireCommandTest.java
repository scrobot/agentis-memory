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

class PExpireCommandTest {

    private KvStore kvStore;
    private PExpireCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new PExpireCommand(kvStore);
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }

    @Test
    void returnsOneForExistingKey() {
        kvStore.set("k", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage resp = cmd.handle(null, args("PEXPIRE", "k", "5000"));
        assertEquals(1L, ((RespMessage.RespInteger) resp).value());
    }

    @Test
    void returnsZeroForMissingKey() {
        RespMessage resp = cmd.handle(null, args("PEXPIRE", "missing", "5000"));
        assertEquals(0L, ((RespMessage.RespInteger) resp).value());
    }

    @Test
    void setsExpireAtInMilliseconds() throws InterruptedException {
        kvStore.set("k", "v".getBytes(StandardCharsets.UTF_8), -1);
        cmd.handle(null, args("PEXPIRE", "k", "100"));
        assertNotNull(kvStore.get("k"));
        Thread.sleep(200);
        assertNull(kvStore.get("k"));
    }

    @Test
    void errorOnInvalidMilliseconds() {
        kvStore.set("k", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("PEXPIRE", "k", "notanumber")));
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("PEXPIRE", "k")));
    }
}
