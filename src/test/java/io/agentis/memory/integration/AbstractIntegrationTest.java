package io.agentis.memory.integration;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;

import static org.awaitility.Awaitility.await;

@Tag("docker")
public abstract class AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);
    protected static final int CONTAINER_PORT = 6399;

    protected static final GenericContainer<?> container;

    static {
        TestInfrastructure.buildImageOnce();

        container = new GenericContainer<>(TestInfrastructure.IMAGE_NAME)
                .withExposedPorts(CONTAINER_PORT)
                .waitingFor(
                        Wait.forListeningPort()
                                .withStartupTimeout(Duration.ofMinutes(5))
                );
    }

    protected static JedisPool jedisPool;

    @BeforeAll
    static void startContainer() {
        if (!container.isRunning()) {
            log.info("Starting singleton integration test container...");
            container.start();
            log.info("Container started at {}:{}", container.getHost(), container.getMappedPort(CONTAINER_PORT));
        }
        if (jedisPool == null) {
            jedisPool = new JedisPool(container.getHost(), container.getMappedPort(CONTAINER_PORT));
        }
        waitForServer(jedisPool);
    }

    // No @AfterAll container.stop() here — JVM shutdown hook or Testcontainers will handle it
    // This allows the container to be reused across multiple test classes

    @BeforeEach
    void flushBeforeTest() {
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.flushDB();
            }
        }
    }

    protected static void waitForServer(JedisPool pool) {
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(300))
                .until(() -> {
                    try (Jedis jedis = pool.getResource()) {
                        return "PONG".equals(jedis.ping());
                    } catch (Exception e) {
                        return false;
                    }
                });
    }

    protected static String projectRoot() {
        String cwd = System.getProperty("user.dir");
        File dockerfile = new File(cwd, "Dockerfile");
        if (dockerfile.exists()) return cwd;
        File fallbackDockerfile = new File(new File(cwd).getParent(), "Dockerfile");
        if (fallbackDockerfile.exists()) return new File(cwd).getParent();
        throw new IllegalStateException("Cannot locate Dockerfile at " + cwd);
    }
}
