package io.agentis.memory.command.zset;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZScanCommandTest {

    private KvStore kvStore;
    private ZAddCommand zadd;
    private ZScanCommand zscan;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        zadd = new ZAddCommand(kvStore);
        zscan = new ZScanCommand(kvStore);
    }

    @Test
    void scansAllMembers() {
        zadd.handle(null, args("ZADD", "z", "1", "a", "2", "b", "3", "c"));
        RespMessage result = zscan.handle(null, args("ZSCAN", "z", "0"));
        assertInstanceOf(RespMessage.RespArray.class, result);
        List<RespMessage> outer = ((RespMessage.RespArray) result).elements();
        assertEquals(2, outer.size());
        // cursor should be "0" (complete)
        assertEquals("0", new String(((RespMessage.BulkString) outer.get(0)).value(), StandardCharsets.UTF_8));
        // inner items: member score member score ...
        List<RespMessage> items = ((RespMessage.RespArray) outer.get(1)).elements();
        assertEquals(6, items.size()); // 3 members × 2
    }

    @Test
    void matchPattern() {
        zadd.handle(null, args("ZADD", "z", "1", "foo", "2", "bar", "3", "baz"));
        RespMessage result = zscan.handle(null, args("ZSCAN", "z", "0", "MATCH", "b*"));
        List<RespMessage> items = ((RespMessage.RespArray) ((RespMessage.RespArray) result).elements().get(1)).elements();
        // bar and baz → 4 elements
        assertEquals(4, items.size());
    }

    @Test
    void emptyForMissingKey() {
        RespMessage result = zscan.handle(null, args("ZSCAN", "nokey", "0"));
        List<RespMessage> outer = ((RespMessage.RespArray) result).elements();
        assertEquals("0", new String(((RespMessage.BulkString) outer.get(0)).value(), StandardCharsets.UTF_8));
        assertTrue(((RespMessage.RespArray) outer.get(1)).elements().isEmpty());
    }

    @Test
    void wrongTypeError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, zscan.handle(null, args("ZSCAN", "str", "0")));
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
