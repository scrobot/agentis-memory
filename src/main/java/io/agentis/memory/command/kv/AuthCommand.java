package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// AUTH password — validates password against config.requirepass
@Singleton
public class AuthCommand implements CommandHandler {

    public static final AttributeKey<Boolean> AUTHENTICATED = AttributeKey.valueOf("authenticated");

    private final ServerConfig config;

    @Inject
    public AuthCommand(ServerConfig config) {
        this.config = config;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (config.requirepass == null || config.requirepass.isBlank()) {
            return new RespMessage.Error("ERR Client sent AUTH, but no password is set");
        }
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'AUTH'");
        }
        String provided = new String(args.get(1));
        if (config.requirepass.equals(provided)) {
            ctx.channel().attr(AUTHENTICATED).set(true);
            return new RespMessage.SimpleString("OK");
        }
        ctx.channel().attr(AUTHENTICATED).set(false);
        return new RespMessage.Error("WRONGPASS invalid username-password pair or user is disabled.");
    }

    @Override
    public String name() {
        return "AUTH";
    }
}
