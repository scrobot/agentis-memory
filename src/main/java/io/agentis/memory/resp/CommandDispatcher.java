package io.agentis.memory.resp;

import io.agentis.memory.command.CommandRouter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty handler: receives decoded RespMessage, dispatches to CommandRouter,
 * writes response back to channel.
 */
public class CommandDispatcher extends SimpleChannelInboundHandler<RespMessage> {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);

    private final CommandRouter router;

    public CommandDispatcher(CommandRouter router) {
        this.router = router;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.debug("Client connected: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.debug("Client disconnected: {}", ctx.channel().remoteAddress());
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
        log.warn("Channel error from {}: {}", ctx.channel().remoteAddress(), cause.getMessage());
        ctx.writeAndFlush(new RespMessage.Error("ERR " + cause.getMessage()));
    }
}
