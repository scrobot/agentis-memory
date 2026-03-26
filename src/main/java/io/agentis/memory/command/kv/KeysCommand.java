package io.agentis.memory.command.kv;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.KvStore;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// KEYS pattern — glob-match (*, ?, [abc]) over all keys.
// WARNING: debug-only command; prefer SCAN for production use.
@Singleton
public class KeysCommand implements CommandHandler {

    private final KvStore kvStore;

    @Inject
    public KeysCommand(KvStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        if (args.size() < 2) {
            return new RespMessage.Error("ERR wrong number of arguments for 'KEYS'");
        }
        String pattern = new String(args.get(1));
        Pattern regex = globToRegex(pattern);
        List<RespMessage> matched = new ArrayList<>();
        for (var entry : kvStore.getStore().entrySet()) {
            if (!entry.getValue().isExpired() && regex.matcher(entry.getKey()).matches()) {
                matched.add(new RespMessage.BulkString(entry.getKey().getBytes(StandardCharsets.UTF_8)));
            }
        }
        return new RespMessage.RespArray(matched);
    }

    @Override
    public String name() {
        return "KEYS";
    }

    static Pattern globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                case '[' -> {
                    sb.append('[');
                    i++;
                    while (i < glob.length() && glob.charAt(i) != ']') {
                        char gc = glob.charAt(i);
                        if (gc == '\\') sb.append('\\');
                        sb.append(gc);
                        i++;
                    }
                    sb.append(']');
                }
                case '.' , '(', ')', '{', '}', '+', '^', '$', '|', '\\' ->
                        sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        sb.append('$');
        return Pattern.compile(sb.toString());
    }
}
