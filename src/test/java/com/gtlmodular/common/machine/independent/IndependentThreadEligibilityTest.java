package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.eligibility.IndependentThreadEligibility;
import com.gtlmodular.common.machine.independent.eligibility.ThreadEligibilitySettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IndependentThreadEligibilityTest {

    private static final String MACHINE = "gtladditions:arcane_cache_vault";
    private final IndependentThreadEligibility eligibility = new IndependentThreadEligibility(Set.of(Number.class));

    @ParameterizedTest
    @CsvSource({
            "false, false, false, false, false",
            "false, true,  false, false, false",
            "false, true,  true,  false, false",
            "false, true,  true,  true,  false",
            "true,  true,  false, false, true",
            "true,  false, false, false, false",
            "true,  false, true,  false, true",
            "true,  true,  true,  false, true",
            "true,  true,  false, true,  false",
            "true,  false, true,  true,  false",
            "true,  true,  true,  true,  false"
    })
    void eligibilityAndRetentionAreIndependent(boolean enabled, boolean exactClass, boolean whitelisted,
                                              boolean blacklisted, boolean expectedEligibility) {
        var settings = new ThreadEligibilitySettings(enabled,
                whitelisted ? Set.of(MACHINE) : Set.of(), blacklisted ? Set.of(MACHINE) : Set.of());
        Class<?> actualLogicClass = exactClass ? Number.class : Integer.class;

        for (boolean hasOwnedWork : List.of(false, true)) {
            var decision = eligibility.evaluate(settings, MACHINE, actualLogicClass, hasOwnedWork);

            assertEquals(expectedEligibility, decision.eligibleForNewBatches());
            assertEquals(hasOwnedWork, decision.mustRetainOwnedWork());
        }
    }

    @Test
    void everySuppliedStandardClassMatchesButItsSubclassesDoNot() {
        var standards = Set.<Class<?>>of(Number.class, CharSequence.class, List.class, Set.class, Runnable.class);
        var policy = new IndependentThreadEligibility(standards);
        var settings = new ThreadEligibilitySettings(true, Set.of(), Set.of());

        for (Class<?> standard : standards) {
            assertTrue(policy.evaluate(settings, MACHINE, standard, false).eligibleForNewBatches());
        }
        for (Class<?> subclass : List.of(Integer.class, String.class, ArrayList.class, HashSet.class, Thread.class)) {
            assertFalse(policy.evaluate(settings, MACHINE, subclass, false).eligibleForNewBatches());
        }
    }

    @Test
    void classTokensAreCopiedAndNotComparedByAssignability() {
        var standards = new HashSet<Class<?>>();
        standards.add(Number.class);
        var policy = new IndependentThreadEligibility(standards);
        standards.add(Integer.class);
        standards.remove(Number.class);
        var settings = new ThreadEligibilitySettings(true, Set.of(), Set.of());

        assertTrue(policy.evaluate(settings, MACHINE, Number.class, false).eligibleForNewBatches());
        assertFalse(policy.evaluate(settings, MACHINE, Integer.class, false).eligibleForNewBatches());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "machine", ":machine", "mod:", "mod:one:two", "MOD:machine", "mod:Machine",
            "mod:*", "*:machine", "mod:mach?ne", " mod:machine", "mod:machine ", "mod:机", "mod/more:machine",
            "mod:machine\n"})
    void incompleteOrNonExactIdsCannotAdmitNewWork(String id) {
        assertFalse(ThreadEligibilitySettings.isValidMachineId(id));
        var settings = new ThreadEligibilitySettings(true, Set.of(), Set.of());

        var decision = eligibility.evaluate(settings, id, Number.class, true);

        assertFalse(decision.eligibleForNewBatches());
        assertTrue(decision.mustRetainOwnedWork());
        if (id != null) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ThreadEligibilitySettings(true, Set.of(id), Set.of()));
            assertThrows(IllegalArgumentException.class,
                    () -> new ThreadEligibilitySettings(true, Set.of(), Set.of(id)));
        }
    }

    @Test
    void missingLogicTokenDoesNotDiscardOwnedWork() {
        assertFalse(ThreadEligibilitySettings.isValidMachineId(17));
        var settings = new ThreadEligibilitySettings(true, Set.of(MACHINE), Set.of());

        var decision = eligibility.evaluate(settings, MACHINE, null, true);

        assertFalse(decision.eligibleForNewBatches());
        assertTrue(decision.mustRetainOwnedWork());
    }

    @Test
    void duplicateAndUnknownIdsKeepTheirExactNamespaceAndPath() {
        String unknown = "not_installed.mod-2:machines/future.v1";
        var settings = ThreadEligibilitySettings.from(true, List.of(unknown, unknown), List.of());

        assertEquals(Set.of(unknown), settings.whitelist());
        assertTrue(eligibility.evaluate(settings, unknown, Integer.class, false).eligibleForNewBatches());
        assertFalse(eligibility.evaluate(settings, "other:machines/future.v1", Integer.class, false)
                .eligibleForNewBatches());
        assertFalse(eligibility.evaluate(settings, unknown + "/extra", Integer.class, false)
                .eligibleForNewBatches());
    }

    @Test
    void listUpdatesCreateIndependentImmutableSnapshots() {
        var whitelist = new ArrayList<>(List.of(MACHINE));
        var blacklist = new ArrayList<String>();
        var before = ThreadEligibilitySettings.from(true, whitelist, blacklist);
        whitelist.clear();
        blacklist.add(MACHINE);
        var after = ThreadEligibilitySettings.from(true, whitelist, blacklist);

        assertTrue(eligibility.evaluate(before, MACHINE, Integer.class, true).eligibleForNewBatches());
        var decision = eligibility.evaluate(after, MACHINE, Number.class, true);
        assertFalse(decision.eligibleForNewBatches());
        assertTrue(decision.mustRetainOwnedWork());
        assertEquals(Set.of(MACHINE), before.whitelist());
        assertTrue(before.blacklist().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> before.whitelist().clear());
        assertThrows(UnsupportedOperationException.class, () -> after.blacklist().clear());
    }
}
