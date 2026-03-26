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

class PersistCommandTest {

    private KvStore kvStore;
    private PersistCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new PersistCommand(kvStore);
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }

    @Test
    void removesTtlFromKeyWithExpiry() {
        kvStore.set("k", "v".getBytes(StandardCharsets.UTF_8), -1);
        kvStore.expire("k", 100);
        KvStore.Entry before = kvStore.getEntry("k");
        assertNotEquals(-1, before.expireAt());

        RespMessage resp = cmd.handle(null, args("PERSIST", "k"));
        assertEquals(1L, ((RespMessage.RespInteger) resp).value());

        KvStore.Entry after = kvStore.getEntry("k");
        assertEquals(-1, after.expireAt());
    }

    @Test
    void returnsZeroForKeyWithoutTtl() {
        kvStore.set("k", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage resp = cmd.handle(null, args("PERSIST", "k"));
        assertEquals(0L, ((RespMessage.RespInteger) resp).value());
    }

    @Test
    void returnsZeroForMissingKey() {
        RespMessage resp = cmd.handle(null, args("PERSIST", "missing"));
        assertEquals(0L, ((RespMessage.RespInteger) resp).value());
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("PERSIST")));
    }
}
