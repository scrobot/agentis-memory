package io.agentis.memory.command.zset;

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
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

// ZSCAN key cursor [MATCH pattern] [COUNT count]
@Singleton
public class ZScanCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public ZScanCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'ZSCAN'");
        }
        String key = new String(args.get(1), StandardCharsets.UTF_8);
        // cursor is always "0" in this simple implementation (full scan)
        String matchPattern = null;

        int i = 3;
        while (i < args.size()) {
            String opt = new String(args.get(i), StandardCharsets.UTF_8).toUpperCase();
            if (opt.equals("MATCH") && i + 1 < args.size()) {
                matchPattern = new String(args.get(i + 1), StandardCharsets.UTF_8);
                i += 2;
            } else if (opt.equals("COUNT") && i + 1 < args.size()) {
                // COUNT is a hint; we always return all matching in one shot
                i += 2;
            } else {
                return new RespMessage.Error("ERR syntax error");
            }
        }

        KvStore.Entry entry = kvStore.getEntry(key);
        if (entry == null) {
            List<RespMessage> empty = List.of(
                    new RespMessage.BulkString("0".getBytes(StandardCharsets.UTF_8)),
                    new RespMessage.RespArray(List.of()));
            return new RespMessage.RespArray(empty);
        }
        if (!(entry.value() instanceof StoreValue.SortedSetValue sv)) {
            return ZSetUtil.WRONGTYPE;
        }

        Pattern regex = matchPattern != null ? globToRegex(matchPattern) : null;
        List<RespMessage> items = new ArrayList<>();
        for (Map.Entry<Double, TreeSet<String>> bucket : sv.scoreToMembers().entrySet()) {
            for (String m : bucket.getValue()) {
                if (regex == null || regex.matcher(m).matches()) {
                    items.add(new RespMessage.BulkString(m.getBytes(StandardCharsets.UTF_8)));
                    items.add(new RespMessage.BulkString(ZSetUtil.formatScore(bucket.getKey())));
                }
            }
        }

        List<RespMessage> response = List.of(
                new RespMessage.BulkString("0".getBytes(StandardCharsets.UTF_8)),
                new RespMessage.RespArray(items));
        return new RespMessage.RespArray(response);
    }

    private static Pattern globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                case '[' -> sb.append("[");
                case ']' -> sb.append("]");
                case '\\' -> { sb.append("\\\\"); }
                default -> {
                    if ("\\.+^${}()|".indexOf(c) >= 0) sb.append('\\');
                    sb.append(c);
                }
            }
        }
        sb.append("$");
        return Pattern.compile(sb.toString());
    }

    @Override
    public String name() {
        return "ZSCAN";
    }
}
