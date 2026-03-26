package io.agentis.memory.command.zset;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

// ZRANGEBYLEX key min max [LIMIT offset count]
@Singleton
public class ZRangeByLexCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public ZRangeByLexCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 4) {
            return new RespMessage.Error("ERR wrong number of arguments for 'ZRANGEBYLEX'");
        }
        String key = new String(args.get(1), StandardCharsets.UTF_8);
        String minLex = new String(args.get(2), StandardCharsets.UTF_8);
        String maxLex = new String(args.get(3), StandardCharsets.UTF_8);

        int limitOffset = 0, limitCount = -1;
        boolean hasLimit = false;

        int i = 4;
        while (i < args.size()) {
            String opt = new String(args.get(i), StandardCharsets.UTF_8).toUpperCase();
            if (opt.equals("LIMIT")) {
                if (i + 2 >= args.size()) {
                    return new RespMessage.Error("ERR syntax error");
                }
                try {
                    limitOffset = Integer.parseInt(new String(args.get(i + 1), StandardCharsets.UTF_8));
                    limitCount = Integer.parseInt(new String(args.get(i + 2), StandardCharsets.UTF_8));
                } catch (NumberFormatException e) {
                    return new RespMessage.Error("ERR value is not an integer or out of range");
                }
                hasLimit = true;
                i += 3;
            } else {
                return new RespMessage.Error("ERR syntax error");
            }
        }

        if (!isValidLexBound(minLex) || !isValidLexBound(maxLex)) {
            return new RespMessage.Error("ERR min or max is not valid string range item");
        }

        KvStore.Entry entry = kvStore.getEntry(key);
        if (entry == null) {
            return new RespMessage.RespArray(List.of());
        }
        if (!(entry.value() instanceof StoreValue.SortedSetValue sv)) {
            return ZSetUtil.WRONGTYPE;
        }

        boolean exMin = minLex.startsWith("(");
        boolean exMax = maxLex.startsWith("(");
        String minVal = minLex.equals("-") ? null : minLex.substring(1);
        String maxVal = maxLex.equals("+") ? null : maxLex.substring(1);

        List<String> members = new ArrayList<>();
        for (Map.Entry<Double, TreeSet<String>> bucket : sv.scoreToMembers().entrySet()) {
            for (String m : bucket.getValue()) {
                boolean above = minVal == null || (exMin ? m.compareTo(minVal) > 0 : m.compareTo(minVal) >= 0);
                boolean below = maxVal == null || (exMax ? m.compareTo(maxVal) < 0 : m.compareTo(maxVal) <= 0);
                if (above && below) {
                    members.add(m);
                }
            }
        }

        if (hasLimit) {
            int size = members.size();
            int from = Math.min(limitOffset, size);
            int to = limitCount < 0 ? size : Math.min(from + limitCount, size);
            members = new ArrayList<>(members.subList(from, to));
        }

        List<RespMessage> result = new ArrayList<>();
        for (String m : members) {
            result.add(new RespMessage.BulkString(m.getBytes(StandardCharsets.UTF_8)));
        }
        return new RespMessage.RespArray(result);
    }

    private boolean isValidLexBound(String s) {
        return s.equals("-") || s.equals("+") || s.startsWith("[") || s.startsWith("(");
    }

    @Override
    public String name() {
        return "ZRANGEBYLEX";
    }
}
