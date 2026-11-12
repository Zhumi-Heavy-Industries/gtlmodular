// Ported from GT-Shanhai (https://github.com/dishanhai/gtl_shanhai-dishanhai),
// (c) 山海恒长在 (dishanhai), LGPL-3.0. Ported with the author's permission.
package com.gtlmodular.common.item.terminal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.item.terminal.ShanhaiTerminalAeBinding.Context;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ShanhaiStructureExecutor {

    /** 施工／拆解逐位置結果，供終端的施工報告界面列出。 */
    public enum Outcome { PLACED, REPLACED, SKIPPED, DISMANTLED }

    public record Detail(BlockPos pos, Outcome outcome, ItemStack stack, Component name) {}

    public record Result(boolean success, int changed, String message, List<Detail> details) {
        public Result(boolean success, int changed, String message) {
            this(success, changed, message, List.of());
        }
    }

    private static Detail detail(ShanhaiStructurePlan.Entry entry, Outcome outcome) {
        boolean existing = outcome == Outcome.DISMANTLED;
        ItemStack stack = (existing ? entry.current() : entry.desired()).copyWithCount(1);
        Fluid fluid = existing ? entry.currentFluid() : entry.desiredFluid();
        Component name = !stack.isEmpty() ? stack.getHoverName()
                : fluid != Fluids.EMPTY ? fluid.getFluidType().getDescription()
                : Component.translatable("message.gtlmodular.whale_terminal.manual_choice");
        return new Detail(entry.pos(), outcome, stack, name);
    }

    private static Outcome placedOutcome(ShanhaiStructurePlan.Entry entry) {
        return entry.kind() == ShanhaiStructurePlan.Kind.REPLACE
                || entry.kind() == ShanhaiStructurePlan.Kind.FORCE_REPLACE ? Outcome.REPLACED : Outcome.PLACED;
    }

    private final ShanhaiTerminalMaterialService materials;

    public ShanhaiStructureExecutor(ShanhaiTerminalMaterialService materials) {
        this.materials = materials;
    }

    public Result execute(ServerPlayer player, ShanhaiStructurePlan plan, Context ae) {
        if (player.getServer() == null) return new Result(false, 0, "服务端不可用");
        ServerLevel level = player.getServer().getLevel(plan.target().dimension());
        if (level == null || !level.hasChunkAt(plan.target().pos())) {
            return new Result(false, 0, "目标区块未加载");
        }
        Result heightFailure = validateBuildHeight(level, plan);
        if (heightFailure != null) return heightFailure;
        ShanhaiTerminalMaterialService.BuildBatch buildBatch = materials.prepareBuildBatch(player, ae, plan);

        Map<AEItemKey, Long> availablePreview = buildBatch.availableAmounts();
        Map<AEFluidKey, Long> availableFluidPreview = buildBatch.availableFluidAmounts();
        List<ItemStack> replacementReturns = new ArrayList<>();
        List<ItemStack> forcedReturnCandidates = new ArrayList<>();
        Map<AEFluidKey, Long> fluidReturns = new LinkedHashMap<>();
        for (ShanhaiStructurePlan.Entry entry : plan.entries()) {
            if (!entry.requiresBuild()) continue;
            if (entry.requiresFluid()) {
                if (!reservePreviewFluid(availableFluidPreview, entry.desiredFluid())) continue;
            } else if (!reservePreviewMaterial(availablePreview, entry.desired())) {
                continue;
            }
            if (entry.currentFluidAmount() > 0) {
                fluidReturns.merge(AEFluidKey.of(entry.currentFluid()),
                        entry.currentFluidAmount(), Long::sum);
            }
            if (entry.current().isEmpty()) continue;
            if (entry.kind() == ShanhaiStructurePlan.Kind.REPLACE) {
                replacementReturns.add(entry.current());
            } else if (entry.kind() == ShanhaiStructurePlan.Kind.FORCE_REPLACE) {
                forcedReturnCandidates.add(entry.current());
            }
        }
        for (Map.Entry<AEFluidKey, Long> fluidReturn : fluidReturns.entrySet()) {
            if (!materials.canRefundFluidForStructure(player, ae,
                    fluidReturn.getKey().getFluid(), fluidReturn.getValue())) {
                buildBatch.refundRemaining(player, ae);
                return new Result(false, 0, "旧流体无回收空间");
            }
        }
        if (!materials.canReturnAll(player, ae, replacementReturns)) {
            buildBatch.refundRemaining(player, ae);
            return new Result(false, 0, "旧部件无回收空间");
        }
        if (!materials.canStoreDismantled(player, ae, forcedReturnCandidates)) {
            buildBatch.refundRemaining(player, ae);
            return new Result(false, 0, "阻挡方块无回收空间");
        }

        int changed = 0;
        int skippedMissing = 0;
        List<Detail> details = new ArrayList<>();
        List<ShanhaiStructurePlan.Entry> changedEntries = new ArrayList<>();
        List<ItemStack> forcedReturns = new ArrayList<>();
        // ★ 2026-11-01 批量快路径开关：≥256 处施工（恒星炎炀锻炉换 4,346 块恒星热力容器就是这种）
        boolean bulkReplace = plan.entries().size() >= 256;
        int bulkChanged = 0;
        List<ItemStack> bulkReturns = new ArrayList<>();
        long bulkStartNs = System.nanoTime();
        for (ShanhaiStructurePlan.Entry entry : plan.entries()) {
            if (!entry.requiresBuild()) continue;
            Fluid reservedFluid = Fluids.EMPTY;
            ItemStack material = ItemStack.EMPTY;
            if (entry.requiresFluid()) {
                reservedFluid = entry.desiredFluid();
                if (!buildBatch.takeOneFluid(reservedFluid)) {
                    skippedMissing++;
                    details.add(detail(entry, Outcome.SKIPPED));
                    continue;
                }
            } else {
                material = buildBatch.takeOne(entry.desired());
            }
            if (!entry.requiresFluid() && material.isEmpty()) {
                skippedMissing++;
                details.add(detail(entry, Outcome.SKIPPED));
                continue;
            }
            // ★ 2026-11-01 大批量替换快路径（玩家实测：换恒星热力容器时服务器线程被按 33 s =
            //   "Can't keep up ... 33278 ms / 665 ticks"，UI 自然要等它跑完）：
            //   1) 普通方块（无 BlockEntity）的 REPLACE 不再「先 setBlock(空气) 再 BlockItem 放置」两趟，
            //      直接一次 setBlock 成目标状态 —— 世界更新减半，也绕开 BlockItem 的放置逻辑；
            //   2) 拆下来的旧方块不逐块退款（原来每块 1 次背包 36 格扫描 + 2 次 AE 网络调用），
            //      先攒进 bulkReturns，循环结束后一次性 storeDismantled 聚合入库。
            if (entry.kind() == ShanhaiStructurePlan.Kind.REPLACE
                    && !entry.desiredState().isAir()
                    && !(entry.desiredState().getBlock() instanceof net.minecraft.world.level.block.EntityBlock)) {
                ItemStack bulkRemoved = entry.current().isEmpty()
                        ? ItemStack.EMPTY : entry.current().copyWithCount(1);
                if (!level.setBlock(entry.pos(), entry.desiredState(), 3)) {
                    buildBatch.refundRemaining(player, ae);
                    return new Result(false, changed, "方块放置失败: " + entry.pos().toShortString(), details);
                }
                if (!bulkRemoved.isEmpty()) bulkReturns.add(bulkRemoved);
                changed++;
                bulkChanged++;
                changedEntries.add(entry);
                details.add(detail(entry, placedOutcome(entry)));
                continue;
            }
            ItemStack removed = ItemStack.EMPTY;
            Fluid removedFluid = Fluids.EMPTY;
            boolean forced = entry.kind() == ShanhaiStructurePlan.Kind.FORCE_REPLACE;
            if (entry.kind() == ShanhaiStructurePlan.Kind.REPLACE || forced) {
                removedFluid = entry.currentFluid();
                removed = removeExisting(level, entry);
            }
            if (!place(level, player, entry, material)) {
                restoreExisting(level, entry, removed, removedFluid);
                if (entry.requiresFluid()) {
                    restoreFluid(player, ae, reservedFluid);
                } else {
                    materials.refund(player, ae, material);
                }
                buildBatch.refundRemaining(player, ae);
                if (!materials.storeDismantled(player, ae, forcedReturns)) {
                    return new Result(false, changed, "方块放置失败且已替换方块回收失败", details);
                }
                return new Result(false, changed, "方块放置失败: " + entry.pos().toShortString(), details);
            }
            if (!removed.isEmpty()) {
                if (forced) {
                    forcedReturns.add(removed);
                } else if (!materials.refundDismantled(player, ae, removed)) {
                    buildBatch.refundRemaining(player, ae);
                    if (!materials.storeDismantled(player, ae, forcedReturns)) {
                        return new Result(false, changed + 1, "旧部件与阻挡方块回收失败", details);
                    }
                    return new Result(false, changed + 1, "旧部件回收失败", details);
                }
            }
            if (removedFluid != Fluids.EMPTY && !restoreFluid(player, ae, removedFluid, entry.currentFluidAmount())) {
                restoreExisting(level, entry, removed, removedFluid);
                if (entry.requiresFluid()) {
                    restoreFluid(player, ae, reservedFluid);
                } else {
                    materials.refund(player, ae, entry.desired().copyWithCount(1));
                }
                buildBatch.refundRemaining(player, ae);
                if (!materials.storeDismantled(player, ae, forcedReturns)) {
                    return new Result(false, changed, "旧流体与阻挡方块回收失败", details);
                }
                return new Result(false, changed, "旧流体回收失败", details);
            }
            changed++;
            changedEntries.add(entry);
            details.add(detail(entry, placedOutcome(entry)));
        }
        if (!bulkReturns.isEmpty()
                && !materials.storeDismantled(player, ae, bulkReturns)
                && !(ae != null && materials.storeDismantled(player, null, bulkReturns))) {
            return new Result(false, changed, "旧部件回收失败（批量）", details);
        }
        if (!materials.storeDismantled(player, ae, forcedReturns)) {
            return new Result(false, changed, "阻挡方块回收失败", details);
        }
        if (bulkReplace) {
            GTLModular.LOGGER.info(
                    "[gtlmodular][终端] 大批量施工 {} 项完成：总 {} ms（快路径替换 {} 项，批量退料 {} 件，跳过 {} 项）",
                    plan.entries().size(), (System.nanoTime() - bulkStartNs) / 1_000_000L,
                    bulkChanged, bulkReturns.size(), skippedMissing);
        }
        if (!buildBatch.refundRemaining(player, ae)) {
            return new Result(false, changed, "剩余材料回收失败", details);
        }
        if (changed == 0) return new Result(false, 0, "材料不足", details);
        orientPlacedBlocks(level, plan, changedEntries);
        String message = skippedMissing == 0 ? "施工完成"
                : "部分施工完成，缺材料跳过 " + skippedMissing + " 处";
        if (!forcedReturns.isEmpty()) message += "，回收 " + forcedReturns.size() + " 个阻挡方块";
        return new Result(true, changed, message, details);
    }

    public Result executeCreative(ServerPlayer player, ShanhaiStructurePlan plan, Context ae) {
        if (player.getServer() == null) return new Result(false, 0, "服务端不可用");
        ServerLevel level = player.getServer().getLevel(plan.target().dimension());
        if (level == null || !level.hasChunkAt(plan.target().pos())) {
            return new Result(false, 0, "目标区块未加载");
        }
        Result heightFailure = validateBuildHeight(level, plan);
        if (heightFailure != null) return heightFailure;

        int changed = 0;
        List<Detail> details = new ArrayList<>();
        List<ShanhaiStructurePlan.Entry> changedEntries = new ArrayList<>();
        List<ItemStack> forcedReturns = new ArrayList<>();
        for (ShanhaiStructurePlan.Entry entry : plan.entries()) {
            if (!entry.requiresBuild()) continue;
            ItemStack material = entry.desired().copyWithCount(1);
            ItemStack removed = ItemStack.EMPTY;
            Fluid removedFluid = Fluids.EMPTY;
            boolean forced = entry.kind() == ShanhaiStructurePlan.Kind.FORCE_REPLACE;
            boolean directReplace = entry.kind() == ShanhaiStructurePlan.Kind.REPLACE
                    && !entry.desiredState().isAir()
                    && !(entry.desiredState().getBlock() instanceof net.minecraft.world.level.block.EntityBlock);
            if (directReplace) {
                removed = entry.current().isEmpty() ? ItemStack.EMPTY : entry.current().copyWithCount(1);
                removedFluid = entry.currentFluid();
                if (!level.setBlock(entry.pos(), entry.desiredState(), 3)) {
                    storeCreativeForcedReturns(player, ae, forcedReturns);
                    return new Result(false, changed, "方块放置失败: " + entry.pos().toShortString(), details);
                }
            } else {
                if (entry.kind() == ShanhaiStructurePlan.Kind.REPLACE || forced) {
                    removedFluid = entry.currentFluid();
                    if (!materials.canRefundFluidForStructure(player, ae, removedFluid,
                            entry.currentFluidAmount())) {
                        storeCreativeForcedReturns(player, ae, forcedReturns);
                        return new Result(false, changed, "旧流体无回收空间", details);
                    }
                    removed = removeExisting(level, entry);
                }
                if (!place(level, player, entry, material)) {
                    restoreExisting(level, entry, removed, removedFluid);
                    storeCreativeForcedReturns(player, ae, forcedReturns);
                    return new Result(false, changed, "方块放置失败: " + entry.pos().toShortString(), details);
                }
            }
            if (forced && !removed.isEmpty()) {
                forcedReturns.add(removed);
            } else if (!removed.isEmpty() && !materials.refundDismantled(player, ae, removed)) {
                storeCreativeForcedReturns(player, ae, forcedReturns);
                return new Result(false, changed + 1, "替换旧方块无回收空间", details);
            }
            if (removedFluid != Fluids.EMPTY) restoreFluid(player, ae, removedFluid, entry.currentFluidAmount());
            changed++;
            changedEntries.add(entry);
            details.add(detail(entry, placedOutcome(entry)));
        }
        if (!storeCreativeForcedReturns(player, ae, forcedReturns)) {
            return new Result(false, changed, "创造模式施工完成，但阻挡方块回收失败", details);
        }
        orientPlacedBlocks(level, plan, changedEntries);
        return new Result(true, changed, forcedReturns.isEmpty()
                ? "创造模式施工完成"
                : "创造模式施工完成，回收 " + forcedReturns.size() + " 个阻挡方块", details);
    }

    private boolean storeCreativeForcedReturns(ServerPlayer player, Context ae, List<ItemStack> forcedReturns) {
        if (forcedReturns.isEmpty()) return true;
        if (materials.storeDismantled(player, ae, forcedReturns)) return true;
        return ae != null && materials.storeDismantled(player, null, forcedReturns);
    }

    public Result dismantle(ServerPlayer player, ShanhaiStructurePlan plan, Context ae) {
        if (player.getServer() == null) return new Result(false, 0, "服务端不可用");
        ServerLevel level = player.getServer().getLevel(plan.target().dimension());
        if (level == null || !level.hasChunkAt(plan.target().pos())) {
            return new Result(false, 0, "目标区块未加载");
        }
        List<ItemStack> dismantleReturns = plan.entries().stream()
                .filter(ShanhaiStructurePlan.Entry::canDismantle)
                .map(ShanhaiStructurePlan.Entry::current).toList();
        Map<AEFluidKey, Long> fluidReturns = new LinkedHashMap<>();
        for (ShanhaiStructurePlan.Entry entry : plan.entries()) {
            if (!entry.canDismantle() || entry.currentFluid() == Fluids.EMPTY) continue;
            fluidReturns.merge(AEFluidKey.of(entry.currentFluid()),
                    entry.currentFluidAmount(), Long::sum);
        }
        if (!materials.canStoreDismantled(player, ae, dismantleReturns, fluidReturns)) {
            return new Result(false, 0, "拆解物无回收空间");
        }
        int changed = 0;
        List<Detail> details = new ArrayList<>();
        List<ShanhaiStructurePlan.Entry> removedEntries = new ArrayList<>();
        List<ItemStack> removedStacks = new ArrayList<>();
        for (ShanhaiStructurePlan.Entry entry : plan.entries()) {
            if (!entry.canDismantle()) continue;
            ItemStack removed = removeExisting(level, entry);
            removedEntries.add(entry);
            removedStacks.add(removed);
            changed++;
            details.add(detail(entry, Outcome.DISMANTLED));
        }
        if (!materials.storeDismantled(player, ae, removedStacks, fluidReturns)) {
            for (int i = 0; i < removedEntries.size(); i++) {
                restoreExisting(level, removedEntries.get(i), removedStacks.get(i),
                        removedEntries.get(i).currentFluid());
            }
            return new Result(false, 0, "拆解物回收失败");
        }
        return new Result(true, changed, "拆解完成", details);
    }

    private Result validateBuildHeight(ServerLevel level, ShanhaiStructurePlan plan) {
        ShanhaiStructureBuildHeightValidator.Result result =
                ShanhaiStructureBuildHeightValidator.validateForGtceu(
                        plan, level.getMinBuildHeight(), level.getMaxBuildHeight());
        if (result.valid()) return null;
        if (level.getMaxBuildHeight() > result.maxBuildHeight() && result.upperLimitExceeded()) {
            return new Result(false, 0, "结构超过 GTCEu 多方块固定上限 Y"
                    + result.maxBuildY() + "，首个越界位置: "
                    + result.firstViolation().toShortString());
        }
        return new Result(false, 0, "结构超出世界高度范围 Y="
                + result.minBuildHeight() + ".." + result.maxBuildY()
                + "，首个越界位置: " + result.firstViolation().toShortString());
    }

    private static boolean reservePreviewMaterial(Map<AEItemKey, Long> available, ItemStack wanted) {
        AEItemKey key = AEItemKey.of(wanted);
        if (key == null) return false;
        Long amount = available.get(key);
        if (amount == null || amount <= 0) return false;
        if (amount == 1) {
            available.remove(key);
        } else {
            available.put(key, amount - 1);
        }
        return true;
    }

    private static boolean reservePreviewFluid(Map<AEFluidKey, Long> available, Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) return false;
        AEFluidKey key = AEFluidKey.of(fluid);
        Long amount = available.get(key);
        if (amount == null || amount < net.minecraftforge.fluids.FluidType.BUCKET_VOLUME) return false;
        long remaining = amount - net.minecraftforge.fluids.FluidType.BUCKET_VOLUME;
        if (remaining <= 0) {
            available.remove(key);
        } else {
            available.put(key, remaining);
        }
        return true;
    }

    private ItemStack removeExisting(ServerLevel level, ShanhaiStructurePlan.Entry entry) {
        level.setBlock(entry.pos(), Blocks.AIR.defaultBlockState(), 3);
        return entry.current().copy();
    }

    private void restoreExisting(ServerLevel level, ShanhaiStructurePlan.Entry entry, ItemStack removed,
                                 Fluid removedFluid) {
        if (removed.isEmpty() && (removedFluid == null || removedFluid == Fluids.EMPTY)) return;
        level.setBlock(entry.pos(), entry.currentState(), 3);
    }

    private boolean place(ServerLevel level, ServerPlayer player,
                          ShanhaiStructurePlan.Entry entry, ItemStack material) {
        if (entry.requiresFluid()) {
            return placeFluid(level, entry);
        }
        if (!(material.getItem() instanceof BlockItem blockItem)) return false;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(entry.pos()), Direction.UP, entry.pos(), false);
        BlockPlaceContext context = new BlockPlaceContext(
                level, player, InteractionHand.MAIN_HAND, material, hit);
        InteractionResult result = blockItem.place(context);
        return result.consumesAction() && !level.getBlockState(entry.pos()).isAir();
    }

    private boolean placeFluid(ServerLevel level, ShanhaiStructurePlan.Entry entry) {
        if (entry.desiredState().getFluidState().isEmpty()) return false;
        BlockState sourceState = entry.desiredFluid().defaultFluidState().createLegacyBlock();
        if (sourceState.getFluidState().isEmpty()) return false;
        if (!level.setBlock(entry.pos(), sourceState, 3)) return false;
        BlockState placed = level.getBlockState(entry.pos());
        if (placed.getFluidState().getType() == entry.desiredFluid()
                && placed.getFluidState().isSource()) return true;
        level.setBlock(entry.pos(), entry.currentState(), 3);
        return false;
    }

    private boolean restoreFluid(ServerPlayer player, Context ae, Fluid fluid) {
        return restoreFluid(player, ae, fluid, net.minecraftforge.fluids.FluidType.BUCKET_VOLUME);
    }

    private boolean restoreFluid(ServerPlayer player, Context ae, Fluid fluid, long amount) {
        if (fluid == null || fluid == Fluids.EMPTY || amount <= 0) return true;
        return materials.refundFluidForStructure(player, ae, fluid, amount);
    }

    private void orientPlacedBlocks(ServerLevel level, ShanhaiStructurePlan plan,
                                    List<ShanhaiStructurePlan.Entry> changedEntries) {
        MetaMachine controller = MetaMachine.getMachine(level, plan.target().pos());
        Direction preferred = controller == null ? Direction.NORTH : controller.getFrontFacing();
        for (ShanhaiStructurePlan.Entry entry : changedEntries) {
            BlockState state = level.getBlockState(entry.pos());
            // ★ 2026-11-01：机壳类（无 FACING 属性）不必查机器/邻居，省掉几千次 getMachine + getBlockState
            if (!state.hasProperty(BlockStateProperties.FACING)
                    && !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) continue;
            Direction found = null;
            Direction[] candidates = { preferred, Direction.NORTH, Direction.SOUTH,
                    Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN };
            MetaMachine placedMachine = MetaMachine.getMachine(level, entry.pos());
            for (Direction direction : candidates) {
                if (placedMachine != null && !placedMachine.isFacingValid(direction)) continue;
                if (level.getBlockState(entry.pos().relative(direction)).isAir()) {
                    found = direction;
                    break;
                }
            }
            if (found == null) found = preferred;
            if (state.hasProperty(BlockStateProperties.FACING)) {
                level.setBlock(entry.pos(), state.setValue(BlockStateProperties.FACING, found), 3);
            } else if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                Direction horizontal = found.getAxis().isHorizontal() ? found : preferred;
                if (!horizontal.getAxis().isHorizontal()) horizontal = Direction.NORTH;
                level.setBlock(entry.pos(), state.setValue(BlockStateProperties.HORIZONTAL_FACING, horizontal), 3);
            }
        }
    }
}
