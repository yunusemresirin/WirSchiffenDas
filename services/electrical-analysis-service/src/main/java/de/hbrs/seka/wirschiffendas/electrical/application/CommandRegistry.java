package de.hbrs.seka.wirschiffendas.electrical.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/** Local PoC deduplication. Completed entries expire; running work is never evicted. */
@Component
public class CommandRegistry {
    private final int capacity;
    private final Duration retention;
    private final Map<Key, Instant> commands = new LinkedHashMap<>();

    public CommandRegistry(@Value("${worker.deduplication.capacity:10000}") int capacity,
                           @Value("${worker.deduplication.retention:PT1H}") Duration retention) {
        if (capacity < 1 || retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Deduplication capacity and retention must be positive");
        }
        this.capacity = capacity;
        this.retention = retention;
    }

    public synchronized boolean begin(String analysisId, String attemptId) {
        Instant cutoff = Instant.now().minus(retention);
        commands.entrySet().removeIf(entry -> entry.getValue() != null && entry.getValue().isBefore(cutoff));
        Key key = new Key(analysisId, attemptId);
        if (commands.containsKey(key)) return false;
        if (commands.size() >= capacity) {
            var completed = commands.entrySet().stream().filter(entry -> entry.getValue() != null).findFirst();
            if (completed.isEmpty()) throw new RejectedExecutionException("Worker deduplication registry is full");
            commands.remove(completed.get().getKey());
        }
        commands.put(key, null);
        return true;
    }

    public synchronized void complete(String analysisId, String attemptId) {
        commands.replace(new Key(analysisId, attemptId), Instant.now());
    }

    private record Key(String analysisId, String attemptId) {}
}
