// Ported from GT-Shanhai (https://github.com/dishanhai/gtl_shanhai-dishanhai),
// (c) 山海恒长在 (dishanhai), LGPL-3.0. Ported with the author's permission.
package com.gtlmodular.common.item.terminal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.gtlmodular.common.item.ShanhaiUltimateTerminalConfig;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.Lazy;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.common.block.BlockMap;

public final class ShanhaiStructurePlanner {

    @FunctionalInterface
    public interface CandidatePriority {
        int priority(ItemStack candidate);
    }

    private record Candidate(ItemStack item, BlockState state) {
        private Candidate {
            item = item == null ? ItemStack.EMPTY : item.copyWithCount(1);
            state = state == null ? Blocks.AIR.defaultBlockState() : state;
        }
    }
    private static final Candidate EMPTY_CANDIDATE =
            new Candidate(ItemStack.EMPTY, Blocks.AIR.defaultBlockState());

    private ShanhaiStructurePlanner() {}

    public static ShanhaiStructurePlan scan(IMultiController controller, ItemStack terminal) {
        // 普通结构查看不需要仓室/替换候选的优先级排序：省掉对每个槽位的候选列表 sort。
        return scanInternal(controller, terminal, null, false);
    }

    /** 扫描报告路径：非仓室候选不会带进 plan（reportPlan 对这些候选不读取），减少 29k 级 ItemStack 复制。 */
    public static ShanhaiStructurePlan scanForReport(IMultiController controller, ItemStack terminal) {
        return scanInternal(controller, terminal, null, true);
    }

    public static ShanhaiStructurePlan scan(IMultiController controller, ItemStack terminal,
                                            CandidatePriority priority) {
        return scanInternal(controller, terminal, priority, false);
    }

    private static ShanhaiStructurePlan scanInternal(IMultiController controller, ItemStack terminal,
                                                       CandidatePriority priority, boolean reportOnly) {
        Level level = controller.self().getLevel();
        boolean mirrored = ShanhaiUltimateTerminalConfig.isMirrored(terminal);
        boolean noChambers = ShanhaiUltimateTerminalConfig.isNoChamberMode(terminal);
        boolean replaceMode = ShanhaiUltimateTerminalConfig.isReplaceMode(terminal);
        boolean absoluteReplace = ShanhaiUltimateTerminalConfig.isAbsoluteReplaceMode(terminal);
        int repeatCount = ShanhaiUltimateTerminalConfig.getRepeatCount(terminal);
        Block[] replacementFamily = replacementFamily(terminal);
        Block replacementTarget = replacementTarget(terminal, replacementFamily);
        Candidate replacementCandidate = replacementTarget == null ? EMPTY_CANDIDATE
                : new Candidate(replacementTarget.asItem().getDefaultInstance(), replacementTarget.defaultBlockState());
        CandidateCache candidateCache = new CandidateCache();
        List<ShanhaiStructurePatternAdapter.Slot> slots =
                ShanhaiStructurePatternAdapter.map(controller, repeatCount, mirrored);
        List<ShanhaiStructurePlan.Entry> entries = new ArrayList<>(slots.size());
        BlockPos controllerPos = controller.self().getPos();
        for (ShanhaiStructurePatternAdapter.Slot slot : slots) {
            if (slot.pos().equals(controllerPos)) continue;
            PreparedCandidates prepared = candidateCache.prepared(slot.predicate(), priority);
            entries.add(classify(level, slot.pos(), prepared,
                    replacementFamily, replacementTarget, replacementCandidate, replaceMode, absoluteReplace,
                    noChambers, reportOnly));
        }
        Block controllerBlock = controller.self().getBlockState().getBlock();
        var machineId = ForgeRegistries.BLOCKS.getKey(controllerBlock);
        return new ShanhaiStructurePlan(
                GlobalPos.of(level.dimension(), controllerPos),
                machineId == null ? "" : machineId.toString(), mirrored, repeatCount, entries);
    }

