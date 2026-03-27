package io.agentis.memory.resp;

import io.agentis.memory.command.CommandRouter;
import io.agentis.memory.config.ServerConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCP server using virtual threads and plain ServerSocket.
 * Replaces Netty ServerBootstrap — zero native dependencies, zero reflection issues.
 */
@Singleton
public class RespServer {
    private static final Logger log = LoggerFactory.getLogger(RespServer.class);

    private final ServerConfig config;
    private final CommandRouter router;
    private ServerSocket serverSocket;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final AtomicInteger activeConnections = new AtomicInteger();

    @Inject
    public RespServer(ServerConfig config, CommandRouter router) {
        this.config = config;
        this.router = router;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(config.bind, config.port));

        log.info("Agentis Memory listening on {}:{}", config.bind, config.port);

        // Accept loop runs on its own virtual thread
        Thread.startVirtualThread(() -> {
            try {
                while (!stopped.get()) {
                    Socket socket = serverSocket.accept();
                    Thread.startVirtualThread(() -> handleConnection(socket));
                }
            } catch (IOException e) {
                if (!stopped.get()) {
                    log.error("Accept loop error", e);
                }
            } finally {
                shutdownLatch.countDown();
            }
        });
    }

    private void handleConnection(Socket socket) {
        ClientConnection conn = new SocketClientConnection(socket);
        conn.setAttribute("protocol_version", 2); // RESP2 default
        conn.setAttribute("authenticated", Boolean.FALSE);
        int active = activeConnections.incrementAndGet();
        log.trace("Client connected: {} (active: {})", conn.remoteAddress(), active);

        try (socket) {
            RespParser parser = new RespParser(socket.getInputStream());
            RespWriter writer = new RespWriter(socket.getOutputStream());

            while (!stopped.get()) {
                RespMessage msg = parser.readMessage();
                if (msg == null) break; // clean disconnect

                try {
                    RespMessage response = router.dispatch(conn, msg);
                    if (response != null) {
                        writer.write(response);
                        // Pipelining: only flush when no more buffered commands
                        if (!parser.hasBufferedData()) {
                            writer.flush();
                        }
                    }
                } catch (QuitException _) {
                    // QUIT command: write +OK, then close
                    writer.write(new RespMessage.SimpleString("OK"));
                    writer.flush();
                    break;
                } catch (Exception e) {
                    log.error("Command dispatch error client={}", conn.remoteAddress(), e);
                    try {
                        writer.write(new RespMessage.Error("ERR internal error: " + e.getMessage()));
                        writer.flush();
                    } catch (IOException writeErr) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            if (!stopped.get()) {
                log.trace("Client disconnected: {} ({})", conn.remoteAddress(), e.getMessage());
            }
        }

        active = activeConnections.decrementAndGet();
        log.trace("Client disconnected: {} (active: {})", conn.remoteAddress(), active);
    }

    public void shutdown() {
        stopped.set(true);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log.warn("Error closing server socket", e);
            }
        }
    }

    public void waitForShutdown() throws InterruptedException {
        shutdownLatch.await();
    }
}
