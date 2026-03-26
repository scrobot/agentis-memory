package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;

// SMEMBERS key
@Singleton
public class SMembersCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public SMembersCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'SMEMBERS'");
        }
        String key = new String(args.get(1), StandardCharsets.UTF_8);
        try {
            StoreValue.SetValue sv = kvStore.getSet(key);
            if (sv == null) {
                return new RespMessage.RespArray(List.of());
            }
            List<RespMessage> elements = sv.members().stream()
                    .map(m -> (RespMessage) new RespMessage.BulkString(m.getBytes(StandardCharsets.UTF_8)))
                    .toList();
            return new RespMessage.RespArray(elements);
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error(e.getMessage());
        }
    }

    @Override
    public String name() {
        return "SMEMBERS";
    }
}
