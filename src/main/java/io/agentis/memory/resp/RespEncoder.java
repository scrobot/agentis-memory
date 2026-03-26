package io.agentis.memory.resp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Netty encoder: RespMessage → raw bytes.
 */
public class RespEncoder extends MessageToByteEncoder<RespMessage> {

    private static final byte[] CRLF = "\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Override
    protected void encode(ChannelHandlerContext ctx, RespMessage msg, ByteBuf out) {
        writeMessage(out, msg);
    }

    private void writeMessage(ByteBuf out, RespMessage msg) {
        switch (msg) {
            case RespMessage.SimpleString s -> {
                out.writeByte('+');
                out.writeBytes(s.value().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.writeBytes(CRLF);
            }
            case RespMessage.Error e -> {
                out.writeByte('-');
                out.writeBytes(e.message().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.writeBytes(CRLF);
            }
            case RespMessage.RespInteger i -> {
                out.writeByte(':');
                out.writeBytes(java.lang.Long.toString(i.value()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.writeBytes(CRLF);
            }
            case RespMessage.BulkString b -> {
                out.writeByte('$');
                out.writeBytes(java.lang.Integer.toString(b.value().length).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.writeBytes(CRLF);
                out.writeBytes(b.value());
                out.writeBytes(CRLF);
            }
            case RespMessage.NullBulkString _ -> {
                out.writeBytes("$-1\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            case RespMessage.RespArray a -> {
                out.writeByte('*');
                if (a.elements() == null) {
                    out.writeBytes("-1\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    out.writeBytes(java.lang.Integer.toString(a.elements().size()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    out.writeBytes(CRLF);
                    for (RespMessage element : a.elements()) {
                        writeMessage(out, element);
                    }
                }
            }
            case RespMessage.Null _ -> {
                out.writeBytes("_\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            case RespMessage.Boolean b -> {
                out.writeBytes((b.value() ? "#t\r\n" : "#f\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            case RespMessage.Double d -> {
                out.writeByte(',');
                out.writeBytes(java.lang.Double.toString(d.value()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.writeBytes(CRLF);
            }
            case RespMessage.BigNumber n -> {
                out.writeByte('(');
                out.writeBytes(n.value().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.writeBytes(CRLF);
            }
            case RespMessage.VerbatimString v -> {
                out.writeByte('=');
                int len = v.value().length() + 4; // format:3 + ':'
                out.writeBytes(java.lang.Integer.toString(len).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.writeBytes(CRLF);
                out.writeBytes(v.format().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.writeByte(':');
                out.writeBytes(v.value().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.writeBytes(CRLF);
            }
            case RespMessage.RespMap m -> {
                out.writeByte('%');
                out.writeBytes(java.lang.Integer.toString(m.elements().size()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.writeBytes(CRLF);
                for (java.util.Map.Entry<RespMessage, RespMessage> entry : m.elements().entrySet()) {
                    writeMessage(out, entry.getKey());
                    writeMessage(out, entry.getValue());
                }
            }
            case RespMessage.RespSet s -> {
                out.writeByte('~');
                out.writeBytes(java.lang.Integer.toString(s.elements().size()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.writeBytes(CRLF);
                for (RespMessage element : s.elements()) {
                    writeMessage(out, element);
                }
            }
        }
    }
}
