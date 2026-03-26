package io.agentis.memory.integration;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespServer;
import io.avaje.inject.BeanScope;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class RedisInsightCompatTest {

    private static final Logger log = LoggerFactory.getLogger(RedisInsightCompatTest.class);
    private static final int PORT = 6397;

    private static BeanScope scope;
    private static RespServer server;
    private static Jedis jedis;
    private static ExecutorService executor;

    @BeforeAll
    static void setup() {
        ServerConfig config = new ServerConfig();
        config.port = PORT;
        config.bind = "127.0.0.1";
        scope = BeanScope.builder().bean(ServerConfig.class, config).build();
        server = scope.get(RespServer.class);
        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try { server.start(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (Exception e) { log.error("Server error", e); }
        });
        jedis = new Jedis("127.0.0.1", PORT);
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(200))
                .until(() -> { try { return "PONG".equals(jedis.ping()); } catch (Exception e) { return false; } });
    }

    @AfterAll
    static void tearDown() {
        if (jedis != null) jedis.close();
        if (server != null) server.shutdown();
        if (executor != null) executor.shutdownNow();
        if (scope != null) scope.close();
    }

    @Test
    void infoReturnsServerSection() {
        String info = jedis.info();
        assertNotNull(info);
        assertTrue(info.contains("redis_version"), "INFO should contain redis_version");
        assertTrue(info.contains("# Server"), "INFO should contain # Server section");
    }

    @Test
    void infoServerSection() {
        String info = jedis.info("server");
        assertTrue(info.contains("redis_version"));
        assertTrue(info.contains("tcp_port"));
    }

    @Test
    void dbSizeReturnsCount() {
        jedis.set("dbsize-test", "value");
        long size = jedis.dbSize();
        assertTrue(size >= 1);
    }

    @Test
    void configGetSave() {
        Map<String, String> result = jedis.configGet("save");
        assertFalse(result.isEmpty());
        assertTrue(result.containsKey("save"));
    }

    @Test
    void configGetAppendonly() {
        Map<String, String> result = jedis.configGet("appendonly");
        assertFalse(result.isEmpty());
        assertTrue(result.containsKey("appendonly"));
    }

    @Test
    void configGetUnknownReturnsEmpty() {
        Map<String, String> result = jedis.configGet("unknownparam123");
        assertTrue(result.isEmpty());
    }

    @Test
    void scanZeroWorks() {
        jedis.set("scan-key-1", "v1");
        jedis.set("scan-key-2", "v2");
        var result = jedis.scan("0");
        assertNotNull(result);
        assertNotNull(result.getResult());
    }

    @Test
    void typeReturnsStringForExistingKey() {
        jedis.set("type-test-key", "value");
        String type = jedis.type("type-test-key");
        assertEquals("string", type);
    }

    @Test
    void typeReturnsNoneForMissingKey() {
        String type = jedis.type("nonexistent-key-xyz");
        assertEquals("none", type);
    }

    @Test
    void clientSetName() {
        String result = jedis.clientSetname("test-client");
        assertEquals("OK", result);
    }

    @Test
    void commandReturnsNoError() {
        // Jedis sends COMMAND on connect; verify no exception is thrown by using a fresh connection
        try (Jedis fresh = new Jedis("127.0.0.1", PORT)) {
            assertNotNull(fresh.ping());
        }
    }

    @Test
    void delCommandWorks() {
        jedis.set("del-test", "value");
        assertEquals(1L, jedis.del("del-test"));
        assertNull(jedis.get("del-test"));
    }

    @Test
    void existsCommandWorks() {
        jedis.set("exists-test", "value");
        assertTrue(jedis.exists("exists-test"));
        jedis.del("exists-test");
        assertFalse(jedis.exists("exists-test"));
    }

    @Test
    void ttlAndExpireWork() {
        jedis.set("ttl-test", "value");
        jedis.expire("ttl-test", 100L);
        long ttl = jedis.ttl("ttl-test");
        assertTrue(ttl > 0 && ttl <= 100);
    }

    @Test
    void keysCommandWorks() {
        jedis.set("keys-prefix-a", "v1");
        jedis.set("keys-prefix-b", "v2");
        var keys = jedis.keys("keys-prefix-*");
        assertTrue(keys.size() >= 2);
    }
}
