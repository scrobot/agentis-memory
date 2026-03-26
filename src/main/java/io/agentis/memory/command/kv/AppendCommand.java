package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// APPEND key value
@Singleton
public class AppendCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public AppendCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'append'");
        }
        String key = new String(args.get(1));
        byte[] suffix = args.get(2);
        try {
            int newLen = kvStore.append(key, suffix);
            return new RespMessage.RespInteger(newLen);
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
    }

    @Override
    public String name() {
        return "APPEND";
    }
}
