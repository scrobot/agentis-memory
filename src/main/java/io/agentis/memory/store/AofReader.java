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

        long fileSize;
        try {
            fileSize = Files.size(aofPath);
        } catch (IOException e) {
            fileSize = -1;
        }
        log.info("AOF recovery starting: file={} size={}KB", aofPath.getFileName(), fileSize > 0 ? fileSize / 1024 : "?");
        long startTime = System.currentTimeMillis();
        int count = 0;
        int errors = 0;

        try {
            byte[] bytes = Files.readAllBytes(aofPath);
            RespParser parser = new RespParser(new ByteArrayInputStream(bytes));

            while (true) {
                try {
                    RespMessage msg = parser.readMessage();
                    if (msg == null) break;

                    RespMessage result = router.dispatch(null, msg);
                    count++;
                    if (result instanceof RespMessage.Error) errors++;
                } catch (Exception e) {
                    log.error("AOF replay halted at command #{}: {}", count, e.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            log.error("Failed to read AOF file: {}", e.getMessage());
        }

        long duration = System.currentTimeMillis() - startTime;
        long rate = duration > 0 ? (count * 1000L / duration) : 0;
        if (errors > 0) {
            log.warn("AOF recovery done: {} commands in {}ms ({} cmd/s), {} errors", count, duration, rate, errors);
        } else {
            log.info("AOF recovery done: {} commands in {}ms ({} cmd/s)", count, duration, rate);
        }
    }
}
