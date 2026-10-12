package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.control.LoadedMachineIndex;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class LoadedMachineIndexTest {
    @Test
    void concurrentLoadsUnloadsAndVisitsKeepAllRemainingMachines() throws Exception {
        var index = new LoadedMachineIndex<Object>();
        List<Object> machines = IntStream.range(0, 256).mapToObj(i -> new Object()).toList();
        Set<Object> known = Set.copyOf(machines);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(3);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int worker = 0; worker < 2; worker++) {
                List<Object> owned = machines.subList(worker * 128, (worker + 1) * 128);
                tasks.add(executor.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    for (int round = 0; round < 100; round++) {
                        owned.forEach(index::add);
                        owned.forEach(index::remove);
                    }
                    for (int i = 0; i < owned.size(); i += 2) index.add(owned.get(i));
                    return null;
                }));
            }
            tasks.add(executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                for (int round = 0; round < 2000; round++) {
                    Set<Object> visited = new HashSet<>();
                    index.visit(machine -> {
                        assertTrue(known.contains(machine));
                        assertTrue(visited.add(machine), "同一轮遍历不能重复唤醒机器");
                    });
                }
                return null;
            }));
            start.countDown();
            for (Future<?> task : tasks) task.get(10, TimeUnit.SECONDS);
            Set<Object> expected = new HashSet<>();
            for (int i = 0; i < machines.size(); i += 2) expected.add(machines.get(i));
            Set<Object> remaining = new HashSet<>();
            index.visit(remaining::add);
            assertEquals(expected, remaining);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void callbacksAllowConcurrentWorldCleanupAndVisitTheOriginalSnapshot() throws Exception {
        var index = new LoadedMachineIndex<Object>();
        Object first = new Object();
        Object second = new Object();
        Object replacement = new Object();
        index.add(first);
        index.add(second);
        var executor = Executors.newSingleThreadExecutor();
        try {
            List<Object> visited = new ArrayList<>();
            index.visit(machine -> {
                visited.add(machine);
                assertDoesNotThrow(() -> executor.submit(() -> {
                    index.remove(machine);
                    index.clear();
                    index.add(replacement);
                }).get(5, TimeUnit.SECONDS));
            });
            assertEquals(2, visited.size());
            assertEquals(Set.of(first, second), Set.copyOf(visited));
            visited.clear();
            index.visit(visited::add);
            assertEquals(List.of(replacement), visited);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
