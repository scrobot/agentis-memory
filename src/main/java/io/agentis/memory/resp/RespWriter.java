package io.agentis.memory.resp;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Blocking OutputStream-based RESP encoder.
 * Replaces Netty's RespEncoder (MessageToByteEncoder).
 * Supports RESP2 + RESP3 types.
 */
public class RespWriter {
    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.UTF_8);

    private final BufferedOutputStream out;

    public RespWriter(OutputStream outputStream) {
        this.out = (outputStream instanceof BufferedOutputStream bos)
                ? bos
                : new BufferedOutputStream(outputStream);
    }

    public void write(RespMessage msg) throws IOException {
        writeMessage(msg);
    }

    public void flush() throws IOException {
        out.flush();
    }

    private void writeMessage(RespMessage msg) throws IOException {
        switch (msg) {
            case RespMessage.SimpleString s -> {
                out.write('+');
                out.write(s.value().getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
            }
            case RespMessage.Error e -> {
                out.write('-');
                out.write(e.message().getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
            }
            case RespMessage.RespInteger i -> {
                out.write(':');
                out.write(Long.toString(i.value()).getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
            }
            case RespMessage.BulkString b -> {
                out.write('$');
                out.write(Integer.toString(b.value().length).getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
                out.write(b.value());
                out.write(CRLF);
            }
            case RespMessage.NullBulkString _ -> {
                out.write("$-1\r\n".getBytes(StandardCharsets.UTF_8));
            }
            case RespMessage.RespArray a -> {
                out.write('*');
                if (a.elements() == null) {
                    out.write("-1\r\n".getBytes(StandardCharsets.UTF_8));
                } else {
                    out.write(Integer.toString(a.elements().size()).getBytes(StandardCharsets.UTF_8));
                    out.write(CRLF);
                    for (RespMessage element : a.elements()) {
                        writeMessage(element);
                    }
                }
            }
            case RespMessage.Null _ -> {
                out.write("_\r\n".getBytes(StandardCharsets.UTF_8));
            }
            case RespMessage.Boolean b -> {
                out.write((b.value() ? "#t\r\n" : "#f\r\n").getBytes(StandardCharsets.UTF_8));
            }
            case RespMessage.Double d -> {
                out.write(',');
                out.write(java.lang.Double.toString(d.value()).getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
            }
            case RespMessage.BigNumber n -> {
                out.write('(');
                out.write(n.value().getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
            }
            case RespMessage.VerbatimString v -> {
                out.write('=');
                int len = v.value().length() + 4; // format:3 + ':'
                out.write(Integer.toString(len).getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
                out.write(v.format().getBytes(StandardCharsets.UTF_8));
                out.write(':');
                out.write(v.value().getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
            }
            case RespMessage.RespMap m -> {
                out.write('%');
                out.write(Integer.toString(m.elements().size()).getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
                for (Map.Entry<RespMessage, RespMessage> entry : m.elements().entrySet()) {
                    writeMessage(entry.getKey());
                    writeMessage(entry.getValue());
                }
            }
            case RespMessage.RespSet s -> {
                out.write('~');
                out.write(Integer.toString(s.elements().size()).getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
                for (RespMessage element : s.elements()) {
                    writeMessage(element);
                }
            }
        }
    }
}
