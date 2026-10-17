package com.gtlmodular.gametest;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.gametest.fixture.NacEnergyFixture;
import com.gtlmodular.gametest.fixture.NacInventoryFixture;
import com.gtlmodular.gametest.fixture.NacStructureFixture;
import com.gtlmodular.gametest.fixture.NacStructureLayout;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** 组装矩阵的 20 格部装外壳档：同级才成形，且外壳档低于配方要求时不取该配方。 */
public final class NacMatrixTierTests {

    private static final List<NacStructureFixture> fixtures = new ArrayList<>();
    private static final String TIER_RECIPE = "nanochip/assembly_matrix/test_tier_gate";

    private enum Scenario {
        SAME_TIER,
        MIXED_TIER,
        BELOW_RECIPE
    }

    @AfterBatch(batch = "nac_matrix_tier")
    public static void cleanup(ServerLevel level) {
        fixtures.forEach(NacStructureFixture::releaseChunks);
        fixtures.clear();
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        List<TestFunction> tests = new ArrayList<>();
        for (Scenario scenario : Scenario.values()) {
            String name = "nac_matrix_tier_" + scenario.name().toLowerCase(Locale.ROOT);
            tests.add(new TestFunction("nac_matrix_tier", NanochipModContext.modId() + "." + name,
                    NacStructureLayout.templateId().toString(), 6000, 0, true,
                    helper -> run(helper, scenario)));
        }
        return tests;
    }

    private static void run(GameTestHelper helper, Scenario scenario) {
        Block lv = casing(helper, "lv");
        Block mv = casing(helper, "mv");
        Block luv = casing(helper, "luv");
        NacStructureFixture structure = new NacStructureFixture(helper, true);
        fixtures.add(structure);
        structure.build();
        int cells = switch (scenario) {
            case SAME_TIER -> structure.replaceAssemblyCasings(luv);
            case MIXED_TIER -> {
                int count = structure.replaceAssemblyCasings(lv);
                structure.replaceAssemblyCasing(mv, 0);
                yield count;
            }
            case BELOW_RECIPE -> structure.replaceAssemblyCasings(lv);
        };
        helper.assertTrue(cells == 20, "部装外壳格应为 20 个，实际 " + cells + " 个");
        if (scenario == Scenario.MIXED_TIER) {
            helper.startSequence()
                    .thenIdle(200)
                    .thenExecute(() -> structure.assertModuleNotFormed(
                            "20 格部装外壳混级（lv×19 + mv×1）仍成形（期望不成形）"))
                    .thenExecute(structure::releaseChunks).thenSucceed();
            return;
        }
        if (scenario == Scenario.SAME_TIER) {
            helper.startSequence()
                    .thenWaitUntil(structure::assertConnected)
                    .thenExecute(() -> assertTier(helper, structure, 5))
                    .thenExecute(structure::releaseChunks).thenSucceed();
            return;
        }
        GateRun run = new GateRun();
        helper.startSequence()
                .thenWaitUntil(structure::assertConnected)
                .thenExecute(() -> run.prepare(helper, structure))
                .thenIdle(100)
                .thenExecute(() -> run.assertRejected(helper, structure))
                .thenExecute(structure::releaseChunks).thenSucceed();
    }

    private static void assertTier(GameTestHelper helper, NacStructureFixture structure, int expected) {
        int actual = structure.moduleHousingTier();
        helper.assertTrue(actual == expected,
                "组装矩阵外壳档读取不符：期望 " + expected + " 档，实际 " + actual + " 档");
    }

    private static Block casing(GameTestHelper helper, String tier) {
        ResourceLocation id = new ResourceLocation("gtlcore", "component_assembly_line_casing_" + tier);
        helper.assertTrue(ForgeRegistries.BLOCKS.containsKey(id), "缺少 gtlcore 部件装配线外壳：" + id);
        return ForgeRegistries.BLOCKS.getValue(id);
    }

    private static GTRecipe tierRecipe(GameTestHelper helper) {
        ResourceLocation id = new ResourceLocation(NanochipModContext.modId(), TIER_RECIPE);
        var raw = helper.getLevel().getRecipeManager().byKey(id)
                .orElseThrow(() -> new GameTestAssertException("测试配方未加载：" + id + "（检查 KubeJS 脚本日志）"));
        helper.assertTrue(raw instanceof GTRecipe, "测试配方不是 GTRecipe：" + id);
        return (GTRecipe) raw;
    }

    /** 1 档矩阵配上要求 5 档的真实配方：料和电都给足，它仍必须一条也开不了。 */
    private static final class GateRun {
        private NacInventoryFixture inventory;
        private NacEnergyFixture energy;

        private void prepare(GameTestHelper helper, NacStructureFixture structure) {
            assertTier(helper, structure, 1);
            GTRecipe recipe = tierRecipe(helper);
            helper.assertTrue(recipe.data.getInt("nanochip_assembly_matrix_tier") == 5,
                    "测试配方要求的外壳档不是 5：" + recipe.data);
            inventory = new NacInventoryFixture(helper, structure.module(),
                    "kubejs:nac_matrix_tier_input", "kubejs:nac_matrix_tier_output");
            energy = new NacEnergyFixture(helper, structure, false);
            energy.deposit(640);
            inventory.addInput();
        }

        private void assertRejected(GameTestHelper helper, NacStructureFixture structure) {
            var logic = structure.module().getRecipeLogic();
            helper.assertTrue(inventory.inputs() == 1 && inventory.outputs() == 0 && inventory.outputLava() == 0,
                    "外壳档低于配方要求时仍扣料/产出：期望输入 1 件未动、无产出，实际 " + inventory.describe());
            helper.assertTrue(logic.isIdle() && logic.getProgress() == 0,
                    "外壳档 1 档不该取到要求 5 档的配方：期望 100 tick 后仍空闲，实际 status="
                            + logic.getStatus() + " progress=" + logic.getProgress() + "/" + logic.getDuration()
                            + " recipe=" + logic.getLastRecipe());
            energy.assertSpent(0);
        }
    }
}
