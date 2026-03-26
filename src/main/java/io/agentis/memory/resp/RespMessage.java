package io.agentis.memory.resp;

import java.util.List;

public sealed interface RespMessage {

    record SimpleString(String value) implements RespMessage {}
    record Error(String message) implements RespMessage {}
    record Integer(long value) implements RespMessage {}
    record BulkString(byte[] value) implements RespMessage {
        public static final BulkString NULL = new BulkString(null);
    }
    record Array(List<RespMessage> elements) implements RespMessage {
        public static final Array NULL = new Array(null);
        public static final Array EMPTY = new Array(List.of());
    }
}
