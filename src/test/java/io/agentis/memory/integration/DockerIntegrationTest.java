package io.agentis.memory.integration;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that build the application Docker image from the project
 * Dockerfile and run commands against it via Jedis (Redis-compatible client).
 *
 * These tests are tagged "docker" and run separately via:
 *   ./gradlew integrationTest
 */
@Tag("docker")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DockerIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DockerIntegrationTest.class);
    private static final int CONTAINER_PORT = 6399;

    @SuppressWarnings("resource")
    private static final GenericContainer<?> container = new GenericContainer<>(
            new ImageFromDockerfile("agentis-memory-test", false)
                    .withDockerfile(Path.of(projectRoot(), "Dockerfile"))
                    .withFileFromPath(".", Path.of(projectRoot()))
    )
            .withExposedPorts(CONTAINER_PORT)
            .waitingFor(
                    Wait.forLogMessage(".*Agentis Memory listening.*", 1)
                            .withStartupTimeout(Duration.ofMinutes(10))
            )
            .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("agentis-memory"));

    // JedisPool is thread-safe; each test borrows a connection and returns it.
    private static JedisPool jedisPool;

    @BeforeAll
    static void startContainer() {
        container.start();
        jedisPool = new JedisPool(container.getHost(), container.getMappedPort(CONTAINER_PORT));

        // Wait until PING responds (covers cases where the log message arrives before the
        // TCP listener is fully ready)
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(300))
                .until(() -> {
                    try (Jedis jedis = jedisPool.getResource()) {
                        return "PONG".equals(jedis.ping());
                    } catch (Exception e) {
                        return false;
                    }
                });
    }

    @AfterAll
    static void stopContainer() {
        if (jedisPool != null) jedisPool.close();
        if (container.isRunning()) container.stop();
    }

    // ─── PING ─────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void ping() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals("PONG", jedis.ping());
        }
    }

    // ─── SET / GET ─────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    void setAndGet() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals("OK", jedis.set("docker:hello", "world"));
            assertEquals("world", jedis.get("docker:hello"));
        }
    }

    @Test
    @Order(3)
    void getMissing() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertNull(jedis.get("docker:does-not-exist"));
        }
    }

    @Test
    @Order(4)
    void overwrite() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("docker:overwrite", "first");
            jedis.set("docker:overwrite", "second");
            assertEquals("second", jedis.get("docker:overwrite"));
        }
    }

    // ─── EXPIRY ───────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    void setexExpiresEntry() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex("docker:ttl", 1, "transient");
            assertEquals("transient", jedis.get("docker:ttl"));
        }

        await().atMost(Duration.ofSeconds(3))
                .pollDelay(Duration.ofMillis(1200))
                .until(() -> {
                    try (Jedis jedis = jedisPool.getResource()) {
                        return jedis.get("docker:ttl") == null;
                    }
                });
    }

    @Test
    @Order(6)
    void ttlReturnsPositiveValue() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex("docker:ttl2", 60, "persistent");
            long ttl = jedis.ttl("docker:ttl2");
            assertTrue(ttl > 0 && ttl <= 60, "TTL should be between 1 and 60, got: " + ttl);
        }
    }

    // ─── EXISTS / DEL ─────────────────────────────────────────────────────────

    @Test
    @Order(7)
    void existsAndDel() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("docker:exists", "yes");
            assertEquals(1L, jedis.exists("docker:exists"));
            jedis.del("docker:exists");
            assertEquals(0L, jedis.exists("docker:exists"));
        }
    }

    // ─── DBSIZE ───────────────────────────────────────────────────────────────

    @Test
    @Order(8)
    void dbsizeIncreasesAfterSet() {
        try (Jedis jedis = jedisPool.getResource()) {
            long before = jedis.dbSize();
            jedis.set("docker:dbsize-probe", "x");
            long after = jedis.dbSize();
            assertTrue(after >= before + 1, "DBSIZE should increase after SET");
        }
    }

    // ─── CONCURRENT WRITES ───────────────────────────────────────────────────

    @Test
    @Order(9)
    void concurrentSetsDoNotLoseData() throws InterruptedException {
        int threads = 10;
        int keysPerThread = 20;
        Thread[] workers = new Thread[threads];

        for (int t = 0; t < threads; t++) {
            int tid = t;
            workers[t] = new Thread(() -> {
                // Each thread borrows its own connection from the pool
                try (Jedis jedis = jedisPool.getResource()) {
                    for (int k = 0; k < keysPerThread; k++) {
                        String key = "docker:concurrent:" + tid + ":" + k;
                        jedis.set(key, "v" + k);
                    }
                }
            });
        }

        for (Thread w : workers) w.start();
        for (Thread w : workers) w.join(5_000);

        // Spot-check a few keys from every thread
        try (Jedis jedis = jedisPool.getResource()) {
            for (int t = 0; t < threads; t++) {
                for (int k = 0; k < keysPerThread; k += 5) {
                    String key = "docker:concurrent:" + t + ":" + k;
                    String val = jedis.get(key);
                    assertEquals("v" + k, val, "Missing or wrong value for " + key);
                }
            }
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    /**
     * Resolves the project root (the directory containing the Dockerfile) from
     * the working directory used by Gradle. Fails fast with a clear error if the
     * Dockerfile cannot be located, rather than producing a cryptic downstream error.
     */
    private static String projectRoot() {
        String cwd = System.getProperty("user.dir");

        File dockerfile = new File(cwd, "Dockerfile");
        if (dockerfile.exists()) {
            return cwd;
        }

        // Fallback: one level up (in case of subproject layout)
        File fallbackDockerfile = new File(new File(cwd).getParent(), "Dockerfile");
        if (fallbackDockerfile.exists()) {
            return new File(cwd).getParent();
        }

        throw new IllegalStateException(
                "Cannot locate Dockerfile. Searched:\n  " + dockerfile.getAbsolutePath() +
                "\n  " + fallbackDockerfile.getAbsolutePath() +
                "\nEnsure tests are run from the project root (e.g. ./gradlew integrationTest)."
        );
    }
}
