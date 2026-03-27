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

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("docker")
class SnapshotIntegrationTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(SnapshotIntegrationTest.class);
    private static Path tempDir;

    @BeforeAll
    static void setUpAll() throws IOException {
        tempDir = Files.createTempDirectory("agentis-snapshot-test");
    }

    @Test
    void testSnapshotPersistenceAndRecovery() throws Exception {
        log.info("Starting testSnapshotPersistenceAndRecovery in Docker");

        // 1. Start container, write data, trigger BGSAVE, stop container
        try (GenericContainer<?> snapshotContainer = createSnapshotContainer()) {
            snapshotContainer.start();
            try (JedisPool pool = new JedisPool(snapshotContainer.getHost(), snapshotContainer.getMappedPort(CONTAINER_PORT))) {
                waitForServer(pool);
                try (Jedis jedis = pool.getResource()) {
                    jedis.set("snapkey1", "snapvalue1");
                    jedis.hset("snaphash", "f1", "v1");
                    
                    String res = jedis.sendCommand(() -> "BGSAVE".getBytes()).toString();
                    log.info("BGSAVE response: {}", res);
                    
                    // Wait a bit for snapshot to finish
                    Thread.sleep(2000);
                }
            }
            snapshotContainer.stop();
        }

        log.info("Container stopped. Restarting to verify snapshot recovery...");

        // 2. Start new container with same volume and verify data
        try (GenericContainer<?> snapshotContainer = createSnapshotContainer()) {
            snapshotContainer.start();
            try (JedisPool pool = new JedisPool(snapshotContainer.getHost(), snapshotContainer.getMappedPort(CONTAINER_PORT))) {
                waitForServer(pool);
                try (Jedis jedis = pool.getResource()) {
                    assertEquals("snapvalue1", jedis.get("snapkey1"));
                    assertEquals("v1", jedis.hget("snaphash", "f1"));
                    log.info("Snapshot test: recovery verified");
                }
            }
        }
    }

    private GenericContainer<?> createSnapshotContainer() {
        return new GenericContainer<>(TestInfrastructure.IMAGE_NAME)
                .withExposedPorts(CONTAINER_PORT)
                .withFileSystemBind(tempDir.toAbsolutePath().toString(), "/data", BindMode.READ_WRITE)
                .withCommand("./agentis-memory", "--port", "6399", "--bind", "0.0.0.0",
                        "--data-dir", "/data", "--no-aof")
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));
    }
}
