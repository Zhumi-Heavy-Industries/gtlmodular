package com.gtlmodular.common.machine.component;

import com.gregtechceu.gtceu.api.GTValues;
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
 * GTO「部件组装机」——基础机与大型机已合并为一台，靠结构升级切换形态：
 * <ul>
 *   <li>1 级：BASE 本体 7×5×5，4 线程，{@code CATier ≤ IV}；</li>
 *   <li>2 级：本体 + ASSEMBLY 扩展 29×6×13，8 线程，{@code CATier ≤ UV}，允许激光仓。</li>
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

    public ComponentAssemblerMachine(IMachineBlockEntity holder,
                                     Supplier<BlockPattern> pattern, Supplier<String[][]> slices,
                                     java.util.function.Predicate<Character> tierCells,
                                     Object... args) {
        super(holder, args);
        this.structureTierTrait = new StructureTierTrait(this,
                StructureUpgradeSupport.layout(pattern, slices, tierCells),
                null, Set.of("CoilType"), StructureUpgradeSupport.noOpSync());
    }

    /** 1 级 = 原基础机，2 级 = 原大型机。 */
    private boolean upgraded() {
        return structureTierTrait.getStructureTier() >= 2;
    }

    private int maxRecipeTier() {
        return upgraded() ? GTValues.UV : GTValues.IV;
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
        // 一次升级同时拿到原大型机的装配扩展（档位/舱位）与动力扩展（并行/超频）。
                return StructureUpgradeSupport.requirement(
                // 解锁成本只用本机结构自身要用、本包稳定可产出的基础外壳；
                // 不收超级并行仓/超频仓等舱室（玩家反馈这些根本拿不到）。
                StructureUpgradeSupport.cost("gtceu:steel_machine_casing", 32),
                StructureUpgradeSupport.cost("gtceu:high_power_casing", 32),
                StructureUpgradeSupport.cost("gtceu:clean_machine_casing", 32));
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
        GTLModular.LOGGER.info("[gtlmodular] 部件组装机已成形 @{}（结构等级 {}，线程 {}，配方等级上限 {}）",
                getPos(), structureTierTrait.getStructureTier(), effectiveThreads(), maxRecipeTier());
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
        // 没装线程仓时：1 级 = 4，2 级 = 8。装了线程仓时引擎统一读仓
        // （IThreadedRecipeMachine#effectiveThreads()），机器类不再做额外适配。
        return upgraded() ? 8 : 4;
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
                && data.getInt(RECIPE_TIER_KEY) > maxRecipeTier()) {
            return null;
        }
        return super.fullModifyRecipe(recipe, params, result);
    }
}
