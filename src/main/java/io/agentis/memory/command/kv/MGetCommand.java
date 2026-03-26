package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.command.server.HelloCommand;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

// MGET key [key ...]
@Singleton
public class MGetCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public MGetCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'MGET'");
        }
        Integer version = conn != null ? (Integer) conn.getAttribute(HelloCommand.PROTOCOL_VERSION) : 2;
        List<RespMessage> results = new ArrayList<>();
        for (int i = 1; i < args.size(); i++) {
            String key = new String(args.get(i));
            byte[] value = kvStore.get(key);
            if (value == null) {
                if (version != null && version == 3) {
                    results.add(new RespMessage.Null());
                } else {
                    results.add(new RespMessage.NullBulkString());
                }
            } else {
                results.add(new RespMessage.BulkString(value));
            }
        }
        return new RespMessage.RespArray(results);
    }

    @Override
    public String name() {
        return "MGET";
    }
}
