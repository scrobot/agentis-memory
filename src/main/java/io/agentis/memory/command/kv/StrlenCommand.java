package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// STRLEN key
@Singleton
public class StrlenCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public StrlenCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'strlen'");
        }
        String key = new String(args.get(1));
        KvStore.Entry e = kvStore.getEntry(key);
        if (e == null) {
            return new RespMessage.RespInteger(0);
        }
        if (!(e.value() instanceof StoreValue.StringValue sv)) {
            return new RespMessage.Error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
        return new RespMessage.RespInteger(sv.raw().length);
    }

    @Override
    public String name() {
        return "STRLEN";
    }
}
