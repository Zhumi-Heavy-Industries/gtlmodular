package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.config.GTLModularConfig;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * NAC 主机能量面（P3 · M3 抽取）：<b>电网容器 + 只读能量查询</b>。
 *
 * <p>本类只做两件事：① 把 {@link GridEnergyContainer} 这个「从无线电网取电」的
 * {@code IEnergyContainer} / {@code IRecipeHandler} 实现搬出主机；② 主机那批纯只读能量读数
 * （蓄电 / 容量 / 输入电压 / 输入功率 / 可获取量 / 无限源判定 / 取电身份判定）。
 *
 * <p><b>刻意不改的三件事</b>（R7 高风险，逐条守）：
 * <ol>
 *   <li>主机 {@code getEnergyContainer()} 的方法体<b>留在主机原样不动</b>（只把
 *       {@code innerContainers(...)} 的调用改成 {@code NanochipEnergy.innerContainers(...)}）——
 *       「电网容器 index 0 → 基类能源仓随后」的顺序<b>一个字未改</b>；</li>
 *   <li>反射目标仍是 {@code EnergyContainerList.class.getDeclaredField("energyContainerList")}
 *       （{@link #ENERGY_CONTAINER_LIST_FIELD}），取不到就 {@code null} ⇒ 同一退化路径；
 *       主机方法体也<b>没有</b>被改写；</li>
 *   <li>{@code @Persisted gridPowerUser} 字段与它的全部写点（{@code onMachinePlaced} /
 *       {@code resolveGridUser}）留在主机；本类只通过 {@code machine.gridPowerUser()} 只读它。</li>
 * </ol>
 *
 * <p>有字段写 / 限流计数器的方法一律没搬：{@code logUnlimitedEnergyAssist}、
 * {@code requestModuleEnergy}（写 {@code drawAccumulator}）、{@code payModuleEnergy}、
 * {@code distributeModuleEnergy}、{@code gridEnergyContainer()} 缓存访问器、{@code register/unregisterGridPowerHandler}。
 */
final class NanochipEnergy {

    private NanochipEnergy() {}

    /** {@link EnergyContainerList} 私有列表字段的缓存句柄（{@code null} = 取不到 ⇒ 走退化路径）。 */
    @Nullable
    private static final Field ENERGY_CONTAINER_LIST_FIELD = resolveEnergyListField();

    /**
     * ★ 2026-11-13【NAC 电网取电】<b>本机这一刻有没有取电身份</b>。
     *
     * <p>判据唯一：{@link NanochipAssemblyComplexMachine#gridPowerUser} 非空<b>且</b>配置开关打开。
     * 两者缺一 ⇒ 电网容器自报 0 压 0 安 0 容量 ⇒ {@code isEnergySourceEffectivelyUnlimited()} 为假
     * ⇒ 全机<b>逐字回到"必须有能源仓"的老行为</b>（老存档读档后就是这个状态）。
     */
    static boolean hasGridPowerIdentity(NanochipAssemblyComplexMachine machine) {
        return machine.gridPowerUser() != null && GTLModularConfig.isNanochipGridPowerEnabled();
    }

    static List<IEnergyContainer> innerContainers(EnergyContainerList list) {
        try {
            Field f = ENERGY_CONTAINER_LIST_FIELD;
            if (f == null) {
                return List.of();
            }
            Object raw = f.get(list);
            if (raw instanceof List) {
                List l = (List)raw;
                ArrayList<IEnergyContainer> out = new ArrayList<IEnergyContainer>(l.size());
                for (Object o : l) {
                    if (!(o instanceof IEnergyContainer)) continue;
                    IEnergyContainer c = (IEnergyContainer)o;
                    out.add(c);
                }
                return out;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return List.of();
    }

    @Nullable
    static Field resolveEnergyListField() {
        try {
            Field f = EnergyContainerList.class.getDeclaredField("energyContainerList");
            f.setAccessible(true);
            return f;
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    /** 复合体自身蓄电（EU）。 */
    static long getTotalStoredEU(NanochipAssemblyComplexMachine machine) {
        if (!machine.isFormed()) {
            return 0L;
        }
        EnergyContainerList e = machine.getEnergyContainer();
        return e == null ? 0L : e.getEnergyStored();
    }

    /** 复合体自身蓄电上限（EU）。 */
    static long getTotalCapacityEU(NanochipAssemblyComplexMachine machine) {
        if (!machine.isFormed()) {
            return 0L;
        }
        EnergyContainerList e = machine.getEnergyContainer();
        return e == null ? 0L : e.getEnergyCapacity();
    }

    /** 复合体能源仓输入电压。 */
    static long getMaxInputVoltage(NanochipAssemblyComplexMachine machine) {
        if (!machine.isFormed()) {
            return 0L;
        }
        // 电网/免费电时直接给哨兵电压：EnergyContainerList 会把多个容器的
        // 电压×电流相加，Long.MAX 级电网叠上能源仓后可能溢出成 0，
        // 模块的 HostEnergyContainer 就会显示“最大输入 0”。
        if (GTLModularConfig.isNanochipGridPowerEnabled() || GTLModularConfig.isNanochipFreeEnergyEnabled()) {
            return Long.MAX_VALUE;
        }
        EnergyContainerList e = machine.getEnergyContainer();
        if (e == null) {
            return 0L;
        }
        // 有些无线电源只报“已存/容量 = Long.MAX”，电压字段是 0；模块代理读的就是
        // 这个电压，所以这里把 Long.MAX 级储能/容量也视为 MAX 输入电压。
        if (e.getEnergyStored() >= NanochipAssemblyComplexMachine.ON_DEMAND_POWER_FLOOR_EU
                || e.getEnergyCapacity() >= NanochipAssemblyComplexMachine.ON_DEMAND_POWER_FLOOR_EU) {
            return Long.MAX_VALUE;
        }
        return e.getInputVoltage();
    }

    /**
     * <b>NAC 最大输入功率（EU/t）＝ 能源仓输入电压 × 安培数</b>。
     *
     * <p>wiki 的缓存公式读的就是它：{@code 模块缓存 = NAC最大输入功率 × (2 × 模块最大配方时间)}，
     * 以及「超频后的功率不能超过 NAC 可用输入功率」。
     * 这里对 {@code 电压 × 安培} 做一次**饱和乘法**（电压到 OPV/MAX 档时数值极大，直接乘会回绕成负数）。
     */
    static long getMaxInputPower(NanochipAssemblyComplexMachine machine) {
        if (!machine.isFormed()) {
            return 0L;
        }
        EnergyContainerList e = machine.getEnergyContainer();
        if (e == null) {
            return 0L;
        }
        long v = machine.getMaxInputVoltage();
        long a = Math.max(1L, e.getInputAmperage());
        if (v <= 0L) {
            return 0L;
        }
        return NanochipAssemblyComplexMachine.saturatingMul(v, a);
    }

    /**
     * ★ 2026-10-08【无线电网供电兼容】<b>本复合体的供电是不是「无限 / 按需现取」型</b>。
     *
     * <p>判据只看能源容器<b>自报的容量</b>（{@link EnergyContainerList#getEnergyCapacity()}）：
     * 达到 {@link NanochipAssemblyComplexMachine#ON_DEMAND_POWER_FLOOR_EU} ⇒ 真。覆盖两类真实能源：
     * GTL Additions 的「无线电网输入终端」与创造级能源仓 —— 它们都<b>不储能、按需现取</b>，
     * 因此 {@code getEnergyStored()} 只能是 0 或一个没有意义的饱和值，
     * <b>不能用「已存多少」来判断它们给不给得出电</b>。
     *
     * <p><b>容器类名的真实出处</b>（供日后核对，本方法刻意不依赖它）：
     * 无线电网输入终端 {@code com.gtladd.gtladditions.common.machine.multiblock.part.WirelessEnergyNetworkTerminalPartMachineBase}
     * 提供的是 {@code com.gtladd.gtladditions.common.machine.trait.NetworkEnergyContainer}，
     * 其类签名逐字为
     * {@code NotifiableRecipeHandlerTrait<Long> + IEnergyContainer + IInt128EnergyContainer}
     * ——{@code _census/_probe_nacmat_evidence/classstr_gtladditions-3.2.8Custom_SubSpace-fix1.txt:12007-12017, 12609-12616}；
     * 其中 {@code IInt128EnergyContainer} 是 gtlcore 真实接口
     * （{@code org.gtlcore.gtlcore.api.capability.IInt128EnergyContainer}，方法
     * {@code getInt128EnergyStored/…Capacity/…InputPerSec/…OutputPerSec}，
     * ——同目录 {@code classstr_gtlcore-1.2.3.2-fix1.txt:9-22}），
     * 即「容量超出 long 表达范围、必须用 Int128 计量」的能源所实现的那一个接口。
     */
    static boolean isEnergySourceEffectivelyUnlimited(NanochipAssemblyComplexMachine machine) {
        if (!machine.isFormed()) {
            return false;
        }
        // 电网/免费电本质是“按需现取”，容量本身已是 Long.MAX 级；不能再让
        // EnergyContainerList 把电网容量和能源仓容量相加后溢出成负数/0 来判断。
        if (GTLModularConfig.isNanochipGridPowerEnabled() || GTLModularConfig.isNanochipFreeEnergyEnabled()) {
            return true;
        }
        EnergyContainerList e = machine.getEnergyContainer();
        if (e == null) {
            return false;
        }
        return e.getEnergyCapacity() >= 0x3FFFFFFFFFFFFFFFL;
    }

    /**
     * ★ 2026-10-08【无线电网供电兼容】<b>本复合体这一刻「可获取」的 EU</b>
     * —— 即「需要量 ≤ 主机可提供的量」这条口径里的右边。
     *
     * <ul>
     *   <li>储能式（正常能源仓/激光仓）：返回<b>已存</b>（{@link EnergyContainerList#getEnergyStored()}），
     *       与改动前的 {@code avail} 口径<b>逐字一致</b>；</li>
     *   <li>无限 / 按需现取型（无线电网终端、创造能源）：已存没有意义（恒为 0）⇒
     *       返回<b>容量</b>（{@link NanochipAssemblyComplexMachine#ON_DEMAND_POWER_FLOOR_EU} 以上），表示「要多少现取多少」；</li>
     *   <li>真·完全无电（容量 0 且存量 0）：返回 0 ⇒ 调用方照旧拦住。</li>
     * </ul>
     */
    static long getObtainableEnergyEU(NanochipAssemblyComplexMachine machine) {
        if (!machine.isFormed()) {
            return 0L;
        }
        EnergyContainerList e = machine.getEnergyContainer();
        if (e == null) {
            return 0L;
        }
        long stored = e.getEnergyStored();
        return isEnergySourceEffectivelyUnlimited(machine) ? Long.MAX_VALUE : stored;
    }

    static final class GridEnergyContainer
    implements IEnergyContainer,
    IRecipeHandler<Long> {
        /** 余额读数缓存时长（tick）＝ 20（1 秒）。同一 tick 内恒定 ⇒ 匹配与付款同源。 */
        private static final long CACHE_TICKS = 20L;
        private final NanochipAssemblyComplexMachine machine;
        private long cachedEu;
        private long cachedAtTick = Long.MIN_VALUE;

        GridEnergyContainer(NanochipAssemblyComplexMachine machine) {
            this.machine = machine;
        }

        @Nullable
        /**
         * 取电身份（{@code null} = 开关已关；开关开着时由
         * {@code NanochipAssemblyComplexMachine#resolveGridUser()} 保证非空）。
         *
         * <p>★ 2026-11-14【照抄山海 · 身份】原先这里直接读 {@code machine.gridPowerUser()}，
         * 于是"没登记放置者"＝ {@code null} ＝ 本容器整段惰性。现在改走
         * {@link NanochipAssemblyComplexMachine#resolveGridUser()}：**没登记就现生成一个随机 UUID
         * 并持久化**，逐字照抄山海 {@code MatterCopierMachine.ensureWirelessUuid()}
         * （{@code _census/shanhai-src/com/dishanhai/gt_shanhai/common/machine/misc/MatterCopierMachine.java:461-465}：
         * {@code if (getUuid() == null) setUuid(UUID.randomUUID());}）。
         * ⇒ 机器**永远有一个自己的电网账户**，"能不能从电网取电"不再由"有没有登记成功"决定。
         */
        private UUID user() {
            return GTLModularConfig.isNanochipGridPowerEnabled() ? this.machine.resolveGridUser() : null;
        }

        /**
         * 该玩家（或他去 FTB 队伍）当下的无线电网余额（EU，夹到非负 long）。
         *
         * <p>{@code getUserEU} 返回 {@code BigInteger}
         * （{@code classstr_gtladditions-...txt:2774-2776}），量级可超出 long ⇒ 先比 {@code Long.MAX_VALUE}
         * 再窄化（<b>绝不</b>直接 {@code longValue()} 让它回绕成负数）。
         * 读数按 {@link #CACHE_TICKS} 缓存：余额查询本身是一次哈希查表，
         * 但本容器每 tick 会被读取点问多次 ⇒ 缓存既省开销、又保证同 tick 内"匹配/付款"读同一个数。
         */
        private long wireEu() {
            long now;
            Level lv = this.machine.getLevel();
            long l = now = lv == null ? Long.MIN_VALUE : lv.getGameTime();
            if (now == Long.MIN_VALUE || this.cachedAtTick == Long.MIN_VALUE || now - this.cachedAtTick >= 20L) {
                this.cachedAtTick = now;
                UUID u = this.user();
                if (u == null) {
                    this.cachedEu = 0L;
                } else {
                    BigInteger bal = null;
                    try {
                        bal = WirelessEnergyManager.getUserEU((UUID)u);
                    }
                    catch (Throwable t) {
                        this.cachedEu = 0L;
                        bal = null;
                    }
                    this.cachedEu = bal == null || bal.signum() <= 0 ? 0L : (bal.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : bal.longValue());
                }
            }
            return this.cachedEu;
        }

        public long getEnergyStored() {
            if (GTLModularConfig.isNanochipFreeEnergyEnabled()) {
                return Long.MAX_VALUE;
            }
            return this.user() == null ? 0L : this.wireEu();
        }

        /**
         * ★ 2026-11-14【照抄山海 · 取消"无身份⇒必须有能源仓"这个坑】
         *
         * <p>改前：没有取电身份 ⇒ 自报 0 ⇒ {@code isEnergySourceEffectivelyUnlimited()} 为假 ⇒
         * 该机**整机退回"必须有能源仓"的老行为**（这正是"用户重放了主机与模块还是供电不足"
         * 那条路上的第二重坑：即使身份登记成功，只要读档/拆放时序错一次就被打回老行为）。
         *
         * <p>改后：只要**开关开着**就自报哨兵容量（与 GTLAdditions「无线电网输入终端」的
         * Long.MAX 级容量同口径，山海 wiki 逐字「普通能源输入上限为 Long.MAX 级别」）——
         * 身份缺失时 {@link #getEnergyStored()} 仍如实报 0 ⇒ 配方匹配阶段照样
         * {@code FAIL_NO_ENOUGH_EU_IN}、机器**原地等电**（不退进度、不掉料），
         * 而不是"悄悄改成必须有能源仓"。
         *
         * <p>本项**不是**装饰：{@code WorkableElectricMultiblockMachine.getOverclockVoltage()} 读
         * {@code getInputVoltage()}，而本机 {@code buildRecipe()} 第一句是
         * {@code if (getOverclockVoltage() <= 0) return null;}（{@code :3311-3315}）
         * ⇒ 输入电压为 0 时一条配方都 build 不出来。
         */
        public long getEnergyCapacity() {
            if (GTLModularConfig.isNanochipFreeEnergyEnabled()) {
                return Long.MAX_VALUE;
            }
            return GTLModularConfig.isNanochipGridPowerEnabled() ? 0x3FFFFFFFFFFFFFFFL : 0L;
        }

        /**
         * 同 {@link #getEnergyCapacity()}：开关开着就报哨兵电压（喂 {@code getOverclockVoltage()}）。
         *
         * <p>★ 2026-11-13【与"无线电网输入终端"对齐】：哨兵值由 {@code ON_DEMAND_POWER_FLOOR_EU}
         * （= {@code Long.MAX_VALUE / 2} = 2^62 ⇒ 面板显示 {@code MAX+15}）改为
         * {@code Long.MAX_VALUE}（⇒ 面板显示 {@code MAX+16}）。
         * 依据：gtladditions 无线电网输入终端的官方 tooltip 逐字为
         * {@code providing up to Long.MAX input power}（{@code strings_gtladditions-…txt:628-632}），
         * 且 GTCEu 的 {@code GTValues.VEX[30] = Long.MAX_VALUE} 对应档名 {@code MAX+16}
         * （{@code VEX}/{@code VNF} 均 31 项，{@code MAX}=14）。
         *
         * <p>本改动**只影响显示**：扣电走 {@code changeEnergy()}、配方结算走 capabilities proxy、
         * 超频被 {@code MAX_OVERCLOCKS=2} 封顶，三者都不读本值。
         * 唯一的例外是模块侧 {@code hatchTier = getTierByVoltage(...)}（29 → 30，可能多一次超频，
         * 中置信待实测，且受时长/功率闸约束）。{@code ON_DEMAND_POWER_FLOOR_EU} 常量与起跑阈值
         * （{@code :4894}）**保持不动** —— 那个才真影响起跑闸。
         */
        public long getInputVoltage() {
            return GTLModularConfig.isNanochipGridPowerEnabled() || GTLModularConfig.isNanochipFreeEnergyEnabled() ? Long.MAX_VALUE : 0L;
        }

        public long getInputAmperage() {
            return 1L;
        }

        public long getOutputVoltage() {
            return 0L;
        }

        public long getOutputAmperage() {
            return 0L;
        }

        public boolean inputsEnergy(Direction side) {
            return false;
        }

        public long acceptEnergyFromNetwork(Direction side, long voltage, long amperage) {
            return 0L;
        }

        /**
         * <b>唯一的取电点</b>：负值 = 扣电，正值 = 不退（NAC 从不回充电网）。
         *
         * <p>语义三条（都为了"记账与真扣一致"）：
         * <ol>
         *   <li>{@code amount >= 0} ⇒ 返回 0（不回充）；</li>
         *   <li>没有取电身份 ⇒ 返回 0 并打一条限流 WARN（<b>绝不静默</b>）；</li>
         *   <li>余额不够 ⇒ 只扣 {@code min(need, 余额)}，返回<b>真实扣到的量</b>
         *       （扣不到就 0）⇒ 剩下的额度由列表后面的能源仓兜底；
         *       一点都扣不到时调用方读到的就是 0 ＝ "等电"。</li>
         * </ol>
         *
         * <p>★ 为什么'只扣能扣的部分'是对的：{@code EnergyContainerList.changeEnergy} 的循环
         * 判据是"加够了没"，所以本容器<b>必须如实返回自己真扣到的量</b>，
         * 否则"承诺扣了但没扣"会让机器带着假电记账（风险 C 的反面）。
         */
        public long changeEnergy(long amount) {
            long need;
            if (amount >= 0L) {
                return 0L;
            }
            if (GTLModularConfig.isNanochipFreeEnergyEnabled()) {
                return amount;
            }
            if (!GTLModularConfig.isNanochipGridPowerEnabled()) {
                return 0L;
            }
            UUID u = this.user();
            if (u == null) {
                this.machine.warnGridDrawWithoutUser("\u7535\u7f51\u5bb9\u5668\u6263\u7535");
                return 0L;
            }
            long l = need = amount == Long.MIN_VALUE ? Long.MAX_VALUE : -amount;
            if (need <= 0L) {
                return 0L;
            }
            long paid = Math.min(need, this.wireEu());
            if (paid <= 0L) {
                return 0L;
            }
            if (!WirelessEnergyManager.addEUToGlobalEnergyMap((UUID)u, (long)(-paid), (MetaMachine)this.machine)) {
                return 0L;
            }
            this.cachedEu = Math.max(0L, this.cachedEu - paid);
            return -paid;
        }

        /** 本处理器结算的通道：EU（`RecipeCapability<Long>`）。 */
        public RecipeCapability<Long> getCapability() {
            return EURecipeCapability.CAP;
        }

        /**
         * 本处理器"此刻有多少" —— 上游的并行/可行性计算（gtlcore `IParallelLogic`）读它。
         * 报真值：余额 0 就是 0（不报哨兵），否则会造出"匹配过了却扣不到"的假电。
         *
         * <p>⚠️ 返回类型必须是 {@code List<Object>}（不是 {@code List<Long>}）——这是接口的签名。
         * 逐字对齐本仓**已编译进主源集**的同款 EU 处理器
         * {@code IndustrialSteamPlatformMachine.SteamCacheEnergyHandler#getContents()}
         * （{@code _census/enhancedcore-src/src/main/java/com/gtl/enhancedcore/common/machine/IndustrialSteamPlatformMachine.java:436-438}）。
         */
        public List<Object> getContents() {
            return Collections.singletonList(this.user() == null ? 0L : this.wireEu());
        }

        /** 同上，供按容量算并行的调用方读（double 口径）。 */
        public double getTotalContentAmount() {
            return this.user() == null ? 0.0 : (double)this.wireEu();
        }

        public List<Long> handleRecipeInner(IO io, GTRecipe recipe, List<Long> left, @Nullable String slot, boolean simulate) {
            long paidSigned;
            if (io != IO.IN || left == null || left.isEmpty()) {
                return left;
            }
            long need = 0L;
            for (Long v : left) {
                if (v == null || v <= 0L) continue;
                need = need > Long.MAX_VALUE - v ? Long.MAX_VALUE : need + v;
            }
            if (need <= 0L) {
                return null;
            }
            long paid = simulate ? (this.user() == null ? 0L : Math.min(need, this.wireEu())) : ((paidSigned = this.changeEnergy(-need)) >= 0L ? 0L : -paidSigned);
            long remain = need - paid;
            return remain <= 0L ? null : List.of(Long.valueOf(remain));
        }
    }
}
