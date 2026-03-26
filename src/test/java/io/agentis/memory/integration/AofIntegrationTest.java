package io.agentis.memory.integration;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * AOF persistence test. Needs its own containers (stop/restart cycle),
 * but reuses the pre-built image from AbstractIntegrationTest.
 */
@Tag("docker")
class AofIntegrationTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(AofIntegrationTest.class);
    private static Path tempDir;

    @BeforeAll
    static void setUpAll() throws IOException {
        tempDir = Files.createTempDirectory("agentis-aof-test");
    }

    @Test
    void testAofPersistenceAndRecovery() throws Exception {
        log.info("Starting testAofPersistenceAndRecovery in Docker");

        // 1. Start container, write data, stop container
        try (GenericContainer<?> aofContainer = createAofContainer()) {
            aofContainer.start();
            try (JedisPool pool = new JedisPool(aofContainer.getHost(), aofContainer.getMappedPort(CONTAINER_PORT))) {
                waitForServer(pool);
                try (Jedis jedis = pool.getResource()) {
                    jedis.set("key1", "value1");
                    jedis.set("key2", "value2");
                    jedis.del("key1");
                    log.info("AOF test: data written");
                }
            }
            aofContainer.stop();
        }

        log.info("Container stopped. Restarting to verify recovery...");

        // 2. Start new container with same volume and verify data
        try (GenericContainer<?> aofContainer = createAofContainer()) {
            aofContainer.start();
            try (JedisPool pool = new JedisPool(aofContainer.getHost(), aofContainer.getMappedPort(CONTAINER_PORT))) {
                waitForServer(pool);
                try (Jedis jedis = pool.getResource()) {
                    assertEquals("value2", jedis.get("key2"));
                    assertNull(jedis.get("key1"));
                    log.info("AOF test: recovery verified");
                }
            }
        }
    }

    /**
     * Creates a container from the pre-built image (no ImageFromDockerfile).
     * Uses a shared temp dir as /data volume for AOF persistence across restarts.
     */
    private GenericContainer<?> createAofContainer() {
        return new GenericContainer<>(TestInfrastructure.IMAGE_NAME)
                .withExposedPorts(CONTAINER_PORT)
                .withFileSystemBind(tempDir.toAbsolutePath().toString(), "/data", BindMode.READ_WRITE)
                .withCommand("bin/agentis-memory", "--port", "6399", "--bind", "0.0.0.0",
                        "--data-dir", "/data", "--aof-enabled", "true", "--aof-fsync", "always")
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));
    }
}
