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

// ZCOUNT key min max
@Singleton
public class ZCountCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public ZCountCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 4) {
            return new RespMessage.Error("ERR wrong number of arguments for 'ZCOUNT'");
        }
        String key = new String(args.get(1), StandardCharsets.UTF_8);
        String minStr = new String(args.get(2), StandardCharsets.UTF_8);
        String maxStr = new String(args.get(3), StandardCharsets.UTF_8);

        double min, max;
        try {
            min = ZSetUtil.parseScore(minStr);
            max = ZSetUtil.parseScore(maxStr);
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR min or max is not a float");
        }
        boolean exMin = ZSetUtil.isExclusive(minStr);
        boolean exMax = ZSetUtil.isExclusive(maxStr);

        KvStore.Entry entry = kvStore.getEntry(key);
        if (entry == null) {
            return new RespMessage.RespInteger(0);
        }
        if (!(entry.value() instanceof StoreValue.SortedSetValue sv)) {
            return ZSetUtil.WRONGTYPE;
        }

        long count = 0;
        for (Map.Entry<Double, TreeSet<String>> bucket : sv.scoreToMembers().entrySet()) {
            double s = bucket.getKey();
            boolean aboveMin = exMin ? s > min : s >= min;
            boolean belowMax = exMax ? s < max : s <= max;
            if (aboveMin && belowMax) {
                count += bucket.getValue().size();
            } else if (s > max) {
                break;
            }
        }
        return new RespMessage.RespInteger(count);
    }

    @Override
    public String name() {
        return "ZCOUNT";
    }
}
