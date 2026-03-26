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

class SRandMemberCommandTest {

    private KvStore kvStore;
    private SAddCommand addCmd;
    private SRandMemberCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        addCmd = new SAddCommand(kvStore);
        cmd = new SRandMemberCommand(kvStore);
    }

    @Test
    void returnsNilForMissingKey() {
        assertInstanceOf(RespMessage.NullBulkString.class, cmd.handle(null, args("SRANDMEMBER", "missing")));
    }

    @Test
    void returnsMemberFromSet() {
        addCmd.handle(null, args("SADD", "s", "a", "b", "c"));
        RespMessage result = cmd.handle(null, args("SRANDMEMBER", "s"));
        assertInstanceOf(RespMessage.BulkString.class, result);
        String member = new String(((RespMessage.BulkString) result).value(), StandardCharsets.UTF_8);
        assertTrue(Set.of("a", "b", "c").contains(member));
    }

    @Test
    void positiveCountReturnsDistinctMembers() {
        addCmd.handle(null, args("SADD", "s", "a", "b", "c"));
        RespMessage.RespArray result = (RespMessage.RespArray) cmd.handle(null, args("SRANDMEMBER", "s", "2"));
        assertEquals(2, result.elements().size());
        // All returned must be from the original set
        Set<String> all = Set.of("a", "b", "c");
        for (RespMessage m : result.elements()) {
            assertTrue(all.contains(new String(((RespMessage.BulkString) m).value(), StandardCharsets.UTF_8)));
        }
    }

    @Test
    void positiveCountLargerThanSetReturnsAllDistinct() {
        addCmd.handle(null, args("SADD", "s", "a", "b"));
        RespMessage.RespArray result = (RespMessage.RespArray) cmd.handle(null, args("SRANDMEMBER", "s", "10"));
        assertEquals(2, result.elements().size());
    }

    @Test
    void negativeCountAllowsDuplicates() {
        addCmd.handle(null, args("SADD", "s", "a"));
        RespMessage.RespArray result = (RespMessage.RespArray) cmd.handle(null, args("SRANDMEMBER", "s", "-5"));
        assertEquals(5, result.elements().size());
        // All must be "a"
        for (RespMessage m : result.elements()) {
            assertEquals("a", new String(((RespMessage.BulkString) m).value(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void wrongTypeReturnsError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("SRANDMEMBER", "str")));
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
