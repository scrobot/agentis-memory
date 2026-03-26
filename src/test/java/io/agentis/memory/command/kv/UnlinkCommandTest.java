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

class UnlinkCommandTest {

    private KvStore kvStore;
    private UnlinkCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new UnlinkCommand(kvStore);
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }

    @Test
    void unlinksSingleKey() {
        kvStore.set("k", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage resp = cmd.handle(null, args("UNLINK", "k"));
        assertEquals(1L, ((RespMessage.RespInteger) resp).value());
        assertNull(kvStore.get("k"));
    }

    @Test
    void unlinksMultipleKeys() {
        kvStore.set("a", "1".getBytes(StandardCharsets.UTF_8), -1);
        kvStore.set("b", "2".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage resp = cmd.handle(null, args("UNLINK", "a", "b", "missing"));
        assertEquals(2L, ((RespMessage.RespInteger) resp).value());
    }

    @Test
    void returnsZeroForAllMissingKeys() {
        RespMessage resp = cmd.handle(null, args("UNLINK", "x", "y"));
        assertEquals(0L, ((RespMessage.RespInteger) resp).value());
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("UNLINK")));
    }
}
