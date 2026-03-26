package io.agentis.memory.command.list;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// LSET key index element
@Singleton
public class LSetCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public LSetCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 4) {
            return new RespMessage.Error("ERR wrong number of arguments for 'LSET'");
        }
        String key = new String(args.get(1));
        int index;
        try {
            index = Integer.parseInt(new String(args.get(2)));
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR value is not an integer or out of range");
        }
        byte[] element = args.get(3);
        try {
            boolean found = kvStore.lset(key, index, element);
            if (!found) return new RespMessage.Error("ERR no such key");
            return new RespMessage.SimpleString("OK");
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error(e.getMessage());
        } catch (IndexOutOfBoundsException e) {
            return new RespMessage.Error(e.getMessage());
        }
    }

    @Override
    public String name() {
        return "LSET";
    }
}
