package io.agentis.memory.resp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * Netty decoder: raw bytes → RespMessage.
 * Handles partial reads — Netty will re-invoke decode() when more bytes arrive.
 */
public class RespDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        while (in.isReadable()) {
            in.markReaderIndex();
            RespMessage msg = decodeNext(in);
            if (msg == null) {
                in.resetReaderIndex();
                return;
            }
            out.add(msg);
        }
    }

    private RespMessage decodeNext(ByteBuf in) {
        if (!in.isReadable()) return null;
        byte prefix = in.readByte();
        return switch (prefix) {
            case '+' -> decodeSimpleString(in);
            case '-' -> decodeError(in);
            case ':' -> decodeInteger(in);
            case '$' -> decodeBulkString(in);
            case '*' -> decodeArray(in);
            default -> throw new RuntimeException("Unknown RESP type: " + (char) prefix);
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
        try {
            return new RespMessage.RespInteger(Long.parseLong(s));
        } catch (NumberFormatException nfe) {
            throw new RuntimeException("Invalid RESP integer: " + s, nfe);
        }
    }

    private RespMessage decodeBulkString(ByteBuf in) {
        String line = readLine(in);
        if (line == null) return null;
        try {
            int len = java.lang.Integer.parseInt(line);
            if (len == -1) return new RespMessage.NullBulkString();
            if (in.readableBytes() < len + 2) return null;
            byte[] bytes = new byte[len];
            in.readBytes(bytes);
            in.skipBytes(2); // \r\n
            return new RespMessage.BulkString(bytes);
        } catch (NumberFormatException nfe) {
            throw new RuntimeException("Invalid RESP bulk string length: " + line, nfe);
        }
    }

    private RespMessage decodeArray(ByteBuf in) {
        String line = readLine(in);
        if (line == null) return null;
        try {
            int count = java.lang.Integer.parseInt(line);
            if (count == -1) return new RespMessage.RespArray(null);
            java.util.List<RespMessage> elements = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                RespMessage msg = decodeNext(in);
                if (msg == null) return null;
                elements.add(msg);
            }
            return new RespMessage.RespArray(elements);
        } catch (NumberFormatException nfe) {
            throw new RuntimeException("Invalid RESP array length: " + line, nfe);
        }
    }

    private String readLine(ByteBuf in) {
        int eol = in.indexOf(in.readerIndex(), in.writerIndex(), (byte) '\r');
        if (eol == -1 || in.writerIndex() <= eol + 1 || in.getByte(eol + 1) != '\n') {
            return null;
        }
        int len = eol - in.readerIndex();
        byte[] bytes = new byte[len];
        in.readBytes(bytes);
        in.skipBytes(2); // \r\n
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
