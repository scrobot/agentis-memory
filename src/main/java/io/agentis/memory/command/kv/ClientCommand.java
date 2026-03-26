package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

// CLIENT SETNAME / CLIENT INFO (stub for Redis Insight compatibility)
public class ClientCommand implements CommandHandler {

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for CLIENT");
        }
        String sub = new String(args.get(1)).toUpperCase();
        return switch (sub) {
            case "SETNAME" -> new RespMessage.SimpleString("OK");
            case "GETNAME" -> new RespMessage.BulkString(null); // NULL bulk string
            case "INFO"    -> new RespMessage.BulkString("id=0 addr=127.0.0.1:0 name= db=0\n".getBytes());
            default -> new RespMessage.Error("ERR unknown CLIENT subcommand '" + sub + "'");
        };
    }
}
