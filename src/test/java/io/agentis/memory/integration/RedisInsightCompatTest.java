package io.agentis.memory.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RedisInsightCompatTest extends AbstractIntegrationTest {

    @Test
    void infoReturnsServerSection() {
        try (Jedis jedis = jedisPool.getResource()) {
            String info = jedis.info();
            assertNotNull(info);
            assertTrue(info.contains("redis_version"), "INFO should contain redis_version");
            assertTrue(info.contains("# Server"), "INFO should contain # Server section");
        }
    }

    @Test
    void infoServerSection() {
        try (Jedis jedis = jedisPool.getResource()) {
            String info = jedis.info("server");
            assertTrue(info.contains("redis_version"));
            assertTrue(info.contains("tcp_port"));
        }
    }

    @Test
    void dbSizeReturnsCount() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("dbsize-test", "value");
            long size = jedis.dbSize();
            assertTrue(size >= 1);
        }
    }

    @Test
    void configGetSave() {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> result = jedis.configGet("save");
            assertFalse(result.isEmpty());
            assertTrue(result.containsKey("save"));
        }
    }

    @Test
    void configGetAppendonly() {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> result = jedis.configGet("appendonly");
            assertFalse(result.isEmpty());
            assertTrue(result.containsKey("appendonly"));
        }
    }

    @Test
    void configGetUnknownReturnsEmpty() {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> result = jedis.configGet("unknownparam123");
            assertTrue(result.isEmpty());
        }
    }

    @Test
    void scanZeroWorks() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("scan-key-1", "v1");
            jedis.set("scan-key-2", "v2");
            var result = jedis.scan("0");
            assertNotNull(result);
            assertNotNull(result.getResult());
        }
    }

    @Test
    void typeReturnsStringForExistingKey() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("type-test-key", "value");
            String type = jedis.type("type-test-key");
            assertEquals("string", type);
        }
    }

    @Test
    void typeReturnsNoneForMissingKey() {
        try (Jedis jedis = jedisPool.getResource()) {
            String type = jedis.type("nonexistent-key-xyz");
            assertEquals("none", type);
        }
    }

    @Test
    void clientSetName() {
        try (Jedis jedis = jedisPool.getResource()) {
            String result = jedis.clientSetname("test-client");
            assertEquals("OK", result);
        }
    }

    @Test
    void commandReturnsNoError() {
        try (Jedis fresh = jedisPool.getResource()) {
            assertNotNull(fresh.ping());
        }
    }

    @Test
    void delCommandWorks() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("del-test", "value");
            assertEquals(1L, jedis.del("del-test"));
            assertNull(jedis.get("del-test"));
        }
    }

    @Test
    void existsCommandWorks() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("exists-test", "value");
            assertTrue(jedis.exists("exists-test"));
            jedis.del("exists-test");
            assertFalse(jedis.exists("exists-test"));
        }
    }

    @Test
    void ttlAndExpireWork() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("ttl-test", "value");
            jedis.expire("ttl-test", 100L);
            long ttl = jedis.ttl("ttl-test");
            assertTrue(ttl > 0 && ttl <= 100);
        }
    }

    @Test
    void keysCommandWorks() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("keys-prefix-a", "v1");
            jedis.set("keys-prefix-b", "v2");
            var keys = jedis.keys("keys-prefix-*");
            assertTrue(keys.size() >= 2);
        }
    }
}
