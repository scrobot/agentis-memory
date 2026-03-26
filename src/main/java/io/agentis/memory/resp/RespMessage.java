package io.agentis.memory.resp;

import java.util.List;

public sealed interface RespMessage {

    record SimpleString(String value) implements RespMessage {}
    record Error(String message) implements RespMessage {}
    record RespInteger(long value) implements RespMessage {}
    record BulkString(byte[] value) implements RespMessage {}
    record NullBulkString() implements RespMessage {}
    record RespArray(List<RespMessage> elements) implements RespMessage {}
}
