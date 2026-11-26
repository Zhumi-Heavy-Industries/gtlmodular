package com.gtlmodular.common.machine.component;

import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gtladd.gtladditions.api.machine.feature.IStructureTierMachine;
import com.gtladd.gtladditions.api.machine.feature.TierRequirement;
import com.gtladd.gtladditions.common.machine.trait.StructureTierTrait;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.machine.engine.IThreadedRecipeMachine;
import com.gtlmodular.common.machine.engine.ThreadedRecipeLogic;
import com.gtlmodular.common.machine.upgrade.StructureUpgradeSupport;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

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
        implements IThreadedRecipeMachine, org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine,
        com.gregtechceu.gtceu.api.machine.feature.IMachineLife, IStructureTierMachine, com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine {

    @Persisted
    @DescSynced
    private final StructureTierTrait structureTierTrait;
    private static ManagedFieldHolder FIELD_HOLDER;

    @Override
    public int getMaxParallel() {
        return 1;
    }


    /** 部件装配配方写在 {@code data} 里的外壳等级键（GTO / gtlcore 同款键名）。 */
    public static final String RECIPE_TIER_KEY = "CATier";

    private final int threads;
    private final int maxRecipeTier;

    public ComponentAssemblerMachine(IMachineBlockEntity holder, int threads, int maxRecipeTier,
                                     Supplier<BlockPattern> pattern, Supplier<String[][]> slices,
                                     String tierChars,
                                     Object... args) {
        super(holder, args);
        this.threads = threads;
        this.maxRecipeTier = maxRecipeTier;
        this.structureTierTrait = new StructureTierTrait(this,
                StructureUpgradeSupport.layout(pattern, slices,
                        ch -> tierChars.indexOf(ch) >= 0),
                null, Set.of("CoilType"), StructureUpgradeSupport.noOpSync());
    }

    @Override
    public StructureTierTrait getStructureTierTrait() {
        return structureTierTrait;
    }

    @Override
    public TierRequirement tierRequirement(int tier) {
        if (tier != 2) {
            return null;
        }
        if (threads >= 8) {
            // 大型机已带装配扩展段 ⇒ 对应 GTO 的 POWER 扩展（激光仓 ≤2 + 并行仓）。
            return StructureUpgradeSupport.requirement(
                    StructureUpgradeSupport.cost("gtladditions:super_parallel_hatch", 4),
                    StructureUpgradeSupport.cost("gtlmodular:overclock_hatch_uv", 1));
        }
        // 基础机对应 GTO 的 ASSEMBLY 扩展：外壳档位上限 IV→UV，开放 IO 与加速仓。
        return StructureUpgradeSupport.requirement(
                StructureUpgradeSupport.cost("gtlcore:multi_functional_casing", 64),
                StructureUpgradeSupport.cost("gtlcore:process_machine_casing", 64),
                StructureUpgradeSupport.cost("gtlmodular:overclock_hatch_uv", 1));
    }

    @Override
    public boolean checkPattern() {
        return structureTierTrait.checkPattern();
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        if (FIELD_HOLDER == null) {
            FIELD_HOLDER = new ManagedFieldHolder(ComponentAssemblerMachine.class, super.getFieldHolder());
        }
        return FIELD_HOLDER;
    }

    @Override
    public void attachSideTabs(TabsWidget tabs) {
        super.attachSideTabs(tabs);
        StructureUpgradeSupport.attachTab(tabs, this);
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        textList.add(StructureUpgradeSupport.display(structureTierTrait));
        textList.add(Component.translatable("gtlmodular.structure_upgrade.component_assembler"));
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new ThreadedRecipeLogic(this);
    }

    /** ★ 2026-11-10：记录放置者 UUID —— 能源缓冲「从无线电网抽电」的身份。 */
    @Override
    public void onMachinePlaced(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity player,
                                net.minecraft.world.item.ItemStack stack) {
        if (player instanceof net.minecraft.world.entity.player.Player p
                && getRecipeLogic() instanceof ThreadedRecipeLogic logic) {
            logic.setGridUser(p.getUUID());
        }
    }

    /** 成形/失形各打一行：先分清是结构没成还是配方没配，再看引擎的 5 秒诊断。 */
    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        structureTierTrait.onStructureFormed();
        GTLModular.LOGGER.info("[gtlmodular] 部件组装机已成形 @{}（线程 {}，配方等级上限 {}，结构等级 {}）",
                getPos(), effectiveThreads(), maxRecipeTier, structureTierTrait.getStructureTier());
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        structureTierTrait.onStructureInvalid();
        GTLModular.LOGGER.info("[gtlmodular] 部件组装机失形 @{}", getPos());
    }

    @Override
    public boolean saveBreak() {
        return structureTierTrait.getHasDropData()
                || com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine.super.saveBreak();
    }

    @Override
    public int baseThreads() {
        // 没装线程仓时的固定值（v1 = 4 / v2 = 8）。装了线程仓时引擎统一读仓
        // （IThreadedRecipeMachine#effectiveThreads()），机器类不再做额外适配。
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
