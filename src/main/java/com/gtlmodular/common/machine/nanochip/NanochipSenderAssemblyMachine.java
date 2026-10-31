package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>NAC 发件总成</b>（对应 NH 的真空输出端口 VCO）。
 *
 * <p>P1 落了骨架（频率 / 颜色 / 样板槽 / 存档 / 同步 / 界面）；<b>P2 起它负责把 16 格缓冲里的东西
 * 推到「同频 + 同色」线路的收件总成</b>：
 * <ul>
 *   <li><b>一条线只允许一个发件总成</b>：多于一个时 {@link NanochipPortNetwork.Line#conflict()} 为真，
 *       基类直接让整线罢工（日志/界面都走基类那一套），本类一个物品都不推；</li>
 *   <li><b>按数量平摊</b>给该线的多个收件总成，余数按方块坐标顺位补（规划书 §4.4 第 4 条）；</li>
 *   <li>收件总成装不下 ⇒ <b>留在本机缓冲、下一拍重试</b>（绝不丢）；基类每拍把缓冲余量记进
 *       {@code pending} 三笔账。</li>
 * </ul>
 *
 * <p>为什么是「发件推」而不是「收件拉」：规划书 §4.4 第 7 条明确写的是「<b>发件总成</b>推不出去时留缓冲、
 * 下 tick 重试」。收件总成只是被投递方，它不需要知道线路上有谁，这也避免了 N 个收件总成同时去抢同一个发件缓冲
 * 造成的重复/漏发。
 */
public class NanochipSenderAssemblyMachine extends NanochipPortPartMachine {

    public NanochipSenderAssemblyMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier, IO.OUT);
    }

    /**
     * 每拍（0.5 秒）把缓冲按数量平摊推进本线所有收件总成。
     *
     * <p><b>先按「物品 + NBT」归并、再平摊</b>：同一种物品可能分散在多个槽里（例如 8+8 个同款电路组件），
     * 若逐槽平摊，2 个收件会拿到 (4+4, 4+4)=8/8 —— 看起来对，但 3 个收件时逐槽算会得到 6/6/8 这类
     * 与"按总数量均分"不符的分配。归并后按总量算，才是规划书要的「按数量平摊、余数按位置顺位补」。
     *
     * <p>某个收件总成装不下的份额<b>不会改派</b>给别的收件总成（要求的是"按数量平摊"，不是"能塞多少塞多少"）；
     * 这部分留在原槽，计入挂起、下一拍重试。
     */
    @Override
    protected void onLineTick(NanochipPortNetwork.Line line) {
        if (line.conflict() || !line.hasReceiver() || !isWorkingEnabled()) {
            return;
        }
        NotifiableItemStackHandler inventory = getInventory();
        if (inventory == null) {
            return;
        }
        var receivers = line.receivers();
        int count = receivers.size();

        // ① 归并：物品 + NBT 相同的槽算一组（最多 16 组，线性查找足够）。
        List<Group> groups = new ArrayList<>(4);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Group group = null;
            for (Group candidate : groups) {
                if (ItemStack.isSameItemSameTags(candidate.prototype, stack)) {
                    group = candidate;
                    break;
                }
            }
            if (group == null) {
                group = new Group(stack.copyWithCount(1));
                groups.add(group);
            }
            group.slots.add(slot);
            group.total += stack.getCount();
        }

        // ② 平摊：按总数量 ÷ 收件数，余数从坐标最小的收件开始各补 1。
        for (Group group : groups) {
            // P4：发件总成放了样板 ⇒ 只发样板输出里有的物品；没放样板 ⇒ 全发（旧行为）。
            if (!lineSends(group.prototype)) {
                continue;
            }
            int each = group.total / count;
            int remainder = group.total % count;
            int moved = 0;
            for (int i = 0; i < count; i++) {
                int want = each + (i < remainder ? 1 : 0);
                if (want <= 0) {
                    continue;
                }
                moved += insertInto(receivers.get(i), group.prototype.copyWithCount(want));
            }
            if (moved > 0) {
                extractFromGroup(inventory, group, moved);
                accountPushed(moved);
            }
        }
    }

    /** 从该组的各个槽里按顺序扣掉 {@code amount} 件（只扣实际已送出的部分）。 */
    private static void extractFromGroup(NotifiableItemStackHandler inventory, Group group, int amount) {
        int remaining = amount;
        for (int slot : group.slots) {
            if (remaining <= 0) {
                return;
            }
            ItemStack inSlot = inventory.getStackInSlot(slot);
            if (inSlot.isEmpty()) {
                continue;
            }
            int take = Math.min(remaining, inSlot.getCount());
            // 走 storage（不是 handler.extractItem）：语义只看"这一格现在有什么"，
            // 不受 capabilityIO 的 IN/OUT 闸门影响（本机是 OUT 本来能过，统一写法防以后改方向踩坑）。
            inventory.storage.extractItem(slot, take, false);
            remaining -= take;
        }
    }

    /**
     * 把一件（或一小堆）塞进某个收件总成的 16 格缓冲；返回<b>实际收下</b>的数量。
     *
     * <p>走 {@code storage.insertItem}（而不是能力面）：收件总成的 handlerIO 是 IN、能力面同样只进不出，
     * 两条路等价；走 storage 是为了明确「这是本模组自己的线路投递，不是外部自动化」。
     */
    private static int insertInto(NanochipReceiverAssemblyMachine receiver, ItemStack stack) {
        NotifiableItemStackHandler target = receiver.getInventory();
        if (target == null) {
            return 0;
        }
        // P4：收件总成放了样板 ⇒ 只收样板里出现过的物品（空样板 = 全收，兼容旧行为）。
        if (!receiver.lineAccepts(stack)) {
            return 0;
        }
        int inserted = 0;
        ItemStack remaining = stack;
        for (int slot = 0; slot < target.getSlots() && !remaining.isEmpty(); slot++) {
            ItemStack rest = target.storage.insertItem(slot, remaining, false);
            int moved = remaining.getCount() - rest.getCount();
            if (moved > 0) {
                inserted += moved;
                remaining = rest;
            }
        }
        if (inserted > 0) {
            receiver.accountReceived(inserted);
        }
        return inserted;
    }

    /** 一组「物品 + NBT」相同的槽：原型（数量 1）、所在槽、总数量。 */
    private static final class Group {
        final ItemStack prototype;
        final List<Integer> slots = new ArrayList<>(4);
        int total;

        Group(ItemStack prototype) {
            this.prototype = prototype;
        }
    }
}
