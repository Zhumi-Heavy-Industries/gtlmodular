package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.config.GTLModularConfig;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * ★ 2026-11-10：<b>通用独立线程引擎 · EU 能源缓冲（每 N tick 才调一次能源仓；优先从电网扣）</b>。
 *
 * <h2>两条取电顺序（队长口径）</h2>
 * <ol>
 *   <li><b>无线电网优先</b>（gtmthings {@code WirelessEnergyManager}，放置者 UUID 在
 *       {@code IMachineLife.onMachinePlaced} 时由机器转发给本逻辑）——「直接从电网抽电」；</li>
 *   <li>电网余额不够 / 没有身份 ⇒ <b>能源仓兜底</b>（{@code EnergyContainerList}）。</li>
 * </ol>
 * 每 {@link #refillTicks}（默认 10）tick 才批量补一次能（补上一窗口真实消耗），
 * 所以能源仓/电网的调用频率 ≈ 每 10 tick 一次，而不是每 tick 每批次一次。
 *
 * <h2>为什么不能每 tick 直扣</h2>
 * 引擎每个批次每 tick 都会走一次 {@code handleTickRecipeIO} ⇒ 每 tick 都穿 EU 能力链。
 * 本处理器插在 {@code IO.IN + EU} 能力列表最前面，先把这一 tick 的电从缓存付掉。
 *
 * <h2>账目纪律（防复制/防丢电）</h2>
 * <ul>
 *   <li>缓存分两份：{@code cachedEU}（总余额）与 {@code gridEU}（其中来自电网的部分）；</li>
 *   <li>花钱时<b>先花电网那份</b> ⇒ 复位/存档时 {@code cachedEU - gridEU} 退回能源仓，
 *       {@code gridEU} <b>退回电网</b>（绝不把电网的电灌进能源仓）；</li>
 *   <li>缓存不够时<b>整额交回</b>后面的真能源仓（不部分支付、不双扣）。</li>
 * </ul>
 *
 * <h2>边界</h2>
 * 机器/能源仓界面会少显示最多一个窗口的存量（缓存里的电不在 {@code getEnergyStored()} 里）；
 * 能力表被重建后每 tick {@code tryInstall()} 幂等补插；配置关掉则整个不创建。
 */
public final class EnergyBufferHandler implements IRecipeHandler<Long> {

    /** 电网余额读数的缓存时长（tick）＝ 20；同 20 tick 内匹配/付款读同一份余额。 */
    private static final int BALANCE_TICKS = 20;

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final IRecipeLogicMachine machine;

    /** 补能间隔（tick）——即「每 N tick 才调用一次能源仓」。 */
    private final int refillTicks;

    /** 电网取电身份（放置者 UUID；可空）。 */
    private final Supplier<UUID> gridUser;

    /** 缓存总余额（EU）。 */
    private long cachedEU;

    /** 缓存中来自电网的部分（复位/存档时退回电网，不灌进能源仓）。 */
    private long gridEU;

    /** 距下次补能的 tick 数。 */
    private int countdown;

    /** 上一窗口的真实消耗量（补能目标 = 它）。 */
    private long windowDemand;

    /** 电网余额缓存（BigInteger → long，按 {@link #BALANCE_TICKS} 刷新）。 */
    private long cachedBalance;
    private long balanceTick = Long.MIN_VALUE;

    /** 安装失败（能力表只读 / 被别的模组替换）后不再重试。 */
    private boolean disabled;

    public EnergyBufferHandler(IRecipeLogicMachine machine, int refillTicks, Supplier<UUID> gridUser) {
        this.machine = machine;
        this.refillTicks = Math.max(1, refillTicks);
        this.gridUser = gridUser;
        this.countdown = this.refillTicks;
    }

    // ------------------------------------------------------------------ 安装 / 每 tick

    /** 把自己插到 {@code IO.IN + EU} 能力列表最前面（幂等）。 */
    public boolean tryInstall() {
        if (disabled) {
            return false;
        }
        try {
            List<IRecipeHandler<?>> handlers =
                    machine.getCapabilitiesProxy().get(IO.IN, EURecipeCapability.CAP);
            if (handlers == null || handlers.isEmpty()) {
                return false;   // 还没成形 / 还没挂能源仓
            }
            if (handlers.contains(this)) {
                return true;
            }
            handlers.add(0, this);
            return true;
        } catch (Throwable t) {
            disabled = true;
            return false;
        }
    }

    /** 引擎每 tick 调一次：安装检查 + 到点补能。 */
    public void tick() {
        if (disabled) {
            return;
        }
        if (!tryInstall()) {
            return;
        }
        if (--countdown > 0) {
            return;
        }
        countdown = refillTicks;
        refill();
    }

    /** 到点补能：先电网、后能源仓；补「上一窗口真实消耗量 − 现有余额」。 */
    private void refill() {
        long want = windowDemand - cachedEU;
        windowDemand = 0L;
        if (want <= 0L) {
            return;
        }
        long fromGrid = takeFromGrid(want);
        if (fromGrid > 0L) {
            cachedEU = saturatedAdd(cachedEU, fromGrid);
            gridEU = saturatedAdd(gridEU, fromGrid);
            want -= fromGrid;
        }
        if (want <= 0L) {
            return;
        }
        IEnergyContainer container = energyContainer();
        if (container == null) {
            return;
        }
        long stored = container.getEnergyStored();
        long take = Math.min(want, stored);
        if (take <= 0L) {
            return;
        }
        long changed = container.changeEnergy(-take);
        if (changed < 0L) {
            cachedEU = saturatedAdd(cachedEU, -changed);
        }
    }

    /** 复位 / 存档 / 切回原逻辑：能源仓那份退回仓，电网那份退回电网。 */
    public void flushBack() {
        if (cachedEU <= 0L) {
            gridEU = 0L;
            windowDemand = 0L;
            countdown = refillTicks;
            return;
        }
        long hatchPart = Math.max(0L, cachedEU - gridEU);
        if (hatchPart > 0L) {
            IEnergyContainer container = energyContainer();
            if (container != null) {
                container.changeEnergy(hatchPart);
            }
        }
        long gridPart = Math.min(gridEU, cachedEU);
        if (gridPart > 0L) {
            UUID user = user();
            if (user != null) {
                try {
                    com.hepdd.gtmthings.api.misc.WirelessEnergyManager
                            .addEUToGlobalEnergyMap(user, gridPart, machine.self());
                } catch (Throwable ignored) {
                    // 退不回电网就丢弃（绝不允许把它灌进能源仓 → 复制）
                }
            }
        }
        cachedEU = 0L;
        gridEU = 0L;
        windowDemand = 0L;
        countdown = refillTicks;
    }

    @Nullable
    private IEnergyContainer energyContainer() {
        try {
            if (machine.self() instanceof WorkableElectricMultiblockMachine workable) {
                return workable.getEnergyContainer();
            }
        } catch (Throwable ignored) {
            // 未成形 / 容器未就绪
        }
        return null;
    }

    // ------------------------------------------------------------------ 电网

    @Nullable
    private UUID user() {
        try {
            return gridUser == null ? null : gridUser.get();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 电网余额（缓 {BALANCE_TICKS} tick；BigInteger 钳到 long）。 */
    private long gridBalance() {
        if (!GTLModularConfig.isThreadingGridPower()) {
            return 0L;
        }
        UUID user = user();
        if (user == null) {
            return 0L;
        }
        long now;
        try {
            var level = machine.self().getLevel();
            now = level == null ? Long.MIN_VALUE : level.getGameTime();
        } catch (Throwable t) {
            return 0L;
        }
        if (now == Long.MIN_VALUE) {
            return 0L;
        }
        if (balanceTick == Long.MIN_VALUE || now - balanceTick >= BALANCE_TICKS) {
            balanceTick = now;
            try {
                BigInteger balance =
                        com.hepdd.gtmthings.api.misc.WirelessEnergyManager.getUserEU(user);
                cachedBalance = balance == null || balance.signum() <= 0
                        ? 0L
                        : (balance.compareTo(LONG_MAX) > 0 ? Long.MAX_VALUE : balance.longValue());
            } catch (Throwable t) {
                cachedBalance = 0L;
            }
        }
        return cachedBalance;
    }

    /** 从无线电网扣 {@code want}（返回真扣到的量；扣不到 0）。 */
    private long takeFromGrid(long want) {
        if (want <= 0L || !GTLModularConfig.isThreadingGridPower()) {
            return 0L;
        }
        UUID user = user();
        if (user == null) {
            return 0L;
        }
        long take = Math.min(want, gridBalance());
        if (take <= 0L) {
            return 0L;
        }
        try {
            if (!com.hepdd.gtmthings.api.misc.WirelessEnergyManager
                    .addEUToGlobalEnergyMap(user, -take, machine.self())) {
                return 0L;
            }
        } catch (Throwable t) {
            return 0L;
        }
        cachedBalance = Math.max(0L, cachedBalance - take);
        return take;
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return (b > 0L && sum < a) ? Long.MAX_VALUE : sum;
    }

    // ------------------------------------------------------------------ IRecipeHandler<Long>

    /**
     * 配方要 EU 时先走缓存；缓存不够先问电网补差额，仍不够就把整额交回真能源仓。
     */
    @Override
    public List<Long> handleRecipeInner(IO io, GTRecipe recipe, List<Long> left,
                                        @Nullable String slot, boolean simulate) {
        if (io != IO.IN || left == null || left.isEmpty()) {
            return left;
        }
        long need = 0L;
        for (Long v : left) {
            if (v == null || v <= 0L) {
                continue;
            }
            need = saturatedAdd(need, v);
        }
        if (need <= 0L) {
            return null;
        }
        if (simulate) {
            long available = saturatedAdd(cachedEU, gridBalance());
            return available >= need ? null : List.of(need);
        }
        windowDemand = saturatedAdd(windowDemand, need);
        if (cachedEU < need) {
            long fromGrid = takeFromGrid(need - cachedEU);
            if (fromGrid > 0L) {
                cachedEU = saturatedAdd(cachedEU, fromGrid);
                gridEU = saturatedAdd(gridEU, fromGrid);
            }
        }
        if (cachedEU >= need) {
            // 先花电网那份（复位时电网的电只能退回电网）
            long fromGrid = Math.min(need, gridEU);
            gridEU -= fromGrid;
            cachedEU -= fromGrid;
            cachedEU -= (need - fromGrid);
            return null;
        }
        return List.of(need);
    }

    @Override
    public RecipeCapability<Long> getCapability() {
        return EURecipeCapability.CAP;
    }

    /**
     * 排序优先级：{@code IFilteredHandler.HIGHEST}。
     *
     * <p>GTCEu 的 {@code RecipeRunner} 在付款前会
     * {@code handlers.sort(IRecipeHandler.ENTRY_COMPARATOR)}：先「非空在前」、再按优先级降序。
     * 本缓存必须排在真能源仓<b>前面</b>，否则真仓先付款、本处理器连
     * {@code handleRecipeInner} 都不会被调用 ⇒ {@code windowDemand} 永远是 0、缓存永远补不起来。
     */
    @Override
    public int getPriority() {
        return com.gregtechceu.gtceu.api.capability.recipe.IFilteredHandler.HIGHEST;
    }

    @Override
    public List<Object> getContents() {
        return Collections.singletonList(cachedEU);
    }

    /** 排序用「非空」读数；必须恒 > 0（理由见 {@link #getPriority()}），真实匹配走 handleRecipeInner。 */
    @Override
    public double getTotalContentAmount() {
        return Math.max(1.0, (double) cachedEU);
    }

    @Override
    public int getSize() {
        return 1;
    }

    /** 只读：当前缓存余额（诊断/显示用）。 */
    public long getCachedEU() {
        return cachedEU;
    }
}
