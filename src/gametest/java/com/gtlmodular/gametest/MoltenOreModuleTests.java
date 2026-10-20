package com.gtlmodular.gametest;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.util.FakePlayer;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineHost;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEExtendedOutputPartMachine;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                        MoltenOreModuleTests::moduleForms),
                new TestFunction(BATCH + "_fair", "gtlmodular.molten_ore_fairness",
                        MoltenOreTestLayout.templateId().toString(), 2400, 0, true,
                        MoltenOreModuleTests::fairness),
                new TestFunction(BATCH + "_real", "gtlmodular.molten_ore_real_shell",
                        MoltenOreTestLayout.templateId().toString(), 2400, 0, true,
                        MoltenOreModuleTests::realShellForms));
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
                .thenExecute(() -> addLavaRecipe(helper, module))
                .thenExecute(() -> feed(helper, module, pos))
                .thenExecute(() -> fillEnergy(level, pos))
                .thenExecute(() -> module.getRecipeLogic().updateTickSubscription())
                .thenWaitUntil(() -> helper.assertTrue(ownsWork(), "引擎没有开批: reason=" + reason()))
                .thenWaitUntil(() -> helper.assertTrue(outputFluid(level, pos), "批次没有输出流体"))
                .thenExecute(() -> helper.assertTrue(energyStored(level, pos) < energyCapacity(level, pos),
                        "批次没有扣能源仓电量"))
                .thenWaitUntil(() -> helper.assertTrue(rotate(level, pos), "候选没有轮转到 lava, produced=" + PRODUCED))
                .thenExecute(() -> helper.assertTrue(outputAmount(level, pos, Fluids.WATER) == 1_000L,
                        "水产物数量不守恒: " + outputAmount(level, pos, Fluids.WATER)))
                .thenExecute(() -> helper.assertTrue(outputAmount(level, pos, Fluids.LAVA) == 1_000L,
                        "熔岩产物数量不守恒: " + outputAmount(level, pos, Fluids.LAVA)))
                .thenSucceed();
    }

    private static final Set<String> FAIR_PRODUCED = new HashSet<>();

    private static void fairness(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = GameTestMachines.player(helper, "MoltenOreFairness");
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
        placePart(helper, player, GTMachines.FLUID_IMPORT_HATCH[1], pos.offset(-1, 0, -1));
        placePart(helper, player, GTMachines.FLUID_IMPORT_HATCH[1], pos.offset(1, 0, -1));
        placePart(helper, player, GTMachines.ENERGY_INPUT_HATCH[1], pos.offset(-1, 0, 1));
        placePart(helper, player, GTMachines.FLUID_EXPORT_HATCH[1], pos.offset(1, 0, 1));
        build(helper, module, pos, player);
        FAIR_PRODUCED.clear();

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
                    addFairRecipe(helper, module, Fluids.WATER, "fair_water");
                    addFairRecipe(helper, module, Fluids.LAVA, "fair_lava");
                    fillFairInputs(level, pos);
                    fillEnergy(level, pos);
                })
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
                .thenExecute(() -> module.getRecipeLogic().updateTickSubscription())
                .thenWaitUntil(() -> helper.assertTrue(ownsWork(), "引擎没有开批: reason=" + reason()))
                .thenWaitUntil(() -> helper.assertTrue(batchCount() >= 2, "没有并行开第二批: " + batchCount()))
                .thenWaitUntil(() -> helper.assertTrue(fairTick(level, pos),
                        "同时可做的两条配方没有都轮到: " + FAIR_PRODUCED))
                .thenSucceed();
    }

    private static void realShellForms(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = GameTestMachines.player(helper, "MoltenOreRealShell");
        var hostDefinition = GTRegistries.MACHINES.get(
                new ResourceLocation("gtlmodular", "molten_module_host_test"));
        helper.assertTrue(hostDefinition != null, "测试主机未注册");
        var hostPos = helper.absolutePos(MoltenOreTestLayout.hostPosition());
        var host = (WorkableElectricMultiblockMachine) GameTestMachines.place(helper, player,
                hostDefinition, hostPos, Direction.NORTH);
        build(helper, host, hostPos, player);

        MachineDefinition definition = GTRegistries.MACHINES.get(
                new ResourceLocation("gtlmodular", "molten_space_ore_module_real"));
        helper.assertTrue(definition != null, "真实外壳测试模块未注册");
        var pos = helper.absolutePos(MoltenOreTestLayout.modulePosition());
        var moduleBase = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation("kubejs", "module_base"));
        helper.assertTrue(moduleBase != null, "缺少 module_base");
        for (BlockPos base : realBaseOffsets(pos)) {
            level.setBlockAndUpdate(base, moduleBase.defaultBlockState());
        }
        placePart(helper, player, GTMachines.ITEM_IMPORT_BUS[2], pos.offset(-2, 0, 1));
        placePart(helper, player, GTMachines.ENERGY_INPUT_HATCH[1], pos.offset(-2, 0, -1));
        var sinkDefinition = GTRegistries.MACHINES.get(
                new ResourceLocation("gtlmodular", "buffered_me_output_buffer"));
        helper.assertTrue(sinkDefinition != null, "ME 输出总成未注册");
        var sink = GameTestMachines.place(helper, player, sinkDefinition, pos.offset(2, 0, -1),
                Direction.NORTH);
        sink.setFrontFacing(Direction.EAST);
        helper.assertTrue(sink instanceof MEExtendedOutputPartMachine, "ME 输出总成类型不符");
        var placed = GameTestMachines.place(helper, player, definition, pos, Direction.NORTH);
        helper.assertTrue(placed instanceof WorkableElectricMultiblockMachine, "真实外壳类型不符");
        var module = (WorkableElectricMultiblockMachine) placed;
        build(helper, module, pos, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(host.isFormed() && module.isFormed(),
                        "测试主机或真实 15×31×11 外壳未成形"))
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
                        "真实外壳与测试主机未连接"))
                .thenExecute(() -> setupAe(level, pos))
                .thenWaitUntil(() -> helper.assertTrue(aeActive(level, pos)
                        && sink instanceof MEExtendedOutputPartMachine out && out.getMainNode().isActive(),
                        "真实 ME 网络或输出总成节点未激活"))
                .thenExecute(() -> {
                    GTRecipe recipe = GTRecipeBuilder.of(
                                    new ResourceLocation("gtlmodular", "real_shell_item"),
                                    module.getRecipeType())
                            .inputItems(new ItemStack(Items.STONE))
                            .outputItems(new ItemStack(Items.DIRT))
                            .duration(20)
                            .EUt(32)
                            .buildRawRecipe();
                    module.getRecipeType().getLookup().addRecipe(recipe);
                    GTRecipe sand = GTRecipeBuilder.of(
                                    new ResourceLocation("gtlmodular", "real_shell_sand"),
                                    module.getRecipeType())
                            .inputItems(new ItemStack(Items.COBBLESTONE))
                            .outputItems(new ItemStack(Items.SAND))
                            .duration(20)
                            .EUt(32)
                            .buildRawRecipe();
                    module.getRecipeType().getLookup().addRecipe(sand);
                    if (MetaMachine.getMachine(level, pos.offset(-2, 0, 1)) instanceof ItemBusPartMachine bus) {
                        bus.getInventory().setStackInSlot(0, new ItemStack(Items.STONE, 64));
                        bus.getInventory().setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
                    }
                    if (MetaMachine.getMachine(level, pos.offset(-2, 0, -1)) instanceof EnergyHatchPartMachine hatch) {
                        hatch.energyContainer.setEnergyStored(hatch.energyContainer.getEnergyCapacity());
                    }
                })
                .thenExecute(() -> {
                    try {
                        Object controller = module.getRecipeLogic().getClass()
                                .getMethod("gtlmodular$controller", boolean.class)
                                .invoke(module.getRecipeLogic(), true);
                        helper.assertTrue(controller != null, "真实外壳没有建立独立线程控制器");
                        CONTROLLER[0] = controller;
                    } catch (ReflectiveOperationException failure) {
                        helper.fail("无法检查真实外壳控制器：" + failure);
                    }
                })
                .thenExecute(() -> module.getRecipeLogic().updateTickSubscription())
                .thenWaitUntil(() -> helper.assertTrue(ownsWork(), "真实外壳引擎没有开批: reason=" + reason()))
                .thenWaitUntil(() -> helper.assertTrue(batchCount() >= 2, "没有并行两批: " + batchCount()))
                .thenWaitUntil(() -> helper.assertTrue(realOutput(level, pos),
                        "真实外壳没有输出物品: " + testBatchInfo()))
                .thenSucceed();
    }

    private static void setupAe(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        BlockPos cellPos = pos.offset(3, 0, -1);
        BlockPos drivePos = pos.offset(4, 0, -1);
        level.setBlockAndUpdate(cellPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(drivePos, AEBlocks.DRIVE.block().defaultBlockState());
        if (level.getBlockEntity(drivePos) instanceof DriveBlockEntity drive) {
            drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        }
    }

    private static boolean aeActive(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos.offset(4, 0, -1)) instanceof DriveBlockEntity drive
                && drive.getMainNode().isActive();
    }

    private static boolean realOutput(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        if (MetaMachine.getMachine(level, pos.offset(-2, 0, -1)) instanceof EnergyHatchPartMachine hatch) {
            hatch.energyContainer.setEnergyStored(hatch.energyContainer.getEnergyCapacity());
        }
        if (!(level.getBlockEntity(pos.offset(4, 0, -1)) instanceof DriveBlockEntity drive)) return false;
        MEStorage storage = drive.getCellInventory(0);
        return storage != null
                && storage.extract(AEItemKey.of(new ItemStack(Items.DIRT)), 1L,
                        Actionable.SIMULATE, IActionSource.empty()) > 0L
                && storage.extract(AEItemKey.of(new ItemStack(Items.SAND)), 1L,
                        Actionable.SIMULATE, IActionSource.empty()) > 0L;
    }

    private static BlockPos[] realBaseOffsets(BlockPos p) {
        return new BlockPos[]{
                p.offset(-2, -4, 6), p.offset(2, -4, 6), p.offset(-2, -4, 5), p.offset(2, -4, 5),
                p.offset(-2, -4, 4), p.offset(2, -4, 4), p.offset(-2, -3, 4), p.offset(2, -3, 4),
                p.offset(-2, -4, 3), p.offset(2, -4, 3), p.offset(-2, -3, 3), p.offset(2, -3, 3),
                p.offset(-2, -4, 2), p.offset(2, -4, 2), p.offset(-2, -3, 2), p.offset(2, -3, 2),
                p.offset(-2, -4, 1), p.offset(2, -4, 1), p.offset(-2, -3, 1), p.offset(2, -3, 1),
                p.offset(-2, -4, 0), p.offset(2, -4, 0), p.offset(-2, -3, 0), p.offset(2, -3, 0),
                p.offset(-2, -4, -1), p.offset(2, -4, -1), p.offset(-2, -3, -1), p.offset(-1, -3, -1),
                p.offset(0, -3, -1), p.offset(1, -3, -1), p.offset(2, -3, -1), p.offset(-2, -4, -2),
                p.offset(2, -4, -2), p.offset(-2, -3, -2), p.offset(-1, -3, -2), p.offset(0, -3, -2),
                p.offset(1, -3, -2), p.offset(2, -3, -2), p.offset(-2, -4, -3), p.offset(-1, -4, -3),
                p.offset(0, -4, -3), p.offset(1, -4, -3), p.offset(2, -4, -3), p.offset(-2, -3, -3),
                p.offset(-1, -3, -3), p.offset(0, -3, -3), p.offset(1, -3, -3), p.offset(2, -3, -3),
                p.offset(-2, -4, -4), p.offset(-1, -4, -4), p.offset(0, -4, -4), p.offset(1, -4, -4),
                p.offset(2, -4, -4)
        };
    }

    private static void addFairRecipe(GameTestHelper helper, WorkableElectricMultiblockMachine module,
                                      net.minecraft.world.level.material.Fluid fluid, String id) {
        GTRecipe recipe = GTRecipeBuilder.of(new ResourceLocation("gtlmodular", id), module.getRecipeType())
                .inputFluids(FluidStack.create(fluid, 1_000L))
                .outputFluids(FluidStack.create(fluid, 1_000L))
                .duration(20)
                .EUt(32)
                .buildRawRecipe();
        module.getRecipeType().getLookup().addRecipe(recipe);
    }

    private static void fillFairInputs(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        if (MetaMachine.getMachine(level, pos.offset(-1, 0, -1)) instanceof FluidHatchPartMachine water) {
            water.tank.setFluidInTank(0, FluidStack.create(Fluids.WATER, 1_000L));
        }
        if (MetaMachine.getMachine(level, pos.offset(1, 0, -1)) instanceof FluidHatchPartMachine lava) {
            lava.tank.setFluidInTank(0, FluidStack.create(Fluids.LAVA, 1_000L));
        }
    }

    private static boolean fairTick(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        fillEnergy(level, pos);
        fillFairInputs(level, pos);
        if (MetaMachine.getMachine(level, pos.offset(1, 0, 1)) instanceof FluidHatchPartMachine output) {
            FluidStack fluid = output.tank.getFluidInTank(0);
            if (!fluid.isEmpty()) {
                FAIR_PRODUCED.add(fluid.getFluid().builtInRegistryHolder().key().location().toString());
                output.tank.setFluidInTank(0, FluidStack.empty());
            }
        }
        return FAIR_PRODUCED.contains("minecraft:water") && FAIR_PRODUCED.contains("minecraft:lava");
    }
    private static long outputAmount(net.minecraft.server.level.ServerLevel level, BlockPos pos,
                                     net.minecraft.world.level.material.Fluid fluid) {
        long total = 0L;
        for (BlockPos offset : new BlockPos[]{pos.offset(1, 0, -1), pos.offset(1, 0, 1)}) {
            if (MetaMachine.getMachine(level, offset) instanceof FluidHatchPartMachine hatch) {
                FluidStack stack = hatch.tank.getFluidInTank(0);
                if (stack.getFluid() == fluid) total += stack.getAmount();
            }
        }
        return total;
    }

    private static final Set<String> PRODUCED = new HashSet<>();

    private static void addLavaRecipe(GameTestHelper helper, WorkableElectricMultiblockMachine module) {
        PRODUCED.clear();
        GTRecipe recipe = GTRecipeBuilder.of(
                        new ResourceLocation("gtlmodular", "test_lava_recipe"), module.getRecipeType())
                .inputFluids(FluidStack.create(Fluids.LAVA, 1_000L))
                .outputFluids(FluidStack.create(Fluids.LAVA, 1_000L))
                .duration(20)
                .EUt(32)
                .buildRawRecipe();
        module.getRecipeType().getLookup().addRecipe(recipe);
    }

    private static boolean rotate(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        fillEnergy(level, pos);
        boolean waterSeen = PRODUCED.contains("minecraft:water");
        if (MetaMachine.getMachine(level, pos.offset(-1, 0, -1)) instanceof FluidHatchPartMachine input) {
            input.tank.setFluidInTank(0, FluidStack.create(waterSeen ? Fluids.LAVA : Fluids.WATER, 1_000L));
        }
        for (BlockPos offset : new BlockPos[]{pos.offset(1, 0, -1), pos.offset(1, 0, 1)}) {
            if (MetaMachine.getMachine(level, offset) instanceof FluidHatchPartMachine output) {
                FluidStack fluid = output.tank.getFluidInTank(0);
                if (!fluid.isEmpty()) {
                    PRODUCED.add(fluid.getFluid().builtInRegistryHolder().key().location().toString());
                }
            }
        }
        return PRODUCED.contains("minecraft:water") && PRODUCED.contains("minecraft:lava");
    }

    private static void fillEnergy(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        if (MetaMachine.getMachine(level, pos.offset(-1, 0, 1)) instanceof EnergyHatchPartMachine hatch) {
            hatch.energyContainer.setEnergyStored(hatch.energyContainer.getEnergyCapacity());
        }
    }

    private static long energyStored(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        if (MetaMachine.getMachine(level, pos.offset(-1, 0, 1)) instanceof EnergyHatchPartMachine hatch) {
            return hatch.energyContainer.getEnergyStored();
        }
        return -1L;
    }

    private static long energyCapacity(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        if (MetaMachine.getMachine(level, pos.offset(-1, 0, 1)) instanceof EnergyHatchPartMachine hatch) {
            return hatch.energyContainer.getEnergyCapacity();
        }
        return -1L;
    }

    private static boolean outputFluid(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        for (BlockPos offset : new BlockPos[]{pos.offset(1, 0, -1), pos.offset(1, 0, 1)}) {
            if (MetaMachine.getMachine(level, offset) instanceof FluidHatchPartMachine hatch
                    && hatch.tank.getFluidInTank(0).getAmount() > 0L) {
                return true;
            }
        }
        return false;
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

    private static String testBatchInfo() {
        try {
            return (String) CONTROLLER[0].getClass().getMethod("testBatchInfo").invoke(CONTROLLER[0]);
        } catch (ReflectiveOperationException failure) {
            return "?" + failure;
        }
    }

    private static int batchCount() {
        try {
            var field = CONTROLLER[0].getClass().getDeclaredField("session");
            field.setAccessible(true);
            Object session = field.get(CONTROLLER[0]);
            if (session == null) return 0;
            Object batches = session.getClass().getMethod("batches").invoke(session);
            return batches instanceof java.util.List<?> list ? list.size() : 0;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("无法读取批次数量", failure);
        }
    }

    private static void feed(GameTestHelper helper, WorkableElectricMultiblockMachine module, BlockPos pos) {
        GTRecipe recipe = GTRecipeBuilder.of(
                        new ResourceLocation("gtlmodular", "test_water_recipe"), module.getRecipeType())
                .inputFluids(FluidStack.create(Fluids.WATER, 1_000L))
                .outputFluids(FluidStack.create(Fluids.WATER, 1_000L))
                .duration(20)
                .EUt(32)
                .buildRawRecipe();
        module.getRecipeType().getLookup().addRecipe(recipe);
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
                    "结构未匹配：" + machine.getDefinition().getId() + " err="
                            + (state.error == null ? "none" : state.error.getErrorInfo().getString()));
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
