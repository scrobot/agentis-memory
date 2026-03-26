package io.agentis.memory.integration;

import org.junit.jupiter.api.*;
import redis.clients.jedis.Jedis;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerCommandsTest extends AbstractIntegrationTest {

    @BeforeEach
    void flushBefore() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushDB();
        }
    }

    @Test
    void testSelect0() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals("OK", jedis.select(0));
        }
    }

    @Test
    void testSelectNonZeroReturnsError() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertThrows(Exception.class, () -> jedis.select(1));
        }
    }

    @Test
    void testFlushDb() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("key1", "val1");
            jedis.set("key2", "val2");
            assertEquals(2L, jedis.dbSize());

            assertEquals("OK", jedis.flushDB());
            assertEquals(0L, jedis.dbSize());
        }
    }

    @Test
    void testFlushAll() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("key1", "val1");
            jedis.set("key2", "val2");
            assertEquals(2L, jedis.dbSize());

            assertEquals("OK", jedis.flushAll());
            assertEquals(0L, jedis.dbSize());
        }
    }

    @Test
    void testEcho() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals("hello world", jedis.echo("hello world"));
        }
    }

    @Test
    void testEchoEmpty() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals("", jedis.echo(""));
        }
    }

    @Test
    void testTime() {
        try (Jedis jedis = jedisPool.getResource()) {
            List<String> time = jedis.time();
            assertNotNull(time);
            assertEquals(2, time.size());

            long seconds = Long.parseLong(time.get(0));
            long micros = Long.parseLong(time.get(1));

            long nowSeconds = System.currentTimeMillis() / 1000;
            assertTrue(Math.abs(seconds - nowSeconds) <= 10, "TIME seconds should be close to current time");
            assertTrue(micros >= 0 && micros < 1_000_000, "TIME microseconds should be in [0, 1000000)");
        }
    }
}
