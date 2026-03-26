package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// HDEL key field [field ...]
@Singleton
public class HdelCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public HdelCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'hdel'");
        }
        String key = new String(args.get(1));
        List<String> fields = args.subList(2, args.size()).stream()
                .map(String::new)
                .toList();
        Object result = kvStore.hdel(key, fields);
        if (result instanceof String err) {
            return new RespMessage.Error(err);
        }
        return new RespMessage.RespInteger((Long) result);
    }

    @Override
    public String name() {
        return "HDEL";
    }
}
