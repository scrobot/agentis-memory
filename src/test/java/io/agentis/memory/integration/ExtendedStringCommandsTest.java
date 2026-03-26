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

class ExtendedStringCommandsTest {
    private static final Logger log = LoggerFactory.getLogger(ExtendedStringCommandsTest.class);

    private static BeanScope scope;
    private static RespServer server;
    private static Jedis jedis;
    private static final int PORT = 6402;
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
                log.error("Server test thread interrupted", e);
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
    void flushKeys() {
        // Clean up test keys between tests
        jedis.del("k1", "k2", "k3", "counter", "appkey", "setnxkey", "getsetkey", "getdelkey", "getexkey");
    }

    // --- MSET / MGET ---

    @Test
    void testMsetAndMget() {
        String result = jedis.mset("k1", "v1", "k2", "v2", "k3", "v3");
        assertEquals("OK", result);

        List<String> values = jedis.mget("k1", "k2", "k3", "missing");
        assertEquals(4, values.size());
        assertEquals("v1", values.get(0));
        assertEquals("v2", values.get(1));
        assertEquals("v3", values.get(2));
        assertNull(values.get(3));
    }

    // --- INCR / DECR ---

    @Test
    void testIncrDecr() {
        assertEquals(1L, jedis.incr("counter"));
        assertEquals(2L, jedis.incr("counter"));
        assertEquals(1L, jedis.decr("counter"));
    }

    @Test
    void testIncrByDecrBy() {
        assertEquals(10L, jedis.incrBy("counter", 10));
        assertEquals(3L, jedis.decrBy("counter", 7));
    }

    @Test
    void testIncrByFloat() {
        jedis.set("counter", "10.5");
        double result = jedis.incrByFloat("counter", 0.1);
        assertEquals(10.6, result, 0.0001);
    }

    // --- APPEND / STRLEN ---

    @Test
    void testAppendAndStrlen() {
        assertEquals(5L, jedis.append("appkey", "hello"));
        assertEquals(11L, jedis.append("appkey", " world"));
        assertEquals("hello world", jedis.get("appkey"));
        assertEquals(11L, jedis.strlen("appkey"));
    }

    @Test
    void testStrlenMissingKey() {
        assertEquals(0L, jedis.strlen("missing_strlen_key"));
    }

    // --- SETNX ---

    @Test
    void testSetnx() {
        assertEquals(1L, jedis.setnx("setnxkey", "first"));
        assertEquals(0L, jedis.setnx("setnxkey", "second"));
        assertEquals("first", jedis.get("setnxkey"));
    }

    // --- GETSET ---

    @Test
    void testGetset() {
        assertNull(jedis.getSet("getsetkey", "v1"));
        assertEquals("v1", jedis.getSet("getsetkey", "v2"));
        assertEquals("v2", jedis.get("getsetkey"));
    }

    // --- GETDEL ---

    @Test
    void testGetdel() {
        jedis.set("getdelkey", "toDelete");
        assertEquals("toDelete", jedis.getDel("getdelkey"));
        assertNull(jedis.get("getdelkey"));
    }

    @Test
    void testGetdelMissingKey() {
        assertNull(jedis.getDel("missing_getdel_key"));
    }
}
