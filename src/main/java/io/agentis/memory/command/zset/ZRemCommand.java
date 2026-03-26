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

// ZREM key member [member ...]
@Singleton
public class ZRemCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public ZRemCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'ZREM'");
        }
        String key = new String(args.get(1), StandardCharsets.UTF_8);
        KvStore.Entry entry = kvStore.getEntry(key);
        if (entry == null) {
            return new RespMessage.RespInteger(0);
        }
        if (!(entry.value() instanceof StoreValue.SortedSetValue sv)) {
            return ZSetUtil.WRONGTYPE;
        }

        long removed = 0;
        for (int i = 2; i < args.size(); i++) {
            String member = new String(args.get(i), StandardCharsets.UTF_8);
            if (ZSetUtil.removeMember(sv, member)) {
                removed++;
            }
        }
        kvStore.deleteIfEmptySortedSet(key);
        return new RespMessage.RespInteger(removed);
    }

    @Override
    public String name() {
        return "ZREM";
    }
}
