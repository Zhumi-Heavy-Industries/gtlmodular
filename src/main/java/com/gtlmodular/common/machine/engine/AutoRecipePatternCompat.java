package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MERecipePatternBufferPartMachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * gtlcore「ME配方类型统合样板总成」兼容取数。
 *
 * <p>这个部件不是库存式样板仓：它会按控制器当前配方类型自动展开 AE 样板，
 * 但把展开结果放在自己的 {@code recipePatterns/availablePatterns} 里，
 * 不一定马上写进普通样板槽的库存缓存。独立线程引擎如果只走
 * {@code getMEPatternRecipeHandleParts() → getCachedGTRecipe()}，就会看到 0 条候选。
 *
 * <p>这里把它的展开结果读成 {@link GTRecipe}，作为候选来源补齐；
 * 只读、失败即空表，绝不把异常抛进 tick。
 */
public final class AutoRecipePatternCompat {

    private AutoRecipePatternCompat() {
    }

    private record Cached(long revision, List<GTRecipe> recipes) {}

    /** 按方块 + 配置版本缓存；自动样板 403 条时不能每 tick 重扫。 */
    private static final Map<MERecipePatternBufferPartMachine, Cached> CACHE = new WeakHashMap<>();

    /** 控制器里所有自动配方样板总成当前已展开的配方（可能为空）。 */
    public static List<GTRecipe> expandedRecipes(IMultiController controller) {
        if (controller == null || !controller.isFormed()) {
            return List.of();
        }
        List<GTRecipe> out = new ArrayList<>();
        try {
            for (IMultiPart part : controller.getParts()) {
                if (!(part instanceof MERecipePatternBufferPartMachine auto)) {
                    continue;
                }
                try {
                    long revision = auto.getConfigurationRevision();
                    Cached cached = CACHE.get(auto);
                    if (cached != null && cached.revision() == revision) {
                        out.addAll(cached.recipes());
                        continue;
                    }
                    // 空查询 = 不按名称过滤；空集合 = 不按状态过滤，取全部已展开配方。
                    List<GTRecipe> recipes = new ArrayList<>();
                    var patterns = auto.searchRecipes("", Set.of());
                    if (patterns != null) {
                        for (var pattern : patterns) {
                            if (pattern != null && pattern.recipe() != null) {
                                recipes.add(pattern.recipe());
                            }
                        }
                    }
                    CACHE.put(auto, new Cached(revision, List.copyOf(recipes)));
                    out.addAll(recipes);
                } catch (Throwable ignored) {
                    // 单个部件读取失败不影响其它部件/其它候选
                }
            }
        } catch (Throwable ignored) {
            return out;
        }
        return out;
    }
}
