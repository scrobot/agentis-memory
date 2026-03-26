package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

// COMMAND / COMMAND DOCS (stub for Redis client compatibility)
public class CommandMetaCommand implements CommandHandler {

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        return RespMessage.Array.EMPTY;
    }
}
