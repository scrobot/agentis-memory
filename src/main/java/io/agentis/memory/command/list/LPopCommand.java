package io.agentis.memory.command.list;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.stream.Collectors;

// LPOP key [count]
@Singleton
public class LPopCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public LPopCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'LPOP'");
        }
        String key = new String(args.get(1));
        boolean withCount = args.size() >= 3;
        int count = 1;
        if (withCount) {
            try {
                count = Integer.parseInt(new String(args.get(2)));
                if (count < 0) return new RespMessage.Error("ERR value is not an integer or out of range");
            } catch (NumberFormatException e) {
                return new RespMessage.Error("ERR value is not an integer or out of range");
            }
        }
        try {
            List<byte[]> popped = kvStore.lpop(key, count);
            if (popped == null) {
                return withCount ? new RespMessage.RespArray(null) : new RespMessage.NullBulkString();
            }
            if (!withCount) {
                return new RespMessage.BulkString(popped.get(0));
            }
            List<RespMessage> elements = popped.stream()
                    .map(b -> (RespMessage) new RespMessage.BulkString(b))
                    .collect(Collectors.toList());
            return new RespMessage.RespArray(elements);
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error(e.getMessage());
        }
    }

    @Override
    public String name() {
        return "LPOP";
    }
}
