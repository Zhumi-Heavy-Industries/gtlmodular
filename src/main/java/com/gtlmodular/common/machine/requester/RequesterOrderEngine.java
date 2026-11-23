package com.gtlmodular.common.machine.requester;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gtlmodular.GTLModular;

import java.util.Arrays;
import java.util.concurrent.Future;

/**
 * 补货牌的下单引擎：<b>串行 + 三重读数 + 15 秒宽限 + 失败冷却</b>。
 *
 * <h2>为什么单独成类</h2>
 * <p>{@link RequesterMachine} 只管"存什么、显示什么"，节奏与订单状态机全在这里，
 * 便于对照 {@code ShanhaiTerminalCraftingManager}（终端那条已验证的链路）审查。
 *
 * <h2>串行</h2>
 * <p>一次只跑<b>一个</b> {@code beginCraftingCalculation} future（AE 的合成计算在
 * {@code CRAFTING_POOL} 线程池里跑，一次丢 16 个会拖慢服务端 tick —— 终端的实证结论）。
 * future 完成后才提交/失败，然后才开始下一格。
 *
 * <h2>三重读数</h2>
 * <pre>
 *   stored   = 网络库存（cachedInventory）
 *   inFlight = craftingService.getRequestedAmount(key)   // 已下单、还在合成中的量
 *   realGap  = target - stored - inFlight
 * </pre>
 *
 * <h2>15 秒宽限</h2>
 * <p>产物回网有延迟，表现为 {@code inFlight 归零但 stored 没涨}。空窗期内<b>不重复下单</b>，
 * 否则会连环下单（值来自设计文档，{@link #GRACE_TICKS} 可调）。
 *
 * <h2>失败冷却</h2>
 * <p>计算失败（缺料/无样板）或提交失败后，同一槽<b>冷却 5 秒</b>再试，避免每 20 tick
 * 打一次完整合成计算。
 */
public final class RequesterOrderEngine {

    /** 轮询周期（tick）。原版请求器同值。 */
    public static final int POLL_INTERVAL = 20;
    /** 产物回网宽限期（tick）= 15 秒。 */
    public static final long GRACE_TICKS = 300L;
    /** 失败冷却（tick）= 5 秒。 */
    public static final long FAILURE_COOLDOWN = 100L;
    /** 单次计算超时（tick）= 20 秒；超时放弃本槽（下次轮询重来）。 */
    public static final long CALC_TIMEOUT = 400L;

    private final RequesterMachine machine;

    /** 当前正在算的 future（串行：同一时刻最多一个）。 */
    private Future<ICraftingPlan> pending;
    private int pendingSlot = -1;
    private AEKey pendingKey;
    private long pendingStartedAt;

    /** 每个槽"最近一次看到在途"的 gameTime；-1 = 从未。 */
    private final long[] lastInFlightSeen = new long[RequesterMachine.SLOT_COUNT];
    /** 每个槽的失败冷却到期时间。 */
    private final long[] retryAfter = new long[RequesterMachine.SLOT_COUNT];
    /** 每个槽「为什么卡住」的人话原因；空串 = 没卡住。 */
    private final String[] slotReason = new String[RequesterMachine.SLOT_COUNT];

    /**
     * 每个槽的下一次申请量上限（0 = 不限，用「缺口」与「批量」里的较小值）。
     *
     * <p>用途：CPU 存储装不下这一单时（AE2 的 {@code bytes} 与合成量成正比），
     * 自动把申请量缩半再算，直到能装下 —— 而不是像以前那样“冷却 5 秒后原样重试”。
     */
    private final long[] amountCap = new long[RequesterMachine.SLOT_COUNT];

    /** 缩单下限：请求量降到这个值仍装不下，判定为 CPU 容量真的不够。 */
    private static final long MIN_AUTO_SHRINK = 1L;

    /** 下一次轮询的 gameTime（不能用 Long.MIN_VALUE 做初值：减法会溢出，导致永不轮询）。 */
    private long nextPoll;
    private boolean lastOnline;
    private TickableSubscription subscription;

    public RequesterOrderEngine(RequesterMachine machine) {
        this.machine = machine;
        Arrays.fill(this.lastInFlightSeen, -1L);
        Arrays.fill(this.slotReason, "");
    }

