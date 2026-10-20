package com.gtlmodular.gametest;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.gametest.fixture.GameTestMachines;
import com.gtlmodular.gametest.fixture.NacStructureLayout;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEExtendedOutputPartMachine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/** 带合并的 ME 输出总成：验证过滤作废、日志只报一次、以及交回余量模式。 */
public final class BufferedOutputBufferTests {
    private static final String BATCH = "buffered_output";
    private static final List<String> captured = new ArrayList<>();
    private static Appender capture;

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        if (!NanochipModContext.modId().equals("gtlmodular")) {
            return List.of();
        }
        return List.of(
                function("void_simulate", BufferedOutputBufferTests::voidModeSimulateReturnsEmpty),
                function("void_log_once", BufferedOutputBufferTests::voidModeLogsOnceWithCoords),
                function("return_mode", BufferedOutputBufferTests::returnModeKeepsFiltered),
                function("offline_keeps_remainder", BufferedOutputBufferTests::offlineKeepsRemainder),
                function("storage_simulate", BufferedOutputBufferTests::storageSimulate));
    }

    @AfterBatch(batch = BATCH + "_void_simulate")
    public static void cleanupVoidSimulate(ServerLevel level) {
        setVoidFiltered(true);
        detachCapture();
    }

    @AfterBatch(batch = BATCH + "_void_log_once")
    public static void cleanupVoidLog(ServerLevel level) {
        setVoidFiltered(true);
        detachCapture();
    }

    @AfterBatch(batch = BATCH + "_return_mode")
    public static void cleanupReturnMode(ServerLevel level) {
        setVoidFiltered(true);
        detachCapture();
    }

    private static TestFunction function(String name, Consumer<GameTestHelper> body) {
        return new TestFunction(BATCH + "_" + name, "gtlmodular.buffered_output_" + name,
                NacStructureLayout.templateId().toString(), 4800, 0, true, body);
    }

    /** 过滤命中的产物不进余量，引擎才会认为 outputFits=true 并允许开批。 */
    private static void voidModeSimulateReturnsEmpty(GameTestHelper helper) {
        setVoidFiltered(true);
        var part = build(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(part.getMainNode().isActive(), "AE 节点未激活"))
                .thenExecute(() -> applyBlacklist(helper, part))
                .thenExecute(() -> helper.assertTrue(handle(part, true).isEmpty(),
                        "作废模式下 simulate 必须返回空余量，否则批次开不了"))
                .thenExecute(() -> helper.assertTrue(handle(part, false).isEmpty(),
                        "作废模式下真实交付不应把 filtered 留在余量"))
                .thenSucceed();
    }

    /** 作废统计一次性上报，坐标要指向这台机器。 */
    private static void voidModeLogsOnceWithCoords(GameTestHelper helper) {
        setVoidFiltered(true);
        var part = build(helper);
        attachCapture();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(part.getMainNode().isActive(), "AE 节点未激活"))
                .thenExecute(() -> applyBlacklist(helper, part))
                .thenExecute(() -> {
                    handle(part, false);
                    handle(part, false);
                })
                .thenExecute(() -> {
                    var pos = part.getPos();
                    helper.assertTrue(captured.size() == 1, "作废日志应只出现一次，实际 " + captured.size());
                    helper.assertTrue(captured.get(0).contains("BlockPos{x=" + pos.getX()),
                            "作废日志坐标不对: " + captured.get(0));
                })
                .thenSucceed();
    }

    /** 关掉开关后，被过滤的产物原样回到余量。 */
    private static void returnModeKeepsFiltered(GameTestHelper helper) {
        var part = build(helper);
        setVoidFiltered(false);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(part.getMainNode().isActive(), "AE 节点未激活"))
                .thenExecute(() -> applyBlacklist(helper, part))
                .thenExecute(() -> helper.assertTrue(handle(part, true).size() == 1,
                        "交回模式下 simulate 应把 filtered 留在余量"))
                .thenExecute(() -> helper.assertTrue(handle(part, false).size() == 1,
                        "交回模式下真实交付应把 filtered 留在余量"))
                .thenSucceed();
    }

    /** ME 离线时 simulate 必须返回余量（批次不开），真实交付也不能丢产物。 */
    private static void offlineKeepsRemainder(GameTestHelper helper) {
        var part = buildOffline(helper);
        helper.startSequence()
                .thenExecute(() -> helper.assertFalse(part.getMainNode().isActive(), "无供电时 AE 节点不应激活"))
                .thenExecute(() -> helper.assertTrue(handle(part, true).size() == 1,
                        "ME 离线时 simulate 必须返回余量，否则会开出永远出不了料的批次"))
                .thenExecute(() -> helper.assertTrue(handle(part, false).size() == 1,
                        "ME 离线时产物必须留在余量，不能丢"))
                .thenSucceed();
    }

    private static MEExtendedOutputPartMachine buildOffline(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(20, 5, 20));
        level.setBlockAndUpdate(pos.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        MachineDefinition definition = GTRegistries.MACHINES.get(
                new ResourceLocation("gtlmodular", "buffered_me_output_buffer"));
        helper.assertTrue(definition != null, "机器未注册: buffered_me_output_buffer");
        var machine = GameTestMachines.place(helper, GameTestMachines.player(helper, "BufferedOutputOffline"),
                definition, pos, Direction.NORTH);
        helper.assertTrue(machine instanceof MEExtendedOutputPartMachine, "机器类型不符");
        return (MEExtendedOutputPartMachine) machine;
    }

    private static void storageSimulate(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(20, 5, 20));
        BlockPos cellPos = pos.east();
        BlockPos drivePos = cellPos.east();
        level.setBlockAndUpdate(cellPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(drivePos, AEBlocks.DRIVE.block().defaultBlockState());
        if (level.getBlockEntity(drivePos) instanceof DriveBlockEntity drive) {
            drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_256K.stack());
        }
        MachineDefinition definition = GTRegistries.MACHINES.get(
                new ResourceLocation("gtlmodular", "buffered_me_output_buffer"));
        helper.assertTrue(definition != null, "机器未注册: buffered_me_output_buffer");
        var machine = GameTestMachines.place(helper, GameTestMachines.player(helper, "BufferedStorage"),
                definition, pos, Direction.NORTH);
        machine.setFrontFacing(Direction.EAST);
        helper.assertTrue(machine instanceof MEExtendedOutputPartMachine, "机器类型不符");
        var part = (MEExtendedOutputPartMachine) machine;
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(part.getMainNode().isActive(),
                        "输出总成节点未激活"))
                .thenExecute(() -> helper.assertTrue(handleTwo(part, true).isEmpty(),
                        "有存储时 simulate 应返回空余量"))
                .thenExecute(() -> helper.assertTrue(handleTwo(part, false).isEmpty(),
                        "有存储时真实交付应成功"))
                .thenExecute(() -> {
                    if (level.getBlockEntity(drivePos) instanceof DriveBlockEntity drive) {
                        MEStorage storage = drive.getCellInventory(0);
                        helper.assertTrue(storage.extract(AEItemKey.of(new ItemStack(Items.DIRT)), 100_000L,
                                Actionable.SIMULATE, IActionSource.empty()) > 0L, "驱动里没有 dirt");
                        helper.assertTrue(storage.extract(AEItemKey.of(new ItemStack(Items.SAND)), 100_000L,
                                Actionable.SIMULATE, IActionSource.empty()) > 0L, "驱动里没有 sand");
                    }
                })
                .thenSucceed();
    }

    private static List<Ingredient> handleTwo(MEExtendedOutputPartMachine part, boolean simulate) {
        var contents = new ObjectArrayList<Ingredient>();
        contents.add(LongIngredient.create(Ingredient.of(new ItemStack(Items.DIRT)), 100_000L));
        contents.add(LongIngredient.create(Ingredient.of(new ItemStack(Items.SAND)), 100_000L));
        return part.getMERecipeHandlerTraits().left().meHandleRecipeOutput(contents, simulate);
    }

    private static List<Ingredient> handleDirt(MEExtendedOutputPartMachine part, boolean simulate) {
        var contents = new ObjectArrayList<Ingredient>();
        contents.add(LongIngredient.create(Ingredient.of(new ItemStack(Items.DIRT)), 100_000L));
        return part.getMERecipeHandlerTraits().left().meHandleRecipeOutput(contents, simulate);
    }

    private static MEExtendedOutputPartMachine build(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(20, 5, 20));
        BlockPos cellPos = pos.below();
        level.setBlockAndUpdate(cellPos, AEBlocks.ENERGY_CELL.block().defaultBlockState());
        var cell = (EnergyCellBlockEntity) level.getBlockEntity(cellPos);
        helper.assertTrue(cell != null, "AE 能量元件未创建");
        helper.assertTrue(cell.injectAEPower(100_000, Actionable.MODULATE) == 0, "AE 元件未接受电量");
        MachineDefinition definition = GTRegistries.MACHINES.get(
                new ResourceLocation("gtlmodular", "buffered_me_output_buffer"));
        helper.assertTrue(definition != null, "机器未注册: buffered_me_output_buffer");
        var machine = GameTestMachines.place(helper, GameTestMachines.player(helper, "BufferedOutput"),
                definition, pos, Direction.NORTH);
        // ME part 的节点在正面；能量元件在下侧，必须把正面转向下方才能连上。
        machine.setFrontFacing(Direction.DOWN);
        helper.assertTrue(machine instanceof MEExtendedOutputPartMachine, "机器类型不符");
        return (MEExtendedOutputPartMachine) machine;
    }

    private static void setVoidFiltered(boolean value) {
        try {
            Class<?> config = Class.forName("com.gtlmodular.common.config.GTLModularConfig");
            config.getMethod("setOutputAssemblyVoidFiltered", boolean.class).invoke(null, value);
            Object actual = config.getMethod("isOutputAssemblyVoidFiltered").invoke(null);
            if (!Boolean.valueOf(value).equals(actual)) {
                throw new IllegalStateException("outputAssembly.voidFiltered 设置未生效: " + actual);
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("无法设置 outputAssembly.voidFiltered", failure);
        }
    }

    private static void applyBlacklist(GameTestHelper helper, MEExtendedOutputPartMachine part) {
        var filter = part.getFilterHandler();
        filter.setItemFilter(0, AEItemKey.of(new ItemStack(Items.IRON_INGOT)));
        filter.updateItemFilterSettings(true, true);
        helper.assertTrue(filter.isHasItemFilter(), "过滤槽未生效");
    }

    /** 用真实入口（meHandleRecipeOutput）走一遍：过滤命中 64 个铁锭。 */
    private static List<Ingredient> handle(MEExtendedOutputPartMachine part, boolean simulate) {
        var contents = new ObjectArrayList<Ingredient>();
        contents.add(LongIngredient.create(Ingredient.of(new ItemStack(Items.IRON_INGOT)), 64));
        return part.getMERecipeHandlerTraits().left().meHandleRecipeOutput(contents, simulate);
    }

    private static void attachCapture() {
        captured.clear();
        capture = new AbstractAppender("buffered-output-capture", null, null, false) {
            @Override
            public void append(LogEvent event) {
                String message = event.getMessage().getFormattedMessage();
                if (message.contains("作废被过滤的产物")) {
                    captured.add(message);
                }
            }
        };
        capture.start();
        ((Logger) LogManager.getLogger("GTL-Modular")).addAppender(capture);
    }

    private static void detachCapture() {
        if (capture != null) {
            ((Logger) LogManager.getLogger("GTL-Modular")).removeAppender(capture);
            capture.stop();
            capture = null;
        }
    }
}
