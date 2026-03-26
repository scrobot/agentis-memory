package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;

// CLIENT SETNAME name | CLIENT GETNAME | CLIENT INFO
// Client name is stored in channel attribute.
@Singleton
public class ClientCommand implements CommandHandler {

    public static final AttributeKey<String> CLIENT_NAME = AttributeKey.valueOf("clientName");

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'CLIENT'");
        }
        String sub = new String(args.get(1)).toUpperCase();
        return switch (sub) {
            case "SETNAME" -> {
                if (args.size() < 3) {
                    yield new RespMessage.Error("ERR wrong number of arguments for 'CLIENT SETNAME'");
                }
                String name = new String(args.get(2));
                ctx.channel().attr(CLIENT_NAME).set(name);
                yield new RespMessage.SimpleString("OK");
            }
            case "GETNAME" -> {
                String name = ctx.channel().attr(CLIENT_NAME).get();
                yield name != null
                        ? new RespMessage.BulkString(name.getBytes(StandardCharsets.UTF_8))
                        : new RespMessage.NullBulkString();
            }
            case "INFO" -> {
                String name = ctx.channel().attr(CLIENT_NAME).get();
                String info = "id=1 addr=" + ctx.channel().remoteAddress()
                        + " cmd=client name=" + (name != null ? name : "") + "\n";
                yield new RespMessage.BulkString(info.getBytes(StandardCharsets.UTF_8));
            }
            case "LIST" -> new RespMessage.BulkString("".getBytes(StandardCharsets.UTF_8));
            case "ID" -> new RespMessage.RespInteger(1);
            case "NO-EVICT", "NO-TOUCH", "CACHING", "REPLY", "UNPAUSE", "PAUSE" ->
                    new RespMessage.SimpleString("OK");
            default -> new RespMessage.Error("ERR Unknown subcommand '" + sub + "' for 'CLIENT'");
        };
    }

    @Override
    public String name() {
        return "CLIENT";
    }
}
