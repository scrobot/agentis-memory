package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.ClientConnection;
import io.agentis.memory.resp.RespMessage;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;

// CLIENT SETNAME name | CLIENT GETNAME | CLIENT INFO
// Client name is stored in connection attribute.
@Singleton
public class ClientCommand implements CommandHandler {

    public static final String CLIENT_NAME = "clientName";

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'CLIENT'");
        }
        String sub = new String(args.get(1)).toUpperCase();
        return switch (sub) {
            case "SETNAME" -> {
                if (args.size() < 3) {
                    yield new RespMessage.Error("ERR wrong number of arguments for 'CLIENT SETNAME'");
                }
                String name = new String(args.get(2));
                conn.setAttribute(CLIENT_NAME, name);
                yield new RespMessage.SimpleString("OK");
            }
            case "GETNAME" -> {
                String name = (String) conn.getAttribute(CLIENT_NAME);
                yield name != null
                        ? new RespMessage.BulkString(name.getBytes(StandardCharsets.UTF_8))
                        : new RespMessage.NullBulkString();
            }
            case "INFO" -> {
                String name = (String) conn.getAttribute(CLIENT_NAME);
                String info = "id=1 addr=" + conn.remoteAddress()
                        + " cmd=client name=" + (name != null ? name : "") + "\n";
                yield new RespMessage.BulkString(info.getBytes(StandardCharsets.UTF_8));
            }
            case "LIST" -> new RespMessage.BulkString("".getBytes(StandardCharsets.UTF_8));
            case "ID" -> new RespMessage.RespInteger(1);
            case "NO-EVICT", "NO-TOUCH", "CACHING", "REPLY", "UNPAUSE", "PAUSE" ->
                    new RespMessage.SimpleString("OK");
            default -> new RespMessage.Error("ERR Unknown subcommand '" + sub + "' for 'CLIENT'");
        };
    }

    @Override
    public String name() {
        return "CLIENT";
    }
}
