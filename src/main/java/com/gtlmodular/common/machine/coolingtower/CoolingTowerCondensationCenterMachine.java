package com.gtlmodular.common.machine.coolingtower;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IMachineModifyDrops;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gtladd.gtladditions.common.items.GTLAddItems;
import com.gtlmodular.common.machine.engine.IThreadedRecipeMachine;
import com.gtlmodular.common.machine.engine.ThreadedRecipeLogic;
import com.gtlmodular.common.machine.fuel.OverclockProfiles;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 「超级大冰箱」（{@code gtlmodular:cooling_tower_condensation_center}）的机器类。
 *
 * <h2>2026-11-10 晚：改名 + 去干冰 + 热容改 0.09</h2>
 * <ul>
 *   <li>中文名改成 <b>超级大冰箱</b>（英文 Super Big Refrigerator）；</li>
 *   <li><b>不再消耗凛冰粉</b>：删掉整条 {@code beforeWorking} 扣料逻辑与界面「上次干冰需求」行；</li>
 *   <li><b>相对论级热容</b>：保留「手持一组（64 个）右键嵌入 / 拆机返还」，
 *       效果改为<b>耗电倍率 ×0.09</b>（原来 0.35；本次按队长新文案改）。
 *       实现方式：在注册的 {@code recipeModifier} 里把配方的 <b>tick 输入 EU</b> 乘 0.09
 *       （只动 EU，不动物品/流体；引擎与原版两条路都走这个修改器）。</li>
 * </ul>
 *
 * <h2>线程 / 并行</h2>
 * <ul>
 *   <li>装线程仓 ⇒ 本模组独立线程引擎 {@link ThreadedRecipeLogic}，线程数 = 线程仓当前设定值；</li>
 *   <li>不装线程仓 ⇒ 原版 GTCEu {@link RecipeLogic}（不是 gtladditions 的多配方逻辑）；</li>
 *   <li>并行仓：单批次并行 = 并行仓当前并行；并行仓 = 0 时按无并行跑
 *       （0.3.374 修复：以前会返回 null 导致「没配方」）。</li>
 * </ul>
 */
