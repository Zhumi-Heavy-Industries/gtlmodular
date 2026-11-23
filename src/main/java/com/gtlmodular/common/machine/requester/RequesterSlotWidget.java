package com.gtlmodular.common.machine.requester;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.integration.ae2.utils.AEUtil;
import com.lowdragmc.lowdraglib.gui.ingredient.IGhostIngredientTarget;
import com.lowdragmc.lowdraglib.gui.ingredient.Target;
import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <b>双层配置槽</b>（照抄 ME 输入总成的 {@code AEDualConfigSlotWidget}）：
 * 上半 18×18 = 配置格（拖物品或流体，二选一，后拖的赢），
 * 下半 18×18 = 库存格（只读，显示网络现有量）。
 *
 * <h2>为什么自己画</h2>
 * <p>GTCEu 的 {@code AEItemConfigSlotWidget} / {@code AEFluidConfigSlotWidget} 各自绑定
 * 物品表或流体表，一个下标只能是固定类型；需求是"每格物品/流体二选一"（ME 输入总成语义），
 * 而且补货牌还要在格子上叠"目标量 / 状态标记"。
 *
 * <h2>流体怎么标</h2>
 * <p>从 JEI 把流体拖进配置格即可（{@link #getPhantomTargets} 同时给出物品与流体两种目标），
 * 之后该格就是流体格：底图切 {@code FLUID_SLOT}、液体用液体贴图渲染、数量带 {@code mB/B} 单位、
 * 角标显示 {@code L}。物品格则是 {@code SLOT} + 物品图标。
 *
 * <h2>点格子做什么</h2>
 * <p>左键=选中该格（把下标写进 {@link RequesterMachine#setSelectedSlot}），
 * 然后到左侧「数量设置」子页去改目标量 / 并发量；右键=清空。
 * 不再弹浮动「数量」框（旧实现把它画到了窗口外）。
 *
 * <h2>网络同步</h2>
 * <p>槽内容（key + 目标量 + 批量 + 库存）是<b>服务端权威</b>：客户端只发 action，
 * 服务端写完回推整格快照。clientSlot 是本地副本，专门给绘制用。
 */
public class RequesterSlotWidget extends Widget implements IGhostIngredientTarget {

    /** 客户端 → 服务端：设置本格为某个物品。 */
    private static final int ACTION_SET_ITEM = 1;
    /** 客户端 → 服务端：设置本格为某个流体。 */
    private static final int ACTION_SET_FLUID = 2;
    /** 客户端 → 服务端：清空本格。 */
    private static final int ACTION_CLEAR = 3;
    /** 客户端 → 服务端：选中本格（供「数量设置」子页编辑）。 */
    private static final int ACTION_SELECT = 4;
    /** 服务端 → 客户端：整格快照。 */
    private static final int UPDATE_SLOT = 100;

    private static final int CELL = RequesterMachine.SLOT_SIZE;

    private final RequesterMachine machine;
    private final int index;

    /**
     * 客户端的槽位副本。
     *
     * <p>槽内容由服务端权威写，客户端<b>不能</b>直接读服务端的 {@link RequesterSlot} 来画
     * （那是另一侧的对象，值不会更新）；必须靠 {@link #readUpdateInfo} 把服务端快照同步进来。
     * 这也是 GTCEu/gttcore 的标准做法（它们有一份 displayList）。
     */
    private RequesterSlot clientSlot = RequesterSlot.EMPTY;
    /** 服务端用：上一次推给客户端的内容，避免每 tick 刷包。 */
    private RequesterSlot lastSent = RequesterSlot.EMPTY;
    /** 服务端用：上一次推给客户端的选中态。 */
    private boolean lastSentSelected;

    public RequesterSlotWidget(RequesterMachine machine, int index, int x, int y) {
        super(new Position(x, y), new Size(CELL, RequesterMachine.SLOT_HEIGHT));
        this.machine = machine;
        this.index = index;
    }

    /** 取当前该显示的槽（客户端用副本，服务端用真数据）。 */
    private RequesterSlot displaySlot() {
        return isRemote() ? clientSlot : machine.slot(index);
    }

    /**
     * 供其它界面（如「数量设置」子页）读取本格的显示数据。
     *
     * <p>客户端返回 widget 自己同步下来的快照，服务端返回真实槽位；
     * 这样子页不需要再发一份包，也能看到服务端权威的数值。
     */
    public RequesterSlot displayedSlot() {
        return displaySlot();
    }

    public int slotIndex() {
        return index;
    }

    private boolean isSelected() {
        if (isRemote()) {
            // 客户端：选中态也是服务端字段，走 widget 自己的同步，避免主界面重建
            return clientSelected;
        }
        return machine.getSelectedSlot() == index;
    }

    /** 客户端选中态（供网格/数量弹窗查询）。 */
    public boolean isClientSelected() {
        return isRemote() ? clientSelected : machine.getSelectedSlot() == index;
    }

    /** 客户端选中态副本。 */
    private boolean clientSelected;
    /** 「数量」弹窗（可选；由界面层注入）。 */
    private RequesterAmountBox amountBox;

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    @OnlyIn(Dist.CLIENT)
    @Override
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        Position pos = getPosition();
        RequesterSlot slot = displaySlot();
        boolean fluid = slot.key() instanceof AEFluidKey;

        // ---- 上层：配置格（与 ME 输入总成一致：空槽 SLOT_DARK+暗箭头）----
        if (slot.isEmpty()) {
            GuiTextures.SLOT_DARK.draw(graphics, mouseX, mouseY, pos.x, pos.y, CELL, CELL);
            GuiTextures.CONFIG_ARROW_DARK.draw(graphics, mouseX, mouseY, pos.x, pos.y, CELL, CELL);
        } else if (fluid) {
            GuiTextures.FLUID_SLOT.draw(graphics, mouseX, mouseY, pos.x, pos.y, CELL, CELL);
            GuiTextures.CONFIG_ARROW.draw(graphics, mouseX, mouseY, pos.x, pos.y, CELL, CELL);
        } else {
            GuiTextures.SLOT.draw(graphics, mouseX, mouseY, pos.x, pos.y, CELL, CELL);
            GuiTextures.CONFIG_ARROW.draw(graphics, mouseX, mouseY, pos.x, pos.y, CELL, CELL);
        }
        if (isSelected()) {
            GuiTextures.SELECT_BOX.draw(graphics, mouseX, mouseY, pos.x, pos.y, CELL, CELL);
        }

        // ---- 下层：库存格（只读）----
        GuiTextures.SLOT_DARK.draw(graphics, mouseX, mouseY, pos.x, pos.y + CELL, CELL, CELL);

        AEKey key = slot.key();
        if (key != null) {
            // 上层画「目标」（与 ME 输入总成 config 槽同语义），下层画「网络现有」。
            drawKey(graphics, key, pos.x + 1, pos.y + 1, slot.target());
            drawKey(graphics, key, pos.x + 1, pos.y + CELL + 1, slot.stock());

            // 配置格的角标：状态 + 目标量
            char status = machine.slotStatusAt(index);
            if (status != RequesterMachine.STATUS_IDLE) {
                String mark = statusMark(status);
                if (!mark.isEmpty()) {
                    DrawerHelper.drawStringSized(graphics, mark, pos.x + 1, pos.y + 1,
                            statusColor(status), true, 0.5F, false);
                }
            }
            if (slot.target() > 0) {
                DrawerHelper.drawStringFixedCorner(graphics,
                        compact(slot.target()),
                        pos.x + CELL - 1, pos.y + CELL - 1, 0xFFFFFF, true, 0.5F);
            }
            // 库存格角标：网络现有量（有库存才画）
            if (slot.stock() > 0) {
                DrawerHelper.drawStringFixedCorner(graphics,
                        compact(slot.stock()),
                        pos.x + CELL - 1, pos.y + CELL * 2 - 1, 0xFFFFFF, true, 0.5F);
            }
            // 流体格额外角标：库存格左上角一个小 L（配置格左上角已被状态标记占用）
            if (fluid) {
                DrawerHelper.drawStringSized(graphics, "L", pos.x + 1, pos.y + CELL + 1,
                        0x66CCFF, true, 0.5F, false);
            }
        }
    }

    /**
     * 格子内的数量文字：<b>必须能放进 18px 宽、9px 高的角落</b>。
     *
     * <p>不能用 {@link RequesterNumbers#displayFor}——它会输出 {@code 1.83e6}、{@code 1.5B}
     * 这类长串（截图里数字挤出格子就是这么来的）。这里统一走 LDLib 的紧凑格式：
     * {@code 1.8k} / {@code 1.83M} / {@code 9.2E}，与 GTCEu 配置槽同款；
     * 带单位的准确数值在悬浮提示里看。
     */
    @OnlyIn(Dist.CLIENT)
    private static String compact(long amount) {
        return com.lowdragmc.lowdraglib.gui.util.TextFormattingUtil
                .formatLongToCompactString(amount, 3);
    }

    /** 在 16×16 区域内画一个 key（物品图标 / 液体贴图）；amount 决定液体填充高度。 */
    @OnlyIn(Dist.CLIENT)
    private static void drawKey(GuiGraphics graphics, AEKey key, int x, int y, long amount) {
        if (key instanceof AEItemKey itemKey) {
            ItemStack stack = itemKey.toStack(1);
            DrawerHelper.drawItemStack(graphics, stack, x, y, -1, null);
        } else if (key instanceof AEFluidKey fluidKey) {
            long shown = Math.max(1L, amount);
            FluidStack fluidStack = FluidStack.create(fluidKey.getFluid(), shown, fluidKey.getTag());
            DrawerHelper.drawFluidForGui(graphics, fluidStack, shown, x, y, 16, 16);
        }
    }

    private static String statusMark(char status) {
        return switch (status) {
            case RequesterMachine.STATUS_IN_FLIGHT -> "…";
            case RequesterMachine.STATUS_ORDERING -> "*";
            case RequesterMachine.STATUS_BLOCKED -> "!";
            case RequesterMachine.STATUS_IMPOSSIBLE -> "×";
            case RequesterMachine.STATUS_OFFLINE -> "-";
            default -> "";
        };
    }

    private static int statusColor(char status) {
        return switch (status) {
            case RequesterMachine.STATUS_IN_FLIGHT -> 0x55AAFF;
            case RequesterMachine.STATUS_ORDERING -> 0xFFD700;
            case RequesterMachine.STATUS_BLOCKED -> 0xFFAA00;
            case RequesterMachine.STATUS_IMPOSSIBLE -> 0xFF5555;
            case RequesterMachine.STATUS_OFFLINE -> 0x888888;
            default -> 0xFFFFFF;
        };
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (!isMouseOverElement(mouseX, mouseY)) return;
        RequesterSlot slot = displaySlot();
        boolean fluid = slot.key() instanceof AEFluidKey;
        List<Component> lines = new ArrayList<>();
        if (slot.isEmpty()) {
            lines.add(Component.literal("§7从 JEI 拖入物品或流体"));
            lines.add(Component.literal("§8流体拖进来就是流体格；右键清空"));
        } else {
            lines.add(Component.literal("§f" + slot.key().getDisplayName().getString()
                    + (fluid ? " §9（流体）" : "")));
            lines.add(Component.literal("§7目标 §f" + RequesterNumbers.displayFor(slot.target(), fluid)
                    + (fluid ? " §8(mB)" : "§7（" + slot.target() + "）")));
            lines.add(Component.literal("§7网络现有 §f" + RequesterNumbers.displayFor(slot.stock(), fluid)));
            long batch = slot.effectiveBatch(fluid
                    ? machine.getDefaultFluidBatch() : machine.getDefaultItemBatch());
            lines.add(Component.literal("§7单次批量 §f" + RequesterNumbers.displayFor(batch, fluid)
                    + (slot.batch() > 0L ? "" : " §8(默认)")));
            lines.add(Component.literal("§8状态 " + statusName(machine.slotStatusAt(index))));
            lines.add(Component.literal("§7左键打开数量设置"));
            lines.add(Component.literal("§8右键清空"));
        }
        graphics.renderTooltip(net.minecraft.client.Minecraft.getInstance().font, lines,
                Optional.empty(), mouseX, mouseY);
    }

    private static String statusName(char status) {
        return switch (status) {
            case RequesterMachine.STATUS_IN_FLIGHT -> "在途";
            case RequesterMachine.STATUS_ORDERING -> "计算中";
            case RequesterMachine.STATUS_BLOCKED -> "阻塞（冷却中）";
            case RequesterMachine.STATUS_IMPOSSIBLE -> "无法合成";
            case RequesterMachine.STATUS_OFFLINE -> "网络离线";
            default -> "充足";
        };
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    @OnlyIn(Dist.CLIENT)
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOverElement(mouseX, mouseY)) return false;
        // 只有上半格（配置格）响应操作；下半格是只读库存
        Position pos = getPosition();
        boolean overConfig = isMouseOver(pos.x, pos.y, CELL, CELL, mouseX, mouseY);
        if (button == 1) {
            writeClientAction(ACTION_CLEAR, buf -> {});
            return true;
        }
        if (button == 0) {
            if (!overConfig) return false;
            // 左键：手上的物品/流体直接放进去（与 GTCEu 配置槽同款手感）
            if (gui != null) {
                ItemStack held = gui.getModularUIContainer().getCarried();
                if (held != null && !held.isEmpty()) {
                    FluidStack fluid = com.lowdragmc.lowdraglib.side.fluid.FluidTransferHelper
                            .getFluidContained(held);
                    if (fluid != null && !fluid.isEmpty()) {
                        writeClientAction(ACTION_SET_FLUID, fluid::writeToBuf);
                    } else {
                        writeClientAction(ACTION_SET_ITEM, buf -> buf.writeItem(held));
                    }
                    return true;
                }
            }
            // 空手左键：选中该格，并立即在主界面下方弹出「数量」框
            writeClientAction(ACTION_SELECT, buf -> {});
            if (amountBox != null) {
                amountBox.openFor(index);
            }
            return true;
        }
        return false;
    }

    /** 由界面层注入「数量」弹窗（点格子时立即打开）。 */
    public void setAmountBox(RequesterAmountBox amountBox) {
        this.amountBox = amountBox;
    }

    // ------------------------------------------------------------------
    // 服务端 → 客户端：槽位快照同步
    // ------------------------------------------------------------------

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (isRemote()) return;
        RequesterSlot server = machine.slot(index);
        RequesterSlot snapshot = server.snapshot();
        boolean selected = machine.getSelectedSlot() == index;
        if (!snapshot.equals(lastSent) || selected != lastSentSelected) {
            lastSent = snapshot;
            lastSentSelected = selected;
            clientSlot = snapshot.copy();
            clientSelected = selected;
            writeUpdateInfo(UPDATE_SLOT, buffer -> {
                writeSlot(buffer, snapshot);
                buffer.writeBoolean(selected);
            });
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        super.readUpdateInfo(id, buffer);
        if (id == UPDATE_SLOT) {
            clientSlot = readSlot(buffer);
            clientSelected = buffer.readBoolean();
        }
    }

    /** 界面初始化时也要把当前值带过去。 */
    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        if (!isRemote()) {
            RequesterSlot snapshot = machine.slot(index).snapshot();
            lastSent = snapshot;
            lastSentSelected = machine.getSelectedSlot() == index;
            writeSlot(buffer, snapshot);
            buffer.writeBoolean(lastSentSelected);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        clientSlot = readSlot(buffer);
        clientSelected = buffer.readBoolean();
    }

    private static void writeSlot(FriendlyByteBuf buffer, RequesterSlot slot) {
        CompoundTag tag = slot.serializeNBT();
        buffer.writeNbt(tag);
    }

    private static RequesterSlot readSlot(FriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        RequesterSlot slot = new RequesterSlot(null, 0L, 0L);
        if (tag != null) slot.deserializeNBT(tag);
        return slot;
    }

    // ------------------------------------------------------------------
    // 客户端 action → 服务端
    // ------------------------------------------------------------------

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        super.handleClientAction(id, buffer);
        switch (id) {
            case ACTION_CLEAR -> {
                machine.clearSlot(index);
                if (machine.getSelectedSlot() == index) machine.clearSelectedSlot();
            }
            case ACTION_SET_ITEM -> {
                ItemStack stack = buffer.readItem();
                GenericStack generic = GenericStack.fromItemStack(stack);
                if (generic != null) {
                    machine.acceptStack(index, generic);
                }
            }
            case ACTION_SET_FLUID -> {
                FluidStack fluid = FluidStack.readFromBuf(buffer);
                GenericStack generic = AEUtil.fromFluidStack(fluid);
                if (generic != null) {
                    machine.acceptStack(index, generic);
                }
            }
            case ACTION_SELECT -> machine.setSelectedSlot(index);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    // JEI / EMI 拖拽：一个格子同时接受物品与流体
    // ------------------------------------------------------------------

    /**
     * 同时给出两种目标：物品目标 + 流体目标。
     *
     * <p>不能用 {@code IGhostItemTarget} + {@code IGhostFluidTarget} 双接口：
     * 两者都继承 {@code IGhostIngredientTarget} 且各自给了 {@code convertIngredient} 默认实现，
     * Java 会报"继承了不相关默认值"。所以这里直接实现底层的
     * {@link IGhostIngredientTarget}，自己按拖入物种类分发。
     */
    @OnlyIn(Dist.CLIENT)
    @Override
    public List<Target> getPhantomTargets(Object ingredient) {
        List<Target> targets = new ArrayList<>(2);

        ItemStack item = convertToItemStack(ingredient);
        if (item != null) {
            Rect2i area = configBox();
            targets.add(new Target() {
                @NotNull
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(@NotNull Object accepted) {
                    ItemStack stack = convertToItemStack(accepted);
                    if (stack != null) {
                        acceptItem(stack);
                    }
                }
            });
        }

        FluidStack fluid = convertToFluidStack(ingredient);
        if (fluid != null) {
            Rect2i area = configBox();
            targets.add(new Target() {
                @NotNull
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(@NotNull Object accepted) {
                    FluidStack stack = convertToFluidStack(accepted);
                    if (stack != null) {
                        acceptFluid(stack);
                    }
                }
            });
        }
        return targets;
    }

    /** 拖拽落点只覆盖上半格（配置格），库存格不接收拖拽。 */
    @OnlyIn(Dist.CLIENT)
    private Rect2i configBox() {
        Rect2i box = toRectangleBox();
        return new Rect2i(box.getX(), box.getY(), box.getWidth(), CELL);
    }

    @OnlyIn(Dist.CLIENT)
    private static ItemStack convertToItemStack(Object ingredient) {
        if (ingredient instanceof ItemStack stack) {
            return stack.isEmpty() ? null : stack;
        }
        if (com.lowdragmc.lowdraglib.LDLib.isJeiLoaded()
                && ingredient instanceof mezz.jei.api.ingredients.ITypedIngredient<?> typed) {
            // 流体也可能以 ItemStack 形式出现（桶/单元），这里只接受真正的物品
            if (typed.getIngredient() instanceof net.minecraftforge.fluids.FluidStack) return null;
            ItemStack stack = typed.getItemStack().orElse(ItemStack.EMPTY);
            return stack.isEmpty() ? null : stack;
        }
        return null;
    }

    @OnlyIn(Dist.CLIENT)
    private static FluidStack convertToFluidStack(Object ingredient) {
        // JEI / EMI / REI 都把流体包在一层里：先解包，再交给下面的判定
        Object raw = unwrapFluidIngredient(ingredient);

        // LDLib 自己的 FluidStack
        if (raw instanceof FluidStack ldlFluid) {
            return ldlFluid.isEmpty() ? null : ldlFluid.copy();
        }
        // Forge 的 FluidStack（JEI 流体面板拖出来的就是它）
        if (raw instanceof net.minecraftforge.fluids.FluidStack forgeFluid) {
            if (forgeFluid.isEmpty()) return null;
            return FluidStack.create(forgeFluid.getFluid(),
                    Math.max(1, forgeFluid.getAmount()), forgeFluid.getTag());
        }
        // 手持容器（桶等）也能拖：从物品里抽流体
        FluidStack drained = com.lowdragmc.lowdraglib.gui.widget.PhantomFluidWidget
                .drainFrom(raw == null ? ingredient : raw);
        return drained == null || drained.isEmpty() ? null : drained;
    }

    /**
     * 把 JEI/EMI/REI 的流体包装层剥开。
     *
     * <p><b>这是“只能拖物品、拖不进流体”的根因</b>：JEI 拖拽交给
     * {@code getPhantomTargets} 的是 {@code ITypedIngredient}，真正的
     * Forge {@code FluidStack} 在 {@code getIngredient()} 里。旧代码只判断外层类型，
     * 于是流体永远被当成“无法识别”而被丢弃。
     */
    @OnlyIn(Dist.CLIENT)
    private static Object unwrapFluidIngredient(Object ingredient) {
        // JEI
        if (LDLib.isJeiLoaded()
                && ingredient instanceof mezz.jei.api.ingredients.ITypedIngredient<?> typed) {
            Object inner = typed.getIngredient();
            if (inner instanceof net.minecraftforge.fluids.FluidStack fluid && !fluid.isEmpty()) {
                return fluid;
            }
            if (inner instanceof FluidStack fluid && !fluid.isEmpty()) {
                return fluid;
            }
            // 流体被 JEI 包成 ItemStack（桶/单元）时，保持原样让 drainFrom 处理
            return inner;
        }
        // REI 的 architectury FluidStack
        if (ingredient instanceof dev.architectury.fluid.FluidStack archFluid) {
            return FluidStack.create(archFluid.getFluid(),
                    Math.max(1, archFluid.getAmount()), archFluid.getTag());
        }
        return ingredient;
    }

    @OnlyIn(Dist.CLIENT)
    private void acceptItem(ItemStack itemStack) {
        writeClientAction(ACTION_SET_ITEM, buf -> buf.writeItem(itemStack));
    }

    @OnlyIn(Dist.CLIENT)
    private void acceptFluid(FluidStack fluidStack) {
        writeClientAction(ACTION_SET_FLUID, fluidStack::writeToBuf);
    }
}
