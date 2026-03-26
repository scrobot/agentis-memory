package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// GETSET key value
@Singleton
public class GetSetCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public GetSetCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'getset'");
        }
        String key = new String(args.get(1));
        byte[] newValue = args.get(2);

        // Check type before writing
        KvStore.Entry existing = kvStore.getEntry(key);
        if (existing != null && !(existing.value() instanceof StoreValue.StringValue)) {
            return new RespMessage.Error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        byte[] old = (existing != null) ? ((StoreValue.StringValue) existing.value()).raw() : null;
        kvStore.set(key, newValue, -1);

        if (old == null) {
            return new RespMessage.NullBulkString();
        }
        return new RespMessage.BulkString(old);
    }

    @Override
    public String name() {
        return "GETSET";
    }
}
