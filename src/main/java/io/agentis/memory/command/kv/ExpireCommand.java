package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// EXPIRE key seconds — sets TTL; :1 if key exists, :0 if not
@Singleton
public class ExpireCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public ExpireCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'EXPIRE'");
        }
        String key = new String(args.get(1));
        long seconds;
        try {
            seconds = Long.parseLong(new String(args.get(2)));
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR value is not an integer or out of range");
        }
        return kvStore.expire(key, seconds)
                ? new RespMessage.RespInteger(1)
                : new RespMessage.RespInteger(0);
    }

    @Override
    public String name() {
        return "EXPIRE";
    }
}
