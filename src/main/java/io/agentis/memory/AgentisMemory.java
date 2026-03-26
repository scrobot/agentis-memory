package io.agentis.memory;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.persistence.AofWriter;
import io.agentis.memory.persistence.SnapshotReader;
import io.agentis.memory.persistence.SnapshotWriter;
import io.agentis.memory.resp.RespServer;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.vector.VectorEngine;

public class AgentisMemory {

    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.parse(args);

        KvStore kvStore = new KvStore(config);
        VectorEngine vectorEngine = new VectorEngine(config);
        AofWriter aofWriter = new AofWriter(config);
        SnapshotWriter snapshotWriter = new SnapshotWriter(config, kvStore, vectorEngine);

        // Recovery: load snapshots then replay AOF delta
        // Server remains in LOADING state until complete
        SnapshotReader.recover(config, kvStore, vectorEngine, aofWriter);

        RespServer server = new RespServer(config, kvStore, vectorEngine, aofWriter, snapshotWriter);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
            aofWriter.flush();
            snapshotWriter.writeFinal();
        }));

        server.start();
    }
}
