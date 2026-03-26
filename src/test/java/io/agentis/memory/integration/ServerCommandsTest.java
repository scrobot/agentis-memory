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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class ServerCommandsTest {
    private static final Logger log = LoggerFactory.getLogger(ServerCommandsTest.class);

    private static BeanScope scope;
    private static RespServer server;
    private static Jedis jedis;
    private static final int PORT = 6396; // distinct port for this test
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
    void flushBefore() {
        jedis.flushDB();
    }

    @Test
    void testSelect0() {
        assertEquals("OK", jedis.select(0));
    }

    @Test
    void testSelectNonZeroReturnsError() {
        assertThrows(Exception.class, () -> jedis.select(1));
    }

    @Test
    void testFlushDb() {
        jedis.set("key1", "val1");
        jedis.set("key2", "val2");
        assertEquals(2L, jedis.dbSize());

        assertEquals("OK", jedis.flushDB());
        assertEquals(0L, jedis.dbSize());
    }

    @Test
    void testFlushAll() {
        jedis.set("key1", "val1");
        jedis.set("key2", "val2");
        assertEquals(2L, jedis.dbSize());

        assertEquals("OK", jedis.flushAll());
        assertEquals(0L, jedis.dbSize());
    }

    @Test
    void testEcho() {
        assertEquals("hello world", jedis.echo("hello world"));
    }

    @Test
    void testEchoEmpty() {
        assertEquals("", jedis.echo(""));
    }

    @Test
    void testTime() {
        List<String> time = jedis.time();
        assertNotNull(time);
        assertEquals(2, time.size());

        long seconds = Long.parseLong(time.get(0));
        long micros = Long.parseLong(time.get(1));

        long nowSeconds = System.currentTimeMillis() / 1000;
        assertTrue(Math.abs(seconds - nowSeconds) <= 2, "TIME seconds should be close to current time");
        assertTrue(micros >= 0 && micros < 1_000_000, "TIME microseconds should be in [0, 1000000)");
    }
}
