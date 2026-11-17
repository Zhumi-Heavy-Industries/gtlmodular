package com.gtlmodular.common.item.terminal;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.item.ShanhaiUltimateTerminalConfig;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingCPU;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * GTO 式「一个订单」：把全部缺口编码成一个 AE 处理样板，输出一个隐藏订单标记，
 * 再通过临时样板让 AE 只提交一个 job。与原有多缺口逐个下单逻辑完全隔离。
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TerminalOrderService {

    private static final ShanhaiTerminalMaterialService MATERIALS = new ShanhaiTerminalMaterialService();
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private static final class Session {
        final ServerPlayer player;
        final ItemStack terminal;
        final ShanhaiStructurePlan plan;
        final ShanhaiTerminalAeBinding.Context ae;
        final ICraftingService service;
        final ICraftingCPU cpu;
        final TerminalTempPatternHolder holder;
        final Future<ICraftingPlan> future;
        int ticks;

        Session(ServerPlayer player, ItemStack terminal, ShanhaiStructurePlan plan,
                ShanhaiTerminalAeBinding.Context ae, ICraftingService service, ICraftingCPU cpu,
                TerminalTempPatternHolder holder, Future<ICraftingPlan> future) {
            this.player = player;
            this.terminal = terminal.copy();
            this.plan = plan;
            this.ae = ae;
            this.service = service;
            this.cpu = cpu;
            this.holder = holder;
            this.future = future;
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
        TerminalMaterialRequest request = TerminalMaterialRequest.create(player, plan, ae);
        if (request.isEmpty()) return false;
        if (request.kindCount() > 128) {
            player.sendSystemMessage(Component.literal(
                    "§e[哦鲸鲸终端] 缺口种类过多（" + request.kindCount() + " 种），订单已跳过；请分批施工"));
            return false;
        }
        ICraftingService service = ae.grid().getCraftingService();
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
        ItemStack marker = TerminalOrderMarker.create(plan.target(), plan.fingerprint());
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                inputs.toArray(GenericStack[]::new),
                new GenericStack[]{GenericStack.fromItemStack(marker)});
        IPatternDetails details = PatternDetailsHelper.decodePattern(encoded, player.level());
        if (details == null) return false;

        if (!(service instanceof TerminalTempPatternHolder holder)) return false;
        AEItemKey markerKey = AEItemKey.of(marker);
        if (markerKey == null) return false;
        holder.gtlmodular$setTempPattern(details);
        try {
            Future<ICraftingPlan> future = service.beginCraftingCalculation(
                    player.level(), ae::source, markerKey, 1, CalculationStrategy.REPORT_MISSING_ITEMS);
            UUID id = ShanhaiUltimateTerminalConfig.getTerminalUuid(terminal);
            Session session = new Session(player, terminal, plan, ae, service, orderCpu, holder, future);
            Session previous = SESSIONS.put(id, session);
            if (previous != null) {
                previous.holder.gtlmodular$setTempPattern(null);
                SESSIONS.remove(id, previous);
            }
            player.sendSystemMessage(Component.literal(
                    "§b[哦鲸鲸终端] 已创建单个 AE 订单（" + request.kindCount() + " 种材料，共 "
                            + totalAmount + " 份），正在计算..."));
            return true;
        } catch (Throwable t) {
            holder.gtlmodular$setTempPattern(null);
            player.sendSystemMessage(Component.literal(
                    "§c[哦鲸鲸终端] 订单创建失败: " + t.getMessage()));
            return false;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) return;
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            Session session = entry.getValue();
            if (session.player.isRemoved()) {
                cleanup(entry.getKey(), session, false);
                continue;
            }
            session.ticks++;
            if (session.ticks > 600) {
                session.player.sendSystemMessage(Component.literal(
                        "§c[哦鲸鲸终端] 订单计算超时"));
                cleanup(entry.getKey(), session, false);
                continue;
            }
            if (!session.future.isDone()) continue;
            ICraftingPlan plan;
            try {
                plan = session.future.get();
            } catch (Exception e) {
                plan = null;
            }
            cleanup(entry.getKey(), session, false);
            if (plan == null) {
                fallback(session, "订单样板未被 AE 识别");
                continue;
            }
            if (plan.simulation() || !plan.missingItems().isEmpty()) {
                Component missing = describeMissing(plan);
                GTLModular.LOGGER.info("终端订单计算不完整，缺少: {}", missing.getString());
                session.player.sendSystemMessage(Component.literal(
                        "§c[哦鲸鲸终端] 订单材料无法通过 AE 合成：§f").append(missing));
                fallback(session, "单订单材料不完整");
                continue;
            }
            GTLModular.LOGGER.info("终端订单提交前: simulation={}, missing={}, bytes={}",
                    plan.simulation(), plan.missingItems().size(), plan.bytes());
            ICraftingSubmitResult result = session.service.submitJob(
                    plan, null, session.cpu, true, session.ae.source());
            if (result.successful()) {
                session.player.sendSystemMessage(Component.literal(
                        "§a[哦鲸鲸终端] 单个 AE 订单已提交；完成后自动施工"));
            } else {
                session.player.sendSystemMessage(Component.literal(
                        "§c[哦鲸鲸终端] 订单提交失败: " + result.errorCode()));
                fallback(session, "单订单提交失败");
            }
        }
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

    private static void cleanup(UUID id, Session session, boolean submitted) {
        SESSIONS.remove(id, session);
        session.holder.gtlmodular$setTempPattern(null);
    }

    public static void cancel(ItemStack terminal) {
        if (terminal == null || terminal.isEmpty()) return;
        UUID id = ShanhaiUltimateTerminalConfig.getTerminalUuid(terminal);
        Session session = SESSIONS.remove(id);
        if (session != null) session.holder.gtlmodular$setTempPattern(null);
    }
}
