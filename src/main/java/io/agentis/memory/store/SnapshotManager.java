package io.agentis.memory.store;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.vector.Chunk;
import io.agentis.memory.vector.HnswIndex;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class SnapshotManager {
    private static final Logger log = LoggerFactory.getLogger(SnapshotManager.class);
    private static final byte[] MAGIC = "AGMM".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1;

    private final ServerConfig config;
    private final KvStore kvStore;
    private final HnswIndex hnswIndex;
    private final AofWriter aofWriter;
    private final AtomicLong dirtyCount = new AtomicLong(0);
    private long lastSnapshotTime = System.currentTimeMillis();

    @Inject
    public SnapshotManager(ServerConfig config, KvStore kvStore, HnswIndex hnswIndex, AofWriter aofWriter) {
        this.config = config;
        this.kvStore = kvStore;
        this.hnswIndex = hnswIndex;
        this.aofWriter = aofWriter;
    }

    public void incrementDirty() {
        dirtyCount.incrementAndGet();
    }

    public boolean shouldSnapshot() {
        long now = System.currentTimeMillis();
        long elapsed = (now - lastSnapshotTime) / 1000;
        return dirtyCount.get() >= config.snapshotAfterChanges && elapsed >= config.snapshotInterval;
    }

    public synchronized void save() throws IOException {
        log.info("Starting background save...");
        long start = System.currentTimeMillis();

        Path dataDir = config.dataDir;
        Files.createDirectories(dataDir);
        Path tempKv = dataDir.resolve("snapshot.kv.tmp");
        Path tempHnsw = dataDir.resolve("snapshot.hnsw.tmp");

        try {
            // 1. Save KV Store
            saveKv(tempKv);
            // 2. Save HNSW Index
            hnswIndex.save(tempHnsw);

            // 3. Atomic rename
            Files.move(tempKv, dataDir.resolve("snapshot.kv"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempHnsw, dataDir.resolve("snapshot.hnsw"), StandardCopyOption.REPLACE_EXISTING);

            // 4. Reset AOF if snapshot is successful? 
            // In Redis, AOF is not reset by RDB. But AOF rewrite exists.
            // For now, we don't reset AOF, but in a real system we might.
            // The recovery logic will handle Snapshot + AOF.

            dirtyCount.set(0);
            lastSnapshotTime = System.currentTimeMillis();
            log.info("Snapshot saved in {}ms", System.currentTimeMillis() - start);
        } finally {
            Files.deleteIfExists(tempKv);
            Files.deleteIfExists(tempHnsw);
        }
    }

    private void saveKv(Path path) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path.toFile())))) {
            out.write(MAGIC);
            out.writeInt(VERSION);

            Map<String, KvStore.Entry> store = kvStore.getStore();
            out.writeLong(store.size());

            for (Map.Entry<String, KvStore.Entry> entry : store.entrySet()) {
                KvStore.Entry e = entry.getValue();
                if (e.isExpired()) continue;

                out.writeUTF(entry.getKey());
                out.writeLong(e.createdAt());
                out.writeLong(e.expireAt());
                out.writeBoolean(e.hasVectorIndex());

                writeValue(out, e.value());
            }
        }
    }

    private void writeValue(DataOutputStream out, StoreValue value) throws IOException {
        if (value instanceof StoreValue.StringValue sv) {
            out.writeByte(0);
            out.writeInt(sv.raw().length);
            out.write(sv.raw());
        } else if (value instanceof StoreValue.HashValue hv) {
            out.writeByte(1);
            out.writeInt(hv.fields().size());
            for (Map.Entry<String, byte[]> f : hv.fields().entrySet()) {
                out.writeUTF(f.getKey());
                out.writeInt(f.getValue().length);
                out.write(f.getValue());
            }
        } else if (value instanceof StoreValue.ListValue lv) {
            out.writeByte(2);
            out.writeInt(lv.list().size());
            for (byte[] item : lv.list()) {
                out.writeInt(item.length);
                out.write(item);
            }
        } else if (value instanceof StoreValue.SetValue sv) {
            out.writeByte(3);
            out.writeInt(sv.members().size());
            for (String member : sv.members()) {
                out.writeUTF(member);
            }
        } else if (value instanceof StoreValue.SortedSetValue ssv) {
            out.writeByte(4);
            out.writeInt(ssv.memberToScore().size());
            for (Map.Entry<String, Double> entry : ssv.memberToScore().entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeDouble(entry.getValue());
            }
        }
    }

    public void load() throws IOException {
        Path kvPath = config.dataDir.resolve("snapshot.kv");
        Path hnswPath = config.dataDir.resolve("snapshot.hnsw");

        if (Files.exists(kvPath)) {
            loadKv(kvPath);
        }
        if (Files.exists(hnswPath)) {
            hnswIndex.load(hnswPath);
        }
    }

    private void loadKv(Path path) throws IOException {
        log.info("Loading KV snapshot from {}...", path);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(path.toFile())))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!java.util.Arrays.equals(magic, MAGIC)) {
                throw new IOException("Invalid snapshot magic");
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported snapshot version: " + version);
            }

            long count = in.readLong();
            for (long i = 0; i < count; i++) {
                String key = in.readUTF();
                long createdAt = in.readLong();
                long expireAt = in.readLong();
                boolean hasVectorIndex = in.readBoolean();
                StoreValue value = readValue(in);

                KvStore.Entry entry = new KvStore.Entry(value, createdAt, expireAt, hasVectorIndex);
                if (!entry.isExpired()) {
                    kvStore.putEntry(key, entry);
                }
            }
        }
        log.info("KV snapshot loaded.");
    }

    private StoreValue readValue(DataInputStream in) throws IOException {
        byte type = in.readByte();
        return switch (type) {
            case 0 -> {
                int len = in.readInt();
                byte[] bytes = new byte[len];
                in.readFully(bytes);
                yield new StoreValue.StringValue(bytes);
            }
            case 1 -> {
                int size = in.readInt();
                ConcurrentHashMap<String, byte[]> fields = new ConcurrentHashMap<>();
                for (int i = 0; i < size; i++) {
                    String field = in.readUTF();
                    int len = in.readInt();
                    byte[] bytes = new byte[len];
                    in.readFully(bytes);
                    fields.put(field, bytes);
                }
                yield new StoreValue.HashValue(fields);
            }
            case 2 -> {
                int size = in.readInt();
                CopyOnWriteArrayList<byte[]> list = new CopyOnWriteArrayList<>();
                for (int i = 0; i < size; i++) {
                    int len = in.readInt();
                    byte[] bytes = new byte[len];
                    in.readFully(bytes);
                    list.add(bytes);
                }
                yield new StoreValue.ListValue(list);
            }
            case 3 -> {
                int size = in.readInt();
                Set<String> members = ConcurrentHashMap.newKeySet();
                for (int i = 0; i < size; i++) {
                    members.add(in.readUTF());
                }
                yield new StoreValue.SetValue(members);
            }
            case 4 -> {
                int size = in.readInt();
                ConcurrentHashMap<String, Double> memberToScore = new ConcurrentHashMap<>();
                ConcurrentSkipListMap<Double, java.util.TreeSet<String>> scoreToMembers = new ConcurrentSkipListMap<>();
                for (int i = 0; i < size; i++) {
                    String member = in.readUTF();
                    double score = in.readDouble();
                    memberToScore.put(member, score);
                    scoreToMembers.computeIfAbsent(score, k -> new java.util.TreeSet<>()).add(member);
                }
                yield new StoreValue.SortedSetValue(memberToScore, scoreToMembers);
            }
            default -> throw new IOException("Unknown value type: " + type);
        };
    }
}
