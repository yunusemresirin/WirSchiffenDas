package de.hbrs.seka.wirschiffendas.fluid.application;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CommandRegistryTest {
    @Test
    void concurrentDuplicatesAdmitOnlyOneWorker() throws Exception {
        var registry = new CommandRegistry(10, Duration.ofHours(1));
        var accepted = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 32; i++) futures.add(executor.submit(() -> {
                if (registry.begin("analysis", "attempt")) accepted.incrementAndGet();
            }));
            for (var future : futures) future.get();
        }
        assertEquals(1, accepted.get());
        registry.complete("analysis", "attempt");
        assertFalse(registry.begin("analysis", "attempt"));
        assertTrue(registry.begin("analysis", "new-attempt"));
    }

    @Test
    void boundedRegistryNeverEvictsRunningWork() {
        var registry = new CommandRegistry(1, Duration.ofHours(1));
        assertTrue(registry.begin("analysis", "attempt"));
        assertThrows(RejectedExecutionException.class, () -> registry.begin("other", "attempt"));
        assertFalse(registry.begin("analysis", "attempt"));
        registry.complete("analysis", "attempt");
        assertTrue(registry.begin("other", "attempt"));
    }
}
