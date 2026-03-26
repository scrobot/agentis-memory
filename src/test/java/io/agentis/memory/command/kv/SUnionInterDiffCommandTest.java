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

class SUnionInterDiffCommandTest {

    private KvStore kvStore;
    private SAddCommand addCmd;
    private SUnionCommand unionCmd;
    private SInterCommand interCmd;
    private SDiffCommand diffCmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        addCmd = new SAddCommand(kvStore);
        unionCmd = new SUnionCommand(kvStore);
        interCmd = new SInterCommand(kvStore);
        diffCmd = new SDiffCommand(kvStore);
    }

    private Set<String> toSet(RespMessage msg) {
        return ((RespMessage.RespArray) msg).elements().stream()
                .map(m -> new String(((RespMessage.BulkString) m).value(), StandardCharsets.UTF_8))
                .collect(Collectors.toSet());
    }

    // --- SUNION ---

    @Test
    void sunionReturnsUnionOfSets() {
        addCmd.handle(null, args("SADD", "s1", "a", "b"));
        addCmd.handle(null, args("SADD", "s2", "b", "c"));
        Set<String> result = toSet(unionCmd.handle(null, args("SUNION", "s1", "s2")));
        assertEquals(Set.of("a", "b", "c"), result);
    }

    @Test
    void sunionWithMissingKeyTreatsAsEmpty() {
        addCmd.handle(null, args("SADD", "s1", "a"));
        Set<String> result = toSet(unionCmd.handle(null, args("SUNION", "s1", "missing")));
        assertEquals(Set.of("a"), result);
    }

    @Test
    void sunionWrongTypeReturnsError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, unionCmd.handle(null, args("SUNION", "str")));
    }

    // --- SINTER ---

    @Test
    void sinterReturnsIntersection() {
        addCmd.handle(null, args("SADD", "s1", "a", "b", "c"));
        addCmd.handle(null, args("SADD", "s2", "b", "c", "d"));
        Set<String> result = toSet(interCmd.handle(null, args("SINTER", "s1", "s2")));
        assertEquals(Set.of("b", "c"), result);
    }

    @Test
    void sinterWithMissingKeyReturnsEmpty() {
        addCmd.handle(null, args("SADD", "s1", "a"));
        RespMessage.RespArray result = (RespMessage.RespArray) interCmd.handle(null, args("SINTER", "s1", "missing"));
        assertTrue(result.elements().isEmpty());
    }

    @Test
    void sinterWrongTypeReturnsError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, interCmd.handle(null, args("SINTER", "str")));
    }

    // --- SDIFF ---

    @Test
    void sdiffReturnsDifference() {
        addCmd.handle(null, args("SADD", "s1", "a", "b", "c"));
        addCmd.handle(null, args("SADD", "s2", "b", "c"));
        Set<String> result = toSet(diffCmd.handle(null, args("SDIFF", "s1", "s2")));
        assertEquals(Set.of("a"), result);
    }

    @Test
    void sdiffWithMissingSubtractKeyReturnsFirstSet() {
        addCmd.handle(null, args("SADD", "s1", "a", "b"));
        Set<String> result = toSet(diffCmd.handle(null, args("SDIFF", "s1", "missing")));
        assertEquals(Set.of("a", "b"), result);
    }

    @Test
    void sdiffWithMissingFirstKeyReturnsEmpty() {
        addCmd.handle(null, args("SADD", "s2", "a"));
        RespMessage.RespArray result = (RespMessage.RespArray) diffCmd.handle(null, args("SDIFF", "missing", "s2"));
        assertTrue(result.elements().isEmpty());
    }

    @Test
    void sdiffWrongTypeReturnsError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, diffCmd.handle(null, args("SDIFF", "str")));
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
