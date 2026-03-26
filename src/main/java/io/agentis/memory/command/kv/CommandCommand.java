package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;

import java.util.List;

// COMMAND [DOCS] — stub returning empty array.
// Redis Insight queries this on connect.
@Singleton
public class CommandCommand implements CommandHandler {

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        return new RespMessage.RespArray(List.of());
    }

    @Override
    public String name() {
        return "COMMAND";
    }
}
