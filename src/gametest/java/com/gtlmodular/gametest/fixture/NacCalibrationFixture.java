package com.gtlmodular.gametest.fixture;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipCalibration.Family;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** 历史只从真实交付建立，测量配方不进白名单，避免测量本身改变下一次校准。 */
public final class NacCalibrationFixture {
    private final GameTestHelper helper;
    private final NacStructureFixture structure;
    private final Family family;
    private final boolean special;
    private final Map<Family, Integer> counts = new EnumMap<>(Family.class);
    private NacEnergyFixture energy;
    private ItemBusPartMachine input;
    private ItemBusPartMachine output;
    private ResourceLocation recipeId;
    private Job job;
    private long cumulativeEU;
    private long previousSpent;
    private long eut;
    private int duration;
    private int paidTicks;
    private long firstPaidTick;
    private long lastPaidTick;
    private boolean observing;
    private GTRecipe batch;

    public NacCalibrationFixture(GameTestHelper helper, NacStructureFixture structure, Family family, boolean special) {
        this.helper = helper;
        this.structure = structure;
        this.family = family;
        this.special = special;
        helper.onEachTick(() -> {
            if (observing) observeTick();
        });
    }

    public void prepare() {
        energy = new NacEnergyFixture(helper, structure, false);
        input = NacInventoryFixture.part(structure.module(), GTMachines.ITEM_IMPORT_BUS[1].getId(), ItemBusPartMachine.class);
        output = NacInventoryFixture.part(structure.module(), GTMachines.ITEM_EXPORT_BUS[1].getId(), ItemBusPartMachine.class);
        helper.assertTrue(!NanochipModContext.config().isNanochipCalibrationUseFullWindow(), "必须使用默认已记录数量分母");
        helper.assertTrue(NanochipModContext.config().isNanochipSimpleLogistics(), "必须使用默认简单物流");
        helper.assertTrue(structure.module().getOverclockVoltage() == 2_097_152
                && structure.module().getEnergyContainer().getEnergyCapacity() == 134_217_728,
                "IV 256A 激光仓功率或容量不符");
        helper.assertTrue(!structure.module().getModuleSpec().hasUpkeep(), "矩阵维护耗材改变，需扩展流体断言");
        helper.assertTrue(structure.host().moduleEuMultiplier(family) == 1
                && structure.host().moduleDurationMultiplier(family) == 1, "存在额外主机模块倍率");
        assertHistory();
    }

    public void history(GameTestSequence sequence, Family produced, int amount) {
        if (amount > 0) append(sequence, new Job("history_" + key(produced) + "_" + amount, produced, amount, 20, true));
    }

    public void probe(GameTestSequence sequence, Family produced, int ticks) {
        append(sequence, new Job("probe_" + key(produced) + "_" + ticks, produced, 1, ticks, false));
    }

    private void append(GameTestSequence sequence, Job next) {
        sequence.thenExecute(() -> admit(next)).thenWaitUntil(this::assertAdmitted)
                .thenExecuteFor(3, () -> {
                    assertHistory();
                    energy.assertSpent(cumulativeEU);
                    helper.assertTrue(outputCount() == 0 && structure.module().getRecipeLogic().getProgress() == 0,
                            "缺电等待时提前交付或推进：" + job);
                }).thenExecute(() -> {
                    previousSpent = energy.spent();
                    paidTicks = 0;
                    observing = true;
                    energy.deposit(eut * duration);
                }).thenWaitUntil(this::assertDelivered).thenExecute(this::collect)
                .thenExecuteFor(3, this::assertIdle);
    }

