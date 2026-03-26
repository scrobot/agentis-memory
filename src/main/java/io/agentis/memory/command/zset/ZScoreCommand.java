package io.agentis.memory.command.zset;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.command.server.HelloCommand;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;

// ZSCORE key member
@Singleton
public class ZScoreCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public ZScoreCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'ZSCORE'");
        }
        String key = new String(args.get(1), StandardCharsets.UTF_8);
        String member = new String(args.get(2), StandardCharsets.UTF_8);

        Integer version = conn != null ? (Integer) conn.getAttribute(HelloCommand.PROTOCOL_VERSION) : 2;

        KvStore.Entry entry = kvStore.getEntry(key);
        if (entry == null) {
            return (version != null && version == 3) ? new RespMessage.Null() : new RespMessage.NullBulkString();
        }
        if (!(entry.value() instanceof StoreValue.SortedSetValue sv)) {
            return ZSetUtil.WRONGTYPE;
        }
        Double score = sv.memberToScore().get(member);
        if (score == null) {
            return (version != null && version == 3) ? new RespMessage.Null() : new RespMessage.NullBulkString();
        }

        if (version != null && version == 3) {
            return new RespMessage.Double(score);
        }

        return new RespMessage.BulkString(ZSetUtil.formatScore(score));
    }

    @Override
    public String name() {
        return "ZSCORE";
    }
}
