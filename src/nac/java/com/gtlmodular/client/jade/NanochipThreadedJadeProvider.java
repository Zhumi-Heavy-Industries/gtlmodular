package com.gtlmodular.client.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            NanochipThreadedRecipeLogic logic = threadedLogic(accessor);
            if (logic == null) {
                return;
            }
            int limit = logic.getThreadLimit();

            data.putInt(NanochipJadeKeys.KEY_THREAD_LIMIT, limit);
            int running = logic.runningThreadCount();
            data.putInt(NanochipJadeKeys.KEY_THREAD_RUNNING, running);

            data.putInt(KEY_DISTINCT_RECIPES, logic.distinctRunningRecipeCount());
            data.putLong(NanochipJadeKeys.KEY_THREAD_RUNS, logic.totalRunsAtOnce());

            data.putInt(NanochipJadeKeys.KEY_MIRRORED_SLOT, logic.mirroredThreadLabel());
            data.putLong(NanochipJadeKeys.KEY_MIRRORED_PARALLELS, logic.mirroredThreadParallels());

            writeCalibrationReduction(data, accessor);

            ListTag list = new ListTag();
            for (Map.Entry<Integer, NanochipThreadedRecipeLogic.ThreadRec> entry
                    : snapshotThreads(logic)) {
                list.add(threadTag(entry.getKey(), entry.getValue()));
            }
            data.put(NanochipJadeKeys.KEY_THREAD_LIST, list);
        } catch (Throwable t) {

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

    private static NanochipThreadedRecipeLogic threadedLogic(BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity mbe)) {
            return null;
        }
        if (!(mbe.getMetaMachine() instanceof com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine machine)) {
            return null;
        }
        return machine.getRecipeLogic() instanceof NanochipThreadedRecipeLogic logic ? logic : null;
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

    // 沿用批次快照的进度与稳定编号排序，让面板和 Jade 指向同一批次。
    private static List<Map.Entry<Integer, NanochipThreadedRecipeLogic.ThreadRec>> snapshotThreads(
            NanochipThreadedRecipeLogic logic) {
        List<Map.Entry<Integer, NanochipThreadedRecipeLogic.ThreadRec>> out =
                new ArrayList<>(NanochipJadeKeys.OUTPUT_LINES);
        for (Map.Entry<Integer, NanochipThreadedRecipeLogic.ThreadRec> entry : logic.snapshot()) {
            if (out.size() >= NanochipJadeKeys.OUTPUT_LINES) {
                break;
            }
            if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                out.add(entry);
            }
        }
        return out;
    }

    private static CompoundTag threadTag(int slot, NanochipThreadedRecipeLogic.ThreadRec rec) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(NanochipJadeKeys.KEY_SLOT, slot);
        tag.putInt(NanochipJadeKeys.KEY_PROGRESS, rec.progress());
        tag.putInt(NanochipJadeKeys.KEY_DURATION, rec.duration());

        tag.putLong(NanochipJadeKeys.KEY_PARALLELS, rec.parallels());

        // 客户端需要注册 ID 还原物品图标，仅传语言键无法查回物品。
        String outputId = NanochipThreadedRecipeLogic.firstItemOutputId(rec.recipe());
        if (outputId != null && !outputId.isEmpty()) {
            tag.putString(KEY_OUTPUT_ID, outputId);
            tag.putLong(KEY_OUTPUT_COUNT, NanochipThreadedRecipeLogic.firstItemOutputAmount(rec.recipe()));
        }
        return tag;
    }
}
