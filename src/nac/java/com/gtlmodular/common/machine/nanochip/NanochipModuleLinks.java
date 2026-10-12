package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.machine.nanochip.registration.NanochipModules;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtlmodular.common.data.NanochipBlocks;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.structure.NanochipStructureDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;
import org.gtlcore.gtlcore.utils.datastructure.ModuleRenderInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

final class NanochipModuleLinks {

    private final NanochipAssemblyComplexMachine host;

    private static final int MODULE_REFRESH_INTERVAL = 20;

    /** 连接集合必须保持主机与模块双向一致；查询接口只读，不能顺带增删成员。 */
    private final Set<IModularMachineModule<NanochipAssemblyComplexMachine, ?>> modules = new LinkedHashSet<>();

    /** 匹配失败会清空上下文，因此保留最后一次成功的槽位供失效诊断使用。 */
    private final List<BlockPos> lastMatchedModuleSlots = new ArrayList<>();

    /** 运行期只读完成匹配后的快照，避免与异步匹配修改 matchContext 竞争。 */
    private volatile List<BlockPos> cachedModuleSlots = List.of();

    /** 读档可能恢复 isFormed 却没有运行期槽位；存档快照须逐格验证后才能采用。 */
    private volatile List<BlockPos> restoredModuleSlots = List.of();

    private volatile boolean restoredSlotsSettled;

    private boolean slotsUnavailableLogged;

    private long lastRescanTick = -1L;

    /** 结构回调与服务端 tick 可能跨线程，用原子交换避免消费时漏掉新请求。 */
    private final AtomicBoolean resyncRequested = new AtomicBoolean(false);

    /** 限流使用时间差，不能以 Long.MIN_VALUE 作哨兵而让减法溢出。 */
    private long lastForcedResyncTick = -10_000L;

    private long joinQuietUntilTick = -10_000L;

    private static final long INVALID_GRACE_TICKS = 100L;

    private long moduleDisconnectDeadline = -1L;

    private static final String NBT_MODULE_SLOTS = "GTLModularNacModuleSlots";

    NanochipModuleLinks(NanochipAssemblyComplexMachine host) {
        this.host = host;
    }

    String summarizeFSlotContent() {
        Level level = host.getLevel();
        List<BlockPos> slots = this.cachedModuleSlots;
        int controllers = 0;
        int casings = 0;
        int others = 0;
        if (level != null) {
            for (BlockPos pos : slots) {
                if (!level.isLoaded(pos)) {
                    others++;
                    continue;
                }
                Block block = level.getBlockState(pos).getBlock();
                if (isModuleControllerBlock(block)) {
                    controllers++;
                } else if (block == NanochipBlocks.MESH_INTERFACE_CASING.get()) {
                    casings++;
                } else {
                    others++;
                }
            }
        }
        return "F 位 " + slots.size() + " 格：模块控制器 " + controllers + " 台"
                + "（上限 " + host.getMaxModuleCount() + "）/ 网格接口机壳 " + casings + " 个 / 其它 " + others;
    }

    private static boolean isModuleControllerBlock(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null && NanochipModules.byId(id) != null;
    }

