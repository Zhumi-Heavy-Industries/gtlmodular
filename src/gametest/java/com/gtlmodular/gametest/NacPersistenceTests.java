package com.gtlmodular.gametest;

import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipCalibration.Family;
import com.gtlmodular.gametest.fixture.NacPersistenceFixture;
import com.gtlmodular.gametest.fixture.NacStructureFixture;
import com.gtlmodular.gametest.fixture.NacStructureLayout;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NacPersistenceTests {
    private static final List<NacStructureFixture> fixtures = new ArrayList<>();

    @AfterBatch(batch = "persistence_host")
    public static void cleanupHost(ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "persistence_module")
    public static void cleanupModule(ServerLevel level) { cleanup(); }

    private static void cleanup() {
        fixtures.forEach(NacStructureFixture::releaseChunks);
        fixtures.clear();
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(test(true), test(false));
    }

    private static TestFunction test(boolean host) {
        String name = "persistence_" + (host ? "host" : "module");
        return new TestFunction(name, NanochipModContext.modId() + ".nac_" + name,
                NacStructureLayout.templateId().toString(), host ? 12000 : 6000, 0, true,
                helper -> new Run(helper, host).start());
    }

    private static final class Run {
        private final GameTestHelper helper;
        private final boolean host;
        private final NacStructureFixture structure;
        private NacPersistenceFixture fixture;
        private long totalEU;

        private Run(GameTestHelper helper, boolean host) {
            this.helper = helper;
            this.host = host;
            structure = new NacStructureFixture(helper, false);
            fixtures.add(structure);
            structure.build();
        }

        private void start() {
            helper.startSequence().thenWaitUntil(structure::assertConnected)
                    .thenExecute(() -> {
                        fixture = new NacPersistenceFixture(helper, structure);
                        fixture.input(false, 0, "nac_tier_history_primitive_12_input");
                        fixture.deposit(640);
                    }).thenWaitUntil(() -> assertHistory(12, 0, 0, 640))
                    .thenExecute(() -> {
                        fixture.input(false, 0, "nac_calibration_crystal_input");
                        fixture.deposit(640);
                    }).thenWaitUntil(() -> assertHistory(12, 2, 0, 1280))
                    .thenExecute(this::begin).thenWaitUntil(this::assertPaused)
                    .thenExecute(() -> {
                        // 缺电等待沿用上游进度衰减；存档用例预留足量续跑电，避免混入断电规则。
                        fixture.deposit(totalEU - 320);
                        fixture.reload(host);
                    }).thenWaitUntil(this::assertReconnected)
                    .thenWaitUntil(this::assertFinished)
                    .thenExecute(() -> fixture.reload(host))
                    .thenWaitUntil(this::assertReconnected)
                    .thenExecuteFor(100, this::assertSettled)
                    .thenWaitUntil(this::assertReconnected)
                    .thenExecute(() -> {
                        LogUtils.getLogger().info("GameTest NAC persistence completed: host={} processingEU={} history={} hostOutput={} moduleOutput={}",
                                host, totalEU, structure.host().getCalibration().save(), fixture.outputs(true), fixture.outputs(false));
                        fixture.collectRemainder();
                        structure.releaseChunks();
                    }).thenSucceed();
        }

        private void assertHistory(int primitive, int crystal, int optical, long spent) {
            helper.assertTrue(structure.module().getRecipeLogic().isIdle(), "等待历史加工完成");
            var calibration = structure.host().getCalibration();
            helper.assertTrue(calibration.countOf(Family.PRIMITIVE) == primitive
                    && calibration.countOf(Family.CRYSTAL) == crystal && calibration.countOf(Family.OPTICAL) == optical
                    && calibration.total() == primitive + crystal + optical, "真实加工的校准历史不符：" + calibration.save());
            helper.assertTrue(fixture.outputs(false).equals(crystal == 0
                            ? Map.of("kubejs:nac_tier_primitive_output", primitive)
                            : Map.of("kubejs:nac_tier_primitive_output", primitive, "kubejs:nac_calibration_crystal_output", crystal)),
                    "历史加工的真实交付不符：" + fixture.outputs(false));
            fixture.assertInputsEmpty();
            fixture.assertSpent(spent);
        }

        private void begin() {
            helper.assertTrue(structure.host().getCalibration().activeTier() == 0, "历史不应使待保存批次改变倍率");
            totalEU = host ? 32L * (120 + 160) : 1280;
            fixture.input(host, 0, host ? "nac_persistence_host_crystal_input" : "nac_calibration_crystal_input");
            fixture.input(host, 1, host ? "nac_persistence_host_optical_input" : "nac_calibration_optical_input");
            fixture.deposit(321);
        }

        private void assertPaused() {
            var batches = fixture.batches(host);
            helper.assertTrue(batches.size() == (host ? 2 : 1), "等待真实运行批次：" + batches);
            helper.assertTrue(fixture.balance() == 1 && batches.stream()
                    .allMatch(batch -> batch.progress() > 0 && batch.progress() < batch.duration()), "等待批次运行至断电：" + batches);
            long spent = batches.stream().mapToLong(batch -> RecipeHelper.getInputEUt(batch.recipe()) * batch.progress()).sum();
            helper.assertTrue(spent == 320, "已推进进度与真实扣电不符：" + batches);
            helper.assertTrue(batches.stream().mapToLong(batch -> RecipeHelper.getInputEUt(batch.recipe()) * batch.duration()).sum() == totalEU,
                    "实际批次总电量不符：" + batches);
            if (host) {
                var ids = batches.stream().map(batch -> batch.id().toString()).sorted().toList();
                String prefix = NanochipModContext.modId() + ":nanochip/assembly_matrix/test_persistence_host_";
                helper.assertTrue(ids.equals(List.of(prefix + "crystal", prefix + "optical")), "两条独立批次配方 ID 不符：" + ids);
            } else {
                Map<String, Integer> sources = new LinkedHashMap<>();
                var entries = batches.get(0).recipe().data.getList("gtlmodular:calibration_sources", Tag.TAG_COMPOUND);
                for (int i = 0; i < entries.size(); i++) {
                    var entry = entries.getCompound(i);
                    helper.assertTrue(entry.getString("family").equals(entry.getString("id").endsWith("crystal") ? "CRYSTAL" : "OPTICAL"),
                            "混合来源的配方族不符：" + entry);
                    sources.merge(entry.getString("id"), entry.getInt("amount"), Integer::sum);
                }
                String prefix = NanochipModContext.modId() + ":nanochip/assembly_matrix/test_";
                helper.assertTrue(sources.equals(Map.of(prefix + "crystal", 2, prefix + "optical", 3)), "待保存批次缺少混合系列来源：" + sources);
            }
            fixture.assertInputsEmpty();
            fixture.assertSpent(1600);
            assertOutputs(false);
        }

        private void assertOutputs(boolean finished) {
            Map<String, Integer> module = new LinkedHashMap<>(Map.of("kubejs:nac_tier_primitive_output", 12,
                    "kubejs:nac_calibration_crystal_output", 2));
            Map<String, Integer> target = finished ? Map.of("kubejs:nac_calibration_crystal_output", 2,
                    "kubejs:nac_calibration_optical_output", 3) : Map.of();
            if (!host) target.forEach((item, count) -> module.merge(item, count, Integer::sum));
            helper.assertTrue(fixture.outputs(false).equals(module) && fixture.outputs(true).equals(host ? target : Map.of()),
                    "产物丢失或重复：host=" + fixture.outputs(true) + " module=" + fixture.outputs(false));
        }

        private void assertFinished() {
            assertReconnected();
            assertSettled();
        }

        private void assertSettled() {
            helper.assertTrue(fixture.hostLogic().runningThreadCount() == 0
                    && structure.module().getRecipeLogic().isIdle(), "等待恢复后的真实加工完成");
            assertOutputs(true);
            fixture.assertInputsEmpty();
            fixture.assertSpent(1280 + totalEU);
            var cal = structure.host().getCalibration();
            helper.assertTrue(cal.countOf(Family.PRIMITIVE) == 12 && cal.countOf(Family.CRYSTAL) == 4
                    && cal.countOf(Family.OPTICAL) == 3 && cal.total() == 19, "恢复后校准结算丢失或重复：" + cal.save());
            CompoundTag saved = cal.save();
            helper.assertTrue(saved.getInt("Cursor") == 19 && saved.getInt("Recorded") == 19
                    && saved.getInt("Total") == 19 && saved.getInt("Fill") == 19, "校准历史游标没有只推进一次：" + saved);
        }

        private void assertReconnected() {
            structure.assertConnected();
            fixture.assertLiveParts();
        }
    }
}
