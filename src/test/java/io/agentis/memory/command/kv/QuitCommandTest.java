package io.agentis.memory.command.kv;

import io.agentis.memory.resp.RespEncoder;
import io.agentis.memory.resp.RespMessage;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuitCommandTest {

    private final QuitCommand cmd = new QuitCommand();

    @Test
    void closesChannelAndReturnsNull() {
        EmbeddedChannel channel = new EmbeddedChannel(new RespEncoder());
        var ctx = channel.pipeline().firstContext();

        RespMessage result = cmd.handle(ctx, args("QUIT"));
        // QUIT writes +OK and closes, returns null to avoid double-write
        assertNull(result);
        channel.runPendingTasks();
        assertFalse(channel.isActive());
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
