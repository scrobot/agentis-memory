package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.command.CommandRouter;
import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.ClientConnection;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.resp.RespServer;
import io.agentis.memory.store.AofWriter;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.SnapshotManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Singleton
public class InfoCommand implements CommandHandler {

    private static final long START_TIME_MS = System.currentTimeMillis();

    private final ServerConfig config;
    private final KvStore kvStore;
    private final AofWriter aofWriter;

    // Field injection to break circular dependency:
    // InfoCommand -> CommandRouter -> InfoCommand (via List<CommandHandler>)
    // InfoCommand -> RespServer -> CommandRouter -> InfoCommand
    @Inject CommandRouter router;
    @Inject RespServer respServer;
    @Inject SnapshotManager snapshotManager;

    // For instantaneous_ops_per_sec calculation
    private long lastSampleTime = System.currentTimeMillis();
    private long lastSampleOps = 0;
    private volatile long cachedOpsPerSec = 0;

    @Inject
    public InfoCommand(ServerConfig config, KvStore kvStore, AofWriter aofWriter) {
        this.config = config;
        this.kvStore = kvStore;
        this.aofWriter = aofWriter;
    }

    // Test-friendly constructor (no DI)
    public InfoCommand(ServerConfig config, KvStore kvStore, CommandRouter router,
                       RespServer respServer, SnapshotManager snapshotManager, AofWriter aofWriter) {
        this.config = config;
        this.kvStore = kvStore;
        this.router = router;
        this.respServer = respServer;
        this.snapshotManager = snapshotManager;
        this.aofWriter = aofWriter;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        String section = args.size() > 1 ? new String(args.get(1)).toLowerCase() : "all";
        StringBuilder sb = new StringBuilder(1024);

        boolean all = section.equals("all");

        if (all || section.equals("server")) {
            long uptimeSeconds = (System.currentTimeMillis() - START_TIME_MS) / 1000;
            sb.append("# Server\r\n");
            sb.append("redis_version:7.0.0\r\n");
            sb.append("redis_mode:standalone\r\n");
            sb.append("os:agentis-memory\r\n");
            sb.append("tcp_port:").append(config.port).append("\r\n");
            sb.append("uptime_in_seconds:").append(uptimeSeconds).append("\r\n");
            sb.append("uptime_in_days:").append(uptimeSeconds / 86400).append("\r\n");
            sb.append("hz:10\r\n");
            sb.append("executable:agentis-memory\r\n");
            sb.append("\r\n");
        }

        if (all || section.equals("memory")) {
            long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            sb.append("# Memory\r\n");
            sb.append("used_memory:").append(usedMemory).append("\r\n");
            sb.append("used_memory_human:").append(humanSize(usedMemory)).append("\r\n");
            sb.append("used_memory_peak:").append(usedMemory).append("\r\n");
            sb.append("used_memory_peak_human:").append(humanSize(usedMemory)).append("\r\n");
            sb.append("maxmemory:").append(config.maxMemoryBytes).append("\r\n");
            sb.append("mem_allocator:java\r\n");
            sb.append("\r\n");
        }

        if (all || section.equals("clients")) {
            sb.append("# Clients\r\n");
            sb.append("connected_clients:").append(respServer != null ? respServer.getActiveConnections() : 0).append("\r\n");
            sb.append("blocked_clients:0\r\n");
            sb.append("\r\n");
        }

        if (all || section.equals("stats")) {
            long totalCmds = router != null ? router.getCommandsProcessed() : 0;
            long totalConns = respServer != null ? respServer.getTotalConnectionsReceived() : 0;
            updateOpsPerSec(totalCmds);
            sb.append("# Stats\r\n");
            sb.append("total_commands_processed:").append(totalCmds).append("\r\n");
            sb.append("total_connections_received:").append(totalConns).append("\r\n");
            sb.append("instantaneous_ops_per_sec:").append(cachedOpsPerSec).append("\r\n");
            sb.append("\r\n");
        }

        if (all || section.equals("persistence")) {
            long lastSaveEpoch = snapshotManager != null ? snapshotManager.getLastSnapshotTime() / 1000 : 0;
            String bgsaveStatus = snapshotManager == null || snapshotManager.isLastSnapshotSuccessful() ? "ok" : "err";
            sb.append("# Persistence\r\n");
            sb.append("rdb_last_save_time:").append(lastSaveEpoch).append("\r\n");
            sb.append("rdb_last_bgsave_status:").append(bgsaveStatus).append("\r\n");
            sb.append("aof_enabled:").append(config.aofEnabled ? 1 : 0).append("\r\n");
            sb.append("aof_last_write_status:ok\r\n");
            sb.append("\r\n");
        }

        if (all || section.equals("keyspace")) {
            long keys = kvStore.size();
            sb.append("# Keyspace\r\n");
            if (keys > 0) {
                sb.append("db0:keys=").append(keys).append(",expires=0,avg_ttl=0\r\n");
            }
            sb.append("\r\n");
        }

        if (all || section.equals("replication")) {
            sb.append("# Replication\r\n");
            sb.append("role:master\r\n");
            sb.append("connected_slaves:0\r\n");
            sb.append("\r\n");
        }

        if (all || section.equals("cpu")) {
            sb.append("# CPU\r\n");
            sb.append("used_cpu_sys:0.000000\r\n");
            sb.append("used_cpu_user:0.000000\r\n");
            sb.append("\r\n");
        }

        if (all || section.equals("commandstats")) {
            Map<String, Long> counts = router != null ? router.getCommandCounts() : Map.of();
            if (!counts.isEmpty()) {
                sb.append("# Commandstats\r\n");
                counts.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .forEach(e -> sb.append("cmdstat_").append(e.getKey())
                                .append(":calls=").append(e.getValue())
                                .append(",usec=0,usec_per_call=0.00\r\n"));
                sb.append("\r\n");
            }
        }

        return new RespMessage.BulkString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void updateOpsPerSec(long currentOps) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastSampleTime;
        if (elapsed >= 1000) {
            cachedOpsPerSec = (currentOps - lastSampleOps) * 1000 / elapsed;
            lastSampleTime = now;
            lastSampleOps = currentOps;
        }
    }

    private static String humanSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return String.format("%.2fG", bytes / (1024.0 * 1024 * 1024));
        if (bytes >= 1024L * 1024) return String.format("%.2fM", bytes / (1024.0 * 1024));
        return String.format("%.2fK", bytes / 1024.0);
    }

    @Override
    public String name() {
        return "INFO";
    }
}
