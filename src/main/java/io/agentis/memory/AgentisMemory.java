package io.agentis.memory;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespServer;
import io.agentis.memory.store.AofReader;
import io.avaje.inject.BeanScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentisMemory {
    private static final Logger log = LoggerFactory.getLogger(AgentisMemory.class);

    static void main(String[] args) {
        ServerConfig config = ServerConfig.parse(args);

        try (BeanScope scope = BeanScope.builder()
                .bean(ServerConfig.class, config)
                .build()) {

            // Recovery from AOF before starting the server
            AofReader aofReader = scope.get(AofReader.class);
            aofReader.recover();

            RespServer server = scope.get(RespServer.class);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down Agentis Memory...");
                server.shutdown();
            }));

            try {
                server.start();
                log.info("Server started. Press Ctrl+C to stop.");
                server.waitForShutdown();
            } catch (InterruptedException e) {
                log.error("Server interrupted", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Failed to start server", e);
            }
        }
    }
}
