package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

// PING [message]
public class PingCommand implements CommandHandler {

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() > 1) {
            return new RespMessage.BulkString(args.get(1));
        }
        return new RespMessage.SimpleString("PONG");
    }
}
