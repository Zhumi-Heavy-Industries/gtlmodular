package com.gtlmodular.network;

import java.util.function.Supplier;

import com.gtlmodular.common.data.GTLModularItems;
import com.gtlmodular.common.item.terminal.ShanhaiStructureDetector;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端「按住 Ctrl 右击多方块主机」→ 服务端结构检测。
 *
 * <p>客户端只负责识别输入并回报坐标；是否手持终端、距离、目标是否控制器、是否成型全部在服务端
 * 重新校验，避免客户端伪造包扫描任意结构（逐面重扫大结构会长时间占服务端线程）。
 */
public record TerminalStructureDetectPacket(BlockPos pos) {

    /** 与上游一致：超过 8 格忽略。 */
    private static final double MAX_DISTANCE_SQR = 64.0;

    public TerminalStructureDetectPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public static void handle(TerminalStructureDetectPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> apply(packet, player));
        context.setPacketHandled(true);
    }

    private static void apply(TerminalStructureDetectPacket packet, ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (!held.is(GTLModularItems.WHALE_TERMINAL.get())) return;
        if (!player.level().isLoaded(packet.pos)) return;
        if (player.position().distanceToSqr(Vec3.atCenterOf(packet.pos)) > MAX_DISTANCE_SQR) return;
        if (!(MetaMachine.getMachine(player.level(), packet.pos) instanceof IMultiController controller)) return;
        ShanhaiStructureDetector.detect(player, controller, held);
    }
}
