package io.agentis.memory.command.zset;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZIncrByCommandTest {

    private KvStore kvStore;
    private ZAddCommand zadd;
    private ZIncrByCommand zincrby;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        zadd = new ZAddCommand(kvStore);
        zincrby = new ZIncrByCommand(kvStore);
    }

    @Test
    void incrementsExistingMember() {
        zadd.handle(null, args("ZADD", "z", "5", "a"));
        RespMessage result = zincrby.handle(null, args("ZINCRBY", "z", "3", "a"));
        assertInstanceOf(RespMessage.BulkString.class, result);
        assertEquals("8", new String(((RespMessage.BulkString) result).value(), StandardCharsets.UTF_8));
    }

    @Test
    void createsNewMemberWithScore() {
        RespMessage result = zincrby.handle(null, args("ZINCRBY", "z", "7", "newmember"));
        assertEquals("7", new String(((RespMessage.BulkString) result).value(), StandardCharsets.UTF_8));
        KvStore.Entry e = kvStore.getEntry("z");
        assertNotNull(e);
        assertInstanceOf(StoreValue.SortedSetValue.class, e.value());
    }

    @Test
    void wrongTypeError() {
        kvStore.set("str", "v".getBytes(StandardCharsets.UTF_8), -1);
        assertInstanceOf(RespMessage.Error.class, zincrby.handle(null, args("ZINCRBY", "str", "1", "a")));
    }

    @Test
    void errorOnInvalidIncrement() {
        RespMessage result = zincrby.handle(null, args("ZINCRBY", "z", "notfloat", "a"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
