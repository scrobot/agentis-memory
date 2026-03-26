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

// SINTER key [key ...]
@Singleton
public class SInterCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public SInterCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'SINTER'");
        }
        try {
            Set<String> result = null;
            for (int i = 1; i < args.size(); i++) {
                String key = new String(args.get(i), StandardCharsets.UTF_8);
                StoreValue.SetValue sv = kvStore.getSet(key);
                if (sv == null) {
                    // intersection with empty set = empty result
                    return new RespMessage.RespArray(List.of());
                }
                if (result == null) {
                    result = new HashSet<>(sv.members());
                } else {
                    result.retainAll(sv.members());
                }
                if (result.isEmpty()) return new RespMessage.RespArray(List.of());
            }
            if (result == null) return new RespMessage.RespArray(List.of());
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
        return "SINTER";
    }
}
