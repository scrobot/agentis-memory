package io.agentis.memory.command.kv;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RandomKeyCommandTest {

    private KvStore kvStore;
    private RandomKeyCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new RandomKeyCommand(kvStore);
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }

    @Test
    void returnsNilWhenDatabaseEmpty() {
        RespMessage resp = cmd.handle(null, args("RANDOMKEY"));
        assertInstanceOf(RespMessage.NullBulkString.class, resp);
    }

    @Test
    void returnsAKeyWhenDatabaseHasKeys() {
        kvStore.set("a", "1".getBytes(StandardCharsets.UTF_8), -1);
        kvStore.set("b", "2".getBytes(StandardCharsets.UTF_8), -1);
        kvStore.set("c", "3".getBytes(StandardCharsets.UTF_8), -1);

        RespMessage resp = cmd.handle(null, args("RANDOMKEY"));
        assertInstanceOf(RespMessage.BulkString.class, resp);
        String key = new String(((RespMessage.BulkString) resp).value(), StandardCharsets.UTF_8);
        assertTrue(Set.of("a", "b", "c").contains(key));
    }

    @Test
    void doesNotReturnExpiredKey() throws InterruptedException {
        kvStore.set("expired", "v".getBytes(StandardCharsets.UTF_8), -1);
        kvStore.expire("expired", 0); // expires immediately (0 seconds)
        Thread.sleep(50);
        kvStore.set("live", "v".getBytes(StandardCharsets.UTF_8), -1);

        // Repeat to reduce chance of flakiness
        for (int i = 0; i < 10; i++) {
            RespMessage resp = cmd.handle(null, args("RANDOMKEY"));
            if (resp instanceof RespMessage.BulkString bs) {
                String key = new String(bs.value(), StandardCharsets.UTF_8);
                assertNotEquals("expired", key);
            }
        }
    }
}
