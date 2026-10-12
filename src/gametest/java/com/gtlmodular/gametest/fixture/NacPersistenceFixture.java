package com.gtlmodular.gametest.fixture;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gtlmodular.common.machine.nanochip.NanochipConcurrentBatches;
import com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** 同一服务器 tick 保存全部仓室，防止重放旧批次时混用完成后的物品或能源。 */
public final class NacPersistenceFixture {
    private final GameTestHelper helper;
    private final NacStructureFixture structure;
    private final List<BlockPos> positions = new ArrayList<>();
    private long deposited;

    public NacPersistenceFixture(GameTestHelper helper, NacStructureFixture structure) {
        this(helper, structure, true);
    }

    public NacPersistenceFixture(GameTestHelper helper, NacStructureFixture structure, boolean prepareHistory) {
        this.helper = helper;
        this.structure = structure;
        Stream.concat(structure.host().getParts().stream(), structure.module().getParts().stream())
                .map(part -> part.self().getPos()).distinct().forEach(positions::add);
        positions.add(structure.host().getPos());
        positions.add(structure.module().getPos());
        // 空闲恢复用例自行准备供电和材料，不能被历史用例的填仓动作提前唤醒。
        if (!prepareHistory) return;
        new NacEnergyFixture(helper, structure, true);
        // 不参与这些配方的非空流体也必须随仓室保存，空仓无法发现丢存储的问题。
        for (var machine : List.of(structure.host(), structure.module())) {
            var input = NacInventoryFixture.part(machine, GTMachines.FLUID_IMPORT_HATCH[1].getId(), FluidHatchPartMachine.class);
            var output = NacInventoryFixture.part(machine, GTMachines.FLUID_EXPORT_HATCH[1].getId(), FluidHatchPartMachine.class);
            helper.assertTrue(input.tank.fill(FluidStack.create(Fluids.WATER, 1001), false) == 1001
                    && output.tank.getStorages()[0].fill(FluidStack.create(Fluids.LAVA, 203), false) == 203,
                    "流体仓未接收保存对照材料");
        }
    }

    public NanochipThreadedRecipeLogic hostLogic() {
        return (NanochipThreadedRecipeLogic) structure.host().getRecipeLogic();
    }

    public void assertLiveParts() {
        Stream.concat(structure.host().getParts().stream(), structure.module().getParts().stream()).forEach(part ->
                helper.assertTrue(!part.self().isInValid() && part.self() == MetaMachine.getMachine(
                        helper.getLevel(), part.self().getPos()), "恢复后的控制器仍持有旧仓室对象"));
    }

    public void deposit(long eu) {
        helper.assertTrue(eu >= 0 && WirelessEnergyManager.addEUToGlobalEnergyMap(
                structure.player().getUUID(), eu, structure.host()), "电网充值失败：" + eu);
        deposited += eu;
    }

    public long balance() {
        return WirelessEnergyManager.getUserEU(structure.player().getUUID()).longValueExact();
    }

    public void collectRemainder() {
        helper.assertTrue(balance() == 1 && WirelessEnergyManager.addEUToGlobalEnergyMap(
                structure.player().getUUID(), -1, structure.host()), "未能取回保存对照用的 1 EU 余量");
        deposited--;
        helper.assertTrue(balance() == 0, "测试账户未结清，会污染下一轮相同位置的用例");
    }

    public void assertSpent(long expected) {
        helper.assertTrue(deposited - balance() == expected, "实际扣电错误：deposited=" + deposited
                + " balance=" + balance() + " expectedSpent=" + expected);
        helper.assertTrue(structure.module().getBufferEU() == 0 && machines().stream()
                .flatMap(machine -> machine.getTraits().stream()).filter(NotifiableEnergyContainer.class::isInstance)
                .map(NotifiableEnergyContainer.class::cast).allMatch(energy -> energy.getEnergyStored() == 0),
                "电网用例意外动用了模块缓存或真实能源仓");
        for (var machine : List.of(structure.host(), structure.module())) {
            for (var id : List.of(GTMachines.FLUID_IMPORT_HATCH[1].getId(), GTMachines.FLUID_EXPORT_HATCH[1].getId())) {
                var hatch = NacInventoryFixture.part(machine, id, FluidHatchPartMachine.class);
                boolean input = id.equals(GTMachines.FLUID_IMPORT_HATCH[1].getId());
                var fluid = hatch.tank.getFluidInTank(0);
                helper.assertTrue(fluid.getFluid() == (input ? Fluids.WATER : Fluids.LAVA)
                        && fluid.getAmount() == (input ? 1001 : 203), "未参与配方的流体在保存或续跑时变化");
            }
        }
    }

