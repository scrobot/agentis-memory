package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// UNLINK key [key ...] — semantically identical to DEL; returns number of keys removed
@Singleton
public class UnlinkCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public UnlinkCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'UNLINK'");
        }
        long removed = 0;
        for (int i = 1; i < args.size(); i++) {
            if (kvStore.delete(new String(args.get(i)))) {
                removed++;
            }
        }
        return new RespMessage.RespInteger(removed);
    }

    @Override
    public String name() {
        return "UNLINK";
    }
}
