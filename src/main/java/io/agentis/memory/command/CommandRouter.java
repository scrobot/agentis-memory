package io.agentis.memory.command;

import io.agentis.memory.command.kv.*;
import io.agentis.memory.command.mem.*;
import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.persistence.AofWriter;
import io.agentis.memory.persistence.SnapshotWriter;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.vector.VectorEngine;
import io.netty.channel.ChannelHandlerContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the incoming RespMessage array into command name + args,
 * then dispatches to the appropriate CommandHandler.
 */
public class CommandRouter {

    private final Map<String, CommandHandler> handlers = new HashMap<>();
    private final ServerConfig config;

    public CommandRouter(ServerConfig config, KvStore kvStore, VectorEngine vectorEngine,
                         AofWriter aofWriter, SnapshotWriter snapshotWriter) {
        this.config = config;

        // KV commands
        handlers.put("SET",     new SetCommand(kvStore, aofWriter));
        handlers.put("GET",     new GetCommand(kvStore));
        handlers.put("DEL",     new DelCommand(kvStore, aofWriter));
        handlers.put("EXISTS",  new ExistsCommand(kvStore));
        handlers.put("EXPIRE",  new ExpireCommand(kvStore, aofWriter));
        handlers.put("TTL",     new TtlCommand(kvStore));
        handlers.put("KEYS",    new KeysCommand(kvStore));
        handlers.put("SCAN",    new ScanCommand(kvStore));
        handlers.put("TYPE",    new TypeCommand(kvStore));
        handlers.put("DBSIZE",  new DbSizeCommand(kvStore));
        handlers.put("BGSAVE",  new BgSaveCommand(snapshotWriter));

        // Server / connection commands
        handlers.put("PING",    new PingCommand());
        handlers.put("QUIT",    new QuitCommand());
        handlers.put("AUTH",    new AuthCommand(config));
        handlers.put("INFO",    new InfoCommand(config, kvStore, vectorEngine));
        handlers.put("CLIENT",  new ClientCommand());
        handlers.put("CONFIG",  new ConfigCommand(config));
        handlers.put("COMMAND", new CommandMetaCommand());

        // Vector / memory commands
        handlers.put("MEMSAVE",   new MemSaveCommand(kvStore, vectorEngine, aofWriter));
        handlers.put("MEMQUERY",  new MemQueryCommand(vectorEngine));
        handlers.put("MEMDEL",    new MemDelCommand(kvStore, vectorEngine, aofWriter));
        handlers.put("MEMSTATUS", new MemStatusCommand(vectorEngine));
    }

    public RespMessage dispatch(ChannelHandlerContext ctx, RespMessage msg) {
        if (!(msg instanceof RespMessage.Array array) || array.elements() == null || array.elements().isEmpty()) {
            return new RespMessage.Error("ERR protocol error: expected array");
        }

        List<byte[]> args = array.elements().stream()
                .map(m -> switch (m) {
                    case RespMessage.BulkString b -> b.value();
                    default -> null;
                })
                .toList();

        if (args.isEmpty() || args.getFirst() == null) {
            return new RespMessage.Error("ERR empty command");
        }

        String name = new String(args.getFirst()).toUpperCase();

        // AUTH gate: if requirepass is set and client is not authenticated, reject all except AUTH
        // TODO: per-channel auth state

        CommandHandler handler = handlers.get(name);
        if (handler == null) {
            return new RespMessage.Error("ERR unknown command '" + name + "'");
        }

        return handler.handle(ctx, args);
    }
}
