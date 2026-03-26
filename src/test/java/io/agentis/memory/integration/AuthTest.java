package io.agentis.memory.integration;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisDataException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Auth test. Needs its own container with --requirepass.
 * Does NOT share the singleton container from AbstractIntegrationTest.
 */
@Tag("docker")
class AuthTest {
    private static final Logger log = LoggerFactory.getLogger(AuthTest.class);
    private static final int CONTAINER_PORT = 6399;
    private static final String PASSWORD = "testpassword";

    private static GenericContainer<?> authContainer;

    @BeforeAll
    static void startAuthContainer() {
        TestInfrastructure.buildImageOnce();
        authContainer = new GenericContainer<>(TestInfrastructure.IMAGE_NAME)
                .withExposedPorts(CONTAINER_PORT)
                .withCommand("bin/agentis-memory", "--port", "6399", "--bind", "0.0.0.0", "--requirepass", PASSWORD)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));
        authContainer.start();
        log.info("Auth container started at {}:{}", authContainer.getHost(), authContainer.getMappedPort(CONTAINER_PORT));
    }

    @AfterAll
    static void stopAuthContainer() {
        if (authContainer != null) authContainer.stop();
    }

    @Test
    void commandWithoutAuthIsRejected() {
        try (Jedis jedis = new Jedis(authContainer.getHost(), authContainer.getMappedPort(CONTAINER_PORT))) {
            JedisDataException ex = assertThrows(JedisDataException.class, () -> jedis.ping());
            assertTrue(ex.getMessage().contains("NOAUTH"), "Expected NOAUTH error, got: " + ex.getMessage());
        }
    }

    @Test
    void commandWithWrongPasswordIsRejected() {
        try (Jedis jedis = new Jedis(authContainer.getHost(), authContainer.getMappedPort(CONTAINER_PORT))) {
            JedisDataException ex = assertThrows(JedisDataException.class, () -> jedis.auth("wrongpassword"));
            assertTrue(ex.getMessage().contains("WRONGPASS") || ex.getMessage().contains("invalid"),
                    "Expected WRONGPASS error, got: " + ex.getMessage());
        }
    }

    @Test
    void commandWithCorrectPasswordSucceeds() {
        try (Jedis jedis = new Jedis(authContainer.getHost(), authContainer.getMappedPort(CONTAINER_PORT))) {
            assertEquals("OK", jedis.auth(PASSWORD));
            assertEquals("PONG", jedis.ping());
        }
    }

    @Test
    void setAndGetAfterAuth() {
        try (Jedis jedis = new Jedis(authContainer.getHost(), authContainer.getMappedPort(CONTAINER_PORT))) {
            jedis.auth(PASSWORD);
            assertEquals("OK", jedis.set("auth-test-key", "hello"));
            assertEquals("hello", jedis.get("auth-test-key"));
        }
    }
}
