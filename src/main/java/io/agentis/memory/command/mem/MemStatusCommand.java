package io.agentis.memory.command.mem;

import io.agentis.memory.command.CommandHandler;
import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.vector.VectorEngine;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

// MEMSTATUS key
// Returns [status, chunk_count, dimensions, last_updated_ms]
// status: indexed | pending | error
public class MemStatusCommand implements CommandHandler {

    private final VectorEngine vectorEngine;

    public MemStatusCommand(VectorEngine vectorEngine) {
        this.vectorEngine = vectorEngine;
    }

    @Override
    public RespMessage handle(ChannelHandlerContext ctx, List<byte[]> args) {
        // TODO: look up indexation state for key, return 4-element array
        return new RespMessage.Error("ERR not implemented");
    }
}
