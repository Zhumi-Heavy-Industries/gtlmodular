package com.gtladd.gtladditions.utils;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtladd.gtladditions.api.recipe.WirelessGTRecipe;
import com.gtladd.gtladditions.common.data.ParallelData;
import kotlin.jvm.functions.Function2;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;

/**
 * ★ 0.3.518：Additions 3.6.2 fix1 的<b>类路径兼容桥</b>。
 *
 * <p>fix1 把 {@code com.gtladd.gtladditions.utils.RecipeCalculationHelper} 挪到了
 * {@code com.gtladd.gtladditions.utils.recipe.RecipeCalculationHelper}，但本机
 * {@code GTL-Enhancedcore-5.3.9-add-3.6.2-fix.jar} 仍引用旧路径（6 个类、4 个方法），
 * 运行期抛 {@code NoClassDefFoundError} 导致「Ticking block entity」崩溃。
 *
 * <p>桥接类只提供这 4 个旧签名，方法体转发到 fix1 的新类；旧版
 * {@code calculateParallelsWithGreedyAllocation} 内部缺省的并行配方 lambda
 * 用 {@link IParallelLogic#getRecipeOutputChance} 复原（与 3.3.1 版行为一致）。
 * 其余方法增强版没调用，桥不做转发。
 *
 * <p>若以后 Additions 恢复旧路径，本类会与 jar 内同名类冲突——届时删掉本文件即可。
 */
public final class RecipeCalculationHelper {

    /** Kotlin object 的 INSTANCE：增强版字节码用 getstatic 取这个字段。 */
    @NotNull
    public static final RecipeCalculationHelper INSTANCE = new RecipeCalculationHelper();

    @NotNull
    public static final String RECIPE_CYCLE_CONTAINER = "recipe_cycle_container";

    private RecipeCalculationHelper() {
    }

    private static com.gtladd.gtladditions.utils.recipe.RecipeCalculationHelper delegate() {
        return com.gtladd.gtladditions.utils.recipe.RecipeCalculationHelper.INSTANCE;
    }

    @NotNull
    public GTRecipe multipleRecipe(@NotNull GTRecipe recipe, long parallel) {
        return delegate().multipleRecipe(recipe, parallel);
    }

    @NotNull
    public GTRecipe buildNormalRecipe(@NotNull List<? extends com.gregtechceu.gtceu.api.recipe.content.Content> itemOutputs,
                                      @NotNull List<? extends com.gregtechceu.gtceu.api.recipe.content.Content> fluidOutputs,
                                      double duration, long totalEu, int minDuration) {
        return delegate().buildNormalRecipe(itemOutputs, fluidOutputs, duration, totalEu, minDuration);
    }

    @NotNull
    public WirelessGTRecipe buildWirelessRecipe(@NotNull List<? extends com.gregtechceu.gtceu.api.recipe.content.Content> itemOutputs,
                                                @NotNull List<? extends com.gregtechceu.gtceu.api.recipe.content.Content> fluidOutputs,
                                                int duration, @NotNull BigInteger totalEu, @NotNull GTRecipeType recipeType) {
        return delegate().buildWirelessRecipe(itemOutputs, fluidOutputs, duration, totalEu, recipeType);
    }

    /**
     * 旧签名 4 参数；新版多了第 5 个 {@code createParalleledRecipe} lambda，
     * 用旧版缺省逻辑补齐：{@code IParallelLogic.getRecipeOutputChance(machine, multipleRecipe(recipe, p))}。
     */
    public ParallelData calculateParallelsWithGreedyAllocation(@NotNull Collection<? extends GTRecipe> recipes,
                                                               long totalParallel,
                                                               @NotNull IRecipeLogicMachine machine,
                                                               @NotNull Function2<? super GTRecipe, ? super Long, ? extends it.unimi.dsi.fastutil.longs.LongLongPair> getParallelAndConsumption) {
        Function2<GTRecipe, Long, GTRecipe> createParalleledRecipe = (recipe, parallel) ->
                IParallelLogic.getRecipeOutputChance(machine, delegate().multipleRecipe(recipe, parallel));
        return delegate().calculateParallelsWithGreedyAllocation(
                recipes, totalParallel, machine, getParallelAndConsumption, createParalleledRecipe);
    }
}
