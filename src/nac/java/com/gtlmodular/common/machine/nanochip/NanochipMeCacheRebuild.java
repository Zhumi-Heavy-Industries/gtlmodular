package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.MeWiringBudget;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPatternRecipeHandlePart;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEFilterIOPartMachine;

import java.util.ArrayList;
import java.util.List;

/**
 * 「配方 → 槽」缓存重建（AE 推来料、机器却 0 候选 / 一直「等待开始加工」）。
 * <p>
 * gtlcore 的快速匹配用的是一张 {@code recipes2SlotsMap}，把「配方」映射到「哪个样板槽」；
 * 这张表只有一个重建口 {@code MEPatternRecipeHandlePart.restoreMachineCache}，而且只由
 * {@code IRecipeCapabilityMachine.upDate()} 那条链（结构成形 / 镜像重绑）触发。于是：
 * AE 把料推进样板槽、结构没变、机器空闲，机器就因为没有「配方→槽」条目枚举不到候选。
 * <p>
 * 判定必须看**结构里的 ME 样板部件**（{@code host.getParts()}），不能看已经登记好的接线表：
 * 接线表为空正是这个 bug 的一半，用它判空会永远不触发。动作顺序也照上游：
 * {@code upDate()} 先补接线 → 再对每个已登记部件 {@code restoreMachineCache} 补映射。
 * 限频/限次/证伪沿用 {@link MeWiringBudget}（连续两次读数一字未变即停手）。
 */
final class NanochipMeCacheRebuild {

    private final NanochipAssemblyComplexMachine host;

    /** null = 本轮卡住还没开始重建；开出一批或枚举到候选即丢弃。 */
    private MeWiringBudget budget;

    private int lastReading = -1;

    NanochipMeCacheRebuild(NanochipAssemblyComplexMachine host) {
        this.host = host;
    }

    /**
     * 每个服务端 tick、在候选搜索之前调用。
     *
     * @param lastRaw 上一轮真实搜索枚举到的候选数（0 = 什么都没枚举到）
     * @param idle    当前没有在跑批次
     * @return 本 tick 是否真的重建过
     */
    boolean maybeRebuild(int lastRaw, boolean idle) {
        if (!idle || lastRaw != 0) {
            budget = null;
            lastReading = -1;
            return false;
        }
        Level level = host.getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        List<IMEFilterIOPartMachine> structureParts = structureMeParts();
        if (structureParts.isEmpty()) {
            return false;
        }
        long now = level.getGameTime();
        if (budget == null) {
            budget = new MeWiringBudget();
        }
        if (budget.peek(now) != MeWiringBudget.Decision.ALLOWED) {
            return false;
        }
        int current = reading();
        if (lastReading >= 0) {
            budget.record(lastReading, current);
            if (budget.stopped()) {
                NanochipModContext.logger().info("[gtlmodular] NAC 配方→槽缓存重建停手 @{}：{}（已试 {} 次；"
                                + "样板槽里有料却一直 0 候选时，请把这条与「NAC 主机停工诊断」一起留存）",
                        host.getPos(), budget.stopReason(), budget.attempts());
                return false;
            }
        }
        lastReading = current;
        budget.start(now);
        try {
            IRecipeCapabilityMachine capability = (IRecipeCapabilityMachine) host;
            capability.upDate();
            int rebuilt = 0;
            for (MEPatternRecipeHandlePart part : registeredParts(capability)) {
                part.restoreMachineCache(capability::tryAddAndActiveRhp);
                rebuilt++;
            }
            int after = reading();
            NanochipModContext.logger().info("[gtlmodular] NAC 配方→槽缓存已重建 @{}：结构样板部件={}，"
                            + "已登记部件={}，读数 {}→{}（第 {} 次，余 {} 次）",
                    host.getPos(), structureParts.size(), rebuilt, current, after,
                    budget.attempts(), budget.remaining());
            return true;
        } catch (RuntimeException | LinkageError failure) {
            budget.fail("重建抛 " + failure.getClass().getSimpleName());
            NanochipModContext.logger().warn("[gtlmodular] NAC 配方→槽缓存重建失败 @{}：{}（已停手）",
                    host.getPos(), failure.toString());
            return false;
        }
    }

    /** 面板 / 诊断用的一句话读数。 */
    String describe() {
        if (budget == null) {
            return "未触发";
        }
        StringBuilder text = new StringBuilder(48);
        text.append(budget.attempts()).append('/').append(MeWiringBudget.MAX_ATTEMPTS);
        text.append(budget.stopped() ? "（已停手）" : "（余 " + budget.remaining() + " 次）");
        return text.toString();
    }

    /** 结构里真实存在的 ME 样板/输出部件（不看接线表）。 */
    private List<IMEFilterIOPartMachine> structureMeParts() {
        try {
            var parts = host.getParts();
            if (parts == null) {
                return List.of();
            }
            List<IMEFilterIOPartMachine> out = new ArrayList<>();
            for (var part : parts) {
                if (part instanceof IMEFilterIOPartMachine me) {
                    out.add(me);
                }
            }
            return out;
        } catch (RuntimeException | LinkageError failure) {
            return List.of();
        }
    }

    private static List<MEPatternRecipeHandlePart> registeredParts(IRecipeCapabilityMachine capability) {
        try {
            var parts = capability.getMEPatternRecipeHandleParts();
            return parts == null ? List.of() : parts;
        } catch (RuntimeException | LinkageError failure) {
            return List.of();
        }
    }

    /** 读数 = 已登记部件数 + 样板槽缓存配方数：重建前后不变说明这一招在本机无效。 */
    private int reading() {
        try {
            var parts = registeredParts((IRecipeCapabilityMachine) host);
            int cached = 0;
            for (MEPatternRecipeHandlePart part : parts) {
                try {
                    ObjectSet<com.gregtechceu.gtceu.api.recipe.GTRecipe> set = part.getCachedGTRecipe();
                    if (set != null) {
                        cached += set.size();
                    }
                } catch (RuntimeException | LinkageError ignored) {
                    // 读不到按 0 计，不影响重建本身。
                }
            }
            return parts.size() + cached;
        } catch (RuntimeException | LinkageError failure) {
            return -1;
        }
    }
}
