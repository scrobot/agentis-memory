package io.agentis.memory.resp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Netty encoder: RespMessage → raw bytes.
 */
public class RespEncoder extends MessageToByteEncoder<RespMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, RespMessage msg, ByteBuf out) {
        // TODO: implement RESP v2 serialization
    }
}
