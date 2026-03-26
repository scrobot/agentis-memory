package io.agentis.memory.command.kv;

import io.agentis.memory.resp.ClientConnection;
import io.agentis.memory.resp.RespMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ClientCommandTest {

    private ClientCommand cmd;
    private ClientConnection conn;

    @BeforeEach
    void setUp() {
        cmd = new ClientCommand();
        conn = new StubClientConnection();
    }

    @Test
    void setNameReturnsOk() {
        RespMessage result = cmd.handle(conn, args("CLIENT", "SETNAME", "myapp"));
        assertEquals("OK", ((RespMessage.SimpleString) result).value());
    }

    @Test
    void getNameAfterSetName() {
        cmd.handle(conn, args("CLIENT", "SETNAME", "myapp"));
        RespMessage result = cmd.handle(conn, args("CLIENT", "GETNAME"));
        assertInstanceOf(RespMessage.BulkString.class, result);
        assertEquals("myapp", new String(((RespMessage.BulkString) result).value(), StandardCharsets.UTF_8));
    }

    @Test
    void getNameBeforeSetNameReturnsNull() {
        RespMessage result = cmd.handle(conn, args("CLIENT", "GETNAME"));
        assertInstanceOf(RespMessage.NullBulkString.class, result);
    }

    @Test
    void infoReturnsBulkString() {
        RespMessage result = cmd.handle(conn, args("CLIENT", "INFO"));
        assertInstanceOf(RespMessage.BulkString.class, result);
    }

    @Test
    void unknownSubcommandReturnsError() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(conn, args("CLIENT", "UNKNOWN")));
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(conn, args("CLIENT")));
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }

    private static class StubClientConnection implements ClientConnection {
        private final ConcurrentHashMap<String, Object> attrs = new ConcurrentHashMap<>();

        @Override public String remoteAddress() { return "test:0"; }
        @Override public void setAttribute(String key, Object value) { attrs.put(key, value); }
        @Override public Object getAttribute(String key) { return attrs.get(key); }
        @Override public void close() {}
    }
}
