package io.agentis.memory.integration;

import org.junit.jupiter.api.*;
import redis.clients.jedis.Jedis;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BasicCommandsTest extends AbstractIntegrationTest {

    @Test
    void testPing() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals("PONG", jedis.ping());
        }
    }

    @Test
    void testSetGet() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals("OK", jedis.set("foo", "bar"));
            assertEquals("bar", jedis.get("foo"));
        }
    }

    @Test
    void testGetNonExistent() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertNull(jedis.get("nonexistent"));
        }
    }

    @Test
    void testSetEx() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex("temp", 1, "value");
            assertEquals("value", jedis.get("temp"));

            await().atMost(Duration.ofSeconds(2))
                    .pollDelay(Duration.ofMillis(1100))
                    .until(() -> {
                        try (Jedis j = jedisPool.getResource()) {
                            return j.get("temp") == null;
                        }
                    });
        }
    }
}
