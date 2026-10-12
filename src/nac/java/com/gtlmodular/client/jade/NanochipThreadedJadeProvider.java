package com.gtlmodular.client.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.diag.SwallowedErrors;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.display.IndependentThreadDisplay;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** 批次读数统一从服务端快照发布，避免客户端按单配方进度重算。 */
public class NanochipThreadedJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final NanochipThreadedJadeProvider INSTANCE = new NanochipThreadedJadeProvider();

    private static final ResourceLocation UID =
            NanochipModContext.registryId(NanochipJadeKeys.UID_PATH);

    static final String KEY_CALIB_EU_MUL = "KalibEuMul";

    static final String KEY_CALIB_DUR_MUL = "KalibDurMul";

    static final String KEY_DISTINCT_RECIPES = "DistinctRecipes";

    static final String KEY_OUTPUT_ID = "OutputId";

    static final String KEY_OUTPUT_COUNT = "OutputCount";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        try {
            if (data == null || accessor == null) {
                return;
            }
            IndependentThreadDisplay display = NanochipJadeThreadData.displayOf(accessor);
            if (display == null) {
                return;
            }
            writeCalibrationReduction(data, accessor);
            NanochipJadeThreadData.write(data, display.threadDisplaySnapshot());
        } catch (Throwable t) {
            SwallowedErrors.report("NanochipThreadedJadeProvider#appendServerData", t);

            NanochipModContext.logger().debug("[gtlmodular] NAC Jade 线程状态写入失败（本次跳过）：{}", t.toString());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        NanochipJadeDisplay.appendTooltip(tooltip, accessor, config);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    private static void writeCalibrationReduction(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity mbe)) {
            return;
        }
        if (!(mbe.getMetaMachine() instanceof NanochipAssemblyComplexMachine nac)) {
            return;
        }
        data.putDouble(KEY_CALIB_EU_MUL, nac.calibrationEuMultiplierForDisplay());
        data.putDouble(KEY_CALIB_DUR_MUL, nac.calibrationDurationMultiplierForDisplay());
    }
}
