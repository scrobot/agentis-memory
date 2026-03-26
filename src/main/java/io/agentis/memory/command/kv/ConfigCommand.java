package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;

// CONFIG GET pattern — returns key-value pairs for known config params.
// Supports: save, appendonly. Unknown params return empty array.
@Singleton
public class ConfigCommand implements CommandHandler {

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'CONFIG'");
        }
        String sub = new String(args.get(1)).toUpperCase();
        if (sub.equals("GET")) {
            if (args.size() < 3) {
                return new RespMessage.Error("ERR wrong number of arguments for 'CONFIG GET'");
            }
            String param = new String(args.get(2)).toLowerCase();
            return switch (param) {
                case "save" -> arrayOf("save", "3600 1 300 100 60 10000");
                case "appendonly" -> arrayOf("appendonly", "no");
                case "maxmemory" -> arrayOf("maxmemory", "0");
                case "maxmemory-policy" -> arrayOf("maxmemory-policy", "noeviction");
                case "hz" -> arrayOf("hz", "10");
                case "bind" -> arrayOf("bind", "127.0.0.1");
                default -> new RespMessage.RespArray(List.of());
            };
        }
        if (sub.equals("SET")) {
            // Accept silently for compatibility
            return new RespMessage.SimpleString("OK");
        }
        if (sub.equals("RESETSTAT")) {
            return new RespMessage.SimpleString("OK");
        }
        return new RespMessage.Error("ERR Unknown subcommand '" + sub + "' for 'CONFIG'");
    }

    private static RespMessage.RespArray arrayOf(String key, String value) {
        return new RespMessage.RespArray(List.of(
                new RespMessage.BulkString(key.getBytes(StandardCharsets.UTF_8)),
                new RespMessage.BulkString(value.getBytes(StandardCharsets.UTF_8))
        ));
    }

    @Override
    public String name() {
        return "CONFIG";
    }
}
