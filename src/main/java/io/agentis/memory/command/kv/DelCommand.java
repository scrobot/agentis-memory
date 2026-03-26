package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.persistence.AofWriter;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

// DEL key [key ...]
public class DelCommand implements CommandHandler {

    private final KvStore kvStore;
    private final AofWriter aofWriter;

    public DelCommand(KvStore kvStore, AofWriter aofWriter) {
        this.kvStore = kvStore;
        this.aofWriter = aofWriter;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        // TODO: implement DEL key [key ...]
        return new RespMessage.Error("ERR not implemented");
    }
}
