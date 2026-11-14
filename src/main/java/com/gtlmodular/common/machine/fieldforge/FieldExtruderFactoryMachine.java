package com.gtlmodular.common.machine.fieldforge;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;

/**
 * 力场压模工厂（{@code gtlmodular:field_extruder_factory}）—— GTO
 * {@code gtocore:field_extruder_factory} 的移植体。
 *
 * <h2>与 GTO 原机的唯一实质差异：配方表</h2>
 * <p><b>GTO 原机跑 {@code GTRecipeTypes.EXTRUDER_RECIPES}（压模机表）</b>
 * （{@code _gto_src/.../machines/MultiBlockF.java:255}）。
 * <b>本机按队长 2026-11-14 口径改跑本模组自建的「零部件加工」表</b>
 * {@code gtlmodular:component_parts} —— 即奈亚拉托提普之触新增的那个模式所用的同一张表
 * （{@code com.gtlmodular.common.data.tentacle.ComponentPartsRecipeTypes}），
 * <b>不是</b>压模机表。原机的「力场压模」只是个设定外壳，这条改造让它在数值上真正落到
 * 「一锭出全套零件」的批量零件工艺上。</p>
 *
 * <h2>机器本身（照搬 GTO 的设定）</h2>
 * <ul>
 *   <li>{@code nonYAxisRotation} ⇒ {@link com.gregtechceu.gtceu.api.data.RotationState#NON_Y_AXIS}
 *       （只能水平朝向，与 GTO 同）；</li>
 *   <li>可并行 + 可接激光仓 + 多配方并发（GTO 的 {@code CrossRecipeMultiblockMachine::createHatchParallel}）；</li>
 *   <li>并行数：<b>不按线圈</b>（本结构没有线圈位），按结构里的并行仓（{@code PARALLEL_HATCH}，
 *       GTO 原谓词 {@code setMaxGlobalLimited(1)}）；超频走基础 {@link WorkableElectricMultiblockMachine}
 *       的完美超频 —— GTO 未给本机挂 {@code .overclock()}，故保持默认。</li>
 * </ul>
 *
 * <p>本类不覆写配方逻辑：并行由 GT 的并行仓乘法完成（{@code createHatchParallel} 语义 =
 * 结构里并行仓的并行数即整机并行数），无需自定义 {@code RecipeLogic}。</p>
 */
public class FieldExtruderFactoryMachine extends WorkableElectricMultiblockMachine {

    public FieldExtruderFactoryMachine(IMachineBlockEntity holder) {
        super(holder);
    }
}
