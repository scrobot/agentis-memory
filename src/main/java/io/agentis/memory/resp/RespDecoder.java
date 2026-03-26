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
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // TODO: implement RESP v2 parsing
        // Types: + (SimpleString), - (Error), : (Integer), $ (BulkString), * (Array)
    }
}
