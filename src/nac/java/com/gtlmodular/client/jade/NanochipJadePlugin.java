package com.gtlmodular.client.jade;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gtlmodular.common.machine.nanochip.NanochipColor;
import com.gtlmodular.common.machine.nanochip.NanochipVacuumHatchMachine;
import com.gtlmodular.common.machine.nanochip.display.NanochipLogisticsText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** 真空颜色由本模组字段提供，需要自行显示颜色名；GTCEu 的喷漆提示读取另一套状态。 */
@WailaPlugin
public class NanochipJadePlugin implements IWailaPlugin {

    /** 批次表只在服务端权威维护，需两端注册 provider，以 serverData 驱动客户端显示。 */
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(NanochipThreadedJadeProvider.INSTANCE, net.minecraft.world.level.block.entity.BlockEntity.class);
        registration.registerBlockDataProvider(NanochipModuleBonusJadeProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(NanochipInputJadeProvider.INSTANCE, MetaMachineBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(VacuumColorProvider.INSTANCE, Block.class);
        registration.registerBlockComponent(NanochipThreadedJadeProvider.INSTANCE, Block.class);
        registration.registerBlockComponent(NanochipModuleBonusJadeProvider.INSTANCE, Block.class);
        registration.registerBlockComponent(NanochipInputJadeProvider.INSTANCE, Block.class);
        // 收集完成后按 provider UID 精确删除，不影响其他行的图标、对齐和合法倍率。
        registration.addTooltipCollectedCallback((tooltip, accessor) -> {
            if (accessor instanceof BlockAccessor block && NanochipJadeThreadData.isIndependentThreaded(block)) {
                tooltip.remove(new ResourceLocation("gtlcore", "recipe_multiplier_provider"));
            }
        });
    }

    public enum VacuumColorProvider implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID =
                NanochipModContext.registryId("vacuum_conveyor_color");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity)) {
                return;
            }
            if (!(blockEntity.getMetaMachine() instanceof NanochipVacuumHatchMachine hatch)) {
                return;
            }
            tooltip.add(NanochipLogisticsText.deprecated());
            NanochipColor color = hatch.color();
            if (color == null) {
                tooltip.add(Component.translatable("gtlmodular.nanochip.vacuum.color.unpainted"));
                return;
            }
            tooltip.add(Component.translatable("gtlmodular.nanochip.vacuum.color.line",
                    NanochipLogisticsText.colorName(color)));
        }
    }
}
