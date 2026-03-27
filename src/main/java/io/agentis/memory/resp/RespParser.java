package io.agentis.memory.resp;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * High-performance RESP parser with internal read buffer.
 * Supports RESP2 + RESP3 types. Designed for minimal allocation on the hot path.
 *
 * Key optimizations vs previous version:
 * - Own 16KB read buffer (no BufferedInputStream overhead)
 * - readLineLong() parses integers without String allocation
 * - readLineString() scans buffer directly for CRLF
 * - hasBufferedData() enables pipelining in RespServer
 */
public class RespParser {
    private final InputStream in;
    private final byte[] buf;
    private int pos;
    private int limit;

    public RespParser(InputStream in) {
        this(in, 16384);
    }

    public RespParser(InputStream in, int bufferSize) {
        this.in = in;
        this.buf = new byte[bufferSize];
        this.pos = 0;
        this.limit = 0;
    }

    /**
     * Returns true if there is unprocessed data in the read buffer.
     * Used by RespServer to decide whether to flush after a command
     * (pipelining support: skip flush if more commands are buffered).
     */
    public boolean hasBufferedData() {
        return pos < limit;
    }

    public RespMessage readMessage() throws IOException {
        int prefix = readByte();
        if (prefix == -1) return null; // clean disconnect
        return switch (prefix) {
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

    // ─── Type decoders ───────────────────────────────────────────────

    private RespMessage decodeSimpleString() throws IOException {
        return new RespMessage.SimpleString(readLineString());
    }

    private RespMessage decodeError() throws IOException {
        return new RespMessage.Error(readLineString());
    }

    private RespMessage decodeInteger() throws IOException {
        return new RespMessage.RespInteger(readLineLong());
    }

    private RespMessage decodeBulkString() throws IOException {
        int len = (int) readLineLong();
        if (len == -1) return new RespMessage.NullBulkString();
        byte[] data = readExact(len);
        readCrlf();
        return new RespMessage.BulkString(data);
    }

    private RespMessage decodeArray() throws IOException {
        int count = (int) readLineLong();
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
        if (b == -1) throw new EOFException("Unexpected EOF");
        readCrlf();
        return new RespMessage.Boolean(b == 't');
    }

    private RespMessage decodeDouble() throws IOException {
        return new RespMessage.Double(java.lang.Double.parseDouble(readLineString()));
    }

    private RespMessage decodeBigNumber() throws IOException {
        return new RespMessage.BigNumber(readLineString());
    }

    private RespMessage decodeVerbatimString() throws IOException {
        int len = (int) readLineLong();
        byte[] data = readExact(len);
        readCrlf();
        String format = new String(data, 0, 3, StandardCharsets.UTF_8);
        String content = new String(data, 4, len - 4, StandardCharsets.UTF_8);
        return new RespMessage.VerbatimString(format, content);
    }

    private RespMessage decodeMap() throws IOException {
        int count = (int) readLineLong();
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
        int count = (int) readLineLong();
        Set<RespMessage> elements = new LinkedHashSet<>(count);
        for (int i = 0; i < count; i++) {
            RespMessage msg = readMessage();
            if (msg == null) throw new EOFException("Unexpected EOF in set");
            elements.add(msg);
        }
        return new RespMessage.RespSet(elements);
    }

    // ─── Buffer I/O primitives ───────────────────────────────────────

    private int readByte() throws IOException {
        if (pos >= limit && !refill()) return -1;
        return buf[pos++] & 0xFF;
    }

    private boolean refill() throws IOException {
        int n = in.read(buf, 0, buf.length);
        if (n <= 0) {
            limit = 0;
            pos = 0;
            return false;
        }
        limit = n;
        pos = 0;
        return true;
    }

    /**
     * Reads a CRLF-terminated line, parsing the content as a long.
     * Avoids String allocation — hot path for array counts and bulk string lengths.
     */
    private long readLineLong() throws IOException {
        long value = 0;
        boolean negative = false;

        if (pos >= limit && !refill())
            throw new EOFException("Unexpected EOF reading line integer");

        if (buf[pos] == '-') {
            negative = true;
            pos++;
        }

        while (true) {
            if (pos >= limit && !refill())
                throw new EOFException("Unexpected EOF reading line integer");
            byte b = buf[pos++];
            if (b == '\r') {
                if (pos >= limit && !refill())
                    throw new EOFException("Unexpected EOF after \\r");
                pos++; // skip \n
                return negative ? -value : value;
            }
            value = value * 10 + (b - '0');
        }
    }

    /**
     * Reads a CRLF-terminated line as a String.
     * Used for SimpleString, Error, and other non-numeric types (cold path).
     * Fast path: scans buffer for CRLF and creates String from buffer slice.
     * Slow path: line spans buffer boundary — accumulates via ByteArrayOutputStream.
     */
    private String readLineString() throws IOException {
        // Fast path: try to find \r\n within current buffer
        for (int i = pos; i < limit - 1; i++) {
            if (buf[i] == '\r' && buf[i + 1] == '\n') {
                String result = new String(buf, pos, i - pos, StandardCharsets.UTF_8);
                pos = i + 2;
                return result;
            }
        }

        // Slow path: line spans buffer boundary
        var sb = new java.io.ByteArrayOutputStream(64);
        int prev = -1;
        while (pos < limit) {
            byte b = buf[pos++];
            if (prev == '\r' && b == '\n') {
                byte[] bytes = sb.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8);
            }
            sb.write(b);
            prev = b & 0xFF;
        }
        while (true) {
            if (!refill()) throw new EOFException("Unexpected EOF while reading line");
            while (pos < limit) {
                byte b = buf[pos++];
                if (prev == '\r' && b == '\n') {
                    byte[] bytes = sb.toByteArray();
                    return new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8);
                }
                sb.write(b);
                prev = b & 0xFF;
            }
        }
    }

    /**
     * Reads exactly len bytes. Copies from buffer first, then reads
     * remaining directly from stream (bypasses buffer for large payloads).
     */
    private byte[] readExact(int len) throws IOException {
        byte[] data = new byte[len];
        int offset = 0;

        int buffered = limit - pos;
        if (buffered > 0) {
            int toCopy = Math.min(buffered, len);
            System.arraycopy(buf, pos, data, 0, toCopy);
            pos += toCopy;
            offset = toCopy;
        }

        while (offset < len) {
            int n = in.read(data, offset, len - offset);
            if (n == -1) throw new EOFException("Unexpected EOF reading " + len + " bytes");
            offset += n;
        }

        return data;
    }

    private void readCrlf() throws IOException {
        if (pos >= limit && !refill())
            throw new EOFException("Unexpected EOF expecting CRLF");
        if (buf[pos++] != '\r')
            throw new IOException("Expected \\r in CRLF");
        if (pos >= limit && !refill())
            throw new EOFException("Unexpected EOF expecting LF");
        if (buf[pos++] != '\n')
            throw new IOException("Expected \\n in CRLF");
    }
}
