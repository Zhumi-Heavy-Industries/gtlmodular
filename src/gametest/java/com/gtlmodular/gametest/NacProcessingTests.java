package com.gtlmodular.gametest;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.gametest.fixture.NacEnergyFixture;
import com.gtlmodular.gametest.fixture.NacInventoryFixture;
import com.gtlmodular.gametest.fixture.NacStructureFixture;
import com.gtlmodular.gametest.fixture.NacStructureLayout;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public final class NacProcessingTests {
    private static final List<NacStructureFixture> fixtures = new ArrayList<>();

    @AfterBatch(batch = "nac_processing_local")
    public static void cleanupLocal(ServerLevel level) { cleanup(); }

    @AfterBatch(batch = "nac_processing_local_capacity")
    public static void cleanupLocalCapacity(ServerLevel level) { cleanup(); }

    @AfterBatch(batch = "nac_processing_grid")
    public static void cleanupGrid(ServerLevel level) { cleanup(); }

    @AfterBatch(batch = "nac_processing_blocked")
    public static void cleanupBlocked(ServerLevel level) { cleanup(); }

    @AfterBatch(batch = "nac_processing_output_during_work")
    public static void cleanupDuringWork(ServerLevel level) { cleanup(); }

    private static void cleanup() {
        fixtures.forEach(NacStructureFixture::releaseChunks);
        fixtures.clear();
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(test("local", NacProcessingTests::local),
                test("local_capacity", NacProcessingTests::localCapacity),
                test("grid", NacProcessingTests::grid), test("blocked", NacProcessingTests::blocked),
                test("output_during_work", NacProcessingTests::outputDuringWork));
    }

    private static TestFunction test(String suffix, Consumer<GameTestHelper> body) {
        String name = "nac_processing_" + suffix;
        return new TestFunction(name, NanochipModContext.modId() + "." + name,
                NacStructureLayout.templateId().toString(), 6000, 0, true, body);
    }

    private static void local(GameTestHelper helper) {
        Run run = new Run(helper, false);
        helper.startSequence()
                .thenWaitUntil(run.structure::assertConnected)
                .thenExecute(run::prepare)
                .thenExecute(() -> run.energy.deposit(608))
                .thenExecuteFor(80, run::assertNoMaterial)
                .thenExecute(() -> run.inventory.addInput())
                .thenWaitUntil(run::assertStarted)
                .thenExecuteFor(80, () -> run.assertWaiting(0))
                .thenExecute(run::observeUninterrupted)
                .thenExecute(() -> run.energy.deposit(32))
                .thenWaitUntil(run::assertFinished)
                .thenExecuteFor(80, run::assertFinished)
                .thenExecute(() -> run.energy.deposit(640))
                .thenExecute(() -> run.inventory.addWater())
                .thenExecuteFor(80, run::assertNoExtraMaterial)
                .thenExecute(() -> run.log("finished once"))
                .thenExecute(run.structure::releaseChunks)
                .thenSucceed();
    }

    private static void localCapacity(GameTestHelper helper) {
        Run run = new Run(helper, false);
        var sequence = helper.startSequence()
                .thenWaitUntil(run.structure::assertConnected)
                .thenExecute(run::prepare)
                .thenExecute(run::observeUninterrupted)
                .thenExecute(() -> {
                    long capacity = run.structure.module().getEnergyContainer().getEnergyCapacity();
                    helper.assertTrue(capacity == 1024, "LV 能源仓容量不符：" + capacity);
                    run.energy.deposit(capacity);
                    run.inventory.addWater(2000);
                    run.inventory.addInput(3);
                });
        for (int completed = 1; completed <= 3; completed++) {
            int count = completed;
            sequence.thenWaitUntil(() -> run.assertCapacityDelivered(count))
                    .thenExecuteFor(40, () -> run.assertCapacityDelivered(count));
            if (count < 3) sequence.thenExecute(() -> run.energy.deposit(640));
        }
        sequence.thenExecute(() -> {
                    helper.assertTrue(run.paidTicks == 60, "缩批后有效扣电次数不是 60");
                    run.log("capacity split delivered three times without interruption");
                })
                // 前面的 0/32 逐 tick 观察只针对未超频的 32 EU/t 配方；超容量配方按超频后的耗电运行，守恒由总扣电断言保证。
                .thenExecute(() -> run.observing = false)
                .thenExecute(run::prepareOversizedInput)
                // 单份 1280 EU 大于仓容量 1024：仓满后开工并逐 tick 扣电，电耗尽时暂停，补电后完成且只交付一次。
                .thenWaitUntil(run::assertOversizedStarted)
                .thenWaitUntil(() -> helper.assertTrue(run.energy.stored() == 0, "单份超容量批次应逐 tick 耗尽仓内电量后暂停"))
                .thenExecuteFor(20, run::assertOversizedStarted)
                .thenExecute(() -> run.energy.deposit(640))
                .thenWaitUntil(run::assertOversizedFinished)
                .thenExecuteFor(40, run::assertOversizedFinished)
                .thenExecute(run.structure::releaseChunks).thenSucceed();
    }

    private static void grid(GameTestHelper helper) {
        Run run = new Run(helper, true);
        helper.startSequence()
                .thenWaitUntil(run.structure::assertConnected)
                .thenExecute(run::prepare)
                .thenExecuteFor(80, run::assertNoMaterial)
                .thenExecute(() -> run.inventory.addInput())
                .thenWaitUntil(run::assertStarted)
                .thenExecuteFor(60, () -> run.assertWaiting(0))
                .thenExecute(run::observe)
                .thenExecute(() -> run.energy.deposit(32))
                .thenWaitUntil(() -> {
                    helper.assertTrue(run.structure.module().getRecipeLogic().getProgress() == 1,
                            "只有一个 tick 电量时未推进一次");
                    run.energy.assertSpent(32);
                })
                .thenExecuteFor(60, () -> run.assertWaiting(1))
                .thenExecute(() -> run.energy.deposit(608))
                .thenWaitUntil(run::assertFinished)
                .thenExecuteFor(80, run::assertFinished)
                .thenExecute(() -> run.log("grid recovered once"))
                .thenExecute(run.structure::releaseChunks)
                .thenSucceed();
    }

    private static void blocked(GameTestHelper helper) {
        Run run = new Run(helper, false);
        helper.startSequence()
                .thenWaitUntil(run.structure::assertConnected)
                .thenExecute(run::prepare)
                .thenExecute(() -> run.energy.deposit(640))
                .thenExecute(() -> run.inventory.blockOutputs())
                .thenExecute(() -> run.inventory.addInput())
                .thenExecuteFor(80, () -> {
                    run.inventory.assertBlocked();
                    helper.assertTrue(run.inventory.inputs() == 1 && run.inventory.inputWater() == 1000,
                            "输出满时扣除了原料");
                    run.energy.assertSpent(0);
                    helper.assertTrue(run.structure.module().getRecipeLogic().getProgress() == 0,
                            "输出满时加工推进");
                })
                .thenExecute(() -> run.inventory.clearBlockers())
                .thenExecute(run::observe)
                .thenWaitUntil(run::assertFinished)
                .thenExecuteFor(80, run::assertFinished)
                .thenExecute(() -> run.log("output unblocked once"))
                .thenExecute(run.structure::releaseChunks)
                .thenSucceed();
    }

    private static void outputDuringWork(GameTestHelper helper) {
        Run run = new Run(helper, false);
        helper.startSequence()
                .thenWaitUntil(run.structure::assertConnected)
                .thenExecute(run::prepare)
                .thenExecute(() -> run.energy.deposit(640))
                .thenExecute(() -> run.inventory.addInput())
                .thenWaitUntil(() -> {
                    int progress = run.structure.module().getRecipeLogic().getProgress();
                    helper.assertTrue(progress > 0 && progress < 20, "等待正在加工的真实批次：" + run.status());
                })
                .thenExecute(() -> run.log("blocking outputs during work"))
                .thenExecute(() -> run.inventory.blockOutputs())
                .thenExecuteFor(1000, () -> {
                    run.inventory.assertBlocked();
                    helper.assertTrue(run.energy.spent() <= 640, "输出堵塞后额外耗电");
                })
                .thenExecute(() -> run.log("blocked during work"))
                .thenExecute(() -> run.inventory.clearBlockers())
                .thenExecute(() -> run.outputsClearedAt = helper.getTick())
                .thenWaitUntil(run::assertRecoveredOutput)
                .thenExecuteFor(80, run::assertFinished)
                .thenExecute(() -> run.log("in-flight output recovered once"))
                .thenExecute(run.structure::releaseChunks)
                .thenSucceed();
    }

    private static final class Run {
        private final GameTestHelper helper;
        private final NacStructureFixture structure;
        private final boolean grid;
        private NacInventoryFixture inventory;
        private NacInventoryFixture hostInventory;
        private NacEnergyFixture energy;
        private boolean observing;
        private boolean uninterrupted;
        private long lastSpent;
        private long paidTicks;
        private long outputsClearedAt;

        private Run(GameTestHelper helper, boolean grid) {
            this.helper = helper;
            this.grid = grid;
            structure = new NacStructureFixture(helper, !grid);
            fixtures.add(structure);
            structure.build();
            helper.onEachTick(() -> {
                if (observing) assertTick();
                if (hostInventory != null) {
                    helper.assertTrue(hostInventory.inputs() == 0 && hostInventory.outputs() == 0
                                    && hostInventory.inputWater() == 0 && hostInventory.outputLava() == 0,
                            "加工意外改动主机仓室");
                }
            });
        }

        private void prepare() {
            inventory = new NacInventoryFixture(helper, structure.module());
            hostInventory = new NacInventoryFixture(helper, structure.host());
            energy = new NacEnergyFixture(helper, structure, grid);
            var recipe = helper.getLevel().getRecipeManager().byKey(new ResourceLocation("gtl_gametest", "nanochip_assembly_matrix/nac_processing"))
                    .orElseThrow(() -> new IllegalStateException("RecipeManager 未加载自建配方；检查 server_scripts 日志"));
            helper.assertTrue(recipe instanceof GTRecipe, "测试配方不是 GTRecipe");
            GTRecipe raw = (GTRecipe) recipe;
            helper.assertTrue(raw.getType() == structure.module().getRecipeType()
                    && raw.duration == 20 && RecipeHelper.getInputEUt(raw) == 32, "自建配方参数不符");
            helper.assertTrue(NanochipModContext.config().isNanochipSimpleLogistics(), "此用例覆盖默认简单物流");
            helper.assertTrue(!structure.module().getModuleSpec().hasUpkeep(), "矩阵新增了维护流体，须扩展守恒断言");
            inventory.addWater();
            log("prepared raw=32EUt*20ticks input=1+1000mB output=1+1000mB");
        }

        private void assertNoMaterial() {
            helper.assertTrue(inventory.inputs() == 0 && inventory.inputWater() == 1000
                    && inventory.outputs() == 0 && inventory.outputLava() == 0, "无料时物料发生变化");
            helper.assertTrue(structure.module().getRecipeLogic().getProgress() == 0, "无料时推进");
            energy.assertSpent(0);
        }

        private void assertStarted() {
            var logic = structure.module().getRecipeLogic();
            helper.assertTrue(logic.getLastRecipe() != null && !logic.isIdle(), "合批尚未建立：" + status());
            helper.assertTrue(logic.getDuration() == 20 && RecipeHelper.getInputEUt(logic.getLastRecipe()) == 32,
                    "实际批次功率/时长不是 32*20：" + status());
            helper.assertTrue(inventory.inputs() == 0 && inventory.inputWater() == 0, "合批未真实扣料");
            log("admitted");
        }

        private void assertWaiting(int progress) {
            helper.assertTrue(structure.module().getRecipeLogic().getProgress() == progress,
                    "缺电期间进度变化：" + status());
            helper.assertTrue(inventory.outputs() == 0 && inventory.outputLava() == 0, "缺电期间产出");
            energy.assertSpent(32L * progress);
        }

        private void assertNoExtraMaterial() {
            helper.assertTrue(inventory.inputs() == 0 && inventory.inputWater() == 1000
                    && inventory.outputs() == 1 && inventory.outputLava() == 1000,
                    "有足量电和水但无物品原料时又发生加工");
            helper.assertTrue(structure.module().getRecipeLogic().isIdle(), "无料时应保持空闲");
            energy.assertSpent(640);
        }

        private void assertRecoveredOutput() {
            structure.assertConnected();
            energy.assertSpent(640);
            var logic = structure.module().getRecipeLogic();
            if (helper.getTick() - outputsClearedAt >= 1000 && logic.isIdle()
                    && logic.getProgress() == 0 && logic.getDuration() == 0
                    && inventory.inputs() == 0 && inventory.inputWater() == 0
                    && inventory.outputs() == 0 && inventory.outputLava() == 0) {
                throw new GameTestAssertException("NAC_OUTPUT_LOSS: 腾空后仍缺少 1 件产物和 1000 mB 岩浆；" + status());
            }
            assertFinished();
        }

        private void observe() {
            lastSpent = energy.spent();
            observing = true;
        }

        private void observeUninterrupted() {
            uninterrupted = true;
            observe();
        }

        private void assertTick() {
            long spent = energy.spent();
            long delta = spent - lastSpent;
            helper.assertTrue(delta == 0 || delta == 32, "单 tick 扣电不是 0/32：" + status());
            if (delta == 32) paidTicks++;
            lastSpent = spent;
            int progress = structure.module().getRecipeLogic().getProgress();
            var logic = structure.module().getRecipeLogic();
            if (uninterrupted && progress > 0 && progress < logic.getDuration()) {
                // 测试服的高速 tick 不等于机器的调度 tick；等待调度不能误报为断电。
                helper.assertTrue(logic.isWorking()
                                && structure.module().getEnergyContainer().getEnergyStored()
                                >= 32L * (logic.getDuration() - progress),
                        "备足整批电量后断电或余电不足：" + status());
            }
            energy.assertSpent(640L * inventory.outputs() + 32L * progress);
        }

        private void assertCapacityDelivered(int completed) {
            helper.assertTrue(inventory.outputs() == completed && inventory.outputLava() == 1000L * completed,
                    "缩批后交付数量不符：" + status());
            energy.assertSpent(640L * completed);
            var logic = structure.module().getRecipeLogic();
            helper.assertTrue(logic.getProgress() == 0, "下一批缺电时提前推进：" + status());
            if (completed < 3) {
                helper.assertTrue(!logic.isIdle() && logic.getDuration() == 20
                                && RecipeHelper.getInputEUt(logic.getLastRecipe()) == 32,
                        "缩批后的待加工批次不是一份：" + status());
                helper.assertTrue(inventory.inputs() == 2 - completed
                                && inventory.inputWater() == 1000L * (2 - completed),
                        "超出容量的输入未留在仓室：" + status());
            } else {
                helper.assertTrue(logic.isIdle() && inventory.inputs() == 0 && inventory.inputWater() == 0,
                        "缩批加工后未结清：" + status());
            }
        }

        private void prepareOversizedInput() {
            var raw = helper.getLevel().getRecipeManager().byKey(new ResourceLocation(
                    "gtl_gametest", "nanochip_assembly_matrix/nac_processing_oversized")).orElseThrow();
            helper.assertTrue(raw instanceof GTRecipe recipe && recipe.duration == 40
                            && RecipeHelper.getInputEUt(recipe) == 32,
                    "单份超容量配方参数不符");
            inventory = new NacInventoryFixture(helper, structure.module(), "kubejs:nac_processing_oversized_input");
            energy.deposit(640);
            inventory.addWater();
            inventory.addInput();
        }

        private void assertOversizedStarted() {
            helper.assertTrue(inventory.inputs() == 0 && inventory.inputWater() == 0
                            && inventory.outputs() == 3 && inventory.outputLava() == 3000,
                    "单份放不下时仓满后应扣料开工、尚未产出：" + status());
        }

        private void assertOversizedFinished() {
            helper.assertTrue(inventory.inputs() == 0 && inventory.inputWater() == 0
                            && inventory.outputs() == 4 && inventory.outputLava() == 4000,
                    "单份放不下的批次应逐 tick 完成并只交付一次：" + status());
            helper.assertTrue(structure.module().getRecipeLogic().isIdle(), "单份超容量批次完成后未空闲：" + status());
            // 必须付清的是 1920（前三批）+ 1280（64 EU/t × 20 tick）＝ 3200；余量只可能是「断电回退重付」：
            // GTCEu 原版 RecipeLogic 在仓内电量耗尽时把进度回退 2 tick，补电后重付，一次 = 2 tick × 64 EU/t = 128。
            // 回退几次取决于「电量耗尽点与批次进度的交错」，而它会随服务器每 tick 的工作量漂移——实测：批次 2 基线
            // 3328（余量 1 次）、独立版 3200（余量 0，这一轮压根没断电）、加了 16 个物品与 43 条配方后 3584（3 次）。
            // 所以这里判不变量：付清基础量、余量必须是整次回退、且不超过观测上限，不钉死某一次的具体次数。
            long base = 3200L;
            long rollbackUnit = 128L;
            long extra = energy.spent() - base;
            helper.assertTrue(extra >= 0L && extra <= 6L * rollbackUnit && extra % rollbackUnit == 0,
                    "超容量批次的扣电不是「基础 + 整次断电回退」：" + energy.spent()
                            + "（基础 " + base + "，余量 " + extra + "）" + status());
        }

        private void assertFinished() {
            inventory.assertFinished();
            energy.assertSpent(640);
            helper.assertTrue(structure.module().getRecipeLogic().isIdle(), "完成后未空闲：" + status());
        }

        private String status() {
            var logic = structure.module().getRecipeLogic();
            return "status=" + logic.getStatus() + " progress=" + logic.getProgress() + "/" + logic.getDuration()
                    + " " + energy.describe() + " " + inventory.describe();
        }

        private void log(String phase) {
            LogUtils.getLogger().info("GameTest NAC processing: phase={} tick={} paidTicks={} {}", phase,
                    helper.getTick(), paidTicks, status());
        }
    }
}
