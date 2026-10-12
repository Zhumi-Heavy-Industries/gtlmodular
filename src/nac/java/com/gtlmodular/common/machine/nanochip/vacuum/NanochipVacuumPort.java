package com.gtlmodular.common.machine.nanochip.vacuum;

import com.gtlmodular.common.machine.nanochip.NanochipVacuumHatchMachine;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.NanochipModuleMachine;
import com.gtlmodular.common.machine.nanochip.NanochipConversionMap;
import com.gtlmodular.common.machine.nanochip.NanochipVacuumNetwork;
import com.gtlmodular.common.machine.nanochip.NanochipPipeGraph;
import com.gtlmodular.common.machine.nanochip.NanochipColor;
import static com.gtlmodular.common.machine.nanochip.NanochipVacuumHatchMachine.ROOM_ENCAPSULATE_INTERVAL;
import static com.gtlmodular.common.machine.nanochip.NanochipVacuumHatchMachine.TRANSFER_INTERVAL;
import static com.gtlmodular.common.machine.nanochip.NanochipVacuumHatchMachine.VACUUM_MOVE_TICK;
import static com.gtlmodular.common.machine.nanochip.NanochipVacuumHatchMachine.WORK_ANIM_WINDOW;
import static com.gtlmodular.common.machine.nanochip.NanochipVacuumHatchMachine.SLOTS;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.List;

public final class NanochipVacuumPort {

    private final NanochipVacuumHatchMachine machine;
    private final java.util.function.Supplier<IO> io;
    private final Runnable refreshCasingState;
    private long lastWorkTick = Long.MIN_VALUE;
    /** 时间差参与限流，不能使用会导致减法溢出的 Long.MIN_VALUE 哨兵。 */
    private long lastVacuumLogTick = -10_000L;
    private long lastNoWorkLogTick = -10_000L;
    private static final int TRANSFER_BATCH = 64;

    public NanochipVacuumPort(NanochipVacuumHatchMachine machine,
                              java.util.function.Supplier<IO> io, Runnable refreshCasingState) {
        this.machine = machine;
        this.io = io;
        this.refreshCasingState = refreshCasingState;
    }

    public boolean isVacuumWorking() {
        Level level = machine.getLevel();
        // 未工作哨兵不能参与时间差，否则溢出会让动画永久显示工作态。
        return level != null && lastWorkTick != Long.MIN_VALUE
                && level.getGameTime() - lastWorkTick < WORK_ANIM_WINDOW;
    }

