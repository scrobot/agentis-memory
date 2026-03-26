package io.agentis.memory.resp;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClientConnection backed by a plain Socket.
 * Per-connection attributes stored in a ConcurrentHashMap.
 */
public class SocketClientConnection implements ClientConnection {
    private final Socket socket;
    private final ConcurrentHashMap<String, Object> attrs = new ConcurrentHashMap<>();

    public SocketClientConnection(Socket socket) {
        this.socket = socket;
    }

    @Override
    public String remoteAddress() {
        return socket.getRemoteSocketAddress() != null
                ? socket.getRemoteSocketAddress().toString()
                : "unknown";
    }

    @Override
    public void setAttribute(String key, Object value) {
        attrs.put(key, value);
    }

    @Override
    public Object getAttribute(String key) {
        return attrs.get(key);
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
