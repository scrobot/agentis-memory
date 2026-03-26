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

import static org.junit.jupiter.api.Assertions.*;

class SPopCommandTest {

    private KvStore kvStore;
    private SAddCommand addCmd;
    private SPopCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        addCmd = new SAddCommand(kvStore);
        cmd = new SPopCommand(kvStore);
    }

    @Test
    void popsAndRemovesMemberFromSet() {
        addCmd.handle(null, args("SADD", "s", "a", "b", "c"));
        RespMessage result = cmd.handle(null, args("SPOP", "s"));
        assertInstanceOf(RespMessage.BulkString.class, result);
        String popped = new String(((RespMessage.BulkString) result).value(), StandardCharsets.UTF_8);
        assertTrue(Set.of("a", "b", "c").contains(popped));
        // Card should now be 2
        assertEquals(2L, ((RespMessage.RespInteger) new SCardCommand(kvStore).handle(null, args("SCARD", "s"))).value());
    }

    @Test
    void returnsNilForMissingKey() {
        assertInstanceOf(RespMessage.NullBulkString.class, cmd.handle(null, args("SPOP", "missing")));
    }

    @Test
    void deletesKeyWhenSetBecomesEmpty() {
        addCmd.handle(null, args("SADD", "s", "only"));
        cmd.handle(null, args("SPOP", "s"));
        assertNull(kvStore.getEntry("s"));
    }

    @Test
    void popsCountMembers() {
        addCmd.handle(null, args("SADD", "s", "a", "b", "c", "d"));
        RespMessage result = cmd.handle(null, args("SPOP", "s", "2"));
        RespMessage.RespArray arr = (RespMessage.RespArray) result;
        assertEquals(2, arr.elements().size());
        assertEquals(2L, ((RespMessage.RespInteger) new SCardCommand(kvStore).handle(null, args("SCARD", "s"))).value());
    }

    @Test
    void wrongTypeReturnsError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("SPOP", "str")));
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
