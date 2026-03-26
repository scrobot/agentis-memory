package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// TYPE key — returns the Redis type name of the value stored at key, or +none if absent
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
        KvStore.Entry entry = kvStore.getEntry(key);
        if (entry == null) {
            return new RespMessage.SimpleString("none");
        }
        String typeName = switch (entry.value()) {
            case StoreValue.StringValue ignored -> "string";
            case StoreValue.HashValue ignored -> "hash";
            case StoreValue.ListValue ignored -> "list";
            case StoreValue.SortedSetValue ignored -> "zset";
        };
        return new RespMessage.SimpleString(typeName);
    }

    @Override
    public String name() {
        return "TYPE";
    }
}
