package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.machine.LazyPatternAssemblyPartMachine;
import com.gtlmodular.common.machine.MoltenPatternAssemblyPartMachine;

/**
 * ★ 2026-11-25：<b>多配方表机器「空闲时跟随样板轮换模式」</b> —— 「只能挂猪咪样板总成才能工作」的
 * 第二块补丁（第一块是 {@link BatchFinder} 的「样板在仓里就扫整表」，见
 * {@link BatchSupport#hasWaitingMePattern}）。
 *
 * <h2>为什么还需要这一块</h2>
 * <p>不同家的样板总成，供料口径不一样：
 * <table border="1">
 *   <tr><th>样板总成</th><th>要不要控制器停在「样板所属那张表」上</th></tr>
 *   <tr><td>gtlcore 原生 {@code MEPatternBufferPartMachine}</td>
 *       <td><b>不要</b> —— {@code getAvailablePatterns()} 直接返回全部槽样板，不按类型过滤；
 *           AE 照推不误。它坏的是「冷槽找不到配方」，靠第一块补丁治好。</td></tr>
 *   <tr><td>gtlcore「ME 配方类型统合」{@code MERecipePatternBufferPartMachine}</td>
 *       <td><b>要</b> —— {@code refreshPatterns()} 用
 *           {@code MERecipePatternHelper.getActiveRecipeTypes(controllers)} 展开 AE 样板
 *           （该文件 :175-177），而那套取的是 {@code machine.getRecipeType()}（<b>单值</b>，
 *           见 {@code MERecipePatternHelper:62-81}；它对 {@code getMultiRecipeType} 的反射钩子
 *           只接受<b>一个</b> GTRecipeType，表达不了两张表）⇒ 非激活表的样板<b>根本不会展开</b>，
 *           也就没有料可推。</td></tr>
 *   <tr><td>本模组猪咪 / 超级猪咪 / 惰性猪咪</td>
 *       <td><b>要</b>，但它们<b>自己会切</b>（{@code MoltenPatternAssemblyPartMachine#autoSwitchTick}、
 *           {@code LazyPatternAssemblyPartMachine#autoSwitchTick}）⇒ 本类对它们<b>完全让位</b>，
 *           绝不抢。</td></tr>
 * </table>
 *
 * <h2>本类做什么</h2>
 * <p>控制器有 2 张以上配方表、结构里挂了 ME 样板仓、<b>且那台样板仓不是本模组的猪咪</b>时：
 * 只要引擎<b>一条批次都没有</b>（含等料/等电的 WAITING 批次也算有）并持续 {@value #IDLE_DWELL_TICKS}
 * tick，就把控制器激活的配方表按圈往后挪一格。转一圈之后，每家总成都会轮到「样板所属那张表」，
 * 于是各自按自己的口径把样板展开 / 认上 ⇒ 不再依赖玩家的手动切模式。
 *
 * <h2>纪律（每条都是「不动现有能跑的路径」）</h2>
 * <ol>
 *   <li><b>只在真空闲时切</b>：{@code runningBatches > 0} 一律不动（WAITING 批次也在引擎里），
 *       绝不打断任何在跑/在等的配方；</li>
 *   <li><b>猪咪在场一律让位</b>（它们有完整的自动切换 + 手动择时 + SUPB/增强版停手逻辑，
 *       本类再插一脚只会打架）；</li>
 *   <li><b>没挂样板仓不动</b>：普通输入仓机器靠手动切模式就好，轮换只会让界面上的模式乱跳；</li>
 *   <li><b>单表机器不动</b>：{@code getRecipeTypes().length <= 1} 直接返回；</li>
 *   <li><b>要机器显式选择加入</b>：只认带 {@link IIndependentThreadHost} 标记的多表跨配方主机
 *       （高维等离子聚变核心 / 奈亚拉托提普之触），别的机器<b>一律不轮换</b> ——
 *       NAC 主机按订单自己跨表派工、单表机器没有第二张表，都不该被这里碰；</li>
 *   <li><b>一行回退</b>：{@link #ENABLED} 置 {@code false} ⇒ 立刻逐字回到加本类之前的行为
 *       （调用点只有 {@link ThreadedRecipeLogic#serverTick()} 一处，且本类不写任何存档字段）。</li>
 * </ol>
 *
 * <p><b>零存档影响</b>：只改控制器已有的 {@code activeRecipeType}（本身是 {@code @Persisted} 的既有字段，
 * 玩家手动切也是它），本类自身不落盘、不同步。
 */
