package com.gtlmodular.common.machine.independent;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.config.NanochipConfig;
import com.gtlmodular.common.config.OtherMachineThreadsConfig;
import com.gtlmodular.common.machine.independent.eligibility.IndependentThreadEligibility;
import com.gtlmodular.common.machine.independent.eligibility.ThreadEligibilitySettings;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OtherMachineThreadsConfigTest {

    private static final String ENABLED = "independentThreadsForOtherMachines";
    private static final String WHITELIST = "independentThreadsMachineWhitelist";
    private static final String BLACKLIST = "independentThreadsMachineBlacklist";
    private static final String MAX_TICKS = "independentThreadsMaxBatchTicks";
    private static final String MAX_RUNS = "independentThreadsMaxBatchRuns";
    private static final String ALLOW_ME_IO = "independentThreadsAllowMeIo";
    private static final String MAX_PER_RECIPE = "independentThreadsMaxBatchesPerRecipe";
    private static final String CACHE = "independentThreadsCandidateCacheTicks";
    private static final String SCAN = "independentThreadsCandidateScanBudget";
    private static final String STATS = "independentThreadsStats";
    private static final String MACHINE = "gtladditions:arcane_cache_vault";

    @Test
    void megastructureBatchLimitsHaveExplicitDefaultsAndCanBeOverridden() {
        var spec = GTLModularConfig.SPEC;
        ForgeConfigSpec.ConfigValue<?> ticks = spec.getValues().get(List.of(MAX_TICKS));
        ForgeConfigSpec.ConfigValue<?> runs = spec.getValues().get(List.of(MAX_RUNS));
        ForgeConfigSpec.ConfigValue<?> me = spec.getValues().get(List.of(ALLOW_ME_IO));
        ForgeConfigSpec.ConfigValue<?> perRecipe = spec.getValues().get(List.of(MAX_PER_RECIPE));
        ForgeConfigSpec.ConfigValue<?> cache = spec.getValues().get(List.of(CACHE));
        ForgeConfigSpec.ConfigValue<?> scan = spec.getValues().get(List.of(SCAN));
        ForgeConfigSpec.ConfigValue<?> stats = spec.getValues().get(List.of(STATS));
        assertNotNull(ticks);
        assertNotNull(runs);
        assertNotNull(me);
        assertNotNull(perRecipe);
        assertNotNull(cache);
        assertNotNull(scan);
        assertNotNull(stats);
        assertEquals(10_000, ticks.getDefault());
        assertEquals(0L, runs.getDefault());
        assertEquals(false, me.getDefault());
        assertEquals(0, perRecipe.getDefault());
        assertEquals(5, cache.getDefault());
        assertEquals(0, scan.getDefault());
        assertEquals(false, stats.getDefault());

        var data = CommentedConfig.inMemory();
        spec.correct(data);
        data.set(MAX_TICKS, 20_000);
        data.set(MAX_RUNS, 1_000_000_000L);
        data.set(ALLOW_ME_IO, true);
        data.set(MAX_PER_RECIPE, 1);
        data.set(CACHE, 7);
        data.set(SCAN, 32);
        data.set(STATS, true);
        try {
            spec.setConfig(data);
            assertEquals(20_000, GTLModularConfig.OTHER_MACHINE_THREADS.maxBatchTicks());
            assertEquals(1_000_000_000L, GTLModularConfig.OTHER_MACHINE_THREADS.maxBatchRuns());
            assertTrue(GTLModularConfig.OTHER_MACHINE_THREADS.allowMeIo());
            assertEquals(1, GTLModularConfig.OTHER_MACHINE_THREADS.maxBatchesPerRecipe());
            assertEquals(7, GTLModularConfig.OTHER_MACHINE_THREADS.candidateCacheTicks());
            assertEquals(32, GTLModularConfig.OTHER_MACHINE_THREADS.candidateScanBudget());
            assertTrue(GTLModularConfig.OTHER_MACHINE_THREADS.statsEnabled());
        } finally {
            spec.setConfig(null);
        }
    }

    @Test
    void fullProductDefinesTopLevelDefaultsAndBindsTheComponentToItsSpec() {
        var spec = GTLModularConfig.SPEC;
        ForgeConfigSpec.ConfigValue<?> enabled = spec.getValues().get(List.of(ENABLED));
        ForgeConfigSpec.ConfigValue<?> whitelist = spec.getValues().get(List.of(WHITELIST));
        ForgeConfigSpec.ConfigValue<?> blacklist = spec.getValues().get(List.of(BLACKLIST));
        assertNotNull(enabled);
        assertNotNull(whitelist);
        assertNotNull(blacklist);
        assertEquals(false, enabled.getDefault());
        assertEquals(List.of(), whitelist.getDefault());
        assertEquals(List.of(), blacklist.getDefault());
        assertEquals(new ThreadEligibilitySettings(false, Set.of(), Set.of()),
                GTLModularConfig.OTHER_MACHINE_THREADS.snapshot());

        var data = CommentedConfig.inMemory();
        spec.correct(data);
        data.set(ENABLED, true);
        data.set(WHITELIST, List.of(MACHINE));
        data.set(BLACKLIST, List.of("unknown:blocked"));
        try {
            spec.setConfig(data);

            assertEquals(new ThreadEligibilitySettings(true, Set.of(MACHINE), Set.of("unknown:blocked")),
                    GTLModularConfig.OTHER_MACHINE_THREADS.snapshot());
        } finally {
            spec.setConfig(null);
        }
    }

    @Test
    void separateBuildersHaveSeparateValuesAndSpecs() {
        var firstBuilder = new ForgeConfigSpec.Builder();
        var first = new OtherMachineThreadsConfig(firstBuilder);
        var firstSpec = firstBuilder.build();
        var secondBuilder = new ForgeConfigSpec.Builder();
        var second = new OtherMachineThreadsConfig(secondBuilder);
        var secondSpec = secondBuilder.build();
        var firstData = CommentedConfig.inMemory();
        firstSpec.correct(firstData);
        firstData.set(ENABLED, true);
        firstData.set(WHITELIST, List.of(MACHINE));
        var secondData = CommentedConfig.inMemory();
        secondSpec.correct(secondData);

        firstSpec.setConfig(firstData);
        secondSpec.setConfig(secondData);

        assertTrue(first.snapshot().enabled());
        assertEquals(Set.of(MACHINE), first.snapshot().whitelist());
        assertFalse(second.snapshot().enabled());
        assertTrue(second.snapshot().whitelist().isEmpty());
        for (String key : List.of(ENABLED, WHITELIST, BLACKLIST)) {
            assertNotSame(firstSpec.getValues().get(key), secondSpec.getValues().get(key));
        }
    }

    @Test
    void reloadChangesEligibilityWithoutMutatingAnEarlierSnapshotOrLosingRetention() {
        var builder = new ForgeConfigSpec.Builder();
        var component = new OtherMachineThreadsConfig(builder);
        var spec = builder.build();
        var data = CommentedConfig.inMemory();
        spec.correct(data);
        data.set(ENABLED, true);
        var whitelist = new ArrayList<>(List.of(MACHINE, MACHINE));
        data.set(WHITELIST, whitelist);
        spec.setConfig(data);
        var policy = new IndependentThreadEligibility(Set.of(Number.class));
        var before = component.snapshot();
        assertTrue(policy.evaluate(before, MACHINE, Integer.class, true).eligibleForNewBatches());

        whitelist.clear();
        data.set(BLACKLIST, List.of(MACHINE));
        spec.afterReload();
        var blocked = component.snapshot();

        assertEquals(Set.of(MACHINE), before.whitelist());
        assertTrue(before.blacklist().isEmpty());
        assertTrue(blocked.whitelist().isEmpty());
        assertFalse(policy.evaluate(blocked, MACHINE, Number.class, true).eligibleForNewBatches());
        assertTrue(policy.evaluate(blocked, MACHINE, Number.class, true).mustRetainOwnedWork());

        data.set(BLACKLIST, List.of());
        spec.afterReload();
        assertTrue(policy.evaluate(component.snapshot(), MACHINE, Number.class, false).eligibleForNewBatches());
        assertFalse(policy.evaluate(component.snapshot(), MACHINE, Integer.class, true).eligibleForNewBatches());

        data.set(ENABLED, false);
        data.set(WHITELIST, List.of(MACHINE));
        spec.afterReload();
        var disabled = policy.evaluate(component.snapshot(), MACHINE, Integer.class, true);
        assertFalse(disabled.eligibleForNewBatches());
        assertTrue(disabled.mustRetainOwnedWork());
    }

    @Test
    void forgeValidatesBothListsWithoutResolvingRegistrationsOrExpandingIds() {
        var builder = new ForgeConfigSpec.Builder();
        var component = new OtherMachineThreadsConfig(builder);
        var spec = builder.build();
        var data = CommentedConfig.inMemory();
        spec.correct(data);
        assertTrue(spec.isCorrect(data));
        String unknown = "uninstalled:machines/future-2.0";
        for (String key : List.of(WHITELIST, BLACKLIST)) {
            ForgeConfigSpec.ValueSpec listSpec = spec.getSpec().get(key);
            assertTrue(listSpec.test(List.of()));
            assertTrue(listSpec.test(List.of(unknown, unknown)));
            for (Object invalid : List.of("bare_id", ":missing", "mod:", "mod:*", "Mod:machine", "mod:one:two", 12)) {
                assertFalse(listSpec.test(List.of(invalid)));
            }
            data.set(key, List.of(unknown, unknown, "bare_id", "mod:*", 12));
        }
        spec.correct(data);
        spec.setConfig(data);

        assertEquals(Set.of(unknown), component.snapshot().whitelist());
        assertEquals(Set.of(unknown), component.snapshot().blacklist());
    }

    @Test
    void sharedNanochipConfigDoesNotDefineOtherMachineKeys() {
        var builder = new ForgeConfigSpec.Builder();
        new NanochipConfig(builder, "gtl_nac");
        var spec = builder.build();

        for (String key : List.of(ENABLED, WHITELIST, BLACKLIST, MAX_TICKS, MAX_RUNS, ALLOW_ME_IO, MAX_PER_RECIPE,
                CACHE, SCAN, STATS)) {
            assertFalse(spec.getValues().contains(key));
        }
    }
}
