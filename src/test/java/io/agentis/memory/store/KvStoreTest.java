package io.agentis.memory.store;

import io.agentis.memory.config.ServerConfig;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class KvStoreTest {

    @Test
    void testSetGet() {
        KvStore store = new KvStore(new ServerConfig());
        store.set("foo", "bar".getBytes(StandardCharsets.UTF_8), -1);
        byte[] val = store.get("foo");
        assertNotNull(val);
        assertEquals("bar", new String(val, StandardCharsets.UTF_8));
    }

    @Test
    void testExpiry() {
        KvStore store = new KvStore(new ServerConfig());
        store.set("temp", "value".getBytes(StandardCharsets.UTF_8), 1); // 1 second
        assertNotNull(store.get("temp"));

        await().atMost(Duration.ofSeconds(2))
                .pollDelay(Duration.ofMillis(1100))
                .until(() -> store.get("temp") == null);
    }

    @Test
    void testDelete() {
        KvStore store = new KvStore(new ServerConfig());
        store.set("foo", "bar".getBytes(StandardCharsets.UTF_8), -1);
        store.delete("foo");
        assertNull(store.get("foo"));
    }
}
