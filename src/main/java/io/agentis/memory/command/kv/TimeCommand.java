package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;

// TIME
// Returns [unix_seconds, microseconds] as an array of bulk strings.
@Singleton
public class TimeCommand implements CommandHandler {

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        long nowMicros = System.currentTimeMillis() * 1000;
        long seconds = nowMicros / 1_000_000;
        long micros = nowMicros % 1_000_000;

        RespMessage secondsMsg = new RespMessage.BulkString(Long.toString(seconds).getBytes(StandardCharsets.UTF_8));
        RespMessage microsMsg = new RespMessage.BulkString(Long.toString(micros).getBytes(StandardCharsets.UTF_8));

        return new RespMessage.RespArray(List.of(secondsMsg, microsMsg));
    }

    @Override
    public String name() {
        return "TIME";
    }
}
