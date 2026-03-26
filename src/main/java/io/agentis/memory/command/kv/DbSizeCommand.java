package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// DBSIZE — returns the number of keys in the store
@Singleton
public class DbSizeCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public DbSizeCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        return new RespMessage.RespInteger(kvStore.size());
    }

    @Override
    public String name() {
        return "DBSIZE";
    }
}
