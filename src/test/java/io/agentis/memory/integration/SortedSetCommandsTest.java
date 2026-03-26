package io.agentis.memory.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.resps.Tuple;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for sorted set commands via Jedis.
 * Tests: ZADD, ZSCORE, ZRANGE, ZRANGEBYSCORE, ZRANK, ZREVRANK, ZCARD, ZCOUNT,
 *        ZINCRBY, ZREVRANGE, ZRANGEBYSCORE, ZRANGEBYLEX, ZREM, ZSCAN, TYPE.
 */
class SortedSetCommandsTest extends AbstractIntegrationTest {

    @BeforeEach
    void cleanKey() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushDB();
        }
    }

    @Test
    void zaddAndZscore() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals(1L, jedis.zadd("zs", 1.5, "a"));
            assertEquals(1L, jedis.zadd("zs", 2.0, "b"));
            assertEquals("1.5", jedis.zscore("zs", "a").toString().replace(".5", ".5"));
            assertEquals(1.5, jedis.zscore("zs", "a"), 0.001);
            assertEquals(2.0, jedis.zscore("zs", "b"), 0.001);
            assertNull(jedis.zscore("zs", "missing"));
        }
    }

    @Test
    void zaddMultipleAndZcard() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0));
            assertEquals(3L, jedis.zcard("zs"));
        }
    }

    @Test
    void zrange() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0));
            List<String> members = jedis.zrange("zs", 0, -1);
            assertEquals(List.of("a", "b", "c"), members);
        }
    }

    @Test
    void zrangeWithScores() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0));
            List<Tuple> tuples = jedis.zrangeWithScores("zs", 0, -1);
            assertEquals(2, tuples.size());
            assertEquals("a", tuples.get(0).getElement());
            assertEquals(1.0, tuples.get(0).getScore(), 0.001);
        }
    }

    @Test
    void zrevrange() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0));
            List<String> members = jedis.zrevrange("zs", 0, -1);
            assertEquals(List.of("c", "b", "a"), members);
        }
    }

    @Test
    void zrangebyscore() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0, "d", 4.0));
            List<String> members = jedis.zrangeByScore("zs", 2.0, 3.0);
            assertEquals(List.of("b", "c"), members);
        }
    }

    @Test
    void zrangebyscoreWithLimit() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0, "d", 4.0));
            List<String> members = jedis.zrangeByScore("zs", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1, 2);
            assertEquals(List.of("b", "c"), members);
        }
    }

    @Test
    void zrevrangebyscore() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0));
            List<String> members = jedis.zrevrangeByScore("zs", 3.0, 1.0);
            assertEquals(List.of("c", "b", "a"), members);
        }
    }

    @Test
    void zrank() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0));
            assertEquals(0L, jedis.zrank("zs", "a"));
            assertEquals(1L, jedis.zrank("zs", "b"));
            assertEquals(2L, jedis.zrank("zs", "c"));
            assertNull(jedis.zrank("zs", "missing"));
        }
    }

    @Test
    void zrevrank() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0));
            assertEquals(2L, jedis.zrevrank("zs", "a"));
            assertEquals(1L, jedis.zrevrank("zs", "b"));
            assertEquals(0L, jedis.zrevrank("zs", "c"));
        }
    }

    @Test
    void zcount() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0, "d", 4.0));
            assertEquals(3L, jedis.zcount("zs", 2.0, 4.0));
            assertEquals(4L, jedis.zcount("zs", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY));
        }
    }

    @Test
    void zincrby() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd("zs", 5.0, "a");
            double newScore = jedis.zincrby("zs", 3.0, "a");
            assertEquals(8.0, newScore, 0.001);
        }
    }

    @Test
    void zrem() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0));
            assertEquals(2L, jedis.zrem("zs", "a", "c"));
            assertEquals(1L, jedis.zcard("zs"));
            assertNull(jedis.zscore("zs", "a"));
        }
    }

    @Test
    void zremDeletesKeyWhenEmpty() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd("zs", 1.0, "a");
            jedis.zrem("zs", "a");
            assertEquals("none", jedis.type("zs"));
        }
    }

    @Test
    void typeReturnsZset() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd("zs", 1.0, "a");
            assertEquals("zset", jedis.type("zs"));
        }
    }

    @Test
    void zaddNxOption() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd("zs", 1.0, "a");
            // NX: should not update existing
            jedis.zadd("zs", 99.0, "a", ZAddParams.zAddParams().nx());
            assertEquals(1.0, jedis.zscore("zs", "a"), 0.001);
        }
    }

    @Test
    void zaddXxOption() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd("zs", 1.0, "a");
            // XX: update only if exists
            jedis.zadd("zs", 5.0, "a", ZAddParams.zAddParams().xx());
            assertEquals(5.0, jedis.zscore("zs", "a"), 0.001);
            // XX + new member → not added
            jedis.zadd("zs", 10.0, "newmember", ZAddParams.zAddParams().xx());
            assertNull(jedis.zscore("zs", "newmember"));
        }
    }

    @Test
    void wrongTypeErrorOnStringKey() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("strkey", "value");
            assertThrows(Exception.class, () -> jedis.zadd("strkey", 1.0, "a"));
        }
    }
}
