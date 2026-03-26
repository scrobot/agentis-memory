package io.agentis.memory.command.list;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// RPUSH key element [element ...]
@Singleton
public class RPushCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public RPushCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'RPUSH'");
        }
        String key = new String(args.get(1));
        List<byte[]> elements = args.subList(2, args.size());
        try {
            long len = kvStore.rpush(key, elements);
            return new RespMessage.RespInteger(len);
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error(e.getMessage());
        }
    }

    @Override
    public String name() {
        return "RPUSH";
    }
}
