package io.agentis.memory.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for list commands (LPUSH, RPUSH, LPOP, LRANGE, TYPE).
 * Starts a real Agentis Memory server and connects via Jedis.
 */
class ListCommandsIntegrationTest extends AbstractIntegrationTest {

    @BeforeEach
    void flushDb() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushDB();
        }
    }

    // -------------------------------------------------------------------------
    // LPUSH / RPUSH
    // -------------------------------------------------------------------------

    @Test
    void lpush_returnsLengthAndPrependsElements() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals(1L, jedis.lpush("mylist", "a"));
            assertEquals(2L, jedis.lpush("mylist", "b"));
            // List is [b, a] — LRANGE 0 -1
            List<String> all = jedis.lrange("mylist", 0, -1);
            assertEquals(List.of("b", "a"), all);
        }
    }

    @Test
    void rpush_appendsElementsToTail() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.rpush("mylist", "a", "b", "c");
            assertEquals(List.of("a", "b", "c"), jedis.lrange("mylist", 0, -1));
        }
    }

    @Test
    void lpush_multipleElementsPrependsLeftToRight() {
        try (Jedis jedis = jedisPool.getResource()) {
            // LPUSH mylist a b c → [c, b, a]
            jedis.lpush("mylist", "a", "b", "c");
            assertEquals(List.of("c", "b", "a"), jedis.lrange("mylist", 0, -1));
        }
    }

    // -------------------------------------------------------------------------
    // LPOP / RPOP
    // -------------------------------------------------------------------------

    @Test
    void lpop_returnsHeadElement() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.rpush("mylist", "a", "b", "c");
            assertEquals("a", jedis.lpop("mylist"));
            assertEquals(List.of("b", "c"), jedis.lrange("mylist", 0, -1));
        }
    }

    @Test
    void rpop_returnsTailElement() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.rpush("mylist", "a", "b", "c");
            assertEquals("c", jedis.rpop("mylist"));
        }
    }

    @Test
    void lpop_nilOnMissingKey() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertNull(jedis.lpop("mylist"));
        }
    }

    @Test
    void rpop_nilOnMissingKey() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertNull(jedis.rpop("mylist"));
        }
    }

    // -------------------------------------------------------------------------
    // LRANGE
    // -------------------------------------------------------------------------

    @Test
    void lrange_fullRange() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.rpush("mylist", "x", "y", "z");
            assertEquals(List.of("x", "y", "z"), jedis.lrange("mylist", 0, -1));
        }
    }

    @Test
    void lrange_emptyOnMissingKey() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertTrue(jedis.lrange("mylist", 0, -1).isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // TYPE
    // -------------------------------------------------------------------------

    @Test
    void typeReturnsListForListKey() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.rpush("mylist", "v");
            assertEquals("list", jedis.type("mylist"));
        }
    }

    @Test
    void typeReturnsNoneForMissingKey() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals("none", jedis.type("mylist"));
        }
    }
}
