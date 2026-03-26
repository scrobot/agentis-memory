package io.agentis.memory.integration;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespServer;
import io.avaje.inject.BeanScope;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.resps.Tuple;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for sorted set commands via Jedis.
 * Tests: ZADD, ZSCORE, ZRANGE, ZRANGEBYSCORE, ZRANK, ZREVRANK, ZCARD, ZCOUNT,
 *        ZINCRBY, ZREVRANGE, ZRANGEBYSCORE, ZRANGEBYLEX, ZREM, ZSCAN, TYPE.
 */
class SortedSetCommandsTest {

    private static final Logger log = LoggerFactory.getLogger(SortedSetCommandsTest.class);

    private static BeanScope scope;
    private static RespServer server;
    private static Jedis jedis;
    private static final int PORT = 6405;
    private static ExecutorService executor;

    @BeforeAll
    static void setup() {
        ServerConfig config = new ServerConfig();
        config.port = PORT;
        config.bind = "127.0.0.1";

        scope = BeanScope.builder()
                .bean(ServerConfig.class, config)
                .build();

        server = scope.get(RespServer.class);

        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                server.start();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Server test failure", e);
            }
        });

        jedis = new Jedis("127.0.0.1", PORT);
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    try {
                        return "PONG".equals(jedis.ping());
                    } catch (Exception e) {
                        return false;
                    }
                });
    }

    @AfterAll
    static void tearDown() {
        if (jedis != null) jedis.close();
        if (server != null) server.shutdown();
        if (executor != null) executor.shutdownNow();
        if (scope != null) scope.close();
    }

    @BeforeEach
    void cleanKey() {
        jedis.del("zs");
    }

    @Test
    void zaddAndZscore() {
        assertEquals(1L, jedis.zadd("zs", 1.5, "a"));
        assertEquals(1L, jedis.zadd("zs", 2.0, "b"));
        assertEquals("1.5", jedis.zscore("zs", "a").toString().replace(".5", ".5"));
        assertEquals(1.5, jedis.zscore("zs", "a"), 0.001);
        assertEquals(2.0, jedis.zscore("zs", "b"), 0.001);
        assertNull(jedis.zscore("zs", "missing"));
    }

    @Test
    void zaddMultipleAndZcard() {
        jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0));
        assertEquals(3L, jedis.zcard("zs"));
    }

    @Test
    void zrange() {
        jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0));
        List<String> members = jedis.zrange("zs", 0, -1);
        assertEquals(List.of("a", "b", "c"), members);
    }

    @Test
    void zrangeWithScores() {
        jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0));
        List<Tuple> tuples = jedis.zrangeWithScores("zs", 0, -1);
        assertEquals(2, tuples.size());
        assertEquals("a", tuples.get(0).getElement());
        assertEquals(1.0, tuples.get(0).getScore(), 0.001);
    }

    @Test
    void zrevrange() {
        jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0));
        List<String> members = jedis.zrevrange("zs", 0, -1);
        assertEquals(List.of("c", "b", "a"), members);
    }

    @Test
    void zrangebyscore() {
        jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0, "d", 4.0));
        List<String> members = jedis.zrangeByScore("zs", 2.0, 3.0);
        assertEquals(List.of("b", "c"), members);
    }

    @Test
    void zrangebyscoreWithLimit() {
        jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0, "d", 4.0));
        List<String> members = jedis.zrangeByScore("zs", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1, 2);
        assertEquals(List.of("b", "c"), members);
    }

    @Test
    void zrevrangebyscore() {
        jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0));
        List<String> members = jedis.zrevrangeByScore("zs", 3.0, 1.0);
        assertEquals(List.of("c", "b", "a"), members);
    }

    @Test
    void zrank() {
        jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0));
        assertEquals(0L, jedis.zrank("zs", "a"));
        assertEquals(1L, jedis.zrank("zs", "b"));
        assertEquals(2L, jedis.zrank("zs", "c"));
        assertNull(jedis.zrank("zs", "missing"));
    }

    @Test
    void zrevrank() {
        jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0));
        assertEquals(2L, jedis.zrevrank("zs", "a"));
        assertEquals(1L, jedis.zrevrank("zs", "b"));
        assertEquals(0L, jedis.zrevrank("zs", "c"));
    }

    @Test
    void zcount() {
        jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0, "d", 4.0));
        assertEquals(3L, jedis.zcount("zs", 2.0, 4.0));
        assertEquals(4L, jedis.zcount("zs", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY));
    }

    @Test
    void zincrby() {
        jedis.zadd("zs", 5.0, "a");
        double newScore = jedis.zincrby("zs", 3.0, "a");
        assertEquals(8.0, newScore, 0.001);
    }

    @Test
    void zrem() {
        jedis.zadd("zs", Map.of("a", 1.0, "b", 2.0, "c", 3.0));
        assertEquals(2L, jedis.zrem("zs", "a", "c"));
        assertEquals(1L, jedis.zcard("zs"));
        assertNull(jedis.zscore("zs", "a"));
    }

    @Test
    void zremDeletesKeyWhenEmpty() {
        jedis.zadd("zs", 1.0, "a");
        jedis.zrem("zs", "a");
        assertEquals("none", jedis.type("zs"));
    }

    @Test
    void typeReturnsZset() {
        jedis.zadd("zs", 1.0, "a");
        assertEquals("zset", jedis.type("zs"));
    }

    @Test
    void zaddNxOption() {
        jedis.zadd("zs", 1.0, "a");
        // NX: should not update existing
        jedis.zadd("zs", 99.0, "a", ZAddParams.zAddParams().nx());
        assertEquals(1.0, jedis.zscore("zs", "a"), 0.001);
    }

    @Test
    void zaddXxOption() {
        jedis.zadd("zs", 1.0, "a");
        // XX: update only if exists
        jedis.zadd("zs", 5.0, "a", ZAddParams.zAddParams().xx());
        assertEquals(5.0, jedis.zscore("zs", "a"), 0.001);
        // XX + new member → not added
        jedis.zadd("zs", 10.0, "newmember", ZAddParams.zAddParams().xx());
        assertNull(jedis.zscore("zs", "newmember"));
    }

    @Test
    void wrongTypeErrorOnStringKey() {
        jedis.set("strkey", "value");
        assertThrows(Exception.class, () -> jedis.zadd("strkey", 1.0, "a"));
    }
}
