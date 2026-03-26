package io.agentis.memory.command.kv;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeysCommandTest {

    private KvStore kvStore;
    private KeysCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new KeysCommand(kvStore);
        kvStore.set("foo", "v".getBytes(StandardCharsets.UTF_8), -1);
        kvStore.set("foobar", "v".getBytes(StandardCharsets.UTF_8), -1);
        kvStore.set("bar", "v".getBytes(StandardCharsets.UTF_8), -1);
    }

    @Test
    void matchesAll() {
        RespMessage.RespArray result = (RespMessage.RespArray) cmd.handle(null, args("KEYS", "*"));
        assertEquals(3, result.elements().size());
    }

    @Test
    void matchesWithPrefix() {
        RespMessage.RespArray result = (RespMessage.RespArray) cmd.handle(null, args("KEYS", "foo*"));
        assertEquals(2, result.elements().size());
    }

    @Test
    void matchesExact() {
        RespMessage.RespArray result = (RespMessage.RespArray) cmd.handle(null, args("KEYS", "bar"));
        assertEquals(1, result.elements().size());
    }

    @Test
    void returnsEmptyForNoMatch() {
        RespMessage.RespArray result = (RespMessage.RespArray) cmd.handle(null, args("KEYS", "xyz*"));
        assertEquals(0, result.elements().size());
    }

    @Test
    void matchesWithQuestionMark() {
        RespMessage.RespArray result = (RespMessage.RespArray) cmd.handle(null, args("KEYS", "fo?"));
        assertEquals(1, result.elements().size());
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("KEYS")));
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
