package io.agentis.memory.command.kv;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SScanCommandTest {

    private KvStore kvStore;
    private SAddCommand addCmd;
    private SScanCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        addCmd = new SAddCommand(kvStore);
        cmd = new SScanCommand(kvStore);
    }

    @Test
    void returnsAllMembersInOneScan() {
        addCmd.handle(null, args("SADD", "s", "a", "b", "c"));
        RespMessage.RespArray result = (RespMessage.RespArray) cmd.handle(null, args("SSCAN", "s", "0", "COUNT", "100"));
        RespMessage.BulkString cursor = (RespMessage.BulkString) result.elements().get(0);
        assertEquals("0", new String(cursor.value(), StandardCharsets.UTF_8));
        RespMessage.RespArray members = (RespMessage.RespArray) result.elements().get(1);
        assertEquals(3, members.elements().size());
    }

    @Test
    void returnsEmptyForMissingKey() {
        RespMessage.RespArray result = (RespMessage.RespArray) cmd.handle(null, args("SSCAN", "missing", "0"));
        RespMessage.BulkString cursor = (RespMessage.BulkString) result.elements().get(0);
        assertEquals("0", new String(cursor.value(), StandardCharsets.UTF_8));
        assertTrue(((RespMessage.RespArray) result.elements().get(1)).elements().isEmpty());
    }

    @Test
    void matchFilterWorks() {
        addCmd.handle(null, args("SADD", "s", "hello", "world", "hi"));
        RespMessage.RespArray result = (RespMessage.RespArray) cmd.handle(null, args("SSCAN", "s", "0", "MATCH", "h*", "COUNT", "100"));
        RespMessage.RespArray members = (RespMessage.RespArray) result.elements().get(1);
        assertEquals(2, members.elements().size()); // "hello" and "hi"
    }

    @Test
    void wrongTypeReturnsError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("SSCAN", "str", "0")));
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("SSCAN", "s")));
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
