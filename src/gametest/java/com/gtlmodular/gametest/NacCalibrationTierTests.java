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
        return List.of(test(Family.CRYSTAL, false), test(Family.WETWARE, false), test(Family.BIOWARE, false),
                test(Family.OPTICAL, false), test(Family.EXOTIC, true), test(Family.COSMIC, true), test(Family.TEMPORAL, true));
    }

    private static TestFunction test(Family family, boolean special) {
        String name = "calibration_tiers_" + family.name().toLowerCase(Locale.ROOT);
        return new TestFunction(name, NanochipModContext.modId() + ".nac_" + name,
                NacStructureLayout.templateId().toString(), 30000, 0, true, helper -> run(helper, family, special));
    }

    private static void run(GameTestHelper helper, Family family, boolean special) {
        // 激光仓容量为 64 个满功率 tick，能预存长批次；普通能源仓的 16 tick 容量无法覆盖时长分支。
        NacStructureFixture structure = new NacStructureFixture(helper, GTMachines.LASER_INPUT_HATCH_256[5]);
        fixtures.add(structure);
        structure.build();
        NacCalibrationFixture calibration = new NacCalibrationFixture(helper, structure, family, special);
        var sequence = helper.startSequence().thenWaitUntil(structure::assertConnected).thenExecute(calibration::prepare);
        // 基础族只贡献分母；这些真实产量让每个阈值都有相邻一件的严格下界和精确达标点。
        int[] primitives = special ? new int[]{99, 114, 116} : new int[]{12, 12, 12};
        int[] atThreshold = special ? new int[]{1, 6, 29} : new int[]{3, 8, 18};
        double[] thresholds = special ? new double[]{0.01, 0.05, 0.20} : new double[]{0.20, 0.40, 0.60};
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
                double threshold = thresholds[index];
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
