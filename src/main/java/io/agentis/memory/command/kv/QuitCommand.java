package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

// QUIT — close connection after reply
public class QuitCommand implements CommandHandler {

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        ctx.writeAndFlush(new RespMessage.SimpleString("OK"))
                .addListener(f -> ctx.close());
        return null; // response already written
    }
}
