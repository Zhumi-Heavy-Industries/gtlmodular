package com.gtlmodular.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gtlmodular.common.machine.fuel.ExtendHatchPredicate;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Supplier;

/**
 * 在结构构建期间给 {@link ExtendHatchPredicate} 建立「当前是哪台机器」的凭据。
 *
 * <h2>为什么需要这一层</h2>
 * <p>线程仓 / 超频仓只应出现在<b>增强版核心的机器</b>上，而唯一能追加仓室位置的入口
 * {@code Predicates.abilities} 拿不到「正在为谁构建结构」。结构本身是惰性的
 * {@code Supplier<BlockPattern>}（首次 {@code getPattern()} / 预览时才 build），
 * 所以这里把供应商包一层：构建期间登记机器 id，构建结束立即清除。
 *
 * <h2>为什么包供应商而不是在 setPatternFactory 时判断</h2>
 * <p>增强版注册机器时就把结构与谓词一起写定了；我们的追加必须发生在
 * {@code FactoryBlockPattern#build} 真正解析谓词的那一刻，那时结构工厂正在执行。
 * 包一层供应商正好覆盖这个窗口，且对结构内容零修改。
 *
 * <h2>嵌套构建</h2>
 * <p>凭据用 {@link ExtendHatchPredicate} 内的栈管理，重复进入会正确嵌套与恢复，
 * 不会因为某台机器构建失败而把凭据永久污染给下一台。
 */
@Mixin(value = MultiblockMachineDefinition.class, remap = false)
public abstract class MultiblockDefinitionExtendHatchMixin {

    @ModifyVariable(method = "setPatternFactory", at = @At("HEAD"), argsOnly = true, remap = false)
    private Supplier<BlockPattern> gtlmodular$tagExtendHatchBuild(Supplier<BlockPattern> original) {
        if (original == null) {
            return null;
        }
        MultiblockMachineDefinition definition = (MultiblockMachineDefinition) (Object) this;
        ResourceLocation id = definition == null ? null : definition.getId();
        if (!ExtendHatchPredicate.isExtendMachine(id)) {
            // 不是增强版的机器：一个字节都不动，免得给无关机器多包一层供应商。
            return original;
        }
        return () -> ExtendHatchPredicate.buildWithCredential(id, original);
    }
}
