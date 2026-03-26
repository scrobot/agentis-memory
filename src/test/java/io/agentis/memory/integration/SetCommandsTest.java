package io.agentis.memory.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.*;
import redis.clients.jedis.Jedis;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SetCommandsTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(SetCommandsTest.class);

    @Test
    void saddAndSmembers() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals(3L, jedis.sadd("s1", "a", "b", "c"));
            assertEquals(Set.of("a", "b", "c"), jedis.smembers("s1"));
        }
    }

    @Test
    void saddIgnoresDuplicates() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sadd("s1", "a", "b");
            assertEquals(1L, jedis.sadd("s1", "b", "c"));
            assertEquals(3L, jedis.scard("s1"));
        }
    }

    @Test
    void sremRemovesMembers() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sadd("s1", "a", "b", "c");
            assertEquals(2L, jedis.srem("s1", "a", "b"));
            assertEquals(Set.of("c"), jedis.smembers("s1"));
        }
    }

    @Test
    void scardReturnsSize() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sadd("s1", "x", "y");
            assertEquals(2L, jedis.scard("s1"));
        }
    }

    @Test
    void sismember() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sadd("s1", "hello");
            assertTrue(jedis.sismember("s1", "hello"));
            assertFalse(jedis.sismember("s1", "world"));
        }
    }

    @Test
    void typeReturnsSet() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sadd("s1", "member");
            assertEquals("set", jedis.type("s1"));
        }
    }

    @Test
    void sinterReturnsIntersection() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sadd("s1", "a", "b", "c");
            jedis.sadd("s2", "b", "c", "d");
            assertEquals(Set.of("b", "c"), jedis.sinter("s1", "s2"));
        }
    }

    @Test
    void sunionReturnsUnion() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sadd("s1", "a", "b");
            jedis.sadd("s2", "b", "c");
            assertEquals(Set.of("a", "b", "c"), jedis.sunion("s1", "s2"));
        }
    }

    @Test
    void sdiffReturnsDifference() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sadd("s1", "a", "b", "c");
            jedis.sadd("s2", "b", "c");
            assertEquals(Set.of("a"), jedis.sdiff("s1", "s2"));
        }
    }

}
