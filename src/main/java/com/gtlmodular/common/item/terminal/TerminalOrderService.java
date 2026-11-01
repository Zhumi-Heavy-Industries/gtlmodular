package com.gtlmodular.common.item.terminal;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.item.ShanhaiUltimateTerminalConfig;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.network.chat.Component;
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
        final TerminalTempPatternHolder holder;
        final Future<ICraftingPlan> future;
        int ticks;

        Session(ServerPlayer player, ItemStack terminal, ShanhaiStructurePlan plan,
                ShanhaiTerminalAeBinding.Context ae, ICraftingService service,
                TerminalTempPatternHolder holder, Future<ICraftingPlan> future) {
            this.player = player;
            this.terminal = terminal.copy();
            this.plan = plan;
            this.ae = ae;
            this.service = service;
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

        ICraftingService service = ae.grid().getCraftingService();
        if (!(service instanceof TerminalTempPatternHolder holder)) return false;
        AEItemKey markerKey = AEItemKey.of(marker);
        if (markerKey == null) return false;
        holder.gtlmodular$setTempPattern(details);
        try {
            Future<ICraftingPlan> future = service.beginCraftingCalculation(
                    player.level(), ae::source, markerKey, 1, CalculationStrategy.REPORT_MISSING_ITEMS);
            UUID id = ShanhaiUltimateTerminalConfig.getTerminalUuid(terminal);
            Session session = new Session(player, terminal, plan, ae, service, holder, future);
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
            if (plan == null || plan.simulation()) {
                session.player.sendSystemMessage(Component.literal(
                        "§c[哦鲸鲸终端] 订单材料无法通过 AE 合成"));
                continue;
            }
            ICraftingSubmitResult result = session.service.submitJob(
                    plan, null, null, true, session.ae.source());
            if (result.successful()) {
                session.player.sendSystemMessage(Component.literal(
                        "§a[哦鲸鲸终端] 单个 AE 订单已提交；完成后自动施工"));
            } else {
                session.player.sendSystemMessage(Component.literal(
                        "§c[哦鲸鲸终端] 订单提交失败: " + result.errorCode()));
            }
        }
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
