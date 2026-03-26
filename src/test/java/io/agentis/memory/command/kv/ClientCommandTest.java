package io.agentis.memory.command.kv;

import io.agentis.memory.resp.RespMessage;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientCommandTest {

    private ClientCommand cmd;
    private ChannelHandlerContext ctx;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        cmd = new ClientCommand();
        channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ctx = channel.pipeline().firstContext();
    }

    @Test
    void setNameReturnsOk() {
        RespMessage result = cmd.handle(ctx, args("CLIENT", "SETNAME", "myapp"));
        assertEquals("OK", ((RespMessage.SimpleString) result).value());
    }

    @Test
    void getNameAfterSetName() {
        cmd.handle(ctx, args("CLIENT", "SETNAME", "myapp"));
        RespMessage result = cmd.handle(ctx, args("CLIENT", "GETNAME"));
        assertInstanceOf(RespMessage.BulkString.class, result);
        assertEquals("myapp", new String(((RespMessage.BulkString) result).value(), StandardCharsets.UTF_8));
    }

    @Test
    void getNameBeforeSetNameReturnsNull() {
        RespMessage result = cmd.handle(ctx, args("CLIENT", "GETNAME"));
        assertInstanceOf(RespMessage.NullBulkString.class, result);
    }

    @Test
    void infoReturnsBulkString() {
        RespMessage result = cmd.handle(ctx, args("CLIENT", "INFO"));
        assertInstanceOf(RespMessage.BulkString.class, result);
    }

    @Test
    void unknownSubcommandReturnsError() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(ctx, args("CLIENT", "UNKNOWN")));
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(ctx, args("CLIENT")));
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
