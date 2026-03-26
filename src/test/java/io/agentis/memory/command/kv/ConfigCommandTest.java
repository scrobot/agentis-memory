package io.agentis.memory.command.kv;

import io.agentis.memory.resp.RespMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigCommandTest {

    private final ConfigCommand cmd = new ConfigCommand();

    @Test
    void getKnownParamSave() {
        RespMessage result = cmd.handle(null, args("CONFIG", "GET", "save"));
        assertInstanceOf(RespMessage.RespArray.class, result);
        RespMessage.RespArray arr = (RespMessage.RespArray) result;
        assertEquals(2, arr.elements().size());
        assertEquals("save", new String(((RespMessage.BulkString) arr.elements().get(0)).value()));
    }

    @Test
    void getKnownParamAppendonly() {
        RespMessage result = cmd.handle(null, args("CONFIG", "GET", "appendonly"));
        assertInstanceOf(RespMessage.RespArray.class, result);
        RespMessage.RespArray arr = (RespMessage.RespArray) result;
        assertEquals(2, arr.elements().size());
        assertEquals("appendonly", new String(((RespMessage.BulkString) arr.elements().get(0)).value()));
    }

    @Test
    void getUnknownParamReturnsEmptyArray() {
        RespMessage result = cmd.handle(null, args("CONFIG", "GET", "unknown-param"));
        assertInstanceOf(RespMessage.RespArray.class, result);
        assertEquals(0, ((RespMessage.RespArray) result).elements().size());
    }

    @Test
    void setReturnsOk() {
        RespMessage result = cmd.handle(null, args("CONFIG", "SET", "hz", "15"));
        assertEquals("OK", ((RespMessage.SimpleString) result).value());
    }

    @Test
    void errorOnMissingArgs() {
        assertInstanceOf(RespMessage.Error.class, cmd.handle(null, args("CONFIG")));
    }

    private List<byte[]> args(String... parts) {
        return java.util.Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
