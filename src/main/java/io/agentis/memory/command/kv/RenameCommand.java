package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// RENAME key newkey — renames key to newkey. Overwrites newkey if exists. Preserves type and TTL.
// RENAMENX key newkey — renames only if newkey does not exist. Returns 1 if renamed, 0 if newkey exists.
@Singleton
public class RenameCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public RenameCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        String cmd = new String(args.get(0)).toUpperCase();
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for '" + cmd + "'");
        }
        String key = new String(args.get(1));
        String newKey = new String(args.get(2));

        KvStore.Entry entry = kvStore.getEntry(key);
        if (entry == null) {
            return new RespMessage.Error("ERR no such key");
        }

        if ("RENAMENX".equals(cmd)) {
            KvStore.Entry existing = kvStore.getEntry(newKey);
            if (existing != null) {
                return new RespMessage.RespInteger(0);
            }
        }

        // Move entry: delete old, put new preserving value and TTL
        kvStore.getStore().remove(key);
        kvStore.getStore().put(newKey, entry);
        return "RENAMENX".equals(cmd)
                ? new RespMessage.RespInteger(1)
                : new RespMessage.SimpleString("OK");
    }

    @Override
    public String name() {
        return "RENAME";
    }

    @Override
    public List<String> aliases() {
        return List.of("RENAMENX");
    }
}
