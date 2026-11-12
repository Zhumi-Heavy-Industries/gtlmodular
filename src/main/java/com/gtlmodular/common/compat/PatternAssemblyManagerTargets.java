package com.gtlmodular.common.compat;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtlmodular.common.data.GTLModularMachines;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;

/**
 * GTL-Enhancedcore 的总成管理器只把它自己写死的机器 id 当总成
 * （{@code AssemblyManagerIndex.isTarget()} 里硬编码 {@code gtladditions:me_super_pattern_buffer}），
 * 猪咪样板总成因此不会出现在传送名单、橙金标记和快捷传送里。
 *
 * <p>这里按注册表判断哪些机器需要补进去：各档（MV~UIV）与超级猪咪。
 * <b>惰性猪咪不在名单内</b>——它的镜像来源那条链路 Enhancedcore 把具体类
 * {@code MEPatternBufferPartMachine} 写死进了集合/泛型，惰性机继承的是 Base，塞不进去。
 *
 * <p>只依赖注册表对象本身（identity 比较），不再多一份 id 字符串，注册 id 改了这里自动跟上。
 */
public final class PatternAssemblyManagerTargets {

    private PatternAssemblyManagerTargets() {
    }

    public static boolean isTarget(MetaMachine machine) {
        if (!(machine instanceof MEPatternBufferPartMachineBase)) {
            return false;
        }
        MachineDefinition definition = machine.getDefinition();
        if (definition == null) {
            return false;
        }
        if (definition == GTLModularMachines.SUPER_PATTERN_ASSEMBLY) {
            return true;
        }
        for (MachineDefinition tiered : GTLModularMachines.PATTERN_ASSEMBLY.values()) {
            if (tiered == definition) {
                return true;
            }
        }
        return false;
    }
}
