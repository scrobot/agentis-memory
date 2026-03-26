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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class MemCommandsTest {
    private static BeanScope scope;
    private static RespServer server;
    private static ExecutorService executor;
    private static Jedis jedis;
    private static final int PORT = 6400;

    @BeforeAll
    static void setup() {
        ServerConfig config = new ServerConfig();
        config.port = PORT;

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

        jedis = new Jedis("127.0.0.1", PORT);
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
    void testMemSaveAndQuery() {
        // 1. MEMSAVE
        Object responseObj = jedis.sendCommand(() -> "MEMSAVE".getBytes(), "doc:1", "The capital of France is Paris.");
        String response = responseObj instanceof byte[] ? new String((byte[]) responseObj) : String.valueOf(responseObj);
        assertEquals("OK", response);

        // 2. Wait for indexing
        await().atMost(Duration.ofSeconds(15))
                .until(() -> {
                    List<Object> status = (List<Object>) jedis.sendCommand(() -> "MEMSTATUS".getBytes(), "doc:1");
                    return "indexed".equals(new String((byte[]) status.get(0)));
                });

        // 3. MEMQUERY
        List<List<byte[]>> results = (List<List<byte[]>>) jedis.sendCommand(() -> "MEMQUERY".getBytes(), "doc", "What is the capital of France?", "1");
        assertFalse(results.isEmpty());
        assertEquals("doc:1", new String(results.get(0).get(0)));
        assertTrue(new String(results.get(0).get(1)).contains("Paris"));
        
        double score = Double.parseDouble(new String(results.get(0).get(2)));
        assertTrue(score > 0.5, "Score should be high for relevant query, but was " + score);

        // 4. MEMDEL
        Long deleted = (Long) jedis.sendCommand(() -> "MEMDEL".getBytes(), "doc:1");
        assertEquals(1L, deleted);

        // 5. Verify deleted
        List<List<byte[]>> emptyResults = (List<List<byte[]>>) jedis.sendCommand(() -> "MEMQUERY".getBytes(), "doc", "Paris", "1");
        assertTrue(emptyResults.isEmpty());
    }
    
    @Test
    void testMemQueryAll() {
        jedis.sendCommand(() -> "MEMSAVE".getBytes(), "ns1:a", "Apple is a fruit.");
        jedis.sendCommand(() -> "MEMSAVE".getBytes(), "ns2:b", "Banana is a fruit.");
        
        await().atMost(Duration.ofSeconds(15))
                .until(() -> {
                    List<Object> s1 = (List<Object>) jedis.sendCommand(() -> "MEMSTATUS".getBytes(), "ns1:a");
                    List<Object> s2 = (List<Object>) jedis.sendCommand(() -> "MEMSTATUS".getBytes(), "ns2:b");
                    return "indexed".equals(new String((byte[]) s1.get(0))) && "indexed".equals(new String((byte[]) s2.get(0)));
                });
        
        // Query specific namespace
        List<List<byte[]>> res1 = (List<List<byte[]>>) jedis.sendCommand(() -> "MEMQUERY".getBytes(), "ns1", "fruit", "10");
        assertEquals(1, res1.size());
        assertEquals("ns1:a", new String(res1.get(0).get(0)));
        
        // Query ALL namespaces
        List<List<byte[]>> resAll = (List<List<byte[]>>) jedis.sendCommand(() -> "MEMQUERY".getBytes(), "ALL", "fruit", "10");
        assertEquals(2, resAll.size());
    }
}
