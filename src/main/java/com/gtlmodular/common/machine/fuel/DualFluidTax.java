package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.config.GTLModularConfig;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.utils.MachineIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public final class DualFluidTax {

    private static final Logger LOGGER = LoggerFactory.getLogger("GTL-Modular");

    private DualFluidTax() {
    }

    public static boolean onBeforeWorking(IRecipeLogicMachine machine, GTRecipe recipe) {
        if (Boolean.getBoolean("gtlmodular.testHost")) return true;
        if (!GTLModularConfig.isDualFluidTaxEnabled()) {
            return true;
        }
        WorkableElectricMultiblockMachine wem = asWorkable(machine);
        if (wem == null) {
            return true;
        }
        if (!canRun(wem)) {
            return false;
        }
        long amount = amountPerDrain(wem);
        Fluid lava = lavaFluid();
        Fluid blaze = blazeFluid();
        boolean lavaOk = lava != null && hasFluid(wem, lava, amount);
        boolean blazeOk = blaze != null && hasFluid(wem, blaze, amount);
        if (!lavaOk || !blazeOk) {
            notifyMissing(wem, lavaOk, blazeOk, lava, blaze, amount);
            return false;
        }
        return true;
    }

    public static boolean onWorking(IRecipeLogicMachine machine) {
        if (Boolean.getBoolean("gtlmodular.testHost")) return true;
        if (!GTLModularConfig.isDualFluidTaxEnabled()) {
            return true;
        }
        WorkableElectricMultiblockMachine wem = asWorkable(machine);
        if (wem == null) {
            return true;
        }
        long offset = wem.getOffsetTimer();
        int interval = Math.max(1, GTLModularConfig.getDrainIntervalTicks());
        if (offset % interval != 0) {
            return true;
        }
        if (!canRun(wem)) {
            return false;
        }
        long amount = amountPerDrain(wem);
        Fluid lava = lavaFluid();
        Fluid blaze = blazeFluid();
        if (amount <= 0 || lava == null || blaze == null) {
            return false;
        }
        // 先模拟检查两种流体，再实际扣除，避免已知不足时只消耗其中一种。
        boolean lavaOk = hasFluid(wem, lava, amount);
        boolean blazeOk = hasFluid(wem, blaze, amount);
        if (!lavaOk || !blazeOk) {
            notifyMissing(wem, lavaOk, blazeOk, lava, blaze, amount);
            return false;
        }
        MachineIO.inputFluid(wem, FluidStack.create(lava, amount));
        MachineIO.inputFluid(wem, FluidStack.create(blaze, amount));
        return true;
    }

    private static void notifyMissing(WorkableMultiblockMachine machine, boolean lavaOk, boolean blazeOk,
                                      Fluid lava, Fluid blaze, long amount) {
        try {
            Component lavaName = lavaName(lava);
            Component blazeName = blazeName(blaze);
            Component message;
            if (!lavaOk && !blazeOk) {
                message = Component.translatable("gtlmodular.tax.need_both", lavaName, blazeName);
            } else if (!lavaOk) {
                message = Component.translatable("gtlmodular.tax.need_single", lavaName);
            } else {
                message = Component.translatable("gtlmodular.tax.need_single", blazeName);
            }
            org.gtlcore.gtlcore.api.recipe.RecipeResult.of(machine,
                    org.gtlcore.gtlcore.api.recipe.RecipeResult.fail(message));
        } catch (RuntimeException e) {
            LOGGER.debug("[gtlmodular] 发送税提示失败", e);
        }
    }

    private static Component lavaName(Fluid lava) {
        return fluidName(lava, "熔岩");
    }

    public static Component lavaDisplayName() {
        return lavaName(lavaFluid());
    }

    public static Component blazeDisplayName() {
        return blazeName(blazeFluid());
    }

    private static Component blazeName(Fluid blaze) {
        return fluidName(blaze, "液态烈焰");
    }

    private static Component fluidName(Fluid fluid, String fallback) {
        try {
            if (fluid != null && !fluid.isSame(Fluids.EMPTY)) {
                return FluidStack.create(fluid, 1).getDisplayName();
            }
        } catch (RuntimeException ignored) {
        }
        return Component.literal(fallback);
    }

    private static WorkableElectricMultiblockMachine asWorkable(IRecipeLogicMachine machine) {
        if (machine instanceof WorkableElectricMultiblockMachine wem) {
            return wem;
        }
        return null;
    }

    private static boolean canRun(WorkableElectricMultiblockMachine machine) {
        int tier = machine.getTier();
        int baseline = GTLModularConfig.getBaselineTier();
        if (tier < baseline) {
            return GTLModularConfig.isAllowBelowBaseline();
        }
        return true;
    }

    private static long amountPerDrain(WorkableElectricMultiblockMachine machine) {
        int tier = machine.getTier();
        int baseline = GTLModularConfig.getBaselineTier();
        double mult = GTLModularConfig.getPerTierMultiplier();
        double exp = Math.max(0, tier - baseline);
        double amount = GTLModularConfig.getIvBaselineMbPerSecond() * Math.pow(mult, exp);
        return (long) Math.ceil(amount);
    }

    private static final java.util.Set<String> WARNED_FLUID_IDS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static String cachedLavaId;
    private static Fluid cachedLava;
    private static String cachedBlazeId;
    private static Fluid cachedBlaze;

    private static Fluid fluidById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(id));
        if (fluid == null && WARNED_FLUID_IDS.add(id)) {
            LOGGER.error("[gtlmodular] 税流体配置无效或未加载: {}（同类报错已抑制）", id);
        }
        return fluid;
    }

    /** 只缓存成功解析的流体，避免注册尚未就绪时永久固化失败结果。 */
    private static Fluid lavaFluid() {
        String id = GTLModularConfig.getLavaFluidId();
        if (cachedLava != null && java.util.Objects.equals(id, cachedLavaId)) {
            return cachedLava;
        }
        cachedLavaId = id;
        cachedLava = fluidById(id);
        return cachedLava;
    }

    private static Fluid blazeFluid() {
        String id = GTLModularConfig.getBlazeFluidId();
        if (cachedBlaze != null && java.util.Objects.equals(id, cachedBlazeId)) {
            return cachedBlaze;
        }
        cachedBlazeId = id;
        cachedBlaze = fluidById(id);
        return cachedBlaze;
    }

    private static boolean hasFluid(WorkableMultiblockMachine machine, Fluid fluid, long amount) {
        if (fluid == null || amount <= 0) {
            return false;
        }
        IRecipeCapabilityHolder holder = holderOf(machine);
        if (holder == null) {
            return false;
        }
        List<IRecipeHandler<?>> handlers;
        try {
            handlers = holder.getCapabilitiesProxy().get(IO.IN, FluidRecipeCapability.CAP);
        } catch (RuntimeException e) {
            LOGGER.debug("[gtlmodular] 读取流体输入仓失败", e);
            return false;
        }
        if (handlers == null || handlers.isEmpty()) {
            return false;
        }
        long total = 0;
        for (IRecipeHandler<?> handler : handlers) {
            if (handler == null) {
                continue;
            }
            for (Object content : handler.getContents()) {
                if (content instanceof FluidStack fs && fs.getFluid() == fluid) {
                    total += fs.getAmount();
                }
            }
        }
        return total >= amount;
    }

    @SuppressWarnings("unchecked")
    private static IRecipeCapabilityHolder holderOf(WorkableMultiblockMachine machine) {
        try {
            return (IRecipeCapabilityHolder) (Object) machine;
        } catch (ClassCastException e) {
            LOGGER.error("[gtlmodular] 机器未实现 IRecipeCapabilityHolder", e);
            return null;
        }
    }
}
