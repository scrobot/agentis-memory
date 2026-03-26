package io.agentis.memory.store;

import io.agentis.memory.command.CommandRouter;
import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.resp.RespParser;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class AofReader {
    private static final Logger log = LoggerFactory.getLogger(AofReader.class);

    private final ServerConfig config;
    private final CommandRouter router;

    @Inject
    public AofReader(ServerConfig config, CommandRouter router) {
        this.config = config;
        this.router = router;
    }

    public void recover() {
        if (!config.aofEnabled) return;

        Path aofPath = config.dataDir.resolve("appendonly.aof");
        if (!Files.exists(aofPath)) {
            log.info("AOF file not found at {}, skipping recovery", aofPath);
            return;
        }

        log.info("Starting AOF recovery from {}...", aofPath);
        long startTime = System.currentTimeMillis();
        int count = 0;

        try {
            byte[] bytes = Files.readAllBytes(aofPath);
            RespParser parser = new RespParser(new ByteArrayInputStream(bytes));

            while (true) {
                try {
                    RespMessage msg = parser.readMessage();
                    if (msg == null) break;

                    router.dispatch(null, msg);
                    count++;
                } catch (Exception e) {
                    log.error("Failed to decode AOF message after {} commands", count, e);
                    break;
                }
            }
        } catch (IOException e) {
            log.error("Failed to read AOF file", e);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("AOF recovery finished: replayed {} commands in {}ms", count, duration);
    }
}
