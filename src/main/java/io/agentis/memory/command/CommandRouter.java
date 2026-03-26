package io.agentis.memory.command;

import io.agentis.memory.command.kv.AuthCommand;
import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.AofWriter;
import io.agentis.memory.store.SnapshotManager;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses the incoming RespMessage array into command name + args,
 * then dispatches to the appropriate CommandHandler.
 */
@Singleton
public class CommandRouter {
    private static final Logger log = LoggerFactory.getLogger(CommandRouter.class);

    private final Map<String, CommandHandler> handlers = new HashMap<>();
    private final ServerConfig config;
    private final AofWriter aofWriter;
    private final SnapshotManager snapshotManager;

    @Inject
    public CommandRouter(ServerConfig config, AofWriter aofWriter, SnapshotManager snapshotManager, List<CommandHandler> commandHandlers) {
        this.config = config;
        this.aofWriter = aofWriter;
        this.snapshotManager = snapshotManager;

        for (CommandHandler handler : commandHandlers) {
            handlers.put(handler.name().toUpperCase(), handler);
            for (String alias : handler.aliases()) {
                handlers.put(alias.toUpperCase(), handler);
            }
        }
        log.info("Registered {} command handlers (including aliases)", handlers.size());
    }

    public RespMessage dispatch(ChannelHandlerContext ctx, RespMessage msg) {
        if (!(msg instanceof RespMessage.RespArray array) || array.elements() == null || array.elements().isEmpty()) {
            return new RespMessage.Error("ERR protocol error: expected array");
        }

        List<byte[]> args = array.elements().stream()
                .map(m -> switch (m) {
                    case RespMessage.BulkString b -> b.value();
                    case RespMessage.SimpleString s -> s.value().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    case RespMessage.RespInteger i -> java.lang.Long.toString(i.value()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    default -> null;
                })
                .toList();

        if (args.isEmpty() || args.getFirst() == null) {
            return new RespMessage.Error("ERR empty command");
        }

        String name = new String(args.getFirst()).toUpperCase();

        // Auth check: if requirepass is set, ensure client is authenticated
        if (ctx != null && config.requirepass != null && !config.requirepass.isBlank()
                && !NO_AUTH_COMMANDS.contains(name)) {
            Boolean authenticated = ctx.channel().attr(AuthCommand.AUTHENTICATED).get();
            if (!Boolean.TRUE.equals(authenticated)) {
                return new RespMessage.Error("NOAUTH Authentication required.");
            }
        }

        CommandHandler handler = handlers.get(name);
        if (handler == null) {
            if (ctx != null) {
                log.warn("Unknown command '{}' from {}", name, ctx.channel().remoteAddress());
            } else {
                log.warn("Unknown command '{}' during recovery", name);
            }
            return new RespMessage.Error("ERR unknown command '" + name + "'");
        }

        RespMessage response = handler.handle(ctx, args);
        if (response instanceof RespMessage.Error err) {
            if (ctx != null) {
                log.warn("CMD {} from {} → ERROR: {}", name, ctx.channel().remoteAddress(), err.message());
            } else {
                log.warn("CMD {} during recovery → ERROR: {}", name, err.message());
            }
        } else {
            if (ctx != null) {
                log.info("CMD {} from {} → {}", name, ctx.channel().remoteAddress(), describeResponse(response));
                if (handler.isWriteCommand()) {
                    aofWriter.append(args);
                    snapshotManager.incrementDirty();
                    if (snapshotManager.shouldSnapshot()) {
                        new Thread(() -> {
                            try {
                                snapshotManager.save();
                            } catch (Exception e) {
                                log.error("Auto-snapshot failed", e);
                            }
                        }, "snapshot-worker").start();
                    }
                }
            } else {
                log.debug("REPLAY CMD {} → {}", name, describeResponse(response));
            }
        }
        return response;
    }

    private static String describeResponse(RespMessage response) {
        return switch (response) {
            case RespMessage.SimpleString s -> s.value();
            case RespMessage.RespInteger i -> String.valueOf(i.value());
            case RespMessage.BulkString b -> b.value() == null ? "nil" : "(bulk " + b.value().length + "b)";
            case RespMessage.RespArray a -> a.elements() == null ? "nil" : "(array " + a.elements().size() + ")";
            case RespMessage.Error e -> e.message();
            default -> response.getClass().getSimpleName();
        };
    }

    // Commands that are always allowed even without AUTH
    private static final Set<String> NO_AUTH_COMMANDS = Set.of("AUTH", "QUIT", "HELLO");
}