    private static ShanhaiStructurePlan.Entry classify(Level level, BlockPos pos,
                                                         PreparedCandidates prepared,
                                                         Block[] replacementFamily,
                                                         Block replacementTarget,
                                                         Candidate replacementCandidate,
                                                         boolean replaceMode,
                                                         boolean absoluteReplace,
                                                         boolean noChambers,
                                                         boolean reportOnly) {
        List<Candidate> allCandidates = prepared.all();
        List<Candidate> ordinaryCandidates = prepared.ordinary();
        boolean chamberCapable = prepared.chamberCapable();
        List<ItemStack> candidateItems = reportOnly && !chamberCapable ? List.of() : prepared.items();

        BlockState currentState = level.getBlockState(pos);
        // Report-only scan never consumes current ItemStacks; do not allocate one for every slot.
        ItemStack current = reportOnly || currentState.isAir()
                ? ItemStack.EMPTY : currentState.getBlock().asItem().getDefaultInstance();
        boolean replacementApplies = false;
        if (replaceMode && replacementTarget != null) {
            for (Candidate candidate : ordinaryCandidates) {
                if (contains(replacementFamily, candidate.state().getBlock())) {
                    replacementApplies = true;
                    break;
                }
            }
        }

        Candidate desiredCandidate;
        if (replacementApplies) {
            desiredCandidate = replacementCandidate;
        } else if (ordinaryCandidates.isEmpty()) {
            desiredCandidate = EMPTY_CANDIDATE;
        } else {
            desiredCandidate = ordinaryCandidates.get(0);
        }
        ItemStack desired = desiredCandidate.item();
        BlockState desiredState = desiredCandidate.state();
        boolean hasBuildTarget = !desired.isEmpty() || desiredState.getBlock() instanceof LiquidBlock;
        Block currentBlock = currentState.getBlock();
        boolean currentAllowed = false;
        for (Candidate candidate : allCandidates) {
            if (candidate.state().getBlock() == currentBlock) {
                currentAllowed = true;
                break;
            }
        }
        currentAllowed &= !(currentBlock instanceof LiquidBlock) || currentState.getFluidState().isSource();
        boolean currentMatchesStructure = currentAllowed
                || (replacementApplies && contains(replacementFamily, currentBlock));
        boolean fillingFlow = currentBlock instanceof LiquidBlock
                && currentBlock == desiredState.getBlock() && !currentState.getFluidState().isSource();

        ShanhaiStructurePlan.Kind kind;
        if (replacementApplies && contains(replacementFamily, currentBlock)
                && currentBlock != replacementTarget) {
            kind = ShanhaiStructurePlan.Kind.REPLACE;
        } else if (currentAllowed) {
            kind = ShanhaiStructurePlan.Kind.SATISFIED;
        } else if (currentState.isAir() || fillingFlow) {
            kind = hasBuildTarget ? ShanhaiStructurePlan.Kind.PLACE : ShanhaiStructurePlan.Kind.MANUAL;
        } else {
            kind = absoluteReplace && hasBuildTarget
                    ? ShanhaiStructurePlan.Kind.FORCE_REPLACE
                    : ShanhaiStructurePlan.Kind.BLOCKED;
        }

        if ((!noChambers && chamberCapable)
                || (chamberCapable && kind == ShanhaiStructurePlan.Kind.SATISFIED)) {
            kind = ShanhaiStructurePlan.Kind.CHAMBER_HINT;
        }
        // Scan report only reads candidates for chamber positions. Avoid copying every ordinary slot's
        // candidate stacks into 29k plan entries; this was a major transient-allocation source.
        if (reportOnly && !chamberCapable) candidateItems = List.of();
        return new ShanhaiStructurePlan.Entry(pos, kind, desired, desiredState,
                current, currentState, candidateItems, chamberCapable, currentMatchesStructure);
    }

