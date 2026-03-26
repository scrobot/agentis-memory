package io.agentis.memory.command.zset;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;

// ZADD key [NX|XX] [GT|LT] [CH] [INCR] score member [score member ...]
@Singleton
public class ZAddCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public ZAddCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 4) {
            return new RespMessage.Error("ERR wrong number of arguments for 'ZADD'");
        }
        String key = new String(args.get(1), StandardCharsets.UTF_8);

        // Parse options before score-member pairs
        boolean nx = false, xx = false, gt = false, lt = false, ch = false, incr = false;
        int i = 2;
        while (i < args.size()) {
            String opt = new String(args.get(i), StandardCharsets.UTF_8).toUpperCase();
            switch (opt) {
                case "NX" -> { nx = true; i++; }
                case "XX" -> { xx = true; i++; }
                case "GT" -> { gt = true; i++; }
                case "LT" -> { lt = true; i++; }
                case "CH" -> { ch = true; i++; }
                case "INCR" -> { incr = true; i++; }
                default -> { /* start of score-member pairs */ i = i; i = args.size(); }  // break loop
            }
            if (!opt.equals("NX") && !opt.equals("XX") && !opt.equals("GT") &&
                !opt.equals("LT") && !opt.equals("CH") && !opt.equals("INCR")) {
                break;
            }
        }

        if (nx && xx) {
            return new RespMessage.Error("ERR XX and NX options at the same time are not compatible");
        }
        if (nx && (gt || lt)) {
            return new RespMessage.Error("ERR GT, LT, and NX options at the same time are not compatible");
        }

        // Re-parse properly: find where score-member pairs start
        i = 2;
        loop:
        while (i < args.size()) {
            String opt = new String(args.get(i), StandardCharsets.UTF_8).toUpperCase();
            switch (opt) {
                case "NX", "XX", "GT", "LT", "CH", "INCR" -> i++;
                default -> { break loop; }
            }
        }

        int pairStart = i;
        int remaining = args.size() - pairStart;
        if (remaining == 0 || remaining % 2 != 0) {
            return new RespMessage.Error("ERR syntax error");
        }
        if (incr && remaining != 2) {
            return new RespMessage.Error("ERR INCR option supports a single increment-element pair");
        }

        // Check WRONGTYPE first
        KvStore.Entry existing = kvStore.getEntry(key);
        if (existing != null && !(existing.value() instanceof StoreValue.SortedSetValue)) {
            return ZSetUtil.WRONGTYPE;
        }

        // With XX and no existing key, nothing can be added — short-circuit without creating the key
        if (xx && existing == null) {
            return new RespMessage.RespInteger(0);
        }

        StoreValue.SortedSetValue sv = kvStore.getOrCreateSortedSet(key);
        if (sv == null) {
            return ZSetUtil.WRONGTYPE;
        }

        if (incr) {
            // INCR mode: behaves like ZINCRBY
            double incrAmount;
            try {
                incrAmount = Double.parseDouble(new String(args.get(pairStart), StandardCharsets.UTF_8));
            } catch (NumberFormatException e) {
                return new RespMessage.Error("ERR value is not a valid float");
            }
            String member = new String(args.get(pairStart + 1), StandardCharsets.UTF_8);
            Double current = sv.memberToScore().get(member);

            if (nx && current != null) return new RespMessage.NullBulkString();
            if (xx && current == null) return new RespMessage.NullBulkString();

            double newScore = (current == null ? 0.0 : current) + incrAmount;
            if (gt && current != null && newScore <= current) return new RespMessage.NullBulkString();
            if (lt && current != null && newScore >= current) return new RespMessage.NullBulkString();

            ZSetUtil.putMember(sv, member, newScore);
            return new RespMessage.BulkString(ZSetUtil.formatScore(newScore));
        }

        // Normal ZADD
        long added = 0;
        long changed = 0;
        for (int j = pairStart; j < args.size(); j += 2) {
            double score;
            try {
                score = Double.parseDouble(new String(args.get(j), StandardCharsets.UTF_8));
            } catch (NumberFormatException e) {
                return new RespMessage.Error("ERR value is not a valid float");
            }
            String member = new String(args.get(j + 1), StandardCharsets.UTF_8);
            Double current = sv.memberToScore().get(member);

            if (nx && current != null) continue;
            if (xx && current == null) continue;
            if (gt && current != null && score <= current) continue;
            if (lt && current != null && score >= current) continue;

            boolean wasNew = ZSetUtil.putMember(sv, member, score);
            if (wasNew) {
                added++;
                changed++;
            } else if (current != null && current != score) {
                changed++;
            }
        }

        return new RespMessage.RespInteger(ch ? changed : added);
    }

    @Override
    public String name() {
        return "ZADD";
    }
}
