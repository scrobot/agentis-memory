package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// EXISTS key — returns :1 if exists (lazy expiry applied), :0 if not
@Singleton
public class ExistsCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public ExistsCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'EXISTS'");
        }
        String key = new String(args.get(1));
        // get() applies lazy expiry
        return kvStore.get(key) != null
                ? new RespMessage.RespInteger(1)
                : new RespMessage.RespInteger(0);
    }

    @Override
    public String name() {
        return "EXISTS";
    }
}
