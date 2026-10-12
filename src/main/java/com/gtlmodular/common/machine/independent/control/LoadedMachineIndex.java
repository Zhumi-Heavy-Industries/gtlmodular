package com.gtlmodular.common.machine.independent.control;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public final class LoadedMachineIndex<T> {
    private final Set<T> loaded = Collections.newSetFromMap(new WeakHashMap<>());

    public synchronized void add(T machine) { loaded.add(machine); }
    public synchronized void remove(T machine) { loaded.remove(machine); }
    public synchronized void clear() { loaded.clear(); }
    public void visit(Consumer<T> visitor) {
        List<T> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(loaded);
        }
        // 回调可能触发机器生命周期，不能持有索引锁执行。
        for (T machine : snapshot) visitor.accept(machine);
    }
}
