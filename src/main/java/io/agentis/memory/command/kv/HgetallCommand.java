package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.command.server.HelloCommand;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// HGETALL key
@Singleton
public class HgetallCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public HgetallCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() != 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'hgetall'");
        }
        String key = new String(args.get(1));
        Object result = kvStore.hgetall(key);
        if (result instanceof String err) {
            return new RespMessage.Error(err);
        }

        List<byte[]> flat = (List<byte[]>) result;
        Integer version = ctx != null ? ctx.channel().attr(HelloCommand.PROTOCOL_VERSION).get() : 2;
        if (version != null && version == 3) {
            Map<RespMessage, RespMessage> map = new LinkedHashMap<>();
            for (int i = 0; i < flat.size(); i += 2) {
                map.put(new RespMessage.BulkString(flat.get(i)), new RespMessage.BulkString(flat.get(i + 1)));
            }
            return new RespMessage.RespMap(map);
        }

        List<RespMessage> elements = flat.stream()
                .map(b -> (RespMessage) new RespMessage.BulkString(b))
                .toList();
        return new RespMessage.RespArray(elements);
    }

    @Override
    public String name() {
        return "HGETALL";
    }
}
