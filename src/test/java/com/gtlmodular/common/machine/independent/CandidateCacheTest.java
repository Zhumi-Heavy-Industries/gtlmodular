package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.adapter.CandidateCache;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CandidateCacheTest {

    @Test
    void cacheLivesForItsTtlAndReloadsAfterExpiry() {
        CandidateCache<String> cache = new CandidateCache<>();
        AtomicInteger loads = new AtomicInteger();
        var loader = (java.util.function.Supplier<List<String>>) () -> {
            loads.incrementAndGet();
            return List.of("a", "b");
        };

        assertEquals(List.of("a", "b"), cache.get(0, 5, loader));
        assertEquals(List.of("a", "b"), cache.get(4, 5, loader));
        assertEquals(1, loads.get());
        assertEquals(List.of("a", "b"), cache.get(5, 5, loader));
        assertEquals(2, loads.get());
    }

    @Test
    void zeroTtlDisablesCachingAndInvalidateForcesAReload() {
        CandidateCache<String> cache = new CandidateCache<>();
        AtomicInteger loads = new AtomicInteger();
        var loader = (java.util.function.Supplier<List<String>>) () -> {
            loads.incrementAndGet();
            return List.of("x");
        };

        cache.get(0, 0, loader);
        cache.get(0, 0, loader);
        assertEquals(2, loads.get());

        cache.get(0, 5, loader);
        cache.invalidate();
        cache.get(1, 5, loader);
        assertEquals(4, loads.get());
    }
}
