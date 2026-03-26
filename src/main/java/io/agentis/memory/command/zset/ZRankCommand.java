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
import java.util.Map;
import java.util.TreeSet;

// ZRANK key member  /  ZREVRANK key member
@Singleton
public class ZRankCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public ZRankCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 3) {
            String cmd = new String(args.get(0), StandardCharsets.UTF_8).toUpperCase();
            return new RespMessage.Error("ERR wrong number of arguments for '" + cmd + "'");
        }
        boolean rev = new String(args.get(0), StandardCharsets.UTF_8).equalsIgnoreCase("ZREVRANK");
        String key = new String(args.get(1), StandardCharsets.UTF_8);
        String member = new String(args.get(2), StandardCharsets.UTF_8);

        KvStore.Entry entry = kvStore.getEntry(key);
        if (entry == null) {
            return new RespMessage.NullBulkString();
        }
        if (!(entry.value() instanceof StoreValue.SortedSetValue sv)) {
            return ZSetUtil.WRONGTYPE;
        }
        Double memberScore = sv.memberToScore().get(member);
        if (memberScore == null) {
            return new RespMessage.NullBulkString();
        }

        // Count members with lower score (ascending rank)
        long rank = 0;
        for (Map.Entry<Double, TreeSet<String>> bucket : sv.scoreToMembers().entrySet()) {
            double s = bucket.getKey();
            if (s < memberScore) {
                rank += bucket.getValue().size();
            } else if (s == memberScore) {
                // Within same score, members are ordered lexicographically
                for (String m : bucket.getValue()) {
                    if (m.equals(member)) break;
                    rank++;
                }
                break;
            } else {
                break;
            }
        }

        if (rev) {
            long total = sv.memberToScore().size();
            rank = total - 1 - rank;
        }
        return new RespMessage.RespInteger(rank);
    }

    @Override
    public String name() {
        return "ZRANK";
    }

    @Override
    public List<String> aliases() {
        return List.of("ZREVRANK");
    }
}
