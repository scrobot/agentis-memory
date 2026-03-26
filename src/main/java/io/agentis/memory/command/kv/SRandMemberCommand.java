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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

// SRANDMEMBER key [count]
@Singleton
public class SRandMemberCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public SRandMemberCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'SRANDMEMBER'");
        }
        String key = new String(args.get(1), StandardCharsets.UTF_8);
        try {
            StoreValue.SetValue sv = kvStore.getSet(key);

            // no count: return single random member or nil
            if (args.size() == 2) {
                if (sv == null || sv.members().isEmpty()) return new RespMessage.NullBulkString();
                List<String> list = new ArrayList<>(sv.members());
                String picked = list.get(ThreadLocalRandom.current().nextInt(list.size()));
                return new RespMessage.BulkString(picked.getBytes(StandardCharsets.UTF_8));
            }

            int count;
            try {
                count = Integer.parseInt(new String(args.get(2), StandardCharsets.UTF_8));
            } catch (NumberFormatException nfe) {
                return new RespMessage.Error("ERR value is not an integer or out of range");
            }

            if (sv == null || sv.members().isEmpty()) {
                return new RespMessage.RespArray(List.of());
            }

            List<String> list = new ArrayList<>(sv.members());
            List<RespMessage> result = new ArrayList<>();

            if (count >= 0) {
                // positive: up to count distinct members
                int n = Math.min(count, list.size());
                // shuffle to pick random distinct subset
                java.util.Collections.shuffle(list, new java.util.Random());
                for (int i = 0; i < n; i++) {
                    result.add(new RespMessage.BulkString(list.get(i).getBytes(StandardCharsets.UTF_8)));
                }
            } else {
                // negative: abs(count) members, may repeat
                int n = Math.abs(count);
                for (int i = 0; i < n; i++) {
                    String picked = list.get(ThreadLocalRandom.current().nextInt(list.size()));
                    result.add(new RespMessage.BulkString(picked.getBytes(StandardCharsets.UTF_8)));
                }
            }
            return new RespMessage.RespArray(result);
        } catch (KvStore.WrongTypeException e) {
            return new RespMessage.Error(e.getMessage());
        }
    }

    @Override
    public String name() {
        return "SRANDMEMBER";
    }
}
