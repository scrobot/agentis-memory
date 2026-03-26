package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.ClientConnection;
import io.agentis.memory.resp.QuitException;
import io.agentis.memory.resp.RespMessage;
import jakarta.inject.Singleton;

import java.util.List;

// QUIT — responds +OK and signals the connection loop to close
@Singleton
public class QuitCommand implements CommandHandler {

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        // Throw QuitException; the server loop catches it, writes +OK, and closes.
        throw new QuitException();
    }

    @Override
    public String name() {
        return "QUIT";
    }
}
