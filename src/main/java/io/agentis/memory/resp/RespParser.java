package io.agentis.memory.resp;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Blocking InputStream-based RESP parser.
 * Replaces Netty's RespDecoder (ByteToMessageDecoder).
 * Supports RESP2 + RESP3 types.
 */
public class RespParser {
    private final BufferedInputStream in;

    public RespParser(InputStream inputStream) {
        this.in = (inputStream instanceof BufferedInputStream bis)
                ? bis
                : new BufferedInputStream(inputStream);
    }

    /**
     * Reads the next RESP message from the stream.
     * Returns null on clean disconnect (EOF before any byte read).
     * Throws IOException on I/O error or mid-message disconnect.
     */
    public RespMessage readMessage() throws IOException {
        int prefix = in.read();
        if (prefix == -1) return null; // clean disconnect
        return switch ((byte) prefix) {
            case '+' -> decodeSimpleString();
            case '-' -> decodeError();
            case ':' -> decodeInteger();
            case '$' -> decodeBulkString();
            case '*' -> decodeArray();
            case '_' -> decodeNull();
            case '#' -> decodeBoolean();
            case ',' -> decodeDouble();
            case '(' -> decodeBigNumber();
            case '=' -> decodeVerbatimString();
            case '%' -> decodeMap();
            case '~' -> decodeSet();
            default -> throw new IOException("Unknown RESP type: " + (char) prefix);
        };
    }

    private RespMessage decodeSimpleString() throws IOException {
        return new RespMessage.SimpleString(readLine());
    }

    private RespMessage decodeError() throws IOException {
        return new RespMessage.Error(readLine());
    }

    private RespMessage decodeInteger() throws IOException {
        String line = readLine();
        try {
            return new RespMessage.RespInteger(Long.parseLong(line));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid RESP integer: " + line, e);
        }
    }

    private RespMessage decodeBulkString() throws IOException {
        String line = readLine();
        int len;
        try {
            len = Integer.parseInt(line);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid RESP bulk string length: " + line, e);
        }
        if (len == -1) return new RespMessage.NullBulkString();
        byte[] data = readExact(len);
        readCrlf();
        return new RespMessage.BulkString(data);
    }

    private RespMessage decodeArray() throws IOException {
        String line = readLine();
        int count;
        try {
            count = Integer.parseInt(line);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid RESP array length: " + line, e);
        }
        if (count == -1) return new RespMessage.RespArray(null);
        List<RespMessage> elements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            RespMessage msg = readMessage();
            if (msg == null) throw new EOFException("Unexpected EOF in array");
            elements.add(msg);
        }
        return new RespMessage.RespArray(elements);
    }

    private RespMessage decodeNull() throws IOException {
        readCrlf();
        return new RespMessage.Null();
    }

    private RespMessage decodeBoolean() throws IOException {
        int b = readByte();
        readCrlf();
        return new RespMessage.Boolean(b == 't');
    }

    private RespMessage decodeDouble() throws IOException {
        String line = readLine();
        return new RespMessage.Double(java.lang.Double.parseDouble(line));
    }

    private RespMessage decodeBigNumber() throws IOException {
        return new RespMessage.BigNumber(readLine());
    }

    private RespMessage decodeVerbatimString() throws IOException {
        String line = readLine();
        int len = Integer.parseInt(line);
        byte[] data = readExact(len);
        readCrlf();
        // format is 3 bytes + ':' + content
        String format = new String(data, 0, 3, StandardCharsets.UTF_8);
        String content = new String(data, 4, len - 4, StandardCharsets.UTF_8);
        return new RespMessage.VerbatimString(format, content);
    }

    private RespMessage decodeMap() throws IOException {
        String line = readLine();
        int count = Integer.parseInt(line);
        Map<RespMessage, RespMessage> elements = new LinkedHashMap<>(count);
        for (int i = 0; i < count; i++) {
            RespMessage key = readMessage();
            if (key == null) throw new EOFException("Unexpected EOF in map key");
            RespMessage value = readMessage();
            if (value == null) throw new EOFException("Unexpected EOF in map value");
            elements.put(key, value);
        }
        return new RespMessage.RespMap(elements);
    }

    private RespMessage decodeSet() throws IOException {
        String line = readLine();
        int count = Integer.parseInt(line);
        Set<RespMessage> elements = new LinkedHashSet<>(count);
        for (int i = 0; i < count; i++) {
            RespMessage msg = readMessage();
            if (msg == null) throw new EOFException("Unexpected EOF in set");
            elements.add(msg);
        }
        return new RespMessage.RespSet(elements);
    }

    /**
     * Reads a line terminated by \r\n. Returns the content without the terminator.
     */
    private String readLine() throws IOException {
        // Read bytes until \r\n
        var buf = new java.io.ByteArrayOutputStream(64);
        int prev = -1;
        while (true) {
            int b = in.read();
            if (b == -1) throw new EOFException("Unexpected EOF while reading line");
            if (prev == '\r' && b == '\n') {
                // Remove the trailing \r we already wrote
                byte[] bytes = buf.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8);
            }
            buf.write(b);
            prev = b;
        }
    }

    private void readCrlf() throws IOException {
        int cr = readByte();
        int lf = readByte();
        if (cr != '\r' || lf != '\n') {
            throw new IOException("Expected \\r\\n, got " + cr + "," + lf);
        }
    }

    private byte[] readExact(int len) throws IOException {
        byte[] data = new byte[len];
        int offset = 0;
        while (offset < len) {
            int n = in.read(data, offset, len - offset);
            if (n == -1) throw new EOFException("Unexpected EOF reading " + len + " bytes");
            offset += n;
        }
        return data;
    }

    private int readByte() throws IOException {
        int b = in.read();
        if (b == -1) throw new EOFException("Unexpected EOF");
        return b;
    }
}
