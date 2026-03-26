package io.agentis.memory.resp;

/**
 * Thrown by QuitCommand to signal that the connection should be closed
 * after sending the OK response.
 */
public class QuitException extends RuntimeException {
    public QuitException() {
        super("QUIT");
    }
}
