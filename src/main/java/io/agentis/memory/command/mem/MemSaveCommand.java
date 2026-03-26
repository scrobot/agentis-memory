package io.agentis.memory.command.mem;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.vector.VectorEngine;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * MEMSAVE key value
 */
@Singleton
public class MemSaveCommand implements CommandHandler {

    private final KvStore kvStore;
    private final VectorEngine vectorEngine;
    private final ServerConfig config;

    @Inject
    public MemSaveCommand(KvStore kvStore, VectorEngine vectorEngine, ServerConfig config) {
        this.kvStore = kvStore;
        this.vectorEngine = vectorEngine;
        this.config = config;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() != 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'MEMSAVE' command");
        }

        String key = new String(args.get(1), StandardCharsets.UTF_8);
        byte[] value = args.get(2);

        if (value.length > config.maxValueSizeBytes) {
            return new RespMessage.Error("ERR value exceeds max-value-size limit");
        }

        String text = new String(value, StandardCharsets.UTF_8);
        
        // 1. Store in KV Store with hasVectorIndex=true
        // We need a way to set hasVectorIndex=true in KvStore.
        // For now, let's use a modified set method or similar.
        // Looking at KvStore.java, the 'set' method doesn't take hasVectorIndex.
        // We'll need to update KvStore or use a different approach.
        
        kvStore.set(key, value, -1, true); 
        
        vectorEngine.indexAsync(key, text);

        return new RespMessage.SimpleString("OK");
    }

    @Override
    public String name() {
        return "MEMSAVE";
    }

    @Override
    public boolean isWriteCommand() {
        return true;
    }
}
