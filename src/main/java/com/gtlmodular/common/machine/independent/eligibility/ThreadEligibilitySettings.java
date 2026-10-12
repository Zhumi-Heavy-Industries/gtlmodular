package com.gtlmodular.common.machine.independent.eligibility;

import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;

public record ThreadEligibilitySettings(boolean enabled, Set<String> whitelist, Set<String> blacklist) {

    private static final Pattern MACHINE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public ThreadEligibilitySettings {
        whitelist = copyIds(whitelist);
        blacklist = copyIds(blacklist);
    }

    public static ThreadEligibilitySettings from(boolean enabled, Collection<? extends String> whitelist,
                                                 Collection<? extends String> blacklist) {
        return new ThreadEligibilitySettings(enabled, Set.copyOf(whitelist), Set.copyOf(blacklist));
    }

    public static boolean isValidMachineId(Object candidate) {
        return candidate instanceof String id && MACHINE_ID.matcher(id).matches();
    }

    private static Set<String> copyIds(Set<String> ids) {
        Set<String> copy = Set.copyOf(ids);
        for (String id : copy) {
            if (!isValidMachineId(id)) {
                throw new IllegalArgumentException("Expected a complete, exact machine ID: " + id);
            }
        }
        return copy;
    }
}
