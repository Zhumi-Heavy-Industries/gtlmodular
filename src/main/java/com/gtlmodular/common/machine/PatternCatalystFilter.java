package com.gtlmodular.common.machine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 「不消耗输入（催化剂）」过滤器：把催化剂从<b>报给 AE 的样板输入表</b>里摘掉。
 *
 * <h3>为什么需要它</h3>
 * <p>GT 的配方里有一类输入是<b>不消耗</b>的（{@code Content.chance == 0}，例如模具、催化剂床、
 * 各种「催化基底」）。AE 不知道这件事：它把样板里声明的每一个输入都当作<b>会消耗</b>，
 * 于是下单 N 批就要 N 份催化剂 —— 玩家明明有 1 个催化剂、机器也根本不会吃掉它，
 * AE 却会因为「第 2 批缺催化剂」把整单卡住。
 *
 * <p>本模组的「催化剂自动供给」在机器侧用 gtlcore 的虚拟原料（{@code GTLItems.VIRTUAL_INGREDIENT}）
 * 把这类输入变成「网络里有 1 个就够、不消耗」的<b>虚拟供给</b>。机器侧解决了，AE 侧还有一个坑：
 * 只要样板里仍声明着这个输入，AE 的合成计划就仍会按数量要它。所以这里给 AE 换一个「输入表里
 * 没有催化剂」的样板视图 —— 计划阶段直接不产生这笔需求。
 *
 * <h3>边界（宁可不摘，绝不摘错）</h3>
 * <ul>
 *   <li>只摘「样板实际输入键 == 催化剂键」的那一格（{@code getPossibleInputs()} 里的键逐个严格比对，
 *       不走 {@code isValid} 的模糊匹配）—— AE 加工样板是精确物品/流体，不会出现标签多选；</li>
 *   <li>一旦有一格摘不掉（键对不上），整张样板<b>原样返回</b>，不返回半成品视图；</li>
 *   <li>{@link #getOutputs()}、{@link #getDefinition()}、{@code equals}/{@code hashCode} 全部委托原样板
 *       —— gtlcore 的 {@code patternSlotMap} 以样板对象为键，AE 推回来的样板也要能找回原槽位；</li>
 *   <li>{@link #pushInputsToExternalInventory} 会把「按过滤后下标排列」的输入还原成「按原样板下标排列」
 *       再交给原样板，避免外部投料把顺序错位。</li>
 * </ul>
 */
public final class PatternCatalystFilter implements IPatternDetails {

    private final IPatternDetails delegate;

    /** 过滤后的输入表（与 {@link #keptIndices} 一一对应）。 */
    private final IInput[] filteredInputs;

    /** 过滤后第 i 格在原样板里的下标。{@code null} = 一格都没摘（这种情况下不会构造本对象）。 */
    @Nullable
    private final int[] keptIndices;

    private PatternCatalystFilter(IPatternDetails delegate, IInput[] filteredInputs, @Nullable int[] keptIndices) {
        this.delegate = delegate;
        this.filteredInputs = filteredInputs;
        this.keptIndices = keptIndices;
    }

    /**
     * 构造过滤视图。
     *
     * @param catalystKeys 该样板所在槽位对应的「不消耗输入」键集合（空/全不匹配 ⇒ 原样返回）
     * @return 过滤后的样板；什么都不用摘时<b>返回原样板本身</b>（调用方可以按引用判断有没有变化）
     */
    public static IPatternDetails wrap(IPatternDetails delegate, Set<AEKey> catalystKeys, Level level) {
        if (delegate == null || catalystKeys == null || catalystKeys.isEmpty()) {
            return delegate;
        }
        IInput[] originals;
        try {
            originals = delegate.getInputs();
        } catch (Throwable ignored) {
            return delegate;
        }
        if (originals == null || originals.length == 0) {
            return delegate;
        }
        List<IInput> kept = new ArrayList<>(originals.length);
        List<Integer> keptIndexList = new ArrayList<>(originals.length);
        for (int i = 0; i < originals.length; i++) {
            IInput input = originals[i];
            if (input != null && isCatalystInput(input, catalystKeys, level)) {
                continue;   // 催化剂：不报给 AE
            }
            kept.add(input);
            keptIndexList.add(i);
        }
        if (kept.size() == originals.length) {
            return delegate;   // 一格都没摘
        }
        int[] indices = new int[keptIndexList.size()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = keptIndexList.get(i);
        }
        return new PatternCatalystFilter(delegate, kept.toArray(IInput[]::new), indices);
    }

    /**
     * 这一格输入是不是「催化剂」。
     *
     * <p>判据是<b>严格相等</b>：该格声明的可能输入里必须有一项与催化剂键完全相等。
     * AE 加工样板一格通常只有一个精确键；若一格声明了多个可能项（例如被展开过的标签），
     * 只有当<b>全部</b>可能项都在催化剂集合里时才算催化剂 —— 否则摘掉它可能与某条消耗输入冲突。
     */
    private static boolean isCatalystInput(IInput input, Set<AEKey> catalystKeys, Level level) {
        try {
            GenericStack[] possible = input.getPossibleInputs();
            if (possible == null || possible.length == 0) {
                return false;
            }
            boolean allCatalyst = true;
            for (GenericStack stack : possible) {
                if (stack == null || stack.what() == null || !catalystKeys.contains(stack.what())) {
                    allCatalyst = false;
                    break;
                }
            }
            if (allCatalyst) {
                return true;
            }
            // 单键格：常见情况（精确物品/流体样板）
            return possible.length == 1 && possible[0] != null && possible[0].what() != null
                    && catalystKeys.contains(possible[0].what());
        } catch (Throwable ignored) {
            return false;   // 解不出来 ⇒ 当普通输入（宁可不摘）
        }
    }

    /** 拆回原样板（机器侧推料/找槽位时必须用原样板）。 */
    public IPatternDetails unwrap() {
        return this.delegate;
    }

    @Override
    public appeng.api.stacks.AEItemKey getDefinition() {
        return this.delegate.getDefinition();
    }

    @Override
    public IInput[] getInputs() {
        return this.filteredInputs;
    }

    @Override
    public GenericStack[] getOutputs() {
        return this.delegate.getOutputs();
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return this.delegate.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        int[] indices = this.keptIndices;
        if (indices == null) {
            this.delegate.pushInputsToExternalInventory(inputHolder, inputSink);
            return;
        }
        // AE 给的 inputHolder 是按「过滤后的输入表」排列的；原样板要的是「按原输入表」排列。
        // 这里做一次下标还原（其余格留空 KeyCounter），顺序错了会把料投到错误的输入格。
        IInput[] originals = this.delegate.getInputs();
        KeyCounter[] expanded = new KeyCounter[originals == null ? indices.length : originals.length];
        for (int i = 0; i < expanded.length; i++) {
            expanded[i] = new KeyCounter();
        }
        for (int i = 0; i < indices.length && i < inputHolder.length; i++) {
            KeyCounter source = inputHolder[i];
            if (source == null) {
                continue;
            }
            KeyCounter target = expanded[indices[i]];
            for (AEKey key : source.keySet()) {
                long amount = source.get(key);
                if (amount > 0L) {
                    target.add(key, amount);
                }
            }
        }
        this.delegate.pushInputsToExternalInventory(expanded, inputSink);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof IPatternDetails other)) {
            return false;
        }
        IPatternDetails target = obj instanceof PatternCatalystFilter filter ? filter.delegate : other;
        return this.delegate.equals(target);
    }

    @Override
    public int hashCode() {
        return this.delegate.hashCode();
    }

    @Override
    public String toString() {
        return "PatternCatalystFilter[" + this.delegate + "]";
    }
}
