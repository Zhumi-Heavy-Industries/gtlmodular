package com.gtlmodular.gametest;

import com.gregtechceu.gtceu.common.machine.multiblock.part.LaserHatchPartMachine;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.NanochipModuleMachine;
import com.gtlmodular.gametest.fixture.NacStructureFixture;
import com.gtlmodular.gametest.fixture.NacStructureLayout;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** 激光仓只属于蚀刻阵列塔身的 H 格：1 个可成形，2 个超限量；其它模块与主机都不收。 */
public final class NacHatchAdmissionTests {

    private static final List<NacStructureFixture> fixtures = new ArrayList<>();

    private enum Scenario {
        ETCHER_ONE_LASER,
        ETCHER_TWO_LASERS,
        OTHER_MODULE_ONE_LASER,
        HOST_ONE_LASER
    }

    @AfterBatch(batch = "nac_hatch_admission")
    public static void cleanup(ServerLevel level) {
        fixtures.forEach(NacStructureFixture::releaseChunks);
        fixtures.clear();
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        List<TestFunction> tests = new ArrayList<>();
        for (Scenario scenario : Scenario.values()) {
            String name = "nac_hatch_admission_" + scenario.name().toLowerCase(Locale.ROOT);
            tests.add(new TestFunction("nac_hatch_admission", NanochipModContext.modId() + "." + name,
                    NacStructureLayout.templateId().toString(), 1200, 0, true,
                    helper -> run(helper, scenario)));
        }
        return tests;
    }

    private static void run(GameTestHelper helper, Scenario scenario) {
        ResourceLocation moduleId = switch (scenario) {
            case ETCHER_ONE_LASER, ETCHER_TWO_LASERS -> NanochipModContext.registryId("nanochip_etching_array");
            case OTHER_MODULE_ONE_LASER -> NanochipModContext.registryId("nanochip_cutting_chamber");
            case HOST_ONE_LASER -> null;
        };
        NacStructureFixture structure = new NacStructureFixture(helper, null, moduleId, true);
        fixtures.add(structure);
        structure.build();
        switch (scenario) {
            case ETCHER_ONE_LASER -> {
                helper.assertTrue(structure.moduleLaserSlots().size() >= 1,
                        "蚀刻阵列没有激光仓格（实有 " + structure.moduleLaserSlots().size() + " 个）");
                structure.installLaserInModuleLaserSlot(0);
            }
            case ETCHER_TWO_LASERS -> {
                // 塔身只开 1 个激光仓格；第 2 个放进普通 V 格，验证它不因「模块收激光」而被接纳。
                helper.assertTrue(structure.moduleLaserSlots().size() >= 1,
                        "蚀刻阵列没有激光仓格（实有 " + structure.moduleLaserSlots().size() + " 个）");
                structure.installLaserInModuleLaserSlot(0);
                structure.installLaserInModuleOrdinaryHatchSlot();
            }
            case OTHER_MODULE_ONE_LASER -> structure.installLaserInModuleHatchSlot(0);
            case HOST_ONE_LASER -> structure.installLaserInHostHatchSlot(0);
        }
        if (scenario == Scenario.ETCHER_ONE_LASER) {
            // 成形可能先于激光仓被复检接纳：等它被接纳，别拿成形当「仓已收下」。
            helper.startSequence()
                    .thenWaitUntil(structure::assertConnected)
                    .thenWaitUntil(() -> {
                        long lasers = lasers(structure.module());
                        helper.assertTrue(lasers == 1,
                                "激光仓未被蚀刻阵列结构接纳：期望 1 个，实际 " + lasers + " 个；" + state(structure));
                    })
                    .thenExecute(structure::releaseChunks).thenSucceed();
            return;
        }
        String expectation = switch (scenario) {
            case ETCHER_TWO_LASERS -> "蚀刻阵列装 2 个激光仓仍成形（期望不成形）";
            case OTHER_MODULE_ONE_LASER -> "其它模块装 1 个激光仓仍成形（期望不成形）";
            case HOST_ONE_LASER -> "主机装 1 个激光仓仍成形（期望不成形）";
            default -> "非预期场景";
        };
        helper.startSequence()
                .thenIdle(200)
                .thenExecute(() -> {
                    if (scenario == Scenario.HOST_ONE_LASER) {
                        structure.assertHostNotFormed(expectation);
                    } else {
                        structure.assertModuleNotFormed(expectation);
                    }
                })
                .thenExecute(structure::releaseChunks).thenSucceed();
    }

    private static long lasers(NanochipModuleMachine module) {
        return module.getParts().stream().map(part -> part.self())
                .filter(LaserHatchPartMachine.class::isInstance).count();
    }

    private static String state(NacStructureFixture structure) {
        return "host formed=" + structure.host().isFormed() + " module formed=" + structure.module().isFormed()
                + " module error=" + NanochipAssemblyComplexMachine.describeStructureFailure(
                        structure.module().getMultiblockState());
    }
}
