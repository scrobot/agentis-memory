package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// PERSIST key — removes TTL from key. Returns 1 if TTL removed, 0 if key has no TTL or does not exist.
@Singleton
public class PersistCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public PersistCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'PERSIST'");
        }
        String key = new String(args.get(1));
        KvStore.Entry entry = kvStore.getEntry(key);
        if (entry == null || entry.expireAt() == -1) {
            return new RespMessage.RespInteger(0);
        }
        // Replace entry with no expiry
        kvStore.getStore().put(key, new KvStore.Entry(entry.value(), entry.createdAt(), -1, entry.hasVectorIndex()));
        return new RespMessage.RespInteger(1);
    }

    @Override
    public String name() {
        return "PERSIST";
    }
}
