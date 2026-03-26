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

// ZINCRBY key increment member
@Singleton
public class ZIncrByCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public ZIncrByCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 4) {
            return new RespMessage.Error("ERR wrong number of arguments for 'ZINCRBY'");
        }
        String key = new String(args.get(1), StandardCharsets.UTF_8);
        double increment;
        try {
            increment = Double.parseDouble(new String(args.get(2), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR value is not a valid float");
        }
        String member = new String(args.get(3), StandardCharsets.UTF_8);

        KvStore.Entry existing = kvStore.getEntry(key);
        if (existing != null && !(existing.value() instanceof StoreValue.SortedSetValue)) {
            return ZSetUtil.WRONGTYPE;
        }

        StoreValue.SortedSetValue sv = kvStore.getOrCreateSortedSet(key);
        if (sv == null) {
            return ZSetUtil.WRONGTYPE;
        }

        Double current = sv.memberToScore().getOrDefault(member, 0.0);
        double newScore = current + increment;
        ZSetUtil.putMember(sv, member, newScore);
        return new RespMessage.BulkString(ZSetUtil.formatScore(newScore));
    }

    @Override
    public String name() {
        return "ZINCRBY";
    }
}
