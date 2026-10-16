package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.lowdragmc.lowdraglib.misc.FluidStorage;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 全板浸没装置的含杂度规则。
 *
 * NH 的 flushTank 先看 mOutputHatches 是否为空，空就整段跳过排空；能排时把杂质当废液
 * addOutputPartial 进流体输出仓。所以本包的「输出仓」只认 EXPORT_FLUIDS 的流体输出仓，
 * 物品输出总线不是排空落点。NH 的排空判断在后处理里每 20 tick 跑一次，不挂在配方结束上：
 * 只挂在配方结束，含杂度满时来不及排空的设备补上输出仓也永远跑不到排空那一步。
 * 本包没有 NH 的浸洗液槽，排空按 NH flushTank 的 ÷4 压成「超突变活性焊料 → 突变活性焊料」。
 */
final class NanochipModuleImpurity {

    private NanochipModuleImpurity() {
    }

    /** NH 的 validateRecipe 用 impurityPercentage == 1.0 直接拒配方，等价于本包含杂度满 100％。 */
    static final double CEILING = 100.0D;

    static final String FULL_KEY = "gtlmodular.nanochip.board.impurity_full";

    private static final ResourceLocation SUPER_SOLDER_ID =
            new ResourceLocation("gtceu", "super_mutated_living_solder");
    private static final ResourceLocation MUTATED_SOLDER_ID =
            new ResourceLocation("gtceu", "mutated_living_solder");

    /** 一次排空消耗的超突变活性焊料；NH 的 flushTank 按杂质流体的 ÷4 产出，所以换算是 4:1。 */
    static final long SOLDER_PER_FLUSH = 1000L;
    static final long MUTATED_PER_FLUSH = SOLDER_PER_FLUSH / 4L;

    static boolean atCeiling(double impurity) {
        return impurity >= CEILING;
    }

    static Component ceilingReason() {
        return Component.translatable(FULL_KEY);
    }

    /** 满含杂度不给新配方；正在跑的那一批不受影响。 */
    static boolean blocksStart(NanochipModuleMachine module) {
        return module.getModuleSpec().features().board() && atCeiling(module.impurity());
    }

    /** 到阈值且排得掉才排；配方结束与每 20 tick 的定时复核共用这一条判据。 */
    static boolean shouldFlush(NanochipModuleMachine module) {
        if (!module.getModuleSpec().features().board()) {
            return false;
        }
        double impurity = module.impurity();
        return impurity > 0.0D && impurity >= module.autoFlushThreshold() && canFlush(module);
    }

    /**
     * 排空落点：NH 只认流体输出仓（mOutputHatches），没有就排不掉。本包排空要拿输入仓的超突变
     * 活性焊料换 1/4 的突变活性焊料，所以输出仓装不下时也必须整体拒绝：先抽料却产不出东西就是丢流体。
     */
    static boolean canFlush(NanochipModuleMachine module) {
        Fluid superSolder = ForgeRegistries.FLUIDS.getValue(SUPER_SOLDER_ID);
        Fluid mutatedSolder = ForgeRegistries.FLUIDS.getValue(MUTATED_SOLDER_ID);
        return superSolder != null && mutatedSolder != null
                && drainable(module, superSolder, SOLDER_PER_FLUSH) >= SOLDER_PER_FLUSH
                && fillable(module, mutatedSolder, MUTATED_PER_FLUSH) >= MUTATED_PER_FLUSH;
    }

    /**
     * 先 simulate 探「输入仓抽得到 1000 mB、输出仓装得下 250 mB」，两边都成立才真抽真排。
     * 任一条不成立就一点都不动并返回 false：含杂度停在 100％ 等玩家补料，与 NH「排不掉就停」一致。
     */
    static boolean flushSolder(NanochipModuleMachine module) {
        if (!canFlush(module)) {
            return false;
        }
        // 两条都探过，同一 tick 里没有别的写手，真抽真排的数目与 simulate 一致，不会抽到了却产不出。
        Fluid superSolder = ForgeRegistries.FLUIDS.getValue(SUPER_SOLDER_ID);
        Fluid mutatedSolder = ForgeRegistries.FLUIDS.getValue(MUTATED_SOLDER_ID);
        if (drain(module, superSolder, SOLDER_PER_FLUSH, false) < SOLDER_PER_FLUSH) {
            return false;
        }
        return fill(module, mutatedSolder, MUTATED_PER_FLUSH, false) >= MUTATED_PER_FLUSH;
    }

    private static long drainable(NanochipModuleMachine module, Fluid fluid, long amount) {
        return drain(module, fluid, amount, true);
    }

    private static long fillable(NanochipModuleMachine module, Fluid fluid, long amount) {
        return fill(module, fluid, amount, true);
    }

    /** 1X/4X/9X 输入仓可能各装一部分，所以按仓按槽累计，不能只看第一个仓。 */
    private static long drain(NanochipModuleMachine module, Fluid fluid, long amount, boolean simulate) {
        long remaining = amount;
        for (FluidStorage storage : storages(module, PartAbility.IMPORT_FLUIDS)) {
            if (remaining <= 0L) {
                break;
            }
            remaining -= storage.drain(FluidStack.create(fluid, remaining), simulate).getAmount();
        }
        return amount - remaining;
    }

    private static long fill(NanochipModuleMachine module, Fluid fluid, long amount, boolean simulate) {
        long remaining = amount;
        for (FluidStorage storage : storages(module, PartAbility.EXPORT_FLUIDS)) {
            if (remaining <= 0L) {
                break;
            }
            remaining -= storage.fill(FluidStack.create(fluid, remaining), simulate);
        }
        return amount - remaining;
    }

    /**
     * 仓槽才是真正收流体的那一层：仓自身的 fill/drain 由仓的 io 门控（输出仓 fill、输入仓 drain
     * 在 GT 里恒失败），排空是机器自己内部倒料，只有 getStorages() 拿到的槽位不分进出口。
     */
    private static List<FluidStorage> storages(NanochipModuleMachine module, PartAbility ability) {
        List<FluidStorage> out = new ArrayList<>();
        var parts = module.getParts();
        if (parts == null) {
            return out;
        }
        for (var part : parts) {
            if (part == null || part.self() == null) {
                continue;
            }
            BlockState state = part.self().getBlockState();
            if (state == null || !ability.isApplicable(state.getBlock())) {
                continue;
            }
            if (part.self() instanceof FluidHatchPartMachine hatch) {
                for (FluidStorage storage : hatch.tank.getStorages()) {
                    out.add(storage);
                }
            }
        }
        return out;
    }
}
