package com.gtlmodular.common.item.terminal;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularItems;
import com.gtlmodular.common.item.ShanhaiUltimateTerminalBehavior;
import com.gtlmodular.common.item.ShanhaiUltimateTerminalConfig;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 替换模式的独立分批执行器：
 * 先扫描并缓存可替换位置，再由玩家第二次潜行右键启动，按 tick 分批替换。
 * 不混进普通施工/订单逻辑。
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TerminalReplaceService {

    private static final int BATCH = 64;
    private static final int PROGRESS_EVERY = 512;
    private static final Map<UUID, Job> PREPARED = new ConcurrentHashMap<>();
    private static final ShanhaiTerminalMaterialService MATERIALS = new ShanhaiTerminalMaterialService();
    private static final ShanhaiStructureExecutor EXECUTOR = new ShanhaiStructureExecutor(MATERIALS);

    private static final class Job {
        final ServerPlayer player;
        final ItemStack terminal;
        final ShanhaiStructurePlan plan;
        final List<ShanhaiStructurePlan.Entry> entries;
        final String signature;
        int index;
        int changed;
        int skipped;
        boolean started;
        boolean announcedProgress;

        Job(ServerPlayer player, ItemStack terminal, ShanhaiStructurePlan plan,
            List<ShanhaiStructurePlan.Entry> entries) {
            this.player = player;
            this.terminal = terminal.copy();
            this.plan = plan;
            this.entries = entries;
            this.signature = signature(plan, entries);
        }
    }

    private TerminalReplaceService() {}

    /** 首次潜行缓存；同一结构第二次潜行启动；结构变了则重新缓存。 */
    public static boolean prepareOrStart(ServerPlayer player, ItemStack terminal, ShanhaiStructurePlan plan) {
        UUID id = ShanhaiUltimateTerminalConfig.getTerminalUuid(terminal);
        List<ShanhaiStructurePlan.Entry> entries = replaceEntries(plan);
        if (entries.isEmpty()) return false;
        String signature = signature(plan, entries);
        Job existing = PREPARED.get(id);
        if (existing != null && existing.signature.equals(signature)) {
            if (existing.started) {
                player.sendSystemMessage(Component.literal(
                        "§e[哦鲸鲸终端] 替换正在进行：§f" + existing.index + " / "
                                + existing.entries.size()));
                return true;
            }
            existing.started = true;
            player.sendSystemMessage(Component.literal(
                    "§a[哦鲸鲸终端] 开始分批替换 §f" + existing.entries.size() + " §a处..."));
            return true;
        }
        PREPARED.put(id, new Job(player, terminal, plan, entries));
        player.sendSystemMessage(Component.literal(
                "§b[哦鲸鲸终端] 已扫描可替换方块 §f" + entries.size()
                        + " §b处；再次潜行右击开始分批替换"));
        return true;
    }

    private static String signature(ShanhaiStructurePlan plan,
                                    List<ShanhaiStructurePlan.Entry> entries) {
        StringBuilder value = new StringBuilder(plan.fingerprint());
        for (ShanhaiStructurePlan.Entry entry : entries) {
            value.append(';').append(entry.pos().asLong()).append(':')
                    .append(entry.currentState()).append(':')
                    .append(entry.current().getItem());
        }
        return value.toString();
    }

    /** 第一次潜行：只缓存可替换位置，不动方块。 */
    public static boolean prepare(ServerPlayer player, ItemStack terminal, ShanhaiStructurePlan plan) {
        UUID id = ShanhaiUltimateTerminalConfig.getTerminalUuid(terminal);
        List<ShanhaiStructurePlan.Entry> entries = replaceEntries(plan);
        if (entries.isEmpty()) return false;
        PREPARED.put(id, new Job(player, terminal, plan, entries));
        player.sendSystemMessage(Component.literal(
                "§b[哦鲸鲸终端] 已扫描可替换方块 §f" + entries.size()
                        + " §b处；再次潜行右击开始分批替换"));
        return true;
    }

    /** 第二次潜行：启动分批替换。 */
    public static boolean start(ServerPlayer player, ItemStack terminal, ShanhaiStructurePlan plan) {
        UUID id = ShanhaiUltimateTerminalConfig.getTerminalUuid(terminal);
        Job job = PREPARED.get(id);
        if (job == null || !job.plan.fingerprint().equals(plan.fingerprint())) return false;
        job.started = true;
        player.sendSystemMessage(Component.literal(
                "§a[哦鲸鲸终端] 开始分批替换 §f" + job.entries.size() + " §a处..."));
        return true;
    }

    public static void cancel(ItemStack terminal) {
        if (terminal == null || terminal.isEmpty()) return;
        PREPARED.remove(ShanhaiUltimateTerminalConfig.getTerminalUuid(terminal));
    }

    public static boolean hasPrepared(ItemStack terminal) {
        if (terminal == null || terminal.isEmpty()) return false;
        return PREPARED.containsKey(ShanhaiUltimateTerminalConfig.getTerminalUuid(terminal));
    }

    private static List<ShanhaiStructurePlan.Entry> replaceEntries(ShanhaiStructurePlan plan) {
        List<ShanhaiStructurePlan.Entry> entries = new ArrayList<>();
        for (ShanhaiStructurePlan.Entry entry : plan.entries()) {
            if (entry.requiresBuild()
                    && (entry.kind() == ShanhaiStructurePlan.Kind.REPLACE
                    || entry.kind() == ShanhaiStructurePlan.Kind.FORCE_REPLACE)) {
                entries.add(entry);
            }
        }
        return entries;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PREPARED.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        Iterator<Map.Entry<UUID, Job>> iterator = PREPARED.entrySet().iterator();
        while (iterator.hasNext()) {
            Job job = iterator.next().getValue();
            if (!job.started) continue;
            if (job.player.isRemoved()) {
                iterator.remove();
                continue;
            }
            if (!ShanhaiUltimateTerminalConfig.isReplaceMode(job.terminal)) {
                iterator.remove();
                continue;
            }
            var ae = ShanhaiUltimateTerminalConfig.isAeMode(job.terminal)
                    ? ShanhaiTerminalAeBinding.resolve(job.player, job.terminal) : null;
            ServerLevel level = server.getLevel(job.plan.target().dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            int end = Math.min(job.entries.size(), job.index + BATCH);
            if (job.index >= end) {
                finish(iterator, job);
                continue;
            }
            List<ShanhaiStructurePlan.Entry> batch = job.entries.subList(job.index, end);
            int changed;
            if (job.player.isCreative()) {
                // 创造模式直接 setBlock；旧方块按批聚合后一次性退回 AE/背包，避免逐块回收卡顿。
                changed = 0;
                List<ItemStack> oldStacks = new ArrayList<>();
                for (ShanhaiStructurePlan.Entry entry : batch) {
                    if (!level.hasChunkAt(entry.pos())) continue;
                    if (!entry.current().isEmpty()) oldStacks.add(entry.current().copy());
                    level.setBlock(entry.pos(), entry.desiredState(), 3);
                    changed++;
                }
                if (!oldStacks.isEmpty() && !MATERIALS.storeDismantled(job.player, ae, oldStacks)) {
                    job.player.sendSystemMessage(Component.literal(
                            "§c[哦鲸鲸终端] 旧方块回收失败：AE/背包没有空间（本批已替换）"));
                }
            } else {
                ShanhaiStructurePlan sub = new ShanhaiStructurePlan(
                        job.plan.target(), job.plan.machineId(), job.plan.mirrored(),
                        job.plan.repeatCount(), batch);
                ShanhaiStructureExecutor.Result result = EXECUTOR.execute(job.player, sub, ae);
                if (!result.success() && result.changed() == 0) {
                    job.player.sendSystemMessage(Component.literal(
                            "§c[哦鲸鲸终端] 分批替换停止：" + result.message()
                                    + "（已完成 " + job.changed + " 处）"));
                    iterator.remove();
                    continue;
                }
                changed = result.changed();
            }
            job.changed += changed;
            job.index = end;
            if (job.index % PROGRESS_EVERY < BATCH) {
                job.player.sendSystemMessage(Component.literal(
                        "§b[哦鲸鲸终端] 替换进度 §f" + job.index + " / " + job.entries.size()
                                + " §b（已改 " + job.changed + " 处）"));
            }
            if (job.index >= job.entries.size()) {
                finish(iterator, job);
            }
        }
    }

    private static void finish(Iterator<Map.Entry<UUID, Job>> iterator, Job job) {
        iterator.remove();
        job.player.sendSystemMessage(Component.literal(
                "§a[哦鲸鲸终端] 分批替换完成：§f" + job.changed + " §a处，可替换总数 §f"
                        + job.entries.size()));
    }
}
