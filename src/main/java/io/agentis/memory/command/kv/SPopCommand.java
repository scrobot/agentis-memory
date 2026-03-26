package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

// SPOP key [count]
@Singleton
public class SPopCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public SPopCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'SPOP'");
        }
        String key = new String(args.get(1), StandardCharsets.UTF_8);
        try {
            StoreValue.SetValue sv = kvStore.getSet(key);

            // no count: return single random member or nil
            if (args.size() == 2) {
                if (sv == null || sv.members().isEmpty()) return new RespMessage.NullBulkString();
                String popped = popOne(sv);
                kvStore.removeIfEmptySet(key);
                return new RespMessage.BulkString(popped.getBytes(StandardCharsets.UTF_8));
            }

            int count;
            try {
                count = Integer.parseInt(new String(args.get(2), StandardCharsets.UTF_8));
            } catch (NumberFormatException nfe) {
                return new RespMessage.Error("ERR value is not an integer or out of range");
            }
            if (count < 0) {
                return new RespMessage.Error("ERR value is not an integer or out of range");
            }

            if (sv == null || sv.members().isEmpty()) {
                return new RespMessage.RespArray(List.of());
            }

            int n = Math.min(count, sv.members().size());
            List<RespMessage> result = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                String popped = popOne(sv);
                result.add(new RespMessage.BulkString(popped.getBytes(StandardCharsets.UTF_8)));
            }
            kvStore.removeIfEmptySet(key);
            return new RespMessage.RespArray(result);
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error(e.getMessage());
        }
    }

    private String popOne(StoreValue.SetValue sv) {
        List<String> list = new ArrayList<>(sv.members());
        String chosen = list.get(ThreadLocalRandom.current().nextInt(list.size()));
        sv.members().remove(chosen);
        return chosen;
    }

    @Override
    public String name() {
        return "SPOP";
    }
}
