package com.gtlmodular.client.render;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gtladd.gtladditions.client.render.machine.OverlayHullMachineRenderer;
import com.gtlmodular.common.diag.SwallowedErrors;
import com.gtlmodular.common.machine.MoltenPatternAssemblyPartMachine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** 自定义部件渲染器需主动读取控制器外观，才能在成形后融入机壳。 */
public class BlendingPatternHullRenderer extends OverlayHullMachineRenderer {

    public BlendingPatternHullRenderer(ResourceLocation hull, ResourceLocation overlay) {
        super(hull, overlay);
    }

    @Override
    public void renderMachine(List<BakedQuad> quads, MachineDefinition definition, MetaMachine machine,
                              Direction frontFacing, Direction side, RandomSource rand,
                              Direction facing, ModelState modelState) {
        try {
            if (MoltenPatternAssemblyPartMachine.BLEND_WITH_CASING
                    && machine instanceof MoltenPatternAssemblyPartMachine part
                    && part.isFormed()) {
                BlockState casing = resolveCasingState(part, side);
                if (casing != null) {
                    BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(casing);
                    if (model != null) {
                        quads.addAll(model.getQuads(casing, side, rand));
                        // 保留正面覆盖层，便于玩家辨认部件位置。
                        if (side != null && frontFacing != null && side == frontFacing) {
                            try {
                                BakedModel own = getRotatedModel(side);
                                if (own != null) {
                                    quads.addAll(own.getQuads(null, side, rand));
                                }
                            } catch (Throwable ignored) {
                                SwallowedErrors.report("BlendingPatternHullRenderer#renderMachine-frontOverlay", ignored);
                                // 覆盖层失败时仍保留已取得的机壳模型。
                            }
                        }
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("BlendingPatternHullRenderer#renderMachine-casing", ignored);
        }
        super.renderMachine(quads, definition, machine, frontFacing, side, rand, facing, modelState);
    }

    private static BlockState resolveCasingState(MoltenPatternAssemblyPartMachine part, Direction side) {
        try {
            for (IMultiController controller : part.getControllers()) {
                try {
                    BlockState st = controller.getPartAppearance((IMultiPart) part, side,
                            part.getBlockState(), part.getPos());
                    if (st != null && !st.isAir()) {
                        return st;
                    }
                } catch (Throwable ignored) {
                    SwallowedErrors.report("BlendingPatternHullRenderer#resolveCasingState-partAppearance", ignored);
                }
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("BlendingPatternHullRenderer#resolveCasingState-controllers", ignored);
        }
        return null;
    }
}