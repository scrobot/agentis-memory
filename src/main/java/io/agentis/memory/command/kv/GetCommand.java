package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.command.server.HelloCommand;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// GET key
@Singleton
public class GetCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public GetCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'GET'");
        }
        String key = new String(args.get(1));
        byte[] value = kvStore.get(key);
        if (value == null) {
            Integer version = conn != null ? (Integer) conn.getAttribute(HelloCommand.PROTOCOL_VERSION) : 2;
            if (version != null && version == 3) {
                return new RespMessage.Null();
            }
            return new RespMessage.NullBulkString();
        }
        return new RespMessage.BulkString(value);
    }

    @Override
    public String name() {
        return "GET";
    }
}