    private void admit(Job next) {
        assertIdle();
        job = next;
        recipeId = new ResourceLocation(NanochipModContext.modId(),
                (job.history ? "nanochip/assembly_matrix/" : "calibration/") + "test_tier_" + job.name);
        var raw = helper.getLevel().getRecipeManager().byKey(recipeId).orElseThrow();
        helper.assertTrue(raw instanceof GTRecipe recipe && recipe.duration == job.ticks
                && RecipeHelper.getInputEUt(recipe) == (job.history ? 32 : 1_048_576)
                && recipe.getType() == structure.module().getRecipeType()
                && recipe.data.getString("circuit-calibration").equals(job.produced.name()), "原配方参数错误：" + recipeId);
        helper.assertTrue(com.gtlmodular.common.machine.nanochip.NanochipCalibration.whitelistMatches(recipeId,
                NanochipModContext.config().getNanochipCalibrationRecipeWhitelist()) == job.history,
                "历史/测量配方白名单前提错误：" + recipeId);
        int tier = expectedTier();
        double euFactor = !special && tier > 0 ? 0.8 : 1;
        double durationFactor = job.produced != family || tier == 0 ? 1
                : special ? new double[]{1, 0.8, 0.6, 0.5}[tier] : tier >= 2 ? 0.8 : 1;
        // 上游将总能量重新分配到仓室功率，并分别取整时长和最低 20 tick 下的功率。
        double total = (job.history ? 32.0 : 1_048_576.0) * job.ticks * euFactor * durationFactor;
        duration = (int) Math.round(Math.max(20, total / 2_097_152));
        eut = total > 2_097_152 * 20 ? 2_097_152 : (long) (total / 20);
        helper.assertTrue(input.getInventory().insertItem(0, stack("nac_tier_" + job.name + "_input"), false).isEmpty(),
                "测试原料未进入真实仓室");
    }

    private void assertAdmitted() {
        var logic = structure.module().getRecipeLogic();
        helper.assertTrue(!logic.isIdle() && logic.getLastRecipe() != null, "等待真实批次：" + job);
        batch = logic.getLastRecipe();
        helper.assertTrue(logic.getDuration() == duration && RecipeHelper.getInputEUt(batch) == eut,
                "校准后批次参数错误：" + job + " expected=" + eut + "*" + duration
                        + " actual=" + RecipeHelper.getInputEUt(batch) + "*" + logic.getDuration());
        assertInputsEmpty();
        var sources = batch.data.getList("gtlmodular:calibration_sources", Tag.TAG_COMPOUND);
        helper.assertTrue(sources.size() == 1 && sources.getCompound(0).getString("id").equals(recipeId.toString())
                && sources.getCompound(0).getString("family").equals(job.produced.name())
                && sources.getCompound(0).getInt("amount") == job.amount, "真实批次来源错误：" + sources);
    }

    private void observeTick() {
        long spent = energy.spent();
        long delta = spent - previousSpent;
        helper.assertTrue(delta == 0 || delta == eut, "单 tick 实际扣电错误：" + job + " delta=" + delta);
        if (delta > 0) {
            if (paidTicks == 0) firstPaidTick = helper.getLevel().getGameTime();
            lastPaidTick = helper.getLevel().getGameTime();
            paidTicks++;
        }
        previousSpent = spent;
        helper.assertTrue(paidTicks <= duration, "加工额外扣电：" + job);
        var logic = structure.module().getRecipeLogic();
        if (!logic.isIdle()) {
            helper.assertTrue(logic.getProgress() == paidTicks, "扣电次数与真实加工进度不符：" + job);
            if (outputCount() == 0) assertHistory();
        }
    }

    private void assertDelivered() {
        structure.assertConnected();
        helper.assertTrue(structure.module().getRecipeLogic().isIdle() && outputCount() == job.amount,
                "等待真实交付：" + job + " outputs=" + outputCount());
        // 游戏调度的等待时间不是有效加工进度；每次推进必须和实际扣款一一对应。
        helper.assertTrue(paidTicks == duration,
                "实际加工 tick 与批次时长不符：" + job + " paidTicks=" + paidTicks + " duration=" + duration);
        energy.assertSpent(cumulativeEU + eut * duration);
        assertInputsEmpty();
    }

    private void collect() {
        observing = false;
        cumulativeEU += eut * duration;
        if (job.history) counts.merge(job.produced, job.amount, Integer::sum);
        assertHistory();
        int removed = 0;
        for (int slot = 0; slot < output.getInventory().getSlots(); slot++) {
            removed += output.getInventory().extractItem(slot, Integer.MAX_VALUE, false).getCount();
        }
        helper.assertTrue(removed == job.amount, "取出真实产物数量错误：" + job);
        LogUtils.getLogger().info("GameTest NAC calibration measurement: active={} tier={} history={} recipe={} raw={}*{} actual={}*{} paidTicks={} elapsedServerTicks={} EU={} totalEU={}",
                family, expectedTier(), counts, recipeId, job.history ? 32 : 1_048_576, job.ticks, eut, duration,
                paidTicks, lastPaidTick - firstPaidTick + 1, eut * duration, cumulativeEU);
    }

