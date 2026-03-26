package io.agentis.memory.store;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.vector.Chunk;
import io.agentis.memory.vector.HnswIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotManagerTest {

    @TempDir
    Path tempDir;

    private ServerConfig config;
    private KvStore kvStore;
    private HnswIndex hnswIndex;
    private SnapshotManager snapshotManager;

    @BeforeEach
    void setUp() {
        config = new ServerConfig();
        config.dataDir = tempDir;
        kvStore = new KvStore(config);
        hnswIndex = new HnswIndex(config);
        // AofWriter not strictly needed for these unit tests if we don't call methods that use it
        snapshotManager = new SnapshotManager(config, kvStore, hnswIndex, null);
    }

    @Test
    void testKvSerialization() throws IOException {
        kvStore.set("key1", "value1".getBytes(StandardCharsets.UTF_8), -1);
        kvStore.set("key2", "value2".getBytes(StandardCharsets.UTF_8), 100); // long TTL
        kvStore.hset("hash1", List.of("f1".getBytes(), "v1".getBytes()));

        snapshotManager.save();

        // Clear and reload
        KvStore newKvStore = new KvStore(config);
        HnswIndex newHnswIndex = new HnswIndex(config);
        SnapshotManager newSnapshotManager = new SnapshotManager(config, newKvStore, newHnswIndex, null);

        newSnapshotManager.load();

        assertArrayEquals("value1".getBytes(StandardCharsets.UTF_8), newKvStore.get("key1"));
        assertArrayEquals("value2".getBytes(StandardCharsets.UTF_8), newKvStore.get("key2"));
        assertArrayEquals("v1".getBytes(), (byte[]) newKvStore.hget("hash1", "f1"));
    }

    @Test
    void testHnswSerialization() throws IOException {
        float[] vec = new float[384];
        vec[0] = 1.0f;
        Chunk chunk = new Chunk("key1", 0, "text1", vec, "default");
        hnswIndex.add(chunk);

        snapshotManager.save();

        // Clear and reload
        KvStore newKvStore = new KvStore(config);
        HnswIndex newHnswIndex = new HnswIndex(config);
        SnapshotManager newSnapshotManager = new SnapshotManager(config, newKvStore, newHnswIndex, null);

        newSnapshotManager.load();

        var results = newHnswIndex.search(vec, "default", 1);
        assertEquals(1, results.size());
        assertEquals("key1", results.get(0).parentKey());
        assertEquals("text1", results.get(0).chunkText());
    }
}
