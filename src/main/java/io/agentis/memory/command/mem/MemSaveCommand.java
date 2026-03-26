package io.agentis.memory.command.mem;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.persistence.AofWriter;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.vector.VectorEngine;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

// MEMSAVE key value
// Synchronous: stores original in KV, returns +OK immediately.
// Asynchronous: background chunking → embedding → HNSW indexation.
public class MemSaveCommand implements CommandHandler {

    private final KvStore kvStore;
    private final VectorEngine vectorEngine;
    private final AofWriter aofWriter;

    public MemSaveCommand(KvStore kvStore, VectorEngine vectorEngine, AofWriter aofWriter) {
        this.kvStore = kvStore;
        this.vectorEngine = vectorEngine;
        this.aofWriter = aofWriter;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        // TODO: validate args, write to KV, submit async indexation job
        return new RespMessage.Error("ERR not implemented");
    }
}
