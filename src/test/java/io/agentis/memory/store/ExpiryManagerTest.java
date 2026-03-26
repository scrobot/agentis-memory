package io.agentis.memory.store;

import io.agentis.memory.config.ServerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class ExpiryManagerTest {

    private KvStore kvStore;
    private ExpiryManager expiryManager;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        expiryManager = new ExpiryManager(kvStore);
    }

    @AfterEach
    void tearDown() {
        expiryManager.shutdown();
    }

    @Test
    void activeExpiryRemovesExpiredKeys() {
        // Insert 100 keys with TTL=1s
        for (int i = 0; i < 100; i++) {
            kvStore.set("key" + i, "value".getBytes(StandardCharsets.UTF_8), 1);
        }
        assertEquals(100, kvStore.size());

        // Wait 3s — active expiry should remove most of them
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> kvStore.size() < 10);

        assertTrue(kvStore.size() < 10,
                "Expected most keys to be removed by active expiry, remaining: " + kvStore.size());
    }

    @Test
    void nonExpiredKeysAreNotRemoved() {
        for (int i = 0; i < 20; i++) {
            kvStore.set("persist" + i, "v".getBytes(StandardCharsets.UTF_8), 3600);
        }
        expiryManager.runExpiryPass();
        assertEquals(20, kvStore.size());
    }

    @Test
    void runExpiryPassOnEmptyStoreDoesNotThrow() {
        assertDoesNotThrow(() -> expiryManager.runExpiryPass());
    }
}
