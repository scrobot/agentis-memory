package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// TYPE key — returns +string if key exists, +none if not
@Singleton
public class TypeCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public TypeCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'TYPE'");
        }
        String key = new String(args.get(1));
        return kvStore.get(key) != null
                ? new RespMessage.SimpleString("string")
                : new RespMessage.SimpleString("none");
    }

    @Override
    public String name() {
        return "TYPE";
    }
}
