package io.agentis.memory.command.kv;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SMembersCommandTest {

    private KvStore kvStore;
    private SAddCommand addCmd;
    private SMembersCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        addCmd = new SAddCommand(kvStore);
        cmd = new SMembersCommand(kvStore);
    }

    @Test
    void returnsAllMembers() {
        addCmd.handle(null, args("SADD", "s", "a", "b", "c"));
        RespMessage result = cmd.handle(null, args("SMEMBERS", "s"));
        RespMessage.RespArray arr = (RespMessage.RespArray) result;
        Set<String> members = arr.elements().stream()
                .map(m -> new String(((RespMessage.BulkString) m).value(), StandardCharsets.UTF_8))
                .collect(Collectors.toSet());
        assertEquals(Set.of("a", "b", "c"), members);
    }

    @Test
    void returnsEmptyArrayForMissingKey() {
        RespMessage result = cmd.handle(null, args("SMEMBERS", "missing"));
        RespMessage.RespArray arr = (RespMessage.RespArray) result;
        assertTrue(arr.elements().isEmpty());
    }

    @Test
    void wrongTypeReturnsError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("SMEMBERS", "str")));
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("SMEMBERS")));
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