    void refreshModulesFromStructure() {
        Level level = host.getLevel();
        if (!(level instanceof ServerLevel) || !host.isFormed() || !host.isLiveHost()) {
            return;
        }
        // 使用完成匹配后的快照，避免与异步结构检查争读上下文。
        List<BlockPos> slots = cachedModuleSlots;
        if (slots.isEmpty()) {
            slots = adoptRestoredModuleSlots(level);
        }
        if (slots.isEmpty()) {
            // 没有可靠槽位证据时保持现有连接，避免读档暂缺上下文就误断全部模块。
            if (!slotsUnavailableLogged) {
                slotsUnavailableLogged = true;
                NanochipModContext.logger().info("[gtlmodular] NAC @{} 尚无「结构 F 位」成功匹配快照"
                                + "（正在等 GTCEu 给出第一次成功匹配；槽名 \"{}\"）⇒ 本次不改动已连接模块（当前 {} 个）",
                        host.getPos(), NanochipComplexPattern.MODULE_SLOT_NAME, modules.size());
            }
            return;
        }
        slotsUnavailableLogged = false;

        Set<IModularMachineModule<NanochipAssemblyComplexMachine, ?>> recomputed = new LinkedHashSet<>();
        for (BlockPos pos : slots) {
            MetaMachine machine = MetaMachine.getMachine(level, pos);
            if (machine == null || machine == host || !host.isValidModule(machine)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            IModularMachineModule<NanochipAssemblyComplexMachine, ?> module =
                    (IModularMachineModule<NanochipAssemblyComplexMachine, ?>) machine;
            recomputed.add(module);
            if (recomputed.size() >= host.getMaxModuleCount()) {
                break;
            }
        }
        int before = modules.size();

        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> module : recomputed) {
            if (modules.contains(module) && module.getHost() == host) {
                continue;
            }
            module.connectToHost(host);
            if (module instanceof NanochipModuleMachine nanochipModule) {
                nanochipModule.onHostLinked();
            }
            logModuleLinkChange("+", module, recomputed.size(), slots.size());
        }

        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> stale : new ArrayList<>(modules)) {
            if (recomputed.contains(stale)) {
                continue;
            }
            stale.removeFromHost(host);
            logModuleLinkChange("-", stale, recomputed.size(), slots.size());
        }

