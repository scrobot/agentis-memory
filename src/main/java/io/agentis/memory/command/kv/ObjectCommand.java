package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// OBJECT subcommand [key] — inspect internals of Redis objects
// Subcommands: ENCODING, HELP, REFCOUNT, IDLETIME, FREQ
@Singleton
public class ObjectCommand implements CommandHandler {

    private static final List<RespMessage> HELP_LINES = List.of(
            new RespMessage.BulkString("OBJECT <subcommand> [<arg> [value] [opt] ...]. subcommands are:".getBytes()),
            new RespMessage.BulkString("ENCODING <key>".getBytes()),
            new RespMessage.BulkString("    Return the kind of internal representation the Redis object stored at <key> is using.".getBytes()),
            new RespMessage.BulkString("FREQ <key>".getBytes()),
            new RespMessage.BulkString("    Return the access frequency index of the key. The returned integer is proportional to the logarithm of the recent access frequency.".getBytes()),
            new RespMessage.BulkString("HELP".getBytes()),
            new RespMessage.BulkString("    Return subcommand help summary.".getBytes()),
            new RespMessage.BulkString("IDLETIME <key>".getBytes()),
            new RespMessage.BulkString("    Return the idle time of the key, that is the approximated number of seconds elapsed since the last access to the key.".getBytes()),
            new RespMessage.BulkString("REFCOUNT <key>".getBytes()),
            new RespMessage.BulkString("    Return the reference count of the object stored at <key>.".getBytes())
    );

    private final KvStore kvStore;

    @Inject
    public ObjectCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'OBJECT'");
        }
        String subCmd = new String(args.get(1)).toUpperCase();
        return switch (subCmd) {
            case "HELP" -> new RespMessage.RespArray(HELP_LINES);
            case "ENCODING" -> handleEncoding(args);
            case "REFCOUNT" -> handleRefCount(args);
            case "IDLETIME" -> handleIdleTime(args);
            case "FREQ" -> handleFreq(args);
            default -> new RespMessage.Error("ERR unknown subcommand '" + subCmd + "'. Try OBJECT HELP.");
        };
    }

    private RespMessage handleEncoding(List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'OBJECT|ENCODING'");
        }
        String key = new String(args.get(2));
        KvStore.Entry entry = kvStore.getEntry(key);
        if (entry == null) {
            return new RespMessage.Error("ERR no such key");
        }
        String encoding = switch (entry.value()) {
            case StoreValue.StringValue sv -> {
                // Use embstr for short values, raw for longer
                byte[] raw = sv.raw();
                yield (raw != null && raw.length <= 44) ? "embstr" : "raw";
            }
            case StoreValue.HashValue ignored -> "hashtable";
            case StoreValue.ListValue ignored -> "listpack";
        };
        return new RespMessage.BulkString(encoding.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private RespMessage handleRefCount(List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'OBJECT|REFCOUNT'");
        }
        String key = new String(args.get(2));
        KvStore.Entry entry = kvStore.getEntry(key);
        if (entry == null) {
            return new RespMessage.Error("ERR no such key");
        }
        return new RespMessage.RespInteger(1);
    }

    private RespMessage handleIdleTime(List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'OBJECT|IDLETIME'");
        }
        String key = new String(args.get(2));
        KvStore.Entry entry = kvStore.getEntry(key);
        if (entry == null) {
            return new RespMessage.Error("ERR no such key");
        }
        return new RespMessage.RespInteger(0);
    }

    private RespMessage handleFreq(List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'OBJECT|FREQ'");
        }
        String key = new String(args.get(2));
        KvStore.Entry entry = kvStore.getEntry(key);
        if (entry == null) {
            return new RespMessage.Error("ERR no such key");
        }
        return new RespMessage.RespInteger(0);
    }

    @Override
    public String name() {
        return "OBJECT";
    }
}