final class IdleTableFollow {

    /** 总开关（一行回退用）。 */
    static boolean ENABLED = true;

    /** 真空闲多久才轮换一次：5 秒。太短会让界面上的模式一直在跳。 */
    static final int IDLE_DWELL_TICKS = 100;

    private int idleTicks;

    /**
     * 每个服务端 tick 由 {@link ThreadedRecipeLogic#serverTick()} 调一次。
     *
     * @param machine        配方逻辑所属机器
     * @param runningBatches 引擎里当前批次数（含 WAITING）
     */
    void tick(IRecipeLogicMachine machine, int runningBatches) {
        if (!ENABLED || machine == null) {
            return;
        }
        if (runningBatches > 0) {
            this.idleTicks = 0;
            return;
        }
        try {
            if (!isEligible(machine)) {
                this.idleTicks = 0;
                return;
            }
            if (++this.idleTicks < IDLE_DWELL_TICKS) {
                return;
            }
            this.idleTicks = 0;
            GTRecipeType[] types = machine.getRecipeTypes();
            int current = machine.getActiveRecipeType();
            if (types == null || types.length <= 1 || current < 0 || current >= types.length) {
                return;
            }
            int next = (current + 1) % types.length;
            machine.setActiveRecipeType(next);
            GTLModular.LOGGER.debug("[gtlmodular] 多表机器空闲轮换配方表：{} → {} ({}/{}) @{}",
                    nameOf(types, current), nameOf(types, next), next + 1, types.length,
                    machine.self() == null ? "?" : machine.self().getPos());
        } catch (Throwable ignored) {
            // 轮换是纯体验优化：任何异常都不许影响配方逻辑本身
        }
    }

    /** 多表 + 已成形 + 挂了 ME 样板仓 + 那台仓不是本模组的猪咪。 */
    private static boolean isEligible(IRecipeLogicMachine machine) {
        // ★ 选择加入（不是「凡是多表机器都轮换」）：只有自己声明了「我是多表跨配方主机」的机器才轮换。
        //   判据复用 {@link IIndependentThreadHost} 标记接口（2026-11-26 起它只作标记，
        //   原 isIndependentThreadActive() 已随跨表口径统一而删除）。⇒ 单表机器与 NAC 主机
        //   （走自己的外壳逻辑、按订单跨表派工）都不在轮换范围内，零影响。
        if (!(machine.self() instanceof IIndependentThreadHost)) {
            return false;
        }
        GTRecipeType[] types = machine.getRecipeTypes();
        if (types == null || types.length <= 1) {
            return false;
        }
        if (!(machine.self() instanceof IMultiController controller) || !controller.isFormed()) {
            return false;
        }
        boolean hasMePatternPart = false;
        for (IMultiPart part : controller.getParts()) {
            // 猪咪在场：它有自己的「自动切模式」全套（含手动择时 / 引擎有批次不切 / SUPB 停手），
            // 本类必须让位，否则两边同时对 activeRecipeType 下手。
            if (part instanceof MoltenPatternAssemblyPartMachine
                    || part instanceof LazyPatternAssemblyPartMachine) {
                return false;
            }
            if (part instanceof org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternPartMachine) {
                hasMePatternPart = true;
            }
        }
        return hasMePatternPart;
    }

    private static String nameOf(GTRecipeType[] types, int index) {
        try {
            GTRecipeType type = types[index];
            return type == null || type.registryName == null ? "?" : type.registryName.toString();
        } catch (Throwable ignored) {
            return "?";
        }
    }
}
