package io.agentis.memory.resp;

import io.agentis.memory.command.CommandRouter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * Netty handler: receives decoded RespMessage, dispatches to CommandRouter,
 * writes response back to channel.
 */
public class CommandDispatcher extends SimpleChannelInboundHandler<RespMessage> {

    private final CommandRouter router;

    public CommandDispatcher(CommandRouter router) {
        this.router = router;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RespMessage msg) {
        RespMessage response = router.dispatch(ctx, msg);
        if (response != null) {
            ctx.writeAndFlush(response);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.writeAndFlush(new RespMessage.Error("ERR " + cause.getMessage()));
    }
}
