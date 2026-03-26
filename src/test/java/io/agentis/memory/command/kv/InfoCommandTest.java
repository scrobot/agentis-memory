package io.agentis.memory.command.kv;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InfoCommandTest {

    private InfoCommand cmd;

    @BeforeEach
    void setUp() {
        cmd = new InfoCommand(new ServerConfig(), new KvStore(new ServerConfig()));
    }

    @Test
    void allSectionsReturnBulkString() {
        RespMessage result = cmd.handle(null, args("INFO"));
        assertInstanceOf(RespMessage.BulkString.class, result);
        String info = new String(((RespMessage.BulkString) result).value(), StandardCharsets.UTF_8);
        assertTrue(info.contains("# Server"));
        assertTrue(info.contains("# Memory"));
        assertTrue(info.contains("# Keyspace"));
    }

    @Test
    void serverSectionContainsRedisVersion() {
        RespMessage result = cmd.handle(null, args("INFO", "server"));
        String info = new String(((RespMessage.BulkString) result).value(), StandardCharsets.UTF_8);
        assertTrue(info.contains("redis_version:"));
        assertTrue(info.contains("tcp_port:"));
    }

    @Test
    void memorySectionContainsUsedMemory() {
        RespMessage result = cmd.handle(null, args("INFO", "memory"));
        String info = new String(((RespMessage.BulkString) result).value(), StandardCharsets.UTF_8);
        assertTrue(info.contains("used_memory:"));
    }

    @Test
    void keyspaceSectionShowsKeys() {
        KvStore store = new KvStore(new ServerConfig());
        store.set("k1", "v".getBytes(StandardCharsets.UTF_8), -1);
        InfoCommand cmdWithKeys = new InfoCommand(new ServerConfig(), store);
        RespMessage result = cmdWithKeys.handle(null, args("INFO", "keyspace"));
        String info = new String(((RespMessage.BulkString) result).value(), StandardCharsets.UTF_8);
        assertTrue(info.contains("db0:keys=1"));
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
