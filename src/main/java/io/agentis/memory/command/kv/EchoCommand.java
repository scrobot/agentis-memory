package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;

import java.util.List;

// ECHO message
@Singleton
public class EchoCommand implements CommandHandler {

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'echo' command");
        }
        return new RespMessage.BulkString(args.get(1));
    }

    @Override
    public String name() {
        return "ECHO";
    }
}
