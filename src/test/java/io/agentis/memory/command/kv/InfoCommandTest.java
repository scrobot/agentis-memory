package io.agentis.memory.command.kv;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.AofWriter;
import io.agentis.memory.store.KvStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InfoCommandTest {

    private ServerConfig config;
    private KvStore kvStore;

    @BeforeEach
    void setUp() {
        config = new ServerConfig();
        config.aofEnabled = false;
    }

    @Test
    void allSectionsPresent() {
        String info = getInfo("all");
        assertTrue(info.contains("# Server"));
        assertTrue(info.contains("# Memory"));
        assertTrue(info.contains("# Clients"));
        assertTrue(info.contains("# Stats"));
        assertTrue(info.contains("# Persistence"));
        assertTrue(info.contains("# Keyspace"));
        assertTrue(info.contains("# Replication"));
        assertTrue(info.contains("# CPU"));
    }

    @Test
    void serverSectionHasUptime() {
        String info = getInfo("server");
        assertTrue(info.contains("redis_version:7.0.0"));
        assertTrue(info.contains("uptime_in_seconds:"));
        assertTrue(info.contains("tcp_port:" + config.port));
    }

    @Test
    void statsSectionFormat() {
        String info = getInfo("stats");
        assertTrue(info.contains("total_commands_processed:"));
        assertTrue(info.contains("total_connections_received:"));
        assertTrue(info.contains("instantaneous_ops_per_sec:"));
    }

    @Test
    void clientsSectionFormat() {
        String info = getInfo("clients");
        assertTrue(info.contains("connected_clients:"));
        assertTrue(info.contains("blocked_clients:0"));
    }

    @Test
    void persistenceSectionFormat() {
        String info = getInfo("persistence");
        assertTrue(info.contains("rdb_last_save_time:"));
        assertTrue(info.contains("rdb_last_bgsave_status:ok"));
        assertTrue(info.contains("aof_enabled:0"));
    }

    @Test
    void keyspaceShowsRealKeyCount() {
        kvStore = new KvStore(config);
        kvStore.set("k1", "v".getBytes(StandardCharsets.UTF_8), -1);
        kvStore.set("k2", "v".getBytes(StandardCharsets.UTF_8), -1);
        String info = getInfoWithKvStore("keyspace");
        assertTrue(info.contains("db0:keys=2"));
    }

    @Test
    void memorySectionHasUsedMemory() {
        String info = getInfo("memory");
        assertTrue(info.contains("used_memory:"));
        assertTrue(info.contains("maxmemory:"));
    }

    private String getInfo(String section) {
        kvStore = new KvStore(config);
        return getInfoWithKvStore(section);
    }

    private String getInfoWithKvStore(String section) {
        AofWriter aofWriter = new AofWriter(config);
        InfoCommand cmd = new InfoCommand(config, kvStore, null, null, null, aofWriter);
        RespMessage result = cmd.handle(null, args("INFO", section));
        assertInstanceOf(RespMessage.BulkString.class, result);
        return new String(((RespMessage.BulkString) result).value(), StandardCharsets.UTF_8);
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
