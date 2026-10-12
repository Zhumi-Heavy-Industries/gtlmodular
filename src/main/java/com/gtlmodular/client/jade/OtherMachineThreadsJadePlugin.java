package com.gtlmodular.client.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gtlmodular.common.machine.independent.control.IndependentLogicAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin
public final class OtherMachineThreadsJadePlugin implements IWailaPlugin {
    private static final String DATA = "gtlmodular_independent_display";
    private static final ResourceLocation UID = new ResourceLocation("gtlmodular", "independent_threads");

    @Override public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(Provider.INSTANCE, MetaMachineBlockEntity.class);
    }

    @Override public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Provider.INSTANCE, Block.class);
        registration.addTooltipCollectedCallback((tooltip, accessor) -> {
            if (accessor instanceof BlockAccessor block && block.getServerData().getCompound(DATA).getBoolean("owned")) {
                tooltip.remove(new ResourceLocation("gtceu", "workable_provider"));
                tooltip.remove(new ResourceLocation("gtlcore", "recipe_multiplier_provider"));
            }
        });
    }

    public enum Provider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override public ResourceLocation getUid() { return UID; }

        @Override public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity holder)) return;
            var controller = IndependentLogicAccess.controller(holder.getMetaMachine());
            if (controller == null) return;
            var lines = controller.display();
            if (lines.isEmpty()) return;
            CompoundTag data = new CompoundTag();
            data.putBoolean("owned", controller.ownsWork());
            ListTag rows = new ListTag();
            for (Component line : lines) rows.add(StringTag.valueOf(Component.Serializer.toJson(line)));
            data.put("rows", rows);
            tag.put(DATA, data);
        }

        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            ListTag rows = accessor.getServerData().getCompound(DATA).getList("rows", 8);
            for (int i = 0; i < rows.size(); i++) {
                Component line = Component.Serializer.fromJson(rows.getString(i));
                if (line != null) tooltip.add(line);
            }
        }
    }
}
