package io.agentis.memory.command.mem;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.vector.VectorEngine;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * MEMSTATUS key
 */
@Singleton
public class MemStatusCommand implements CommandHandler {

    private final KvStore kvStore;
    private final VectorEngine vectorEngine;

    @Inject
    public MemStatusCommand(KvStore kvStore, VectorEngine vectorEngine) {
        this.kvStore = kvStore;
        this.vectorEngine = vectorEngine;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() != 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'MEMSTATUS' command");
        }

        String key = new String(args.get(1), StandardCharsets.UTF_8);

        if (!kvStore.exists(key)) {
            return new RespMessage.Error("ERR no such key");
        }

        VectorEngine.IndexingStatus status = vectorEngine.getStatus(key);
        if (status == null) {
            // Key exists in KV but not in VectorEngine's session memory.
            // Check if it's supposed to be indexed.
            KvStore.Entry entry = kvStore.getEntry(key);
            if (entry != null && entry.hasVectorIndex()) {
                // It has the flag but status is unknown (maybe after restart).
                // Returning something reasonable.
                return new RespMessage.RespArray(List.of(
                        new RespMessage.BulkString("pending".getBytes(StandardCharsets.UTF_8)),
                        new RespMessage.RespInteger(0),
                        new RespMessage.RespInteger(384),
                        new RespMessage.RespInteger(0)
                ));
            } else {
                return new RespMessage.Error("ERR key is not indexed");
            }
        }

        return new RespMessage.RespArray(List.of(
                new RespMessage.BulkString(status.status().getBytes(StandardCharsets.UTF_8)),
                new RespMessage.RespInteger(status.chunkCount()),
                new RespMessage.RespInteger(status.dimensions()),
                new RespMessage.RespInteger(status.lastUpdatedMs())
        ));
    }

    @Override
    public String name() {
        return "MEMSTATUS";
    }
}
