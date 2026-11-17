package com.gtlmodular.mixin.enhancedcore;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtlmodular.common.machine.fuel.ExtendHatchPredicate;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * 给增强版改写的结构追加超频仓 / 加速仓 —— 在 {@code replace(...)} <b>返回之后</b>。
 *
 * <h2>为什么必须在这个时机</h2>
 * <p>结构谓词在 {@code FactoryBlockPattern} 里一路被
 * {@code setMinGlobalLimited} / {@code setMaxGlobalLimited} / {@code setPreviewCount}
 * 链式处理，而这些方法会把<b>整个 limited 列表</b>（并把 common 全搬进去）
 * 逐项覆盖成同一个值。我们的仓若在那之前进入列表，就会被改写成
 * 「最少1个、最多2个」—— 实测预览里同时出现两套定义，结构里能放 2 台。
 *
 * <p>{@code replace(...)} 返回时，该结构的所有 setter 都已跑完，
 * 谓词表彻底定型，此时追加才是安全的。
 *
 * <h2>为什么不会触发 requiredParts 校验</h2>
 * <p>那条校验在 {@code replace(...)} <b>方法内部</b>、返回之前执行。
 * 本注入在 RETURN 处，校验已经跑完；而且只往 limited 里 <b>add</b> 新对象，
 * 不修改任何既有谓词，所以缓存里已建好的结构仍然通过它自己的校验。
 */
@Mixin(targets = "com.gtl.enhancedcore.common.structure.GtlMegastructurePatterns", remap = false)
public abstract class EnhancedcoreReplaceHatchMixin {

    private static final Field GTLMODULAR$MATCHES = gtlmodular$findMatches();

    private static Field gtlmodular$findMatches() {
        try {
            Field field = BlockPattern.class.getDeclaredField("blockMatches");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException missing) {
            return null;
        }
    }

    @Inject(method = "replace", at = @At("RETURN"), remap = false)
    private static void gtlmodular$appendGainHatches(ResourceLocation id, BlockPattern original,
                                                     CallbackInfoReturnable<BlockPattern> cir) {
        if (!ExtendHatchPredicate.shouldAttachFor(id)) {
            return;
        }
        Field field = GTLMODULAR$MATCHES;
        BlockPattern pattern = cir.getReturnValue();
        if (field == null || pattern == null) {
            return;
        }
        try {
            Object raw = field.get(pattern);
            if (!(raw instanceof TraceabilityPredicate[][][] matches)) {
                return;
            }
            gtlmodular$attachToPattern(matches);
        } catch (ReflectiveOperationException | RuntimeException failed) {
            // 结构构建是启动期一次性动作，追加失败不能拖垮整个注册流程。
        }
    }

    /**
     * 往结构里追加两种仓。
     *
     * <p>整台机器各只追加一次：找一个「已经允许放仓」的格子，把仓加进它的 limited。
     * 若结构里已经有（结构作者自己写的），则跳过。
     */
    private static void gtlmodular$attachToPattern(TraceabilityPredicate[][][] matches) {
        TraceabilityPredicate slot = null;
        boolean hasOverclock = false;
        boolean hasAccelerate = false;

        for (TraceabilityPredicate[][] aisle : matches) {
            for (TraceabilityPredicate[] row : aisle) {
                for (TraceabilityPredicate cell : row) {
                    if (cell == null || cell.isAir() || cell.isAny()) {
                        continue;
                    }
                    if (ExtendHatchPredicate.containsOverclock(cell)) {
                        hasOverclock = true;
                    }
                    if (ExtendHatchPredicate.containsAccelerate(cell)) {
                        hasAccelerate = true;
                    }
                    if (slot == null && !cell.limited.isEmpty()) {
                        // 优先挑已经有仓位谓词的格子（外壳上的仓室位）。
                        slot = cell;
                    }
                }
            }
        }
        if (slot == null) {
            return;
        }
        if (!hasOverclock) {
            ExtendHatchPredicate.attach(slot, ExtendHatchPredicate.OVERCLOCK_ABILITY);
        }
        if (!hasAccelerate) {
            ExtendHatchPredicate.attach(slot, ExtendHatchPredicate.ACCELERATE_ABILITY);
        }
    }

    /** 占位：仅为让 {@link SimplePredicate} 的 import 有意义（追加时会用到）。 */
    private static SimplePredicate gtlmodular$unused() {
        return null;
    }
}
