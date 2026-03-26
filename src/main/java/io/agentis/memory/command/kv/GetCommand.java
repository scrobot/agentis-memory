package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

// GET key
public class GetCommand implements CommandHandler {

    private final KvStore kvStore;

    public GetCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        // TODO: implement GET key
        return new RespMessage.Error("ERR not implemented");
    }
}
