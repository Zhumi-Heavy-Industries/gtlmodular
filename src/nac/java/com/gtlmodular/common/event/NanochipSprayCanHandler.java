package com.gtlmodular.common.event;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtlmodular.common.machine.nanochip.NanochipColor;
import com.gtlmodular.common.machine.nanochip.NanochipPipeMachine;
import com.gtlmodular.common.machine.nanochip.NanochipVacuumHatchMachine;
import com.gtlmodular.common.machine.nanochip.display.NanochipLogisticsText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** 真空端口和管道把颜色保存在机器字段中，需在方块交互前接住喷漆并写入该字段。 */
public final class NanochipSprayCanHandler {

    private NanochipSprayCanHandler() {
    }

    private static final String GTCEU_NS = "gtceu";
    private static final String DYE_SUFFIX = "_dye_spray_can";
    private static final String SOLVENT_CAN = "solvent_spray_can";

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        ItemStack stack = event.getItemStack();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !GTCEU_NS.equals(id.getNamespace())) {
            return;
        }
        String path = id.getPath();
        boolean solvent = SOLVENT_CAN.equals(path);
        NanochipColor picked = null;
        if (path.endsWith(DYE_SUFFIX)) {
            picked = byDyeName(path.substring(0, path.length() - DYE_SUFFIX.length()));
            if (picked == null) {
                return;
            }
        } else if (!solvent) {
            return;
        }
        if (!(level.getBlockEntity(event.getPos()) instanceof MetaMachineBlockEntity be)) {
            return;
        }
        MetaMachine machine = be.getMetaMachine();
        // 未染色状态不参与真空连接，溶剂必须清除颜色，不能当作白色染料。
        final NanochipColor chosen = picked;
        NanochipColor target = solvent ? null : chosen;
        if (machine instanceof NanochipVacuumHatchMachine hatch) {
            hatch.setColor(target);
        } else if (machine instanceof NanochipPipeMachine pipe) {
            pipe.setColor(target, true);
        } else {
            return;
        }
        // 消费掉交互，防止打开机器界面或让喷漆罐重复处理方块。
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        Player player = event.getEntity();
        if (player != null) {
            player.displayClientMessage(NanochipLogisticsText.colorChanged(target), true);
        }
    }

    private static NanochipColor byDyeName(String dyeName) {
        for (DyeColor dye : DyeColor.values()) {
            if (dye.getName().equals(dyeName)) {
                return NanochipColor.of(dye);
            }
        }
        return null;
    }
}
