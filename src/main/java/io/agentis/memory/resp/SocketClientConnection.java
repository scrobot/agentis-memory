package io.agentis.memory.resp;

import java.io.IOException;
import java.net.Socket;

/**
 * ClientConnection backed by a plain Socket.
 * Attributes stored as typed volatile fields instead of ConcurrentHashMap
 * for zero-overhead access on the hot path.
 */
public class SocketClientConnection implements ClientConnection {
    private final Socket socket;
    private volatile int protocolVersion = 2;
    private volatile boolean authenticated;
    private volatile String clientName;

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
        switch (key) {
            case "protocol_version" -> protocolVersion = (Integer) value;
            case "authenticated" -> authenticated = (Boolean) value;
            case "client_name" -> clientName = (String) value;
            default -> {} // ignore unknown attributes
        }
    }

    @Override
    public Object getAttribute(String key) {
        return switch (key) {
            case "protocol_version" -> protocolVersion;
            case "authenticated" -> authenticated;
            case "client_name" -> clientName;
            default -> null;
        };
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
