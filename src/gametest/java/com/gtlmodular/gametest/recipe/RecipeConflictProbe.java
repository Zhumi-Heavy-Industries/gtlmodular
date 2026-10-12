package com.gtlmodular.gametest.recipe;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;

import java.lang.reflect.Method;

/** 静态候选匹配还不足以证明 handler 接受供料，因此使用真实槽库存复核。 */
public final class RecipeConflictProbe {
    private final GameTestHelper helper;
    private final BlockPos position = new BlockPos(1, 1, 1);

    public RecipeConflictProbe(GameTestHelper helper) {
        this.helper = helper;
        var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtceu", "me_mini_pattern_buffer"));
        helper.assertTrue(definition != null, "缺少上游 ME 样板仓");
        helper.setBlock(position, definition.getBlock());
    }

    public void verify(GTRecipe candidate, GTRecipe target, RecipeConflictInputs.Witness witness) {
        var machine = (MEPatternBufferPartMachineBase) MetaMachine.getMachine(helper.getLevel(), helper.absolutePos(position));
        helper.assertTrue(machine != null, "ME 样板仓实体未创建");
        try {
            Method getSlot = MEPatternBufferPartMachineBase.class.getDeclaredMethod("getInternalSlot", int.class);
            getSlot.setAccessible(true);
            Object slot = getSlot.invoke(machine, 0);
            Class<?> slotType = getSlot.getReturnType();
            clear(slotType, slot, "getItemInventory");
            clear(slotType, slot, "getFluidInventory");
            var inputs = witness.supply().patternInputs(witness.circuit());
            var pattern = PatternDetailsHelper.encodeProcessingPattern(inputs.toArray(GenericStack[]::new),
                    new GenericStack[]{new GenericStack(AEItemKey.of(Items.STONE), 1)});
            machine.getTerminalPatternInventory().setItemDirect(0, pattern);
            // 直接填真实槽库存以单独检验匹配器；不伪造节点供电、控制器成形或 AE 推料成功。
            Method add = slotType.getMethod("add", appeng.api.stacks.AEKey.class, long.class);
            add.setAccessible(true);
            for (var input : inputs) add.invoke(slot, input.what(), input.amount());
            String before = machine.getMergedInternalSlot().toString();
            helper.assertTrue(matches(machine, target), "扫描的目标供料无法通过真实 handler：" + target.id);
            helper.assertTrue(matches(machine, candidate), "扫描误报，真实 handler 拒绝候选："
                    + candidate.id + " <- " + target.id);
            helper.assertTrue(before.equals(machine.getMergedInternalSlot().toString()), "扫描模拟消耗了真实库存");
            clear(slotType, slot, "getItemInventory");
            clear(slotType, slot, "getFluidInventory");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法访问上游真实槽库存", e);
        }
    }

    private static void clear(Class<?> type, Object slot, String getter) throws ReflectiveOperationException {
        Method method = type.getMethod(getter);
        method.setAccessible(true);
        ((java.util.Map<?, ?>) method.invoke(slot)).clear();
    }

    private static boolean matches(MEPatternBufferPartMachineBase machine, GTRecipe recipe) {
        var handlers = machine.getMERecipeHandlerTraits();
        var items = recipe.getInputContents(ItemRecipeCapability.CAP).stream().map(c -> c.content).toList();
        var fluids = recipe.getInputContents(FluidRecipeCapability.CAP).stream().map(c -> c.content).toList();
        // 上游遇到空输入不会清理 preparedContents；与真实调用方一样跳过没有输入的能力。
        handlers.left().initMEHandleContents(recipe, items, true);
        handlers.right().initMEHandleContents(recipe, fluids, true);
        return (items.isEmpty() || handlers.left().meHandleRecipe(recipe, true, 0))
                && (fluids.isEmpty() || handlers.right().meHandleRecipe(recipe, true, 0));
    }
}
