package io.agentis.memory.command.list;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// LINDEX key index
@Singleton
public class LIndexCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public LIndexCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'LINDEX'");
        }
        String key = new String(args.get(1));
        int index;
        try {
            index = Integer.parseInt(new String(args.get(2)));
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR value is not an integer or out of range");
        }
        try {
            byte[] val = kvStore.lindex(key, index);
            return val == null ? new RespMessage.NullBulkString() : new RespMessage.BulkString(val);
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error(e.getMessage());
        }
    }

    @Override
    public String name() {
        return "LINDEX";
    }
}
