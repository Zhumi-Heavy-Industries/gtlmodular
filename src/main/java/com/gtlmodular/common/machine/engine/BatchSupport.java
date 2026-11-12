package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.google.common.collect.Table;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternPartMachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通用独立线程引擎的<b>只读探测／文本</b>小工具。
 *
 * <p>抽出来的目的与参考实现（gtl_extend 的 {@code CommonUtils}）一致：这些方法只读世界、
 * 不参与调度、不写任何字段，集中在一处后 {@link BatchFinder}／{@link BatchDiagnostics}／
 * {@link BatchIo} 都能用，又不必互相依赖。
 */
final class BatchSupport {

    private BatchSupport() {}

    /** 机器结构里是否挂了 ME 样板仓（即使它的库存不在通用 handler 里）。 */
    static boolean hasMePatternPart(IRecipeLogicMachine machine) {
        try {
            if (machine.self() instanceof IMultiController controller && controller.isFormed()) {
                for (IMultiPart part : controller.getParts()) {
                    if (part instanceof IMEPatternPartMachine) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 拿不到部件表就不拦（宁可多扫）
        }
        return false;
    }

    /**
     * 「无物品/流体输入」时的定位信息 —— 列出这台机器到底有没有输入仓、仓里有没有东西
     * （区分「没装输入仓」与「装了但是空的」）。
     */
    static String describeInputCaps(IRecipeLogicMachine machine) {
        try {
            Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> proxy = machine.getCapabilitiesProxy();
            if (proxy == null) {
                return "能力表=null";
            }
            Map<RecipeCapability<?>, List<IRecipeHandler<?>>> in = proxy.row(IO.IN);
            if (in == null) {
                return "IN能力表=null";
            }
            int itemHandlers = 0;
            int fluidHandlers = 0;
            int itemContent = 0;
            int fluidContent = 0;
            for (Map.Entry<RecipeCapability<?>, List<IRecipeHandler<?>>> entry : in.entrySet()) {
                RecipeCapability<?> cap = entry.getKey();
                if (cap != ItemRecipeCapability.CAP && cap != FluidRecipeCapability.CAP) {
                    continue;
                }
                for (IRecipeHandler<?> handler : entry.getValue()) {
                    if (cap == ItemRecipeCapability.CAP) {
                        itemHandlers++;
                    } else {
                        fluidHandlers++;
                    }
                    for (Object content : handler.getContents()) {
                        if (content instanceof ItemStack stack && !stack.isEmpty()) {
                            itemContent++;
                        } else if (content instanceof FluidStack fluid && fluid.getAmount() > 0) {
                            fluidContent++;
                        }
                    }
                }
            }
            return "IN能力=" + in.size() + " 物品仓=" + itemHandlers + " 流体仓=" + fluidHandlers
                    + " 物品条目=" + itemContent + " 流体条目=" + fluidContent
                    + " 样板仓=" + (hasMePatternPart(machine) ? "有" : "无");
        } catch (Throwable t) {
            return "输入仓探测异常:" + t.getClass().getSimpleName();
        }
    }

    /** 产物摘要（前 4 项），给「线程批次完成」那行用。 */
    static String describeOutputs(GTRecipe recipe) {
        if (recipe == null) {
            return "?";
        }
        try {
            List<String> out = new ArrayList<>();
            for (Content c : recipe.getOutputContents(ItemRecipeCapability.CAP)) {
                Object inner = ItemRecipeCapability.CAP.of(c.getContent());
                if (inner instanceof net.minecraft.world.item.crafting.Ingredient ingredient
                        && ingredient.getItems().length > 0) {
                    ItemStack stack = ingredient.getItems()[0];
                    out.add(net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(stack.getItem()).toString() + "×" + stack.getCount());
                }
            }
            for (Content c : recipe.getOutputContents(FluidRecipeCapability.CAP)) {
                Object inner = FluidRecipeCapability.CAP.of(c.getContent());
                if (inner instanceof com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient fluid
                        && fluid.getStacks().length > 0) {
                    out.add(net.minecraftforge.registries.ForgeRegistries.FLUIDS
                            .getKey(fluid.getStacks()[0].getFluid()).toString()
                            + "×" + fluid.getAmount() + "mB");
                }
            }
            if (out.isEmpty()) {
                return "无";
            }
            if (out.size() <= 4) {
                return String.join(" ", out);
            }
            return String.join(" ", out.subList(0, 4)) + " …(共 " + out.size() + " 项)";
        } catch (Throwable t) {
            return "?";
        }
    }

    /** {@code ActionResult.reason()} → 可读文本（取不到就给 {@code null}）。 */
    static String reasonText(GTRecipe.ActionResult result) {
        try {
            if (result != null && result.reason() != null) {
                Component reason = result.reason().get();
                if (reason != null) {
                    // 服务端没有语言表：Component#getString() 直接回翻译键（例如
                    // "gtceu.recipe_logic.insufficient_in: item"）⇒ 这里换成中文再进日志，
                    // 免得玩家在日志里看到一坨键名。
                    return reason.getString()
                            .replace("gtceu.recipe_logic.insufficient_in", "输入不足")
                            .replace("gtceu.recipe_logic.insufficient_out", "输出不足")
                            .replace("gtceu.recipe_logic.insufficient", "不足")
                            .replace(": item", ": 物品")
                            .replace(": fluid", ": 流体");
                }
            }
        } catch (Throwable ignored) {
            // 原因本身取不到不影响诊断
        }
        return null;
    }
}
