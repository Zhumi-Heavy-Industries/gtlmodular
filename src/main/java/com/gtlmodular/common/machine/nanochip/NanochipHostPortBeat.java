package com.gtlmodular.common.machine.nanochip;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * <b>NAC 主机的 5 秒总成节拍</b>（NAC 重做 P3）。
 *
 * <p>主机不再需要自己跑"多配方并行引擎"来做这件事（旧引擎的删除在 P5），本类是**新增的一条路径**：
 * 每 100 tick（5 秒）一拍，围绕主机结构里的两种总成做三件事：
 * <ol>
 *   <li><b>收料</b>：扫描主机上所有 {@link NanochipReceiverAssemblyMachine} 的 16 格缓冲；</li>
 *   <li><b>封装（真物品 → CC）</b>：真物品查 {@link NanochipConversionMap#encapsulate}，
 *       查得到就转成 CC 并塞进主机的 {@link NanochipSenderAssemblyMachine}（之后由 P2 的线路投递发出去）；</li>
 *   <li><b>解封装（CC → 真物品）+ 汇总输出</b>：CC 件查 {@link NanochipConversionMap#materialize} 还原成真件，
 *       推给收件总成<b>相邻的非 GT 容器</b>（AE 接口 / 箱子），也就是原版控制室 VCI 的语义：
 *       {@code The NAC can also convert CCs into item equivalents and send them to its Output Bus}。
 *       已处理的 PC（{@code materializableFlag == false}）不在主机还原，留在缓冲里（那是模块/组装矩阵的料）。</li>
 * </ol>
 *
 * <h2>「收 x / 发 y / 挂起 z」</h2>
 * <ul>
 *   <li>收 = 本拍从收件总成缓冲里<b>取走</b>的件数（封装的 + 解封装的）；</li>
 *   <li>发 = 本拍塞进主机发件总成的件数（= 即将由线路发出的 CC）；</li>
 *   <li>挂起 = 主机所有发件总成缓冲的<b>当前余量</b>（推不出去的都在这里，绝不会凭空消失）。</li>
 * </ul>
 * 日志严格按规划书 P3 的验收口径打印：「第 N 拍：收 x / 发 y / 挂起 z」。
 *
 * <h2>发件总成怎么选（P3 的临时规则）</h2>
 * 一条线 =（同频 + 同色）。P3 先按「与收件总成同频同色」挑发件总成；没有就退回主机上坐标最小的那台。
 * P4 接模块/样板后，这里会换成"按样板目标线路派料"。
 *
 * <h2>为什么解封装只推给"非 GT 机器"</h2>
 * 防止和 GT 自己的物流打架：主机结构里相邻的 GT 仓（输入/输出总线）有自己的配方语义，
 * 我们从这里硬塞会造成"同一件既被配方当输入又被我们当产物"的双重口径。AE 接口/箱子/漏斗不是
 * {@link MetaMachineBlockEntity}，可以安全接收。
 */
public final class NanochipHostPortBeat {

    /** 5 秒一拍（与原版 NAC 的封装周期一致）。 */
    public static final int BEAT_TICKS = 100;

    private static final Comparator<NanochipPortPartMachine> BY_POS =
            Comparator.comparingLong(port -> port.getPos().asLong());

    /** 上次看到的「收件/发件」台数（只用于变化时打一行诊断日志，不存盘、不参与业务）。 */
    private static final java.util.Map<NanochipAssemblyComplexMachine, int[]> LAST_PORT_COUNTS =
            new java.util.WeakHashMap<>();

    private NanochipHostPortBeat() {
    }

    /** 台数变化时打一行 INFO：「节拍清点：收件 n 台 / 发件 m 台」——实机排查"节拍看不见部件"用。 */
    private static void logPortCounts(NanochipAssemblyComplexMachine host, int receivers, int senders) {
        int[] last = LAST_PORT_COUNTS.get(host);
        if (last != null && last[0] == receivers && last[1] == senders) {
            return;
        }
        LAST_PORT_COUNTS.put(host, new int[]{receivers, senders});
        GTLModular.LOGGER.info("[gtlmodular] NAC 主机 @{} 节拍清点：收件总成 {} 台 / 发件总成 {} 台",
                host.getPos(), receivers, senders);
    }

    /**
     * 主机每 tick 调一次；只有落在 100 tick 相位上才真的执行。
     *
     * @param beatCounter 主机持有的"已执行拍数"计数器（不存盘、不同步，只用于日志里的「第 N 拍」）
     * @return 新的计数器（本拍执行了就 +1，否则原样返回）
     */
    public static long tick(NanochipAssemblyComplexMachine host, long beatCounter) {
        Level level = host.getLevel();
        if (level == null || level.isClientSide || !host.isFormed()) {
            return beatCounter;
        }
        // 相位按主机坐标错开，避免多台主机同一 tick 集中干活。
        if (Math.floorMod(level.getGameTime() + host.getPos().asLong(), BEAT_TICKS) != 0) {
            return beatCounter;
        }
        if (!GTLModularConfig.isNanochipPortRoutingEnabled()) {
            return beatCounter;
        }

        List<NanochipReceiverAssemblyMachine> receivers = new ArrayList<>(2);
        List<NanochipSenderAssemblyMachine> senders = new ArrayList<>(2);
        for (IMultiPart part : host.getParts()) {
            MetaMachine machine = part.self();
            if (machine instanceof NanochipReceiverAssemblyMachine receiver) {
                receivers.add(receiver);
            } else if (machine instanceof NanochipSenderAssemblyMachine sender) {
                senders.add(sender);
            }
        }
        if (receivers.isEmpty() && senders.isEmpty()) {
            return beatCounter;
        }
        receivers.sort(BY_POS);
        senders.sort(BY_POS);
        logPortCounts(host, receivers.size(), senders.size());

        long beat = beatCounter + 1;
        int received = 0;   // 从收件总成取走的件数
        int sent = 0;       // 塞进发件总成的件数

        if (!receivers.isEmpty()) {
            // 换算表来自 CONVERSION 配方（配方管理器换了才会重建，ensure 内部有缓存）。
            NanochipConversionMap.ensure(level.getServer());
            for (NanochipReceiverAssemblyMachine receiver : receivers) {
                NotifiableItemStackHandler buffer = receiver.getInventory();
                if (buffer == null) {
                    continue;
                }
                for (int slot = 0; slot < buffer.getSlots(); slot++) {
                    ItemStack stack = buffer.getStackInSlot(slot);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    Boolean materializable = NanochipConversionMap.materializableFlag(stack);
                    if (materializable == null) {
                        // ① 真物品 → CC → 主机发件总成（P2 的线路会把它发出去）
                        ItemStack cc = NanochipConversionMap.encapsulate(stack);
                        if (cc.isEmpty()) {
                            continue;                     // 没有换算配方：原样留在缓冲（NH 语义：不动）
                        }
                        NanochipSenderAssemblyMachine target = pickSender(senders, receiver);
                        if (target == null) {
                            continue;                     // 主机没有发件总成：真物品留在收件总成
                        }
                        int moved = insertInto(target.getInventory(), cc);
                        if (moved > 0) {
                            // ★ 必须走 storage（不是 handler.extractItem）：收件总成的 handlerIO=IN，
                            //   NotifiableItemStackHandler.extractItem 的字节码是
                            //   `canCapOutput() ? storage.extractItem : 空栈` ⇒ 对 IN 仓**静默不删**。
                            //   0.3.191 实测就栽在这里：真物品没被扣掉，每拍又封装一份 CC ⇒ 无限刷虚拟件。
                            buffer.storage.extractItem(slot, moved, false);
                            received += moved;
                            sent += moved;
                        }
                    } else if (materializable) {
                        // ② CC → 真物品 → 主机的输出总线（NH：VCI 把真件送进 Output Bus）
                        ItemStack real = NanochipConversionMap.materialize(stack);
                        if (NanochipConversionMap.isCc(real)) {
                            // 换算表里没有这件 CC 对应的真件 ⇒ 不硬塞给 AE（原样留在缓冲，等配方表更新）
                            continue;
                        }
                        int moved = exportUnsealed(host, receiver, real);
                        if (moved > 0) {
                            // 同上：必须 storage.extractItem，否则 CC 不消失、每拍再吐一份真件 ⇒ 刷物品。
                            buffer.storage.extractItem(slot, moved, false);
                            received += moved;
                        }
                    }
                    // ③ PC（已处理，不可还原）：留在缓冲里，等模块/组装矩阵来取
                }
            }
        }

        long pending = 0;
        for (NanochipSenderAssemblyMachine sender : senders) {
            pending += bufferedCount(sender);
        }

        host.recordBeat(beat, received, sent, pending);
        if (GTLModularConfig.isNanochipHostBeatLogEnabled()) {
            GTLModular.LOGGER.info("[gtlmodular] NAC 主机 @{} 第 {} 拍：收 {} / 发 {} / 挂起 {}",
                    host.getPos(), beat, received, sent, pending);
        }
        return beat;
    }

    /** 选发件总成：优先与收件总成同频同色（同一条线），否则退回坐标最小的那台。 */
    private static NanochipSenderAssemblyMachine pickSender(List<NanochipSenderAssemblyMachine> senders,
                                                            NanochipReceiverAssemblyMachine receiver) {
        if (senders.isEmpty()) {
            return null;
        }
        NanochipPortNetwork.ChannelKey key = receiver.channelKey();
        if (key != null) {
            for (NanochipSenderAssemblyMachine sender : senders) {
                if (key.equals(sender.channelKey())) {
                    return sender;
                }
            }
        }
        return senders.get(0);
    }

    /** 把一件（或一小堆）塞进一个 16 格缓冲，返回实际塞进去的数量。 */
    private static int insertInto(NotifiableItemStackHandler handler, ItemStack stack) {
        if (handler == null) {
            return 0;
        }
        ItemStack remaining = insertAll(handler, stack);
        int inserted = stack.getCount() - remaining.getCount();
        if (inserted > 0) {
            handler.onContentsChanged();
        }
        return inserted;
    }

    /** 把物品塞进一个 16 格缓冲，返回<b>塞不下的剩余</b>（可能为空）。 */
    private static ItemStack insertAll(NotifiableItemStackHandler handler, ItemStack stack) {
        ItemStack remaining = stack;
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.storage.insertItem(slot, remaining, false);
        }
        return remaining;
    }

    /**
     * <b>解封装产物的出口</b>：真物品优先塞进<b>主机自己的输出总线 / ME 输出总成</b>
     * （NH 原版语义：{@code The NAC can also convert CCs into item equivalents and send them to its Output Bus}）。
     *
     * <p>顺序：
     * <ol>
     *   <li><b>主机的输出总线</b>（{@code ItemBusPartMachine} 且 {@code handlerIO == OUT}，
     *       排除我们自己的收/发件总成）—— 玩家把 AE 接口贴在输出总线上即可进 AE；</li>
     *   <li><b>主机里的 ME 输出总成 / 任何 AE 节点部件</b>（{@link IGridConnectedMachine}）——
     *       直接写进它的 AE 网络存储。用户实测反馈的「ME 聚合输出总成」就是这一类：
     *       它是 gtlcore 的 {@code MEExtendedOutputPartMachine}，<b>不是</b> {@code ItemBusPartMachine}，
     *       所以旧的"只找输出总线"逻辑会把它跳过、料全卡在收件总成里；</li>
     *   <li>兜底：收件总成外侧贴着<b>非 GT 容器</b>（有人把 AE 接口直接贴在总成上时也能出）。</li>
     * </ol>
     * 返回实际推出的数量。
     */
    private static int exportUnsealed(NanochipAssemblyComplexMachine host,
                                      NanochipReceiverAssemblyMachine receiver, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ItemStack remaining = stack;
        int outputBuses = 0;
        int meParts = 0;
        int meOnline = 0;
        // ① 主机输出总线（按坐标顺序，塞满再下一台）
        for (IMultiPart part : host.getParts()) {
            if (remaining.isEmpty()) {
                break;
            }
            MetaMachine machine = part.self();
            if (!(machine instanceof ItemBusPartMachine bus) || bus instanceof NanochipPortPartMachine) {
                continue;
            }
            NotifiableItemStackHandler inventory = bus.getInventory();
            if (inventory == null || inventory.handlerIO != IO.OUT) {
                continue;                       // 只认输出总线（输入总线/双向仓不塞）
            }
            outputBuses++;
            remaining = insertAll(inventory, remaining);
        }
        // ② 主机里的 ME 输出总成 / AE 节点部件：直接写进 AE 网络
        for (IMultiPart part : host.getParts()) {
            if (remaining.isEmpty()) {
                break;
            }
            MetaMachine machine = part.self();
            if (machine instanceof IGridConnectedMachine mePart) {
                meParts++;
                boolean online = mePart.isOnline()
                        && mePart.getMainNode() != null
                        && mePart.getMainNode().getGrid() != null;
                if (online) {
                    meOnline++;
                }
                remaining = insertIntoMe(mePart, remaining);
            }
        }
        // ③ 兜底：收件总成外侧的非 GT 容器
        if (!remaining.isEmpty()) {
            int moved = exportToNeighbourContainers(receiver.getLevel(), receiver.getPos(), remaining);
            remaining = remaining.copyWithCount(remaining.getCount() - moved);
        }
        if (!remaining.isEmpty()) {
            warnExportStuck(host, receiver, remaining.getCount(), outputBuses, meParts, meOnline);
        }
        return stack.getCount() - remaining.getCount();
    }

    /** 上次「解封装出不去」告警的 gameTime（按主机弱引用，2 分钟限流）。 */
    private static final java.util.Map<NanochipAssemblyComplexMachine, Long> LAST_EXPORT_WARN =
            new java.util.WeakHashMap<>();

    private static void warnExportStuck(NanochipAssemblyComplexMachine host,
                                        NanochipReceiverAssemblyMachine receiver,
                                        int left, int outputBuses, int meParts, int meOnline) {
        Level level = host.getLevel();
        long now = level == null ? 0L : level.getGameTime();
        Long last = LAST_EXPORT_WARN.get(host);
        if (last != null && now - last < 1200L) {
            return;
        }
        LAST_EXPORT_WARN.put(host, now);
        GTLModular.LOGGER.warn(
                "[gtlmodular] NAC 解封装产物出不去：剩 {} 件卡在收件总成 @{}。"
                        + "原因排查：主机结构部件 {} 个；输出总线 {} 台；AE 节点部件 {} 台（在线 {} 台）；"
                        + "收件总成外侧相邻容器未接入。"
                        + "→ 请确认主机里放了输出总线/在线的 ME 输出总成，或把 AE 接口贴在收件总成外侧",
                left, receiver.getPos(), host.getParts().size(), outputBuses, meParts, meOnline);
    }

    /**
     * 把物品直接写进某个 AE 节点部件所在的<b>网络存储</b>，返回塞不下的剩余。
     *
     * <p>用它而不是调该部件自己的 recipe handler：ME 输出总成（gtlcore 的
     * {@code MEExtendedOutputPartMachine}）的对外契约是"配方产物"，没有"塞一件物品进来"的公开口；
     * 而 AE 网络存储本身就是它写产物的地方（{@code grid.getStorageService().getInventory()}）。
     * 节点离线/没有网络时原样返回，走后面的兜底。
     */
    private static ItemStack insertIntoMe(IGridConnectedMachine mePart, ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        try {
            var node = mePart.getMainNode();
            if (node == null) {
                return stack;
            }
            var grid = node.getGrid();
            if (grid == null) {
                return stack;
            }
            MEStorage storage = grid.getStorageService().getInventory();
            if (storage == null) {
                return stack;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) {
                return stack;
            }
            long inserted = storage.insert(key, stack.getCount(), Actionable.MODULATE, IActionSource.empty());
            if (inserted <= 0) {
                return stack;
            }
            int moved = (int) Math.min(inserted, stack.getCount());
            return stack.copyWithCount(stack.getCount() - moved);
        } catch (Throwable ignored) {
            // AE 网络异常（未成形/离线/热重载）绝不能让主机节拍崩：原样留下，下一拍再试。
            return stack;
        }
    }

    /**
     * 把物品推给某个位置的<b>相邻非 GT 容器</b>（六面都试），返回实际推出的数量。
     *
     * <p>跳过 {@link MetaMachineBlockEntity}：那是 GT 自己的机器/仓，不能从这条"主机产物出口"硬塞，
     * 否则会与它们各自的配方/物流语义打架。
     */
    private static int exportToNeighbourContainers(Level level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ItemStack remaining = stack;
        for (Direction direction : Direction.values()) {
            if (remaining.isEmpty()) {
                break;
            }
            BlockPos neighbour = pos.relative(direction);
            if (!level.isLoaded(neighbour)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(neighbour);
            if (be == null || be instanceof MetaMachineBlockEntity) {
                continue;
            }
            IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite())
                    .resolve()
                    .orElse(null);
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = handler.insertItem(slot, remaining, false);
            }
        }
        return stack.getCount() - remaining.getCount();
    }

    /** 一台发件总成 16 格缓冲里的当前件数（= 挂起）。 */
    private static long bufferedCount(NanochipSenderAssemblyMachine sender) {
        NotifiableItemStackHandler buffer = sender.getInventory();
        if (buffer == null) {
            return 0;
        }
        long total = 0;
        for (int slot = 0; slot < buffer.getSlots(); slot++) {
            total += buffer.getStackInSlot(slot).getCount();
        }
        return total;
    }
}
