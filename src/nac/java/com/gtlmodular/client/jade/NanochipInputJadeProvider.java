package com.gtlmodular.client.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic;
import com.gtlmodular.common.machine.nanochip.display.NanochipInputText;
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

/** 客户端只接收服务端生成的文本，COMMON 配置不借客户端本地值推断。 */
public final class NanochipInputJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final NanochipInputJadeProvider INSTANCE = new NanochipInputJadeProvider();
    private static final ResourceLocation UID = NanochipModContext.registryId("nanochip_input_diagnostics");
    private static final String KEY = "gtlmodular_jade_input_diagnostics";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity)
                || !(blockEntity.getMetaMachine() instanceof NanochipAssemblyComplexMachine host)
                || !host.isFormed()) return;
        if (!accessor.showDetails()) {
            data.putBoolean(KEY + "_available", true);
            return;
        }
        if (!(host.getRecipeLogic() instanceof NanochipThreadedRecipeLogic logic)) return;
        ListTag lines = new ListTag();
        for (Component line : NanochipInputText.lines(logic.inputDiagnosticSnapshot())) {
            lines.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        }
        data.put(KEY, lines);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity)
                || !(blockEntity.getMetaMachine() instanceof NanochipAssemblyComplexMachine)) return;
        ListTag lines = accessor.getServerData().getList(KEY, Tag.TAG_STRING);
        if (lines.isEmpty() && !accessor.getServerData().getBoolean(KEY + "_available")) return;
        // 未展开详情时不触发库存采样，也不挤占实时线程读数。
        if (!accessor.showDetails()) {
            tooltip.add(Component.translatable("gtlmodular.nanochip.thread_tuning.details_hint"));
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
