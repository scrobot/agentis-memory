package io.agentis.memory.integration;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespServer;
import io.avaje.inject.BeanScope;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BasicCommandsTest {
    private static final Logger log = LoggerFactory.getLogger(BasicCommandsTest.class);

    private static BeanScope scope;
    private static RespServer server;
    private static Jedis jedis;
    private static final int PORT = 6398; // Use different port for test
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

        // Wait for server to start using Awaitility
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

    @Test
    void testPing() {
        assertEquals("PONG", jedis.ping());
    }

    @Test
    void testSetGet() {
        assertEquals("OK", jedis.set("foo", "bar"));
        assertEquals("bar", jedis.get("foo"));
    }

    @Test
    void testGetNonExistent() {
        assertNull(jedis.get("nonexistent"));
    }

    @Test
    void testSetEx() {
        jedis.setex("temp", 1, "value");
        assertEquals("value", jedis.get("temp"));

        await().atMost(Duration.ofSeconds(2))
                .pollDelay(Duration.ofMillis(1100))
                .until(() -> jedis.get("temp") == null);
    }
}
