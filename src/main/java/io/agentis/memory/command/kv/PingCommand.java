package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.resp.ClientConnection;
import jakarta.inject.Singleton;

import java.util.List;

// PING [message]
@Singleton
public class PingCommand implements CommandHandler {

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() > 1) {
            return new RespMessage.BulkString(args.get(1));
        }
        return new RespMessage.SimpleString("PONG");
    }

    @Override
    public String name() {
        return "PING";
    }
}
