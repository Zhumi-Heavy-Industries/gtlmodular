package com.gtlmodular.gametest;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.gametest.fixture.NacEnergyFixture;
import com.gtlmodular.gametest.fixture.NacInventoryFixture;
import com.gtlmodular.gametest.fixture.NacPersistenceFixture;
import com.gtlmodular.gametest.fixture.NacStructureFixture;
import com.gtlmodular.gametest.fixture.NacStructureLayout;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class NacRestartTests {
    // GTLCore 按墙钟至少隔 40ms 才 tick 机器；加速 GameTest 的 tick 数不能当加工时间。
    private static final long START_LIMIT_MS = 2000;
    private static final long FINISH_LIMIT_MS = 15000;
    public static final String STALE_HOST_ISSUE = "NAC_RELOAD_STALE_HOST";
    private static final List<NacStructureFixture> fixtures = new ArrayList<>();

    @AfterBatch(batch = "nac_restart_host_grid")
    public static void cleanupHost(ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "nac_restart_module_grid")
    public static void cleanupGrid(ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "nac_restart_module_local")
    public static void cleanupLocal(ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "nac_restart_module_small")
    public static void cleanupSmall(ServerLevel level) { cleanup(); }

    private static void cleanup() {
        fixtures.forEach(NacStructureFixture::releaseChunks);
        fixtures.clear();
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(test("host_grid", true, true, true), test("module_grid", false, true, true),
                test("module_local", false, false, false), test("module_small", false, false, true));
    }

    private static TestFunction test(String suffix, boolean host, boolean grid, boolean longRecipe) {
        String name = "nac_restart_" + suffix;
        return new TestFunction(name, NanochipModContext.modId() + "." + name,
                NacStructureLayout.templateId().toString(), 24000, 0, true,
                helper -> new Run(helper, suffix, host, grid, longRecipe).start());
    }

    private static final class Run {
        private final GameTestHelper helper;
        private final String scenario;
        private final boolean host;
        private final boolean grid;
        private final boolean longRecipe;
        private final long batchEU;
        private final NacStructureFixture structure;
        private NacPersistenceFixture persistence;
        private NacEnergyFixture energy;
        private NacInventoryFixture inventory;
        private NacInventoryFixture otherInventory;
        private int round;
        private long remainingSupply;
        private long lastSpent;
        private long pushedAt;
        private long pushedNanos;
        private long startedAt;
        private long eut;
        private int duration;
        private boolean observing;
        private String staleHostEvidence;
        private int staleHostObservations;

        private Run(GameTestHelper helper, String scenario, boolean host, boolean grid, boolean longRecipe) {
            this.helper = helper;
            this.scenario = scenario;
            this.host = host;
            this.grid = grid;
            this.longRecipe = longRecipe;
            // 主机按 4 倍功率、半时长超频一次；矩阵合批按总能量重新分配功率。
            batchEU = host ? 12800 : longRecipe ? 6400 : 640;
            structure = new NacStructureFixture(helper, !grid);
            fixtures.add(structure);
            structure.build();
            helper.onEachTick(() -> {
                observeHostIdentity();
                if (observing) observe();
            });
        }

        private void start() {
            var sequence = helper.startSequence().thenWaitUntil(structure::assertConnected)
                    .thenExecute(this::prepare);
            // 新建空闲和加工过的空闲都重建实体，覆盖无缓存及已有 lastRecipe 的首次到料。
            for (int i = 0; i < 2; i++) {
                sequence.thenExecute(this::reload)
                        .thenWaitUntil(this::assertReconnected)
                        .thenExecute(this::bindInventories)
                        .thenExecuteFor(80, this::assertIdle)
                        .thenWaitUntil(this::assertReconnected)
                        .thenExecute(this::push)
                        .thenWaitUntil(this::assertFinished)
                        .thenExecuteFor(80, this::assertSettled)
                        .thenWaitUntil(this::assertReconnected);
            }
            sequence.thenExecute(() -> {
                observing = false;
                helper.assertTrue(energy.stored() == 0, "用例结束仍有未结清能源");
                structure.releaseChunks();
                // 先完成两轮守恒和时限断言，再单列旧宿主问题，避免已知失败遮住加工回归。
                if (staleHostEvidence != null) {
                    helper.fail(STALE_HOST_ISSUE + "：模块仍连着已卸载的旧主机：" + staleHostEvidence
                            + " observedTicks=" + staleHostObservations);
                }
            }).thenSucceed();
        }

        private void prepare() {
            energy = new NacEnergyFixture(helper, structure, grid);
            persistence = new NacPersistenceFixture(helper, structure, false);
            String type = host ? "nanochip_assembly_complex" : "nanochip_assembly_matrix";
            String name = longRecipe ? "nac_restart" : "nac_processing";
            var raw = helper.getLevel().getRecipeManager()
                    .byKey(new ResourceLocation("gtl_gametest", type + "/" + name)).orElseThrow();
            helper.assertTrue(raw instanceof GTRecipe recipe
                            && recipe.getType() == (host ? structure.host() : structure.module()).getRecipeType()
                            && RecipeHelper.getInputEUt(recipe) == 32 && recipe.duration == (longRecipe ? 200 : 20),
                    "恢复用例的原始配方未按预期加载");
            helper.assertTrue(NanochipModContext.config().isNanochipSimpleLogistics()
                    && !structure.module().getModuleSpec().hasUpkeep(), "测试要求默认简单物流和无额外耗材的组装矩阵");
            if (!grid) {
                long capacity = structure.module().getEnergyContainer().getEnergyCapacity();
                helper.assertTrue(capacity == 1024 && (longRecipe ? batchEU > capacity : batchEU <= capacity),
                        "未建立整批耗电与真实 LV 仓容量的大小对照：capacity=" + capacity + " batchEU=" + batchEU);
            }
        }

        private RecipeLogic logic() {
            return host ? structure.host().getRecipeLogic() : structure.module().getRecipeLogic();
        }

        private void reload() {
            observing = false;
            round++;
            remainingSupply = batchEU;
            topUp();
            // 连真实非空能源一起保存，防止恢复测试意外靠重新充值掩盖丢电。
            persistence.reload(host);
        }

        private void assertReconnected() {
            var linked = structure.module().getHost();
            helper.assertTrue(linked == structure.host(), "恢复后模块指向另一主机对象：expected="
                    + structure.host().getPos() + " actual=" + (linked == null ? "null"
                    : linked.getPos() + " invalid=" + linked.isInValid() + " formed=" + linked.isFormed()));
            structure.assertConnected();
            persistence.assertLiveParts();
            helper.assertTrue(structure.module().drawingFromHost() == grid, "恢复后供电路径改变");
        }

        private void observeHostIdentity() {
            if (round == 0) return;
            var current = structure.host();
            var module = structure.module();
            var linked = module.getHost();
            if (linked != null && linked != current && linked.isInValid() && linked.isFormed()
                    && linked.getPos().equals(current.getPos()) && !current.isInValid() && !module.isInValid()
                    && MetaMachine.getMachine(helper.getLevel(), current.getPos()) == current
                    && MetaMachine.getMachine(helper.getLevel(), module.getPos()) == module) {
                staleHostObservations++;
                if (staleHostEvidence == null) {
                    staleHostEvidence = "scenario=" + scenario + " round=" + round + " tick=" + helper.getTick()
                            + " 当前模块指向已卸载同坐标主机 @" + current.getPos()
                            + "；世界中主机与模块均为恢复后的新对象，旧主机 invalid=true formed=true";
                    LogUtils.getLogger().warn("GameTest NAC restart stale host: {}", staleHostEvidence);
                }
            }
        }

        private void bindInventories() {
            String prefix = longRecipe ? "kubejs:nac_restart_" : "kubejs:nac_processing_";
            inventory = new NacInventoryFixture(helper, host ? structure.host() : structure.module(),
                    prefix + "input", prefix + "output");
            otherInventory = new NacInventoryFixture(helper, host ? structure.module() : structure.host(),
                    prefix + "input", prefix + "output");
        }

        private void assertIdle() {
            assertAmounts(round - 1);
            energy.assertSpent((round - 1) * batchEU);
            helper.assertTrue(logic().isIdle(), "到料前未保持空闲");
            assertOtherIdle();
        }

        private void push() {
            pushedAt = helper.getTick();
            pushedNanos = System.nanoTime();
            startedAt = -1;
            eut = 0;
            duration = 0;
            lastSpent = energy.spent();
            inventory.addWater();
            inventory.addInput();
            observing = true;
        }

        private void topUp() {
            if (remainingSupply == 0) return;
            long amount = grid ? remainingSupply : Math.min(remainingSupply,
                    structure.module().getEnergyContainer().getEnergyCapacity()
                            - structure.module().getEnergyContainer().getEnergyStored());
            if (amount > 0) {
                energy.deposit(amount);
                remainingSupply -= amount;
            }
        }

        private void observe() {
            assertOtherIdle();
            long spent = energy.spent();
            long delta = spent - lastSpent;
            if (startedAt < 0 && delta > 0) {
                startedAt = helper.getTick();
                var recipe = logic().getLastRecipe();
                helper.assertTrue(recipe != null, "已耗电但没有实际批次");
                eut = RecipeHelper.getInputEUt(recipe);
                duration = logic().getDuration();
                helper.assertTrue(eut == (host ? 128 : !longRecipe ? 32 : grid ? 320 : 64)
                                && duration == (host || (longRecipe && !grid) ? 100 : 20)
                                && eut * duration == batchEU,
                        "实际批次功率/时长偏离预期：" + status());
                helper.assertTrue(elapsedMillis() <= START_LIMIT_MS, "首次到料后开工过慢：" + status());
                LogUtils.getLogger().info("GameTest NAC restart started: scenario={} round={} latencyTicks={} latencyMs={} eut={} duration={} batchEU={}",
                        scenario, round, startedAt - pushedAt, elapsedMillis(), eut, duration, batchEU);
            }
            helper.assertTrue(delta == 0 || (eut > 0 && delta == eut), "单 tick 扣电不符：" + status());
            lastSpent = spent;
            long processed = spent - (round - 1) * batchEU;
            helper.assertTrue(processed >= 0 && processed <= batchEU, "加工超额耗电：" + status());
            boolean delivered = inventory.outputs() == round;
            helper.assertTrue(processed == (delivered ? batchEU : eut * logic().getProgress()),
                    "真实扣电与进度/交付不一致：" + status());
            helper.assertTrue(startedAt >= 0 || elapsedMillis() <= START_LIMIT_MS,
                    "读档后首次推料没有及时开工：" + status());
            helper.assertTrue(delivered || elapsedMillis() <= FINISH_LIMIT_MS,
                    "持续真实供电却未在期限内完成：" + status());
            topUp();
        }

        private void assertOtherIdle() {
            helper.assertTrue(otherInventory.inputs() == 0 && otherInventory.outputs() == 0
                    && otherInventory.inputWater() == 0 && otherInventory.outputLava() == 0,
                    "测试加工改动了另一台机器的仓室");
            helper.assertTrue((host ? structure.module() : structure.host()).getRecipeLogic().isIdle(),
                    "无料的另一台机器意外开工");
            helper.assertTrue(structure.host().getCalibration().total() == 0,
                    "非白名单配方意外增加校准");
        }

        private void assertAmounts(int completed) {
            helper.assertTrue(inventory.inputs() == 0 && inventory.inputWater() == 0
                            && inventory.outputs() == completed && inventory.outputLava() == completed * 1000L,
                    "读档或加工丢失/重复物料：" + status());
        }

        private void assertFinished() {
            assertReconnected();
            assertSettled();
            LogUtils.getLogger().info("GameTest NAC restart finished: scenario={} round={} elapsed={} {}",
                    scenario, round, helper.getTick() - pushedAt, status());
        }

        private void assertSettled() {
            assertAmounts(round);
            energy.assertSpent(round * batchEU);
            helper.assertTrue(startedAt >= 0 && logic().isIdle() && remainingSupply == 0,
                    "加工没有完整结清：" + status());
            assertOtherIdle();
        }

        private String status() {
            return "scenario=" + scenario + " round=" + round + " elapsed=" + (helper.getTick() - pushedAt)
                    + " elapsedMs=" + elapsedMillis()
                    + " status=" + logic().getStatus() + " progress=" + logic().getProgress() + "/" + logic().getDuration()
                    + " eut=" + eut + " remainingSupply=" + remainingSupply + " " + energy.describe()
                    + " " + inventory.describe();
        }

        private long elapsedMillis() {
            return (System.nanoTime() - pushedNanos) / 1_000_000;
        }
    }
}
