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
        }
    }
}
