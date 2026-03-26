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

class RenameCommandTest {

    private KvStore kvStore;
    private RenameCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new RenameCommand(kvStore);
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }

    @Test
    void renameExistingKey() {
        kvStore.set("src", "hello".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage resp = cmd.handle(null, args("RENAME", "src", "dst"));
        assertInstanceOf(RespMessage.SimpleString.class, resp);
        assertEquals("OK", ((RespMessage.SimpleString) resp).value());
        assertNull(kvStore.get("src"));
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), kvStore.get("dst"));
    }

    @Test
    void renameNonExistentKeyReturnsError() {
        RespMessage resp = cmd.handle(null, args("RENAME", "missing", "dst"));
        assertInstanceOf(RespMessage.Error.class, resp);
        assertTrue(((RespMessage.Error) resp).message().contains("no such key"));
    }

    @Test
    void renameOverwritesExistingDest() {
        kvStore.set("src", "new".getBytes(StandardCharsets.UTF_8), -1);
        kvStore.set("dst", "old".getBytes(StandardCharsets.UTF_8), -1);
        cmd.handle(null, args("RENAME", "src", "dst"));
        assertArrayEquals("new".getBytes(StandardCharsets.UTF_8), kvStore.get("dst"));
    }

    @Test
    void renamePresentsTtl() {
        kvStore.set("src", "v".getBytes(StandardCharsets.UTF_8), -1);
        kvStore.expire("src", 100);
        cmd.handle(null, args("RENAME", "src", "dst"));
        KvStore.Entry entry = kvStore.getEntry("dst");
        assertNotNull(entry);
        assertNotEquals(-1, entry.expireAt()); // TTL preserved
    }

    @Test
    void renameErrorOnMissingArgs() {
        RespMessage resp = cmd.handle(null, args("RENAME", "src"));
        assertInstanceOf(RespMessage.Error.class, resp);
    }

    // RENAMENX tests

    @Test
    void renamenxSucceedsWhenDestAbsent() {
        kvStore.set("src", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage resp = cmd.handle(null, args("RENAMENX", "src", "dst"));
        assertInstanceOf(RespMessage.RespInteger.class, resp);
        assertEquals(1L, ((RespMessage.RespInteger) resp).value());
        assertNull(kvStore.get("src"));
        assertNotNull(kvStore.get("dst"));
    }

    @Test
    void renamenxFailsWhenDestExists() {
        kvStore.set("src", "v".getBytes(StandardCharsets.UTF_8), -1);
        kvStore.set("dst", "existing".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage resp = cmd.handle(null, args("RENAMENX", "src", "dst"));
        assertInstanceOf(RespMessage.RespInteger.class, resp);
        assertEquals(0L, ((RespMessage.RespInteger) resp).value());
        // src should still exist
        assertNotNull(kvStore.get("src"));
    }

    @Test
    void renamenxErrorWhenSrcMissing() {
        RespMessage resp = cmd.handle(null, args("RENAMENX", "missing", "dst"));
        assertInstanceOf(RespMessage.Error.class, resp);
    }
}
