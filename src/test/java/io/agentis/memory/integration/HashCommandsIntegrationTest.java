package io.agentis.memory.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Hash commands (2b) using Jedis against a live server.
 */
class HashCommandsIntegrationTest extends AbstractIntegrationTest {

    @BeforeEach
    void cleanUp() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushDB();
        }
    }

    @Test
    void hset_and_hget() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals(2L, jedis.hset("hash:test", Map.of("name", "alice", "age", "30")));
            assertEquals("alice", jedis.hget("hash:test", "name"));
            assertEquals("30", jedis.hget("hash:test", "age"));
            assertNull(jedis.hget("hash:test", "missing"));
        }
    }

    @Test
    void hgetall() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset("hash:test", Map.of("k1", "v1", "k2", "v2"));
            Map<String, String> all = jedis.hgetAll("hash:test");
            assertEquals(2, all.size());
            assertEquals("v1", all.get("k1"));
            assertEquals("v2", all.get("k2"));
        }
    }

    @Test
    void hdel_removesFieldAndReturnsCount() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset("hash:test", Map.of("f1", "v1", "f2", "v2"));
            assertEquals(1L, jedis.hdel("hash:test", "f1"));
            assertNull(jedis.hget("hash:test", "f1"));
            assertEquals(1L, jedis.hlen("hash:test"));
        }
    }

    @Test
    void hexists() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset("hash:test", "field", "value");
            assertEquals(1L, jedis.hexists("hash:test", "field") ? 1L : 0L);
            assertFalse(jedis.hexists("hash:test", "missing"));
        }
    }

    @Test
    void hkeys_and_hvals() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset("hash:test", Map.of("a", "1", "b", "2"));
            List<String> keys = new java.util.ArrayList<>(jedis.hkeys("hash:test"));
            List<String> vals = jedis.hvals("hash:test");
            assertEquals(2, keys.size());
            assertEquals(2, vals.size());
            assertTrue(keys.contains("a"));
            assertTrue(keys.contains("b"));
        }
    }

    @Test
    void hlen() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset("hash:test", Map.of("f1", "v1", "f2", "v2", "f3", "v3"));
            assertEquals(3L, jedis.hlen("hash:test"));
        }
    }

    @Test
    void hmset_and_hmget() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hmset("hash:test", Map.of("x", "10", "y", "20"));
            List<String> vals = jedis.hmget("hash:test", "x", "missing", "y");
            assertEquals(3, vals.size());
            assertEquals("10", vals.get(0));
            assertNull(vals.get(1));
            assertEquals("20", vals.get(2));
        }
    }

    @Test
    void hincrby() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals(5L, jedis.hincrBy("hash:test", "counter", 5L));
            assertEquals(8L, jedis.hincrBy("hash:test", "counter", 3L));
            assertEquals(6L, jedis.hincrBy("hash:test", "counter", -2L));
        }
    }

    @Test
    void hincrbyfloat() {
        try (Jedis jedis = jedisPool.getResource()) {
            double r1 = jedis.hincrByFloat("hash:test", "score", 1.5);
            assertEquals(1.5, r1, 0.001);
            double r2 = jedis.hincrByFloat("hash:test", "score", 0.25);
            assertEquals(1.75, r2, 0.001);
        }
    }

    @Test
    void hsetnx_setOnlyIfAbsent() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals(1L, jedis.hsetnx("hash:test", "field", "first"));
            assertEquals(0L, jedis.hsetnx("hash:test", "field", "second"));
            assertEquals("first", jedis.hget("hash:test", "field"));
        }
    }

    @Test
    void type_returnsHash() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset("hash:test", "f", "v");
            assertEquals("hash", jedis.type("hash:test"));
        }
    }

    @Test
    void wrongType_returnsError() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("str:key", "value");
            assertThrows(Exception.class, () -> jedis.hget("str:key", "field"));
        }
    }
}
