package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class NanochipSlotPublisher {

    record Messages(String publish, String clear, String heartbeat) {}

    private final MultiblockControllerMachine machine;
    private final Supplier<String> readSlots;
    private final Consumer<String> writeSlots;
    private final int maxSlots;
    private final Messages messages;

    private static final int NAC_SLOT_HEARTBEAT_INTERVAL = 20;

    private int nacSlotHeartbeat = -1;

    private int lastNacSlotPartCount = -1;

    NanochipSlotPublisher(MultiblockControllerMachine machine, Supplier<String> readSlots,
                          Consumer<String> writeSlots, int maxSlots, Messages messages) {
        this.machine = machine;
        this.readSlots = readSlots;
        this.writeSlots = writeSlots;
        this.maxSlots = maxSlots;
        this.messages = messages;
    }

    void publishNacSlots() {
        Level level = machine.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        String encoded = encodeNacSlots();
        if (!encoded.equals(readSlots.get())) {
            writeSlots.accept(encoded);
            NanochipModContext.logger().info(messages.publish(),
                    machine.getPos(), encoded.isEmpty() ? 0 : encoded.split(",").length);
        }
    }

    void clearNacSlots() {
        Level level = machine.getLevel();
        if (level != null && level.isClientSide) {
            return;
        }
        if (!readSlots.get().isEmpty()) {
            writeSlots.accept("");
            NanochipModContext.logger().info(messages.clear(), machine.getPos());
        }
    }

    /** 零件可能晚于成形回调就绪，周期比较零件数才能补发仓位清单。 */
    boolean tickNacSlotHeartbeat() {
        Level level = machine.getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        if (++this.nacSlotHeartbeat % NAC_SLOT_HEARTBEAT_INTERVAL != 0) {
            return false;
        }
        if (!machine.isFormed()) {
            return false;
        }
        int parts = machine.getParts() == null ? 0 : machine.getParts().size();
        if (parts == this.lastNacSlotPartCount) {
            return false;
        }
        int previous = this.lastNacSlotPartCount;
        this.lastNacSlotPartCount = parts;
        publishNacSlots();
        NanochipModContext.logger().info(messages.heartbeat(),
                machine.getPos(), previous, parts,
                readSlots.get().isEmpty() ? "空（0 条）" : readSlots.get().split(",").length + " 条");
        return true;
    }

    /** 超限时保留的仓位取决于上游零件顺序，不能另行按坐标排序。 */
    private String encodeNacSlots() {
        List<IMultiPart> parts = machine.getParts();
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        int dropped = 0;
        for (IMultiPart part : parts) {
            if (part == null || part.self() == null) {
                continue;
            }
            if (kept >= maxSlots) {
                dropped++;
                continue;
            }
            if (kept > 0) {
                sb.append(',');
            }
            sb.append(part.self().getPos().asLong());
            kept++;
        }
        if (dropped > 0) {
            NanochipModContext.logger().warn("[gtlmodular] CTM: 仓位清单超过上限 {} 条（结构里 {} 个 part @{}），"
                            + "已截断 {} 条 —— 被截断的那几格外壳保持原样（不参与连接纹理）。",
                    maxSlots, parts.size(), machine.getPos(), dropped);
        }
        return sb.toString();
    }

    void resetHeartbeat() {
        this.nacSlotHeartbeat = -1;
    }

    void resetPartCount() {
        this.lastNacSlotPartCount = -1;
    }

    void recordFormedParts() {
        this.nacSlotHeartbeat = 0;
        this.lastNacSlotPartCount = machine.getParts() == null ? -1 : machine.getParts().size();
    }
}
