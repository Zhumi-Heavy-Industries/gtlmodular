package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.PredicateBlocks;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gtlmodular.common.data.fuel.AccelerateHatches;
import com.gtlmodular.common.data.fuel.OverclockHatches;
import com.gtlmodular.common.data.tentacle.ThreadHatchMachines;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * 增益仓（加速仓 / 超频仓 / 线程仓）的结构收尾：<b>整机最多 1 台、非必需</b>。
 *
 * <h2>为什么每个仓都只需要一个 maxCount</h2>
 * <p>{@code FactoryBlockPattern#where(char, predicate)} 把谓词存进 {@code symbolMap}，
 * {@code build()} 再把<b>同一个</b> {@link TraceabilityPredicate} 实例填进所有该符号的格子。
 * 所以同一符号的所有格共享同一批 {@link SimplePredicate} 对象 ——
 * 而结构计数（{@code MultiblockState#getGlobalCount()}）又是以 {@link SimplePredicate}
 * 为键的 ⇒ <b>一个 SimplePredicate 的上限天然就是整结构的配额</b>，不需要逐格处理。
 *
 * <p>反过来说：把某个 {@link SimplePredicate} 的 maxCount 归零会连带影响所有共享它的格子，
 * 所以本类<b>按对象身份</b>去重（{@link Collections#newSetFromMap} + IdentityHashMap），
 * 不按格子处理。
 *
 * <h2>绝不能改动的东西（改了会弄崩别的模组）</h2>
 * <p>{@code gtladditions} 用 {@code PatternPredicateSelector.matches(predicate)} 在结构构建后
 * 按谓词内容做补丁：它把 common + limited 里每一项的 candidates 摊平成方块集合再比对。
 * 因此本类：
 * <ul>
 *   <li>不删除列表里的任何 {@link SimplePredicate}；</li>
 *   <li>不替换结构数组里的谓词对象；</li>
 *   <li>只改 minCount / maxCount 两个数值，追加用 limited.add 就地补。</li>
 * </ul>
 *
 * <h2>为什么必须在结构构建之后</h2>
 * <p>{@code setMinGlobalLimited} / {@code setExactLimit} 会把整个 limited 列表
 * （并把 common 全部搬进来）逐项覆盖成同一值 ⇒ 增益仓会被设成「最少 1 台」。
 * 只有谓词表定型之后才能可靠复位。
 */
public final class HatchQuotaRepair {

    private static final int ACCELERATE = 0;
    private static final int OVERCLOCK = 1;
    private static final int THREAD = 2;

    private HatchQuotaRepair() {
    }

    private static boolean matchesKind(SimplePredicate simple, int kind) {
        return switch (kind) {
            case ACCELERATE -> isAccelerate(simple);
            case OVERCLOCK -> isOverclock(simple);
            case THREAD -> isThread(simple);
            default -> false;
        };
    }

    private static boolean isAccelerate(SimplePredicate simple) {
        return isSubsetOf(simple, Set.copyOf(AccelerateHatches.ACCELERATE_HATCH.getAllBlocks()));
    }

    private static boolean isOverclock(SimplePredicate simple) {
        return isSubsetOf(simple, Set.copyOf(OverclockHatches.OVERCLOCK_HATCH.getAllBlocks()));
    }

    private static boolean isThread(SimplePredicate simple) {
        return isSubsetOf(simple, Set.copyOf(ThreadHatchMachines.THREAD_HATCH.getAllBlocks()));
    }

    private static boolean isGainHatch(SimplePredicate simple) {
        return isAccelerate(simple) || isOverclock(simple) || isThread(simple);
    }

    private static boolean isSubsetOf(SimplePredicate simple, Set<Block> allowed) {
        if (!(simple instanceof PredicateBlocks blocks) || blocks.blocks == null || blocks.blocks.length == 0) {
            return false;
        }
        if (allowed.isEmpty()) {
            return false;
        }
        for (Block block : blocks.blocks) {
            if (!allowed.contains(block)) {
                // 混了别的方块 ⇒ 不是纯仓储谓词，不能动它。
                return false;
            }
        }
        return true;
    }

    /** 这一格能不能作为仓的可放位（空气格与 any 格都不行）。 */
    private static boolean usableSlot(TraceabilityPredicate predicate) {
        return predicate != null && !predicate.isAir() && !predicate.isAny();
    }

    /** 这一格是否含指定类型的仓。 */
    private static boolean containsKind(TraceabilityPredicate predicate, int kind) {
        if (predicate == null) {
            return false;
        }
        for (SimplePredicate simple : predicate.common) {
            if (matchesKind(simple, kind)) {
                return true;
            }
        }
        for (SimplePredicate simple : predicate.limited) {
            if (matchesKind(simple, kind)) {
                return true;
            }
        }
        return false;
    }

    /** 这一格是否含增益仓分支。 */
    private static boolean containsGainHatch(TraceabilityPredicate predicate) {
        if (predicate == null) {
            return false;
        }
        for (SimplePredicate simple : predicate.common) {
            if (isGainHatch(simple)) {
                return true;
            }
        }
        for (SimplePredicate simple : predicate.limited) {
            if (isGainHatch(simple)) {
                return true;
            }
        }
        return false;
    }

    /** 就地把一个仓的上限收成 1、下限放开（非必需）。 */
    private static void limitToOne(SimplePredicate simple) {
        simple.minCount = -1;
        simple.minLayerCount = -1;
        simple.maxCount = 1;
    }

    /** 就地放宽下限，不动上限。 */
    private static void relaxMinimum(SimplePredicate simple) {
        simple.minCount = -1;
        simple.minLayerCount = -1;
    }

    /** 就地追加一份加速仓分支（不新建/替换谓词对象）。 */
    private static void attachAccelerate(TraceabilityPredicate predicate, TraceabilityPredicate branch) {
        for (SimplePredicate simple : branch.limited) {
            limitToOne(simple);
            predicate.limited.add(simple);
        }
        for (SimplePredicate simple : branch.common) {
            limitToOne(simple);
            predicate.limited.add(simple);
        }
    }

    /**
     * 对整个结构做收尾修正。
     *
     * @param pattern       刚构建好的结构，可为 {@code null}
     * @param addAccelerate 是否允许给没有加速仓位的结构补一个
     */
    public static void normalizePattern(BlockPattern pattern, boolean addAccelerate) {
        if (ExtendHatchPredicate.inExtendBuild()) {
            // 增强版核心接管的机器：一个字节都不能改。
            // GtlMegastructurePatterns.replace(...) 会校验 requiredParts 的原始规则
            // （common.size()==1 且 limited 为空），并在缓存里复用同一个 BlockPattern，
            // 改过之后再被它或任何后续校验读到就会抛异常崩服。
            return;
        }
        TraceabilityPredicate[][][] matches = blockMatches(pattern);
        if (matches == null) {
            return;
        }

        // 同一符号的多格共享同一批 SimplePredicate ⇒ 先按对象身份去重收集。
        //
        // 但「共有几个对象」并不等于「能放几台」的答案：同一个仓可能在结构里
        // 被声明了多次（autoAbilities 会为每种能力各调一次 abilities(...)），
        // 产生 N 个互相独立的 SimplePredicate，各自 maxCount=1 ⇒ 整机仍能放 N 台。
        // 所以这里按仓的类型分组：每种仓只保留<b>一个</b>对象的上限 1，
        // 其余全部归零（保留对象本身，供 gtladditions 按内容匹配）。
        Set<SimplePredicate> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<SimplePredicate> accelerate = new ArrayList<>();
        List<SimplePredicate> overclock = new ArrayList<>();
        List<SimplePredicate> thread = new ArrayList<>();

        for (TraceabilityPredicate[][] aisle : matches) {
            for (TraceabilityPredicate[] row : aisle) {
                for (TraceabilityPredicate cell : row) {
                    if (cell == null) {
                        continue;
                    }
                    for (SimplePredicate simple : cell.limited) {
                        if (!isGainHatch(simple) || !seen.add(simple)) {
                            continue;
                        }
                        if (isAccelerate(simple)) {
                            accelerate.add(simple);
                        } else if (isOverclock(simple)) {
                            overclock.add(simple);
                        } else {
                            thread.add(simple);
                        }
                    }
                    for (SimplePredicate simple : cell.common) {
                        if (!isGainHatch(simple) || !seen.add(simple)) {
                            continue;
                        }
                        // common 项不参与上限计数，只保证「不是必需件」。
                        relaxMinimum(simple);
                        if (isAccelerate(simple)) {
                            accelerate.add(simple);
                        } else if (isOverclock(simple)) {
                            overclock.add(simple);
                        } else {
                            thread.add(simple);
                        }
                    }
                }
            }
        }

        // 三种仓统一口径：每种整机最多 1 台、非必需。
        boolean hasAccelerate = keepOnlyOne(accelerate);
        boolean hasOverclock = keepOnlyOne(overclock);
        keepOnlyOne(thread);

        // 兜底：结构作者一处都没写某种仓时，补一个位置（整机只补一格）。
        //
        // 口径：<b>能放其中一种，就要能放另一种</b>。
        // 实测：gtlcore 的 isa_mill 结构只声明了能量/物品/维护仓，
        // 两种增益仓都没有 —— 若只补加速仓，玩家就会发现「加速仓能放、超频仓不能放」。
        // 因此这里对两种仓分别判断、分别补。
        if (!addAccelerate) {
            return;
        }
        TraceabilityPredicate slot = firstWithKind(matches, OVERCLOCK);
        if (slot == null) {
            slot = firstWithKind(matches, ACCELERATE);
        }
        if (slot == null) {
            slot = firstUsableWithoutAnyGainHatch(matches);
        }
        if (slot == null) {
            return;
        }
        if (!hasAccelerate) {
            attachAccelerate(slot, AccelerateHatchPredicate.branch());
        }
        if (!hasOverclock) {
            attachAccelerate(slot, com.gtlmodular.common.data.fuel.OverclockHatches.branch());
        }
    }

    /** 找第一个含指定仓的可放格。 */
    private static TraceabilityPredicate firstWithKind(TraceabilityPredicate[][][] matches, int kind) {
        for (TraceabilityPredicate[][] aisle : matches) {
            for (TraceabilityPredicate[] row : aisle) {
                for (TraceabilityPredicate cell : row) {
                    if (usableSlot(cell) && containsKind(cell, kind)) {
                        return cell;
                    }
                }
            }
        }
        return null;
    }

    /** 找第一个没有任何增益仓的可放格（避免和别的仓抢位置）。 */
    private static TraceabilityPredicate firstUsableWithoutAnyGainHatch(TraceabilityPredicate[][][] matches) {
        for (TraceabilityPredicate[][] aisle : matches) {
            for (TraceabilityPredicate[] row : aisle) {
                for (TraceabilityPredicate cell : row) {
                    if (usableSlot(cell) && !containsGainHatch(cell)) {
                        return cell;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 一组同类仓谓词里，只让第一个可放、可预览，其余全部关掉。
     *
     * <p><b>必须同时把 previewCount 归零</b>：结构预览是按
     * {@code cacheGlobal.get(predicate) < previewCount} 逐<b>对象</b>计数的。
     * 只把 maxCount 设成 0 而 previewCount 仍是 1 时，那个对象依然会在预览里占一格
     * ⇒ 玩家看到「一堆加速仓」。这是预览重复的真正来源。
     *
     * <p>归零而不是删除：{@code gtladditions} 的补丁按列表内容匹配，删掉会让它匹配不上。
     *
     * @return 这组是否非空
     */
    private static boolean keepOnlyOne(List<SimplePredicate> sameKind) {
        boolean first = true;
        for (SimplePredicate simple : sameKind) {
            relaxMinimum(simple);
            if (first) {
                simple.maxCount = 1;
                // 保留可放的那一个在预览里显示；兜底补的分支由调用方决定是否展示。
                if (simple.previewCount < 0) {
                    simple.previewCount = 1;
                }
                first = false;
            } else {
                simple.maxCount = 0;
                simple.previewCount = 0;
            }
        }
        return !sameKind.isEmpty();
    }

    private static TraceabilityPredicate[][][] blockMatches(BlockPattern pattern) {
        if (pattern == null) {
            return null;
        }
        try {
            var field = BlockPattern.class.getDeclaredField("blockMatches");
            field.setAccessible(true);
            Object raw = field.get(pattern);
            return raw instanceof TraceabilityPredicate[][][] matches ? matches : null;
        } catch (ReflectiveOperationException | RuntimeException failed) {
            return null;
        }
    }
}