        if (modules.size() != recomputed.size() || !modules.containsAll(recomputed)) {
            NanochipModContext.logger().warn("[gtlmodular] NAC @{} 模块集合收敛异常：集合 {} 个 / 本次重算 {} 个（预期相等）",
                    host.getPos(), modules.size(), recomputed.size());
        }
        if (modules.size() != before) {
            NanochipModContext.logger().info("[gtlmodular] NAC @{} 已连接模块数变化：{} → {}"
                            + "（结构 F 位 {} 个，其中已成形模块 {} 个）",
                    host.getPos(), before, modules.size(), slots.size(), recomputed.size());
        }
    }

    /** 存档槽位只是候选证据；所有位置已加载且仍是合法 F 位方块后才能采用。 */
    private List<BlockPos> adoptRestoredModuleSlots(Level level) {
        if (this.restoredSlotsSettled) {
            return List.of();
        }
        List<BlockPos> restored = this.restoredModuleSlots;
        if (restored.isEmpty()) {
            this.restoredSlotsSettled = true;
            return List.of();
        }
        for (BlockPos pos : restored) {
            if (!level.isLoaded(pos)) {
                return List.of();
            }
        }
        for (int i = 0; i < restored.size(); i++) {
            BlockPos pos = restored.get(i);
            Block block = level.getBlockState(pos).getBlock();
            if (!isModuleControllerBlock(block) && block != NanochipBlocks.MESH_INTERFACE_CASING.get()) {
                this.restoredSlotsSettled = true;
                this.restoredModuleSlots = List.of();
                NanochipModContext.logger().info("[gtlmodular] NAC @{} 存档 F 位快照**未采用**：第 {} / {} 格 @{} 站的是 {}"
                                + "（既不是网格接口机壳、也不是模块控制器）⇒ 结构已不是存档时的样子，"
                                + "退回等 GTCEu 第一次成功匹配",
                        host.getPos(), i + 1, restored.size(), pos.toShortString(),
                        BuiltInRegistries.BLOCK.getKey(block));
                return List.of();
            }
        }
        this.restoredSlotsSettled = true;
        this.restoredModuleSlots = List.of();
        this.cachedModuleSlots = List.copyOf(restored);
        this.lastMatchedModuleSlots.clear();
        this.lastMatchedModuleSlots.addAll(restored);
        this.slotsUnavailableLogged = false;
        NanochipModContext.logger().info("[gtlmodular] NAC @{} 采用**存档 F 位快照** {} 格（逐格验证通过：全部已加载、"
                        + "且都是网格接口机壳或模块控制器）⇒ 模块连接不必等 GTCEu 重跑整图案匹配",
                host.getPos(), restored.size());
        return this.cachedModuleSlots;
    }

    private void logModuleLinkChange(String sign,
                                    IModularMachineModule<NanochipAssemblyComplexMachine, ?> module,
                                    int recomputedCount, int slotCount) {
        String kind = module instanceof NanochipModuleMachine nanochipModule
                ? nanochipModule.getModuleSpec().nameZh()
                : module.getClass().getSimpleName();
        MetaMachine machine = module instanceof MetaMachine meta ? meta : null;
        NanochipModContext.logger().info("[gtlmodular] NAC @{} 已连接模块 {}「{}」@{}"
                        + "（F 位上成形模块 {}/{}，本次操作后已连接 {} 个）",
                host.getPos(), sign, kind,
                machine == null ? "未知坐标" : machine.getPos().toShortString(),
                recomputedCount, slotCount, modules.size());
    }

    boolean isModuleConnected(IModularMachineModule<NanochipAssemblyComplexMachine, ?> module) {
        return module != null && modules.contains(module);
    }

    private boolean consumePendingResync(long now) {
        if (!this.resyncRequested.getAndSet(false)) {
            return false;
        }
        if (now < this.joinQuietUntilTick) {
            // 静默期仍有周期扫描兜底，丢弃催促不会漏掉连接。
            return false;
        }
        if (now - this.lastForcedResyncTick < NanochipAssemblyComplexMachine.RESYNC_COALESCE_TICKS) {
            // 未到间隔时保留请求，避免漏掉必须执行的重算。
            this.resyncRequested.set(true);
            return false;
        }
        this.lastForcedResyncTick = now;
        this.lastRescanTick = now;
        host.scanAndConnectModules();
        return true;
    }

    void requestCoalescedResync() {
        this.resyncRequested.set(true);
    }

    Set<IModularMachineModule<NanochipAssemblyComplexMachine, ?>> getModuleSet() {
        return modules;
    }

    BlockPos[] getModuleScanPositions() {
        List<NanochipComplexPattern.ModuleControllerSlot> slots = NanochipComplexPattern.moduleControllerOffsets();
        if (slots.isEmpty()) {
            return new BlockPos[0];
        }
        BlockPos origin = host.getPos();
        Direction front = host.getFrontFacing();
        Direction up = host.getUpwardsFacing();
        if (origin == null || front == null || up == null) {
            return new BlockPos[0];
        }
        BlockPos[] out = new BlockPos[slots.size()];
        boolean rotateByHost = NanochipAssemblyComplexMachine.NAC_MODULE_SCAN_ROTATE_BY_HOST;
        for (int i = 0; i < out.length; i++) {
            NanochipComplexPattern.ModuleControllerSlot slot = slots.get(i);
            if (!rotateByHost) {
                out[i] = origin.offset(slot.offset());
                continue;
            }
            ModuleRenderInfo info = new ModuleRenderInfo(slot.offset(), Direction.NORTH, Direction.UP,
                    slot.front(), Direction.UP, null);
            out[i] = origin.offset(info.calculateModuleOffset(front, up));
        }
        return out;
    }

    /** 只在成功匹配后采集 F 位，运行期读取不可变快照以避开上下文并发修改。 */
    void snapshotModuleSlots() {
        List<BlockPos> matched;
        try {
            matched = NanochipComplexPattern.moduleSlotsOf(host.getMultiblockState());
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC @{} 采集 F 位快照失败，保留上一份：{}",
                    host.getPos(), e.toString());
            return;
        }
        // Map 的遍历顺序不稳定，按坐标排序使截断与诊断可复现。
        List<BlockPos> sorted = new ArrayList<>(matched);
        sorted.sort(Comparator.comparingInt((BlockPos p) -> p.getX())
                .thenComparingInt(p -> p.getY())
                .thenComparingInt(p -> p.getZ()));
        cachedModuleSlots = List.copyOf(sorted);
        lastMatchedModuleSlots.clear();
        lastMatchedModuleSlots.addAll(sorted);
    }

    int moduleSlotCount() {
        return cachedModuleSlots.size();
    }

    String summarizeModuleSlots() {
        Level level = host.getLevel();
        if (level == null || lastMatchedModuleSlots.isEmpty()) {
            return "缓存里没有 F 位坐标（本机这次会话还没有成功匹配过一次）";
        }
        int formed = 0;
        for (BlockPos pos : lastMatchedModuleSlots) {
            if (MetaMachine.getMachine(level, pos) instanceof NanochipModuleMachine module && module.isFormed()) {
                formed++;
            }
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (BlockPos pos : lastMatchedModuleSlots) {
            if (shown >= 6) {
                break;
            }
            if (shown > 0) {
                sb.append(" | ");
            }
            sb.append(pos.toShortString()).append('=')
                    .append(NanochipStructureDiagnostics.describeSlotContent(level, pos));
            shown++;
        }
        if (lastMatchedModuleSlots.size() > shown) {
            sb.append(" | …另 ").append(lastMatchedModuleSlots.size() - shown).append(" 格");
        }
        return "上次成功匹配的 F 位 " + lastMatchedModuleSlots.size() + " 个，其中已成形模块 " + formed + " 个：" + sb;
    }

    void tickModuleRefresh(long now) {
        // 合并催促会刷新周期游标，必须先消费，避免同 tick 再执行一次周期扫描。
        if (NanochipModContext.config().isNanochipOriginalMode()) {
            if (lastRescanTick < 0 || now - lastRescanTick >= MODULE_REFRESH_INTERVAL) {
                lastRescanTick = now;
                refreshModulesFromStructure();
            }
            return;
        }
        if (consumePendingResync(now)) {
            return;
        }
        if (lastRescanTick >= 0 && now - lastRescanTick < MODULE_REFRESH_INTERVAL) {
            return;
        }
        lastRescanTick = now;
        host.scanAndConnectModules();
    }

    void onLoad() {
        Level loadLevel = host.getLevel();
        this.joinQuietUntilTick = loadLevel == null ? -10_000L
                : loadLevel.getGameTime() + NanochipAssemblyComplexMachine.JOIN_QUIET_TICKS;
        this.resyncRequested.set(false);
        this.lastForcedResyncTick = -10_000L;
    }

    void disconnectIfExpired(Level lv) {
        if (this.moduleDisconnectDeadline >= 0L && lv.getGameTime() >= this.moduleDisconnectDeadline) {
            this.moduleDisconnectDeadline = -1L;
            int dropped = modules.size();
            host.safeClearModules();
            NanochipModContext.logger().info("[gtlmodular] NAC @{} 失效宽限期（{} tick）到 ⇒ 断开模块 {} 个"
                            + "（宽限期内若重新成形则不断开 —— 这是 0.1.21 的连接去抖）",
                    host.getPos(), INVALID_GRACE_TICKS, dropped);
        }
    }

    void cancelDisconnect() {
        if (this.moduleDisconnectDeadline >= 0L) {
            this.moduleDisconnectDeadline = -1L;
        }
    }

    void scheduleDisconnect() {
        Level invalidLevel = host.getLevel();
        this.moduleDisconnectDeadline = invalidLevel == null ? -1L : invalidLevel.getGameTime() + INVALID_GRACE_TICKS;
    }

    void saveModuleSlots(CompoundTag tag, boolean forDrop) {
        // 掉落物不能携带旧世界槽位坐标，否则换位置放置后会指向旧结构。
        if (!forDrop && !cachedModuleSlots.isEmpty()) {
            long[] packed = new long[cachedModuleSlots.size()];
            for (int i = 0; i < packed.length; i++) {
                packed[i] = cachedModuleSlots.get(i).asLong();
            }
            tag.putLongArray(NBT_MODULE_SLOTS, packed);
        }
    }

    void loadModuleSlots(CompoundTag tag) {
        // 读回的槽位须待世界加载后验证，不能直接作为在线快照。
        if (tag.contains(NBT_MODULE_SLOTS)) {
            long[] packed = tag.getLongArray(NBT_MODULE_SLOTS);
            List<BlockPos> restored = new ArrayList<>(packed.length);
            for (long v : packed) {
                restored.add(BlockPos.of(v));
            }
            restored.sort(Comparator.comparingInt((BlockPos p) -> p.getX())
                    .thenComparingInt(p -> p.getY())
                    .thenComparingInt(p -> p.getZ()));
            this.restoredModuleSlots = List.copyOf(restored);
            this.restoredSlotsSettled = false;
            NanochipModContext.logger().info("[gtlmodular] 读档：取得存档 F 位快照 {} 格"
                    + "（待逐格验证后启用；验证入口 adoptRestoredModuleSlots）", restored.size());
        }
    }

    int lastMatchedSlotCount() {
        return lastMatchedModuleSlots.size();
    }
}
