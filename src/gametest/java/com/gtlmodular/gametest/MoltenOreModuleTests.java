package com.gtlmodular.gametest;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.gametest.fixture.GameTestMachines;
import com.gtlmodular.gametest.fixture.MoltenOreTestLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
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
        build(helper, module, pos, player);
        placePart(helper, player, GTMachines.ITEM_IMPORT_BUS[1], pos.offset(-1, 0, 0));
        placePart(helper, player, GTMachines.ITEM_EXPORT_BUS[1], pos.offset(1, 0, 0));
        placePart(helper, player, GTMachines.ENERGY_INPUT_HATCH[1], pos.offset(0, 0, -1));
        placePart(helper, player, GTMachines.FLUID_IMPORT_HATCH[1], pos.offset(0, 0, 1));
        module.getPatternLock().lock();
        try {
            MultiblockState state = new MultiblockState(level, pos);
            helper.assertTrue(module.getPattern().checkPatternAt(state, true), "炼矿模块仓室未匹配");
        } finally {
            module.getPatternLock().unlock();
        }

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(host.isFormed() && module.isFormed(),
                        "测试主机或炼矿模块未成形"))
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
                    } catch (ReflectiveOperationException failure) {
                        helper.fail("无法检查独立线程控制器：" + failure);
                    }
                })
                .thenSucceed();
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
