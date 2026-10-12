package com.gtlmodular.common.machine.independent.runtime;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gtlmodular.common.machine.independent.persistence.IndependentRecipePayload;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.chance.LongChanceLogic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BatchChanceResolver {
    private BatchChanceResolver() {}

    static Map<RecipeCapability<?>, List<Content>> resolve(GTRecipe source, IndependentRecipePayload payload,
                                                          IRecipeCapabilityHolder holder, IO io, boolean tick) {
        var entries = tick ? (io == IO.IN ? source.tickInputs : source.tickOutputs) : (io == IO.IN ? source.inputs : source.outputs);
        Map<RecipeCapability<?>, List<Content>> result = new LinkedHashMap<>();
        entries.forEach((cap, values) -> {
            var resolved = new ArrayList<Content>();
            var chanced = new ArrayList<Content>();
            for (Content content : values) {
                if (content.chance >= content.maxChance) resolved.add(content);
                else if (tick) chanced.add(content.copy(cap, ContentModifier.multiplier(1.0 / source.parallels)));
                else if (io == IO.OUT || content.chance != 0) chanced.add(content);
            }
            if (!chanced.isEmpty()) {
                var cache = payload.chanceCaches().computeIfAbsent(cap, key -> key.makeChanceCache());
                // 合批族非 tick 使用 LongChanceLogic；tick 则沿用 GTCEu 的能力概率规则及 int parallels。
                List<Content> rolled = tick
                        ? source.getChanceLogicForCapability(cap, io, true).roll(chanced, source.getType().getChanceFunction(),
                            RecipeHelper.getPreOCRecipeEuTier(source), holder.getChanceTier(), cache, source.parallels, cap)
                        : LongChanceLogic.OR.roll(chanced, source.getType().getChanceFunction(), IGTRecipe.of(source).getEuTier(),
                            holder.getChanceTier(), cache, IGTRecipe.of(source).getRealParallels(), cap);
                if (rolled != null) resolved.addAll(rolled);
            }
            var fixed = new ArrayList<Content>();
            for (Content content : resolved) fixed.add(new Content(cap.copyContent(content.content), 10000, 10000, 0, null, null));
            if (!fixed.isEmpty()) result.put(cap, fixed);
        });
        return result;
    }
}
