package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// HLEN key
@Singleton
public class HlenCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public HlenCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() != 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'hlen'");
        }
        String key = new String(args.get(1));
        Object result = kvStore.hlen(key);
        if (result instanceof String err) {
            return new RespMessage.Error(err);
        }
        return new RespMessage.RespInteger((Long) result);
    }

    @Override
    public String name() {
        return "HLEN";
    }
}
