package io.agentis.memory.command.server;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.command.kv.AuthCommand;
import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.resp.ClientConnection;
import io.agentis.memory.resp.RespMessage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HELLO [protover [AUTH username password] [SETNAME clientname]]
 */
@Singleton
public class HelloCommand implements CommandHandler {

    public static final String PROTOCOL_VERSION = "protocol_version";

    private final ServerConfig config;

    @Inject
    public HelloCommand(ServerConfig config) {
        this.config = config;
    }

    @Override
    public RespMessage handle(ClientConnection conn, List<byte[]> args) {
        int version = 2; // Default
        if (args.size() > 1) {
            try {
                version = Integer.parseInt(new String(args.get(1)));
            } catch (NumberFormatException e) {
                return new RespMessage.Error("ERR invalid protocol version");
            }
        }

        if (version != 2 && version != 3) {
            return new RespMessage.Error("NOPROTO unsupported protocol version");
        }

        // Handle AUTH and SETNAME if present
        for (int i = 2; i < args.size(); i++) {
            String arg = new String(args.get(i)).toUpperCase();
            if ("AUTH".equals(arg)) {
                if (i + 2 >= args.size()) {
                    return new RespMessage.Error("ERR syntax error");
                }
                String user = new String(args.get(i + 1));
                String pass = new String(args.get(i + 2));
                if (config.requirepass != null && !config.requirepass.isEmpty()) {
                    if (!config.requirepass.equals(pass)) {
                        return new RespMessage.Error("WRONGPASS invalid username-password pair or user is disabled.");
                    }
                }
                if (conn != null) {
                    conn.setAttribute(AuthCommand.AUTHENTICATED, true);
                }
                i += 2;
            } else if ("SETNAME".equals(arg)) {
                if (i + 1 >= args.size()) {
                    return new RespMessage.Error("ERR syntax error");
                }
                // We don't really store client name yet, but we accept it
                i += 1;
            }
        }

        if (conn != null) {
            conn.setAttribute(PROTOCOL_VERSION, version);
        }

        Map<RespMessage, RespMessage> info = new LinkedHashMap<>();
        info.put(new RespMessage.SimpleString("server"), new RespMessage.SimpleString("agentis-memory"));
        info.put(new RespMessage.SimpleString("version"), new RespMessage.SimpleString("0.1.0"));
        info.put(new RespMessage.SimpleString("proto"), new RespMessage.RespInteger(version));
        info.put(new RespMessage.SimpleString("id"), new RespMessage.RespInteger(conn != null ? conn.hashCode() : 0));
        info.put(new RespMessage.SimpleString("mode"), new RespMessage.SimpleString("standalone"));
        info.put(new RespMessage.SimpleString("role"), new RespMessage.SimpleString("master"));
        info.put(new RespMessage.SimpleString("modules"), new RespMessage.RespArray(List.of()));

        if (version == 3) {
            return new RespMessage.RespMap(info);
        } else {
            // Flatten map for RESP2
            List<RespMessage> flat = new java.util.ArrayList<>();
            for (Map.Entry<RespMessage, RespMessage> entry : info.entrySet()) {
                flat.add(entry.getKey());
                flat.add(entry.getValue());
            }
            return new RespMessage.RespArray(flat);
        }
    }

    @Override
    public String name() {
        return "HELLO";
    }
}
