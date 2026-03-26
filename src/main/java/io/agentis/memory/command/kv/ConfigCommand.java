package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

// CONFIG GET parameter (stub — returns basic values for Redis Insight compatibility)
public class ConfigCommand implements CommandHandler {

    private final ServerConfig config;

    public ConfigCommand(ServerConfig config) {
        this.config = config;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        // TODO: return relevant config values for CONFIG GET
        return RespMessage.Array.EMPTY;
    }
}
