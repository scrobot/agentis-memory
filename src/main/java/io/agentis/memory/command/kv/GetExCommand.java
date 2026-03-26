package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// GETEX key [EX seconds | PX milliseconds | EXAT timestamp | PXAT timestamp-ms | PERSIST]
@Singleton
public class GetExCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public GetExCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'getex'");
        }
        String key = new String(args.get(1));
        KvStore.Entry e = kvStore.getEntry(key);
        if (e == null) {
            return new RespMessage.NullBulkString();
        }
        if (!(e.value() instanceof StoreValue.StringValue sv)) {
            return new RespMessage.Error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        // Parse options
        if (args.size() > 2) {
            String option = new String(args.get(2)).toUpperCase();
            switch (option) {
                case "EX" -> {
                    if (args.size() < 4) return new RespMessage.Error("ERR syntax error");
                    long seconds = parseLong(args.get(3));
                    if (seconds < 0) return new RespMessage.Error("ERR invalid expire time in 'getex' command");
                    kvStore.expire(key, seconds);
                }
                case "PX" -> {
                    if (args.size() < 4) return new RespMessage.Error("ERR syntax error");
                    long millis = parseLong(args.get(3));
                    if (millis < 0) return new RespMessage.Error("ERR invalid expire time in 'getex' command");
                    kvStore.pexpire(key, millis);
                }
                case "EXAT" -> {
                    if (args.size() < 4) return new RespMessage.Error("ERR syntax error");
                    long epochSeconds = parseLong(args.get(3));
                    if (epochSeconds < 0) return new RespMessage.Error("ERR invalid expire time in 'getex' command");
                    kvStore.expireAt(key, epochSeconds * 1000);
                }
                case "PXAT" -> {
                    if (args.size() < 4) return new RespMessage.Error("ERR syntax error");
                    long epochMs = parseLong(args.get(3));
                    if (epochMs < 0) return new RespMessage.Error("ERR invalid expire time in 'getex' command");
                    kvStore.expireAt(key, epochMs);
                }
                case "PERSIST" -> kvStore.persist(key);
                default -> {
                    return new RespMessage.Error("ERR syntax error");
                }
            }
        }

        return new RespMessage.BulkString(sv.raw());
    }

    private long parseLong(byte[] b) {
        try {
            return Long.parseLong(new String(b));
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }

    @Override
    public String name() {
        return "GETEX";
    }
}
