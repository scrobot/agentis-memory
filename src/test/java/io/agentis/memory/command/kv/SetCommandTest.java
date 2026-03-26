package io.agentis.memory.command.kv;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SetCommandTest {

    private KvStore kvStore;
    private SetCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new SetCommand(kvStore);
    }

    @Test
    void setsValueAndReturnsOk() {
        RespMessage result = cmd.handle(null, args("SET", "mykey", "myvalue"));
        assertInstanceOf(RespMessage.SimpleString.class, result);
        assertEquals("OK", ((RespMessage.SimpleString) result).value());
        assertArrayEquals("myvalue".getBytes(StandardCharsets.UTF_8), kvStore.get("mykey"));
    }

    @Test
    void setsValueWithTtl() {
        RespMessage result = cmd.handle(null, args("SET", "ttlkey", "v", "EX", "60"));
        assertInstanceOf(RespMessage.SimpleString.class, result);
        assertEquals("OK", ((RespMessage.SimpleString) result).value());
        assertNotNull(kvStore.get("ttlkey"));
    }

    @Test
    void setexSyntax() {
        RespMessage result = cmd.handle(null, args("SETEX", "exkey", "60", "exvalue"));
        assertInstanceOf(RespMessage.SimpleString.class, result);
        assertEquals("OK", ((RespMessage.SimpleString) result).value());
        assertArrayEquals("exvalue".getBytes(StandardCharsets.UTF_8), kvStore.get("exkey"));
    }

    @Test
    void errorOnMissingArgs() {
        RespMessage result = cmd.handle(null, args("SET", "onlykey"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    @Test
    void errorOnInvalidTtl() {
        RespMessage result = cmd.handle(null, args("SET", "k", "v", "EX", "notanumber"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    // --- NX (set only if not exists) ---

    @Test
    void nxSetsWhenKeyAbsent() {
        RespMessage result = cmd.handle(null, args("SET", "nxkey", "val", "NX"));
        assertInstanceOf(RespMessage.SimpleString.class, result);
        assertEquals("OK", ((RespMessage.SimpleString) result).value());
        assertArrayEquals("val".getBytes(StandardCharsets.UTF_8), kvStore.get("nxkey"));
    }

    @Test
    void nxDoesNotSetWhenKeyPresent() {
        kvStore.set("nxkey", "original".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = cmd.handle(null, args("SET", "nxkey", "new", "NX"));
        assertInstanceOf(RespMessage.NullBulkString.class, result);
        assertArrayEquals("original".getBytes(StandardCharsets.UTF_8), kvStore.get("nxkey"));
    }

    // --- XX (set only if exists) ---

    @Test
    void xxDoesNotSetWhenKeyAbsent() {
        RespMessage result = cmd.handle(null, args("SET", "xxkey", "val", "XX"));
        assertInstanceOf(RespMessage.NullBulkString.class, result);
        assertNull(kvStore.get("xxkey"));
    }

    @Test
    void xxSetsWhenKeyPresent() {
        kvStore.set("xxkey", "original".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = cmd.handle(null, args("SET", "xxkey", "updated", "XX"));
        assertInstanceOf(RespMessage.SimpleString.class, result);
        assertEquals("OK", ((RespMessage.SimpleString) result).value());
        assertArrayEquals("updated".getBytes(StandardCharsets.UTF_8), kvStore.get("xxkey"));
    }

    // --- PX (millisecond TTL) ---

    @Test
    void setsValueWithPxTtl() {
        RespMessage result = cmd.handle(null, args("SET", "pxkey", "v", "PX", "60000"));
        assertInstanceOf(RespMessage.SimpleString.class, result);
        assertEquals("OK", ((RespMessage.SimpleString) result).value());
        assertNotNull(kvStore.get("pxkey"));
    }

    @Test
    void pxTtlOfOneMillisExpiresKey() throws InterruptedException {
        RespMessage result = cmd.handle(null, args("SET", "shortlived", "v", "PX", "1"));
        assertInstanceOf(RespMessage.SimpleString.class, result);
        Thread.sleep(5);
        assertNull(kvStore.get("shortlived"));
    }

    @Test
    void errorOnInvalidPxTtl() {
        RespMessage result = cmd.handle(null, args("SET", "k", "v", "PX", "notanumber"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    @Test
    void errorOnZeroPxTtl() {
        RespMessage result = cmd.handle(null, args("SET", "k", "v", "PX", "0"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    // --- GET (return old value) ---

    @Test
    void getOptionReturnsNilWhenKeyAbsent() {
        RespMessage result = cmd.handle(null, args("SET", "newkey", "val", "GET"));
        assertInstanceOf(RespMessage.NullBulkString.class, result);
        assertArrayEquals("val".getBytes(StandardCharsets.UTF_8), kvStore.get("newkey"));
    }

    @Test
    void getOptionReturnsPreviousValue() {
        kvStore.set("gkey", "oldval".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = cmd.handle(null, args("SET", "gkey", "newval", "GET"));
        assertInstanceOf(RespMessage.BulkString.class, result);
        assertArrayEquals("oldval".getBytes(StandardCharsets.UTF_8), ((RespMessage.BulkString) result).value());
        assertArrayEquals("newval".getBytes(StandardCharsets.UTF_8), kvStore.get("gkey"));
    }

    // --- NX + GET combination ---

    @Test
    void nxGetReturnsPreviousWhenConditionNotMet() {
        kvStore.set("k", "existing".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = cmd.handle(null, args("SET", "k", "newval", "NX", "GET"));
        assertInstanceOf(RespMessage.BulkString.class, result);
        // NX failed (key exists) → GET returns current (= old) value
        assertArrayEquals("existing".getBytes(StandardCharsets.UTF_8), ((RespMessage.BulkString) result).value());
        // Value was NOT overwritten
        assertArrayEquals("existing".getBytes(StandardCharsets.UTF_8), kvStore.get("k"));
    }

    @Test
    void nxGetReturnsNilWhenKeyAbsentAndSets() {
        RespMessage result = cmd.handle(null, args("SET", "fresh", "val", "NX", "GET"));
        assertInstanceOf(RespMessage.NullBulkString.class, result);
        assertArrayEquals("val".getBytes(StandardCharsets.UTF_8), kvStore.get("fresh"));
    }

    // --- Mutual exclusion ---

    @Test
    void nxAndXxTogetherIsError() {
        RespMessage result = cmd.handle(null, args("SET", "k", "v", "NX", "XX"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    // --- EXAT / PXAT ---

    @Test
    void exatInTheFutureStoresKey() {
        long futureUnixSecs = (System.currentTimeMillis() / 1000) + 3600;
        RespMessage result = cmd.handle(null, args("SET", "exatkey", "v", "EXAT", String.valueOf(futureUnixSecs)));
        assertInstanceOf(RespMessage.SimpleString.class, result);
        assertNotNull(kvStore.get("exatkey"));
    }

    @Test
    void pxatInTheFutureStoresKey() {
        long futureMs = System.currentTimeMillis() + 60_000;
        RespMessage result = cmd.handle(null, args("SET", "pxatkey", "v", "PXAT", String.valueOf(futureMs)));
        assertInstanceOf(RespMessage.SimpleString.class, result);
        assertNotNull(kvStore.get("pxatkey"));
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
