package io.agentis.memory.store;

import io.agentis.memory.config.ServerConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

@Singleton
public class AofWriter implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(AofWriter.class);

    private final ServerConfig config;
    private final Path aofPath;
    private volatile FileChannel channel;
    private final ScheduledExecutorService fsyncExecutor;
    private final ConcurrentLinkedQueue<byte[]> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final Thread writerThread;

    @Inject
    public AofWriter(ServerConfig config) {
        this.config = config;
        this.aofPath = config.dataDir.resolve("appendonly.aof");

        if (config.aofEnabled) {
            try {
                Files.createDirectories(config.dataDir);
                this.channel = FileChannel.open(aofPath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND,
                        StandardOpenOption.WRITE);
                log.info("AOF enabled, writing to {}", aofPath);
            } catch (IOException e) {
                log.error("Failed to open AOF file", e);
                this.channel = null;
            }
        } else {
            this.channel = null;
        }

        if (config.aofEnabled && "everysec".equalsIgnoreCase(config.aofFsync)) {
            this.fsyncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "aof-fsync");
                t.setDaemon(true);
                return t;
            });
            this.fsyncExecutor.scheduleAtFixedRate(this::fsync, 1, 1, TimeUnit.SECONDS);
        } else {
            this.fsyncExecutor = null;
        }

        if (config.aofEnabled && channel != null) {
            this.writerThread = new Thread(this::drainLoop, "aof-writer");
            this.writerThread.setDaemon(true);
            this.writerThread.start();
        } else {
            this.writerThread = null;
        }
    }

    /**
     * Non-blocking append: encodes args as RESP and enqueues for async write.
     * Hot path: encode + CAS enqueue only — no synchronized, no I/O.
     */
    public void append(List<byte[]> args) {
        if (channel == null) return;
        byte[] encoded = encodeResp(args);
        queue.add(encoded);
    }

    private void drainLoop() {
        boolean fsyncAlways = "always".equalsIgnoreCase(config.aofFsync);
        while (!stopped.get()) {
            byte[] entry = queue.poll();
            if (entry == null) {
                Thread.onSpinWait();
                entry = queue.poll();
                if (entry == null) {
                    LockSupport.parkNanos(1_000_000); // 1ms
                    continue;
                }
            }
            try {
                channel.write(ByteBuffer.wrap(entry));
                while ((entry = queue.poll()) != null) {
                    channel.write(ByteBuffer.wrap(entry));
                }
                if (fsyncAlways) {
                    channel.force(false);
                }
            } catch (IOException e) {
                log.error("AOF write failed", e);
            }
        }
        // Final drain on shutdown
        byte[] entry;
        while ((entry = queue.poll()) != null) {
            try {
                channel.write(ByteBuffer.wrap(entry));
            } catch (IOException e) {
                log.error("AOF final drain write failed", e);
            }
        }
    }

    static byte[] encodeResp(List<byte[]> args) {
        int size = 1 + digitCount(args.size()) + 2;
        for (byte[] arg : args) {
            size += 1 + digitCount(arg.length) + 2 + arg.length + 2;
        }
        byte[] result = new byte[size];
        int pos = 0;
        result[pos++] = '*';
        pos = writeDigits(args.size(), result, pos);
        result[pos++] = '\r';
        result[pos++] = '\n';
        for (byte[] arg : args) {
            result[pos++] = '$';
            pos = writeDigits(arg.length, result, pos);
            result[pos++] = '\r';
            result[pos++] = '\n';
            System.arraycopy(arg, 0, result, pos, arg.length);
            pos += arg.length;
            result[pos++] = '\r';
            result[pos++] = '\n';
        }
        return result;
    }

    private static int digitCount(int value) {
        if (value == 0) return 1;
        int count = 0;
        int v = value;
        while (v > 0) { count++; v /= 10; }
        return count;
    }

    private static int writeDigits(int value, byte[] buf, int pos) {
        if (value == 0) {
            buf[pos] = '0';
            return pos + 1;
        }
        int digits = digitCount(value);
        int end = pos + digits;
        int p = end - 1;
        int v = value;
        while (v > 0) {
            buf[p--] = (byte) ('0' + (v % 10));
            v /= 10;
        }
        return end;
    }

    private void fsync() {
        if (channel == null) return;
        try {
            channel.force(false);
        } catch (IOException e) {
            log.error("Failed to fsync AOF", e);
        }
    }

    public int getPendingWrites() {
        return queue.size();
    }

    @Override
    public void close() throws Exception {
        stopped.set(true);
        if (writerThread != null) {
            LockSupport.unpark(writerThread);
            writerThread.join(5000);
        }
        if (fsyncExecutor != null) {
            fsyncExecutor.shutdown();
            if (!fsyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                fsyncExecutor.shutdownNow();
            }
        }
        if (channel != null) {
            channel.force(true);
            channel.close();
            channel = null;
        }
    }
}
