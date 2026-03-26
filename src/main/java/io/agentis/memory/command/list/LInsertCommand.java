package io.agentis.memory.command.list;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// LINSERT key BEFORE|AFTER pivot element
@Singleton
public class LInsertCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public LInsertCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 5) {
            return new RespMessage.Error("ERR wrong number of arguments for 'LINSERT'");
        }
        String key = new String(args.get(1));
        String where = new String(args.get(2)).toUpperCase();
        if (!where.equals("BEFORE") && !where.equals("AFTER")) {
            return new RespMessage.Error("ERR syntax error");
        }
        byte[] pivot   = args.get(3);
        byte[] element = args.get(4);
        try {
            long result = kvStore.linsert(key, where.equals("BEFORE"), pivot, element);
            return new RespMessage.RespInteger(result);
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error(e.getMessage());
        }
    }

    @Override
    public String name() {
        return "LINSERT";
    }
}
