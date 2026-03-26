package io.agentis.memory.integration;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespServer;
import io.avaje.inject.BeanScope;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Hash commands (2b) using Jedis against a live server.
 */
class HashCommandsIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(HashCommandsIntegrationTest.class);

    private static BeanScope scope;
    private static RespServer server;
    private static Jedis jedis;
    private static final int PORT = 6407;
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
    void cleanUp() {
        jedis.del("hash:test");
    }

    @Test
    void hset_and_hget() {
        assertEquals(2L, jedis.hset("hash:test", Map.of("name", "alice", "age", "30")));
        assertEquals("alice", jedis.hget("hash:test", "name"));
        assertEquals("30", jedis.hget("hash:test", "age"));
        assertNull(jedis.hget("hash:test", "missing"));
    }

    @Test
    void hgetall() {
        jedis.hset("hash:test", Map.of("k1", "v1", "k2", "v2"));
        Map<String, String> all = jedis.hgetAll("hash:test");
        assertEquals(2, all.size());
        assertEquals("v1", all.get("k1"));
        assertEquals("v2", all.get("k2"));
    }

    @Test
    void hdel_removesFieldAndReturnsCount() {
        jedis.hset("hash:test", Map.of("f1", "v1", "f2", "v2"));
        assertEquals(1L, jedis.hdel("hash:test", "f1"));
        assertNull(jedis.hget("hash:test", "f1"));
        assertEquals(1L, jedis.hlen("hash:test"));
    }

    @Test
    void hexists() {
        jedis.hset("hash:test", "field", "value");
        assertEquals(1L, jedis.hexists("hash:test", "field") ? 1L : 0L);
        assertFalse(jedis.hexists("hash:test", "missing"));
    }

    @Test
    void hkeys_and_hvals() {
        jedis.hset("hash:test", Map.of("a", "1", "b", "2"));
        List<String> keys = new java.util.ArrayList<>(jedis.hkeys("hash:test"));
        List<String> vals = jedis.hvals("hash:test");
        assertEquals(2, keys.size());
        assertEquals(2, vals.size());
        assertTrue(keys.contains("a"));
        assertTrue(keys.contains("b"));
    }

    @Test
    void hlen() {
        jedis.hset("hash:test", Map.of("f1", "v1", "f2", "v2", "f3", "v3"));
        assertEquals(3L, jedis.hlen("hash:test"));
    }

    @Test
    void hmset_and_hmget() {
        jedis.hmset("hash:test", Map.of("x", "10", "y", "20"));
        List<String> vals = jedis.hmget("hash:test", "x", "missing", "y");
        assertEquals(3, vals.size());
        assertEquals("10", vals.get(0));
        assertNull(vals.get(1));
        assertEquals("20", vals.get(2));
    }

    @Test
    void hincrby() {
        assertEquals(5L, jedis.hincrBy("hash:test", "counter", 5L));
        assertEquals(8L, jedis.hincrBy("hash:test", "counter", 3L));
        assertEquals(6L, jedis.hincrBy("hash:test", "counter", -2L));
    }

    @Test
    void hincrbyfloat() {
        double r1 = jedis.hincrByFloat("hash:test", "score", 1.5);
        assertEquals(1.5, r1, 0.001);
        double r2 = jedis.hincrByFloat("hash:test", "score", 0.25);
        assertEquals(1.75, r2, 0.001);
    }

    @Test
    void hsetnx_setOnlyIfAbsent() {
        assertEquals(1L, jedis.hsetnx("hash:test", "field", "first"));
        assertEquals(0L, jedis.hsetnx("hash:test", "field", "second"));
        assertEquals("first", jedis.hget("hash:test", "field"));
    }

    @Test
    void type_returnsHash() {
        jedis.hset("hash:test", "f", "v");
        assertEquals("hash", jedis.type("hash:test"));
    }

    @Test
    void wrongType_returnsError() {
        jedis.set("str:key", "value");
        assertThrows(Exception.class, () -> jedis.hget("str:key", "field"));
    }
}
