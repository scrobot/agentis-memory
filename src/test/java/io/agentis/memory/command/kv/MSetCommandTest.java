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

class MSetCommandTest {

    private KvStore kvStore;
    private MSetCommand mset;
    private MGetCommand mget;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        mset = new MSetCommand(kvStore);
        mget = new MGetCommand(kvStore);
    }

    @Test
    void setsMultipleKeysAtOnce() {
        RespMessage result = mset.handle(null, args("MSET", "k1", "v1", "k2", "v2"));
        assertInstanceOf(RespMessage.SimpleString.class, result);
        assertEquals("OK", ((RespMessage.SimpleString) result).value());
        assertArrayEquals("v1".getBytes(StandardCharsets.UTF_8), kvStore.get("k1"));
        assertArrayEquals("v2".getBytes(StandardCharsets.UTF_8), kvStore.get("k2"));
    }

    @Test
    void overwritesExistingKeys() {
        kvStore.set("k1", "old".getBytes(StandardCharsets.UTF_8), -1);
        mset.handle(null, args("MSET", "k1", "new"));
        assertArrayEquals("new".getBytes(StandardCharsets.UTF_8), kvStore.get("k1"));
    }

    @Test
    void errorOnOddNumberOfArgs() {
        RespMessage result = mset.handle(null, args("MSET", "k1", "v1", "k2"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    @Test
    void errorOnMissingArgs() {
        RespMessage result = mset.handle(null, args("MSET", "k1"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    @Test
    void mgetReturnsCorrectValues() {
        kvStore.set("a", "1".getBytes(StandardCharsets.UTF_8), -1);
        kvStore.set("b", "2".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = mget.handle(null, args("MGET", "a", "b", "missing"));
        assertInstanceOf(RespMessage.RespArray.class, result);
        List<RespMessage> elements = ((RespMessage.RespArray) result).elements();
        assertEquals(3, elements.size());
        assertArrayEquals("1".getBytes(StandardCharsets.UTF_8), ((RespMessage.BulkString) elements.get(0)).value());
        assertArrayEquals("2".getBytes(StandardCharsets.UTF_8), ((RespMessage.BulkString) elements.get(1)).value());
        assertInstanceOf(RespMessage.NullBulkString.class, elements.get(2));
    }

    @Test
    void mgetErrorOnMissingArgs() {
        RespMessage result = mget.handle(null, args("MGET"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
