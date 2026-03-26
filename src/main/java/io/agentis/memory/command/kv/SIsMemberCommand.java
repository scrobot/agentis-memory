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

// SISMEMBER key member
@Singleton
public class SIsMemberCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public SIsMemberCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'SISMEMBER'");
        }
        String key = new String(args.get(1), StandardCharsets.UTF_8);
        String member = new String(args.get(2), StandardCharsets.UTF_8);
        try {
            StoreValue.SetValue sv = kvStore.getSet(key);
            boolean exists = sv != null && sv.members().contains(member);

            Integer version = ctx != null ? ctx.channel().attr(HelloCommand.PROTOCOL_VERSION).get() : 2;
            if (version != null && version == 3) {
                return new RespMessage.Boolean(exists);
            }

            return new RespMessage.RespInteger(exists ? 1 : 0);
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error(e.getMessage());
        }
    }

    @Override
    public String name() {
        return "SISMEMBER";
    }
}
