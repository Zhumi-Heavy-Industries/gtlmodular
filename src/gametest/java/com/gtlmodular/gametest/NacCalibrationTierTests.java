package com.gtlmodular.gametest;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipCalibration.Family;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gtlmodular.gametest.fixture.NacCalibrationFixture;
import com.gtlmodular.gametest.fixture.NacStructureFixture;
import com.gtlmodular.gametest.fixture.NacStructureLayout;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class NacCalibrationTierTests {
    private static final List<NacStructureFixture> fixtures = new ArrayList<>();

    @AfterBatch(batch = "calibration_tiers_crystal")
    public static void cleanupCrystal(ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "calibration_tiers_wetware")
    public static void cleanupWetware(ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "calibration_tiers_bioware")
    public static void cleanupBioware(ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "calibration_tiers_optical")
    public static void cleanupOptical(ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "calibration_tiers_exotic")
    public static void cleanupExotic(ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "calibration_tiers_cosmic")
    public static void cleanupCosmic(ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "calibration_tiers_temporal")
    public static void cleanupTemporal(ServerLevel level) { cleanup(); }

    private static void cleanup() {
        fixtures.forEach(NacStructureFixture::releaseChunks);
        fixtures.clear();
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(test(Family.CRYSTAL), test(Family.WETWARE), test(Family.BIOWARE), test(Family.OPTICAL),
                test(Family.EXOTIC), test(Family.COSMIC), test(Family.TEMPORAL));
    }

    private static TestFunction test(Family family) {
        String name = "calibration_tiers_" + family.name().toLowerCase(Locale.ROOT);
        return new TestFunction(name, NanochipModContext.modId() + ".nac_" + name,
                NacStructureLayout.templateId().toString(), 30000, 0, true, helper -> run(helper, family));
    }

    private static void run(GameTestHelper helper, Family family) {
        // 模块只收普通能源仓，所以这里用 UHV 2A 能源仓：经仓室合并后输入电压仍是 2,097,152，
        // 容量 V[9]×16×2＝67,108,864（32 个满功率 tick），覆盖最长的 31 tick 批次。
        NacStructureFixture structure = new NacStructureFixture(helper, GTMachines.ENERGY_INPUT_HATCH[9]);
        fixtures.add(structure);
        structure.build();
        NacCalibrationFixture calibration = new NacCalibrationFixture(helper, structure, family);
        var sequence = helper.startSequence().thenWaitUntil(structure::assertConnected).thenExecute(calibration::prepare);
        // 基础族只贡献分母；这些真实产量让每个阈值都有相邻一件的严格下界和精确达标点。
        // 晶体/湿件是 30/60/90％，其余五族是 25/50/75％，两组各用同一个分母递进。
        boolean high = family == Family.CRYSTAL || family == Family.WETWARE;
        int[] primitives = high ? new int[]{14, 14, 14} : new int[]{12, 12, 12};
        int[] atThreshold = high ? new int[]{6, 21, 126} : new int[]{4, 12, 36};
        int previousPrimitive = 0;
        int previousFamily = 0;
        Family other = family == Family.CRYSTAL ? Family.OPTICAL : Family.CRYSTAL;
        for (int index = 0; index < 3; index++) {
            calibration.history(sequence, Family.PRIMITIVE, primitives[index] - previousPrimitive);
            calibration.history(sequence, family, atThreshold[index] - 1 - previousFamily);
            for (int side = 0; side < 2; side++) {
                if (side == 1) calibration.history(sequence, family, 1);
                int primitive = primitives[index];
                int produced = atThreshold[index] - 1 + side;
                int tier = index + side;
                double threshold = family.threshold(index + 1);
                boolean reached = side == 1;
                sequence.thenExecute(() -> calibration.checkpoint(primitive, produced, tier, threshold, reached));
                for (int ticks : new int[]{21, 61}) {
                    calibration.probe(sequence, family, ticks);
                    calibration.probe(sequence, other, ticks);
                }
                sequence.thenExecute(() -> calibration.checkpoint(primitive, produced, tier, threshold, reached));
            }
            previousPrimitive = primitives[index];
            previousFamily = atThreshold[index];
        }
        sequence.thenExecute(structure::releaseChunks).thenSucceed();
    }
}
