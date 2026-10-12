package com.gtlmodular.gametest.fixture;

import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.stream.Stream;

public final class NacEnergyFixture {
    private final GameTestHelper helper;
    private final NacStructureFixture structure;
    private final boolean grid;
    private long deposited;

    public NacEnergyFixture(GameTestHelper helper, NacStructureFixture structure, boolean grid) {
        this.helper = helper;
        this.structure = structure;
        this.grid = grid;
        helper.assertTrue(structure.player().getUUID().equals(structure.host().gridPowerUser()),
                "主机未通过正常放置绑定测试身份");
        helper.assertTrue(balance() == 0 && stored() == 0, "用例初始能源不是零：" + describe());
        helper.assertTrue(structure.module().drawingFromHost() == grid, "模块选错供电路径");
        helper.assertTrue(grid ? structure.host().isEnergySourceEffectivelyUnlimited()
                        : structure.module().getEnergyContainer().getEnergyCapacity()
                        < NanochipAssemblyComplexMachine.ON_DEMAND_POWER_FLOOR_EU,
                "供电能力不符合用例");
        repairMaintenance();
    }

    private void repairMaintenance() {
        var player = structure.player();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, GTItems.DUCT_TAPE.asStack(2));
        int expectedTape = 2;
        var parts = Stream.concat(structure.host().getParts().stream(), structure.module().getParts().stream())
                .map(p -> p.self()).filter(MaintenanceHatchPartMachine.class::isInstance)
                .map(MaintenanceHatchPartMachine.class::cast).toList();
        helper.assertTrue(parts.size() == 2, "缺少真实维护仓");
        for (var hatch : parts) {
            if (hatch.hasMaintenanceProblems()) {
                var pos = hatch.getPos();
                var state = helper.getLevel().getBlockState(pos);
                helper.assertTrue(hatch.onUse(state, helper.getLevel(), pos, player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false)).consumesAction(),
                        "维护仓不接受胶带");
                expectedTape--;
            }
            helper.assertTrue(!hatch.hasMaintenanceProblems() && hatch.getDurationMultiplier() == 1.0F,
                    "真实维护修复失败或倍率不是 1");
        }
        helper.assertTrue(player.getMainHandItem().getCount() == expectedTape, "维护胶带消耗不守恒");
        player.setGameMode(GameType.CREATIVE);
        LogUtils.getLogger().info("GameTest NAC processing maintenance: tape consumed={}", 2 - expectedTape);
    }

    public void deposit(long amount) {
        if (grid) {
            helper.assertTrue(WirelessEnergyManager.addEUToGlobalEnergyMap(structure.player().getUUID(),
                    amount, structure.host()), "电网真实充值失败");
        } else {
            helper.assertTrue(structure.module().getEnergyContainer().changeEnergy(amount) == amount,
                    "能源仓未接受有限 EU");
        }
        deposited += amount;
    }

    public long balance() {
        return WirelessEnergyManager.getUserEU(structure.player().getUUID()).longValueExact();
    }

    private List<NotifiableEnergyContainer> hatches() {
        // 读档会创建新仓室，计量不能继续读取卸载对象的旧余额。
        return Stream.concat(structure.host().getParts().stream(), structure.module().getParts().stream())
                .flatMap(p -> p.self().getTraits().stream()).filter(NotifiableEnergyContainer.class::isInstance)
                .map(NotifiableEnergyContainer.class::cast).toList();
    }

    public long stored() {
        return balance() + structure.module().getBufferEU()
                + hatches().stream().mapToLong(NotifiableEnergyContainer::getEnergyStored).sum();
    }

    public long spent() { return deposited - stored(); }

    public void assertSpent(long expected) {
        helper.assertTrue(spent() == expected, "总能源不守恒：expected=" + expected + " " + describe());
        helper.assertTrue(structure.module().getBufferEU() == 0, "本地/电网用例不应预充主机缓存");
        if (!grid) helper.assertTrue(balance() == 0, "能源仓用例动用了电网");
        else helper.assertTrue(hatches().stream().allMatch(h -> h.getEnergyStored() == 0),
                "电网用例动用了能源仓");
    }

    public String describe() {
        return "grid=" + grid + " deposited=" + deposited + " account=" + balance()
                + " hatches=" + hatches().stream().map(NotifiableEnergyContainer::getEnergyStored).toList()
                + " buffer=" + structure.module().getBufferEU() + " spent=" + spent();
    }
}
