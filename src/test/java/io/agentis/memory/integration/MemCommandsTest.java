package io.agentis.memory.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class MemCommandsTest extends AbstractIntegrationTest {

    @BeforeEach
    void flush() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushDB();
        }
    }

    @Test
    void testMemSaveAndQuery() {
        try (Jedis jedis = jedisPool.getResource()) {
            // 1. MEMSAVE
            Object responseObj = jedis.sendCommand(() -> "MEMSAVE".getBytes(), "doc:1", "The capital of France is Paris.");
            String response = responseObj instanceof byte[] ? new String((byte[]) responseObj) : String.valueOf(responseObj);
            assertEquals("OK", response);

            // 2. Wait for indexing
            await().atMost(Duration.ofSeconds(15))
                    .until(() -> {
                        try (Jedis j = jedisPool.getResource()) {
                            List<Object> status = (List<Object>) j.sendCommand(() -> "MEMSTATUS".getBytes(), "doc:1");
                            return "indexed".equals(new String((byte[]) status.get(0)));
                        }
                    });

            // 3. MEMQUERY
            List<List<byte[]>> results = (List<List<byte[]>>) jedis.sendCommand(() -> "MEMQUERY".getBytes(), "doc", "What is the capital of France?", "1");
            assertFalse(results.isEmpty());
            assertEquals("doc:1", new String(results.get(0).get(0)));
            assertTrue(new String(results.get(0).get(1)).contains("Paris"));

            double score = Double.parseDouble(new String(results.get(0).get(2)));
            assertTrue(score > 0.5, "Score should be high for relevant query, but was " + score);

            // 4. MEMDEL
            Long deleted = (Long) jedis.sendCommand(() -> "MEMDEL".getBytes(), "doc:1");
            assertEquals(1L, deleted);

            // 5. Verify deleted
            List<List<byte[]>> emptyResults = (List<List<byte[]>>) jedis.sendCommand(() -> "MEMQUERY".getBytes(), "doc", "Paris", "1");
            assertTrue(emptyResults.isEmpty());
        }
    }

    @Test
    void testMemQueryAll() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sendCommand(() -> "MEMSAVE".getBytes(), "ns1:a", "Apple is a fruit.");
            jedis.sendCommand(() -> "MEMSAVE".getBytes(), "ns2:b", "Banana is a fruit.");

            await().atMost(Duration.ofSeconds(15))
                    .until(() -> {
                        try (Jedis j = jedisPool.getResource()) {
                            List<Object> s1 = (List<Object>) j.sendCommand(() -> "MEMSTATUS".getBytes(), "ns1:a");
                            List<Object> s2 = (List<Object>) j.sendCommand(() -> "MEMSTATUS".getBytes(), "ns2:b");
                            return "indexed".equals(new String((byte[]) s1.get(0))) && "indexed".equals(new String((byte[]) s2.get(0)));
                        }
                    });

            // Query specific namespace
            List<List<byte[]>> res1 = (List<List<byte[]>>) jedis.sendCommand(() -> "MEMQUERY".getBytes(), "ns1", "fruit", "10");
            assertEquals(1, res1.size());
            assertEquals("ns1:a", new String(res1.get(0).get(0)));

            // Query ALL namespaces
            List<List<byte[]>> resAll = (List<List<byte[]>>) jedis.sendCommand(() -> "MEMQUERY".getBytes(), "ALL", "fruit", "10");
            assertEquals(2, resAll.size());
        }
    }
}
