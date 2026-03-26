package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

// AUTH password
public class AuthCommand implements CommandHandler {

    private final ServerConfig config;

    public AuthCommand(ServerConfig config) {
        this.config = config;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        // TODO: validate password, set per-channel authenticated state
        return new RespMessage.Error("ERR not implemented");
    }
}
