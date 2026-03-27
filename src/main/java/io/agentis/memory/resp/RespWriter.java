package io.agentis.memory.resp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * High-performance RESP encoder with internal write buffer.
 * Designed for minimal allocation on the hot path.
 *
 * Key optimizations:
 * - Pre-encoded static responses (OK, PONG, NullBulkString)
 * - Integer encoding without String intermediary
 * - Buffered output with explicit flush (enables pipelining)
 */
public class RespWriter {
    private static final byte[] OK_RESP = "+OK\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PONG_RESP = "+PONG\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] QUEUED_RESP = "+QUEUED\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NULL_BULK_RESP = "$-1\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NULL_RESP = "_\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TRUE_RESP = "#t\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FALSE_RESP = "#f\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NULL_ARRAY_RESP = "*-1\r\n".getBytes(StandardCharsets.UTF_8);

    private final OutputStream out;
    private final byte[] buf;
    private int pos;
    // Scratch space for long encoding (max 20 digits + sign)
    private final byte[] longScratch = new byte[21];

    public RespWriter(OutputStream outputStream) {
        this(outputStream, 16384);
    }

    public RespWriter(OutputStream outputStream, int bufferSize) {
        this.out = outputStream;
        this.buf = new byte[bufferSize];
        this.pos = 0;
    }

    public void write(RespMessage msg) throws IOException {
        switch (msg) {
            case RespMessage.SimpleString s -> {
                String v = s.value();
                if ("OK".equals(v)) {
                    writeRaw(OK_RESP);
                } else if ("PONG".equals(v)) {
                    writeRaw(PONG_RESP);
                } else if ("QUEUED".equals(v)) {
                    writeRaw(QUEUED_RESP);
                } else {
                    writeByte('+');
                    writeStringBytes(v);
                    writeCrlf();
                }
            }
            case RespMessage.Error e -> {
                writeByte('-');
                writeStringBytes(e.message());
                writeCrlf();
            }
            case RespMessage.RespInteger i -> {
                writeByte(':');
                writeLong(i.value());
                writeCrlf();
            }
            case RespMessage.BulkString b -> {
                writeByte('$');
                writeLong(b.value().length);
                writeCrlf();
                writeBytes(b.value());
                writeCrlf();
            }
            case RespMessage.NullBulkString _ -> writeRaw(NULL_BULK_RESP);
            case RespMessage.RespArray a -> {
                if (a.elements() == null) {
                    writeRaw(NULL_ARRAY_RESP);
                } else {
                    writeByte('*');
                    writeLong(a.elements().size());
                    writeCrlf();
                    for (RespMessage element : a.elements()) {
                        write(element);
                    }
                }
            }
            case RespMessage.Null _ -> writeRaw(NULL_RESP);
            case RespMessage.Boolean b -> writeRaw(b.value() ? TRUE_RESP : FALSE_RESP);
            case RespMessage.Double d -> {
                writeByte(',');
                writeStringBytes(java.lang.Double.toString(d.value()));
                writeCrlf();
            }
            case RespMessage.BigNumber n -> {
                writeByte('(');
                writeStringBytes(n.value());
                writeCrlf();
            }
            case RespMessage.VerbatimString v -> {
                writeByte('=');
                int len = v.value().length() + 4; // format:3 + ':'
                writeLong(len);
                writeCrlf();
                writeStringBytes(v.format());
                writeByte(':');
                writeStringBytes(v.value());
                writeCrlf();
            }
            case RespMessage.RespMap m -> {
                writeByte('%');
                writeLong(m.elements().size());
                writeCrlf();
                for (Map.Entry<RespMessage, RespMessage> entry : m.elements().entrySet()) {
                    write(entry.getKey());
                    write(entry.getValue());
                }
            }
            case RespMessage.RespSet s -> {
                writeByte('~');
                writeLong(s.elements().size());
                writeCrlf();
                for (RespMessage element : s.elements()) {
                    write(element);
                }
            }
        }
    }

    public void flush() throws IOException {
        flushBuffer();
        out.flush();
    }

    // ─── Buffer write primitives ─────────────────────────────────────

    private void writeByte(int b) throws IOException {
        if (pos >= buf.length) flushBuffer();
        buf[pos++] = (byte) b;
    }

    private void writeCrlf() throws IOException {
        if (pos + 2 > buf.length) flushBuffer();
        buf[pos++] = '\r';
        buf[pos++] = '\n';
    }

    private void writeRaw(byte[] data) throws IOException {
        if (data.length <= buf.length - pos) {
            System.arraycopy(data, 0, buf, pos, data.length);
            pos += data.length;
        } else {
            flushBuffer();
            if (data.length <= buf.length) {
                System.arraycopy(data, 0, buf, 0, data.length);
                pos = data.length;
            } else {
                out.write(data);
            }
        }
    }

    private void writeBytes(byte[] data) throws IOException {
        if (data.length <= buf.length - pos) {
            System.arraycopy(data, 0, buf, pos, data.length);
            pos += data.length;
        } else {
            flushBuffer();
            out.write(data);
        }
    }

    private void writeStringBytes(String s) throws IOException {
        int len = s.length();
        boolean ascii = true;
        for (int i = 0; i < len; i++) {
            if (s.charAt(i) >= 0x80) {
                ascii = false;
                break;
            }
        }
        if (ascii && len <= buf.length - pos) {
            for (int i = 0; i < len; i++) {
                buf[pos++] = (byte) s.charAt(i);
            }
        } else if (ascii) {
            flushBuffer();
            for (int i = 0; i < len; i++) {
                buf[pos++] = (byte) s.charAt(i);
                if (pos >= buf.length) flushBuffer();
            }
        } else {
            writeBytes(s.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void writeLong(long value) throws IOException {
        int len = encodeLong(value, longScratch);
        if (len <= buf.length - pos) {
            System.arraycopy(longScratch, 0, buf, pos, len);
            pos += len;
        } else {
            flushBuffer();
            System.arraycopy(longScratch, 0, buf, 0, len);
            pos = len;
        }
    }

    static int encodeLong(long value, byte[] dest) {
        if (value == 0) {
            dest[0] = '0';
            return 1;
        }
        boolean negative = value < 0;
        if (negative) value = -value;
        int p = dest.length;
        while (value > 0) {
            dest[--p] = (byte) ('0' + (value % 10));
            value /= 10;
        }
        if (negative) dest[--p] = '-';
        int len = dest.length - p;
        if (p > 0) System.arraycopy(dest, p, dest, 0, len);
        return len;
    }

    private void flushBuffer() throws IOException {
        if (pos > 0) {
            out.write(buf, 0, pos);
            pos = 0;
        }
    }
}
