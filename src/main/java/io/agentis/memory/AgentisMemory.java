package io.agentis.memory;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespServer;
import io.agentis.memory.store.AofReader;
import io.agentis.memory.store.AofWriter;
import io.agentis.memory.store.SnapshotManager;
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

            // Recovery: load snapshot, then replay AOF
            SnapshotManager snapshotManager = scope.get(SnapshotManager.class);
            try {
                snapshotManager.load();
            } catch (Exception e) {
                log.error("Failed to load snapshot", e);
            }

            AofReader aofReader = scope.get(AofReader.class);
            aofReader.recover();

            RespServer server = scope.get(RespServer.class);
            AofWriter aofWriter = scope.get(AofWriter.class);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down Agentis Memory...");
                server.shutdown();
                try {
                    aofWriter.close();
                    log.info("Taking final snapshot...");
                    snapshotManager.save();
                } catch (Exception e) {
                    log.error("Error during graceful shutdown", e);
                }
            }));

            try {
                server.start();
                log.info("Server started. Press Ctrl+C to stop.");
                server.waitForShutdown();
            } catch (Exception e) {
                log.error("Failed to start server", e);
            }
        }
    }
}
