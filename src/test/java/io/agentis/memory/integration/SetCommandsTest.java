package io.agentis.memory.integration;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespServer;
import io.avaje.inject.BeanScope;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class SetCommandsTest {
    private static final Logger log = LoggerFactory.getLogger(SetCommandsTest.class);

    private static BeanScope scope;
    private static RespServer server;
    private static Jedis jedis;
    private static final int PORT = 6396;
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
    void flushDb() {
        // Clean up known test keys before each test
        jedis.del("s1", "s2", "s3", "str");
    }

    @Test
    void saddAndSmembers() {
        assertEquals(3L, jedis.sadd("s1", "a", "b", "c"));
        assertEquals(Set.of("a", "b", "c"), jedis.smembers("s1"));
    }

    @Test
    void saddIgnoresDuplicates() {
        jedis.sadd("s1", "a", "b");
        assertEquals(1L, jedis.sadd("s1", "b", "c"));
        assertEquals(3L, jedis.scard("s1"));
    }

    @Test
    void sremRemovesMembers() {
        jedis.sadd("s1", "a", "b", "c");
        assertEquals(2L, jedis.srem("s1", "a", "b"));
        assertEquals(Set.of("c"), jedis.smembers("s1"));
    }

    @Test
    void scardReturnsSize() {
        jedis.sadd("s1", "x", "y");
        assertEquals(2L, jedis.scard("s1"));
    }

    @Test
    void sismember() {
        jedis.sadd("s1", "hello");
        assertTrue(jedis.sismember("s1", "hello"));
        assertFalse(jedis.sismember("s1", "world"));
    }

    @Test
    void typeReturnsSet() {
        jedis.sadd("s1", "member");
        assertEquals("set", jedis.type("s1"));
    }

    @Test
    void sinterReturnsIntersection() {
        jedis.sadd("s1", "a", "b", "c");
        jedis.sadd("s2", "b", "c", "d");
        assertEquals(Set.of("b", "c"), jedis.sinter("s1", "s2"));
    }

    @Test
    void sunionReturnsUnion() {
        jedis.sadd("s1", "a", "b");
        jedis.sadd("s2", "b", "c");
        assertEquals(Set.of("a", "b", "c"), jedis.sunion("s1", "s2"));
    }

    @Test
    void sdiffReturnsDifference() {
        jedis.sadd("s1", "a", "b", "c");
        jedis.sadd("s2", "b", "c");
        assertEquals(Set.of("a"), jedis.sdiff("s1", "s2"));
    }
}
