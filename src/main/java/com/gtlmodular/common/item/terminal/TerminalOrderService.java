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
        Map<AEKey, Long> shortages = MATERIALS.shortages(plan, player, ae);
        if (shortages.isEmpty()) return false;
        if (shortages.size() > 128) {
            player.sendSystemMessage(Component.literal(
                    "§e[哦鲸鲸终端] 缺口种类过多（" + shortages.size() + " 种），订单已跳过；请分批施工"));
            return false;
        }
        ICraftingService service = ae.grid().getCraftingService();
        ICraftingCPU orderCpu = selectOrderCpu(service);
        if (orderCpu == null) {
            player.sendSystemMessage(Component.literal(
                    "§e[哦鲸鲸终端] 没有可用的合成 CPU，已回退普通下单"));
            return false;
        }
        List<GenericStack> inputs = new ArrayList<>(shortages.size());
        long totalAmount = 0L;
        for (Map.Entry<AEKey, Long> entry : shortages.entrySet()) {
            inputs.add(new GenericStack(entry.getKey(), entry.getValue()));
            totalAmount = Math.min(Long.MAX_VALUE, totalAmount + Math.max(0L, entry.getValue()));
        }
        GTLModular.LOGGER.info("终端订单样板输入: {} 种材料，共 {} 份: {}", shortages.size(), totalAmount, shortages);
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
            if (previous != null && previous.holder != holder) {
                previous.holder.gtlmodular$setTempPattern(null);
            }
            player.sendSystemMessage(Component.literal(
                    "§b[哦鲸鲸终端] 已创建单个 AE 订单（" + shortages.size() + " 种材料，共 "
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
                session.player.sendSystemMessage(Component.literal(
                        "§c[哦鲸鲸终端] 订单样板未被 AE 识别：没有找到订单标记的合成路径"));
                continue;
            }
            if (plan.simulation() || !plan.missingItems().isEmpty()) {
                Component missing = describeMissing(plan);
                GTLModular.LOGGER.info("终端订单计算不完整，缺少: {}", missing.getString());
                session.player.sendSystemMessage(Component.literal(
                        "§c[哦鲸鲸终端] 订单材料无法通过 AE 合成：§f").append(missing));
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
            }
        }
    }

    /** 标准 AE CPU 优先；没有时使用 ECO CPU（由 ECO 专用 mixin 收尾）。 */
    private static ICraftingCPU selectOrderCpu(ICraftingService service) {
        ICraftingCPU eco = null;
        for (ICraftingCPU cpu : service.getCpus()) {
            String name = cpu.getClass().getName();
            if (name.startsWith("appeng.me.cluster.implementations.CraftingCPUCluster")) {
                return cpu;
            }
            if (name.contains("neoecoae") && name.contains("ECOCraftingCPU")) {
                eco = cpu;
            }
        }
        return eco;
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
