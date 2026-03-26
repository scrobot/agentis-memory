package io.agentis.memory.command.mem;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.vector.Embedder;
import io.agentis.memory.vector.HnswIndex;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * MEMQUERY namespace query K
 */
@Singleton
public class MemQueryCommand implements CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(MemQueryCommand.class);

    private final Embedder embedder;
    private final HnswIndex hnswIndex;

    @Inject
    public MemQueryCommand(Embedder embedder, HnswIndex hnswIndex) {
        this.embedder = embedder;
        this.hnswIndex = hnswIndex;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() != 4) {
            return new RespMessage.Error("ERR wrong number of arguments for 'MEMQUERY' command");
        }

        String namespace = new String(args.get(1), StandardCharsets.UTF_8);
        String query = new String(args.get(2), StandardCharsets.UTF_8);
        int k;

        try {
            k = Integer.parseInt(new String(args.get(3), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR value is not an integer or out of range");
        }

        if (k < 1 || k > 1000) {
            return new RespMessage.Error("ERR K must be between 1 and 1000");
        }

        if (query.isBlank()) {
            return new RespMessage.Error("ERR query must not be empty");
        }

        try {
            float[] queryVector = embedder.embed(query);
            List<HnswIndex.SearchResult_> results = hnswIndex.search(queryVector, namespace, k);

            List<RespMessage> responseArray = new ArrayList<>(results.size());
            for (HnswIndex.SearchResult_ res : results) {
                List<RespMessage> entry = List.of(
                        new RespMessage.BulkString(res.parentKey().getBytes(StandardCharsets.UTF_8)),
                        new RespMessage.BulkString(res.chunkText().getBytes(StandardCharsets.UTF_8)),
                        new RespMessage.BulkString(String.valueOf(res.score()).getBytes(StandardCharsets.UTF_8))
                );
                responseArray.add(new RespMessage.RespArray(entry));
            }

            return new RespMessage.RespArray(responseArray);
        } catch (Exception e) {
            log.error("MEMQUERY failed", e);
            return new RespMessage.Error("ERR MEMQUERY failed: " + e.getMessage());
        }
    }

    @Override
    public String name() {
        return "MEMQUERY";
    }
}
