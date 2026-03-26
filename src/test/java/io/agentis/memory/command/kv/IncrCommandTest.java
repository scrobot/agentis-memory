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

class IncrCommandTest {

    private KvStore kvStore;
    private IncrCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new IncrCommand(kvStore);
    }

    // --- INCR ---

    @Test
    void incrCreatesKeyAt1() {
        RespMessage result = cmd.handle(null, args("INCR", "counter"));
        assertEquals(1L, ((RespMessage.RespInteger) result).value());
    }

    @Test
    void incrIncrementsExistingKey() {
        kvStore.set("counter", "10".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = cmd.handle(null, args("INCR", "counter"));
        assertEquals(11L, ((RespMessage.RespInteger) result).value());
    }

    @Test
    void incrReturnsErrorOnNonInteger() {
        kvStore.set("k", "abc".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = cmd.handle(null, args("INCR", "k"));
        assertInstanceOf(RespMessage.Error.class, result);
        assertTrue(((RespMessage.Error) result).message().contains("not an integer"));
    }

    @Test
    void incrOverflowReturnsError() {
        kvStore.set("k", Long.toString(Long.MAX_VALUE).getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = cmd.handle(null, args("INCR", "k"));
        assertInstanceOf(RespMessage.Error.class, result);
        assertTrue(((RespMessage.Error) result).message().contains("overflow"));
    }

    // --- DECR ---

    @Test
    void decrCreatesKeyAtMinus1() {
        RespMessage result = cmd.handle(null, args("DECR", "counter"));
        assertEquals(-1L, ((RespMessage.RespInteger) result).value());
    }

    @Test
    void decrDecrementsExistingKey() {
        kvStore.set("counter", "5".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = cmd.handle(null, args("DECR", "counter"));
        assertEquals(4L, ((RespMessage.RespInteger) result).value());
    }

    // --- INCRBY ---

    @Test
    void incrByAddsAmount() {
        kvStore.set("k", "10".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = cmd.handle(null, args("INCRBY", "k", "5"));
        assertEquals(15L, ((RespMessage.RespInteger) result).value());
    }

    @Test
    void incrByErrorOnInvalidIncrement() {
        RespMessage result = cmd.handle(null, args("INCRBY", "k", "notanumber"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    // --- DECRBY ---

    @Test
    void decrBySubtractsAmount() {
        kvStore.set("k", "20".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = cmd.handle(null, args("DECRBY", "k", "7"));
        assertEquals(13L, ((RespMessage.RespInteger) result).value());
    }

    // --- INCRBYFLOAT ---

    @Test
    void incrByFloatAddsFloat() {
        kvStore.set("k", "10.5".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = cmd.handle(null, args("INCRBYFLOAT", "k", "0.1"));
        assertInstanceOf(RespMessage.BulkString.class, result);
        String val = new String(((RespMessage.BulkString) result).value(), StandardCharsets.UTF_8);
        assertEquals(10.6, Double.parseDouble(val), 0.0001);
    }

    @Test
    void incrByFloatCreatesKeyFrom0() {
        RespMessage result = cmd.handle(null, args("INCRBYFLOAT", "newkey", "3.14"));
        assertInstanceOf(RespMessage.BulkString.class, result);
        String val = new String(((RespMessage.BulkString) result).value(), StandardCharsets.UTF_8);
        assertEquals(3.14, Double.parseDouble(val), 0.0001);
    }

    @Test
    void incrByFloatErrorOnNonFloat() {
        kvStore.set("k", "abc".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = cmd.handle(null, args("INCRBYFLOAT", "k", "1.0"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
