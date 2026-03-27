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

class TypeCommandTest {

    private KvStore kvStore;
    private TypeCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new TypeCommand(kvStore);
    }

    @Test
    void returnsStringForExistingKey() {
        kvStore.set("k1", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = cmd.handle(null, args("TYPE", "k1"));
        assertEquals("string", ((RespMessage.SimpleString) result).value());
    }

    @Test
    void returnsNoneForMissingKey() {
        RespMessage result = cmd.handle(null, args("TYPE", "missing"));
        assertEquals("none", ((RespMessage.SimpleString) result).value());
    }

    @Test
    void returnsNoneForExpiredKey() {
        // Insert an already-expired entry directly
        kvStore.getStore().put("tmp", new KvStore.Entry(
                new StoreValue.StringValue("v".getBytes(StandardCharsets.UTF_8)),
                System.currentTimeMillis() - 5000,
                System.currentTimeMillis() - 100,
                false));
        assertEquals("none", ((RespMessage.SimpleString) cmd.handle(null, args("TYPE", "tmp"))).value());
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("TYPE")));
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
