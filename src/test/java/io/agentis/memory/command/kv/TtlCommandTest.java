package io.agentis.memory.command.kv;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TtlCommandTest {

    private KvStore kvStore;
    private TtlCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new TtlCommand(kvStore);
    }

    @Test
    void returnsNegativeTwoForMissingKey() {
        assertEquals(-2L, ((RespMessage.RespInteger) cmd.handle(null, args("TTL", "missing"))).value());
    }

    @Test
    void returnsNegativeOneForKeyWithoutTtl() {
        kvStore.set("k1", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertEquals(-1L, ((RespMessage.RespInteger) cmd.handle(null, args("TTL", "k1"))).value());
    }

    @Test
    void returnsPositiveTtlForKeyWithExpiry() {
        kvStore.set("k1", "v".getBytes(StandardCharsets.UTF_8), 10);
        long ttl = ((RespMessage.RespInteger) cmd.handle(null, args("TTL", "k1"))).value();
        assertTrue(ttl > 0 && ttl <= 10, "Expected TTL between 1 and 10, got: " + ttl);
    }

    @Test
    void returnsNegativeTwoForExpiredKey() {
        // Insert an already-expired entry directly
        kvStore.getStore().put("k1", new KvStore.Entry(
                new StoreValue.StringValue("v".getBytes(StandardCharsets.UTF_8)),
                System.currentTimeMillis() - 5000,
                System.currentTimeMillis() - 100,
                false));
        assertEquals(-2L, ((RespMessage.RespInteger) cmd.handle(null, args("TTL", "k1"))).value());
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("TTL")));
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
