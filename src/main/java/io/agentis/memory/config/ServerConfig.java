package io.agentis.memory.config;

import java.nio.file.Path;

public class ServerConfig {

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
        // TODO: parse CLI args and optional agentis-memory.conf
        return config;
    }
}
