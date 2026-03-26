package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// TTL key — returns remaining TTL in seconds, -1 if no TTL, -2 if key doesn't exist
@Singleton
public class TtlCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public TtlCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'TTL'");
        }
        String key = new String(args.get(1));
        KvStore.Entry entry = kvStore.getStore().get(key);
        if (entry == null) {
            return new RespMessage.RespInteger(-2);
        }
        if (entry.isExpired()) {
            kvStore.delete(key);
            return new RespMessage.RespInteger(-2);
        }
        if (entry.expireAt() == -1) {
            return new RespMessage.RespInteger(-1);
        }
        long ttlMs = entry.expireAt() - System.currentTimeMillis();
        long ttlSeconds = Math.max(0, (ttlMs + 999) / 1000);
        return new RespMessage.RespInteger(ttlSeconds);
    }

    @Override
    public String name() {
        return "TTL";
    }
}
