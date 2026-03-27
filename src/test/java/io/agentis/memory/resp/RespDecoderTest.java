package io.agentis.memory.resp;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RespDecoderTest {

    private RespMessage parse(String input) throws IOException {
        RespParser parser = new RespParser(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
        return parser.readMessage();
    }

    @Test
    void testSimpleString() throws Exception {
        RespMessage msg = parse("+OK\r\n");
        assertInstanceOf(RespMessage.SimpleString.class, msg);
        assertEquals("OK", ((RespMessage.SimpleString) msg).value());
    }

    @Test
    void testInteger() throws Exception {
        RespMessage msg = parse(":1000\r\n");
        assertInstanceOf(RespMessage.RespInteger.class, msg);
        assertEquals(1000L, ((RespMessage.RespInteger) msg).value());
    }

    @Test
    void testBulkString() throws Exception {
        RespMessage msg = parse("$5\r\nhello\r\n");
        assertInstanceOf(RespMessage.BulkString.class, msg);
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), ((RespMessage.BulkString) msg).value());
    }

    @Test
    void testNullBulkString() throws Exception {
        RespMessage msg = parse("$-1\r\n");
        assertInstanceOf(RespMessage.NullBulkString.class, msg);
    }

    @Test
    void testArray() throws Exception {
        RespMessage msg = parse("*2\r\n$4\r\necho\r\n$5\r\nhello\r\n");
        assertInstanceOf(RespMessage.RespArray.class, msg);
        RespMessage.RespArray array = (RespMessage.RespArray) msg;
        assertEquals(2, array.elements().size());
    }

    @Test
    void testNull() throws Exception {
        RespMessage msg = parse("_\r\n");
        assertInstanceOf(RespMessage.Null.class, msg);
    }

    @Test
    void testBoolean() throws Exception {
        RespParser parser = new RespParser(new ByteArrayInputStream("#t\r\n#f\r\n".getBytes(StandardCharsets.UTF_8)));
        RespMessage msg1 = parser.readMessage();
        RespMessage msg2 = parser.readMessage();
        assertTrue(((RespMessage.Boolean) msg1).value());
        assertFalse(((RespMessage.Boolean) msg2).value());
    }

    @Test
    void testDouble() throws Exception {
        RespMessage msg = parse(",3.14\r\n");
        assertInstanceOf(RespMessage.Double.class, msg);
        assertEquals(3.14, ((RespMessage.Double) msg).value());
    }

    @Test
    void testBigNumber() throws Exception {
        RespMessage msg = parse("(12345678901234567890\r\n");
        assertInstanceOf(RespMessage.BigNumber.class, msg);
        assertEquals("12345678901234567890", ((RespMessage.BigNumber) msg).value());
    }

    @Test
    void testVerbatimString() throws Exception {
        RespMessage msg = parse("=15\r\ntxt:Some string\r\n");
        assertInstanceOf(RespMessage.VerbatimString.class, msg);
        RespMessage.VerbatimString v = (RespMessage.VerbatimString) msg;
        assertEquals("txt", v.format());
        assertEquals("Some string", v.value());
    }

    @Test
    void testMap() throws Exception {
        RespMessage msg = parse("%1\r\n+key\r\n:10\r\n");
        assertInstanceOf(RespMessage.RespMap.class, msg);
        RespMessage.RespMap map = (RespMessage.RespMap) msg;
        assertEquals(1, map.elements().size());
        assertEquals(new RespMessage.RespInteger(10), map.elements().get(new RespMessage.SimpleString("key")));
    }

    @Test
    void testSet() throws Exception {
        RespMessage msg = parse("~2\r\n+a\r\n+b\r\n");
        assertInstanceOf(RespMessage.RespSet.class, msg);
        RespMessage.RespSet set = (RespMessage.RespSet) msg;
        assertEquals(2, set.elements().size());
        assertTrue(set.elements().contains(new RespMessage.SimpleString("a")));
        assertTrue(set.elements().contains(new RespMessage.SimpleString("b")));
    }

    @Test
    void testHasBufferedDataForPipelining() throws Exception {
        // Simulate two pipelined commands: SET key val + GET key
        String twoCommands =
                "*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$3\r\nval\r\n" +
                "*2\r\n$3\r\nGET\r\n$3\r\nkey\r\n";
        RespParser parser = new RespParser(
                new ByteArrayInputStream(twoCommands.getBytes(StandardCharsets.UTF_8)));

        // Parse first command
        RespMessage msg1 = parser.readMessage();
        assertInstanceOf(RespMessage.RespArray.class, msg1);
        assertEquals(3, ((RespMessage.RespArray) msg1).elements().size());

        // After first command, buffer should have second command
        assertTrue(parser.hasBufferedData(), "Should detect pipelined data");

        // Parse second command
        RespMessage msg2 = parser.readMessage();
        assertInstanceOf(RespMessage.RespArray.class, msg2);
        assertEquals(2, ((RespMessage.RespArray) msg2).elements().size());

        // No more data
        assertFalse(parser.hasBufferedData(), "No more buffered data");
    }
}
