package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// FLUSHALL [ASYNC|SYNC]
// Same as FLUSHDB for single-database implementation. ASYNC option ignored.
@Singleton
public class FlushAllCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public FlushAllCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        kvStore.getStore().clear();
        return new RespMessage.SimpleString("OK");
    }

    @Override
    public String name() {
        return "FLUSHALL";
    }
}
