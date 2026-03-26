package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// HVALS key
@Singleton
public class HvalsCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public HvalsCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() != 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'hvals'");
        }
        String key = new String(args.get(1));
        Object result = kvStore.hvals(key);
        if (result instanceof String err) {
            return new RespMessage.Error(err);
        }
        List<byte[]> vals = (List<byte[]>) result;
        List<RespMessage> elements = vals.stream()
                .map(b -> (RespMessage) new RespMessage.BulkString(b))
                .toList();
        return new RespMessage.RespArray(elements);
    }

    @Override
    public String name() {
        return "HVALS";
    }
}
