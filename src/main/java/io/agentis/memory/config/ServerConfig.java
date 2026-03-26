package io.agentis.memory.config;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

@Singleton
public class ServerConfig {
    private static final Logger log = LoggerFactory.getLogger(ServerConfig.class);

    public int port = 6399;
    public String bind = "127.0.0.1";
    public String requirepass = null;
    public Path dataDir = Path.of("data");
    public long maxMemoryBytes = 256 * 1024 * 1024L;
    public long maxValueSizeBytes = 1024 * 1024L;
    public int maxChunksPerKey = 100;
    public String evictionPolicy = "volatile-lru";
    public boolean aofEnabled = true;
    public String aofFsync = "everysec";
    public int snapshotInterval = 300;
    public int snapshotAfterChanges = 1000;
    public int embeddingThreads = 2;
    public Path modelPath = null;
    public int hnswM = 16;
    public int hnswEfConstruction = 100;
    public String logLevel = "info";

    public static ServerConfig parse(String[] args) {
        ServerConfig config = new ServerConfig();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (i + 1 < args.length) {
                switch (arg) {
                    case "--port" -> config.port = Integer.parseInt(args[++i]);
                    case "--bind" -> config.bind = args[++i];
                    case "--requirepass" -> config.requirepass = args[++i];
                    case "--data-dir" -> config.dataDir = Path.of(args[++i]);
                    case "--max-memory" -> config.maxMemoryBytes = parseMemory(args[++i]);
                    case "--max-value-size" -> config.maxValueSizeBytes = parseMemory(args[++i]);
                    case "--max-chunks-per-key" -> config.maxChunksPerKey = Integer.parseInt(args[++i]);
                    case "--eviction-policy" -> config.evictionPolicy = args[++i];
                    case "--aof-fsync" -> config.aofFsync = args[++i];
                    case "--snapshot-interval" -> config.snapshotInterval = Integer.parseInt(args[++i]);
                    case "--snapshot-after-changes" -> config.snapshotAfterChanges = Integer.parseInt(args[++i]);
                    case "--embedding-threads" -> config.embeddingThreads = Integer.parseInt(args[++i]);
                    case "--model-path" -> config.modelPath = Path.of(args[++i]);
                    case "--hnsw-m" -> config.hnswM = Integer.parseInt(args[++i]);
                    case "--hnsw-ef-construction" -> config.hnswEfConstruction = Integer.parseInt(args[++i]);
                    case "--log-level" -> config.logLevel = args[++i];
                }
            }
            // Boolean flags (no value needed)
            switch (arg) {
                case "--aof-enabled" -> config.aofEnabled = true;
                case "--no-aof" -> config.aofEnabled = false;
            }
        }
        config.logConfiguration();
        return config;
    }

    public void logConfiguration() {
        log.info("Configuration:");
        log.info("  bind={}:{}", bind, port);
        log.info("  requirepass={}", requirepass != null ? "****" : "(none)");
        log.info("  data-dir={}", dataDir);
        log.info("  max-memory={}mb", maxMemoryBytes / (1024 * 1024));
        log.info("  max-value-size={}kb", maxValueSizeBytes / 1024);
        log.info("  max-chunks-per-key={}", maxChunksPerKey);
        log.info("  aof-enabled={}, aof-fsync={}", aofEnabled, aofFsync);
        log.info("  snapshot-interval={}s, snapshot-after-changes={}", snapshotInterval, snapshotAfterChanges);
        log.info("  embedding-threads={}, model-path={}", embeddingThreads, modelPath != null ? modelPath : "(auto)");
        log.info("  hnsw-m={}, hnsw-ef-construction={}", hnswM, hnswEfConstruction);
    }

    private static long parseMemory(String value) {
        value = value.trim().toLowerCase();
        if (value.endsWith("gb")) return Long.parseLong(value.replace("gb", "").trim()) * 1024 * 1024 * 1024;
        if (value.endsWith("mb")) return Long.parseLong(value.replace("mb", "").trim()) * 1024 * 1024;
        if (value.endsWith("kb")) return Long.parseLong(value.replace("kb", "").trim()) * 1024;
        return Long.parseLong(value);
    }
}
