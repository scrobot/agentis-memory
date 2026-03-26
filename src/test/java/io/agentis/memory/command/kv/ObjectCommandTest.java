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

class ObjectCommandTest {

    private KvStore kvStore;
    private ObjectCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new ObjectCommand(kvStore);
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }

    @Test
    void helpReturnsArray() {
        RespMessage resp = cmd.handle(null, args("OBJECT", "HELP"));
        assertInstanceOf(RespMessage.RespArray.class, resp);
        assertFalse(((RespMessage.RespArray) resp).elements().isEmpty());
    }

    @Test
    void encodingReturnsEmbstrForShortValue() {
        kvStore.set("k", "hello".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage resp = cmd.handle(null, args("OBJECT", "ENCODING", "k"));
        assertInstanceOf(RespMessage.BulkString.class, resp);
        assertEquals("embstr", new String(((RespMessage.BulkString) resp).value(), StandardCharsets.UTF_8));
    }

    @Test
    void encodingReturnsRawForLongValue() {
        // 45 bytes > 44 threshold for embstr
        byte[] longValue = new byte[45];
        Arrays.fill(longValue, (byte) 'x');
        kvStore.set("k", longValue, -1);
        RespMessage resp = cmd.handle(null, args("OBJECT", "ENCODING", "k"));
        assertInstanceOf(RespMessage.BulkString.class, resp);
        assertEquals("raw", new String(((RespMessage.BulkString) resp).value(), StandardCharsets.UTF_8));
    }

    @Test
    void encodingErrorForMissingKey() {
        RespMessage resp = cmd.handle(null, args("OBJECT", "ENCODING", "missing"));
        assertInstanceOf(RespMessage.Error.class, resp);
    }

    @Test
    void refcountReturnsOne() {
        kvStore.set("k", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage resp = cmd.handle(null, args("OBJECT", "REFCOUNT", "k"));
        assertEquals(1L, ((RespMessage.RespInteger) resp).value());
    }

    @Test
    void refcountErrorForMissingKey() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("OBJECT", "REFCOUNT", "missing")));
    }

    @Test
    void idletimeReturnsZero() {
        kvStore.set("k", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage resp = cmd.handle(null, args("OBJECT", "IDLETIME", "k"));
        assertEquals(0L, ((RespMessage.RespInteger) resp).value());
    }

    @Test
    void freqReturnsZero() {
        kvStore.set("k", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage resp = cmd.handle(null, args("OBJECT", "FREQ", "k"));
        assertEquals(0L, ((RespMessage.RespInteger) resp).value());
    }

    @Test
    void unknownSubcommandReturnsError() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("OBJECT", "BOGUS")));
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("OBJECT")));
    }
}
