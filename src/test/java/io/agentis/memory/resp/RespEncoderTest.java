package io.agentis.memory.resp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RespEncoderTest {

    @Test
    void testEncodeRESP2() {
        assertEncode(new RespMessage.SimpleString("OK"), "+OK\r\n");
        assertEncode(new RespMessage.Error("ERR"), "-ERR\r\n");
        assertEncode(new RespMessage.RespInteger(42), ":42\r\n");
        assertEncode(new RespMessage.BulkString("hello".getBytes(StandardCharsets.UTF_8)), "$5\r\nhello\r\n");
        assertEncode(new RespMessage.NullBulkString(), "$-1\r\n");
        assertEncode(new RespMessage.RespArray(List.of(new RespMessage.SimpleString("A"))), "*1\r\n+A\r\n");
    }

    @Test
    void testEncodeRESP3() {
        assertEncode(new RespMessage.Null(), "_\r\n");
        assertEncode(new RespMessage.Boolean(true), "#t\r\n");
        assertEncode(new RespMessage.Boolean(false), "#f\r\n");
        assertEncode(new RespMessage.Double(3.14), ",3.14\r\n");
        assertEncode(new RespMessage.BigNumber("123"), "(123\r\n");
        assertEncode(new RespMessage.VerbatimString("txt", "hello"), "=9\r\ntxt:hello\r\n");

        Map<RespMessage, RespMessage> map = new LinkedHashMap<>();
        map.put(new RespMessage.SimpleString("a"), new RespMessage.RespInteger(1));
        assertEncode(new RespMessage.RespMap(map), "%1\r\n+a\r\n:1\r\n");

        assertEncode(new RespMessage.RespSet(Set.of(new RespMessage.SimpleString("s"))), "~1\r\n+s\r\n");
    }

    private void assertEncode(RespMessage msg, String expected) {
        ByteBuf buf = Unpooled.buffer();
        RespEncoder encoder = new RespEncoder();
        encoder.encode(null, msg, buf);
        String actual = buf.toString(StandardCharsets.UTF_8);
        assertEquals(expected, actual);
    }
}
