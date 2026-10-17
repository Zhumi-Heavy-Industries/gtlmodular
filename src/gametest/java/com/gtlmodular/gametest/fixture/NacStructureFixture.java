package com.gtlmodular.gametest.fixture;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gtlmodular.common.data.NanochipBlocks;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.NanochipModuleMachine;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class NacStructureFixture {

    private final GameTestHelper helper;
    private final ServerLevel level;
    private final FakePlayer player;
    private final MachineDefinition moduleEnergyHatch;
    private final ResourceLocation moduleId;
    private final boolean installFluidOutput;
    private final NacStructureLayout layout = NacStructureLayout.current();
    private List<BlockPos> moduleLaserSlots = List.of();
    private final List<Long> forcedChunks = new ArrayList<>();
    private final List<BlockPos> hostHatches = new ArrayList<>();
    private final List<BlockPos> moduleHatches = new ArrayList<>();
    private NanochipAssemblyComplexMachine host;
    private NanochipModuleMachine module;
    private BlockPos hostKey;
    private BlockPos moduleKey;
    private BlockPos removedPosition;
    private BlockState removedState;

    public NacStructureFixture(GameTestHelper helper) {
        this(helper, true);
    }

    public NacStructureFixture(GameTestHelper helper, boolean moduleEnergyHatch) {
        this(helper, moduleEnergyHatch ? GTMachines.ENERGY_INPUT_HATCH[1] : null);
    }

    public NacStructureFixture(GameTestHelper helper, MachineDefinition moduleEnergyHatch) {
        this(helper, moduleEnergyHatch, null, true);
    }

    public NacStructureFixture(GameTestHelper helper, MachineDefinition moduleEnergyHatch, ResourceLocation moduleId,
                               boolean installFluidOutput) {
        this.helper = helper;
        this.moduleEnergyHatch = moduleEnergyHatch;
        this.moduleId = moduleId;
        this.installFluidOutput = installFluidOutput;
        this.level = helper.getLevel();
        UUID identity = UUID.nameUUIDFromBytes((level.dimension().location() + ":" +
                helper.absolutePos(layout.hostPosition())).getBytes(StandardCharsets.UTF_8));
        this.player = FakePlayerFactory.get(level, new GameProfile(identity, "NacStructureTest"));
        player.setGameMode(GameType.CREATIVE);
    }

    public void build() {
        moduleLaserSlots = List.of();
        BlockPos min = helper.absolutePos(BlockPos.ZERO);
        BlockPos max = helper.absolutePos(layout.size().offset(-1, -1, -1));
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                long chunk = net.minecraft.world.level.ChunkPos.asLong(x, z);
                if (!level.getForcedChunks().contains(chunk)) {
                    level.setChunkForced(x, z, true);
                    forcedChunks.add(chunk);
                }
                level.getChunk(x, z);
            }
        }
        // Forge 在地下运行 GameTest；隔开天然落沙，避免外界砂砾落进模块必须为空气的格子。
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                level.setBlockAndUpdate(new BlockPos(x, max.getY(), z), Blocks.BARRIER.defaultBlockState());
            }
        }
        level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                new net.minecraft.world.phys.AABB(min, max.offset(1, 1, 1))).forEach(entity -> entity.discard());
        host = (NanochipAssemblyComplexMachine) placeMachine(layout.host().definition(),
                helper.absolutePos(layout.hostPosition()));
        Map<BlockPos, TraceabilityPredicate> hostPattern = buildAndFitHatches(host, hostHatches);
        module = (NanochipModuleMachine) placeMachine(moduleDefinition(),
                helper.absolutePos(layout.modulePosition()));
        Map<BlockPos, TraceabilityPredicate> modulePattern = buildAndFitHatches(module, moduleHatches);
        hostKey = keyBlock(hostPattern, false);
        moduleKey = keyBlock(modulePattern, true);
        log("built");
    }

    private MetaMachine placeMachine(MachineDefinition definition, BlockPos pos) {
        // F 位预填的是机壳，先腾空才能让 BlockItem 走正常放置和所有者绑定。
        level.removeBlock(pos, false);
        ItemStack stack = definition.asStack();
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() - 2);
        player.setYRot(0);
        BlockPlaceContext context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        helper.assertTrue(stack.getItem() instanceof BlockItem, "机器没有方块物品：" + definition.getId());
        helper.assertTrue(((BlockItem) stack.getItem()).place(context).consumesAction(), "机器放置失败：" + pos);
        MetaMachine machine = MetaMachine.getMachine(level, pos);
        helper.assertTrue(machine != null, "机器实体未创建：" + pos);
        machine.setFrontFacing(Direction.NORTH);
        return machine;
    }

    private Map<BlockPos, TraceabilityPredicate> buildAndFitHatches(MultiblockControllerMachine machine,
                                                                  List<BlockPos> hatches) {
        machine.getPatternLock().lock();
        try {
            // 单独的匹配上下文避免自动搭建清空机器正在使用的状态；成形仍由服务器完成。
            MultiblockState probe = new MultiblockState(level, machine.getPos());
            machine.getPattern().autoBuild(player, probe);
            helper.assertTrue(machine.getPattern().checkPatternAt(probe, true),
                    "自动搭建未匹配：" + describe(machine, probe));
            Map<BlockPos, TraceabilityPredicate> predicates = probe.getMatchContext().get("predicates");
            Map<TraceabilityPredicate, Boolean> hatchPredicates = new IdentityHashMap<>();
            List<BlockPos> positions = predicates.entrySet().stream()
                    .filter(entry -> hatchPredicates.computeIfAbsent(entry.getValue(), this::isHatchSlot))
                    .map(Map.Entry::getKey).sorted(BlockPos::compareTo).toList();
            List<MachineDefinition> definitions = new ArrayList<>(List.of(GTMachines.ITEM_IMPORT_BUS[1],
                    GTMachines.ITEM_EXPORT_BUS[1], GTMachines.FLUID_IMPORT_HATCH[1],
                    GTMachines.FLUID_EXPORT_HATCH[1], GTMachines.ENERGY_INPUT_HATCH[1],
                    GTMachines.MAINTENANCE_HATCH));
            if (machine == module) {
                if (moduleEnergyHatch == null) definitions.remove(GTMachines.ENERGY_INPUT_HATCH[1]);
                else definitions.set(4, moduleEnergyHatch);
                if (!installFluidOutput) definitions.remove(GTMachines.FLUID_EXPORT_HATCH[1]);
                moduleLaserSlots = positions.stream()
                        .filter(pos -> acceptsLaser(predicates.get(pos))).toList();
            }
            if (positions.size() < definitions.size()) {
                helper.fail("缺少合法仓位：" + machine.getDefinition().getId() + "@" + machine.getPos()
                        + " count=" + positions.size() + " predicates="
                        + predicates.values().stream().distinct().map(predicate ->
                        Stream.concat(predicate.common.stream(), predicate.limited.stream())
                                .filter(part -> part.candidates != null)
                                .map(part -> part.slotName + ":" + Arrays.stream(part.candidates.get())
                                        .map(info -> info.getBlockState().toString()).toList()).toList()).toList());
            }
            // 自动搭建会填入多余代表仓；固定这组真实仓室，便于后续加工用例复用。
            for (BlockPos pos : positions) {
                level.setBlockAndUpdate(pos, NanochipBlocks.MESH_INTERFACE_CASING.get().defaultBlockState());
            }
            for (int i = 0; i < definitions.size(); i++) {
                placeMachine(definitions.get(i), positions.get(i));
                hatches.add(positions.get(i));
            }
            helper.assertTrue(machine.getPattern().checkPatternAt(probe, true),
                    "显式仓室未匹配：" + describe(machine, probe));
            return Map.copyOf(probe.getMatchContext().get("predicates"));
        } finally {
            machine.getPatternLock().unlock();
        }
    }

    private boolean isHatchSlot(TraceabilityPredicate predicate) {
        // AIR 是上游共享单例，F 位赋予它的槽名也会出现在模块 V 位，不能据此排除 V 位。
        if (Stream.concat(predicate.common.stream(), predicate.limited.stream())
                .anyMatch(part -> part != SimplePredicate.AIR
                        && NanochipComplexPattern.MODULE_SLOT_NAME.equals(part.slotName))) return false;
        return Stream.concat(predicate.common.stream(), predicate.limited.stream())
                .filter(part -> part.candidates != null)
                .flatMap(part -> Arrays.stream(part.candidates.get()))
                .anyMatch(info -> PartAbility.INPUT_ENERGY.isApplicable(info.getBlockState().getBlock()));
    }

    /** 组合谓词里出现激光仓候选，才说明这一格是塔身单独开出来的激光仓格。 */
    private boolean acceptsLaser(TraceabilityPredicate predicate) {
        if (predicate == null) {
            return false;
        }
        return Stream.concat(predicate.common.stream(), predicate.limited.stream())
                .filter(part -> part.candidates != null)
                .flatMap(part -> Arrays.stream(part.candidates.get()))
                .anyMatch(info -> PartAbility.INPUT_LASER.isApplicable(info.getBlockState().getBlock()));
    }

    private BlockPos keyBlock(Map<BlockPos, TraceabilityPredicate> pattern, boolean tower) {
        return pattern.keySet().stream().sorted(BlockPos::compareTo)
                .filter(pos -> level.getBlockState(pos).is(NanochipBlocks.REINFORCEMENT_CASING.get()))
                .filter(pos -> !tower || pos.getY() > module.getPos().getY())
                .filter(pos -> tower || Math.abs(pos.getX() - module.getPos().getX()) > 7)
                .findFirst().orElseThrow(() -> new IllegalStateException("未找到独立的关键结构块"));
    }

    public void assertConnected() {
        helper.assertTrue(level.getChunkSource().isPositionTicking(new net.minecraft.world.level.ChunkPos(host.getPos()).toLong())
                        && level.getChunkSource().isPositionTicking(new net.minecraft.world.level.ChunkPos(module.getPos()).toLong()),
                "控制器区块没有 tick；" + diagnostics());
        helper.assertTrue(host.isFormed() && module.isFormed(), "主机/矩阵尚未成形；" + diagnostics());
        helper.assertTrue(module.hostConnected() && module.getHost() == host
                        && host.getPos().equals(module.getHostPosition())
                        && host.getModules().size() == 1 && host.isModuleConnected(module),
                "双向连接不一致；" + diagnostics());
        helper.assertTrue(NanochipComplexPattern.moduleSlotsOf(host.getMultiblockState()).contains(module.getPos()),
                "矩阵不在真实匹配的 F 位：" + module.getPos());
        assertParts(host, hostHatches);
        assertParts(module, moduleHatches);
    }

    private void assertParts(MultiblockControllerMachine machine, List<BlockPos> positions) {
        helper.assertTrue(positions.stream().allMatch(pos -> machine.getParts().stream()
                        .anyMatch(part -> part.self().getPos().equals(pos))),
                "显式仓室未被控制器接纳；" + diagnostics());
    }

    public void breakHost() { removeKey(hostKey); }

    public void breakModule() { removeKey(moduleKey); }

    private void removeKey(BlockPos pos) {
        removedPosition = pos;
        removedState = level.getBlockState(pos);
        helper.assertTrue(player.gameMode.destroyBlock(pos), "关键块未拆除：" + pos);
        helper.assertTrue(level.isEmptyBlock(pos), "拆除后仍有方块：" + pos);
        log("removed " + pos + " " + removedState);
    }

    public void restore() {
        helper.assertTrue(level.setBlockAndUpdate(removedPosition, removedState), "关键块恢复失败");
        log("restored " + removedPosition);
    }

    public void assertHostDisconnected() {
        // 主机失形保留关联以便恢复；有效连接还要求主机成形，不能用成员表代替这个契约。
        helper.assertTrue(!host.isFormed() && !module.hostConnected(),
                "主机破坏后未失形/断联；" + diagnostics());
        helper.assertTrue(host.isModuleConnected(module) == (module.getHost() == host),
                "失形期间关联变成单向；" + diagnostics());
        helper.assertTrue(module.isFormed(), "主机负例误拆了矩阵结构；" + diagnostics());
    }

    public void assertModuleDisconnected() {
        helper.assertTrue(!module.isFormed() && !module.hostConnected()
                        && host.getModules().isEmpty() && module.getHost() == null,
                "矩阵破坏后未失形/断联；" + diagnostics());
        helper.assertTrue(host.isFormed(), "矩阵负例误拆了主机结构；" + diagnostics());
    }

    public void releaseChunks() {
        for (long chunk : forcedChunks) {
            level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(chunk),
                    net.minecraft.world.level.ChunkPos.getZ(chunk), false);
        }
        forcedChunks.clear();
    }

    private MachineDefinition moduleDefinition() {
        if (moduleId == null) {
            return layout.module().definition();
        }
        MachineDefinition definition = GTRegistries.MACHINES.get(moduleId);
        helper.assertTrue(definition != null, "模块未注册：" + moduleId);
        return definition;
    }

    /** 蚀刻阵列塔身 H 格单独开放激光仓；其它模块没有这种格子。 */
    public List<BlockPos> moduleLaserSlots() {
        return moduleLaserSlots;
    }

    public void installLaserInModuleLaserSlot(int index) {
        helper.assertTrue(index >= 0 && index < moduleLaserSlots.size(),
                "模块没有第 " + index + " 个激光仓格（实有 " + moduleLaserSlots.size() + " 个）");
        installLaser(moduleLaserSlots.get(index));
    }

    public void installLaserInModuleHatchSlot(int index) {
        helper.assertTrue(index >= 0 && index < moduleHatches.size(),
                "模块没有第 " + index + " 个普通仓格（实有 " + moduleHatches.size() + " 个）");
        installLaser(moduleHatches.get(index));
    }

    /** 激光仓只开给塔身 H 格；挑一个普通 V 格放第 2 个激光仓，验证它不被接纳。 */
    public void installLaserInModuleOrdinaryHatchSlot() {
        for (BlockPos pos : moduleHatches) {
            if (!moduleLaserSlots.contains(pos)) {
                installLaser(pos);
                return;
            }
        }
        helper.fail("模块没有可对照的普通仓格：" + moduleHatches.size());
    }

    public void installLaserInHostHatchSlot(int index) {
        helper.assertTrue(index >= 0 && index < hostHatches.size(),
                "主机没有第 " + index + " 个仓格（实有 " + hostHatches.size() + " 个）");
        installLaser(hostHatches.get(index));
    }

    private void installLaser(BlockPos pos) {
        helper.assertTrue(GTMachines.LASER_INPUT_HATCH_256.length > 5
                        && GTMachines.LASER_INPUT_HATCH_256[5] != null,
                "激光源仓未注册：" + GTMachines.LASER_INPUT_HATCH_256.length);
        placeMachine(GTMachines.LASER_INPUT_HATCH_256[5], pos);
    }

    /** 20 个部装外壳格的实际世界坐标；只对朝北放置的矩阵成立。 */
    public List<BlockPos> assemblyCellPositions() {
        helper.assertTrue(module != null, "模块尚未放置");
        helper.assertTrue(module.getFrontFacing() == Direction.NORTH,
                "装配格坐标只覆盖 NORTH 朝向，当前 " + module.getFrontFacing());
        int[] cells = NanochipComplexPattern.moduleAssemblyCells(module.getDefinition().getId().toString());
        helper.assertTrue(cells != null && cells.length > 0 && cells.length % 3 == 0,
                "没有登记装配格：" + module.getDefinition().getId());
        List<BlockPos> out = new ArrayList<>(cells.length / 3);
        int[] offset = new int[3];
        for (int i = 0; i < cells.length / 3; i++) {
            int base = i * 3;
            NanochipComplexPattern.elementOffset(0, NanochipComplexPattern.MOD_CTRL_X - cells[base],
                    cells[base + 1] - NanochipComplexPattern.MOD_CTRL_ROW,
                    NanochipComplexPattern.MOD_CTRL_Z - cells[base + 2], offset);
            out.add(module.getPos().offset(offset[0], offset[1], offset[2]));
        }
        return out;
    }

    public int replaceAssemblyCasings(Block block) {
        List<BlockPos> cells = assemblyCellPositions();
        for (BlockPos pos : cells) {
            if (!level.getBlockState(pos).is(block)) {
                helper.assertTrue(level.setBlockAndUpdate(pos, block.defaultBlockState()),
                        "部装外壳替换失败：" + pos);
            }
        }
        return cells.size();
    }

    public void replaceAssemblyCasing(Block block, int index) {
        List<BlockPos> cells = assemblyCellPositions();
        helper.assertTrue(index >= 0 && index < cells.size(),
                "装配格序号越界：" + index + "/" + cells.size());
        BlockPos pos = cells.get(index);
        helper.assertTrue(level.setBlockAndUpdate(pos, block.defaultBlockState()),
                "部装外壳替换失败：" + pos);
    }

    /** 外壳档是包内实现，测试用反射读取；改名时按用例失败报出，不静默跳过。 */
    public int moduleHousingTier() {
        try {
            Method method = Class.forName("com.gtlmodular.common.machine.nanochip.NanochipAssemblyMatrixTier")
                    .getDeclaredMethod("machineTier", NanochipModuleMachine.class);
            method.setAccessible(true);
            return (int) method.invoke(null, module);
        } catch (ReflectiveOperationException failure) {
            throw new GameTestAssertException("无法读取组装矩阵外壳档：" + failure);
        }
    }

    public void assertHostNotFormed(String expectation) {
        helper.assertTrue(!host.isFormed(), expectation + "；" + diagnostics());
    }

    public void assertModuleNotFormed(String expectation) {
        helper.assertTrue(!module.isFormed(), expectation + "；" + diagnostics());
    }

    public NanochipAssemblyComplexMachine host() { return host; }

    public NanochipModuleMachine module() { return module; }

    public void refreshMachinesAfterLoad() {
        host = (NanochipAssemblyComplexMachine) MetaMachine.getMachine(level, helper.absolutePos(layout.hostPosition()));
        module = (NanochipModuleMachine) MetaMachine.getMachine(level, helper.absolutePos(layout.modulePosition()));
    }

    public FakePlayer player() { return player; }

    public void log(String phase) {
        LogUtils.getLogger().info("GameTest NAC structure: phase={} tick={} bounds={} {}", phase,
                helper.getTick(), layout.size(), diagnostics());
    }

    private String diagnostics() {
        return "host=" + (host == null ? "absent" : describe(host, host.getMultiblockState()))
                + " module=" + (module == null ? "absent" : describe(module, module.getMultiblockState()))
                + " connected=" + (module != null && module.hostConnected())
                + " modules=" + (host == null ? -1 : host.getModules().size())
                + " changed=" + removedPosition
                + (removedPosition == null ? "" : " block=" + level.getBlockState(removedPosition));
    }

    private String describe(MultiblockControllerMachine machine, MultiblockState state) {
        return machine.getDefinition().getId() + "@" + machine.getPos().toShortString()
                + "/" + machine.getFrontFacing() + "/" + machine.getUpwardsFacing()
                + " formed=" + machine.isFormed() + " error="
                + NanochipAssemblyComplexMachine.describeStructureFailure(state);
    }
}
