package io.agentis.memory.command.kv;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DbSizeCommandTest {

    @Test
    void returnsZeroForEmptyStore() {
        KvStore store = new KvStore(new ServerConfig());
        DbSizeCommand cmd = new DbSizeCommand(store);
        assertEquals(0L, ((RespMessage.RespInteger) cmd.handle(null, args("DBSIZE"))).value());
    }

    @Test
    void returnsCorrectCount() {
        KvStore store = new KvStore(new ServerConfig());
        store.set("k1", "v".getBytes(StandardCharsets.UTF_8), -1);
        store.set("k2", "v".getBytes(StandardCharsets.UTF_8), -1);
        DbSizeCommand cmd = new DbSizeCommand(store);
        assertEquals(2L, ((RespMessage.RespInteger) cmd.handle(null, args("DBSIZE"))).value());
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
