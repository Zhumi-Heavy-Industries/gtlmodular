package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 全板浸没装置的含杂度规则。
 *
 * NH 的 flushTank 先看 mOutputHatches 是否为空，空就整段跳过排空；能排时把杂质当废液
 * addOutputPartial 进流体输出仓。所以本包的「输出仓」只认 EXPORT_FLUIDS 的流体输出仓，
 * 物品输出总线不是排空落点。NH 的排空判断在后处理里每 20 tick 跑一次，不挂在配方结束上：
 * 只挂在配方结束，含杂度满时来不及排空的设备补上输出仓也永远跑不到排空那一步。
 */
final class NanochipModuleImpurity {

    private NanochipModuleImpurity() {
    }

    /** NH 的 validateRecipe 用 impurityPercentage == 1.0 直接拒配方，等价于本包含杂度满 100％。 */
    static final double CEILING = 100.0D;

    static final String FULL_KEY = "gtlmodular.nanochip.board.impurity_full";

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

    /** 排空落点：NH 只认流体输出仓（mOutputHatches），没有就排不掉。 */
    static boolean canFlush(NanochipModuleMachine module) {
        if (module.getParts() == null) {
            return false;
        }
        for (var part : module.getParts()) {
            if (part == null || part.self() == null) {
                continue;
            }
            BlockState state = part.self().getBlockState();
            if (state != null && PartAbility.EXPORT_FLUIDS.isApplicable(state.getBlock())) {
                return true;
            }
        }
        return false;
    }
}
