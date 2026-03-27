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

class ExistsCommandTest {

    private KvStore kvStore;
    private ExistsCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new ExistsCommand(kvStore);
    }

    @Test
    void returnsOneForExistingKey() {
        kvStore.set("k1", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertEquals(1L, ((RespMessage.RespInteger) cmd.handle(null, args("EXISTS", "k1"))).value());
    }

    @Test
    void returnsZeroForMissingKey() {
        assertEquals(0L, ((RespMessage.RespInteger) cmd.handle(null, args("EXISTS", "missing"))).value());
    }

    @Test
    void returnsZeroForExpiredKey() {
        // Insert an already-expired entry directly
        kvStore.getStore().put("tmp", new KvStore.Entry(
                new StoreValue.StringValue("v".getBytes(StandardCharsets.UTF_8)),
                System.currentTimeMillis() - 5000,
                System.currentTimeMillis() - 100,
                false));
        assertEquals(0L, ((RespMessage.RespInteger) cmd.handle(null, args("EXISTS", "tmp"))).value());
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("EXISTS")));
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
