package io.agentis.memory.command.mem;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.vector.VectorEngine;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

// MEMQUERY namespace query K
// Returns RESP Array of [key, text, score] tuples, top-K by cosine similarity.
// namespace=ALL searches across all namespaces.
public class MemQueryCommand implements CommandHandler {

    private final VectorEngine vectorEngine;

    public MemQueryCommand(VectorEngine vectorEngine) {
        this.vectorEngine = vectorEngine;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        // TODO: validate namespace/query/K, embed query, search HNSW, return results
        return new RespMessage.Error("ERR not implemented");
    }
}
