package com.gtlmodular.gametest;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.gametest.fixture.NacStructureLayout;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class IndependentOwnershipTests {
    private static final String ROOT = "gtlmodular_independent_threads";
    private static final String TOKEN = "gtlmodular_independent_transfer";

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        if (!NanochipModContext.modId().equals("gtlmodular")) return List.of();
        String template = NacStructureLayout.templateId().toString();
        return List.of(
                new TestFunction("independent_ownership", "gtlmodular.independent_placement_rollback", template,
                        100, 0, true, helper -> placement(helper, false)),
                new TestFunction("independent_ownership", "gtlmodular.independent_creative_rollback", template,
                        100, 0, true, helper -> placement(helper, true)),
                new TestFunction("independent_ownership", "gtlmodular.independent_clone_ownership", template,
                        100, 0, true, IndependentOwnershipTests::clones),
                new TestFunction("independent_ownership", "gtlmodular.independent_cut_and_drop", template,
                        100, 0, true, IndependentOwnershipTests::cutAndDrop));
    }

    private static void placement(GameTestHelper helper, boolean creative) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "OwnershipTest"));
        player.getAbilities().instabuild = creative;
        ItemStack item = GTMachines.ELECTRIC_BLAST_FURNACE.asStack();
        CompoundTag original = retainedBatch();
        original.putUUID(TOKEN, UUID.randomUUID());
        item.setTag(original.copy());
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false));
        Consumer<BlockEvent.EntityPlaceEvent> cancel = event -> {
            if (event.getPos().equals(pos)) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockEvent.EntityPlaceEvent.class, cancel);
        try {
            helper.assertTrue(!ForgeHooks.onPlaceItemIntoWorld(context).consumesAction(), "放置没有被取消");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);
        }
        helper.assertTrue(helper.getLevel().isEmptyBlock(pos), "回滚后方块仍存在");
        helper.assertTrue(item.getCount() == 1 && original.equals(item.getTag()), "回滚改变了手持物品或批次");
        assertDrops(helper, pos, 0);
        helper.assertTrue(ForgeHooks.onPlaceItemIntoWorld(context).consumesAction(), "第二次合法放置失败");
        helper.runAfterDelay(3, () -> {
            RecipeLogic logic = logic(helper, pos);
            logic.serverTick();
            CompoundTag placed = save(logic);
            helper.assertTrue(original.get(ROOT).equals(placed.get(ROOT)), "合法放置没有保留原始批次");
            helper.assertTrue(!original.getUUID(TOKEN).equals(placed.getUUID(TOKEN)), "放置没有兑现旧令牌");
            helper.assertTrue(creative ? item.getCount() == 1 && !item.getOrCreateTag().contains(ROOT) : item.isEmpty(),
                    "成功放置后的手持物品不守恒");
            assertDrops(helper, pos, 0);
            helper.succeed();
        });
    }

    private static void clones(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos copy = source.offset(3, 0, 0);
        BlockPos moved = source.offset(6, 0, 0);
        RecipeLogic original = ownedMachine(helper, source);
        CompoundTag before = save(original);
        clone(helper, source, copy, "normal");
        logic(helper, copy).serverTick();
        helper.assertTrue(!save(logic(helper, copy)).contains(ROOT), "普通 clone 复制了批次");
        helper.assertTrue(before.get(ROOT).equals(save(original).get(ROOT)), "拒绝副本伤及源批次");
        clone(helper, source, moved, "move");
        helper.assertTrue(helper.getLevel().isEmptyBlock(source), "clone move 没移除源方块");
        logic(helper, moved).serverTick();
        helper.assertTrue(before.get(ROOT).equals(save(logic(helper, moved)).get(ROOT)), "clone move 丢失批次");
        assertDrops(helper, source, 0);
        clone(helper, moved, moved, "force");
        logic(helper, moved).serverTick();
        helper.assertTrue(before.get(ROOT).equals(save(logic(helper, moved)).get(ROOT)), "重叠 clone 丢失批次");
        assertDrops(helper, moved, 0);
        helper.succeed();
    }

    private static void cutAndDrop(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos target = source.offset(3, 0, 0);
        BlockPos replay = source.offset(6, 0, 0);
        RecipeLogic original = ownedMachine(helper, source);
        var entity = helper.getLevel().getBlockEntity(source);
        CompoundTag snapshot = entity.saveWithoutMetadata();
        // BG 的实际顺序是先保存 NBT、移除实体，再替换方块；不需要生成物品来搬运批次。
        helper.getLevel().removeBlockEntity(source);
        helper.getLevel().setBlock(source, Blocks.AIR.defaultBlockState(), 48);
        assertDrops(helper, source, 0);
        helper.getLevel().setBlockAndUpdate(target, GTMachines.ELECTRIC_BLAST_FURNACE.getBlock().defaultBlockState());
        helper.getLevel().getBlockEntity(target).load(snapshot.copy());
        logic(helper, target).serverTick();
        helper.assertTrue(save(original).get(ROOT).equals(save(logic(helper, target)).get(ROOT)), "剪切搬运丢失批次");
        helper.getLevel().setBlockAndUpdate(replay, GTMachines.ELECTRIC_BLAST_FURNACE.getBlock().defaultBlockState());
        helper.getLevel().getBlockEntity(replay).load(snapshot.copy());
        logic(helper, replay).serverTick();
        helper.assertTrue(!save(logic(helper, replay)).contains(ROOT), "剪切快照可以重复兑现");
        helper.getLevel().destroyBlock(target, true);
        List<ItemEntity> drops = drops(helper, target);
        helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().getCount() == 1, "普通拆机没有且仅有一个控制器");
        helper.assertTrue(drops.get(0).getItem().getOrCreateTag().contains(ROOT), "拆机掉落丢失批次");
        helper.succeed();
    }

    private static RecipeLogic ownedMachine(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlockAndUpdate(pos, GTMachines.ELECTRIC_BLAST_FURNACE.getBlock().defaultBlockState());
        RecipeLogic logic = logic(helper, pos);
        invoke(controller(logic), "load", new Class<?>[]{CompoundTag.class}, retainedBatch());
        logic.updateTickSubscription();
        logic.serverTick();
        return logic;
    }

    private static CompoundTag retainedBatch() {
        CompoundTag tag = new CompoundTag();
        // 无法解码的已扣料记录也拥有材料；用原文验证所有权，不依赖配方开工或整机结构。
        tag.put(ROOT, StringTag.valueOf("retained paid batch"));
        return tag;
    }

    private static RecipeLogic logic(GameTestHelper helper, BlockPos pos) {
        return ((IRecipeLogicMachine) MetaMachine.getMachine(helper.getLevel(), pos)).getRecipeLogic();
    }

    private static CompoundTag save(RecipeLogic logic) {
        CompoundTag tag = new CompoundTag();
        logic.saveCustomPersistedData(tag, false);
        return tag;
    }

    private static Object controller(RecipeLogic logic) {
        // 测试模组同时用于独立版，只有完整版用例才解析 main 的桥接接口。
        return invoke(logic, "gtlmodular$controller", new Class<?>[]{boolean.class}, true);
    }

    private static Object invoke(Object target, String method, Class<?>[] types, Object... arguments) {
        try {
            return target.getClass().getMethod(method, types).invoke(target, arguments);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("独立批次测试入口不可用：" + method, failure);
        }
    }

    private static void clone(GameTestHelper helper, BlockPos source, BlockPos target, String mode) {
        var server = helper.getLevel().getServer();
        int changed = server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withLevel(helper.getLevel()).withPermission(4),
                "clone " + coordinates(source) + " " + coordinates(source) + " " + coordinates(target) + " replace " + mode);
        helper.assertTrue(changed == 1, "真实 /clone 命令失败：" + mode);
    }

    private static String coordinates(BlockPos pos) { return pos.getX() + " " + pos.getY() + " " + pos.getZ(); }

    private static List<ItemEntity> drops(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1),
                entity -> entity.getItem().is(GTMachines.ELECTRIC_BLAST_FURNACE.asStack().getItem()));
    }

    private static void assertDrops(GameTestHelper helper, BlockPos pos, int count) {
        helper.assertTrue(drops(helper, pos).size() == count, "控制器掉落数量错误，应为 " + count);
    }
}
