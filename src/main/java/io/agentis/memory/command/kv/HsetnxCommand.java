package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// HSETNX key field value
@Singleton
public class HsetnxCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public HsetnxCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() != 4) {
            return new RespMessage.Error("ERR wrong number of arguments for 'hsetnx'");
        }
        String key = new String(args.get(1));
        String field = new String(args.get(2));
        byte[] value = args.get(3);
        Object result = kvStore.hsetnx(key, field, value);
        if (result instanceof String err) {
            return new RespMessage.Error(err);
        }
        return new RespMessage.RespInteger((Long) result);
    }

    @Override
    public String name() {
        return "HSETNX";
    }
}
