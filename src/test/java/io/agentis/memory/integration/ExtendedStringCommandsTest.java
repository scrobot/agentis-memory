package io.agentis.memory.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExtendedStringCommandsTest extends AbstractIntegrationTest {

    @BeforeEach
    void flushKeys() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushDB();
        }
    }

    // --- MSET / MGET ---

    @Test
    void testMsetAndMget() {
        try (Jedis jedis = jedisPool.getResource()) {
            String result = jedis.mset("k1", "v1", "k2", "v2", "k3", "v3");
            assertEquals("OK", result);

            List<String> values = jedis.mget("k1", "k2", "k3", "missing");
            assertEquals(4, values.size());
            assertEquals("v1", values.get(0));
            assertEquals("v2", values.get(1));
            assertEquals("v3", values.get(2));
            assertNull(values.get(3));
        }
    }

    // --- INCR / DECR ---

    @Test
    void testIncrDecr() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals(1L, jedis.incr("counter"));
            assertEquals(2L, jedis.incr("counter"));
            assertEquals(1L, jedis.decr("counter"));
        }
    }

    @Test
    void testIncrByDecrBy() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals(10L, jedis.incrBy("counter", 10));
            assertEquals(3L, jedis.decrBy("counter", 7));
        }
    }

    @Test
    void testIncrByFloat() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("counter", "10.5");
            double result = jedis.incrByFloat("counter", 0.1);
            assertEquals(10.6, result, 0.0001);
        }
    }

    // --- APPEND / STRLEN ---

    @Test
    void testAppendAndStrlen() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals(5L, jedis.append("appkey", "hello"));
            assertEquals(11L, jedis.append("appkey", " world"));
            assertEquals("hello world", jedis.get("appkey"));
            assertEquals(11L, jedis.strlen("appkey"));
        }
    }

    @Test
    void testStrlenMissingKey() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals(0L, jedis.strlen("missing_strlen_key"));
        }
    }

    // --- SETNX ---

    @Test
    void testSetnx() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals(1L, jedis.setnx("setnxkey", "first"));
            assertEquals(0L, jedis.setnx("setnxkey", "second"));
            assertEquals("first", jedis.get("setnxkey"));
        }
    }

    // --- GETSET ---

    @Test
    void testGetset() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertNull(jedis.getSet("getsetkey", "v1"));
            assertEquals("v1", jedis.getSet("getsetkey", "v2"));
            assertEquals("v2", jedis.get("getsetkey"));
        }
    }

    // --- GETDEL ---

    @Test
    void testGetdel() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("getdelkey", "toDelete");
            assertEquals("toDelete", jedis.getDel("getdelkey"));
            assertNull(jedis.get("getdelkey"));
        }
    }

    @Test
    void testGetdelMissingKey() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertNull(jedis.getDel("missing_getdel_key"));
        }
    }
}
