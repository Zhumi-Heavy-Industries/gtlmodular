package com.gtlmodular.common.machine.independent.persistence;

import com.mojang.serialization.DynamicOps;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 严格读字段，避免 NBT/Codec 的缺省值或窄化转换把坏记录变成可运行工作。 */
final class ArchiveFields<T> {
    private final DynamicOps<T> ops;
    private final Map<String, T> values = new LinkedHashMap<>();

    ArchiveFields(DynamicOps<T> ops, T value, String... allowed) {
        this.ops = ops;
        var map = ops.getMap(value).result().orElseThrow(() -> invalid("Expected object"));
        map.entries().forEach(entry -> values.put(ops.getStringValue(entry.getFirst()).result()
                .orElseThrow(() -> invalid("Non-string key")), entry.getSecond()));
        if (!Set.of(allowed).containsAll(values.keySet())) throw invalid("Unknown fields: " + values.keySet());
    }

    T required(String name) {
        T result = values.get(name);
        if (result == null) throw invalid("Missing " + name);
        return result;
    }

    boolean has(String name) { return values.containsKey(name); }

    String string(String name) {
        return ops.getStringValue(required(name)).result().orElseThrow(() -> invalid("Invalid " + name));
    }

    long number(String name) {
        Number number = ops.getNumberValue(required(name)).result()
                .orElseThrow(() -> invalid("Invalid " + name));
        try {
            return new BigDecimal(number.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw invalid("Out of range or fractional " + name);
        }
    }

    int integer(String name) {
        try {
            return Math.toIntExact(number(name));
        } catch (ArithmeticException exception) {
            throw invalid("Out of range " + name);
        }
    }

    boolean bool(String name) {
        T value = required(name);
        if (value.equals(ops.createBoolean(true))) return true;
        if (value.equals(ops.createBoolean(false))) return false;
        throw invalid("Invalid boolean " + name);
    }

    List<T> list(String name) {
        List<T> list = ops.getStream(required(name)).result().orElseThrow(() -> invalid("Invalid " + name)).toList();
        if (list.size() > IndependentBatchPersistence.MAX_RECORDS) throw invalid("Too many " + name);
        return list;
    }

    static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
