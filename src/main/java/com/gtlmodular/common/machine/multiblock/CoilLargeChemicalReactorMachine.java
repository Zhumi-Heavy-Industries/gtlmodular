package com.gtlmodular.common.machine.multiblock;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 大型化学反应釜（{@code gtceu:large_chemical_reactor}）的线圈版机器类 —— 2026-11-09。
 *
 * <h2>为什么要有这个类</h2>
 * 本整合包的 gtceu 是 <b>1.4.4</b>：{@code GTMachines.LARGE_CHEMICAL_REACTOR} 用的是
 * {@code WorkableElectricMultiblockMachine}，结构 C 位写死
 * {@code Predicates.blocks(GTBlocks.COIL_CUPRONICKEL).setExactLimit(1)}，
 * 机器本体<b>从不读线圈</b> —— 玩家放的那个白铜线圈纯装饰，换高级线圈也放不进去。
 *
 * <h2>参考对象：GTO 的大型化学反应釜（行为 1:1）</h2>
 * GTO（{@code com.gtocore.common.machine.multiblock.electric.LargeChemicalReactorMachine}）
 * 继承 gtolib 的 {@code CoilMultiblockMachine}，在 {@code getRealRecipe} 里：
 * <pre>
 *   RecipeModifier.overclocking(this, unit, recipe, false, 1, 1,
 *           (getCoilTier() + 2 &gt; recipe.tier) ? 0.25 : 0.5)
 * </pre>
 * 对照 GTCEu 26.x 的
 * {@code RecipeModifier#overclocking(holder, unit, recipe, boolean generator,
 * double euMultiplier, double durationMultiplier, double durationFactor)} 可知：
 * 最后那个 0.25 / 0.5 是 <b>「每一次超频的耗时倍率」</b>（euMultiplier 与 durationMultiplier
 * 都是 1，不额外省电也不额外加速）——
 * <ul>
 *   <li>线圈等级 + 2 &gt; 配方等级 ⇒ 每次超频耗时 ×0.25 = <b>无损超频</b>（每档 4 倍速）；</li>
 *   <li>否则 ⇒ 每次超频耗时 ×0.5 = <b>普通超频</b>（每档 2 倍速）。</li>
 * </ul>
 * 这正是 GTO 自己的描述：「线圈等级不低于运行的配方等级时启用无损超频」。
 *
 * <h2>线圈等级怎么读（★ 2026-11-09 实机反馈修正）</h2>
 * 玩家反馈「换线圈以后加热线圈等级不跟着变」。基类
 * {@link CoilWorkableElectricMultiblockMachine} 只在 {@code onStructureFormed()} 里从图案上下文
 * 读一次 {@code "CoilType"}，而结构重判/成型回调在个别路径上不一定按预期重跑 ⇒ 界面上那个数字会卡住。
 * 这里改成<b>以实际方块为准</b>：
 * <ul>
 *   <li>{@link #readCoilFromStructure(boolean)} 直接遍历 {@code MultiblockState#getCache()} 里的
 *       成型方块，找到 {@link CoilBlock} 就取它的 {@code coilType}（比 matchContext 更硬）；</li>
 *   <li>成型时立刻读一次；另外每 20 tick（1 秒）巡检一次（只在服务端），换线圈后最多 1 秒自愈；</li>
 *   <li>读到后写进 {@link #formedCoilTier}（{@code @DescSynced}），界面与超频口径都读它，
 *       客户端也能拿到正确值（不再依赖「成型回调 + matchContext」这条容易掉链子的路径）。</li>
 * </ul>
 *
 * <h2>结构</h2>
 * 结构仍是 3×3×3，但线圈槽<b>只有背面正中那一个</b>（见 {@code LargeChemicalReactorCoilSetup} 的
 * {@code buildPattern}）：正面/侧面的旧线圈位全部改回外壳/仓室，JEI 预览也只画那一种。
 */
public class CoilLargeChemicalReactorMachine extends CoilWorkableElectricMultiblockMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            CoilLargeChemicalReactorMachine.class,
            CoilWorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** 结构里实际读到的线圈等级（0 基：白铜=0、坎塔尔=1……）；-1 = 还没读到。 */
    @DescSynced
    private int formedCoilTier = -1;

    /** 线圈巡检计数器（每 20 tick 查一次）。 */
    private int coilRefreshCounter;

    public CoilLargeChemicalReactorMachine(@NotNull IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /** 服务端订阅每 tick 的巡检；客户端不订阅。 */
    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() != null && !getLevel().isClientSide()) {
            subscribeServerTick(this::tickCoilRefresh);
        }
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        readCoilFromStructure(true);
    }

    private void tickCoilRefresh() {
        if (++coilRefreshCounter < 20) {
            return;
        }
        coilRefreshCounter = 0;
        if (isFormed()) {
            readCoilFromStructure(false);
        }
    }

    /**
     * 以结构里的实际方块为准读线圈（服务端）。
     *
     * <p>为什么不用 {@code getMatchContext().get("CoilType")}：那条路径只在「成型判定刚跑完」时可靠，
     * 实机出现过换线圈后等级不更新的情况；直接看方块永远是最新的。
     *
     * @param logWhenChanged 等级变化时是否打日志（成型时打，周期巡检打 debug 以避免刷屏）
     */
    private void readCoilFromStructure(boolean logWhenChanged) {
        if (getLevel() == null || getLevel().isClientSide()) {
            return;
        }
        ICoilType found = null;
        for (BlockPos pos : getMultiblockState().getCache()) {
            Block block = getLevel().getBlockState(pos).getBlock();
            if (block instanceof CoilBlock coil && coil.coilType != null) {
                found = coil.coilType;
                break;
            }
        }
        if (found == null) {
            return;
        }
        int tier = found.getTier();
        if (tier != formedCoilTier) {
            formedCoilTier = tier;
            if (logWhenChanged) {
                GTLModular.LOGGER.info("[gtlmodular] 大型化学反应釜 @{}：加热线圈 = {}（等级 {}）",
                        getPos(), found.getName(), tier + 1);
            } else {
                GTLModular.LOGGER.debug("[gtlmodular] 大型化学反应釜 @{}：加热线圈巡检更新 = {}（等级 {}）",
                        getPos(), found.getName(), tier + 1);
            }
        }
    }

    /** 当前生效的线圈等级：优先用巡检/成型读到的实际值，读不到再退回基类字段。 */
    public int getFormedCoilTier() {
        return formedCoilTier >= 0 ? formedCoilTier : getCoilTier();
    }

    /**
     * 线圈等级是否足以让这台机器用「无损超频」（GTO 判定式）。
     *
     * @param recipeTier 配方等级 = 配方 EU/t 折算的电压等级（ULV=0、LV=1、MV=2……）
     */
    public boolean isPerfectOverclockAvailable(int recipeTier) {
        return getFormedCoilTier() + GTLModularConfig.getLcrCoilTierAdvantage() > recipeTier;
    }

    /**
     * 本次超频该用哪个「每次超频耗时倍率」（GTO：0.25 无损 / 0.5 普通）。
     *
     * @param recipeTier 配方等级（同上）
     */
    public double getOverclockDurationFactor(int recipeTier) {
        if (!GTLModularConfig.isLcrCoilEnabled()) {
            return GTLModularConfig.getLcrCoilPerfectOverclockFactor();
        }
        return isPerfectOverclockAvailable(recipeTier)
                ? GTLModularConfig.getLcrCoilPerfectOverclockFactor()
                : GTLModularConfig.getLcrCoilNormalOverclockFactor();
    }

    /**
     * 界面文字：在标准多方块显示（能量/并行/进度…）之后补一行「加热线圈等级：N」。
     *
     * <p>用 gtceu 自带的 {@code gtceu.multiblock.multi_furnace.heating_coil_level} 键，
     * 与 GTO 的大型化学反应釜同款显示（GTO 也是 {@code getCoilTier() + 1}）。
     */
    @Override
    public void addDisplayText(@NotNull List<Component> textList) {
        super.addDisplayText(textList);
        if (!isFormed() || !GTLModularConfig.isLcrCoilEnabled()) {
            return;
        }
        textList.add(Component.translatable("gtceu.multiblock.multi_furnace.heating_coil_level",
                getFormedCoilTier() + 1));
    }
}
