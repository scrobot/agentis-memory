package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// GETDEL key
@Singleton
public class GetDelCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public GetDelCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'getdel'");
        }
        String key = new String(args.get(1));
        try {
            byte[] value = kvStore.getAndDelete(key);
            if (value == null) {
                return new RespMessage.NullBulkString();
            }
            return new RespMessage.BulkString(value);
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
    }

    @Override
    public String name() {
        return "GETDEL";
    }
}
