package io.agentis.memory.store;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
public class AofWriter implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(AofWriter.class);
    private static final byte[] CRLF = "\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private final ServerConfig config;
    private final Path aofPath;
    private FileChannel channel;
    private final ScheduledExecutorService fsyncExecutor;

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
    }

    public synchronized void append(List<byte[]> args) {
        if (channel == null) return;

        try {
            // Write as RESP Array of BulkStrings
            StringBuilder sb = new StringBuilder();
            sb.append("*").append(args.size()).append("\r\n");
            writeBytes(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for (byte[] arg : args) {
                StringBuilder bsb = new StringBuilder();
                bsb.append("$").append(arg.length).append("\r\n");
                writeBytes(bsb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                writeBytes(arg);
                writeBytes(CRLF);
            }

            if ("always".equalsIgnoreCase(config.aofFsync)) {
                channel.force(false);
            }
        } catch (IOException e) {
            log.error("Failed to write to AOF", e);
        }
    }

    private void writeBytes(byte[] bytes) throws IOException {
        channel.write(ByteBuffer.wrap(bytes));
    }

    private void fsync() {
        if (channel == null) return;
        try {
            channel.force(false);
        } catch (IOException e) {
            log.error("Failed to fsync AOF", e);
        }
    }

    @Override
    public synchronized void close() throws Exception {
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
