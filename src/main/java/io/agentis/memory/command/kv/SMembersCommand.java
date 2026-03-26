package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.command.server.HelloCommand;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

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
                Integer version = ctx != null ? ctx.channel().attr(HelloCommand.PROTOCOL_VERSION).get() : 2;
                if (version != null && version == 3) {
                    return new RespMessage.RespSet(java.util.Set.of());
                }
                return new RespMessage.RespArray(List.of());
            }

            Integer version = ctx != null ? ctx.channel().attr(HelloCommand.PROTOCOL_VERSION).get() : 2;
            if (version != null && version == 3) {
                java.util.Set<RespMessage> elements = sv.members().stream()
                        .map(m -> (RespMessage) new RespMessage.BulkString(m.getBytes(StandardCharsets.UTF_8)))
                        .collect(Collectors.toSet());
                return new RespMessage.RespSet(elements);
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
