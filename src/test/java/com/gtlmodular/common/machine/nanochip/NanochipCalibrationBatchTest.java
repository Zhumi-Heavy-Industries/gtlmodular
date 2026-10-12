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
