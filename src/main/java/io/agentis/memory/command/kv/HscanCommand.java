package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// HSCAN key cursor [MATCH pattern] [COUNT count]
@Singleton
public class HscanCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public HscanCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'hscan'");
        }
        String key = new String(args.get(1));
        int cursor;
        try {
            cursor = Integer.parseInt(new String(args.get(2)));
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR value is not an integer or out of range");
        }
        String matchPattern = null;
        int count = 10;
        for (int i = 3; i < args.size(); i++) {
            String opt = new String(args.get(i)).toUpperCase();
            if (opt.equals("MATCH") && i + 1 < args.size()) {
                matchPattern = new String(args.get(++i));
            } else if (opt.equals("COUNT") && i + 1 < args.size()) {
                try {
                    count = Integer.parseInt(new String(args.get(++i)));
                } catch (NumberFormatException e) {
                    return new RespMessage.Error("ERR value is not an integer or out of range");
                }
            }
        }

        Object result = kvStore.hscan(key, cursor, matchPattern, count);
        if (result instanceof String err) {
            return new RespMessage.Error(err);
        }
        List<?> tuple = (List<?>) result;
        String nextCursor = (String) tuple.get(0);
        List<byte[]> items = (List<byte[]>) tuple.get(1);

        List<RespMessage> itemMessages = items.stream()
                .map(b -> (RespMessage) new RespMessage.BulkString(b))
                .toList();

        return new RespMessage.RespArray(List.of(
                new RespMessage.BulkString(nextCursor.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                new RespMessage.RespArray(itemMessages)
        ));
    }

    @Override
    public String name() {
        return "HSCAN";
    }
}
