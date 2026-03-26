package io.agentis.memory.command.kv;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SetCommandTest {

    private KvStore kvStore;
    private SetCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new SetCommand(kvStore);
    }

    @Test
    void setsValueAndReturnsOk() {
        RespMessage result = cmd.handle(null, args("SET", "mykey", "myvalue"));
        assertInstanceOf(RespMessage.SimpleString.class, result);
        assertEquals("OK", ((RespMessage.SimpleString) result).value());
        assertArrayEquals("myvalue".getBytes(StandardCharsets.UTF_8), kvStore.get("mykey"));
    }

    @Test
    void setsValueWithTtl() {
        RespMessage result = cmd.handle(null, args("SET", "ttlkey", "v", "EX", "60"));
        assertInstanceOf(RespMessage.SimpleString.class, result);
        assertEquals("OK", ((RespMessage.SimpleString) result).value());
        assertNotNull(kvStore.get("ttlkey"));
    }

    @Test
    void setexSyntax() {
        RespMessage result = cmd.handle(null, args("SETEX", "exkey", "60", "exvalue"));
        assertInstanceOf(RespMessage.SimpleString.class, result);
        assertEquals("OK", ((RespMessage.SimpleString) result).value());
        assertArrayEquals("exvalue".getBytes(StandardCharsets.UTF_8), kvStore.get("exkey"));
    }

    @Test
    void errorOnMissingArgs() {
        RespMessage result = cmd.handle(null, args("SET", "onlykey"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    @Test
    void errorOnInvalidTtl() {
        RespMessage result = cmd.handle(null, args("SET", "k", "v", "EX", "notanumber"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
