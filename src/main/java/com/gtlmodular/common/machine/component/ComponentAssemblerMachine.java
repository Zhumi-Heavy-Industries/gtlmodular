package com.gtlmodular.common.machine.component;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.machine.engine.IThreadedRecipeMachine;
import com.gtlmodular.common.machine.engine.ThreadedRecipeLogic;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * GTO「部件组装机」的通用机器类：两台多方块共用，只差线程数与配方等级上限。
 *
 * <ul>
 *   <li>v1（{@code component_assembler}）：4 线程，{@code CATier ≤ IV}；</li>
 *   <li>v2（{@code large_component_assembler}）：8 线程，{@code CATier ≤ UV}，允许激光仓。</li>
 * </ul>
 *
 * <p>线程仍走本模组的独立线程引擎 {@link ThreadedRecipeLogic}；并行倍率固定 1，
 * 真正的并行数取结构里的标准并行仓。
 *
 * <p>等级闸放在 {@link #fullModifyRecipe}：返回 {@code null} ⇒ 引擎在收批前跳过这条候选。
 * 放在 {@code beforeWorking} 会让超档配方每 tick 被选中又被拒，饿死同轮能做的配方。
 */
public class ComponentAssemblerMachine extends WorkableElectricMultiblockMachine
        implements IThreadedRecipeMachine {

    /** 部件装配配方写在 {@code data} 里的外壳等级键（GTO / gtlcore 同款键名）。 */
    public static final String RECIPE_TIER_KEY = "CATier";

    private final int threads;
    private final int maxRecipeTier;

    public ComponentAssemblerMachine(IMachineBlockEntity holder, int threads, int maxRecipeTier, Object... args) {
        super(holder, args);
        this.threads = threads;
        this.maxRecipeTier = maxRecipeTier;
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new ThreadedRecipeLogic(this);
    }

    /** 成形/失形各打一行：先分清是结构没成还是配方没配，再看引擎的 5 秒诊断。 */
    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        GTLModular.LOGGER.info("[gtlmodular] 部件组装机已成形 @{}（线程 {}，配方等级上限 {}）",
                getPos(), threads, maxRecipeTier);
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        GTLModular.LOGGER.info("[gtlmodular] 部件组装机失形 @{}", getPos());
    }

    @Override
    public int baseThreads() {
        return threads;
    }

    @Override
    public int parallelMultiplier() {
        return 1;
    }

    @Nullable
    @Override
    public GTRecipe fullModifyRecipe(GTRecipe recipe, OCParams params, OCResult result) {
        CompoundTag data = recipe.data;
        if (data != null && data.contains(RECIPE_TIER_KEY)
                && data.getInt(RECIPE_TIER_KEY) > maxRecipeTier) {
            return null;
        }
        return super.fullModifyRecipe(recipe, params, result);
    }
}
