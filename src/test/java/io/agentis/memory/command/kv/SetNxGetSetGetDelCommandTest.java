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

class SetNxGetSetGetDelCommandTest {

    private KvStore kvStore;
    private SetNxCommand setnx;
    private GetSetCommand getset;
    private GetDelCommand getdel;
    private GetExCommand getex;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        setnx = new SetNxCommand(kvStore);
        getset = new GetSetCommand(kvStore);
        getdel = new GetDelCommand(kvStore);
        getex = new GetExCommand(kvStore);
    }

    // --- SETNX ---

    @Test
    void setnxSetsWhenKeyAbsent() {
        RespMessage result = setnx.handle(null, args("SETNX", "k", "v"));
        assertEquals(1L, ((RespMessage.RespInteger) result).value());
        assertArrayEquals("v".getBytes(StandardCharsets.UTF_8), kvStore.get("k"));
    }

    @Test
    void setnxDoesNotOverwriteExisting() {
        kvStore.set("k", "existing".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = setnx.handle(null, args("SETNX", "k", "new"));
        assertEquals(0L, ((RespMessage.RespInteger) result).value());
        assertArrayEquals("existing".getBytes(StandardCharsets.UTF_8), kvStore.get("k"));
    }

    @Test
    void setnxErrorOnMissingArgs() {
        RespMessage result = setnx.handle(null, args("SETNX", "k"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    // --- GETSET ---

    @Test
    void getsetReturnsNilForNewKey() {
        RespMessage result = getset.handle(null, args("GETSET", "k", "v"));
        assertInstanceOf(RespMessage.NullBulkString.class, result);
        assertArrayEquals("v".getBytes(StandardCharsets.UTF_8), kvStore.get("k"));
    }

    @Test
    void getsetReturnsOldValueAndSetsNew() {
        kvStore.set("k", "old".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = getset.handle(null, args("GETSET", "k", "new"));
        assertInstanceOf(RespMessage.BulkString.class, result);
        assertArrayEquals("old".getBytes(StandardCharsets.UTF_8), ((RespMessage.BulkString) result).value());
        assertArrayEquals("new".getBytes(StandardCharsets.UTF_8), kvStore.get("k"));
    }

    @Test
    void getsetErrorOnMissingArgs() {
        RespMessage result = getset.handle(null, args("GETSET", "k"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    // --- GETDEL ---

    @Test
    void getdelReturnsValueAndDeletesKey() {
        kvStore.set("k", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = getdel.handle(null, args("GETDEL", "k"));
        assertInstanceOf(RespMessage.BulkString.class, result);
        assertArrayEquals("v".getBytes(StandardCharsets.UTF_8), ((RespMessage.BulkString) result).value());
        assertNull(kvStore.get("k"));
    }

    @Test
    void getdelReturnsNilForMissingKey() {
        RespMessage result = getdel.handle(null, args("GETDEL", "missing"));
        assertInstanceOf(RespMessage.NullBulkString.class, result);
    }

    @Test
    void getdelErrorOnMissingArgs() {
        RespMessage result = getdel.handle(null, args("GETDEL"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    // --- GETEX ---

    @Test
    void getexReturnsValueWithNoOptions() {
        kvStore.set("k", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = getex.handle(null, args("GETEX", "k"));
        assertInstanceOf(RespMessage.BulkString.class, result);
        assertArrayEquals("v".getBytes(StandardCharsets.UTF_8), ((RespMessage.BulkString) result).value());
    }

    @Test
    void getexReturnsNilForMissingKey() {
        RespMessage result = getex.handle(null, args("GETEX", "missing"));
        assertInstanceOf(RespMessage.NullBulkString.class, result);
    }

    @Test
    void getexSetsExpireWithEx() {
        kvStore.set("k", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = getex.handle(null, args("GETEX", "k", "EX", "100"));
        assertInstanceOf(RespMessage.BulkString.class, result);
        // TTL should now be set
        KvStore.Entry e = kvStore.getEntry("k");
        assertNotNull(e);
        assertTrue(e.expireAt() > System.currentTimeMillis());
    }

    @Test
    void getexPersistRemovesTtl() {
        kvStore.set("k", "v".getBytes(StandardCharsets.UTF_8), 100);
        RespMessage result = getex.handle(null, args("GETEX", "k", "PERSIST"));
        assertInstanceOf(RespMessage.BulkString.class, result);
        KvStore.Entry e = kvStore.getEntry("k");
        assertNotNull(e);
        assertEquals(-1L, e.expireAt());
    }

    @Test
    void getexErrorOnInvalidOption() {
        kvStore.set("k", "v".getBytes(StandardCharsets.UTF_8), -1);
        RespMessage result = getex.handle(null, args("GETEX", "k", "BADOPTION"));
        assertInstanceOf(RespMessage.Error.class, result);
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
