package com.gtlmodular.gametest.fixture;

import appeng.api.config.Actionable;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/** 使用上游公共接口，避免共享测试源码依赖完整版类或把它带进独立版。 */
public final class PatternAssemblyFixture {
    private final GameTestHelper helper;
    private final FakePlayer player;
    private final List<Long> forcedChunks = new ArrayList<>();
    private final String assemblyId;
    private WorkableElectricMultiblockMachine controller;
    private MEPatternBufferPartMachineBase assembly;
    private NotifiableEnergyContainer energy;
    private MaintenanceHatchPartMachine maintenance;

    public PatternAssemblyFixture(GameTestHelper helper, String assemblyId) {
        this.helper = helper;
        this.assemblyId = assemblyId;
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(20, 5, 20));
        UUID identity = UUID.nameUUIDFromBytes((assemblyId + center).getBytes(StandardCharsets.UTF_8));
        player = FakePlayerFactory.get(level, new GameProfile(identity, "PatternGateTest"));
        player.setGameMode(GameType.CREATIVE);
    }

    public void build() {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(20, 5, 20));
        for (int x = (center.getX() - 12) >> 4; x <= (center.getX() + 12) >> 4; x++) {
            for (int z = (center.getZ() - 12) >> 4; z <= (center.getZ() + 12) >> 4; z++) {
                long chunk = ChunkPos.asLong(x, z);
                if (!level.getForcedChunks().contains(chunk)) {
                    level.setChunkForced(x, z, true);
                    forcedChunks.add(chunk);
                }
                level.getChunk(x, z);
            }
        }
        controller = (WorkableElectricMultiblockMachine) place(definition("gtceu:large_assembler"), center);
        controller.getRecipeLogic().setWorkingEnabled(false);
        controller.getPatternLock().lock();
        try {
            MultiblockState probe = new MultiblockState(level, center);
            controller.getPattern().autoBuild(player, probe);
            helper.assertTrue(controller.getPattern().checkPatternAt(probe, true), "大型组装厂自动搭建失败");
            Map<BlockPos, TraceabilityPredicate> predicates = probe.getMatchContext().get("predicates");
            List<BlockPos> hatches = predicates.entrySet().stream()
                    .filter(entry -> Stream.concat(entry.getValue().common.stream(), entry.getValue().limited.stream())
                            .filter(part -> part.candidates != null)
                            .flatMap(part -> Arrays.stream(part.candidates.get()))
                            .anyMatch(info -> PartAbility.INPUT_ENERGY.isApplicable(info.getBlockState().getBlock())))
                    .map(Map.Entry::getKey).sorted(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ)).toList();
            helper.assertTrue(hatches.size() >= 3, "组装厂缺少合法仓位");
            var casing = controller.getDefinition().getAppearance().get();
            for (BlockPos pos : hatches) level.setBlockAndUpdate(pos, casing);
            assembly = (MEPatternBufferPartMachineBase) place(definition("gtlmodular:" + assemblyId), hatches.get(0));
            assembly.setFrontFacing(Direction.DOWN);
            MetaMachine hatch = place(GTMachines.ENERGY_INPUT_HATCH[2], hatches.get(1));
            energy = hatch.getTraits().stream().filter(NotifiableEnergyContainer.class::isInstance)
                    .map(NotifiableEnergyContainer.class::cast).findFirst().orElseThrow();
            maintenance = (MaintenanceHatchPartMachine) place(GTMachines.MAINTENANCE_HATCH, hatches.get(2));
            helper.assertTrue(controller.getPattern().checkPatternAt(probe, true),
                    "总成/能源/维护仓未匹配：" + probe.error);
            BlockPos battery = assembly.getPos().below();
            helper.assertTrue(!predicates.containsKey(battery), "AE 能量元件挤占结构");
            level.setBlockAndUpdate(battery, AEBlocks.ENERGY_CELL.block().defaultBlockState());
            var cell = (EnergyCellBlockEntity) level.getBlockEntity(battery);
            helper.assertTrue(cell != null && cell.injectAEPower(10000, Actionable.MODULATE) == 0,
                    "AE 能量元件未接受有限电量");
            // 复用较大的测试模板，但仍检查自动搭建和外接 AE 元件完全落在它的声明范围。
            BlockPos size = NacStructureLayout.current().size();
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            helper.assertTrue(helper.absolutePos(new BlockPos(1, 1, 1)).equals(origin.offset(1, 1, 1)),
                    "此 fixture 只覆盖无旋转模板");
            for (BlockPos pos : Stream.concat(predicates.keySet().stream(), Stream.of(battery)).toList()) {
                BlockPos relative = pos.subtract(origin);
                helper.assertTrue(relative.getX() >= 0 && relative.getY() >= 0 && relative.getZ() >= 0
                                && relative.getX() < size.getX() && relative.getY() < size.getY()
                                && relative.getZ() < size.getZ(), "组装厂越过测试模板：" + relative);
            }
        } finally {
            controller.getPatternLock().unlock();
        }
    }

    private MachineDefinition definition(String id) {
        var definition = GTRegistries.MACHINES.get(new ResourceLocation(id));
        if (definition == null) throw new IllegalStateException("缺少真实机器 " + id);
        return definition;
    }

    private MetaMachine place(MachineDefinition definition, BlockPos pos) {
        var level = helper.getLevel();
        level.removeBlock(pos, false);
        var stack = definition.asStack();
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() - 2);
        player.setYRot(0);
        var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        helper.assertTrue(((BlockItem) stack.getItem()).place(context).consumesAction(), "放置失败 " + definition.getId());
        var machine = MetaMachine.getMachine(level, pos);
        helper.assertTrue(machine != null, "机器实体未创建 " + definition.getId());
        machine.setFrontFacing(Direction.NORTH);
        return machine;
    }

    public void assertReady() {
        helper.assertTrue(controller.isFormed() && controller.getParts().contains(assembly)
                        && assembly.getControllers().contains(controller), "组装厂/样板总成未真实连接");
        helper.assertTrue(assembly.getMainNode().isActive(), "样板总成 AE 节点未供电/取得频道");
        helper.assertTrue(helper.getLevel().getChunkSource().isPositionTicking(new ChunkPos(controller.getPos()).toLong()),
                "组装厂区块不在 tick");
    }

    public void repairMaintenance() {
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, GTItems.DUCT_TAPE.asStack());
        int expectedTape = maintenance.hasMaintenanceProblems() ? 0 : 1;
        if (maintenance.hasMaintenanceProblems()) {
            BlockPos pos = maintenance.getPos();
            maintenance.onUse(helper.getLevel().getBlockState(pos), helper.getLevel(), pos, player,
                    InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        }
        helper.assertTrue(!maintenance.hasMaintenanceProblems() && player.getMainHandItem().getCount() == expectedTape,
                "维护失败或胶带不守恒");
        player.setGameMode(GameType.CREATIVE);
    }

    public void fillEnergy() {
        long amount = energy.getEnergyCapacity() - energy.getEnergyStored();
        helper.assertTrue(energy.changeEnergy(amount) == amount, "有限能源仓未接受电量");
    }

    public long stored() { return energy.getEnergyStored(); }
    public WorkableElectricMultiblockMachine controller() { return controller; }
    public MEPatternBufferPartMachineBase assembly() { return assembly; }

    public void releaseChunks() {
        for (long chunk : forcedChunks) {
            helper.getLevel().setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
        }
        forcedChunks.clear();
    }
}
