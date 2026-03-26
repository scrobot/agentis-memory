package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.vector.VectorEngine;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

// INFO [section]
public class InfoCommand implements CommandHandler {

    private final ServerConfig config;
    private final KvStore kvStore;
    private final VectorEngine vectorEngine;

    public InfoCommand(ServerConfig config, KvStore kvStore, VectorEngine vectorEngine) {
        this.config = config;
        this.kvStore = kvStore;
        this.vectorEngine = vectorEngine;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        // TODO: return Redis-compatible INFO sections (server, memory, keyspace)
        return new RespMessage.Error("ERR not implemented");
    }
}
