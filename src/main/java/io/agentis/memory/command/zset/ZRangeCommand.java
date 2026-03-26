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
import java.util.NavigableMap;
import java.util.TreeSet;

// ZRANGE key min max [BYSCORE|BYLEX] [REV] [LIMIT offset count] [WITHSCORES]
// ZREVRANGE key start stop [WITHSCORES]  (legacy, descending by index)
@Singleton
public class ZRangeCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public ZRangeCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 4) {
            String cmd = new String(args.get(0), StandardCharsets.UTF_8).toUpperCase();
            return new RespMessage.Error("ERR wrong number of arguments for '" + cmd + "'");
        }

        boolean isZRevRange = new String(args.get(0), StandardCharsets.UTF_8).equalsIgnoreCase("ZREVRANGE");
        String key = new String(args.get(1), StandardCharsets.UTF_8);
        String minArg = new String(args.get(2), StandardCharsets.UTF_8);
        String maxArg = new String(args.get(3), StandardCharsets.UTF_8);

        KvStore.Entry entry = kvStore.getEntry(key);
        if (entry == null) {
            return new RespMessage.RespArray(List.of());
        }
        if (!(entry.value() instanceof StoreValue.SortedSetValue sv)) {
            return ZSetUtil.WRONGTYPE;
        }

        if (isZRevRange) {
            return handleZRevRange(sv, minArg, maxArg, args);
        }

        // Parse ZRANGE options
        boolean byScore = false, byLex = false, rev = false, withScores = false;
        int limitOffset = 0, limitCount = -1;
        boolean hasLimit = false;

        int i = 4;
        while (i < args.size()) {
            String opt = new String(args.get(i), StandardCharsets.UTF_8).toUpperCase();
            switch (opt) {
                case "BYSCORE" -> { byScore = true; i++; }
                case "BYLEX" -> { byLex = true; i++; }
                case "REV" -> { rev = true; i++; }
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

        if (byScore && byLex) {
            return new RespMessage.Error("ERR syntax error");
        }
        if (hasLimit && !byScore && !byLex) {
            return new RespMessage.Error("ERR syntax error, LIMIT is only supported in combination with BYSCORE and BYLEX");
        }

        List<String> members;
        List<Double> scores;

        if (byScore) {
            // If REV, args are max min (swap)
            String lo = rev ? maxArg : minArg;
            String hi = rev ? minArg : maxArg;
            double minScore, maxScore;
            try {
                minScore = ZSetUtil.parseScore(lo);
                maxScore = ZSetUtil.parseScore(hi);
            } catch (NumberFormatException e) {
                return new RespMessage.Error("ERR min or max is not a float");
            }
            boolean exMin = ZSetUtil.isExclusive(lo);
            boolean exMax = ZSetUtil.isExclusive(hi);

            members = new ArrayList<>();
            scores = new ArrayList<>();
            collectByScore(sv, minScore, maxScore, exMin, exMax, members, scores);

            if (rev) {
                reverse(members);
                reverse(scores);
            }
            applyLimit(members, scores, hasLimit, limitOffset, limitCount);
        } else if (byLex) {
            String lo = rev ? maxArg : minArg;
            String hi = rev ? minArg : maxArg;
            members = new ArrayList<>();
            scores = new ArrayList<>();
            try {
                collectByLex(sv, lo, hi, members, scores);
            } catch (IllegalArgumentException e) {
                return new RespMessage.Error("ERR " + e.getMessage());
            }
            if (rev) {
                reverse(members);
                reverse(scores);
            }
            applyLimit(members, scores, hasLimit, limitOffset, limitCount);
        } else {
            // Index range
            int size = sv.memberToScore().size();
            int start = parseIndex(minArg, size);
            int stop = parseIndex(maxArg, size);
            if (start < 0) start = 0;
            if (stop >= size) stop = size - 1;

            members = new ArrayList<>();
            scores = new ArrayList<>();
            if (start <= stop) {
                collectByIndex(sv, start, stop, members, scores);
            }
            if (rev) {
                reverse(members);
                reverse(scores);
            }
        }

        return buildResponse(members, scores, withScores);
    }

    // ZREVRANGE: descending by index, WITHSCORES optional
    private RespMessage handleZRevRange(StoreValue.SortedSetValue sv, String startStr, String stopStr, List<byte[]> args) {
        boolean withScores = false;
        for (int i = 4; i < args.size(); i++) {
            if (new String(args.get(i), StandardCharsets.UTF_8).equalsIgnoreCase("WITHSCORES")) {
                withScores = true;
            }
        }
        int size = sv.memberToScore().size();
        int start = parseIndex(startStr, size);
        int stop = parseIndex(stopStr, size);
        if (start < 0) start = 0;
        if (stop >= size) stop = size - 1;

        List<String> members = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        if (start <= stop) {
            collectByIndex(sv, start, stop, members, scores);
        }
        reverse(members);
        reverse(scores);
        return buildResponse(members, scores, withScores);
    }

    private void collectByScore(StoreValue.SortedSetValue sv,
                                double minScore, double maxScore,
                                boolean exMin, boolean exMax,
                                List<String> members, List<Double> scores) {
        NavigableMap<Double, TreeSet<String>> sub = sv.scoreToMembers();
        for (Map.Entry<Double, TreeSet<String>> bucket : sub.entrySet()) {
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
    }

    private void collectByLex(StoreValue.SortedSetValue sv,
                               String minLex, String maxLex,
                               List<String> members, List<Double> scores) {
        // lex range: "[val" inclusive, "(val" exclusive, "-" = -inf, "+" = +inf
        boolean exMin = minLex.startsWith("(");
        boolean exMax = maxLex.startsWith("(");
        String minVal = parseLexBound(minLex);
        String maxVal = parseLexBound(maxLex);

        for (Map.Entry<Double, TreeSet<String>> bucket : sv.scoreToMembers().entrySet()) {
            double s = bucket.getKey();
            for (String m : bucket.getValue()) {
                boolean above = minVal == null || (exMin ? m.compareTo(minVal) > 0 : m.compareTo(minVal) >= 0);
                boolean below = maxVal == null || (exMax ? m.compareTo(maxVal) < 0 : m.compareTo(maxVal) <= 0);
                if (above && below) {
                    members.add(m);
                    scores.add(s);
                }
            }
        }
    }

    private String parseLexBound(String bound) {
        if (bound.equals("-")) return null;  // -inf
        if (bound.equals("+")) return null;  // +inf (handled by direction)
        if (bound.startsWith("[") || bound.startsWith("(")) {
            return bound.substring(1);
        }
        throw new IllegalArgumentException("invalid lex range spec");
    }

    private void collectByIndex(StoreValue.SortedSetValue sv, int start, int stop,
                                List<String> members, List<Double> scores) {
        int idx = 0;
        for (Map.Entry<Double, TreeSet<String>> bucket : sv.scoreToMembers().entrySet()) {
            double s = bucket.getKey();
            for (String m : bucket.getValue()) {
                if (idx > stop) return;
                if (idx >= start) {
                    members.add(m);
                    scores.add(s);
                }
                idx++;
            }
        }
    }

    private int parseIndex(String s, int size) {
        int idx = Integer.parseInt(s);
        if (idx < 0) idx = size + idx;
        return idx;
    }

    private <T> void reverse(List<T> list) {
        for (int i = 0, j = list.size() - 1; i < j; i++, j--) {
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    private void applyLimit(List<String> members, List<Double> scores,
                            boolean hasLimit, int offset, int count) {
        if (!hasLimit) return;
        int size = members.size();
        int from = Math.min(offset, size);
        int to = count < 0 ? size : Math.min(from + count, size);
        List<String> mSlice = new ArrayList<>(members.subList(from, to));
        List<Double> sSlice = new ArrayList<>(scores.subList(from, to));
        members.clear();
        members.addAll(mSlice);
        scores.clear();
        scores.addAll(sSlice);
    }

    private RespMessage buildResponse(List<String> members, List<Double> scores, boolean withScores) {
        List<RespMessage> result = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            result.add(new RespMessage.BulkString(members.get(i).getBytes(StandardCharsets.UTF_8)));
            if (withScores) {
                result.add(new RespMessage.BulkString(ZSetUtil.formatScore(scores.get(i))));
            }
        }
        return new RespMessage.RespArray(result);
    }

    @Override
    public String name() {
        return "ZRANGE";
    }

    @Override
    public List<String> aliases() {
        return List.of("ZREVRANGE");
    }
}
