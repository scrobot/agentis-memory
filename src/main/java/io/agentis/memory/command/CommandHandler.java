package io.agentis.memory.command;

import io.agentis.memory.resp.RespMessage;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

public interface CommandHandler {

    RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args);
}
