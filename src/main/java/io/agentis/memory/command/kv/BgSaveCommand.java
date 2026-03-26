package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.persistence.SnapshotWriter;
import io.agentis.memory.resp.RespMessage;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

// BGSAVE — trigger manual snapshot
public class BgSaveCommand implements CommandHandler {

    private final SnapshotWriter snapshotWriter;

    public BgSaveCommand(SnapshotWriter snapshotWriter) {
        this.snapshotWriter = snapshotWriter;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        // TODO: trigger async snapshot, return +Background saving started
        return new RespMessage.Error("ERR not implemented");
    }
}
