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

/**
 * Integration test for list commands (LPUSH, RPUSH, LPOP, LRANGE, TYPE).
 * Starts a real Agentis Memory server and connects via Jedis.
 */
class ListCommandsIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(ListCommandsIntegrationTest.class);
    private static final int PORT = 6396;

    private static BeanScope scope;
    private static RespServer server;
    private static Jedis jedis;
    private static ExecutorService executor;

    @BeforeAll
    static void startServer() {
        ServerConfig config = new ServerConfig();
        config.port = PORT;
        config.bind = "127.0.0.1";

        scope = BeanScope.builder().bean(ServerConfig.class, config).build();
        server = scope.get(RespServer.class);

        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                server.start();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Server failed", e);
            }
        });

        jedis = new Jedis("127.0.0.1", PORT);
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    try { return "PONG".equals(jedis.ping()); } catch (Exception e) { return false; }
                });
    }

    @AfterAll
    static void stopServer() {
        if (jedis != null) jedis.close();
        if (server != null) server.shutdown();
        if (executor != null) executor.shutdownNow();
        if (scope != null) scope.close();
    }

    @BeforeEach
    void flushDb() {
        // Clean slate per test using DEL on any keys we might use
        jedis.del("mylist", "other");
    }

    // -------------------------------------------------------------------------
    // LPUSH / RPUSH
    // -------------------------------------------------------------------------

    @Test
    void lpush_returnsLengthAndPrependsElements() {
        assertEquals(1L, jedis.lpush("mylist", "a"));
        assertEquals(2L, jedis.lpush("mylist", "b"));
        // List is [b, a] — LRANGE 0 -1
        List<String> all = jedis.lrange("mylist", 0, -1);
        assertEquals(List.of("b", "a"), all);
    }

    @Test
    void rpush_appendsElementsToTail() {
        jedis.rpush("mylist", "a", "b", "c");
        assertEquals(List.of("a", "b", "c"), jedis.lrange("mylist", 0, -1));
    }

    @Test
    void lpush_multipleElementsPrependsLeftToRight() {
        // LPUSH mylist a b c → [c, b, a]
        jedis.lpush("mylist", "a", "b", "c");
        assertEquals(List.of("c", "b", "a"), jedis.lrange("mylist", 0, -1));
    }

    // -------------------------------------------------------------------------
    // LPOP / RPOP
    // -------------------------------------------------------------------------

    @Test
    void lpop_returnsHeadElement() {
        jedis.rpush("mylist", "a", "b", "c");
        assertEquals("a", jedis.lpop("mylist"));
        assertEquals(List.of("b", "c"), jedis.lrange("mylist", 0, -1));
    }

    @Test
    void rpop_returnsTailElement() {
        jedis.rpush("mylist", "a", "b", "c");
        assertEquals("c", jedis.rpop("mylist"));
    }

    @Test
    void lpop_nilOnMissingKey() {
        assertNull(jedis.lpop("mylist"));
    }

    @Test
    void rpop_nilOnMissingKey() {
        assertNull(jedis.rpop("mylist"));
    }

    // -------------------------------------------------------------------------
    // LRANGE
    // -------------------------------------------------------------------------

    @Test
    void lrange_fullRange() {
        jedis.rpush("mylist", "x", "y", "z");
        assertEquals(List.of("x", "y", "z"), jedis.lrange("mylist", 0, -1));
    }

    @Test
    void lrange_emptyOnMissingKey() {
        assertTrue(jedis.lrange("mylist", 0, -1).isEmpty());
    }

    // -------------------------------------------------------------------------
    // TYPE
    // -------------------------------------------------------------------------

    @Test
    void typeReturnsListForListKey() {
        jedis.rpush("mylist", "v");
        assertEquals("list", jedis.type("mylist"));
    }

    @Test
    void typeReturnsNoneForMissingKey() {
        assertEquals("none", jedis.type("mylist"));
    }
}
