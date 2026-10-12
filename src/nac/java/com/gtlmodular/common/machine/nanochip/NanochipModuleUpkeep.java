package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidSlot;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEFilterIOPartMachine;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.utils.MachineIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/** 耗材检测与扣除共用流体来源，避免加成接受某等级净化水而运行扣料拒绝它。 */
public final class NanochipModuleUpkeep {

    private static final Logger LOGGER = LoggerFactory.getLogger("GTL-Modular");

    public static final int DRAIN_INTERVAL_TICKS = 20;

    private NanochipModuleUpkeep() {
    }

    public static boolean onBeforeWorking(IRecipeLogicMachine machine, GTRecipe recipe) {
        return check(machine, false);
    }

    public static boolean onWorking(IRecipeLogicMachine machine) {
        return check(machine, true);
    }

    private static boolean check(IRecipeLogicMachine machine, boolean consume) {
        if (!(machine instanceof NanochipModuleMachine module)) {
            return true;
        }
        NanochipModuleSpec kind = module.getModuleSpec();
        if (!kind.hasUpkeep()) {
            return true;
        }
        if (isExempt(module)) {
            return true;
        }
        Fluid fluid = fluidOf(kind.upkeepFluidId());
        if (fluid == null || fluid.isSame(Fluids.EMPTY)) {
            return true;
        }
        long amount = kind.upkeepMbPerSecond();
        if (amount <= 0L) {
            return true;
        }
        if (consume) {
            long offset = module.getOffsetTimer();
            if (offset % DRAIN_INTERVAL_TICKS != 0L) {
                return true;
            }
        }
        boolean family = isPurifiedWaterFamily(kind.upkeepFluidId());
        Fluid drain = pickAvailable(module, fluid, amount, family);
        if (drain == null) {
            notifyMissing(module, fluid, amount);
            return false;
        }
        if (consume) {
            MachineIO.inputFluid(module, FluidStack.create(drain, amount));
        }
        return true;
    }

    public static final List<String> PURIFIED_WATER_TIERS = List.of(
            "gtceu:grade_1_purified_water",
            "gtceu:grade_2_purified_water",
            "gtceu:grade_3_purified_water",
            "gtceu:grade_4_purified_water",
            "gtceu:grade_5_purified_water",
            "gtceu:grade_6_purified_water",
            "gtceu:grade_7_purified_water",
            "gtceu:grade_8_purified_water",
            "gtceu:grade_9_purified_water",
            "gtceu:grade_10_purified_water",
            "gtceu:grade_11_purified_water",
            "gtceu:grade_12_purified_water",
            "gtceu:grade_13_purified_water",
            "gtceu:grade_14_purified_water",
            "gtceu:grade_15_purified_water",
            "gtceu:grade_16_purified_water");

    public static boolean isPurifiedWaterFamily(String id) {
        return id != null && PURIFIED_WATER_TIERS.contains(id);
    }

    /** 水等级识别共用同一张表，避免加成与耗材对等级范围的理解不同。 */
    public static int purifiedWaterTierOf(String fluidId) {
        if (fluidId == null) {
            return 0;
        }
        int idx = PURIFIED_WATER_TIERS.indexOf(fluidId);
        return idx < 0 ? 0 : idx + 1;
    }

    private static int purifiedWaterTier(Fluid fluid) {
        ResourceLocation rid = ForgeRegistries.FLUIDS.getKey(fluid);
        if (rid == null) {
            return 0;
        }
        for (int i = 0; i < PURIFIED_WATER_TIERS.size(); i++) {
            if (PURIFIED_WATER_TIERS.get(i).equals(rid.toString())) {
                return i + 1;
            }
        }
        return 0;
    }

    private static boolean isPurifiedWater(Fluid fluid) {
        return purifiedWaterTier(fluid) > 0;
    }

    private static volatile int detectedTier;

    private static volatile long detectedAmount;

    public static int lastDetectedTier() {
        return detectedTier;
    }

    public static long lastDetectedAmount() {
        return detectedAmount;
    }

    private static int highestStockedTier(long[] perTier) {
        for (int tier = perTier.length - 1; tier >= 1; tier--) {
            if (perTier[tier] > 0L) {
                return tier;
            }
        }
        return 0;
    }

