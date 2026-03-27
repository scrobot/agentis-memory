package io.agentis.memory.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class TestInfrastructure {
    private static final Logger log = LoggerFactory.getLogger(TestInfrastructure.class);
    public static final String IMAGE_NAME = "agentis-memory:test";

    private static boolean built = false;

    public static synchronized void buildImageOnce() {
        if (built) return;
        
        String root = projectRoot();
        log.info("Building Docker image {} from {}...", IMAGE_NAME, root);
        try {
            var process = new ProcessBuilder("docker", "build", "-t", IMAGE_NAME, root)
                    .redirectErrorStream(true)
                    .start();
            // Capture output so we can log it on failure
            String output;
            try (var is = process.getInputStream()) {
                output = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Docker build output:\n{}", output);
                throw new RuntimeException("Docker build failed with exit code " + exitCode);
            }
            log.info("Docker image {} built successfully", IMAGE_NAME);
            built = true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Docker image", e);
        }
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
