package io.agentis.memory.command.list;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// LTRIM key start stop
@Singleton
public class LTrimCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public LTrimCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 4) {
            return new RespMessage.Error("ERR wrong number of arguments for 'LTRIM'");
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
            kvStore.ltrim(key, start, stop);
            return new RespMessage.SimpleString("OK");
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error(e.getMessage());
        }
    }

    @Override
    public String name() {
        return "LTRIM";
    }
}
