package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// HMGET key field [field ...]
@Singleton
public class HmgetCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public HmgetCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'hmget'");
        }
        String key = new String(args.get(1));
        List<String> fields = args.subList(2, args.size()).stream()
                .map(String::new)
                .toList();
        Object result = kvStore.hmget(key, fields);
        if (result instanceof String err) {
            return new RespMessage.Error(err);
        }
        List<byte[]> values = (List<byte[]>) result;
        List<RespMessage> elements = values.stream()
                .map(b -> b == null ? (RespMessage) new RespMessage.NullBulkString() : new RespMessage.BulkString(b))
                .toList();
        return new RespMessage.RespArray(elements);
    }

    @Override
    public String name() {
        return "HMGET";
    }
}
