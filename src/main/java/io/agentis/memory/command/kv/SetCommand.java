package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.persistence.AofWriter;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

// SET key value [EX seconds]
public class SetCommand implements CommandHandler {

    private final KvStore kvStore;
    private final AofWriter aofWriter;

    public SetCommand(KvStore kvStore, AofWriter aofWriter) {
        this.kvStore = kvStore;
        this.aofWriter = aofWriter;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        // TODO: implement SET key value [EX seconds]
        return new RespMessage.Error("ERR not implemented");
    }
}
