package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;

import java.util.List;

// QUIT — responds +OK and closes the connection
@Singleton
public class QuitCommand implements CommandHandler {

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        ctx.writeAndFlush(new RespMessage.SimpleString("OK"))
                .addListener(f -> ctx.close());
        return null; // response already written
    }

    @Override
    public String name() {
        return "QUIT";
    }
}
