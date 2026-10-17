package com.gtlmodular.gametest;

import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipModuleMachine;
import com.gtlmodular.gametest.fixture.NacInventoryFixture;
import com.gtlmodular.gametest.fixture.NacStructureFixture;
import com.gtlmodular.gametest.fixture.NacStructureLayout;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** 全板浸没设备排空守恒：排空是先抽料再出料，任一侧不成立都不能动输入。 */
public final class NacImpurityTests {

    private static final List<NacStructureFixture> fixtures = new ArrayList<>();
    private static final ResourceLocation SUPER_SOLDER = new ResourceLocation("gtceu", "super_mutated_living_solder");
    private static final ResourceLocation MUTATED_SOLDER = new ResourceLocation("gtceu", "mutated_living_solder");

    private enum Scenario {
        NO_OUTPUT,
        FLUSHES,
        OUTPUT_FULL
    }

    @AfterBatch(batch = "nac_impurity")
    public static void cleanup(ServerLevel level) {
        fixtures.forEach(NacStructureFixture::releaseChunks);
        fixtures.clear();
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        List<TestFunction> tests = new ArrayList<>();
        for (Scenario scenario : Scenario.values()) {
            String name = "nac_impurity_" + scenario.name().toLowerCase(Locale.ROOT);
            tests.add(new TestFunction("nac_impurity", NanochipModContext.modId() + "." + name,
                    NacStructureLayout.templateId().toString(), 6000, 0, true,
                    helper -> run(helper, scenario)));
        }
        return tests;
    }

    private static void run(GameTestHelper helper, Scenario scenario) {
        NacStructureFixture structure = new NacStructureFixture(helper, null,
                NanochipModContext.registryId("nanochip_board_processor"), scenario != Scenario.NO_OUTPUT);
        fixtures.add(structure);
        structure.build();
        var sequence = helper.startSequence()
                .thenWaitUntil(structure::assertConnected)
                .thenExecute(() -> prepare(helper, structure, scenario));
        if (scenario == Scenario.FLUSHES) {
            // 排空挂在每 20 tick 的复核上；多等 1 tick 避开「复核与本步谁先执行」的顺序差。
            sequence.thenIdle(21)
                    .thenExecute(() -> assertFlushed(helper, structure))
                    .thenExecuteFor(40, () -> assertFlushed(helper, structure));
        } else {
            sequence.thenExecuteFor(60, () -> assertUnchanged(helper, structure, scenario));
        }
        sequence.thenExecute(structure::releaseChunks).thenSucceed();
    }

    private static void prepare(GameTestHelper helper, NacStructureFixture structure, Scenario scenario) {
        FluidHatchPartMachine input = hatch(structure, GTMachines.FLUID_IMPORT_HATCH[1].getId());
        Fluid solder = fluid(SUPER_SOLDER);
        helper.assertTrue(solder != null, "测试流体未注册：" + SUPER_SOLDER);
        long filled = input.tank.fill(FluidStack.create(solder, 1000L), false);
        helper.assertTrue(filled == 1000L, "输入仓未接收 1000 mB 超突变活性焊料：实际 " + filled + " mB");
        if (scenario == Scenario.OUTPUT_FULL) {
            FluidHatchPartMachine output = hatch(structure, GTMachines.FLUID_EXPORT_HATCH[1].getId());
            long capacity = output.tank.getStorages()[0].getCapacity();
            long blocked = output.tank.fill(FluidStack.create(Fluids.LAVA, capacity), false);
            helper.assertTrue(blocked == capacity,
                    "输出仓未填满：期望 " + capacity + " mB，实际 " + blocked + " mB");
        }
        setImpurity(helper, structure.module(), 100.0D);
    }

    private static void assertFlushed(GameTestHelper helper, NacStructureFixture structure) {
        FluidHatchPartMachine input = hatch(structure, GTMachines.FLUID_IMPORT_HATCH[1].getId());
        FluidHatchPartMachine output = hatch(structure, GTMachines.FLUID_EXPORT_HATCH[1].getId());
        FluidStack product = output.tank.getFluidInTank(0);
        long remaining = input.tank.getFluidInTank(0).getAmount();
        double impurity = structure.module().impurity();
        helper.assertTrue(remaining == 0L && product.getAmount() == 250L
                        && product.getFluid() == fluid(MUTATED_SOLDER) && impurity == 0.0D,
                "装上输出仓后（20 tick 定时复核）未完成排空：期望输入 0 mB、输出 250 mB 突变活性焊料、"
                        + "含杂度 0%，实际输入 " + remaining + " mB、输出 " + product.getAmount() + " mB "
                        + fluidName(product.getFluid()) + "、含杂度 " + impurity + "%");
    }

    private static void assertUnchanged(GameTestHelper helper, NacStructureFixture structure, Scenario scenario) {
        FluidHatchPartMachine input = hatch(structure, GTMachines.FLUID_IMPORT_HATCH[1].getId());
        long remaining = input.tank.getFluidInTank(0).getAmount();
        double impurity = structure.module().impurity();
        if (scenario == Scenario.NO_OUTPUT) {
            helper.assertTrue(remaining == 1000L && impurity == 100.0D,
                    "没有输出仓时仍排空了焊料：期望输入仍 1000 mB、含杂度停留 100%，"
                            + "实际输入 " + remaining + " mB、含杂度 " + impurity + "%");
            return;
        }
        FluidHatchPartMachine output = hatch(structure, GTMachines.FLUID_EXPORT_HATCH[1].getId());
        FluidStack blocker = output.tank.getFluidInTank(0);
        long capacity = output.tank.getStorages()[0].getCapacity();
        helper.assertTrue(remaining == 1000L && blocker.getAmount() == capacity
                        && blocker.getFluid() == Fluids.LAVA && impurity == 100.0D,
                "输出仓满时仍排空了焊料：期望输入仍 1000 mB、输出仍 " + capacity + " mB 岩浆、"
                        + "含杂度停留 100%，实际输入 " + remaining + " mB、输出 " + blocker.getAmount() + " mB "
                        + fluidName(blocker.getFluid()) + "、含杂度 " + impurity + "%");
    }

    private static FluidHatchPartMachine hatch(NacStructureFixture structure, ResourceLocation id) {
        return NacInventoryFixture.part(structure.module(), id, FluidHatchPartMachine.class);
    }

    private static Fluid fluid(ResourceLocation id) {
        return ForgeRegistries.FLUIDS.getValue(id);
    }

    private static String fluidName(Fluid fluid) {
        ResourceLocation id = fluid == null ? null : ForgeRegistries.FLUIDS.getKey(fluid);
        return id == null ? "未注册流体" : id.toString();
    }

    /** 含杂度没有公开写入口；排空用例必须从「满 100%」起步，只能写入持久化字段。 */
    private static void setImpurity(GameTestHelper helper, NanochipModuleMachine module, double value) {
        try {
            Field field = NanochipModuleMachine.class.getDeclaredField("impurity");
            field.setAccessible(true);
            field.setDouble(module, value);
        } catch (ReflectiveOperationException failure) {
            throw new GameTestAssertException("无法设置含杂度（字段可能改名）：" + failure);
        }
        helper.assertTrue(module.impurity() == value,
                "含杂度写回失败：期望 " + value + "%，实际 " + module.impurity() + "%");
    }
}
