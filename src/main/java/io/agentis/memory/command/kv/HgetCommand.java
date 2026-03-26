package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
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
        return bytes == null ? new RespMessage.NullBulkString() : new RespMessage.BulkString(bytes);
    }

    @Override
    public String name() {
        return "HGET";
    }
}
