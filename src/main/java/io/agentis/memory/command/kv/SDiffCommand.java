package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// SDIFF key [key ...]
@Singleton
public class SDiffCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public SDiffCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'SDIFF'");
        }
        try {
            // Start with members of the first key
            String firstKey = new String(args.get(1), StandardCharsets.UTF_8);
            StoreValue.SetValue firstSv = kvStore.getSet(firstKey);
            Set<String> result = firstSv == null ? new HashSet<>() : new HashSet<>(firstSv.members());

            // Subtract members of subsequent keys
            for (int i = 2; i < args.size(); i++) {
                if (result.isEmpty()) break;
                String key = new String(args.get(i), StandardCharsets.UTF_8);
                StoreValue.SetValue sv = kvStore.getSet(key);
                if (sv != null) result.removeAll(sv.members());
            }
            List<RespMessage> elements = result.stream()
                    .map(m -> (RespMessage) new RespMessage.BulkString(m.getBytes(StandardCharsets.UTF_8)))
                    .toList();
            return new RespMessage.RespArray(elements);
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error(e.getMessage());
        }
    }

    @Override
    public String name() {
        return "SDIFF";
    }
}
