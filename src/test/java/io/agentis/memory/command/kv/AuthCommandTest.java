package io.agentis.memory.command.kv;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.ClientConnection;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.resp.SocketClientConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthCommandTest {

    private ClientConnection conn;

    @BeforeEach
    void setUp() throws Exception {
        // Use a simple in-memory connection stub
        conn = new StubClientConnection();
    }

    @Test
    void errorWhenNoPasswordConfigured() {
        ServerConfig config = new ServerConfig();
        config.requirepass = null;
        AuthCommand cmd = new AuthCommand(config);
        RespMessage result = cmd.handle(conn, args("AUTH", "anything"));
        assertInstanceOf(RespMessage.Error.class, result);
        assertTrue(((RespMessage.Error) result).message().contains("no password is set"));
    }

    @Test
    void okWithCorrectPassword() {
        ServerConfig config = new ServerConfig();
        config.requirepass = "secret";
        AuthCommand cmd = new AuthCommand(config);
        RespMessage result = cmd.handle(conn, args("AUTH", "secret"));
        assertEquals("OK", ((RespMessage.SimpleString) result).value());
        assertTrue(Boolean.TRUE.equals(conn.getAttribute(AuthCommand.AUTHENTICATED)));
    }

    @Test
    void errorWithWrongPassword() {
        ServerConfig config = new ServerConfig();
        config.requirepass = "secret";
        AuthCommand cmd = new AuthCommand(config);
        RespMessage result = cmd.handle(conn, args("AUTH", "wrong"));
        assertInstanceOf(RespMessage.Error.class, result);
        assertFalse(Boolean.TRUE.equals(conn.getAttribute(AuthCommand.AUTHENTICATED)));
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }

    /** Simple stub for unit tests — no real socket needed. */
    private static class StubClientConnection implements ClientConnection {
        private final java.util.concurrent.ConcurrentHashMap<String, Object> attrs = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public String remoteAddress() { return "test:0"; }

        @Override
        public void setAttribute(String key, Object value) { attrs.put(key, value); }

        @Override
        public Object getAttribute(String key) { return attrs.get(key); }

        @Override
        public void close() {}
    }
}
