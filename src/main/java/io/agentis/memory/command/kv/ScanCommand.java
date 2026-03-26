package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// SCAN cursor [MATCH pattern] [COUNT n]
// Simple offset-based cursor over a snapshot of non-expired keys.
// Cursor 0 = start; returned cursor 0 = iteration complete.
@Singleton
public class ScanCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public ScanCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'SCAN'");
        }
        int cursor;
        try {
            cursor = Integer.parseInt(new String(args.get(1)));
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR value is not an integer or out of range");
        }

        String matchPattern = "*";
        int count = 10;
        for (int i = 2; i < args.size() - 1; i++) {
            String opt = new String(args.get(i)).toUpperCase();
            if (opt.equals("MATCH")) {
                matchPattern = new String(args.get(++i));
            } else if (opt.equals("COUNT")) {
                try {
                    count = Integer.parseInt(new String(args.get(++i)));
                } catch (NumberFormatException e) {
                    return new RespMessage.Error("ERR value is not an integer or out of range");
                }
            }
        }

        Pattern regex = KeysCommand.globToRegex(matchPattern);

        // Snapshot of non-expired keys matching the pattern
        List<String> allKeys = new ArrayList<>();
        for (var entry : kvStore.getStore().entrySet()) {
            if (!entry.getValue().isExpired() && regex.matcher(entry.getKey()).matches()) {
                allKeys.add(entry.getKey());
            }
        }
        allKeys.sort(String::compareTo);

        int from = Math.min(cursor, allKeys.size());
        int to = Math.min(from + count, allKeys.size());
        int nextCursor = (to >= allKeys.size()) ? 0 : to;

        List<RespMessage> keys = new ArrayList<>();
        for (int i = from; i < to; i++) {
            keys.add(new RespMessage.BulkString(allKeys.get(i).getBytes(StandardCharsets.UTF_8)));
        }

        List<RespMessage> result = List.of(
                new RespMessage.BulkString(String.valueOf(nextCursor).getBytes(StandardCharsets.UTF_8)),
                new RespMessage.RespArray(keys)
        );
        return new RespMessage.RespArray(result);
    }

    @Override
    public String name() {
        return "SCAN";
    }
}
