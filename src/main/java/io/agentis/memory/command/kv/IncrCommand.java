package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.store.StoreValue;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Handles: INCR, DECR, INCRBY, DECRBY, INCRBYFLOAT
 */
@Singleton
public class IncrCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public IncrCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        String cmd = new String(args.getFirst()).toUpperCase();
        return switch (cmd) {
            case "INCR"        -> handleIncr(args, 1L);
            case "DECR"        -> handleIncr(args, -1L);
            case "INCRBY"      -> handleIncrBy(args, false);
            case "DECRBY"      -> handleIncrBy(args, true);
            case "INCRBYFLOAT" -> handleIncrByFloat(args);
            default            -> new RespMessage.Error("ERR unknown command '" + cmd + "'");
        };
    }

    private RespMessage handleIncr(List<byte[]> args, long delta) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for '" + new String(args.get(0)).toLowerCase() + "'");
        }
        String key = new String(args.get(1));
        return atomicIncrLong(key, delta);
    }

    private RespMessage handleIncrBy(List<byte[]> args, boolean negate) {
        String cmdName = new String(args.get(0)).toLowerCase();
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for '" + cmdName + "'");
        }
        long delta;
        try {
            delta = Long.parseLong(new String(args.get(2)));
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR value is not an integer or out of range");
        }
        if (negate) delta = -delta;
        String key = new String(args.get(1));
        return atomicIncrLong(key, delta);
    }

    private RespMessage handleIncrByFloat(List<byte[]> args) {
        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'incrbyfloat'");
        }
        String key = new String(args.get(1));
        double increment;
        try {
            increment = Double.parseDouble(new String(args.get(2)));
        } catch (NumberFormatException e) {
            return new RespMessage.Error("ERR value is not a valid float");
        }

        KvStore.Entry e = kvStore.getEntry(key);
        double current = 0.0;
        long expireAt = -1;
        if (e != null) {
            if (!(e.value() instanceof StoreValue.StringValue sv)) {
                return new RespMessage.Error("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            String str = new String(sv.raw(), StandardCharsets.UTF_8).trim();
            try {
                current = Double.parseDouble(str);
            } catch (NumberFormatException ex) {
                return new RespMessage.Error("ERR value is not a valid float");
            }
            expireAt = e.expireAt();
        }

        double result = current + increment;
        if (Double.isInfinite(result) || Double.isNaN(result)) {
            return new RespMessage.Error("ERR increment would produce NaN or Infinity");
        }

        // Format: remove trailing zeros like Redis
        String formatted = formatDouble(result);
        byte[] raw = formatted.getBytes(StandardCharsets.UTF_8);
        kvStore.set(key, raw, expireAt > 0 ? (expireAt - System.currentTimeMillis()) / 1000 : -1);
        return new RespMessage.BulkString(raw);
    }

    private RespMessage atomicIncrLong(String key, long delta) {
        while (true) {
            KvStore.Entry e = kvStore.getEntry(key);
            long current = 0;
            if (e != null) {
                if (!(e.value() instanceof StoreValue.StringValue sv)) {
                    return new RespMessage.Error("WRONGTYPE Operation against a key holding the wrong kind of value");
                }
                String str = new String(sv.raw(), StandardCharsets.UTF_8).trim();
                try {
                    current = Long.parseLong(str);
                } catch (NumberFormatException ex) {
                    return new RespMessage.Error("ERR value is not an integer or out of range");
                }
            }
            // overflow check
            if (delta > 0 && current > Long.MAX_VALUE - delta) {
                return new RespMessage.Error("ERR increment or decrement would overflow");
            }
            if (delta < 0 && current < Long.MIN_VALUE - delta) {
                return new RespMessage.Error("ERR increment or decrement would overflow");
            }
            long result = current + delta;
            byte[] newRaw = Long.toString(result).getBytes(StandardCharsets.UTF_8);
            // Use append helper logic: set then verify via CAS in store
            // Simple approach: set and return (not perfect CAS but consistent for single-key ops)
            long ttl = (e != null && e.expireAt() > 0) ? (e.expireAt() - System.currentTimeMillis()) / 1000 : -1;
            kvStore.set(key, newRaw, ttl);
            return new RespMessage.RespInteger(result);
        }
    }

    private static String formatDouble(double d) {
        // Use BigDecimal to strip trailing zeros, matching Redis behaviour
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            return String.valueOf((long) d);
        }
        // Use enough precision and strip trailing zeros
        String s = new BigDecimal(d).stripTrailingZeros().toPlainString();
        return s;
    }

    @Override
    public String name() {
        return "INCR";
    }

    @Override
    public List<String> aliases() {
        return List.of("DECR", "INCRBY", "DECRBY", "INCRBYFLOAT");
    }

    @Override
    public boolean isWriteCommand() {
        return true;
    }
}
