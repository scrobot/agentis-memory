package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

// DBSIZE
public class DbSizeCommand implements CommandHandler {

    private final KvStore kvStore;

    public DbSizeCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        // TODO: implement DBSIZE
        return new RespMessage.Error("ERR not implemented");
    }
}
