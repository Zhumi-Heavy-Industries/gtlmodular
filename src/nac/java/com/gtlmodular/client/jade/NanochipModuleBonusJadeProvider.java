package com.gtlmodular.client.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.display.NanochipModuleBonusText;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** 服务端发送未翻译的完整明细，客户端无须持有模块集合或重复扫描库存。 */
public final class NanochipModuleBonusJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final NanochipModuleBonusJadeProvider INSTANCE = new NanochipModuleBonusJadeProvider();
    private static final ResourceLocation UID = NanochipModContext.registryId("nanochip_module_bonuses");
    private static final String KEY = "gtlmodular_jade_module_bonuses";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity)
                || !(blockEntity.getMetaMachine() instanceof NanochipAssemblyComplexMachine host)
                || !host.isFormed()) return;
        ListTag lines = new ListTag();
        for (Component line : NanochipModuleBonusText.lines(host.moduleBonusSnapshot())) {
            lines.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        }
        data.put(KEY, lines);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity)
                || !(blockEntity.getMetaMachine() instanceof NanochipAssemblyComplexMachine)) return;
        ListTag lines = accessor.getServerData().getList(KEY, Tag.TAG_STRING);
        if (lines.isEmpty()) return;
        // 大量模块会挤出线程读数，沿用 Jade 的详情键展开完整实例明细。
        if (!accessor.showDetails()) {
            tooltip.add(Component.translatable("gtlmodular.nanochip.module_bonuses.details_hint"));
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            Component line = Component.Serializer.fromJson(lines.getString(i));
            if (line != null) tooltip.add(line);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
