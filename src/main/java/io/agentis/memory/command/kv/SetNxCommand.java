package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// SETNX key value
@Singleton
public class SetNxCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public SetNxCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'setnx'");
        }
        String key = new String(args.get(1));
        byte[] value = args.get(2);
        boolean set = kvStore.setNx(key, value);
        return new RespMessage.RespInteger(set ? 1L : 0L);
    }

    @Override
    public String name() {
        return "SETNX";
    }
}
