package io.agentis.memory.command;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.ClientConnection;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.AofWriter;
import io.agentis.memory.store.SnapshotManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Singleton
public class CommandRouter {
    private static final Logger log = LoggerFactory.getLogger(CommandRouter.class);

    private final Map<String, CommandHandler> handlers = new HashMap<>();
    private final ServerConfig config;
    private final AofWriter aofWriter;
    private final SnapshotManager snapshotManager;
    private final boolean noAuth;

    private final LongAdder commandsProcessed = new LongAdder();
    private final LongAdder commandErrors = new LongAdder();
    private final ConcurrentHashMap<String, LongAdder> commandCounters = new ConcurrentHashMap<>();

    @Inject
    public CommandRouter(ServerConfig config, AofWriter aofWriter, SnapshotManager snapshotManager, List<CommandHandler> commandHandlers) {
        this.config = config;
        this.aofWriter = aofWriter;
        this.snapshotManager = snapshotManager;
        this.noAuth = config.requirepass == null || config.requirepass.isBlank();

        for (CommandHandler handler : commandHandlers) {
            handlers.put(handler.name().toUpperCase(), handler);
            for (String alias : handler.aliases()) {
                handlers.put(alias.toUpperCase(), handler);
            }
        }
        log.info("Registered {} command handlers (including aliases)", handlers.size());
    }

    public RespMessage dispatch(ClientConnection conn, RespMessage msg) {
        if (!(msg instanceof RespMessage.RespArray array) || array.elements() == null || array.elements().isEmpty()) {
            return new RespMessage.Error("ERR protocol error: expected array");
        }

        List<byte[]> args = array.elements().stream()
                .map(m -> switch (m) {
                    case RespMessage.BulkString b -> b.value();
                    case RespMessage.SimpleString s -> s.value().getBytes(StandardCharsets.UTF_8);
                    case RespMessage.RespInteger i -> Long.toString(i.value()).getBytes(StandardCharsets.UTF_8);
                    default -> null;
                })
                .toList();

        if (args.isEmpty() || args.getFirst() == null) {
            return new RespMessage.Error("ERR empty command");
        }

        String name = toUpperCase(args.getFirst());

        // Auth check — fast path: skip entirely when no password configured
        if (!noAuth && conn != null && !NO_AUTH_COMMANDS.contains(name)) {
            Boolean authenticated = (Boolean) conn.getAttribute("authenticated");
            if (!Boolean.TRUE.equals(authenticated)) {
                return new RespMessage.Error("NOAUTH Authentication required.");
            }
        }

        CommandHandler handler = handlers.get(name);
        if (handler == null) {
            commandErrors.increment();
            if (conn != null) {
                log.warn("Unknown command '{}' from {}", name, conn.remoteAddress());
            } else {
                log.warn("Unknown command '{}' during AOF replay", name);
            }
            return new RespMessage.Error("ERR unknown command '" + name + "'");
        }

        RespMessage response = handler.handle(conn, args);
        if (response instanceof RespMessage.Error err) {
            commandErrors.increment();
            if (log.isDebugEnabled()) {
                if (conn != null) {
                    log.debug("CMD {} from {} → ERROR: {}", name, conn.remoteAddress(), err.message());
                } else {
                    log.debug("CMD {} during AOF replay → ERROR: {}", name, err.message());
                }
            }
        } else {
            commandsProcessed.increment();
            commandCounters.computeIfAbsent(name, _ -> new LongAdder()).increment();
            if (conn != null) {
                if (log.isDebugEnabled()) {
                    log.debug("CMD {} from {} → {}", name, conn.remoteAddress(), describeResponse(response));
                }
                if (handler.isWriteCommand()) {
                    aofWriter.append(args);
                    snapshotManager.incrementDirty();
                    if (snapshotManager.shouldSnapshot()) {
                        Thread.startVirtualThread(() -> {
                            try {
                                snapshotManager.save();
                            } catch (Exception e) {
                                log.error("Auto-snapshot failed", e);
                            }
                        });
                    }
                }
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("AOF replay: {} → {}", name, describeResponse(response));
                }
            }
        }
        return response;
    }

    public long getCommandsProcessed() { return commandsProcessed.sum(); }
    public long getCommandErrors() { return commandErrors.sum(); }

    public Map<String, Long> getCommandCounts() {
        var snapshot = new java.util.LinkedHashMap<String, Long>();
        commandCounters.forEach((cmd, adder) -> snapshot.put(cmd, adder.sum()));
        return snapshot;
    }

    private static String toUpperCase(byte[] bytes) {
        byte[] upper = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            upper[i] = (b >= 'a' && b <= 'z') ? (byte) (b - 32) : b;
        }
        return new String(upper, StandardCharsets.US_ASCII);
    }

    private static String describeResponse(RespMessage response) {
        return switch (response) {
            case null -> "null";
            case RespMessage.SimpleString s -> s.value();
            case RespMessage.RespInteger i -> String.valueOf(i.value());
            case RespMessage.BulkString b -> b.value() == null ? "nil" : "(bulk " + b.value().length + "b)";
            case RespMessage.RespArray a -> a.elements() == null ? "nil" : "(array " + a.elements().size() + ")";
            case RespMessage.Error e -> e.message();
            default -> response.getClass().getSimpleName();
        };
    }

    private static final Set<String> NO_AUTH_COMMANDS = Set.of("AUTH", "QUIT", "HELLO");
}
