package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;

// SREM key member [member ...]
@Singleton
public class SRemCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public SRemCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'SREM'");
        }
        String key = new String(args.get(1), StandardCharsets.UTF_8);
        try {
            StoreValue.SetValue sv = kvStore.getSet(key);
            if (sv == null) return new RespMessage.RespInteger(0);
            long removed = 0;
            for (int i = 2; i < args.size(); i++) {
                String member = new String(args.get(i), StandardCharsets.UTF_8);
                if (sv.members().remove(member)) removed++;
            }
            kvStore.removeIfEmptySet(key);
            return new RespMessage.RespInteger(removed);
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error(e.getMessage());
        }
    }

    @Override
    public String name() {
        return "SREM";
    }
}
