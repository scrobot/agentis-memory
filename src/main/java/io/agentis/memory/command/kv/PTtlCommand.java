package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// PTTL key — returns remaining TTL in milliseconds. -1 = no TTL. -2 = key does not exist.
@Singleton
public class PTtlCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public PTtlCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'PTTL'");
        }
        String key = new String(args.get(1));
        KvStore.Entry entry = kvStore.getStore().get(key);
        if (entry == null) {
            return new RespMessage.RespInteger(-2);
        }
        if (entry.isExpired()) {
            kvStore.delete(key);
            return new RespMessage.RespInteger(-2);
        }
        if (entry.expireAt() == -1) {
            return new RespMessage.RespInteger(-1);
        }
        long ttlMs = entry.expireAt() - System.currentTimeMillis();
        return new RespMessage.RespInteger(Math.max(0, ttlMs));
    }

    @Override
    public String name() {
        return "PTTL";
    }
}
