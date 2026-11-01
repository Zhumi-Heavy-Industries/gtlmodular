package com.gtlmodular.common.machine.print;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtlmodular.common.machine.engine.IThreadedRecipeMachine;
import com.gtlmodular.common.machine.engine.ThreadedRecipeLogic;

/**
 * 多方块「3D 打印阵列」：8 条线程并发打印一次性工具。
 *
 * <p>结构由 {@code DisposableToolPrintMachines#arrayPattern} 生成（7×5×7 的钛稳定机壳 + 侧面玻璃带），
 * 并行数由结构里的<b>标准 GT 并行仓</b>（{@code PartAbility.PARALLEL_HATCH}）决定
 * —— 8 条线程各自独立找配方、各自计时；每条线程同时可以按并行仓的并行数批量跑。
 *
 * <p><b>机制不依赖 gtlcore / gtladditions</b>：配方引擎换成
 * {@link ThreadedRecipeLogic}（{@code com.gtlmodular.common.machine.engine} 包内新写的独立实现），
 * 只使用 GTCEu 自己的 {@code RecipeLogic} / {@code GTRecipe} / {@code GTRecipeModifiers}。
 */
public class DisposableToolPrinterMultiblockMachine extends WorkableElectricMultiblockMachine
        implements IThreadedRecipeMachine {

    /** 基础线程数（队长口径：八线程）。 */
    public static final int THREADS = 8;

    public DisposableToolPrinterMultiblockMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new ThreadedRecipeLogic(this);
    }

    /** 2026-10-30 诊断：成形/失形各打一行，便于从日志判断是结构没成还是配方没配。 */
    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        com.gtlmodular.GTLModular.LOGGER.info("[gtlmodular] 3D打印阵列已成形 @{}", getPos());
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        com.gtlmodular.GTLModular.LOGGER.info("[gtlmodular] 3D打印阵列失形 @{}", getPos());
    }

    @Override
    public int baseThreads() {
        return THREADS;
    }

    /** 普通多方块不再额外乘倍率；并行全交给结构里的标准并行仓。 */
    @Override
    public int parallelMultiplier() {
        return 1;
    }
}
