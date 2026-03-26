package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.command.server.HelloCommand;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// HGET key field
@Singleton
public class HgetCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public HgetCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() != 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'hget'");
        }
        String key = new String(args.get(1));
        String field = new String(args.get(2));
        Object result = kvStore.hget(key, field);
        if (result instanceof String err) {
            return new RespMessage.Error(err);
        }
        byte[] bytes = (byte[]) result;
        if (bytes == null) {
            Integer version = ctx != null ? ctx.channel().attr(HelloCommand.PROTOCOL_VERSION).get() : 2;
            if (version != null && version == 3) {
                return new RespMessage.Null();
            }
            return new RespMessage.NullBulkString();
        }
        return new RespMessage.BulkString(bytes);
    }

    @Override
    public String name() {
        return "HGET";
    }
}
