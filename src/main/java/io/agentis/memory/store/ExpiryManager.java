package io.agentis.memory.store;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Active expiry manager: periodically samples random keys with TTL and removes expired ones.
 * Mirrors Redis active expiry strategy: sample ~20 keys per cycle, repeat if >25% expired.
 */
@Singleton
public class ExpiryManager {

    private static final Logger log = LoggerFactory.getLogger(ExpiryManager.class);
    private static final int SAMPLE_SIZE = 20;
    private static final double REPEAT_THRESHOLD = 0.25;

    private final KvStore kvStore;
    private final ScheduledExecutorService scheduler;
    private final Random random = new Random();

    @Inject
    public ExpiryManager(KvStore kvStore) {
        this.kvStore = kvStore;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "expiry-manager");
            t.setDaemon(true);
            return t;
        });
        start();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::runExpiryPass, 1, 1, TimeUnit.SECONDS);
        log.info("ExpiryManager started");
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("ExpiryManager stopped");
    }

    void runExpiryPass() {
        try {
            double expiredRatio;
            do {
                expiredRatio = sampleAndExpire();
            } while (expiredRatio > REPEAT_THRESHOLD);
        } catch (Exception e) {
            log.warn("ExpiryManager pass failed", e);
        }
    }

    private double sampleAndExpire() {
        var store = kvStore.getStore();
        if (store.isEmpty()) return 0.0;

        // Collect keys that have a TTL set
        List<String> candidates = new ArrayList<>(SAMPLE_SIZE * 2);
        for (Map.Entry<String, KvStore.Entry> e : store.entrySet()) {
            if (e.getValue().expireAt() != -1) {
                candidates.add(e.getKey());
                if (candidates.size() >= SAMPLE_SIZE * 4) break;
            }
        }

        if (candidates.isEmpty()) return 0.0;

        int sampleSize = Math.min(SAMPLE_SIZE, candidates.size());
        List<String> sample = new ArrayList<>(sampleSize);
        if (candidates.size() <= sampleSize) {
            sample.addAll(candidates);
        } else {
            for (int i = 0; i < sampleSize; i++) {
                sample.add(candidates.get(random.nextInt(candidates.size())));
            }
        }

        int expired = 0;
        for (String key : sample) {
            KvStore.Entry e = store.get(key);
            if (e != null && e.isExpired()) {
                store.remove(key);
                expired++;
            }
        }

        return sampleSize == 0 ? 0.0 : (double) expired / sampleSize;
    }
}
