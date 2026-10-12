package com.gtlmodular.gametest.fixture;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

public final class NacInventoryFixture {
    private final GameTestHelper helper;
    private final ItemBusPartMachine input;
    private final ItemBusPartMachine output;
    private final FluidHatchPartMachine fluidInput;
    private final FluidHatchPartMachine fluidOutput;
    private final Item ingredient;
    private final Item product;
    private long blockers;
    private long blockedFluid;

    public NacInventoryFixture(GameTestHelper helper, MultiblockControllerMachine machine) {
        this(helper, machine, "kubejs:nac_processing_input", "kubejs:nac_processing_output");
    }

    public NacInventoryFixture(GameTestHelper helper, MultiblockControllerMachine machine, String ingredientId) {
        this(helper, machine, ingredientId, "kubejs:nac_processing_output");
    }

    public NacInventoryFixture(GameTestHelper helper, MultiblockControllerMachine machine,
                               String inputId, String outputId) {
        this.helper = helper;
        input = part(machine, GTMachines.ITEM_IMPORT_BUS[1].getId(), ItemBusPartMachine.class);
        output = part(machine, GTMachines.ITEM_EXPORT_BUS[1].getId(), ItemBusPartMachine.class);
        fluidInput = part(machine, GTMachines.FLUID_IMPORT_HATCH[1].getId(), FluidHatchPartMachine.class);
        fluidOutput = part(machine, GTMachines.FLUID_EXPORT_HATCH[1].getId(), FluidHatchPartMachine.class);
        ingredient = item(inputId);
        product = item(outputId);
    }

    public static <T> T part(MultiblockControllerMachine machine, ResourceLocation id, Class<T> type) {
        return machine.getParts().stream().map(p -> p.self())
                .filter(p -> p.getDefinition().getId().equals(id)).map(type::cast).findFirst()
                .orElseThrow(() -> new IllegalStateException("缺少仓室 " + id));
    }

    private Item item(String id) {
        ResourceLocation key = new ResourceLocation(id);
        helper.assertTrue(ForgeRegistries.ITEMS.containsKey(key), "测试物品未注册：" + id);
        return ForgeRegistries.ITEMS.getValue(key);
    }

    public void addWater() {
        addWater(1000);
    }

    public void addWater(long amount) {
        helper.assertTrue(fluidInput.tank.fill(FluidStack.create(Fluids.WATER, amount), false) == amount,
                "真实流体输入仓未接收 " + amount + " mB 水");
    }

    public void addInput() {
        addInput(1);
    }

    public void addInput(int amount) {
        helper.assertTrue(input.getInventory().insertItem(0, new ItemStack(ingredient, amount), false).isEmpty(),
                "真实物品输入仓未接收测试材料");
    }

    public long inputs() { return count(input, ingredient); }
    public long outputs() { return count(output, product); }
    public long inputWater() {
        var fluid = fluidInput.tank.getFluidInTank(0);
        helper.assertTrue(fluid.isEmpty() || fluid.getFluid() == Fluids.WATER, "输入仓出现非测试流体");
        return fluid.getAmount();
    }

    public long outputLava() {
        var fluid = fluidOutput.tank.getFluidInTank(0);
        helper.assertTrue(fluid.isEmpty() || fluid.getFluid() == Fluids.LAVA, "输出仓出现非测试流体");
        return fluid.getAmount();
    }

    private long count(ItemBusPartMachine bus, Item item) {
        long result = 0;
        for (int i = 0; i < bus.getInventory().getSlots(); i++) {
            ItemStack stack = bus.getInventory().getStackInSlot(i);
            helper.assertTrue(stack.isEmpty() || stack.is(item) || stack.is(Items.COBBLESTONE),
                    "仓室出现非测试物品：" + stack);
            if (stack.is(item)) result += stack.getCount();
        }
        return result;
    }

    public void blockOutputs() {
        // 仓室内部存储是玩家 GUI 使用的真实 handler，外部自动输入口不会接受向输出仓塞物品。
        var storage = output.getInventory().storage;
        for (int i = 0; i < storage.getSlots(); i++) {
            int amount = storage.getSlotLimit(i);
            helper.assertTrue(storage.insertItem(i, new ItemStack(Items.COBBLESTONE, amount), false).isEmpty(),
                    "无法塞满输出仓");
            blockers += amount;
        }
        var tank = fluidOutput.tank.getStorages()[0];
        blockedFluid = tank.fill(FluidStack.create(Fluids.LAVA, tank.getCapacity()), false);
        helper.assertTrue(blockedFluid == tank.getCapacity(), "无法塞满流体输出仓");
    }

    public void assertBlocked() {
        helper.assertTrue(count(output, Items.COBBLESTONE) == blockers && outputLava() == blockedFluid,
                "堵塞期间填充物变化：" + describe());
        helper.assertTrue(outputs() == 0, "输出满时提前产出：" + describe());
    }

    public void clearBlockers() {
        var storage = output.getInventory().storage;
        long removed = 0;
        for (int i = 0; i < storage.getSlots(); i++) {
            ItemStack stack = storage.extractItem(i, Integer.MAX_VALUE, false);
            helper.assertTrue(stack.is(Items.COBBLESTONE), "取出填充物时发现其他物品");
            removed += stack.getCount();
        }
        helper.assertTrue(removed == blockers, "填充物不守恒");
        helper.assertTrue(fluidOutput.tank.drain(FluidStack.create(Fluids.LAVA, blockedFluid), false).getAmount()
                == blockedFluid, "输出仓填充流体不守恒");
    }

    public void assertFinished() {
        helper.assertTrue(inputs() == 0 && inputWater() == 0 && outputs() == 1 && outputLava() == 1000,
                "单批物料/流体不符（默认简单物流）：" + describe());
    }

    public String describe() {
        return "items=" + inputs() + "->" + outputs() + " fluids=" + inputWater() + "->" + outputLava();
    }
}
