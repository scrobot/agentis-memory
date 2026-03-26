package io.agentis.memory.command.server;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.SnapshotManager;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

@Singleton
public class BgSaveCommand implements CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(BgSaveCommand.class);
    private final SnapshotManager snapshotManager;

    @Inject
    public BgSaveCommand(SnapshotManager snapshotManager) {
        this.snapshotManager = snapshotManager;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        // In Redis, BGSAVE returns immediately and does work in a background process (fork).
        // Here we'll run it in a separate thread if we want it to be truly background.
        // For simplicity, let's just run it in the current thread for now or use a simple thread.
        
        new Thread(() -> {
            try {
                snapshotManager.save();
            } catch (IOException e) {
                log.error("BGSAVE failed", e);
            }
        }, "bgsave-worker").start();

        return new RespMessage.SimpleString("Background saving started");
    }

    @Override
    public String name() {
        return "BGSAVE";
    }
}
