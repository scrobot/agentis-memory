package io.agentis.memory.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CachedClockTest {

    @Test
    void nowReturnsApproximateCurrentTime() {
        long real = System.currentTimeMillis();
        long cached = CachedClock.now();
        assertTrue(Math.abs(cached - real) < 5,
                "Cached time " + cached + " too far from real time " + real);
    }

    @Test
    void nowAdvancesOverTime() throws InterruptedException {
        long before = CachedClock.now();
        Thread.sleep(10);
        long after = CachedClock.now();
        assertTrue(after >= before, "Cached time should advance");
    }
}
