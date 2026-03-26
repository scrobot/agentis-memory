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

    @Test
    void testNull() throws Exception {
        ByteBuf buf = Unpooled.copiedBuffer("_\r\n", StandardCharsets.UTF_8);
        RespDecoder decoder = new RespDecoder();
        java.util.List<Object> out = new java.util.ArrayList<>();
        decoder.decode(null, buf, out);
        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof RespMessage.Null);
    }

    @Test
    void testBoolean() throws Exception {
        ByteBuf buf = Unpooled.copiedBuffer("#t\r\n#f\r\n", StandardCharsets.UTF_8);
        RespDecoder decoder = new RespDecoder();
        java.util.List<Object> out = new java.util.ArrayList<>();
        decoder.decode(null, buf, out);
        assertEquals(2, out.size());
        assertTrue(((RespMessage.Boolean) out.get(0)).value());
        assertFalse(((RespMessage.Boolean) out.get(1)).value());
    }

    @Test
    void testDouble() throws Exception {
        ByteBuf buf = Unpooled.copiedBuffer(",3.14\r\n", StandardCharsets.UTF_8);
        RespDecoder decoder = new RespDecoder();
        java.util.List<Object> out = new java.util.ArrayList<>();
        decoder.decode(null, buf, out);
        assertEquals(1, out.size());
        assertEquals(3.14, ((RespMessage.Double) out.get(0)).value());
    }

    @Test
    void testBigNumber() throws Exception {
        ByteBuf buf = Unpooled.copiedBuffer("(12345678901234567890\r\n", StandardCharsets.UTF_8);
        RespDecoder decoder = new RespDecoder();
        java.util.List<Object> out = new java.util.ArrayList<>();
        decoder.decode(null, buf, out);
        assertEquals(1, out.size());
        assertEquals("12345678901234567890", ((RespMessage.BigNumber) out.get(0)).value());
    }

    @Test
    void testVerbatimString() throws Exception {
        ByteBuf buf = Unpooled.copiedBuffer("=15\r\ntxt:Some string\r\n", StandardCharsets.UTF_8);
        RespDecoder decoder = new RespDecoder();
        java.util.List<Object> out = new java.util.ArrayList<>();
        decoder.decode(null, buf, out);
        assertEquals(1, out.size());
        RespMessage.VerbatimString v = (RespMessage.VerbatimString) out.get(0);
        assertEquals("txt", v.format());
        assertEquals("Some string", v.value());
    }

    @Test
    void testMap() throws Exception {
        ByteBuf buf = Unpooled.copiedBuffer("%1\r\n+key\r\n:10\r\n", StandardCharsets.UTF_8);
        RespDecoder decoder = new RespDecoder();
        java.util.List<Object> out = new java.util.ArrayList<>();
        decoder.decode(null, buf, out);
        assertEquals(1, out.size());
        RespMessage.RespMap map = (RespMessage.RespMap) out.get(0);
        assertEquals(1, map.elements().size());
        assertEquals(new RespMessage.RespInteger(10), map.elements().get(new RespMessage.SimpleString("key")));
    }

    @Test
    void testSet() throws Exception {
        ByteBuf buf = Unpooled.copiedBuffer("~2\r\n+a\r\n+b\r\n", StandardCharsets.UTF_8);
        RespDecoder decoder = new RespDecoder();
        java.util.List<Object> out = new java.util.ArrayList<>();
        decoder.decode(null, buf, out);
        assertEquals(1, out.size());
        RespMessage.RespSet set = (RespMessage.RespSet) out.get(0);
        assertEquals(2, set.elements().size());
        assertTrue(set.elements().contains(new RespMessage.SimpleString("a")));
        assertTrue(set.elements().contains(new RespMessage.SimpleString("b")));
    }
}
