package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.io.MeIo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MeIoTest {

    private static Map<Object, List<Object>> contents(long amount) {
        Map<Object, List<Object>> map = new LinkedHashMap<>();
        map.put("items", new ArrayList<>(List.of(amount)));
        return map;
    }

    @Test
    void longAmountsAreMovedWithoutIntTruncationAndLeaveAnExactRemainder() {
        long requested = 9_000_000_000L;
        long accepted = 4_000_000_000L;
        MeIo.OutputPart part = (values, simulate) -> {
            long left = (Long) values.get("items").get(0);
            long moved = Math.min(left, accepted);
            Map<Object, List<Object>> result = new LinkedHashMap<>();
            result.put("items", new ArrayList<>(List.of(left - moved)));
            return result;
        };

        var remaining = MeIo.transferParts(List.of(part), contents(requested), false);

        assertEquals(5_000_000_000L, remaining.get("items").get(0));
    }

    @Test
    void partsRunInOrderUntilTheRemainderIsEmpty() {
        MeIo.OutputPart first = (values, simulate) -> {
            Map<Object, List<Object>> result = new LinkedHashMap<>();
            result.put("items", new ArrayList<>(List.of(60L)));
            return result;
        };
        MeIo.OutputPart second = (values, simulate) -> new LinkedHashMap<>();

        var remaining = MeIo.transferParts(List.of(first, second), contents(100L), false);

        assertTrue(remaining.isEmpty());
    }

    @Test
    void emptyContentsNeedNoPart() {
        assertTrue(MeIo.transferParts(List.of(), new LinkedHashMap<>(), false).isEmpty());
        assertEquals(1, MeIo.transferParts(List.of((values, simulate) -> values), contents(7L), true).size());
    }
}
