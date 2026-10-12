package com.gtlmodular.gametest;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipCalibration;
import com.gtlmodular.gametest.fixture.NacEnergyFixture;
import com.gtlmodular.gametest.fixture.NacInventoryFixture;
import com.gtlmodular.gametest.fixture.NacStructureFixture;
import com.gtlmodular.gametest.fixture.NacStructureLayout;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NacCalibrationTests {
    private static final List<NacStructureFixture> fixtures = new ArrayList<>();

    @AfterBatch(batch = "calibration_single")
    public static void cleanupSingle(ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "calibration_unlisted")
    public static void cleanupUnlisted(ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "calibration_cross")
    public static void cleanupCross(ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "calibration_parallel")
    public static void cleanupParallel(ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "calibration_mixed")
    public static void cleanupMixed(ServerLevel level) { cleanup(); }

    private static void cleanup() {
        fixtures.forEach(NacStructureFixture::releaseChunks);
        fixtures.clear();
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(test("single", 1, 0, false), test("unlisted", 0, 1, true),
                test("cross", 1, 1, false), test("parallel", 2, 3, false),
                test("mixed", 1, 1, true));
    }

    private static TestFunction test(String name, int crystals, int opticals, boolean unlisted) {
        return new TestFunction("calibration_" + name,
                NanochipModContext.modId() + ".nac_calibration_" + name,
                NacStructureLayout.templateId().toString(), 6000, 0, true,
                helper -> new Run(helper, crystals, opticals, unlisted).start());
    }

    private static final class Run {
        private final GameTestHelper helper;
        private final NacStructureFixture structure;
        private final int crystals;
        private final int opticals;
        private final boolean unlisted;
        private NacEnergyFixture energy;
        private ItemBusPartMachine input;
        private ItemBusPartMachine output;
        private final Map<String, Integer> sources = new LinkedHashMap<>();
        private GTRecipe batch;
        private boolean loggedBatch;
        private int expectedDuration;
        private long expectedEUt;

        Run(GameTestHelper helper, int crystals, int opticals, boolean unlisted) {
            this.helper = helper;
            this.crystals = crystals;
            this.opticals = opticals;
            this.unlisted = unlisted;
            structure = new NacStructureFixture(helper, false);
            fixtures.add(structure);
            structure.build();
        }

        void start() {
            helper.startSequence().thenWaitUntil(structure::assertConnected)
                    .thenExecute(this::prepare).thenWaitUntil(this::assertAdmitted)
                    .thenExecuteFor(40, () -> {
                        helper.assertTrue(structure.host().getCalibration().total() == 0,
                                "完成前校准提前增加");
                        helper.assertTrue(outputEmpty(), "断电时提前产出");
                        energy.assertSpent(0);
                    })
                    .thenExecute(() -> energy.deposit(640L * (crystals + opticals)))
                    .thenWaitUntil(this::assertDelivered)
                    .thenExecute(this::assertAccounting)
                    .thenExecuteFor(40, this::assertAccounting)
                    .thenExecute(structure::releaseChunks).thenSucceed();
        }

        void prepare() {
            energy = new NacEnergyFixture(helper, structure, true);
            input = NacInventoryFixture.part(structure.module(), GTMachines.ITEM_IMPORT_BUS[1].getId(),
                    ItemBusPartMachine.class);
            output = NacInventoryFixture.part(structure.module(), GTMachines.ITEM_EXPORT_BUS[1].getId(),
                    ItemBusPartMachine.class);
            helper.assertTrue(!NanochipModContext.config().isNanochipCalibrationUseFullWindow(),
                    "用例需要默认的已记录数量分母");
            helper.assertTrue(structure.host().getCalibration().total() == 0, "校准历史非空");
            long voltage = structure.module().getOverclockVoltage();
            helper.assertTrue(voltage >= 32, "测试供电上限低于原配方功率");
            long totalEu = 640L * (crystals + opticals);
            double ticks = totalEu / (double) voltage;
            expectedDuration = (int) Math.round(Math.max(20, ticks));
            expectedEUt = ticks > 20 ? voltage : totalEu / 20;
            add(0, "crystal", crystals);
            add(1, unlisted ? "unlisted" : "optical", opticals);
        }

        private String recipeId(String name) {
            return name.equals("unlisted") ? NanochipModContext.modId() + ":calibration/test_unlisted"
                    : NanochipModContext.modId() + ":nanochip/assembly_matrix/test_" + name;
        }

        private void add(int slot, String name, int count) {
            if (count == 0) return;
            var id = new ResourceLocation(recipeId(name));
            var raw = helper.getLevel().getRecipeManager().byKey(id).orElseThrow();
            helper.assertTrue(raw instanceof GTRecipe recipe && recipe.duration == 20
                    && RecipeHelper.getInputEUt(recipe) == 32, "原配方参数不符：" + id);
            helper.assertTrue(NanochipCalibration.whitelistMatches(id,
                    NanochipModContext.config().getNanochipCalibrationRecipeWhitelist()) != name.equals("unlisted"),
                    "测试配方白名单前提不符：" + id);
            helper.assertTrue(input.getInventory().insertItem(slot, stack(name, "input", count), false).isEmpty(),
                    "测试原料未全部进入真实仓室");
        }

        private ItemStack stack(String name, String kind, int count) {
            var id = new ResourceLocation("kubejs", "nac_calibration_" + name + "_" + kind);
            helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id), "未加载测试物品：" + id);
            return new ItemStack(ForgeRegistries.ITEMS.getValue(id), count);
        }

        void assertAdmitted() {
            var logic = structure.module().getRecipeLogic();
            helper.assertTrue(logic.getLastRecipe() != null && !logic.isIdle(), "等待实际合批");
            if (!loggedBatch) {
                LogUtils.getLogger().info("GameTest NAC calibration candidate: expectedInputs={}/{} actual={}*{} sources={} remaining={},{}",
                        crystals, opticals, RecipeHelper.getInputEUt(logic.getLastRecipe()), logic.getDuration(),
                        logic.getLastRecipe().data, input.getInventory().getStackInSlot(0), input.getInventory().getStackInSlot(1));
                loggedBatch = true;
            }
            helper.assertTrue(logic.getDuration() == expectedDuration
                    && RecipeHelper.getInputEUt(logic.getLastRecipe()) == expectedEUt,
                    "原料没有进入同一个真实批次：actual=" + RecipeHelper.getInputEUt(logic.getLastRecipe())
                            + "*" + logic.getDuration() + " data=" + logic.getLastRecipe().data
                            + " remaining=" + input.getInventory().getStackInSlot(0) + "/"
                            + input.getInventory().getStackInSlot(1));
            for (int i = 0; i < input.getInventory().getSlots(); i++) {
                helper.assertTrue(input.getInventory().getStackInSlot(i).isEmpty(), "合批未扣完全部原料");
            }
            batch = logic.getLastRecipe();
            var entries = batch.data.getList("gtlmodular:calibration_sources", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.getCompound(i);
                String id = entry.getString("id");
                helper.assertTrue(entry.getString("family").equals(id.endsWith("crystal") ? "CRYSTAL" : "OPTICAL"),
                        "实际来源族错误：" + entry);
                sources.merge(id, entry.getInt("amount"), Integer::sum);
            }
            LogUtils.getLogger().info("GameTest NAC calibration admitted: inputs={}/{} unlisted={} batch={}*{} sources={}",
                    crystals, opticals, unlisted, RecipeHelper.getInputEUt(batch), batch.duration, sources);
        }

        boolean outputEmpty() {
            for (int i = 0; i < output.getInventory().getSlots(); i++) {
                if (!output.getInventory().getStackInSlot(i).isEmpty()) return false;
            }
            return true;
        }

        void assertDelivered() {
            structure.assertConnected();
            helper.assertTrue(structure.module().getRecipeLogic().isIdle(), "等待交付完成");
            Map<String, Integer> actual = new LinkedHashMap<>();
            for (int i = 0; i < output.getInventory().getSlots(); i++) {
                var stack = output.getInventory().getStackInSlot(i);
                if (!stack.isEmpty()) actual.merge(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString(),
                        stack.getCount(), Integer::sum);
            }
            Map<String, Integer> expected = new LinkedHashMap<>();
            if (crystals > 0) expected.put("kubejs:nac_calibration_crystal_output", crystals * 2);
            if (opticals > 0) expected.put("kubejs:nac_calibration_" + (unlisted ? "unlisted" : "optical")
                    + "_output", opticals * 3);
            helper.assertTrue(actual.equals(expected), "实际产物数量不符：" + actual + " expected=" + expected);
            energy.assertSpent(640L * (crystals + opticals));
        }

        void assertAccounting() {
            assertDelivered();
            var cal = structure.host().getCalibration();
            LogUtils.getLogger().info("GameTest NAC calibration completed: inputs={}/{} unlisted={} sources={} crystal={} optical={} total={} EU={}",
                    crystals, opticals, unlisted, sources, cal.countOf(NanochipCalibration.Family.CRYSTAL),
                    cal.countOf(NanochipCalibration.Family.OPTICAL), cal.total(), energy.spent());
            helper.assertTrue(cal.countOf(NanochipCalibration.Family.CRYSTAL) == crystals * 2
                    && cal.countOf(NanochipCalibration.Family.OPTICAL) == (unlisted ? 0 : opticals * 3)
                    && cal.total() == crystals * 2 + (unlisted ? 0 : opticals * 3),
                    "真实合批校准数量不符：expected=" + (crystals * 2) + "/" + (unlisted ? 0 : opticals * 3)
                            + " actual=" + cal.countOf(NanochipCalibration.Family.CRYSTAL) + "/"
                            + cal.countOf(NanochipCalibration.Family.OPTICAL) + " sources=" + sources);
            Map<String, Integer> expectedSources = new LinkedHashMap<>();
            if (crystals > 0) expectedSources.put(recipeId("crystal"), crystals * 2);
            if (opticals > 0) expectedSources.put(recipeId(unlisted ? "unlisted" : "optical"), opticals * 3);
            helper.assertTrue(sources.equals(expectedSources), "合批来源数量不符：" + sources);
        }
    }
}
