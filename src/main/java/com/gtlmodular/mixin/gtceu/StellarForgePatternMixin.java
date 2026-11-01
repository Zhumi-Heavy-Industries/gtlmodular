package com.gtlmodular.mixin.gtceu;

import com.google.common.base.Suppliers;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.stellar.StellarForgeStructures;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Supplier;

/**
 * 恒星炎炀锻炉「换壳不换机」—— 在 {@link MultiblockMachineDefinition#setPatternFactory} 的入口把
 * <b>{@code gtceu:stellar_forge}</b> 的结构工厂替换成 GTO 版（65×65×15）。
 *
 * <h2>为什么用这个注入点</h2>
 * <p>玩家要求「和增强版核心一样修改结构」：本包装着 GTL-Enhancedcore 3.9.4，它的
 * {@code GtlMegastructurePatternMixin} 正是在<b>同一点位</b>（{@code MultiblockMachineDefinition#setPatternFactory}
 * 的 {@code @ModifyVariable HEAD argsOnly}）把第三方机器（gtlcore 等）的结构换成巨构。这里照同款点位做，
 * 效果一致：机器 id / 配方表 / 机器逻辑 / 等级判定全部不动，只换结构几何。
 *
 * <h2>为什么只认 {@code gtceu:stellar_forge}</h2>
 * <p>本包该 id 由 gtlcore 注册（{@code TierCasingMachine(holder, "SCTier")}，配方表
 * {@code gtceu:stellar_forge}）——它就是玩家说的「恒星炎炀锻炉」（gtlcore lang {@code block.gtceu.stellar_forge}）。
 * 其余 {@code gtceu:stellar_forge} 之外的机器一律原样返回。
 *
 * <h2>与增强版核心的注入顺序</h2>
 * <p>两者都是同一个 {@code @ModifyVariable} 的 handler，Mixin 会把它们串成链。无论谁先谁后结果都可用：
 * 我们这一环总是返回自己的结构工厂（忽略上游传进来的值）；若它在后面接管，会再对我们的结构做一次
 * 「内部实心格空洞化」，那是它对所有机器的统一处理，不影响形状。
 */
@Mixin(value = MultiblockMachineDefinition.class, remap = false)
public abstract class StellarForgePatternMixin {

    @ModifyVariable(method = "setPatternFactory", at = @At("HEAD"), argsOnly = true, remap = false)
    private Supplier<BlockPattern> gtlmodular$replaceStellarForgePattern(Supplier<BlockPattern> original) {
        MultiblockMachineDefinition definition = (MultiblockMachineDefinition) (Object) this;
        ResourceLocation id = definition.getId();
        if (id == null || !"gtceu".equals(id.getNamespace()) || !"stellar_forge".equals(id.getPath())) {
            return original;
        }
        GTLModular.LOGGER.info(
                "[gtlmodular] 恒星炎炀锻炉：结构工厂替换为 GTO 版（65×65×15，实心 10,503 格）");
        // 惰性构建：首次 getPattern()/预览时才真正解析方块（此时 KubeJS 方块与全部注册表已就绪）。
        return Suppliers.memoize(() -> StellarForgeStructures.build(definition));
    }
}
