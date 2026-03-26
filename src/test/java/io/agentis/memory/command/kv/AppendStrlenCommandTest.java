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

class AppendStrlenCommandTest {

    private KvStore kvStore;
    private AppendCommand append;
    private StrlenCommand strlen;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        append = new AppendCommand(kvStore);
        strlen = new StrlenCommand(kvStore);
    }

    // --- APPEND ---

    @Test
    void appendCreatesKeyIfAbsent() {
        RespMessage result = append.handle(null, args("APPEND", "k", "hello"));
        assertEquals(5L, ((RespMessage.RespInteger) result).value());
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), kvStore.get("k"));
    }

    @Test
    void appendAppendsToExistingValue() {
        kvStore.set("k", "hello".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = append.handle(null, args("APPEND", "k", " world"));
        assertEquals(11L, ((RespMessage.RespInteger) result).value());
        assertArrayEquals("hello world".getBytes(StandardCharsets.UTF_8), kvStore.get("k"));
    }

    @Test
    void appendErrorOnMissingArgs() {
        RespMessage result = append.handle(null, args("APPEND", "k"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    // --- STRLEN ---

    @Test
    void strlenReturnsLength() {
        kvStore.set("k", "hello".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = strlen.handle(null, args("STRLEN", "k"));
        assertEquals(5L, ((RespMessage.RespInteger) result).value());
    }

    @Test
    void strlenReturnsZeroForMissingKey() {
        RespMessage result = strlen.handle(null, args("STRLEN", "missing"));
        assertEquals(0L, ((RespMessage.RespInteger) result).value());
    }

    @Test
    void strlenErrorOnMissingArgs() {
        RespMessage result = strlen.handle(null, args("STRLEN"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
