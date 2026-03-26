package io.agentis.memory.command.kv;

import io.agentis.memory.resp.ClientConnection;
import io.agentis.memory.resp.QuitException;
import io.agentis.memory.resp.RespMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuitCommandTest {

    private final QuitCommand cmd = new QuitCommand();

    @Test
    void throwsQuitException() {
        ClientConnection conn = new StubClientConnection();
        assertThrows(QuitException.class, () -> cmd.handle(conn, args("QUIT")));
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }

    private static class StubClientConnection implements ClientConnection {
        @Override public String remoteAddress() { return "test:0"; }
        @Override public void setAttribute(String key, Object value) {}
        @Override public Object getAttribute(String key) { return null; }
        @Override public void close() {}
    }
}