    public void autoIO() {
        if (!(currentController() instanceof NanochipAssemblyComplexMachine)) {
            return;
        }
        if (machine.getOffsetTimer() % 5L != 0L || !machine.isWorkingEnabled()) {
            return;
        }
        Level level = machine.getLevel();
        if (level == null) {
            return;
        }
        Direction front = machine.getFrontFacing();
        var be = level.getBlockEntity(machine.getPos().relative(front));
        if (be == null) {
            return;
        }
        var resolved = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
                front.getOpposite()).resolve();
        if (resolved.isEmpty()) {
            return;
        }
        net.minecraftforge.items.IItemHandler target = resolved.get();
        if (io.get() == IO.OUT) {
            for (int s = 0; s < target.getSlots(); s++) {
                ItemStack peek = target.extractItem(s, TRANSFER_BATCH, true);
                if (peek.isEmpty()) {
                    continue;
                }
                ItemStack rest = insertIntoInventory(peek);
                int moved = peek.getCount() - rest.getCount();
                if (moved > 0) {
                    target.extractItem(s, moved, false);
                }
            }
        } else {
            int slots = slotCount();
            for (int s = 0; s < slots; s++) {
                ItemStack st = machine.getInventory().storage.getStackInSlot(s);
                if (st.isEmpty()) {
                    continue;
                }
                // 还原端只从网络还原，提示玩家避免把封装件误放进本仓库存。
                if (NanochipConversionMap.isCc(st)) {
                    NanochipModContext.logger().warn(
                            "[gtlmodular] 真空输入仓 @{} 正要把「{}」推给正面容器 —— 输入仓**只从虚拟通道还原真件**、"
                                    + "不看自己的格子（手动放进来的封装件不会被还原，只会被原样推走）。"
                                    + "要还原请把它放进**输出仓 VCO**（真件→网络那一侧），或用样板总成在 ME 终端下单换算",
                            machine.getPos(), st.getHoverName().getString());
                }
                ItemStack rest = st.copy();
                for (int t = 0; t < target.getSlots() && !rest.isEmpty(); t++) {
                    rest = target.insertItem(t, rest, false);
                }
                int moved = st.getCount() - rest.getCount();
                if (moved > 0) {
                    machine.getInventory().storage.extractItem(s, moved, false);
                }
            }
        }
    }

    public void tickVacuum() {
        Level level = machine.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        // 周期刷新覆盖重载后部件回调尚未就绪的情况，使外壳显示最终收敛。
        refreshCasingState.run();
        NanochipColor myColor = machine.color();
        if (myColor == null) {
            return;
        }
        IMultiController controller = currentController();
        if (controller == null) {
            return;
        }
        boolean room = controller instanceof NanochipAssemblyComplexMachine;
        NanochipAssemblyComplexMachine nac;
        NanochipModuleMachine module = null;
        if (controller instanceof NanochipAssemblyComplexMachine host) {
            nac = host;
        } else if (controller instanceof NanochipModuleMachine mod) {
            module = mod;
            nac = mod.getHost();
        } else {
            return;
        }
        if (nac == null || !nac.isFormed()) {
            return;
        }

        boolean input = io.get() == IO.IN;
        long now = level.getGameTime();
        boolean roomEncapsulates = room && !input;
        int period = roomEncapsulates ? ROOM_ENCAPSULATE_INTERVAL : TRANSFER_INTERVAL;
        if (now % period != VACUUM_MOVE_TICK % period) {
            return;
        }

        // 只有真正搬运前才准备映射，避免未染色或未连接的仓反复查询配方管理器。
        NanochipConversionMap.ensure(level.getServer());
        NanochipVacuumNetwork net = nac.vacuumNetwork();
        NanochipColor color = myColor;
        final int comp = NanochipModContext.config().isVacuumRequiresPipes()
                ? nac.pipeGraph().componentOf(level, color, machine.getPos())
                : 0;
        boolean changed;
        if (input) {
            changed = room ? roomMaterialize(net, color, comp) : modulePull(net, color, comp, module);
        } else {
            changed = room ? roomEncapsulate(net, color, comp) : modulePush(net, color, comp, module);
        }
        if (changed) {
            lastWorkTick = now;
            nac.markVacuumDirty();
            if (now - this.lastVacuumLogTick >= 20L) {
                this.lastVacuumLogTick = now;
                NanochipModContext.logger().info(
                        "[gtlmodular] 真空搬运 @{}（{}·{}）：本次{}；该颜色通道现在 = {}",
                        machine.getPos(),
                        room ? "控制室" : "模块",
                        input ? "输入仓 VCI（网络→真件）" : "输出仓 VCO（真件→网络）",
                        input ? "有件被还原" : "有件被封装",
                        net.summary(color));
            }
        } else {
            logNoWork(net, color, room, input);
        }
    }

    private void logNoWork(NanochipVacuumNetwork net, NanochipColor color, boolean room, boolean input) {
        Level level = machine.getLevel();
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        if (now - this.lastNoWorkLogTick < 100L) {
            return;
        }
        int slots = slotCount();
        ItemStack first = ItemStack.EMPTY;
        for (int i = 0; i < slots; i++) {
            ItemStack st = machine.getInventory().storage.getStackInSlot(i);
            if (!st.isEmpty()) { first = st; break; }
        }
        long channelTotal = net.totalOf(color);
        if (input && room) {
            if (channelTotal > 0L) {
                this.lastNoWorkLogTick = now;
                NanochipModContext.logger().info(
                        "[gtlmodular] 真空输入仓 @{}（控制室 VCI）一件都没还原，但通道[{}] 里现在有 = {} "
                                + "（若为「N PC」= 那些是已处理件，按 NH 语义不可还原；若为「N CC」却提不出来 ⇒ 把这条日志发给队员）",
                        machine.getPos(), color.nameZh(), net.summary(color));
            } else if (!first.isEmpty()) {
                this.lastNoWorkLogTick = now;
                NanochipModContext.logger().info(
                        "[gtlmodular] 真空输入仓 @{}（控制室 VCI）通道[{}] 是空的，而本仓格子里还有 {} —— "
                                + "输入仓**只从网络读件**、不看自己的格子（要进网络请用**输出仓 VCO**）",
                        machine.getPos(), color.nameZh(), first.getHoverName().getString());
            }
        } else if (!input && room && !first.isEmpty()) {
            ItemStack payload = NanochipConversionMap.encapsulate(first);
            this.lastNoWorkLogTick = now;
            NanochipModContext.logger().info(
                    "[gtlmodular] 真空输出仓 @{}（控制室 VCO）有 {} 却封装不进网络（{}）—— "
                            + "该件在「真件↔CC 换算表」里查不到对应 CC，按 NH 语义本机不动它",
                    machine.getPos(), first.getHoverName().getString(),
                    payload.isEmpty() ? "encapsulate() 返回空" : "encapsulate() 有结果但通道拒收（16 种上限？）");
        }
    }

    public IMultiController currentController() {
        List<IMultiController> controllers = machine.getControllers();
        return controllers.isEmpty() ? null : controllers.get(0);
    }

    /** 库存槽数随旧 NBT 恢复，需对齐当前容量，避免旧存档缺少新增槽位。 */
    private int slotCount() {
        var storage = machine.getInventory().storage;
        if (storage.getSlots() != SLOTS) {
            storage.setSize(SLOTS);
        }
        return Math.min(SLOTS, storage.getSlots());
    }

    private boolean roomEncapsulate(NanochipVacuumNetwork net, NanochipColor color, int comp) {
        boolean changed = false;
        int slots = slotCount();
        for (int i = 0; i < slots; i++) {
            ItemStack stack = machine.getInventory().storage.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            // 模块配方需要 CC，入网前必须按映射封装真实物品。
            ItemStack payload = NanochipConversionMap.encapsulate(stack);
            if (payload.isEmpty()) {
                continue;
            }
            long accepted = net.insert(color, comp, payload, true);
            if (accepted > 0L) {
                machine.getInventory().storage.extractItem(i, (int) accepted, false);
                changed = true;
            }
        }
        return changed;
    }

    private boolean roomMaterialize(NanochipVacuumNetwork net, NanochipColor color, int comp) {
        boolean changed = false;
        for (int pass = 0; pass < 4; pass++) {
            ItemStack raw = net.extract(color, comp, TRANSFER_BATCH, Boolean.TRUE);
            if (raw.isEmpty()) {
                break;
            }
            ItemStack got = NanochipConversionMap.materialize(raw);
            ItemStack remainder = insertIntoInventory(got);
            if (!remainder.isEmpty()) {
                net.insert(color, comp, remainder, true); // 库存放不下时退回通道，避免已取出的物品丢失。
                break;
            }
            changed = true;
        }
        return changed;
    }

    private boolean modulePull(NanochipVacuumNetwork net, NanochipColor color, int comp,
                               NanochipModuleMachine module) {
        Boolean filter = module != null
                && module.getModuleSpec().features().assembly()
                ? Boolean.FALSE
                : null;
        boolean changed = false;
        for (int pass = 0; pass < 4; pass++) {
            ItemStack got = net.extract(color, comp, TRANSFER_BATCH, filter);
            if (got.isEmpty()) {
                break;
            }
            ItemStack remainder = insertIntoInventory(got);
            if (!remainder.isEmpty()) {
                net.insert(color, comp, remainder, filter == Boolean.FALSE ? Boolean.FALSE : Boolean.TRUE);
                break;
            }
            changed = true;
        }
        return changed;
    }

    private boolean modulePush(NanochipVacuumNetwork net, NanochipColor color, int comp,
                               NanochipModuleMachine module) {
        boolean legacyMaterializable = module != null
                && module.getModuleSpec().features().assembly();
        boolean changed = false;
        int slots = slotCount();
        for (int i = 0; i < slots; i++) {
            ItemStack stack = machine.getInventory().storage.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            // CC/PC 分级由物品身份决定，避免同一组件因端口位置不同而改变可还原性。
            Boolean own = NanochipConversionMap.materializableFlag(stack);
            boolean materializable = own != null ? own : legacyMaterializable;
            long accepted = net.insert(color, comp, stack, materializable);
            if (accepted > 0L) {
                machine.getInventory().storage.extractItem(i, (int) accepted, false);
                changed = true;
            }
        }
        return changed;
    }

    private ItemStack insertIntoInventory(ItemStack stack) {
        ItemStack remainder = stack;
        int slots = slotCount();
        for (int i = 0; i < slots && !remainder.isEmpty(); i++) {
            remainder = machine.getInventory().storage.insertItem(i, remainder, false);
        }
        return remainder;
    }

    public String channelSummary(NanochipVacuumNetwork net) {
        NanochipColor c = machine.color();
        return net == null || c == null ? "" : net.summary(c);
    }

    public int routeComponent() {
        if (!NanochipModContext.config().isVacuumRequiresPipes()) {
            return 0;
        }
        NanochipColor c = machine.color();
        NanochipAssemblyComplexMachine nac = hostComplex();
        Level level = machine.getLevel();
        if (nac == null || level == null) {
            return 0;
        }
        return nac.pipeGraph().componentOf(level, c, machine.getPos());
    }

    public String routingModeText() {
        if (machine.color() == null) {
            return "§7路由模式：§c未染色§7（未染色不连接、不搬运；先用螺丝刀右键或「真空传送」页染色）";
        }
        if (!NanochipModContext.config().isVacuumRequiresPipes()) {
            return "§7路由模式：§a简单（颜色即线，不用铺管道）";
        }
        int comp = machine.routeComponent();
        return comp == NanochipPipeGraph.UNWIRED
                ? "§7路由模式：§e管道定向§7 · 本仓§c未接管道§7（只能与同样未接管道的同色仓互通）"
                : "§7路由模式：§e管道定向§7 · 本仓已在同色管网分量 §f#" + comp;
    }

    public void clearChannel() {
        NanochipColor c = machine.color();
        NanochipAssemblyComplexMachine nac = hostComplex();
        if (c != null && nac != null && !machine.getLevel().isClientSide) {
            nac.vacuumNetwork().clearChannel(c);
            nac.markVacuumDirty();
        }
    }

    public NanochipAssemblyComplexMachine hostComplex() {
        IMultiController controller = currentController();
        if (controller instanceof NanochipAssemblyComplexMachine nac) {
            return nac;
        }
        if (controller instanceof NanochipModuleMachine module) {
            return module.getHost();
        }
        return null;
    }
}
