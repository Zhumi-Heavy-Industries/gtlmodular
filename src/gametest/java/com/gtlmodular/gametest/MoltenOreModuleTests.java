package com.gtlmodular.gametest;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
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
        MachineDefinition definition = GTRegistries.MACHINES.get(
                new ResourceLocation("gtlmodular", "molten_space_ore_module"));
        helper.assertTrue(definition != null, "炼矿模块未注册");
        var pos = helper.absolutePos(MoltenOreTestLayout.modulePosition());
        var placed = GameTestMachines.place(helper, player, definition, pos, Direction.NORTH);
        helper.assertTrue(placed instanceof WorkableElectricMultiblockMachine, "炼矿模块类型不符");
        var module = (WorkableElectricMultiblockMachine) placed;
        module.getPatternLock().lock();
        try {
            MultiblockState state = new MultiblockState(level, pos);
            module.getPattern().autoBuild((FakePlayer) player, state);
            helper.assertTrue(module.getPattern().checkPatternAt(state, true),
                    "炼矿模块结构未成形：" + (state.error == null ? "unknown"
                            : state.error.getErrorInfo().getString() + " pos=" + module.getPos()
                            + " candidates=" + state.error.getCandidates().stream()
                                    .map(list -> list.isEmpty() ? "none" : list.get(0).getHoverName().getString())
                                    .toList()));
        } finally {
            module.getPatternLock().unlock();
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(module.isFormed(), "炼矿模块未成形"))
                .thenSucceed();
    }
}
