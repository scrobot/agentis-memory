package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;

import java.util.List;

// SELECT index
// For MVP: only SELECT 0 is supported. Non-zero index returns error.
@Singleton
public class SelectCommand implements CommandHandler {

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'select' command");
        }
        String indexStr = new String(args.get(1));
        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR value is not an integer or out of range");
        }
        if (index != 0) {
            return new RespMessage.Error("ERR DB index is out of range");
        }
        return new RespMessage.SimpleString("OK");
    }

    @Override
    public String name() {
        return "SELECT";
    }
}
