package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

// SET key value [EX seconds | PX milliseconds | EXAT unix-time-seconds | PXAT unix-time-milliseconds] [NX | XX] [GET]
@Singleton
public class SetCommand implements CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(SetCommand.class);

    private final KvStore kvStore;

    @Inject
    public SetCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        String command = new String(args.get(0)).toUpperCase();

        if (command.equals("SETEX")) {
            return handleSetex(args);
        }

        return handleSet(args);
    }

    private RespMessage handleSetex(List<byte[]> args) {
        if (args.size() < 4) {
            return new RespMessage.Error("ERR wrong number of arguments for 'SETEX'");
        }
        String key = new String(args.get(1));
        long ttlSeconds;
        try {
            ttlSeconds = Long.parseLong(new String(args.get(2)));
            if (ttlSeconds <= 0) {
                return new RespMessage.Error("ERR invalid expire time in 'SETEX'");
            }
        } catch (NumberFormatException nfe) {
            return new RespMessage.Error("ERR value is not an integer or out of range");
        }
        byte[] value = args.get(3);
        kvStore.set(key, value, ttlSeconds);
        return new RespMessage.SimpleString("OK");
    }

    private RespMessage handleSet(List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'SET'");
        }

        String key = new String(args.get(1));
        byte[] value = args.get(2);

        long ttlMillis = -1;
        boolean nx = false;
        boolean xx = false;
        boolean get = false;

        int i = 3;
        while (i < args.size()) {
            String opt = new String(args.get(i)).toUpperCase();
            switch (opt) {
                case "EX" -> {
                    if (i + 1 >= args.size()) {
                        return new RespMessage.Error("ERR syntax error");
                    }
                    try {
                        long secs = Long.parseLong(new String(args.get(i + 1)));
                        if (secs <= 0) {
                            return new RespMessage.Error("ERR invalid expire time in 'SET'");
                        }
                        ttlMillis = secs * 1000L;
                    } catch (NumberFormatException nfe) {
                        return new RespMessage.Error("ERR value is not an integer or out of range");
                    }
                    i += 2;
                }
                case "PX" -> {
                    if (i + 1 >= args.size()) {
                        return new RespMessage.Error("ERR syntax error");
                    }
                    try {
                        long ms = Long.parseLong(new String(args.get(i + 1)));
                        if (ms <= 0) {
                            return new RespMessage.Error("ERR invalid expire time in 'SET'");
                        }
                        ttlMillis = ms;
                    } catch (NumberFormatException nfe) {
                        return new RespMessage.Error("ERR value is not an integer or out of range");
                    }
                    i += 2;
                }
                case "EXAT" -> {
                    if (i + 1 >= args.size()) {
                        return new RespMessage.Error("ERR syntax error");
                    }
                    try {
                        long unixSecs = Long.parseLong(new String(args.get(i + 1)));
                        if (unixSecs <= 0) {
                            return new RespMessage.Error("ERR invalid expire time in 'SET'");
                        }
                        ttlMillis = unixSecs * 1000L - System.currentTimeMillis();
                        if (ttlMillis <= 0) ttlMillis = 1; // already expired → set with 1ms so it expires immediately
                    } catch (NumberFormatException nfe) {
                        return new RespMessage.Error("ERR value is not an integer or out of range");
                    }
                    i += 2;
                }
                case "PXAT" -> {
                    if (i + 1 >= args.size()) {
                        return new RespMessage.Error("ERR syntax error");
                    }
                    try {
                        long unixMs = Long.parseLong(new String(args.get(i + 1)));
                        if (unixMs <= 0) {
                            return new RespMessage.Error("ERR invalid expire time in 'SET'");
                        }
                        ttlMillis = unixMs - System.currentTimeMillis();
                        if (ttlMillis <= 0) ttlMillis = 1;
                    } catch (NumberFormatException nfe) {
                        return new RespMessage.Error("ERR value is not an integer or out of range");
                    }
                    i += 2;
                }
                case "NX" -> {
                    nx = true;
                    i++;
                }
                case "XX" -> {
                    xx = true;
                    i++;
                }
                case "GET" -> {
                    get = true;
                    i++;
                }
                default -> {
                    return new RespMessage.Error("ERR syntax error");
                }
            }
        }

        if (nx && xx) {
            return new RespMessage.Error("ERR syntax error");
        }

        // Simple path: no NX/XX/GET — unconditional set with millis-precision TTL
        if (!nx && !xx && !get) {
            kvStore.setConditional(key, value, ttlMillis, false, false);
            return new RespMessage.SimpleString("OK");
        }

        KvStore.SetResult result = kvStore.setConditional(key, value, ttlMillis, nx, xx);

        // GET option: return previous value regardless of whether condition was met
        if (get) {
            if (!result.conditionMet()) {
                // Condition not met — key unchanged; return current value (which is the "old" value)
                byte[] current = kvStore.get(key);
                return current == null ? new RespMessage.NullBulkString() : new RespMessage.BulkString(current);
            }
            return result.previousValue() == null
                    ? new RespMessage.NullBulkString()
                    : new RespMessage.BulkString(result.previousValue());
        }

        if (!result.conditionMet()) {
            return new RespMessage.NullBulkString(); // nil — condition not satisfied
        }

        return new RespMessage.SimpleString("OK");
    }

    @Override
    public String name() {
        return "SET";
    }

    @Override
    public List<String> aliases() {
        return List.of("SETEX");
    }

    @Override
    public boolean isWriteCommand() {
        return true;
    }
}
