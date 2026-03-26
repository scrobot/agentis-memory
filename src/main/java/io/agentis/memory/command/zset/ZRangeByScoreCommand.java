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

// ZRANGEBYSCORE key min max [WITHSCORES] [LIMIT offset count]
// ZREVRANGEBYSCORE key max min [WITHSCORES] [LIMIT offset count]
@Singleton
public class ZRangeByScoreCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public ZRangeByScoreCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 4) {
            String cmd = new String(args.get(0), StandardCharsets.UTF_8).toUpperCase();
            return new RespMessage.Error("ERR wrong number of arguments for '" + cmd + "'");
        }
        boolean rev = new String(args.get(0), StandardCharsets.UTF_8).equalsIgnoreCase("ZREVRANGEBYSCORE");
        String key = new String(args.get(1), StandardCharsets.UTF_8);
        // For ZREVRANGEBYSCORE: args are max min (note reversed order)
        String firstArg = new String(args.get(2), StandardCharsets.UTF_8);
        String secondArg = new String(args.get(3), StandardCharsets.UTF_8);

        String minStr = rev ? secondArg : firstArg;
        String maxStr = rev ? firstArg : secondArg;

        double minScore, maxScore;
        try {
            minScore = ZSetUtil.parseScore(minStr);
            maxScore = ZSetUtil.parseScore(maxStr);
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR min or max is not a float");
        }
        boolean exMin = ZSetUtil.isExclusive(minStr);
        boolean exMax = ZSetUtil.isExclusive(maxStr);

        boolean withScores = false;
        int limitOffset = 0, limitCount = -1;
        boolean hasLimit = false;

        int i = 4;
        while (i < args.size()) {
            String opt = new String(args.get(i), StandardCharsets.UTF_8).toUpperCase();
            switch (opt) {
                case "WITHSCORES" -> { withScores = true; i++; }
                case "LIMIT" -> {
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
                }
                default -> { return new RespMessage.Error("ERR syntax error"); }
            }
        }

        KvStore.Entry entry = kvStore.getEntry(key);
        if (entry == null) {
            return new RespMessage.RespArray(List.of());
        }
        if (!(entry.value() instanceof StoreValue.SortedSetValue sv)) {
            return ZSetUtil.WRONGTYPE;
        }

        List<String> members = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (Map.Entry<Double, TreeSet<String>> bucket : sv.scoreToMembers().entrySet()) {
            double s = bucket.getKey();
            boolean aboveMin = exMin ? s > minScore : s >= minScore;
            boolean belowMax = exMax ? s < maxScore : s <= maxScore;
            if (aboveMin && belowMax) {
                for (String m : bucket.getValue()) {
                    members.add(m);
                    scores.add(s);
                }
            } else if (s > maxScore) {
                break;
            }
        }

        if (rev) {
            for (int l = 0, r = members.size() - 1; l < r; l++, r--) {
                String tmp = members.get(l); members.set(l, members.get(r)); members.set(r, tmp);
                Double ts = scores.get(l); scores.set(l, scores.get(r)); scores.set(r, ts);
            }
        }

        if (hasLimit) {
            int size = members.size();
            int from = Math.min(limitOffset, size);
            int to = limitCount < 0 ? size : Math.min(from + limitCount, size);
            members = new ArrayList<>(members.subList(from, to));
            scores = new ArrayList<>(scores.subList(from, to));
        }

        List<RespMessage> result = new ArrayList<>();
        for (int j = 0; j < members.size(); j++) {
            result.add(new RespMessage.BulkString(members.get(j).getBytes(StandardCharsets.UTF_8)));
            if (withScores) {
                result.add(new RespMessage.BulkString(ZSetUtil.formatScore(scores.get(j))));
            }
        }
        return new RespMessage.RespArray(result);
    }

    @Override
    public String name() {
        return "ZRANGEBYSCORE";
    }

    @Override
    public List<String> aliases() {
        return List.of("ZREVRANGEBYSCORE");
    }
}
