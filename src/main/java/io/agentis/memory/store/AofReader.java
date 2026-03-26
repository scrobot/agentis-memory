package io.agentis.memory.store;

import io.agentis.memory.command.CommandRouter;
import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespDecoder;
import io.agentis.memory.resp.RespMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class AofReader {
    private static final Logger log = LoggerFactory.getLogger(AofReader.class);

    private final ServerConfig config;
    private final CommandRouter router;

    @Inject
    public AofReader(ServerConfig config, CommandRouter router) {
        this.config = config;
        this.router = router;
    }

    public void recover() {
        if (!config.aofEnabled) return;

        Path aofPath = config.dataDir.resolve("appendonly.aof");
        if (!Files.exists(aofPath)) {
            log.info("AOF file not found at {}, skipping recovery", aofPath);
            return;
        }

        log.info("Starting AOF recovery from {}...", aofPath);
        long startTime = System.currentTimeMillis();
        int count = 0;

        try {
            byte[] bytes = Files.readAllBytes(aofPath);
            ByteBuf in = Unpooled.wrappedBuffer(bytes);
            RespDecoder decoder = new RespDecoder();
            List<Object> out = new ArrayList<>();

            // We need to call decode manually or use a loop similar to RespDecoder
            while (in.isReadable()) {
                int readerIndex = in.readerIndex();
                try {
                    // RespDecoder.decode is protected, but we can use our own logic 
                    // or just expose it. For simplicity, let's assume we can use a helper.
                    // Actually, let's just implement a simple loop here or use the decoder if we can.
                    
                    // Since I cannot easily change RespDecoder access right now without multiple edits,
                    // I will implement a simplified version or use a trick.
                    
                    RespMessage msg = decodeOne(in);
                    if (msg == null) break;
                    
                    router.dispatch(null, msg);
                    count++;
                } catch (Exception e) {
                    log.error("Failed to decode AOF message at index {}", readerIndex, e);
                    break;
                }
            }
        } catch (IOException e) {
            log.error("Failed to read AOF file", e);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("AOF recovery finished: replayed {} commands in {}ms", count, duration);
    }

    // Simplified decoder for AOF recovery
    private RespMessage decodeOne(ByteBuf in) {
        if (!in.isReadable()) return null;
        byte prefix = in.readByte();
        return switch (prefix) {
            case '*' -> decodeArray(in);
            case '+' -> decodeSimpleString(in);
            case '-' -> decodeError(in);
            case ':' -> decodeInteger(in);
            case '$' -> decodeBulkString(in);
            default -> {
                log.warn("Unexpected RESP type in AOF: {} (0x{}) at index {}", (char) prefix, Integer.toHexString(prefix & 0xFF), in.readerIndex() - 1);
                yield null;
            }
        };
    }

    private RespMessage decodeSimpleString(ByteBuf in) {
        String s = readLine(in);
        return s == null ? null : new RespMessage.SimpleString(s);
    }

    private RespMessage decodeError(ByteBuf in) {
        String s = readLine(in);
        return s == null ? null : new RespMessage.Error(s);
    }

    private RespMessage decodeInteger(ByteBuf in) {
        String s = readLine(in);
        if (s == null) return null;
        return new RespMessage.RespInteger(Long.parseLong(s));
    }

    private RespMessage decodeArray(ByteBuf in) {
        String line = readLine(in);
        if (line == null) return null;
        int count = Integer.parseInt(line);
        if (count == -1) return new RespMessage.RespArray(null);
        List<RespMessage> elements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            RespMessage msg = decodeNext(in);
            if (msg == null) return null;
            elements.add(msg);
        }
        return new RespMessage.RespArray(elements);
    }

    private RespMessage decodeNext(ByteBuf in) {
        if (!in.isReadable()) return null;
        byte prefix = in.readByte();
        return switch (prefix) {
            case '$' -> decodeBulkString(in);
            case '+' -> decodeSimpleString(in);
            case ':' -> decodeInteger(in);
            default -> null; 
        };
    }

    private RespMessage decodeBulkString(ByteBuf in) {
        String line = readLine(in);
        if (line == null) return null;
        int len = Integer.parseInt(line);
        if (len == -1) return new RespMessage.NullBulkString();
        if (in.readableBytes() < len + 2) return null;
        byte[] bytes = new byte[len];
        in.readBytes(bytes);
        in.skipBytes(2); // \r\n
        return new RespMessage.BulkString(bytes);
    }

    private String readLine(ByteBuf in) {
        int eol = in.indexOf(in.readerIndex(), in.writerIndex(), (byte) '\r');
        if (eol == -1) return null;
        int len = eol - in.readerIndex();
        byte[] bytes = new byte[len];
        in.readBytes(bytes);
        in.skipBytes(2); // \r\n
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