public class CoolingTowerCondensationCenterMachine
        extends WorkableElectricMultiblockMachine
        implements IThreadedRecipeMachine, IMachineModifyDrops {

    /** 没装线程仓时的固定线程数（本模组自研机器口径：1 条）。 */
    public static final int BASE_THREADS = 1;

    /** 一次嵌入消耗 / 拆机返还的热容数量。 */
    public static final int HEAT_CAPACITOR_STACK_SIZE = 64;

    /** 嵌入相对论级热容后的耗电倍率（2026-11-10 队长文案：0.09）。 */
    public static final double HEAT_CAPACITOR_EU_MULTIPLIER = 0.09;

    /** 已嵌入相对论级热容（持久化 + 同步）。 */
    @Persisted
    @DescSynced
    private boolean relativisticHeatCapacitorInstalled;

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            CoolingTowerCondensationCenterMachine.class,
            WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    public CoolingTowerCondensationCenterMachine(@NotNull IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    @NotNull
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ------------------------------------------------------------------
    // 独立线程引擎 / 原版回退
    // ------------------------------------------------------------------

    @Override
    @NotNull
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new ThreadedRecipeLogic(this);
    }

    /** 装线程仓才由独立线程引擎接管；没装 ⇒ 交回原版 RecipeLogic（见 {@link #createEngineFallbackLogic()}）。 */
    @Override
    public boolean engineRequiresThreadHatch() {
        return true;
    }

    /** 没装线程仓时的「原版工作逻辑」：GTCEu 原生 {@link RecipeLogic}。 */
    @Override
    public RecipeLogic createEngineFallbackLogic() {
        return new VanillaFallbackLogic(this);
    }

    @Override
    public int baseThreads() {
        return BASE_THREADS;
    }

    @Override
    public int parallelMultiplier() {
        return 1;
    }

    @Override
    public boolean usesParallelHatch() {
        return true;
    }

    // ------------------------------------------------------------------
    // 相对论级热容：EU ×0.09
    // ------------------------------------------------------------------

    /**
     * 把配方 <b>tick 输入里的 EU</b> 乘 {@link #HEAT_CAPACITOR_EU_MULTIPLIER}（向下取整、最低 1），
     * 物品/流体输入输出一个都不动。
     *
     * <p>引擎（{@code BatchFinder}）与原版（{@code RecipeLogic}）都会先走注册里的
     * {@code recipeModifier}，所以两条路共用这一份实现。
     */
    public static GTRecipe applyHeatCapacitorEu(@NotNull GTRecipe recipe) {
        List<Content> euContents = recipe.tickInputs.get(EURecipeCapability.CAP);
        if (euContents == null || euContents.isEmpty()) {
            return recipe;
        }
        GTRecipe copy = recipe.copy();
        List<Content> scaled = new ArrayList<>(euContents.size());
        for (Content content : euContents) {
            long amount = content.content instanceof Number number ? number.longValue() : 0L;
            long reduced = Math.max(1L, (long) (amount * HEAT_CAPACITOR_EU_MULTIPLIER));
            scaled.add(new Content(reduced, content.chance, content.maxChance,
                    content.tierChanceBoost, content.slotName, content.uiName));
        }
        copy.tickInputs.put(EURecipeCapability.CAP, scaled);
        return copy;
    }

    // ------------------------------------------------------------------
    // 界面
    // ------------------------------------------------------------------

    @Override
    public void addDisplayText(@NotNull List<Component> textList) {
        super.addDisplayText(textList);
        if (!isFormed()) {
            return;
        }
        // 「同时处理至多 N 个配方」= 独立线程引擎的线程数（线程仓现读；没仓 = 1）
        textList.add(Component.translatable("gtlmodular.multiblock.parallel", effectiveThreads())
                .withStyle(ChatFormatting.GRAY));
        textList.add(Component.translatable("gtlmodular.cooling_tower_condensation_center.heat_capacitor",
                Component.literal(hasRelativisticHeatCapacitor() ? "\u2713" : "x")
                        .withStyle(hasRelativisticHeatCapacitor() ? ChatFormatting.GREEN : ChatFormatting.RED)));
        // 当前超频口径（装了超频仓就显示它的 S/E，否则显示默认）
        OverclockProfiles.addOverclockDisplayLine(this, textList);
        // 找配方失败时把真实原因（缺哪些物品/流体）写到界面
        if (getRecipeLogic() instanceof ThreadedRecipeLogic logic) {
            String detail = logic.diagnosticDetail();
            if (detail != null && !detail.isEmpty() && !detail.contains("还没试")) {
                textList.add(Component.literal("§7诊断：§f" + detail));
            }
        }
    }

    // ------------------------------------------------------------------
    // 热容嵌入
    // ------------------------------------------------------------------

    @Override
    public InteractionResult onUse(@NotNull BlockState state, @NotNull Level world, @NotNull BlockPos pos,
                                   @NotNull Player player, @NotNull InteractionHand hand,
                                   @NotNull BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(GTLAddItems.INSTANCE.getRELATIVISTIC_HEAT_CAPACITOR().get())) {
            if (world.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (this.relativisticHeatCapacitorInstalled) {
                serverPlayer.displayClientMessage(Component.translatable(
                        "gtlmodular.cooling_tower_condensation_center.heat_capacitor_already_installed"), false);
            } else if (stack.getCount() < HEAT_CAPACITOR_STACK_SIZE) {
                serverPlayer.displayClientMessage(Component.translatable(
                        "gtlmodular.cooling_tower_condensation_center.heat_capacitor_need_stack"), false);
            } else {
                if (!player.isCreative()) {
                    stack.shrink(HEAT_CAPACITOR_STACK_SIZE);
                }
                this.relativisticHeatCapacitorInstalled = true;
                serverPlayer.displayClientMessage(Component.translatable(
                        "gtlmodular.cooling_tower_condensation_center.heat_capacitor_installed"), false);
            }
            return InteractionResult.SUCCESS;
        }
        return super.onUse(state, world, pos, player, hand, hit);
    }

    @Override
    public void onDrops(@NotNull List<ItemStack> list) {
        if (this.relativisticHeatCapacitorInstalled) {
            list.add(new ItemStack(GTLAddItems.INSTANCE.getRELATIVISTIC_HEAT_CAPACITOR().get(),
                    HEAT_CAPACITOR_STACK_SIZE));
        }
    }

    public final boolean hasRelativisticHeatCapacitor() {
        return this.relativisticHeatCapacitorInstalled;
    }

    /**
     * 没装线程仓时的「原版工作逻辑」外壳：GTCEu {@link RecipeLogic}。
     * 唯一改动：{@code updateTickSubscription} 空实现 —— 由 {@link ThreadedRecipeLogic} 统一驱动。
     */
    private static final class VanillaFallbackLogic extends RecipeLogic {

        VanillaFallbackLogic(@NotNull CoolingTowerCondensationCenterMachine machine) {
            super(machine);
        }

        @Override
        public void updateTickSubscription() {
            // 由独立线程引擎统一驱动，这里必须是空实现。
        }
    }
}
