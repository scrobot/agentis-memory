package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;

// INFO [section] — returns Redis-compatible INFO bulk string
@Singleton
public class InfoCommand implements CommandHandler {

    private static final long START_TIME_MS = System.currentTimeMillis();

    private final ServerConfig config;
    private final KvStore kvStore;

    @Inject
    public InfoCommand(ServerConfig config, KvStore kvStore) {
        this.config = config;
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        String section = args.size() > 1 ? new String(args.get(1)).toLowerCase() : "all";
        StringBuilder sb = new StringBuilder();

        if (section.equals("all") || section.equals("server")) {
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

        if (section.equals("all") || section.equals("memory")) {
            long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            sb.append("# Memory\r\n");
            sb.append("used_memory:").append(usedMemory).append("\r\n");
            sb.append("used_memory_human:").append(usedMemory / 1024).append("K\r\n");
            sb.append("used_memory_peak:").append(usedMemory).append("\r\n");
            sb.append("used_memory_peak_human:").append(usedMemory / 1024).append("K\r\n");
            sb.append("maxmemory:").append(config.maxMemoryBytes).append("\r\n");
            sb.append("mem_allocator:java\r\n");
            sb.append("\r\n");
        }

        if (section.equals("all") || section.equals("keyspace")) {
            long keys = kvStore.size();
            sb.append("# Keyspace\r\n");
            if (keys > 0) {
                sb.append("db0:keys=").append(keys).append(",expires=0,avg_ttl=0\r\n");
            }
            sb.append("\r\n");
        }

        if (section.equals("all") || section.equals("clients")) {
            sb.append("# Clients\r\n");
            sb.append("connected_clients:1\r\n");
            sb.append("blocked_clients:0\r\n");
            sb.append("\r\n");
        }

        if (section.equals("all") || section.equals("stats")) {
            sb.append("# Stats\r\n");
            sb.append("total_commands_processed:0\r\n");
            sb.append("total_connections_received:0\r\n");
            sb.append("\r\n");
        }

        if (section.equals("all") || section.equals("replication")) {
            sb.append("# Replication\r\n");
            sb.append("role:master\r\n");
            sb.append("connected_slaves:0\r\n");
            sb.append("\r\n");
        }

        if (section.equals("all") || section.equals("cpu")) {
            sb.append("# CPU\r\n");
            sb.append("used_cpu_sys:0.000000\r\n");
            sb.append("used_cpu_user:0.000000\r\n");
            sb.append("\r\n");
        }

        return new RespMessage.BulkString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String name() {
        return "INFO";
    }
}
