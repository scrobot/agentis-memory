package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

// RANDOMKEY — returns a random key from the database, or nil if empty
@Singleton
public class RandomKeyCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public RandomKeyCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        // Collect non-expired keys
        List<String> liveKeys = new ArrayList<>();
        kvStore.getStore().forEach((key, entry) -> {
            if (!entry.isExpired()) {
                liveKeys.add(key);
            }
        });

        if (liveKeys.isEmpty()) {
            return new RespMessage.NullBulkString();
        }
        String randomKey = liveKeys.get(ThreadLocalRandom.current().nextInt(liveKeys.size()));
        return new RespMessage.BulkString(randomKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public String name() {
        return "RANDOMKEY";
    }
}
