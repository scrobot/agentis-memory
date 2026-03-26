package io.agentis.memory;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespServer;
import io.avaje.inject.BeanScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentisMemory {
    private static final Logger log = LoggerFactory.getLogger(AgentisMemory.class);

    public static void main(String[] args) {
        ServerConfig config = ServerConfig.parse(args);

        try (BeanScope scope = BeanScope.builder()
                .bean(ServerConfig.class, config)
                .build()) {

            RespServer server = scope.get(RespServer.class);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down Agentis Memory...");
                server.shutdown();
            }));

            try {
                server.start();
            } catch (InterruptedException e) {
                log.error("Server interrupted", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Failed to start server", e);
            }
        }
    }
}
