package io.agentis.memory.command.mem;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.persistence.AofWriter;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.vector.VectorEngine;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

// MEMDEL key
// Deletes from vector index + KV. Cancels pending indexation if in progress.
public class MemDelCommand implements CommandHandler {

    private final KvStore kvStore;
    private final VectorEngine vectorEngine;
    private final AofWriter aofWriter;

    public MemDelCommand(KvStore kvStore, VectorEngine vectorEngine, AofWriter aofWriter) {
        this.kvStore = kvStore;
        this.vectorEngine = vectorEngine;
        this.aofWriter = aofWriter;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        // TODO: cancel pending job, delete chunks from HNSW, delete from KV
        return new RespMessage.Error("ERR not implemented");
    }
}
