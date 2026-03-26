package io.agentis.memory.command.kv;

import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.RespMessage;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthCommandTest {

    private ChannelHandlerContext ctx;

    @BeforeEach
    void setUp() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ctx = channel.pipeline().firstContext();
    }

    @Test
    void errorWhenNoPasswordConfigured() {
        ServerConfig config = new ServerConfig();
        config.requirepass = null;
        AuthCommand cmd = new AuthCommand(config);
        RespMessage result = cmd.handle(ctx, args("AUTH", "anything"));
        assertInstanceOf(RespMessage.Error.class, result);
        assertTrue(((RespMessage.Error) result).message().contains("no password is set"));
    }

    @Test
    void okWithCorrectPassword() {
        ServerConfig config = new ServerConfig();
        config.requirepass = "secret";
        AuthCommand cmd = new AuthCommand(config);
        RespMessage result = cmd.handle(ctx, args("AUTH", "secret"));
        assertEquals("OK", ((RespMessage.SimpleString) result).value());
        assertTrue(Boolean.TRUE.equals(ctx.channel().attr(AuthCommand.AUTHENTICATED).get()));
    }

    @Test
    void errorWithWrongPassword() {
        ServerConfig config = new ServerConfig();
        config.requirepass = "secret";
        AuthCommand cmd = new AuthCommand(config);
        RespMessage result = cmd.handle(ctx, args("AUTH", "wrong"));
        assertInstanceOf(RespMessage.Error.class, result);
        assertFalse(Boolean.TRUE.equals(ctx.channel().attr(AuthCommand.AUTHENTICATED).get()));
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
