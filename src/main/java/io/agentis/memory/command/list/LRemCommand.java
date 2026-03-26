package io.agentis.memory.command.list;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// LREM key count element
@Singleton
public class LRemCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public LRemCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 4) {
            return new RespMessage.Error("ERR wrong number of arguments for 'LREM'");
        }
        String key = new String(args.get(1));
        int count;
        try {
            count = Integer.parseInt(new String(args.get(2)));
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR value is not an integer or out of range");
        }
        byte[] element = args.get(3);
        try {
            long removed = kvStore.lrem(key, count, element);
            return new RespMessage.RespInteger(removed);
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error(e.getMessage());
        }
    }

    @Override
    public String name() {
        return "LREM";
    }
}
