package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

// SET key value [EX seconds]
@Singleton
public class SetCommand implements CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(SetCommand.class);

    private final KvStore kvStore;

    @Inject
    public SetCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        String command = new String(args.get(0)).toUpperCase();
        if (command.equals("SETEX")) {
            if (args.size() < 4) {
                return new RespMessage.Error("ERR wrong number of arguments for 'SETEX'");
            }
            String key = new String(args.get(1));
            try {
                long ttlSeconds = Long.parseLong(new String(args.get(2)));
                byte[] value = args.get(3);
                kvStore.set(key, value, ttlSeconds);
                return new RespMessage.SimpleString("OK");
            } catch (NumberFormatException nfe) {
                return new RespMessage.Error("ERR value is not an integer or out of range");
            }
        }

        if (args.size() < 3) {
            return new RespMessage.Error("ERR wrong number of arguments for 'SET'");
        }
        String key = new String(args.get(1));
        byte[] value = args.get(2);
        long ttlSeconds = -1;

        if (args.size() > 3) {
            for (int i = 3; i < args.size(); i++) {
                String option = new String(args.get(i)).toUpperCase();
                if (option.equals("EX") && i + 1 < args.size()) {
                    try {
                        ttlSeconds = Long.parseLong(new String(args.get(i + 1)));
                        i++;
                    } catch (NumberFormatException nfe) {
                        return new RespMessage.Error("ERR value is not an integer or out of range");
                    }
                }
            }
        }

        kvStore.set(key, value, ttlSeconds);
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
}
