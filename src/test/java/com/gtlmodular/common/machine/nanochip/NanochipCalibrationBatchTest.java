package com.gtlmodular.common.machine.nanochip;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.gtlmodular.common.machine.nanochip.NanochipCalibration.Family.*;
import static org.junit.jupiter.api.Assertions.*;

class NanochipCalibrationBatchTest {

    @Test
    void paralleledMixedBatchCountsProducedCircuitsAcrossReloadForBothProducts() {
        for (String namespace : List.of("gtlmodular", "gtl_nac")) {
            var batch = new NanochipCalibrationBatch();
            var crystal = new ResourceLocation(namespace, "nanochip/assembly_matrix/crystal");
            var optical = new ResourceLocation(namespace, "nanochip/assembly_matrix/optical");
            batch.capture(crystal, CRYSTAL, 2L * 2);
            batch.capture(optical, OPTICAL, 3L * 3);

            CompoundTag original = new CompoundTag();
            original.putString("owner", "unchanged");
            CompoundTag saved = batch.attach(original);
            assertFalse(original.contains("gtlmodular:calibration_sources"));
            assertEquals("unchanged", saved.getString("owner"));
            var expected = List.of(
                    new NanochipCalibrationBatch.Production(crystal, CRYSTAL, 4),
                    new NanochipCalibrationBatch.Production(optical, OPTICAL, 9));
            assertEquals(expected, sources(saved));
            assertEquals(expected, sources(saved.copy()));

            var calibration = new NanochipCalibration();
            sources(saved.copy()).forEach(p -> calibration.record(p.family(), p.amount()));
            assertEquals(4, calibration.countOf(CRYSTAL));
            assertEquals(9, calibration.countOf(OPTICAL));
            assertEquals(13, calibration.total());
            assertFalse(batch.attach(new CompoundTag()).contains("gtlmodular:calibration_sources"));
        }
    }

    @Test
    void repeatedExecutionPreservesOrderAndCountsEverySuccessfulRun() {
        var batch = new NanochipCalibrationBatch();
        var crystal = new ResourceLocation("gtlmodular", "nanochip/assembly_matrix/crystal");
        var optical = new ResourceLocation("gtlmodular", "nanochip/assembly_matrix/optical");
        batch.capture(crystal, CRYSTAL, 2L * 3);
        batch.capture(optical, OPTICAL, 3L * 2);
        batch.capture(crystal, CRYSTAL, 2L * 3);
        var restored = sources(batch.attach(new CompoundTag()).copy());
        assertEquals(List.of(
                new NanochipCalibrationBatch.Production(crystal, CRYSTAL, 6),
                new NanochipCalibrationBatch.Production(optical, OPTICAL, 6),
                new NanochipCalibrationBatch.Production(crystal, CRYSTAL, 6)), restored);
        var calibration = new NanochipCalibration();
        restored.forEach(p -> calibration.record(p.family(), p.amount()));
        assertEquals(12, calibration.countOf(CRYSTAL));
        assertEquals(6, calibration.countOf(OPTICAL));
        assertEquals(18, calibration.total());

        batch.capture(crystal, CRYSTAL, 2);
        var next = sources(batch.attach(new CompoundTag()));
        assertEquals(List.of(new NanochipCalibrationBatch.Production(crystal, CRYSTAL, 2)), next);
    }

    @Test
    void clearDiscardsUnattachedSourcesAndInvalidCountsCannotCreateCircuits() {
        var batch = new NanochipCalibrationBatch();
        var recipe = new ResourceLocation("gtl_nac", "nanochip/assembly_matrix/crystal");
        batch.capture(recipe, CRYSTAL, 8);
        batch.clear();
        batch.capture(recipe, CRYSTAL, 0);
        batch.capture(recipe, CRYSTAL, -1);
        batch.capture(null, CRYSTAL, 8);
        batch.capture(recipe, null, 8);
        assertFalse(batch.attach(new CompoundTag()).contains("gtlmodular:calibration_sources"));

        batch.capture(recipe, CRYSTAL, Long.MAX_VALUE);
        assertEquals(List.of(new NanochipCalibrationBatch.Production(recipe, CRYSTAL, Integer.MAX_VALUE)),
                sources(batch.attach(new CompoundTag())));
    }

    private static List<NanochipCalibrationBatch.Production> sources(CompoundTag data) {
        return NanochipCalibrationBatch.decode(data.getList("gtlmodular:calibration_sources", 10));
    }

    @Test
    void mixedBatchPreservesEachRecipeFamilyAndScaledCountAcrossNbtRoundTrip() {
        var productions = List.of(
                new NanochipCalibrationBatch.Production(new ResourceLocation("gtlmodular",
                        "nanochip/assembly_matrix/crystal"), CRYSTAL, 8000),
                new NanochipCalibrationBatch.Production(new ResourceLocation("gtlmodular",
                        "nanochip/assembly_matrix/optical"), OPTICAL, 4000));
        assertEquals(productions, NanochipCalibrationBatch.decode(NanochipCalibrationBatch.encode(productions).copy()));
    }

    @Test
    void invalidOriginsCannotCreateCalibration() {
        ListTag list = new ListTag();
        for (String family : List.of("CRYSTAL", "UNKNOWN")) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", "gtl_nac:nanochip/assembly_matrix/crystal");
            entry.putString("family", family);
            entry.putInt("amount", family.equals("CRYSTAL") ? 0 : 8);
            list.add(entry);
        }
        assertTrue(NanochipCalibrationBatch.decode(list).isEmpty());
    }

    @Test
    void restoredBatchStillChecksEachOriginalRecipeAgainstCurrentWhitelist() {
        var allowed = new NanochipCalibrationBatch.Production(new ResourceLocation("gtl_nac",
                "nanochip/assembly_matrix/crystal"), CRYSTAL, 80);
        var excluded = new NanochipCalibrationBatch.Production(new ResourceLocation("pack",
                "unlisted_optical"), OPTICAL, 40);
        var restored = NanochipCalibrationBatch.decode(NanochipCalibrationBatch.encode(List.of(allowed, excluded)));
        var whitelist = List.of("gtl_nac:nanochip/assembly_matrix/*");
        assertEquals(List.of(allowed), restored.stream()
                .filter(production -> NanochipCalibration.whitelistMatches(production.recipeId(), whitelist)).toList());
    }
}