    // ------------------------------------------------------------------
    // 订阅
    // ------------------------------------------------------------------

    public void subscribe() {
        if (subscription == null || !subscription.isStillSubscribed()) {
            subscription = machine.subscribeTick(machine::engineTick);
        }
    }

    public void unsubscribe() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
        cancelPending();
    }

    private void cancelPending() {
        if (pending != null && !pending.isDone()) {
            pending.cancel(true);
        }
        pending = null;
        pendingSlot = -1;
        pendingKey = null;
    }

    // ------------------------------------------------------------------
    // 状态回调
    // ------------------------------------------------------------------

    /** 网络上下线：立刻把所有已配置槽位状态改成「无网络」（上线时等下一轮轮询自然恢复）。 */
    public void onNetworkStateChanged(boolean online) {
        lastOnline = online;
        if (!online) {
            for (int i = 0; i < RequesterMachine.SLOT_COUNT; i++) {
                if (machine.slot(i).key() != null) {
                    machine.setSlotStatus(i, RequesterMachine.STATUS_OFFLINE);
                }
            }
            machine.setBlockedText("AE 网络离线");
        }
    }

    // ------------------------------------------------------------------
    // 主循环
    // ------------------------------------------------------------------

    public void tick(long gameTime) {
        if (!machine.isOnline()) {
            // 不依赖「状态变更回调」：机器从存档加载时就离线的情况下，回调不会再触发，
            // 这里每次 tick 都幂等补一次（setSlotStatus / setBlockedText 自身会去重）。
            onNetworkStateChanged(false);
            return;
        }
        if (!lastOnline) {
            lastOnline = true;
            // 刚恢复联网：把「离线」提示清掉，具体原因等本轮/下轮轮询重算。
            for (int i = 0; i < slotReason.length; i++) {
                if (RequesterMachine.STATUS_OFFLINE == machine.slotStatusAt(i)) {
                    slotReason[i] = "";
                }
            }
            machine.setBlockedText("");
        }

        // 串行：先推进已完成的 future。
        if (pending != null) {
            if (!pending.isDone()) {
                if (gameTime - pendingStartedAt > CALC_TIMEOUT) {
                    GTLModular.LOGGER.warn("[ME库存补充仓] 合成计算超时，放弃槽位 {}（{}）",
                            pendingSlot, pendingKey == null ? "?" : pendingKey.getDisplayName().getString());
                    fail(pendingSlot, gameTime, "计算超时");
                    if (pendingSlot >= 0) slotReason[pendingSlot] = "合成计算超时（" + describe(pendingKey) + "）";
                    cancelPending();
                    machine.setBlockedText(composeBlockedText());
                }
                return;  // 串行：算着的时候不开始下一格
            }
            completePending(gameTime);
        }

        if (gameTime < nextPoll) {
            return;
        }
        nextPoll = gameTime + POLL_INTERVAL;

        IGrid grid = machine.getMainNode().getGrid();
        if (grid == null) return;
        ICraftingService crafting = grid.getCraftingService();
        KeyCounter stored = grid.getStorageService().getCachedInventory();

        for (int slot = 0; slot < RequesterMachine.SLOT_COUNT; slot++) {
            RequesterSlot config = machine.slot(slot);
            AEKey key = config.key();
            if (key == null) {
                machine.setSlotStatus(slot, RequesterMachine.STATUS_IDLE);
                slotReason[slot] = "";
                continue;
            }
            long target = config.target();
            long have = stored.get(key);
            long inFlight = Math.max(0L, crafting.getRequestedAmount(key));
            if (inFlight > 0) lastInFlightSeen[slot] = gameTime;

            machine.setSlotStock(slot, have);

            if (target <= 0 || have >= target) {
                machine.setSlotStatus(slot, RequesterMachine.STATUS_IDLE);
                slotReason[slot] = "";
                continue;
            }
            if (inFlight > 0) {
                machine.setSlotStatus(slot, RequesterMachine.STATUS_IN_FLIGHT);
                slotReason[slot] = "";
                continue;
            }
            // 在途归零但库存没涨：产物回网延迟的宽限期，别重复下单。
            long seen = lastInFlightSeen[slot];
            if (seen >= 0 && gameTime - seen < GRACE_TICKS) {
                machine.setSlotStatus(slot, RequesterMachine.STATUS_IN_FLIGHT);
                slotReason[slot] = "";
                continue;
            }
            if (gameTime < retryAfter[slot]) {
                // 冷却期间保留原来的具体原因（缺什么/提交失败码），只把状态标成「阻塞」。
                machine.setSlotStatus(slot, RequesterMachine.STATUS_BLOCKED);
                if (slotReason[slot].isEmpty()) slotReason[slot] = describe(key) + "（冷却中）";
                continue;
            }
            if (pending != null) {
                // 串行：本轮只可能有一个在算；其余槽位保持原状，下一轮再算
                // （不能标成「已下单」——那会误导玩家以为已经提交了）。
                continue;
            }

            // 三重读数后的真实缺口；按批量补齐，但不越过目标。
            long gap = target - have - inFlight;
            if (gap <= 0) {
                machine.setSlotStatus(slot, RequesterMachine.STATUS_IDLE);
                continue;
            }
            long batch = machine.batchAt(slot);
            long request = Math.max(1L, Math.min(gap, batch));
            // 上次因 CPU 容量不足缩过单：沿用缩小后的上限，成功后再放开。
            if (amountCap[slot] > 0L) {
                request = Math.max(MIN_AUTO_SHRINK, Math.min(request, amountCap[slot]));
            }

            if (!crafting.isCraftable(key)) {
                machine.setSlotStatus(slot, RequesterMachine.STATUS_IMPOSSIBLE);
                slotReason[slot] = describe(key) + "（无合成方式）";
                continue;
            }

            startCalculation(grid, slot, key, request, gameTime);
        }

        machine.setBlockedText(composeBlockedText());
    }

    /** 把各槽的具体原因合成一行提示：最多列 2 条，其余只报数量。 */
    private String composeBlockedText() {
        int count = 0;
        StringBuilder shown = new StringBuilder();
        int shownCount = 0;
        for (String reason : slotReason) {
            if (reason == null || reason.isEmpty()) continue;
            count++;
            if (shownCount < 2) {
                if (shownCount > 0) shown.append("；");
                shown.append(reason);
                shownCount++;
            }
        }
        if (count == 0) return "";
        if (count <= 2) return "需干预：" + shown;
        return "需干预（" + count + " 处）：" + shown + " 等";
    }

    // ------------------------------------------------------------------
    // 计算 → 提交
    // ------------------------------------------------------------------

    private void startCalculation(IGrid grid, int slot, AEKey key, long amount, long gameTime) {
        var level = machine.getLevel();
        if (level == null) return;
        try {
            // 先问 ECO：装了 neoecoae 且网络里有计算主机时，它的规划器能处理循环/替代配方。
            // 返回 null 表示“不用 ECO”，退回 AE2 原生规划。
            Future<ICraftingPlan> future = EcoPlanningBridge.begin(
                    level, grid, machine.getActionSource(), key, amount,
                    CalculationStrategy.REPORT_MISSING_ITEMS);
            if (future == null) {
                future = grid.getCraftingService().beginCraftingCalculation(
                        level, () -> machine.getActionSource(), key, amount,
                        CalculationStrategy.REPORT_MISSING_ITEMS);
            }
            pending = future;
            pendingSlot = slot;
            pendingKey = key;
            pendingStartedAt = gameTime;
            machine.setSlotStatus(slot, RequesterMachine.STATUS_ORDERING);
            slotReason[slot] = "";
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[ME库存补充仓] 启动合成计算失败：{}", describe(key), t);
            fail(slot, gameTime, "计算启动失败");
            cancelPending();
        }
    }

    private void completePending(long gameTime) {
        int slot = pendingSlot;
        AEKey key = pendingKey;
        Future<ICraftingPlan> future = pending;
        pending = null;
        pendingSlot = -1;
        pendingKey = null;

        ICraftingPlan plan;
        try {
            plan = future.get();
        } catch (Exception e) {
            fail(slot, gameTime, "计算异常");
            if (slot >= 0) slotReason[slot] = "合成计算异常（" + describe(key) + "）";
            machine.setBlockedText(composeBlockedText());
            return;
        }
        if (plan == null || plan.simulation() || !plan.missingItems().isEmpty()) {
            String missing = describeMissing(plan);
            fail(slot, gameTime, "方案不完整");
            machine.setSlotStatus(slot, RequesterMachine.STATUS_IMPOSSIBLE);
            if (slot >= 0) slotReason[slot] = "无法合成（缺 " + missing + "）";
            machine.setBlockedText(composeBlockedText());
            return;
        }

        IGrid grid = machine.getMainNode().getGrid();
        if (grid == null) {
            fail(slot, gameTime, "网络断开");
            if (slot >= 0) slotReason[slot] = describe(key) + "：下单时网络断开";
            machine.setBlockedText(composeBlockedText());
            return;
        }
        // ★ 在提交前先把“单太大”的情况处理掉：AE2 的 bytes 与合成量大致成正比，
        //   直接 submit 只会得到 NO_SUITABLE_CPU_FOUND（tooSmall），而旧代码
        //   冷却 5 秒后又用同样的大单重试，永远失败。这里改成主动缩单重算。
        long biggestCpu = largestAvailableCpuStorage(grid);
        if (biggestCpu > 0L && plan.bytes() > biggestCpu) {
            long requested = plan.finalOutput().amount();
            long shrunk = Math.max(MIN_AUTO_SHRINK, requested * biggestCpu / plan.bytes());
            if (shrunk < requested) {
                amountCap[slot] = shrunk;
                GTLModular.LOGGER.info("[ME库存补充仓] 单太大（{} 字节 > CPU 最大 {} 字节），"
                                + "自动缩单：{} ×{} → ×{}",
                        plan.bytes(), biggestCpu, describe(key), requested, shrunk);
                startCalculation(grid, slot, key, shrunk, gameTime);
                return;
            }
        }

        // ★ 产物直接回网络（阶段 0 已验证：requester == null ⇒ standalone 链接 ⇒ 产物进 ME 存储）。
        ICraftingSubmitResult result = grid.getCraftingService()
                .submitJob(plan, null, null, true, machine.getActionSource());
        if (result.successful()) {
            machine.setSlotStatus(slot, RequesterMachine.STATUS_IN_FLIGHT);
            lastInFlightSeen[slot] = gameTime;
            // 下单成功：解除缩单限制，下轮按批量正常补
            if (slot >= 0) amountCap[slot] = 0L;
            // 不清 blockedText：那是「所有槽位」的聚合提示，由下一轮轮询统一重算；
            // 在这里清会把别的槽位的失败信息也一并抹掉。
            GTLModular.LOGGER.info("[ME库存补充仓] 已下单：{} ×{}", describe(key), plan.finalOutput().amount());
        } else {
            String reason = describeSubmitError(result);
            // 如果确实是“装不下”，再缩一档下轮重试（防止 biggestCpu 采样不准）。
            if (result.errorCode() == appeng.api.networking.crafting.CraftingSubmitErrorCode
                    .NO_SUITABLE_CPU_FOUND && slot >= 0) {
                long requested = plan.finalOutput().amount();
                long shrunk = Math.max(MIN_AUTO_SHRINK, requested / 2L);
                if (shrunk < requested) {
                    amountCap[slot] = shrunk;
                    GTLModular.LOGGER.info("[ME库存补充仓] 提交仍失败（{}），缩单：{} ×{} → ×{}",
                            reason, describe(key), requested, shrunk);
                }
            }
            fail(slot, gameTime, "提交失败：" + reason);
            if (slot >= 0) {
                slotReason[slot] = describe(key) + " " + reason;
            }
            machine.setBlockedText(composeBlockedText());
        }
    }

    /**
     * 网络里最空的可用合成 CPU 的剩余存储字节数（拿不到则返回 0）。
     *
     * <p>只看<b>空闲</b>的 CPU：忙碌的 CPU 即使容量大也接不了新单。
     */
    private static long largestAvailableCpuStorage(IGrid grid) {
        long best = 0L;
        for (var cpu : grid.getCraftingService().getCpus()) {
            if (cpu.isBusy()) continue;
            best = Math.max(best, cpu.getAvailableStorage());
        }
        return best;
    }

    /**
     * 把 AE2 的提交错误码翻成“玩家能照着做”的中文。
     *
     * <p>{@code NO_SUITABLE_CPU_FOUND} 尤其容易误读：它不是“没有 CPU”，而是
     * 四种原因全部被排除（离线 / 忙 / 容量太小 / 选择模式不匹配）。
     * AE2 源码 {@code CraftingService.findSuitableCraftingCPU} 的判断顺序：
     * <pre>
     *   !cpu.isActive()                     → offline
     *   cpu.isBusy()                        → busy
     *   getAvailableStorage() &lt; plan.bytes() → tooSmall
     *   !cpu.canBeAutoSelectedFor(src)       → excluded（玩家/机器模式不匹配）
     * </pre>
     */
    private static String describeSubmitError(ICraftingSubmitResult result) {
        var code = result.errorCode();
        if (code == null) return "下单失败";
        return switch (code) {
            case INCOMPLETE_PLAN -> "无法合成（配方方案不完整）";
            case NO_CPU_FOUND -> "网络里没有合成 CPU";
            case NO_SUITABLE_CPU_FOUND -> "没有可接单的 CPU" + describeUnsuitable(result);
            case CPU_BUSY -> "合成 CPU 全部忙碌";
            case CPU_OFFLINE -> "合成 CPU 离线（未供电/未成型）";
            case CPU_TOO_SMALL -> "合成 CPU 容量不足（本单需要的字节超了）";
            case MISSING_INGREDIENT -> "缺少原料";
        };
    }

    /** 把 {@code UnsuitableCpus} 计数翻成人话；拿不到明细时给通用建议。 */
    private static String describeUnsuitable(ICraftingSubmitResult result) {
        Object detail = result.errorDetail();
        if (!(detail instanceof appeng.api.networking.crafting.UnsuitableCpus cpus)) {
            return "（检查 CPU 是否供电、空闲、容量够，且自动选择模式不是“仅玩家”）";
        }
        StringBuilder sb = new StringBuilder("：");
        boolean any = false;
        if (cpus.offline() > 0) {
            sb.append(cpus.offline()).append(" 台离线");
            any = true;
        }
        if (cpus.busy() > 0) {
            if (any) sb.append("、");
            sb.append(cpus.busy()).append(" 台忙碌");
            any = true;
        }
        if (cpus.tooSmall() > 0) {
            if (any) sb.append("、");
            sb.append(cpus.tooSmall()).append(" 台容量不足");
            any = true;
        }
        if (cpus.excluded() > 0) {
            if (any) sb.append("、");
            sb.append(cpus.excluded()).append(" 台因选择模式被排除（改成“任意”或“仅机器”）");
            any = true;
        }
        return any ? sb.toString() : "（检查 CPU 供电与容量）";
    }

    private void fail(int slot, long gameTime, String reason) {
        if (slot >= 0 && slot < retryAfter.length) {
            retryAfter[slot] = gameTime + FAILURE_COOLDOWN;
            if (machine.slot(slot).key() != null) {
                machine.setSlotStatus(slot, RequesterMachine.STATUS_BLOCKED);
            }
        }
        GTLModular.LOGGER.info("[ME库存补充仓] 槽位 {} 失败：{}", slot, reason);
    }

    // ------------------------------------------------------------------
    // 文本
    // ------------------------------------------------------------------

    private static String describe(AEKey key) {
        return key == null ? "?" : key.getDisplayName().getString();
    }

    private static String describeMissing(ICraftingPlan plan) {
        if (plan == null) return "计算未返回方案";
        var missing = plan.missingItems();
        if (missing.isEmpty()) return "方案被判定为模拟（材料不足）";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (var entry : missing) {
            if (shown >= 3) {
                sb.append(" 等 ").append(missing.size()).append(" 项");
                break;
            }
            if (shown > 0) sb.append("、");
            sb.append(entry.getKey().getDisplayName().getString())
              .append(" ×").append(entry.getLongValue());
            shown++;
        }
        return sb.toString();
    }
}
