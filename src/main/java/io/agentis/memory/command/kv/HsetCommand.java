package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// HSET key field value [field value ...]
// HMSET key field value [field value ...]  (alias, returns OK instead of count)
@Singleton
public class HsetCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public HsetCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        String cmd = new String(args.get(0)).toUpperCase();
        if (args.size() < 4 || (args.size() - 2) % 2 != 0) {
            return new RespMessage.Error("ERR wrong number of arguments for '" + cmd.toLowerCase() + "'");
        }
        String key = new String(args.get(1));
        List<byte[]> fieldValues = args.subList(2, args.size());
        Object result = kvStore.hset(key, fieldValues);
        if (result instanceof String err) {
            return new RespMessage.Error(err);
        }
        if (cmd.equals("HMSET")) {
            return new RespMessage.SimpleString("OK");
        }
        return new RespMessage.RespInteger((Long) result);
    }

    @Override
    public String name() {
        return "HSET";
    }

    @Override
    public List<String> aliases() {
        return List.of("HMSET");
    }

    @Override
    public boolean isWriteCommand() {
        return true;
    }
}
