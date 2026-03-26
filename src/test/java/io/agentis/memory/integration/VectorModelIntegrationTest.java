package io.agentis.memory.integration;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespServer;
import io.avaje.inject.BeanScope;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class VectorModelIntegrationTest {

    private static BeanScope scope;
    private static RespServer server;
    private static Jedis jedis;
    private static ExecutorService executor;
    private static final int TEST_PORT = 6397;

    @BeforeAll
    static void setup() {
        ServerConfig config = new ServerConfig();
        config.port = TEST_PORT;
        config.embeddingThreads = 4; // Use more threads for faster inference in tests

        scope = BeanScope.builder()
                .bean(ServerConfig.class, config)
                .build();

        server = scope.get(RespServer.class);
        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        jedis = new Jedis("127.0.0.1", TEST_PORT);
        // Wait for server to be ready
        await().atMost(Duration.ofSeconds(10))
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
    void testMemSaveAndQueryWithRealModel() {
        String key = "doc:ai";
        String content = "Artificial Intelligence is transforming the world. Machine learning models are becoming more powerful every day.";

        // 1. Save document
        Object responseObj = jedis.sendCommand(() -> "MEMSAVE".getBytes(), key.getBytes(), content.getBytes());
        String response = responseObj instanceof byte[] ? new String((byte[]) responseObj) : responseObj.toString();
        assertEquals("OK", response);

        // 2. Wait for indexing to complete
        await().atMost(Duration.ofSeconds(15))
                .until(() -> {
                    List<Object> status = (List<Object>) jedis.sendCommand(() -> "MEMSTATUS".getBytes(), key.getBytes());
                    return "indexed".equals(new String((byte[]) status.get(0)));
                });

        // 3. Query for similar content
        // Query about ML
        List<List<Object>> results = (List<List<Object>>) jedis.sendCommand(() -> "MEMQUERY".getBytes(), "doc".getBytes(), "machine learning and AI".getBytes(), "3".getBytes());
        
        assertFalse(results.isEmpty(), "Should find at least one result");
        
        // Result format: [[key, text, score], ...]
        List<Object> firstResult = results.get(0);
        assertEquals(key, new String((byte[]) firstResult.get(0)));
        assertTrue(new String((byte[]) firstResult.get(1)).contains("Artificial Intelligence"));
        
        double score = Double.parseDouble(new String((byte[]) firstResult.get(2)));
        assertTrue(score > 0.5, "Score should be high for relevant query, got: " + score);
    }

    @Test
    void testNamespaceFiltering() {
        Object r1 = jedis.sendCommand(() -> "MEMSAVE".getBytes(), "tech:java".getBytes(), "Java is a popular programming language.".getBytes());
        Object r2 = jedis.sendCommand(() -> "MEMSAVE".getBytes(), "food:pizza".getBytes(), "Pizza is a delicious Italian dish.".getBytes());
        
        assertEquals("OK", r1 instanceof byte[] ? new String((byte[]) r1) : r1.toString());
        assertEquals("OK", r2 instanceof byte[] ? new String((byte[]) r2) : r2.toString());

        // Wait for indexing
        await().atMost(Duration.ofSeconds(15))
                .until(() -> {
                    List<Object> s1 = (List<Object>) jedis.sendCommand(() -> "MEMSTATUS".getBytes(), "tech:java".getBytes());
                    List<Object> s2 = (List<Object>) jedis.sendCommand(() -> "MEMSTATUS".getBytes(), "food:pizza".getBytes());
                    return "indexed".equals(new String((byte[]) s1.get(0))) && "indexed".equals(new String((byte[]) s2.get(0)));
                });

        // Query tech namespace for something related to food
        List<List<Object>> techResults = (List<List<Object>>) jedis.sendCommand(() -> "MEMQUERY".getBytes(), "tech".getBytes(), "italian food".getBytes(), "5".getBytes());
        
        // Let's filter manually in the test to see if it helps identify the issue
        boolean foundPizza = techResults.stream().anyMatch(res -> new String((byte[]) res.get(0)).equals("food:pizza"));
        assertFalse(foundPizza, "Should not find pizza in tech namespace");

        // Query food namespace
        List<List<Object>> foodResults = (List<List<Object>>) jedis.sendCommand(() -> "MEMQUERY".getBytes(), "food".getBytes(), "italian food".getBytes(), "5".getBytes());
        assertFalse(foodResults.isEmpty());
        assertEquals("food:pizza", new String((byte[]) foodResults.get(0).get(0)));
    }

    @Test
    void testMemStatusForNonExistentKey() {
        try {
            jedis.sendCommand(() -> "MEMSTATUS".getBytes(), "nonexistent".getBytes());
            fail("Should throw error for non-existent key");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("ERR no such key"));
        }
    }
}
