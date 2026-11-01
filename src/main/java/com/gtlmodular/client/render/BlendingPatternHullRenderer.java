package com.gtlmodular.client.render;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gtladd.gtladditions.client.render.machine.OverlayHullMachineRenderer;
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

/**
 * 甲-正解：猪咪样板总成的**外壳融合渲染器**（纯客户端 ✓ 不碰任何存档/数据 ✓）。
 *
 * <p>取证依据（javap ✓）：GTCEu 自己就有"部件成型后显示成机壳"的正规接口
 * {@code IMultiController#getPartAppearance(IMultiPart, Direction, BlockState, BlockPos)} ✓
 * —— 现役部件之所以能融进机壳，就是因为它走了**外观**这条路 ✓
 * 我们（与 gtladditions）强制指定了自己的渲染器 ✗ ⇒ 绕过了外观 ⇒ 永远是自己那身壳 ✓
 *
 * <p>本类做法：
 * <ol>
 *   <li>未成型 / 开关关 / 取不到机壳 ⇒ <b>走 super</b>（自带外壳 ✓ 与旧版完全一致 ✓）</li>
 *   <li>已成型 ⇒ 问控制器要"机壳方块" ✓ 用**该方块的模型**渲染 ⇒ 与机壳真正一体 ✓</li>
 *   <li>正脸（side == 朝向正面）⇒ 另外叠一层我们自己的覆盖层 ✓（保留"这是一台部件"的信息 ✓）</li>
 *   <li>任何异常 ⇒ 回落 super ✓（绝不紫黑/缺模型 ✗）</li>
 * </ol>
 */
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
                        // 正脸：再叠我们自己的覆盖层（保留"这里是一台部件"的可读性 ✓）
                        if (side != null && frontFacing != null && side == frontFacing) {
                            try {
                                // 正脸用**我们自己的模型**画一层（= 保留"这是一台部件"的信息 ✓）
                                BakedModel own = getRotatedModel(side);
                                if (own != null) {
                                    quads.addAll(own.getQuads(null, side, rand));
                                }
                            } catch (Throwable ignored) {
                                // 画不出就算了 ✓ 至少机壳是对的 ✓
                            }
                        }
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 一律回落 ✓
        }
        super.renderMachine(quads, definition, machine, frontFacing, side, rand, facing, modelState);
    }

    /** 问控制器要"成型后该显示成什么方块"（GTCEu 正规接口 ✓）。 */
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
                    // 单个控制器取不到就试下一个 ✓
                }
            }
        } catch (Throwable ignored) {
            // 取不到就回落 ✓
        }
        return null;
    }
}