    private record PreparedCandidates(List<Candidate> all, List<Candidate> ordinary,
                                      List<ItemStack> items, boolean chamberCapable) {}

    private static List<Candidate> readCandidates(SimplePredicate simple) {
        if (simple.candidates == null) return Collections.emptyList();
        BlockInfo[] infos = simple.candidates.get();
        if (infos == null || infos.length == 0) return Collections.emptyList();
        List<Candidate> result = new ArrayList<>(infos.length);
        for (BlockInfo info : infos) {
            BlockState state = info.getBlockState();
            ItemStack stack = itemStackFor(info, state);
            if (stack.isEmpty() && state.getBlock() == Blocks.AIR) continue;
            result.add(new Candidate(stack, state));
        }
        return Collections.unmodifiableList(result);
    }

    private static ItemStack itemStackFor(BlockInfo info, BlockState state) {
        if (state != null && state.getBlock() instanceof LiquidBlock liquidBlock) {
            return liquidBlock.getFluid().getBucket().getDefaultInstance();
        }
        ItemStack stack = info.getItemStackForm();
        return stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    private static final class CandidateCache {
        private static final int MAX_PREPARED = 512;
        private final Map<SimplePredicate, List<Candidate>> byPredicate = new IdentityHashMap<>();
        private final Map<TraceabilityPredicate, PreparedCandidates> byPattern = new IdentityHashMap<>();

        private List<Candidate> get(SimplePredicate simple) {
            return byPredicate.computeIfAbsent(simple, ShanhaiStructurePlanner::readCandidates);
        }

        private PreparedCandidates prepared(TraceabilityPredicate predicate, CandidatePriority priority) {
            PreparedCandidates cached = byPattern.get(predicate);
            if (cached != null) return cached;
            List<Candidate> all = new ArrayList<>();
            for (SimplePredicate simple : predicate.limited) all.addAll(get(simple));
            for (SimplePredicate simple : predicate.common) all.addAll(get(simple));
            List<Candidate> ordinary = new ArrayList<>(all.size());
            List<ItemStack> items = new ArrayList<>(all.size());
            boolean chamber = false;
            for (Candidate candidate : all) {
                Block block = candidate.state().getBlock();
                if (ShanhaiChamberClassifier.isChamberBlock(block)) chamber = true;
                else if (block != Blocks.AIR) ordinary.add(candidate);
                if (!candidate.item().isEmpty()) items.add(candidate.item());
            }
            if (priority != null && ordinary.size() > 1) {
                ordinary.sort(Comparator.comparingInt(candidate -> priority.priority(candidate.item())));
            }
            PreparedCandidates result = new PreparedCandidates(List.copyOf(all), List.copyOf(ordinary),
                    items.isEmpty() ? List.of() : List.copyOf(items), chamber);
            // Some patterns share predicates across many positions. Bound the cache so a pathological
            // generated pattern with unique predicates cannot pin the whole scan in memory.
            if (byPattern.size() >= MAX_PREPARED) byPattern.clear();
            byPattern.put(predicate, result);
            return result;
        }
    }

    private static Block[] replacementFamily(ItemStack terminal) {
        if (!ShanhaiUltimateTerminalConfig.isReplaceMode(terminal)) return new Block[0];
        String family = ShanhaiUltimateTerminalConfig.getReplacementFamily(terminal);
        Lazy<Block[]> lazy = BlockMap.tierBlockMap.get(family);
        return lazy == null ? new Block[0] : lazy.get();
    }

    private static Block replacementTarget(ItemStack terminal, Block[] family) {
        if (family.length == 0) return null;
        String familyName = ShanhaiUltimateTerminalConfig.getReplacementFamily(terminal);
        int tier = Math.min(ShanhaiUltimateTerminalConfig.getReplacementTier(terminal, familyName),
                family.length - 1);
        return family[tier];
    }

    private static boolean contains(Block[] blocks, Block block) {
        for (Block candidate : blocks) if (candidate == block) return true;
        return false;
    }

}
