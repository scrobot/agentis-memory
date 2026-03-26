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

// ZCARD key
@Singleton
public class ZCardCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public ZCardCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'ZCARD'");
        }
        String key = new String(args.get(1), StandardCharsets.UTF_8);
        KvStore.Entry entry = kvStore.getEntry(key);
        if (entry == null) {
            return new RespMessage.RespInteger(0);
        }
        if (!(entry.value() instanceof StoreValue.SortedSetValue sv)) {
            return ZSetUtil.WRONGTYPE;
        }
        return new RespMessage.RespInteger(sv.memberToScore().size());
    }

    @Override
    public String name() {
        return "ZCARD";
    }
}
