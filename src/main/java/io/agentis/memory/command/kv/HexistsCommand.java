package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// HEXISTS key field
@Singleton
public class HexistsCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public HexistsCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() != 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'hexists'");
        }
        String key = new String(args.get(1));
        String field = new String(args.get(2));
        Object result = kvStore.hexists(key, field);
        if (result instanceof String err) {
            return new RespMessage.Error(err);
        }
        return new RespMessage.RespInteger((Long) result);
    }

    @Override
    public String name() {
        return "HEXISTS";
    }
}
