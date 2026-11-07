package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

/**
 * NAC 发件总成（旧真空/无线线路的输出端）。
 *
 * <p>真空网络已停用；方块与 16 格缓冲保留给旧存档和手动存取，不再向线路投递任何物品。
 * NAC 的输入输出只走样板总成。
 */
public class NanochipSenderAssemblyMachine extends NanochipPortPartMachine {

    public NanochipSenderAssemblyMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier, IO.OUT);
    }
}
