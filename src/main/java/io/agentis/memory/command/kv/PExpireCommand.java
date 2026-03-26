package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// PEXPIRE key milliseconds — sets TTL in milliseconds. Returns 1 if set, 0 if key does not exist.
@Singleton
public class PExpireCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public PExpireCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'PEXPIRE'");
        }
        String key = new String(args.get(1));
        long millis;
        try {
            millis = Long.parseLong(new String(args.get(2)));
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR value is not an integer or out of range");
        }

        KvStore.Entry entry = kvStore.getEntry(key);
        if (entry == null) {
            return new RespMessage.RespInteger(0);
        }
        long expireAt = System.currentTimeMillis() + millis;
        kvStore.getStore().put(key, new KvStore.Entry(entry.value(), entry.createdAt(), expireAt, entry.hasVectorIndex()));
        return new RespMessage.RespInteger(1);
    }

    @Override
    public String name() {
        return "PEXPIRE";
    }
}
