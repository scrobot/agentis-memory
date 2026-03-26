package io.agentis.memory.resp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RespDecoderTest {

    @Test
    void testSimpleString() throws Exception {
        ByteBuf buf = Unpooled.copiedBuffer("+OK\r\n", StandardCharsets.UTF_8);
        RespDecoder decoder = new RespDecoder();
        java.util.List<Object> out = new java.util.ArrayList<>();
        decoder.decode(null, buf, out);
        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof RespMessage.SimpleString);
        assertEquals("OK", ((RespMessage.SimpleString) out.get(0)).value());
    }

    @Test
    void testInteger() throws Exception {
        ByteBuf buf = Unpooled.copiedBuffer(":1000\r\n", StandardCharsets.UTF_8);
        RespDecoder decoder = new RespDecoder();
        java.util.List<Object> out = new java.util.ArrayList<>();
        decoder.decode(null, buf, out);
        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof RespMessage.RespInteger);
        assertEquals(1000L, ((RespMessage.RespInteger) out.get(0)).value());
    }

    @Test
    void testBulkString() throws Exception {
        ByteBuf buf = Unpooled.copiedBuffer("$5\r\nhello\r\n", StandardCharsets.UTF_8);
        RespDecoder decoder = new RespDecoder();
        java.util.List<Object> out = new java.util.ArrayList<>();
        decoder.decode(null, buf, out);
        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof RespMessage.BulkString);
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), ((RespMessage.BulkString) out.get(0)).value());
    }

    @Test
    void testNullBulkString() throws Exception {
        ByteBuf buf = Unpooled.copiedBuffer("$-1\r\n", StandardCharsets.UTF_8);
        RespDecoder decoder = new RespDecoder();
        java.util.List<Object> out = new java.util.ArrayList<>();
        decoder.decode(null, buf, out);
        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof RespMessage.NullBulkString);
    }

    @Test
    void testArray() throws Exception {
        ByteBuf buf = Unpooled.copiedBuffer("*2\r\n$4\r\necho\r\n$5\r\nhello\r\n", StandardCharsets.UTF_8);
        RespDecoder decoder = new RespDecoder();
        java.util.List<Object> out = new java.util.ArrayList<>();
        decoder.decode(null, buf, out);
        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof RespMessage.RespArray);
        RespMessage.RespArray array = (RespMessage.RespArray) out.get(0);
        assertEquals(2, array.elements().size());
    }
}
