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

// SUNION key [key ...]
@Singleton
public class SUnionCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public SUnionCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'SUNION'");
        }
        try {
            Set<String> result = new HashSet<>();
            for (int i = 1; i < args.size(); i++) {
                String key = new String(args.get(i), StandardCharsets.UTF_8);
                StoreValue.SetValue sv = kvStore.getSet(key);
                if (sv != null) result.addAll(sv.members());
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
        return "SUNION";
    }
}
