package io.agentis.memory.command.mem;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.vector.VectorEngine;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * MEMDEL key
 */
@Singleton
public class MemDelCommand implements CommandHandler {

    private final KvStore kvStore;
    private final VectorEngine vectorEngine;

    @Inject
    public MemDelCommand(KvStore kvStore, VectorEngine vectorEngine) {
        this.kvStore = kvStore;
        this.vectorEngine = vectorEngine;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() != 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'MEMDEL' command");
        }

        String key = new String(args.get(1), StandardCharsets.UTF_8);
        
        boolean existedInKv = kvStore.delete(key);
        
        // Always try to cancel and remove from vector engine/HNSW, 
        // because it might be pending or in HNSW even if KV entry is gone (though unlikely)
        vectorEngine.cancelAndRemove(key);

        return new RespMessage.RespInteger(existedInKv ? 1 : 0);
    }

    @Override
    public String name() {
        return "MEMDEL";
    }

    @Override
    public boolean isWriteCommand() {
        return true;
    }
}
