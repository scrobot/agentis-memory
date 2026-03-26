package io.agentis.memory.command.list;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.stream.Collectors;

// LRANGE key start stop
@Singleton
public class LRangeCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public LRangeCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 4) {
            return new RespMessage.Error("ERR wrong number of arguments for 'LRANGE'");
        }
        String key = new String(args.get(1));
        int start, stop;
        try {
            start = Integer.parseInt(new String(args.get(2)));
            stop  = Integer.parseInt(new String(args.get(3)));
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR value is not an integer or out of range");
        }
        try {
            List<byte[]> range = kvStore.lrange(key, start, stop);
            List<RespMessage> elements = range.stream()
                    .map(b -> (RespMessage) new RespMessage.BulkString(b))
                    .collect(Collectors.toList());
            return new RespMessage.RespArray(elements);
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error(e.getMessage());
        }
    }

    @Override
    public String name() {
        return "LRANGE";
    }
}