    public void checkpoint(int primitive, int produced, int tier, double threshold, boolean reached) {
        assertHistory();
        helper.assertTrue(counts.getOrDefault(Family.PRIMITIVE, 0) == primitive
                && counts.getOrDefault(family, 0) == produced && expectedTier() == tier, "阈值测试参数错误");
        var calibration = structure.host().getCalibration();
        double percentage = produced / (double) (primitive + produced);
        helper.assertTrue(reached ? Math.abs(percentage - threshold) < 1e-12 : percentage < threshold,
                "未达到指定的阈值前/阈值点");
        helper.assertTrue(calibration.tierOf(family) == tier && calibration.activeTier() == tier
                && calibration.activeFamily() == (tier == 0 ? null : family), "真实历史的活动族/档位错误");
        LogUtils.getLogger().info("GameTest NAC calibration boundary: family={} primitive={} familyCount={} denominator={} threshold={} reached={} tier={}",
                family, primitive, produced, primitive + produced, threshold, reached, tier);
    }

    private int expectedTier() {
        int produced = counts.getOrDefault(family, 0);
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) return 0;
        double share = produced / (double) total;
        double[] thresholds = special ? new double[]{0.01, 0.05, 0.20} : new double[]{0.20, 0.40, 0.60};
        return share >= thresholds[2] ? 3 : share >= thresholds[1] ? 2 : share >= thresholds[0] ? 1 : 0;
    }

    private void assertHistory() {
        var cal = structure.host().getCalibration();
        int expectedTotal = counts.values().stream().mapToInt(Integer::intValue).sum();
        helper.assertTrue(cal.total() == expectedTotal && cal.denominator() == expectedTotal, "历史总数/默认分母不符：" + counts);
        for (Family value : Family.values()) {
            helper.assertTrue(cal.countOf(value) == counts.getOrDefault(value, 0), "校准族数量不符：" + value + " expected=" + counts);
        }
    }

    private void assertIdle() {
        structure.assertConnected();
        assertHistory();
        energy.assertSpent(cumulativeEU);
        assertInputsEmpty();
        helper.assertTrue(outputCount() == 0 && structure.module().getRecipeLogic().isIdle(), "取出后又交付或加工");
        Stream.concat(structure.host().getParts().stream(), structure.module().getParts().stream())
                .map(part -> part.self()).filter(FluidHatchPartMachine.class::isInstance)
                .map(FluidHatchPartMachine.class::cast).forEach(hatch -> {
                    for (int tank = 0; tank < hatch.tank.getTanks(); tank++) {
                        helper.assertTrue(hatch.tank.getFluidInTank(tank).isEmpty(), "无流体配方意外产生流体");
                    }
                });
    }

    private void assertInputsEmpty() {
        for (int slot = 0; slot < input.getInventory().getSlots(); slot++) {
            helper.assertTrue(input.getInventory().getStackInSlot(slot).isEmpty(), "原料未全部扣除");
        }
    }

    private int outputCount() {
        int count = 0;
        for (int slot = 0; slot < output.getInventory().getSlots(); slot++) {
            ItemStack stack = output.getInventory().getStackInSlot(slot);
            if (!stack.isEmpty()) {
                helper.assertTrue(job != null && stack.is(stack("nac_tier_" + key(job.produced) + "_output").getItem()),
                        "意外产物：" + stack);
                count += stack.getCount();
            }
        }
        return count;
    }

    private ItemStack stack(String path) {
        var id = new ResourceLocation("kubejs", path);
        helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id), "缺少测试物品：" + id);
        return new ItemStack(ForgeRegistries.ITEMS.getValue(id));
    }

    private static String key(Family family) { return family.name().toLowerCase(Locale.ROOT); }
    private record Job(String name, Family produced, int amount, int ticks, boolean history) {}
}
