package io.agentis.memory.command.list;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// LPUSH key element [element ...]
@Singleton
public class LPushCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public LPushCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'LPUSH'");
        }
        String key = new String(args.get(1));
        List<byte[]> elements = args.subList(2, args.size());
        try {
            long len = kvStore.lpush(key, elements);
            return new RespMessage.RespInteger(len);
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error(e.getMessage());
        }
    }

    @Override
    public String name() {
        return "LPUSH";
    }
}
