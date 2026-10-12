package com.gtlmodular.common.machine.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** 改写只供 AE 查看，原样板和机器的存档字段仍由机器持有。 */
public final class PatternRewriteCache {

    private final BooleanSupplier foaMode;
    private final IntSupplier foaMultiplier;
    private final IntSupplier patternScale;
    private final IntSupplier patternDivScale;
    private final int minFoaMultiplier;
    private final int maxFoaMultiplier;
    private final int minScale;
    private final int maxScale;
    private List<IPatternDetails> rewrittenPatterns;
    // AE 推回的是改写实例；值相等的其他样板不能借用它的槽位映射。
    private final IdentityHashMap<IPatternDetails, IPatternDetails> rewrittenToOriginal = new IdentityHashMap<>();
    // 已构建但无改写结果时也要复用缓存，避免重复编解码整仓样板。
    private boolean built;

    public PatternRewriteCache(BooleanSupplier foaMode, IntSupplier foaMultiplier,
                               IntSupplier patternScale, IntSupplier patternDivScale,
                               int minFoaMultiplier, int maxFoaMultiplier, int minScale, int maxScale) {
        this.foaMode = foaMode;
        this.foaMultiplier = foaMultiplier;
        this.patternScale = patternScale;
        this.patternDivScale = patternDivScale;
        this.minFoaMultiplier = minFoaMultiplier;
        this.maxFoaMultiplier = maxFoaMultiplier;
        this.minScale = minScale;
        this.maxScale = maxScale;
    }

    public List<IPatternDetails> getAvailablePatterns(Supplier<List<IPatternDetails>> basePatterns, Runnable invalidate,
            Consumer<List<IPatternDetails>> logUnrewritten, Consumer<List<IPatternDetails>> rebuild) {
        if (built && rewrittenPatterns != null) {
            return rewrittenPatterns;
        }
        List<IPatternDetails> base = basePatterns.get();
        if (base.isEmpty() || !isRewriteEnabled()) {
            invalidate.run();
            logUnrewritten.accept(base);
            return base;
        }
        if (!built) {
            rebuild.accept(base);
            built = true;
        }
        return rewrittenPatterns == null || rewrittenPatterns.isEmpty() ? base : rewrittenPatterns;
    }

    public boolean isRewriteEnabled() {
        return foaMode.getAsBoolean() || patternScale.getAsInt() > 1 || patternDivScale.getAsInt() > 1;
    }

    public int effectiveMul() {
        int bothMul = foaMode.getAsBoolean()
                ? Math.max(minFoaMultiplier, Math.min(maxFoaMultiplier, foaMultiplier.getAsInt())) : 1;
        int sideMul = Math.max(minScale, Math.min(maxScale, patternScale.getAsInt()));
        return bothMul * sideMul;
    }

    public int effectiveDiv() {
        return Math.max(minScale, Math.min(maxScale, patternDivScale.getAsInt()));
    }

    public String describeEffectiveScale() {
        return "两侧×" + effectiveMul() + "÷" + effectiveDiv();
    }

    public void invalidate() {
        rewrittenPatterns = null;
        rewrittenToOriginal.clear();
        built = false;
    }

    public IPatternDetails originalPattern(IPatternDetails details) {
        return rewrittenToOriginal.getOrDefault(details, details);
    }

    public int rebuild(List<IPatternDetails> base, UnaryOperator<IPatternDetails> rewrite) {
        rewrittenToOriginal.clear();
        List<IPatternDetails> result = new ArrayList<>(base.size());
        int changedCount = 0;
        for (IPatternDetails details : base) {
            IPatternDetails rewritten = rewrite.apply(details);
            if (rewritten == null || rewritten == details) {
                result.add(details);
                continue;
            }
            result.add(rewritten);
            rewrittenToOriginal.put(rewritten, details);
            changedCount++;
        }
        rewrittenPatterns = changedCount > 0 ? result : null;
        return changedCount;
    }

    public IPatternDetails rewritePattern(IPatternDetails details, Supplier<Level> levelSupplier) {
        Level level = levelSupplier.get();
        if (level == null || !(details instanceof AEProcessingPattern pattern)) {
            return null;
        }
        int mul = effectiveMul();
        int div = effectiveDiv();
        if (mul == 1 && div == 1) {
            return null;
        }
        try {
            // 输入和输出须使用相同比例，才能让 AE 的声明量与机器实际加工量对应。
            GenericStack[] inputs = PatternStacks.scaleStacks(pattern.getSparseInputs(), mul, div);
            GenericStack[] outputs = PatternStacks.scaleStacks(pattern.getSparseOutputs(), mul, div);
            ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
            if (encoded == null || encoded.isEmpty()) {
                return null;
            }
            return PatternDetailsHelper.decodePattern(encoded, level);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
