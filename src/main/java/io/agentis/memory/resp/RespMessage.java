package io.agentis.memory.resp;

import java.util.List;
import java.util.Map;
import java.util.Set;

public sealed interface RespMessage {

    record SimpleString(String value) implements RespMessage {}
    record Error(String message) implements RespMessage {}
    record RespInteger(long value) implements RespMessage {}
    record BulkString(byte[] value) implements RespMessage {}
    record NullBulkString() implements RespMessage {}
    record RespArray(List<RespMessage> elements) implements RespMessage {}

    // RESP3
    record Null() implements RespMessage {}
    record Boolean(boolean value) implements RespMessage {}
    record Double(double value) implements RespMessage {}
    record BigNumber(String value) implements RespMessage {}
    record VerbatimString(String format, String value) implements RespMessage {}
    record RespMap(Map<RespMessage, RespMessage> elements) implements RespMessage {}
    record RespSet(Set<RespMessage> elements) implements RespMessage {}
}
