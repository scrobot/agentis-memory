package io.agentis.memory.resp;

/**
 * Abstraction over a client connection. Replaces Netty ChannelHandlerContext.
 * Stores per-connection state (auth, protocol version, client name).
 */
public interface ClientConnection {
    String remoteAddress();
    void setAttribute(String key, Object value);
    Object getAttribute(String key);
    void close();
}
