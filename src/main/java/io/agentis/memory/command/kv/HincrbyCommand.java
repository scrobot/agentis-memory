package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// HINCRBY key field increment
@Singleton
public class HincrbyCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public HincrbyCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() != 4) {
            return new RespMessage.Error("ERR wrong number of arguments for 'hincrby'");
        }
        String key = new String(args.get(1));
        String field = new String(args.get(2));
        long delta;
        try {
            delta = Long.parseLong(new String(args.get(3)));
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR value is not an integer or out of range");
        }
        Object result = kvStore.hincrby(key, field, delta);
        if (result instanceof String err) {
            return new RespMessage.Error(err);
        }
        return new RespMessage.RespInteger((Long) result);
    }

    @Override
    public String name() {
        return "HINCRBY";
    }
}
