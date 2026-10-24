package com.gtlmodular.common.machine.independent.command;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gtlmodular.common.machine.independent.control.IndependentLogicAccess;

import java.util.ArrayList;
import java.util.List;

/**
 * 机器状态快照：引擎正在跑什么、输入仓里有什么、输出仓里堆了什么。
 * <p>
 * 由 {@code /gtlmodular_threads <pos> dump} 调用，同时写进服务端日志，
 * 便于「这台机器到底吃了什么、产了什么」逐条核对。
 */
public final class MachineStateDump {

    private MachineStateDump() {}

    public static List<String> describe(MetaMachine machine) {
        List<String> out = new ArrayList<>();
        boolean formed = !(machine instanceof MultiblockControllerMachine controller) || controller.isFormed();
        out.add("机器 " + id(machine) + " @" + machine.getPos() + " 成型=" + (formed ? "是" : "否"));

        var controller = IndependentLogicAccess.controller(machine);
        if (controller == null) {
            out.add("引擎：这台机器没有独立线程会话");
        } else {
            var threads = controller.threads();
            out.add("引擎：在用批次 " + threads.size() + " 条");
            for (var view : threads) {
                out.add("  线程#" + view.label() + " 配方=" + view.recipeId()
                        + " 并行=" + view.parallels() + " 份数=" + view.runs()
                        + " 进度=" + view.progress() + "/" + view.duration() + " tick"
                        + " EU/t=" + view.euPerTick()
                        + (view.outputId() == null ? "" : " 产物=" + view.outputId() + "×" + view.outputCount())
                        + (view.waitReason() == null ? "" : " 等待=" + view.waitReason()));
            }
        }

        out.add("--- 输入 ---");
        dumpRow(machine, IO.IN, out);
        dumpRow(machine, IO.BOTH, out);
        out.add("--- 输出 ---");
        dumpRow(machine, IO.OUT, out);
        return out;
    }

    private static void dumpRow(MetaMachine machine, IO io, List<String> out) {
        if (!(machine instanceof IRecipeCapabilityHolder holder)) {
            out.add("  这台机器没有配方仓代理，读不到 " + io);
            return;
        }
        try {
            holder.getCapabilitiesProxy().row(io).forEach((cap, handlers) -> {
                for (Object handler : handlers) {
                    describeHandler(handler, out);
                }
            });
        } catch (RuntimeException failure) {
            out.add("  读取 " + io + " 失败：" + failure.getMessage());
        }
    }

    private static void describeHandler(Object handler, List<String> out) {
        // GTCEu 的库存处理器不是 Forge 的 IItemHandler：先按 LDLib 的 transfer 走，
        // 再退回 IRecipeHandler#getContents()（ME 总线、代理仓都走这一条）。
        if (handler instanceof com.lowdragmc.lowdraglib.side.item.IItemTransfer items) {
            for (int slot = 0; slot < items.getSlots(); slot++) {
                var stack = items.getStackInSlot(slot);
                if (stack != null && !stack.isEmpty()) {
                    out.add("  物品 " + net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(stack.getItem()) + " ×" + stack.getCount() + "（槽 " + slot + "）");
                }
            }
            return;
        }
        if (handler instanceof net.minecraftforge.items.IItemHandler items) {
            for (int slot = 0; slot < items.getSlots(); slot++) {
                var stack = items.getStackInSlot(slot);
                if (stack != null && !stack.isEmpty()) {
                    out.add("  物品 " + net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(stack.getItem()) + " ×" + stack.getCount() + "（槽 " + slot + "）");
                }
            }
            return;
        }
        if (handler instanceof com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer fluids) {
            for (int tank = 0; tank < fluids.getTanks(); tank++) {
                var fluid = fluids.getFluidInTank(tank);
                if (fluid != null && !fluid.isEmpty()) {
                    out.add("  流体 " + net.minecraft.core.registries.BuiltInRegistries.FLUID
                            .getKey(fluid.getFluid()) + " ×" + fluid.getAmount() + "（罐 " + tank + "）");
                }
            }
            return;
        }
        if (handler instanceof com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler<?> recipeHandler) {
            int shown = 0;
            for (Object content : recipeHandler.getContents()) {
                if (shown++ >= 12) {
                    out.add("  …（更多内容省略）");
                    break;
                }
                // 单条内容读失败不能把整段 dump 打空（之前 null 消息的 NPE 就是这样冒出来的）。
                try {
                    out.add("  内容 " + com.gtlmodular.common.machine.independent.io.BatchContents
                            .describe(recipeHandler.getCapability(), content)
                            + " ×" + com.gtlmodular.common.machine.independent.io.BatchContents
                            .amount(recipeHandler.getCapability(), content)
                            + "（" + handler.getClass().getSimpleName() + "）");
                } catch (RuntimeException failure) {
                    out.add("  内容 <" + (content == null ? "null" : content.getClass().getSimpleName())
                            + ">（读取失败：" + failure.getClass().getSimpleName() + "）");
                }
            }
            return;
        }
        out.add("  处理器 " + handler.getClass().getSimpleName() + "（读不到内容）");
    }

    private static String id(MetaMachine machine) {
        try {
            return machine.getDefinition().getId().toString();
        } catch (RuntimeException failure) {
            return machine.getClass().getSimpleName();
        }
    }
}
