package com.gtlmodular.gametest.fixture;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** 共享的 GameTest 机器放置工具：用真实物品放置路径创建机器，避免直接 setBlock 丢机器实体。 */
public final class GameTestMachines {
    private GameTestMachines() {}

    public static FakePlayer player(GameTestHelper helper, String name) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        UUID identity = UUID.nameUUIDFromBytes((name + origin).getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(identity, name));
        player.setGameMode(GameType.CREATIVE);
        return player;
    }

    public static MetaMachine place(GameTestHelper helper, FakePlayer player,
                                    MachineDefinition definition, BlockPos pos, Direction facing) {
        var level = helper.getLevel();
        level.removeBlock(pos, false);
        var stack = definition.asStack();
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() - 2);
        player.setYRot(0);
        var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        helper.assertTrue(((BlockItem) stack.getItem()).place(context).consumesAction(),
                "放置失败 " + definition.getId());
        var machine = MetaMachine.getMachine(level, pos);
        helper.assertTrue(machine != null, "机器实体未创建 " + definition.getId());
        machine.setFrontFacing(facing);
        return machine;
    }
}