    private static Fluid pickAvailable(WorkableMultiblockMachine machine, Fluid fluid, long amount, boolean family) {
        List<FluidStack> stocked = scanInputFluids(machine);
        if (!family) {
            long exact = 0L;
            for (FluidStack fs : stocked) {
                if (fs.getFluid() == fluid) {
                    long add = fs.getAmount();
                    if (add > 0L) {
                        exact = saturatingAdd(exact, add);
                    }
                }
            }
            return exact >= amount ? fluid : null;
        }
        long[] perTier = new long[PURIFIED_WATER_TIERS.size() + 1];
        long total = 0L;
        for (FluidStack fs : stocked) {
            // 网络库存可能报 long 上限，累加须饱和并忽略非正数，避免回绕成负值。
            long add = fs.getAmount();
            if (add <= 0L) {
                continue;
            }
            int tier = purifiedWaterTier(fs.getFluid());
            if (tier > 0) {
                perTier[tier] = saturatingAdd(perTier[tier], add);
                total = saturatingAdd(total, add);
            }
        }
        int tier = highestStockedTier(perTier);
        detectedTier = tier;
        detectedAmount = total;
        if (total < amount) {
            return null;
        }
        return fluidOf(PURIFIED_WATER_TIERS.get(tier - 1));
    }

    private static boolean isExempt(NanochipModuleMachine module) {
        return NanochipCalibrationFluids.exemptionFor(module) != null;
    }

