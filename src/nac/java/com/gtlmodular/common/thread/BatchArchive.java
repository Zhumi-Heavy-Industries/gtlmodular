package com.gtlmodular.common.thread;

import java.util.List;

public record BatchArchive<E>(List<Entry<E>> entries) {
    public BatchArchive {
        entries = List.copyOf(entries);
    }

    public record Entry<E>(E payload, int duration, int progress) {}
}
