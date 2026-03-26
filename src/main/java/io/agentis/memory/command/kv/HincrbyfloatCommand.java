package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// HINCRBYFLOAT key field increment
@Singleton
public class HincrbyfloatCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public HincrbyfloatCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() != 4) {
            return new RespMessage.Error("ERR wrong number of arguments for 'hincrbyfloat'");
        }
        String key = new String(args.get(1));
        String field = new String(args.get(2));
        double delta;
        try {
            delta = Double.parseDouble(new String(args.get(3)));
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR value is not a valid float");
        }
        Object result = kvStore.hincrbyfloat(key, field, delta);
        if (result instanceof String err) {
            return new RespMessage.Error(err);
        }
        return new RespMessage.BulkString((byte[]) result);
    }

    @Override
    public String name() {
        return "HINCRBYFLOAT";
    }
}
