package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

// FLUSHDB [ASYNC|SYNC]
// Deletes all keys. ASYNC option is ignored (always sync for MVP).
@Singleton
public class FlushDbCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public FlushDbCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        kvStore.getStore().clear();
        return new RespMessage.SimpleString("OK");
    }

    @Override
    public String name() {
        return "FLUSHDB";
    }

    @Override
    public boolean isWriteCommand() {
        return true;
    }
}
