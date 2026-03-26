package io.agentis.memory.command.kv;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScanCommandTest {

    private KvStore kvStore;
    private ScanCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new ScanCommand(kvStore);
        for (int i = 0; i < 5; i++) {
            kvStore.set("key" + i, "v".getBytes(StandardCharsets.UTF_8), -1);
        }
    }

    @Test
    void scanFromZeroReturnsCursorAndKeys() {
        RespMessage.RespArray result = (RespMessage.RespArray) cmd.handle(null, args("SCAN", "0"));
        assertEquals(2, result.elements().size());
        assertInstanceOf(RespMessage.BulkString.class, result.elements().get(0));
        assertInstanceOf(RespMessage.RespArray.class, result.elements().get(1));
    }

    @Test
    void scanWithCountReturnsRequestedCount() {
        RespMessage.RespArray result = (RespMessage.RespArray) cmd.handle(null, args("SCAN", "0", "COUNT", "2"));
        RespMessage.RespArray keys = (RespMessage.RespArray) result.elements().get(1);
        assertEquals(2, keys.elements().size());
    }

    @Test
    void scanWithMatchPattern() {
        RespMessage.RespArray result = (RespMessage.RespArray) cmd.handle(null, args("SCAN", "0", "MATCH", "key*", "COUNT", "100"));
        RespMessage.RespArray keys = (RespMessage.RespArray) result.elements().get(1);
        assertEquals(5, keys.elements().size());
    }

    @Test
    void fullIterationCoversAllKeys() {
        int cursor = 0;
        int totalKeys = 0;
        int iterations = 0;
        do {
            RespMessage.RespArray result = (RespMessage.RespArray) cmd.handle(null, args("SCAN", String.valueOf(cursor), "COUNT", "2"));
            cursor = Integer.parseInt(new String(((RespMessage.BulkString) result.elements().get(0)).value()));
            totalKeys += ((RespMessage.RespArray) result.elements().get(1)).elements().size();
            iterations++;
        } while (cursor != 0 && iterations < 20);
        assertEquals(5, totalKeys);
    }

    @Test
    void emptyScanReturnsZeroCursor() {
        KvStore empty = new KvStore(new ServerConfig());
        ScanCommand emptyCmd = new ScanCommand(empty);
        RespMessage.RespArray result = (RespMessage.RespArray) emptyCmd.handle(null, args("SCAN", "0"));
        String cursor = new String(((RespMessage.BulkString) result.elements().get(0)).value());
        assertEquals("0", cursor);
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