    private List<MetaMachine> machines() {
        return positions.stream().map(pos -> MetaMachine.getMachine(helper.getLevel(), pos)).toList();
    }

    public void input(boolean host, int slot, String item) {
        var bus = NacInventoryFixture.part(host ? structure.host() : structure.module(),
                GTMachines.ITEM_IMPORT_BUS[1].getId(), ItemBusPartMachine.class);
        var id = new ResourceLocation("kubejs", item);
        helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id), "缺少测试原料：" + id);
        helper.assertTrue(bus.getInventory().insertItem(slot,
                new ItemStack(ForgeRegistries.ITEMS.getValue(id)), false).isEmpty(), "原料未全部进入真实仓室");
    }

    public void assertInputsEmpty() {
        for (var machine : List.of(structure.host(), structure.module())) {
            var bus = NacInventoryFixture.part(machine, GTMachines.ITEM_IMPORT_BUS[1].getId(), ItemBusPartMachine.class);
            for (int slot = 0; slot < bus.getInventory().getSlots(); slot++) {
                helper.assertTrue(bus.getInventory().getStackInSlot(slot).isEmpty(), "已入批原料尚未扣除");
            }
        }
    }

    public Map<String, Integer> outputs(boolean host) {
        Map<String, Integer> result = new LinkedHashMap<>();
        var bus = NacInventoryFixture.part(host ? structure.host() : structure.module(),
                GTMachines.ITEM_EXPORT_BUS[1].getId(), ItemBusPartMachine.class);
        for (int slot = 0; slot < bus.getInventory().getSlots(); slot++) {
            var stack = bus.getInventory().getStackInSlot(slot);
            if (!stack.isEmpty()) result.merge(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString(),
                    stack.getCount(), Integer::sum);
        }
        return result;
    }

    public List<BatchView> batches(boolean host) {
        List<BatchView> result = new ArrayList<>();
        if (host) {
            for (int i = 0; i < hostLogic().runningThreadCount(); i++) {
                var batch = hostLogic().batchAt(i);
                result.add(view(batch.recipe, batch.progress, batch.duration));
            }
        } else {
            var logic = structure.module().getRecipeLogic();
            if (!logic.isIdle() && logic.getLastRecipe() != null) {
                result.add(view(logic.getLastRecipe(), logic.getProgress(), logic.getDuration()));
            }
        }
        return result;
    }

    private BatchView view(GTRecipe recipe, int progress, int duration) {
        return new BatchView(recipe, recipe.getId(), progress, duration, IGTRecipe.of(recipe).getRealParallels(),
                GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, recipe).result().orElseThrow());
    }

    private void assertSameBatches(List<BatchView> expected, boolean host) {
        var actual = batches(host);
        helper.assertTrue(actual.size() == expected.size(), "恢复批次数不符：" + actual.size() + " / " + expected.size());
        for (int i = 0; i < expected.size(); i++) {
            var before = expected.get(i);
            var after = actual.get(i);
            helper.assertTrue(before.recipe != after.recipe, "恢复复用了旧配方对象");
            helper.assertTrue(java.util.Objects.equals(before.id, after.id) && before.progress == after.progress
                            && before.duration == after.duration && before.runs == after.runs
                            && before.encoded.equals(after.encoded),
                    "批次恢复不一致：before=" + before + " after=" + after);
        }
    }

    private Map<BlockPos, CompoundTag> resources() {
        Map<BlockPos, CompoundTag> result = new LinkedHashMap<>();
        for (MetaMachine machine : machines()) {
            var tag = new CompoundTag();
            if (machine instanceof ItemBusPartMachine bus) {
                for (int i = 0; i < bus.getInventory().getSlots(); i++) {
                    tag.put("item" + i, bus.getInventory().getStackInSlot(i).save(new CompoundTag()));
                }
            }
            if (machine instanceof FluidHatchPartMachine hatch) {
                for (int i = 0; i < hatch.tank.getTanks(); i++) {
                    tag.put("fluid" + i, hatch.tank.getFluidInTank(i).saveToTag(new CompoundTag()));
                }
            }
            int index = 0;
            for (var trait : machine.getTraits()) {
                if (trait instanceof NotifiableEnergyContainer energy) tag.putLong("energy" + index++, energy.getEnergyStored());
            }
            result.put(machine.getPos(), tag);
        }
        return result;
    }

    public void reload(boolean hostBatches) {
        var oldHost = structure.host();
        var oldModule = structure.module();
        oldHost.getPatternLock().lock();
        oldModule.getPatternLock().lock();
        try {
            var history = oldHost.getCalibration().save();
            var batches = batches(hostBatches);
            var resources = resources();
            long balance = balance();
            long buffer = oldModule.getBufferEU();
            Map<BlockPos, SavedEntity> saved = new LinkedHashMap<>();
            for (BlockPos pos : positions) {
                var entity = helper.getLevel().getBlockEntity(pos);
                helper.assertTrue(entity != null && !entity.isRemoved(), "保存前实体缺失：" + pos);
                CompoundTag original = entity.saveWithFullMetadata();
                CompoundTag decoded = bytes(original);
                helper.assertTrue(decoded.equals(original), "完整世界 NBT 字节往返改变了内容");
                saved.put(pos, new SavedEntity(entity, entity.getBlockState(), decoded));
            }
            helper.assertTrue(saved.get(oldHost.getPos()).tag.getCompound("GTLModularNanochipCalibration").equals(history),
                    "完整保存缺少旧校准键或历史");
            helper.assertTrue(saved.get(oldHost.getPos()).tag.contains(NanochipConcurrentBatches.TAG_BATCHES),
                    "完整保存未经过 forDrop=false 的独立批次路径");
            // 先锁住控制器再卸载仓室，避免旧控制器在新对象安装期间异步重判并重新订阅。
            for (BlockPos pos : positions) helper.getLevel().removeBlockEntity(pos);
            // 服务器重启不会保留旧结构的运行期索引；实体内重建也必须解除其旧对象引用。
            var worldData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
            worldData.removeMapping(oldHost.getMultiblockState());
            worldData.removeMapping(oldModule.getMultiblockState());
            for (var entry : saved.entrySet()) {
                var old = entry.getValue();
                helper.assertTrue(old.entity.isRemoved(), "旧实体未卸载：" + entry.getKey());
                BlockEntity restored = BlockEntity.loadStatic(entry.getKey(), old.state, old.tag);
                helper.assertTrue(restored != null && restored != old.entity, "未从 NBT 重建实体：" + entry.getKey());
                helper.getLevel().setBlockEntity(restored);
            }
            structure.refreshMachinesAfterLoad();
            helper.assertTrue(structure.host() != oldHost && structure.module() != oldModule
                    && oldHost.isInValid() && oldModule.isInValid(), "控制器仍复用旧实例");
            helper.assertTrue(resources.equals(resources()) && balance() == balance
                    && structure.module().getBufferEU() == buffer, "恢复改变仓室、能源或电网余额");
            helper.assertTrue(structure.player().getUUID().equals(structure.host().gridPowerUser()), "恢复丢失电网身份");
            helper.assertTrue(history.equals(structure.host().getCalibration().save()), "校准分族、历史或游标没有完整恢复");
            assertSameBatches(batches, hostBatches);
            LogUtils.getLogger().info("GameTest NAC persistence reloaded: hostBatches={} entities={} batches={} history={} balance={}",
                    hostBatches, saved.size(), batches, history, balance);
        } finally {
            oldModule.getPatternLock().unlock();
            oldHost.getPatternLock().unlock();
        }
    }

    private CompoundTag bytes(CompoundTag tag) {
        try {
            var output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, output);
            return NbtIo.readCompressed(new ByteArrayInputStream(output.toByteArray()));
        } catch (IOException e) {
            throw new IllegalStateException("完整世界 NBT 字节往返失败", e);
        }
    }

    public record BatchView(GTRecipe recipe, ResourceLocation id, int progress, int duration, long runs, Tag encoded) {}
    private record SavedEntity(BlockEntity entity, BlockState state, CompoundTag tag) {}
}
