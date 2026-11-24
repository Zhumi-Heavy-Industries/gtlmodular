package com.gtlmodular.common.item.terminal;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.item.ShanhaiUltimateTerminalConfig;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * GTO 式「一个订单」：把全部缺口编码成一个 AE 处理样板，输出一个隐藏订单标记，
 * 再通过临时样板让 AE 只提交一个 job。与原有多缺口逐个下单逻辑完全隔离。
 *
 * <p>临时样板是内存态：只挂在 {@code CraftingService} 的 {@code gtlmodular$tempPattern}
 * 字段上，不注册成 AE 样板、不进样板表，因此不会和 gtlcore 的结构样板（真实 AE 样板
 * + barrier 输出）冲突。
 *
 * <p><b>gtlcore 兼容</b>：gtlcore 默认合成引擎是 GRAPH，GRAPH 的 CPU 只对
 * barrier + display 的占位产物收尾。检测到 GRAPH 时，临时样板的隐藏产物改用
 * barrier + 唯一 display 名（{@link TerminalOrderMarker}），让 GRAPH/LEGACY CPU
 * 都能把订单收尾；自定义 NBT 保证只认我们自己的订单。没有 gtlcore 或 LEGACY 时，
 * 仍然使用鲸鱼终端物品 + NBT。
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TerminalOrderService {

    private static final ShanhaiTerminalMaterialService MATERIALS = new ShanhaiTerminalMaterialService();
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    /** 计算/作业期间占用一张 AE 网络，避免两个终端同时抢同一个 CraftingService 的临时样板字段。 */
    private static final Map<ICraftingService, UUID> GRID_OWNERS =
            Collections.synchronizedMap(new WeakHashMap<ICraftingService, UUID>());
    /** 提交后临时样板要留到作业结束（GRAPH 的 replan 仍会查样板）；按 link/CPU/超时释放。 */
    private static final Map<UUID, HeldPattern> HELD = new ConcurrentHashMap<>();
    private static final long MAX_HOLD_TICKS = 12000L;

    private static final class Session {
        final ServerPlayer player;
        final ItemStack terminal;
        final ShanhaiStructurePlan plan;
        final ShanhaiTerminalAeBinding.Context ae;
        final ICraftingService service;
        final ICraftingCPU cpu;
        final TerminalTempPatternHolder holder;
        final Future<ICraftingPlan> future;
        final IPatternDetails pattern;
        final boolean graphToken;
        final long timeoutTicks;
        int ticks;

        Session(ServerPlayer player, ItemStack terminal, ShanhaiStructurePlan plan,
                ShanhaiTerminalAeBinding.Context ae, ICraftingService service, ICraftingCPU cpu,
                TerminalTempPatternHolder holder, Future<ICraftingPlan> future,
                IPatternDetails pattern, boolean graphToken, long timeoutTicks) {
            this.player = player;
            this.terminal = terminal.copy();
            this.plan = plan;
            this.ae = ae;
            this.service = service;
            this.cpu = cpu;
            this.holder = holder;
            this.future = future;
            this.pattern = pattern;
            this.graphToken = graphToken;
            this.timeoutTicks = timeoutTicks;
        }
    }

    /** 已提交、但临时样板还要留在 CraftingService 上等作业结束（或超时）。 */
    private static final class HeldPattern {
        final ICraftingService service;
        final TerminalTempPatternHolder holder;
        final IPatternDetails pattern;
        final ICraftingLink link;
        final ICraftingCPU cpu;
        final String fingerprint;
        int ticks;

        HeldPattern(ICraftingService service, TerminalTempPatternHolder holder,
                IPatternDetails pattern, ICraftingLink link, ICraftingCPU cpu, String fingerprint) {
            this.service = service;
            this.holder = holder;
            this.pattern = pattern;
            this.link = link;
            this.cpu = cpu;
            this.fingerprint = fingerprint;
        }
    }

    private TerminalOrderService() {}

    public static boolean start(ServerPlayer player, ItemStack terminal,
                                ShanhaiStructurePlan plan, ShanhaiTerminalAeBinding.Context ae) {
        if (player == null || terminal == null || terminal.isEmpty() || plan == null || ae == null) return false;
        UUID terminalId = ShanhaiUltimateTerminalConfig.getTerminalUuid(terminal);
        Session existing = SESSIONS.get(terminalId);
        if (existing != null && existing.plan.fingerprint().equals(plan.fingerprint())) {
            player.sendSystemMessage(Component.literal(
                    "§e[哦鲸鲸终端] 同一结构订单已在计算/提交中，不重复创建"));
            return true;
        }
        // 同一终端的新订单：先撤掉上一张已提交但还没释放的临时样板，避免叠在同一个
        // CraftingService 上；旧会话留到真正要创建新样板时再清，避免新订单提前失败时
        // 把旧订单也弄丢。
        HeldPattern held = HELD.get(terminalId);
        if (held != null && held.fingerprint.equals(plan.fingerprint())) {
            player.sendSystemMessage(Component.literal(
                    "§e[哦鲸鲸终端] 该结构订单已提交，等待 AE 合成完成"));
            return true;
        }
        releaseHeld(terminalId);
        TerminalMaterialRequest request = TerminalMaterialRequest.create(player, plan, ae);
        if (request.isEmpty()) return false;
        if (request.kindCount() > 128) {
            player.sendSystemMessage(Component.literal(
                    "§e[哦鲸鲸终端] 缺口种类过多（" + request.kindCount() + " 种），订单已跳过；请分批施工"));
            return false;
        }
        ICraftingService service = ae.grid().getCraftingService();
        UUID gridOwner = GRID_OWNERS.get(service);
        if (gridOwner != null && !gridOwner.equals(terminalId)) {
            player.sendSystemMessage(Component.literal(
                    "§e[哦鲸鲸终端] 该 AE 网络已有临时结构订单，已回退逐材料下单"));
            return false;
        }
        ICraftingCPU orderCpu = selectOrderCpu(service);
        if (orderCpu == null) {
            player.sendSystemMessage(Component.literal(
                    "§e[哦鲸鲸终端] 没有可用的合成 CPU，已回退普通下单"));
            return false;
        }
        if (!isDirectlySubmittable(orderCpu)) {
            // 网络里只有一个“空闲占位 CPU”（ECO 网络代表会返回一个没有任务的 fake CPU），
            // 此时指定它会直接 CPU_OFFLINE/NO_SUITABLE，交给 AE 自己选更稳。
            orderCpu = null;
        }
        List<GenericStack> inputs = request.inputs();
        long totalAmount = request.totalAmount();
        GTLModular.LOGGER.info("终端订单样板输入: {} 种材料，共 {} 份: {}",
                request.kindCount(), totalAmount, request.shortages());
        try {
            var storage = ae.grid().getStorageService().getInventory().getAvailableStacks();
            for (GenericStack input : inputs) {
                long available = storage.get(input.what());
                GTLModular.LOGGER.info("终端订单库存检查: {} 需要 {}，AE 网络库存 {}",
                        input.what(), input.amount(), available);
            }
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("终端订单库存检查失败", t);
        }

        // gtlcore 默认 GRAPH：它的 CPU 只对 barrier + display 的假产物收尾，所以临时
        // 样板的隐藏产物要用 barrier 形状；LEGACY/没有 gtlcore 时仍用鲸鱼终端物品。
        boolean graphToken = GraphCompat.useGraph();
        ItemStack marker = TerminalOrderMarker.create(plan.target(), plan.fingerprint(), graphToken);
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                inputs.toArray(GenericStack[]::new),
                new GenericStack[]{GenericStack.fromItemStack(marker)});
        IPatternDetails details = PatternDetailsHelper.decodePattern(encoded, player.level());
        if (details == null) {
            player.sendSystemMessage(Component.literal(
                    "§c[哦鲸鲸终端] 订单样板无法解码，已回退逐材料下单"));
            return false;
        }

        if (!(service instanceof TerminalTempPatternHolder holder)) return false;
        AEItemKey markerKey = AEItemKey.of(marker);
        if (markerKey == null) return false;

        // 到这一步才真正替换旧会话：旧 future 取消、旧样板清掉。
        Session previous = SESSIONS.remove(terminalId);
        if (previous != null) {
            cancelFuture(previous.future);
            clearPattern(previous.holder, previous.pattern);
            if (previous.service != service) releaseGrid(previous.service, terminalId);
        }
        clearPattern(holder, holder.gtlmodular$getTempPattern());
        holder.gtlmodular$setTempPattern(details);
        GRID_OWNERS.put(service, terminalId);
        try {
            Future<ICraftingPlan> future = service.beginCraftingCalculation(
                    player.level(), ae::source, markerKey, 1, CalculationStrategy.REPORT_MISSING_ITEMS);
            Session session = new Session(player, terminal, plan, ae, service, orderCpu, holder, future,
                    details, graphToken, calcTimeout(request.kindCount()));
            SESSIONS.put(terminalId, session);
            player.sendSystemMessage(Component.literal(
                    "§b[哦鲸鲸终端] 已创建单个 AE 订单（" + request.kindCount() + " 种材料，共 "
                            + totalAmount + " 份），正在计算..."));
            return true;
        } catch (Throwable t) {
            clearPattern(holder, details);
            releaseGrid(service, terminalId);
            player.sendSystemMessage(Component.literal(
                    "§c[哦鲸鲸终端] 订单创建失败: " + t.getMessage()));
            return false;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!SESSIONS.isEmpty()) {
            for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
                Session session = entry.getValue();
                UUID id = entry.getKey();
                if (session.player.isRemoved()) {
                    cleanup(id, session);
                    continue;
                }
                session.ticks++;
                if (session.ticks > session.timeoutTicks) {
                    session.player.sendSystemMessage(Component.literal(
                            "§c[哦鲸鲸终端] 订单计算超时"));
                    cleanup(id, session);
                    fallback(session, "订单计算超时");
                    continue;
                }
                if (!session.future.isDone()) continue;
                ICraftingPlan plan;
                try {
                    plan = session.future.get();
                } catch (Exception e) {
                    plan = null;
                }
                if (plan == null) {
                    cleanup(id, session);
                    fallback(session, "订单样板未被 AE 识别");
                    continue;
                }
                if (GraphCompat.isGraphPlan(plan) && !session.graphToken) {
                    // 引擎在计算期间切到了 GRAPH，而隐藏产物是鲸鱼终端物品；GRAPH CPU
                    // 不会收尾它。不要提交，回退逐材料。
                    cleanup(id, session);
                    fallback(session, "合成引擎与临时样板不匹配");
                    continue;
                }
                if (plan.simulation() || !plan.missingItems().isEmpty()) {
                    Component missing = describeMissing(plan);
                    GTLModular.LOGGER.info("终端订单计算不完整，缺少: {}", missing.getString());
                    session.player.sendSystemMessage(Component.literal(
                            "§c[哦鲸鲸终端] 订单材料无法通过 AE 合成：§f").append(missing));
                    cleanup(id, session);
                    fallback(session, "单订单材料不完整");
                    continue;
                }
                GTLModular.LOGGER.info("终端订单提交前: simulation={}, missing={}, bytes={}",
                        plan.simulation(), plan.missingItems().size(), plan.bytes());
                ICraftingSubmitResult result;
                try {
                    result = session.service.submitJob(
                            plan, null, session.cpu, true, session.ae.source());
                } catch (Throwable t) {
                    cleanup(id, session);
                    fallback(session, "单订单提交异常: " + t.getMessage());
                    continue;
                }
                if (result.successful()) {
                    // 不立刻清样板：GRAPH 的作业后续 replan 还会再查 CraftingService。
                    // 交给 HELD 按 link/CPU/超时释放。
                    SESSIONS.remove(id, session);
                    HELD.put(id, new HeldPattern(session.service, session.holder, session.pattern,
                            result.link(), session.cpu, session.plan.fingerprint()));
                    session.player.sendSystemMessage(Component.literal(
                            "§a[哦鲸鲸终端] 单个 AE 订单已提交；完成后自动施工"));
                } else {
                    cleanup(id, session);
                    fallback(session, "单订单提交失败: " + result.errorCode());
                }
            }
        }
        tickHeld();
    }

    private static void tickHeld() {
        if (HELD.isEmpty()) return;
        for (Map.Entry<UUID, HeldPattern> entry : HELD.entrySet()) {
            HeldPattern held = entry.getValue();
            held.ticks++;
            boolean finished = held.link != null
                    ? (held.link.isDone() || held.link.isCanceled())
                    : (held.cpu != null && !held.cpu.isBusy());
            // 少数 AE/CPU 组合下 link 可能一直不回报完成；提交 10 秒后 CPU 已空闲也视为
            // 作业结束，避免临时样板长期占着这张网络。
            if (!finished && held.ticks > 200 && held.cpu != null && !held.cpu.isBusy()) {
                finished = true;
            }
            if (!finished && held.ticks <= MAX_HOLD_TICKS) continue;
            HELD.remove(entry.getKey(), held);
            clearPattern(held.holder, held.pattern);
            releaseGrid(held.service, entry.getKey());
        }
    }

    private static void releaseHeld(UUID terminalId) {
        HeldPattern held = HELD.remove(terminalId);
        if (held == null) return;
        clearPattern(held.holder, held.pattern);
        releaseGrid(held.service, terminalId);
    }

    private static void cleanup(UUID id, Session session) {
        SESSIONS.remove(id, session);
        cancelFuture(session.future);
        clearPattern(session.holder, session.pattern);
        releaseGrid(session.service, id);
    }

    private static void clearPattern(TerminalTempPatternHolder holder, IPatternDetails pattern) {
        if (holder == null || pattern == null) return;
        if (holder.gtlmodular$getTempPattern() == pattern) {
            holder.gtlmodular$setTempPattern(null);
        }
    }

    private static void releaseGrid(ICraftingService service, UUID terminalId) {
        if (service == null) return;
        UUID owner = GRID_OWNERS.get(service);
        if (terminalId.equals(owner)) GRID_OWNERS.remove(service);
    }

    private static void cancelFuture(Future<?> future) {
        if (future == null) return;
        try {
            future.cancel(true);
        } catch (Throwable ignored) {
            // AE 计算线程取消失败不影响订单回退
        }
    }

    private static long calcTimeout(int kindCount) {
        return Math.min(6000L, 600L + 20L * Math.max(1, kindCount));
    }

    private static void fallback(Session session, String reason) {
        boolean started = ShanhaiTerminalCraftingManager.begin(
                session.player, session.terminal, session.plan, session.ae, MATERIALS);
        if (started) {
            com.gtlmodular.common.item.terminal.TerminalAutoBuildService.allowPolling(
                    session.terminal);
        }
        session.player.sendSystemMessage(Component.literal(
                started
                        ? "§e[哦鲸鲸终端] " + reason + "，已回退到逐材料下单"
                        : "§c[哦鲸鲸终端] " + reason + "，且逐材料下单也未启动"));
    }

    /**
     * 标准 AE CPU 优先；没有时退到 ECO CPU。
     *
     * <p>注意：AE2 的 getCpus() 只含 CraftingCPUCluster，而 ECO 通过 mixin 往同一个返回里
     * 追加 ECOCraftingCPU（包括网络代表专用的空闲 fake CPU），所以这里要按类型判断，
     * 而不是只认 CraftingCPUCluster 类名；同时必须跳过空闲占位 CPU，否则会选中 fake CPU。
     */
    private static ICraftingCPU selectOrderCpu(ICraftingService service) {
        ICraftingCPU ae = null;
        ICraftingCPU eco = null;
        for (ICraftingCPU cpu : service.getCpus()) {
            String name = cpu.getClass().getName();
            if (name.startsWith("appeng.me.cluster.implementations.CraftingCPUCluster")) {
                // 标准 CPU：空闲的优先，忙的作为最后选择（submitJob 会报 CPU_BUSY）。
                if (ae == null || (ae.isBusy() && !cpu.isBusy())) ae = cpu;
            } else if (name.startsWith("cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU")) {
                // ECO CPU：先排除 fake/占位 CPU，再按空闲优先挑。
                if (!isDirectlySubmittable(cpu)) continue;
                if (eco == null || (eco.isBusy() && !cpu.isBusy())) eco = cpu;
            }
        }
        return ae != null ? ae : eco;
    }

    /** CPU 是否是一个真实可提交的 CPU（非 fake 占位、在线、能装下普通计划）。 */
    private static boolean isDirectlySubmittable(ICraftingCPU cpu) {
        if (cpu == null) return false;
        String name = cpu.getClass().getName();
        if (name.startsWith("appeng.me.cluster.implementations.CraftingCPUCluster")) return true;
        if (!name.startsWith("cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU")) return false;
        // ECO 的 fake CPU 与网络已满载时用同一个“没在跑任务”的空壳；两者都不该被指定。
        // 真 CPU 在空闲时也会 isBusy()==false，所以改用底层 cluster 是否登记了它来判断。
        try {
            Object cluster = cpu.getClass().getMethod("getCluster").invoke(cpu);
            if (cluster == null) return false;
            Object fake = cluster.getClass().getMethod("getFakeCPU").invoke(cluster);
            if (fake == cpu) return false;
            Object active = cluster.getClass().getMethod("getActiveCPUs").invoke(cluster);
            return active instanceof java.util.Collection<?> list && list.contains(cpu);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static Component describeMissing(ICraftingPlan plan) {
        var missing = plan.missingItems();
        if (missing.isEmpty()) {
            return Component.literal("AE 没有给出具体缺失项（可能是样板未注册或计算被中断）");
        }
        MutableComponent text = Component.empty();
        int shown = 0;
        for (AEKey key : missing.keySet()) {
            if (shown >= 8) {
                text.append(Component.literal(" 等"));
                break;
            }
            if (shown > 0) text.append(Component.literal("、"));
            String name = key.getDisplayName().getString();
            MutableComponent item = Component.literal(name).withStyle(style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, name))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("点击复制物品名称"))));
            text.append(item).append(Component.literal(" ×" + missing.get(key))
                    .withStyle(ChatFormatting.GRAY));
            shown++;
        }
        return text;
    }

    public static void cancel(ItemStack terminal) {
        if (terminal == null || terminal.isEmpty()) return;
        UUID id = ShanhaiUltimateTerminalConfig.getTerminalUuid(terminal);
        Session session = SESSIONS.remove(id);
        if (session != null) {
            cancelFuture(session.future);
            clearPattern(session.holder, session.pattern);
            releaseGrid(session.service, id);
        }
        releaseHeld(id);
    }

    /**
     * gtlcore 兼容检测；全部走反射，gtlcore 不在时返回 false。
     *
     * <p>gtlcore 默认引擎是 GRAPH（{@code CraftingEngineRouter.useGraph()}）；GRAPH 的
     * 样板目录通过 {@code CraftingService.getCraftingFor} 读取样板，所以我们的内存临时
     * 样板仍然能参与规划，只是隐藏产物必须是 barrier + display 才能被 GRAPH CPU 收尾。
     */
    private static final class GraphCompat {
        private static final Method USE_GRAPH = find(
                "org.gtlcore.gtlcore.integration.ae2.graph.CraftingEngineRouter", "useGraph");
        private static final Method IS_GRAPH_PLAN = find(
                "org.gtlcore.gtlcore.integration.ae2.graph.GraphCpuController", "isGraphPlan",
                ICraftingPlan.class);

        private GraphCompat() {}

        private static Method find(String owner, String name, Class<?>... args) {
            try {
                Class<?> type = Class.forName(owner, false, TerminalOrderService.class.getClassLoader());
                return type.getMethod(name, args);
            } catch (Throwable ignored) {
                return null;
            }
        }

        static boolean useGraph() {
            if (USE_GRAPH == null) return false;
            try {
                return Boolean.TRUE.equals(USE_GRAPH.invoke(null));
            } catch (Throwable ignored) {
                return false;
            }
        }

        static boolean isGraphPlan(ICraftingPlan plan) {
            if (IS_GRAPH_PLAN == null || plan == null) return false;
            try {
                return Boolean.TRUE.equals(IS_GRAPH_PLAN.invoke(null, plan));
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}
