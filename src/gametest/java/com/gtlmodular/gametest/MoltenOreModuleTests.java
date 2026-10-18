package com.gtlmodular.gametest;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.gametest.fixture.GameTestMachines;
import com.gtlmodular.gametest.fixture.MoltenOreTestLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.util.FakePlayer;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineHost;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;

import java.util.Collection;
import java.util.List;

/** 炼矿模块最小夹具：先验证 GameTest 专用 3×3×3 主机能成形并供电。 */
public final class MoltenOreModuleTests {
    private static final String BATCH = "molten_ore_module";

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        if (!NanochipModContext.modId().equals("gtlmodular")) {
            return List.of();
        }
        return List.of(
                new TestFunction(BATCH, "gtlmodular.molten_ore_host_forms",
                        MoltenOreTestLayout.templateId().toString(), 1200, 0, true,
                        MoltenOreModuleTests::hostForms),
                new TestFunction(BATCH + "_module", "gtlmodular.molten_ore_module_forms",
                        MoltenOreTestLayout.templateId().toString(), 2400, 0, true,
                        MoltenOreModuleTests::moduleForms));
    }

    private static void hostForms(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(MoltenOreTestLayout.hostPosition());
        var player = GameTestMachines.player(helper, "MoltenOreHost");
        MachineDefinition definition = GTRegistries.MACHINES.get(
                new ResourceLocation("gtlmodular", "molten_module_host_test"));
        helper.assertTrue(definition != null, "测试主机未注册");
        var placed = GameTestMachines.place(helper, player, definition, pos, Direction.NORTH);
        helper.assertTrue(placed instanceof WorkableElectricMultiblockMachine, "测试主机类型不符");
        var host = (WorkableElectricMultiblockMachine) placed;
        host.getPatternLock().lock();
        try {
            MultiblockState state = new MultiblockState(level, pos);
            host.getPattern().autoBuild((FakePlayer) player, state);
            helper.assertTrue(host.getPattern().checkPatternAt(state, true), "测试主机结构未成形");
        } finally {
            host.getPatternLock().unlock();
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(host.isFormed(), "测试主机未成形"))
                .thenSucceed();
    }

    private static void moduleForms(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = GameTestMachines.player(helper, "MoltenOreModule");
        MachineDefinition hostDefinition = GTRegistries.MACHINES.get(
                new ResourceLocation("gtlmodular", "molten_module_host_test"));
        helper.assertTrue(hostDefinition != null, "测试主机未注册");
        var hostPos = helper.absolutePos(MoltenOreTestLayout.hostPosition());
        var host = (WorkableElectricMultiblockMachine) GameTestMachines.place(helper, player,
                hostDefinition, hostPos, Direction.NORTH);
        build(helper, host, hostPos, player);

        MachineDefinition definition = GTRegistries.MACHINES.get(
                new ResourceLocation("gtlmodular", "molten_space_ore_module"));
        helper.assertTrue(definition != null, "炼矿模块未注册");
        var pos = helper.absolutePos(MoltenOreTestLayout.modulePosition());
        var placed = GameTestMachines.place(helper, player, definition, pos, Direction.NORTH);
        helper.assertTrue(placed instanceof WorkableElectricMultiblockMachine, "炼矿模块类型不符");
        var module = (WorkableElectricMultiblockMachine) placed;
        // 四个 X 位全部预放，避免 autoBuild 再塞入代表仓；全流体路线避开物品总线代理口。
        placePart(helper, player, GTMachines.FLUID_IMPORT_HATCH[1], pos.offset(-1, 0, -1));
        placePart(helper, player, GTMachines.FLUID_EXPORT_HATCH[1], pos.offset(1, 0, -1));
        placePart(helper, player, GTMachines.ENERGY_INPUT_HATCH[1], pos.offset(-1, 0, 1));
        placePart(helper, player, GTMachines.FLUID_EXPORT_HATCH[1], pos.offset(1, 0, 1));
        build(helper, module, pos, player);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(host.isFormed() && module.isFormed(),
                        "测试主机或炼矿模块未成形"))
                .thenExecute(() -> helper.assertFalse(
                        module.getCapabilitiesProxy().row(com.gregtechceu.gtceu.api.capability.recipe.IO.IN).isEmpty(),
                        "炼矿模块没有输入能力"))
                .thenExecute(() -> {
                    if (host instanceof IModularMachineHost<?> h && module instanceof IModularMachineModule<?, ?> m) {
                        ((IModularMachineModule) m).connectToHost((IModularMachineHost) h);
                    } else {
                        helper.fail("模块/主机未实现模组化接口");
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        host instanceof IModularMachineHost<?> h
                                && module instanceof IModularMachineModule<?, ?> m
                                && m.getHost() == h && h.getModules().contains(m),
                        "测试主机与炼矿模块未连接"))
                .thenExecute(() -> {
                    try {
                        Object controller = module.getRecipeLogic().getClass()
                                .getMethod("gtlmodular$controller", boolean.class)
                                .invoke(module.getRecipeLogic(), true);
                        helper.assertTrue(controller != null, "炼矿模块没有建立独立线程控制器");
                        CONTROLLER[0] = controller;
                    } catch (ReflectiveOperationException failure) {
                        helper.fail("无法检查独立线程控制器：" + failure);
                    }
                })
                .thenExecute(() -> feed(helper, module, pos))
                .thenExecute(() -> module.getRecipeLogic().updateTickSubscription())
                .thenWaitUntil(() -> helper.assertTrue(ownsWork(), "引擎没有开批: reason=" + reason()))
                .thenSucceed();
    }

    private static String reason() {
        try {
            var field = CONTROLLER[0].getClass().getDeclaredField("reason");
            field.setAccessible(true);
            return String.valueOf(field.get(CONTROLLER[0]));
        } catch (ReflectiveOperationException failure) {
            return "?" + failure;
        }
    }

    private static final Object[] CONTROLLER = new Object[1];

    private static boolean ownsWork() {
        try {
            return (boolean) CONTROLLER[0].getClass().getMethod("ownsWork").invoke(CONTROLLER[0]);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("无法检查控制器 ownsWork", failure);
        }
    }

    private static void feed(GameTestHelper helper, WorkableElectricMultiblockMachine module, BlockPos pos) {
        GTRecipe recipe = GTRecipeBuilder.ofRaw()
                .inputFluids(FluidStack.create(Fluids.WATER, 1_000L))
                .outputFluids(FluidStack.create(Fluids.WATER, 1_000L))
                .duration(20)
                .EUt(32)
                .buildRawRecipe();
        helper.assertTrue(module.getRecipeType().getLookup().addRecipe(recipe), "测试配方注入失败");
        if (MetaMachine.getMachine(helper.getLevel(), pos.offset(-1, 0, -1)) instanceof FluidHatchPartMachine hatch) {
            hatch.tank.setFluidInTank(0, FluidStack.create(Fluids.WATER, 1_000L));
        } else {
            helper.fail("流体输入仓缺失");
        }
    }

    private static void build(GameTestHelper helper, WorkableElectricMultiblockMachine machine,
                              BlockPos pos, FakePlayer player) {
        machine.getPatternLock().lock();
        try {
            MultiblockState state = new MultiblockState(helper.getLevel(), pos);
            machine.getPattern().autoBuild(player, state);
            helper.assertTrue(machine.getPattern().checkPatternAt(state, true),
                    "结构未匹配：" + machine.getDefinition().getId());
        } finally {
            machine.getPatternLock().unlock();
        }
    }

    private static void placePart(GameTestHelper helper, FakePlayer player,
                                  MachineDefinition definition, BlockPos pos) {
        helper.getLevel().removeBlock(pos, false);
        GameTestMachines.place(helper, player, definition, pos, Direction.NORTH);
    }
}
