package com.gtlmodular.common.item.terminal;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularItems;
import com.gtlmodular.common.item.ShanhaiUltimateTerminalBehavior;
import com.gtlmodular.common.item.ShanhaiUltimateTerminalConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「结构订单」完成后的自动施工信号。
 *
 * <p>下单时登记玩家/控制器/计划指纹；服务端每秒重扫一次，材料齐备后直接用该玩家身份执行
 * 施工，不需要玩家站在控制器旁边。等价于“订单完成 → 发一个玩家信号 → 扫描 → 施工”。
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TerminalAutoBuildService {

    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final Map<UUID, Request> REQUESTS = new ConcurrentHashMap<>();
    private static int tickCounter;

    private record Request(UUID playerId, UUID terminalId, GlobalPos target,
                           String fingerprint, ShanhaiStructurePlan plan) {}

    private static final ShanhaiTerminalMaterialService MATERIALS = new ShanhaiTerminalMaterialService();
    private static final ShanhaiStructureExecutor EXECUTOR = new ShanhaiStructureExecutor(MATERIALS);

    private TerminalAutoBuildService() {}

    /** 订单/计划准备时登记；材料齐备后自动施工。 */
    public static void register(ServerPlayer player, ItemStack terminal, ShanhaiStructurePlan plan) {
        UUID terminalId = ShanhaiUltimateTerminalConfig.getTerminalUuid(terminal);
        REQUESTS.put(terminalId, new Request(player.getUUID(), terminalId,
                plan.target(), plan.fingerprint(), plan));
    }

    public static void cancel(ItemStack terminal) {
        if (terminal == null || terminal.isEmpty()) return;
        REQUESTS.remove(ShanhaiUltimateTerminalConfig.getTerminalUuid(terminal));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || REQUESTS.isEmpty()) return;
        if (++tickCounter < CHECK_INTERVAL_TICKS) return;
        tickCounter = 0;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        Iterator<Map.Entry<UUID, Request>> iterator = REQUESTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Request request = iterator.next().getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(request.playerId());
            if (player == null) continue;
            ItemStack terminal = findTerminal(player, request.terminalId());
            if (terminal.isEmpty()) {
                iterator.remove();
                continue;
            }
            if (!ShanhaiUltimateTerminalConfig.isAeMode(terminal)
                    || !ShanhaiUltimateTerminalConfig.isOrderMode(terminal)) {
                iterator.remove();
                continue;
            }
            var ae = ShanhaiTerminalAeBinding.resolve(player, terminal);
            if (ae == null) continue;
            ServerLevel level = server.getLevel(request.target().dimension());
            if (level == null) continue;
            BlockPos pos = request.target().pos();
            // 让目标区块处于加载状态，自动施工才能改动方块。
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) level.getChunk(cx + dx, cz + dz);
            }
            // 订单登记时已经扫描过；这里直接使用保存的计划，不再重扫。
            ShanhaiStructurePlan plan = request.plan();
            var preflight = MATERIALS.preflight(plan, player, ae);
            if (!preflight.success()) continue;
            iterator.remove();
            ShanhaiStructureExecutor.Result result = player.isCreative()
                    ? EXECUTOR.executeCreative(player, plan, ae)
                    : EXECUTOR.execute(player, plan, ae);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    (result.success() ? "§a" : "§c") + "[哦鲸鲸终端·订单完成] " + result.message()
                            + "，变更 " + result.changed() + " 处"));
            ShanhaiUltimateTerminalBehavior.sendBuildReport(player, plan, result);
        }
    }

    private static ItemStack findTerminal(ServerPlayer player, UUID terminalId) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(GTLModularItems.WHALE_TERMINAL.get())
                    && ShanhaiUltimateTerminalConfig.getTerminalUuid(stack).equals(terminalId)) {
                return stack;
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(GTLModularItems.WHALE_TERMINAL.get())
                && ShanhaiUltimateTerminalConfig.getTerminalUuid(offhand).equals(terminalId)) {
            return offhand;
        }
        return ItemStack.EMPTY;
    }
}