    /** 多个网络库存来源相加可能溢出，饱和到 long 上限以免误判无料。 */
    private static long saturatingAdd(long a, long b) {
        if (b <= 0L) {
            return a;
        }
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    private static boolean hasFluid(WorkableMultiblockMachine machine, Fluid fluid, long amount) {
        long total = 0L;
        for (FluidStack fs : scanInputFluids(machine)) {
            if (fs.getFluid() == fluid) {
                long add = fs.getAmount();
                if (add > 0L) {
                    total = saturatingAdd(total, add);
                }
            }
        }
        return total >= amount;
    }

    public static java.util.List<FluidStack> scanInputFluids(WorkableMultiblockMachine machine) {
        java.util.List<FluidStack> out = new java.util.ArrayList<>();
        // 没有标准流体仓也可能有 ME 输入，缺少普通 handler 时不能提前返回。
        IRecipeCapabilityHolder holder = holderOf(machine);
        List<IRecipeHandler<?>> handlers = null;
        if (holder != null) {
            try {
                handlers = holder.getCapabilitiesProxy().get(IO.IN, FluidRecipeCapability.CAP);
            } catch (RuntimeException e) {
                LOGGER.debug("[gtlmodular] NAC 模块读取流体输入仓失败", e);
            }
        }
        if (handlers != null) {
            for (IRecipeHandler<?> handler : handlers) {
                if (handler == null) {
                    continue;
                }
                for (Object content : handler.getContents()) {
                    if (content instanceof FluidStack fs && fs.getAmount() > 0L) {
                        out.add(fs);
                    }
                }
            }
        }
        if (NanochipModContext.config().isNanochipSimpleLogistics()) {
            appendMEFluidContents(machine, out);
        }
        // 库存类 AE 部件在两种物流模式下都可供料，不能被简单模式分支遮住。
        appendAEStockFluidContents(machine, out);
        return out;
    }

    private static void appendMEFluidContents(WorkableMultiblockMachine machine, List<FluidStack> out) {
        List<IMultiPart> parts;
        try {
            parts = machine.getParts();
        } catch (RuntimeException e) {
            LOGGER.debug("[gtlmodular] NAC 读取结构部件清单失败（跳过 ME 流体通道）", e);
            return;
        }
        if (parts == null) {
            return;
        }
        for (IMultiPart part : parts) {
            if (!(part instanceof IMEFilterIOPartMachine mePart)) {
                continue;
            }
            try {
                if (!mePart.getMETrait().getIO().support(IO.IN)) {
                    continue;
                }
                var traits = mePart.getMERecipeHandlerTraits();
                if (traits == null || traits.right() == null) {
                    continue;
                }
                Int2ObjectMap<List<FluidStack>> bySlot = traits.right().getActiveAndUnCachedSlotsLimitContentsMap();
                if (bySlot == null) {
                    continue;
                }
                for (List<FluidStack> slot : bySlot.values()) {
                    if (slot == null) {
                        continue;
                    }
                    for (FluidStack fs : slot) {
                        if (fs != null && fs.getAmount() > 0L) {
                            out.add(fs);
                        }
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                LOGGER.debug("[gtlmodular] NAC 读取 ME 流体通道失败（该部件跳过）", e);
            }
        }
    }

    private static void appendAEStockFluidContents(WorkableMultiblockMachine machine, List<FluidStack> out) {
        List<IMultiPart> parts;
        try {
            parts = machine.getParts();
        } catch (RuntimeException e) {
            LOGGER.debug("[gtlmodular] NAC 读取结构部件清单失败（跳过 AE 库存通道）", e);
            return;
        }
        if (parts == null) {
            return;
        }
        for (IMultiPart part : parts) {
            if (part == null || part.self() == null) {
                continue;
            }
            List<MachineTrait> traits;
            try {
                traits = part.self().getTraits();
            } catch (RuntimeException | LinkageError e) {
                continue;
            }
            if (traits == null) {
                continue;
            }
            for (MachineTrait trait : traits) {
                if (!(trait instanceof ExportOnlyAEFluidList stockList)) {
                    continue;
                }
                try {
                    ExportOnlyAEFluidSlot[] slots = stockList.getInventory();
                    if (slots == null) {
                        continue;
                    }
                    for (ExportOnlyAEFluidSlot slot : slots) {
                        if (slot == null) {
                            continue;
                        }
                        FluidStack fs = slot.getFluid();
                        long amount = slot.getFluidAmount();
                        if (fs == null || fs.getRawFluid() == null || amount <= 0L) {
                            continue;
                        }
                        // 网络库存可能超过 int，读取实际库存量并保留 long 精度。
                        out.add(FluidStack.create(fs.getRawFluid(), amount, fs.getTag()));
                    }
                } catch (RuntimeException | LinkageError e) {
                    LOGGER.debug("[gtlmodular] NAC 读取 AE 库存流体失败（该槽跳过）", e);
                }
            }
        }
    }

    /** 时间差用于限流，不能用 Long.MIN_VALUE 哨兵导致减法溢出。 */
    private static long lastDetectLogTick = -10_000L;

    private static void notifyMissing(NanochipModuleMachine module, Fluid fluid, long amount) {
        try {
            boolean water = isPurifiedWaterFamily(module.getModuleSpec().upkeepFluidId());
            Component fluidName = water
                    ? Component.translatable("gtlmodular.nanochip.module.upkeep.water_any_tier")
                    : FluidStack.create(fluid, 1).getDisplayName();
            RecipeResult.of(module, RecipeResult.fail(
                    Component.translatable("gtlmodular.nanochip.module.upkeep.missing",
                            module.getModuleSpec().nameZh(), fluidName, amount)));
            if (water) {
                net.minecraft.world.level.Level lv = module.getLevel();
                long now = lv == null ? 0L : lv.getGameTime();
                if (now - lastDetectLogTick >= 100L) {
                    lastDetectLogTick = now;
                    LOGGER.info("[gtlmodular] NAC 模块耗材检测 @{}（{}）：净化水逐级扫描 = 第 {} 级 / 共 {} mB"
                                    + "（每秒需 {} mB）⇒ 判定不足",
                            module.getPos(), module.getModuleSpec().nameZh(), detectedTier, detectedAmount, amount);
                }
            }
        } catch (RuntimeException e) {
            LOGGER.debug("[gtlmodular] NAC 模块耗材提示发送失败", e);
        }
    }

    /** 运行时机器对象实现了 IRecipeCapabilityHolder；桥接转换以绕开静态类型检查。 */
    @SuppressWarnings("unchecked")
    private static IRecipeCapabilityHolder holderOf(WorkableMultiblockMachine machine) {
        try {
            return (IRecipeCapabilityHolder) (Object) machine;
        } catch (ClassCastException e) {
            LOGGER.error("[gtlmodular] NAC 模块未实现 IRecipeCapabilityHolder", e);
            return null;
        }
    }

    private static final Map<String, Fluid> FLUID_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> WARNED_IDS = ConcurrentHashMap.newKeySet();

    public static Component fluidDisplayName(String fluidId, String fallback) {
        Fluid fluid = fluidOf(fluidId);
        if (fluid != null && !fluid.isSame(Fluids.EMPTY)) {
            try {
                return FluidStack.create(fluid, 1).getDisplayName();
            } catch (RuntimeException ignored) {
            }
        }
        return Component.literal(fallback);
    }

    private static Fluid fluidOf(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Fluid cached = FLUID_CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(id));
        if (fluid == null) {
            if (WARNED_IDS.add(id)) {
                LOGGER.warn("[gtlmodular] NAC 模块耗材流体不存在: {}（本条只提示一次，该模块暂不扣料）", id);
            }
            return null;
        }
        FLUID_CACHE.put(id, fluid);
        return fluid;
    }

    public static boolean sameFluid(String a, String b) {
        return Objects.equals(a, b);
    }
}
