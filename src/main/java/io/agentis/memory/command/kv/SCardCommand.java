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

// SCARD key
@Singleton
public class SCardCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public SCardCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'SCARD'");
        }
        String key = new String(args.get(1), StandardCharsets.UTF_8);
        try {
            StoreValue.SetValue sv = kvStore.getSet(key);
            if (sv == null) return new RespMessage.RespInteger(0);
            return new RespMessage.RespInteger(sv.members().size());
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error(e.getMessage());
        }
    }

    @Override
    public String name() {
        return "SCARD";
    }
}
