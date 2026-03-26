package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// HKEYS key
@Singleton
public class HkeysCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public HkeysCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() != 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'hkeys'");
        }
        String key = new String(args.get(1));
        Object result = kvStore.hkeys(key);
        if (result instanceof String err) {
            return new RespMessage.Error(err);
        }
        List<byte[]> keys = (List<byte[]>) result;
        List<RespMessage> elements = keys.stream()
                .map(b -> (RespMessage) new RespMessage.BulkString(b))
                .toList();
        return new RespMessage.RespArray(elements);
    }

    @Override
    public String name() {
        return "HKEYS";
    }
}
