package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// SSCAN key cursor [MATCH pattern] [COUNT count]
@Singleton
public class SScanCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public SScanCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'SSCAN'");
        }
        String key = new String(args.get(1), StandardCharsets.UTF_8);
        int cursor;
        try {
            cursor = Integer.parseInt(new String(args.get(2), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR value is not an integer or out of range");
        }

        String matchPattern = "*";
        int count = 10;
        for (int i = 3; i < args.size() - 1; i++) {
            String opt = new String(args.get(i), StandardCharsets.UTF_8).toUpperCase();
            if (opt.equals("MATCH")) {
                matchPattern = new String(args.get(++i), StandardCharsets.UTF_8);
            } else if (opt.equals("COUNT")) {
                try {
                    count = Integer.parseInt(new String(args.get(++i), StandardCharsets.UTF_8));
                } catch (NumberFormatException e) {
                    return new RespMessage.Error("ERR value is not an integer or out of range");
                }
            }
        }

        try {
            StoreValue.SetValue sv = kvStore.getSet(key);
            if (sv == null) {
                return new RespMessage.RespArray(List.of(
                        new RespMessage.BulkString("0".getBytes(StandardCharsets.UTF_8)),
                        new RespMessage.RespArray(List.of())
                ));
            }

            Pattern regex = KeysCommand.globToRegex(matchPattern);
            List<String> allMembers = new ArrayList<>();
            for (String m : sv.members()) {
                if (regex.matcher(m).matches()) allMembers.add(m);
            }
            allMembers.sort(String::compareTo);

            int from = Math.min(cursor, allMembers.size());
            int to = Math.min(from + count, allMembers.size());
            int nextCursor = (to >= allMembers.size()) ? 0 : to;

            List<RespMessage> members = new ArrayList<>();
            for (int i = from; i < to; i++) {
                members.add(new RespMessage.BulkString(allMembers.get(i).getBytes(StandardCharsets.UTF_8)));
            }

            return new RespMessage.RespArray(List.of(
                    new RespMessage.BulkString(String.valueOf(nextCursor).getBytes(StandardCharsets.UTF_8)),
                    new RespMessage.RespArray(members)
            ));
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error(e.getMessage());
        }
    }

    @Override
    public String name() {
        return "SSCAN";
    }
}
