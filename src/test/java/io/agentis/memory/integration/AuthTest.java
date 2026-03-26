package io.agentis.memory.integration;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespServer;
import io.avaje.inject.BeanScope;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisDataException;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class AuthTest {

    private static final Logger log = LoggerFactory.getLogger(AuthTest.class);
    private static final int PORT = 6406;
    private static final String PASSWORD = "testpassword";

    private static BeanScope scope;
    private static RespServer server;
    private static ExecutorService executor;

    @BeforeAll
    static void setup() {
        ServerConfig config = new ServerConfig();
        config.port = PORT;
        config.bind = "127.0.0.1";
        config.requirepass = PASSWORD;
        scope = BeanScope.builder().bean(ServerConfig.class, config).build();
        server = scope.get(RespServer.class);
        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try { server.start(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (Exception e) { log.error("Server error", e); }
        });

        // Wait for server to start using a pre-authenticated connection
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    try (Jedis probe = new Jedis("127.0.0.1", PORT)) {
                        probe.auth(PASSWORD);
                        return "PONG".equals(probe.ping());
                    } catch (Exception e) {
                        return false;
                    }
                });
    }

    @AfterAll
    static void tearDown() {
        if (server != null) server.shutdown();
        if (executor != null) executor.shutdownNow();
        if (scope != null) scope.close();
    }

    @Test
    void commandWithoutAuthIsRejected() {
        try (Jedis jedis = new Jedis("127.0.0.1", PORT)) {
            JedisDataException ex = assertThrows(JedisDataException.class, () -> jedis.ping());
            assertTrue(ex.getMessage().contains("NOAUTH"), "Expected NOAUTH error, got: " + ex.getMessage());
        }
    }

    @Test
    void commandWithWrongPasswordIsRejected() {
        try (Jedis jedis = new Jedis("127.0.0.1", PORT)) {
            JedisDataException ex = assertThrows(JedisDataException.class, () -> jedis.auth("wrongpassword"));
            assertTrue(ex.getMessage().contains("WRONGPASS") || ex.getMessage().contains("invalid"),
                    "Expected WRONGPASS error, got: " + ex.getMessage());
        }
    }

    @Test
    void commandWithCorrectPasswordSucceeds() {
        try (Jedis jedis = new Jedis("127.0.0.1", PORT)) {
            assertEquals("OK", jedis.auth(PASSWORD));
            assertEquals("PONG", jedis.ping());
        }
    }

    @Test
    void setAndGetAfterAuth() {
        try (Jedis jedis = new Jedis("127.0.0.1", PORT)) {
            jedis.auth(PASSWORD);
            assertEquals("OK", jedis.set("auth-test-key", "hello"));
            assertEquals("hello", jedis.get("auth-test-key"));
        }
    }
}
