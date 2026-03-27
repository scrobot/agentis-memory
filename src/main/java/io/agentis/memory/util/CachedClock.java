package io.agentis.memory.util;

/**
 * Cached system clock updated every ~1ms by a daemon thread.
 * Avoids System.currentTimeMillis() syscalls on every KV operation.
 * Resolution: 1ms (sufficient for TTL, which has minimum 1ms granularity).
 */
public final class CachedClock {
    private static volatile long currentTime = System.currentTimeMillis();

    static {
        Thread updater = new Thread(() -> {
            while (true) {
                currentTime = System.currentTimeMillis();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "cached-clock");
        updater.setDaemon(true);
        updater.start();
    }

    private CachedClock() {}

    public static long now() {
        return currentTime;
    }
}
