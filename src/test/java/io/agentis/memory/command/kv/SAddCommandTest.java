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

class SAddCommandTest {

    private KvStore kvStore;
    private SAddCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new SAddCommand(kvStore);
    }

    @Test
    void addsNewMembers() {
        RespMessage result = cmd.handle(null, args("SADD", "s", "a", "b", "c"));
        assertEquals(3L, ((RespMessage.RespInteger) result).value());
    }

    @Test
    void ignoresDuplicates() {
        cmd.handle(null, args("SADD", "s", "a", "b"));
        RespMessage result = cmd.handle(null, args("SADD", "s", "b", "c"));
        assertEquals(1L, ((RespMessage.RespInteger) result).value());
    }

    @Test
    void createsSetIfAbsent() {
        cmd.handle(null, args("SADD", "s", "x"));
        assertEquals("set", ((RespMessage.SimpleString) new TypeCommand(kvStore).handle(null, args("TYPE", "s"))).value());
    }

    @Test
    void wrongTypeReturnsError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = cmd.handle(null, args("SADD", "str", "member"));
        assertInstanceOf(RespMessage.Error.class, result);
        assertTrue(((RespMessage.Error) result).message().startsWith("WRONGTYPE"));
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("SADD", "s")));
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
