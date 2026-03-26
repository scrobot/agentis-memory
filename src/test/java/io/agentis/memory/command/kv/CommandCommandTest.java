package io.agentis.memory.command.kv;

import io.agentis.memory.resp.RespMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandCommandTest {

    private final CommandCommand cmd = new CommandCommand();

    @Test
    void returnsEmptyArray() {
        RespMessage result = cmd.handle(null, args("COMMAND"));
        assertInstanceOf(RespMessage.RespArray.class, result);
        assertEquals(0, ((RespMessage.RespArray) result).elements().size());
    }

    @Test
    void returnsEmptyArrayForDocs() {
        RespMessage result = cmd.handle(null, args("COMMAND", "DOCS"));
        assertInstanceOf(RespMessage.RespArray.class, result);
        assertEquals(0, ((RespMessage.RespArray) result).elements().size());
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
