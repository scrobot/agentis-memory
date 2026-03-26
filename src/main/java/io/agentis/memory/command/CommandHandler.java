package io.agentis.memory.command;

import io.agentis.memory.resp.RespMessage;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

public interface CommandHandler {

    RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args);

    /**
     * @return The primary name of the command (e.g., "SET").
     */
    String name();

    /**
     * @return Optional aliases for the command (e.g., "SETEX" for SET).
     */
    default List<String> aliases() {
        return List.of();
    }

    /**
     * @return True if this command modifies the data and should be persisted to AOF.
     */
    default boolean isWriteCommand() {
        return false;
    }
}
