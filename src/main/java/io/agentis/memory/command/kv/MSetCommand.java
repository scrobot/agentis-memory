package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// MSET key value [key value ...]
@Singleton
public class MSetCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public MSetCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        // args[0] = MSET, then pairs
        if (args.size() < 3 || (args.size() % 2) == 0) {
            return new RespMessage.Error("ERR wrong number of arguments for 'MSET'");
        }
        for (int i = 1; i < args.size(); i += 2) {
            String key = new String(args.get(i));
            byte[] value = args.get(i + 1);
            kvStore.set(key, value, -1);
        }
        return new RespMessage.SimpleString("OK");
    }

    @Override
    public String name() {
        return "MSET";
    }

    @Override
    public boolean isWriteCommand() {
        return true;
    }
}
