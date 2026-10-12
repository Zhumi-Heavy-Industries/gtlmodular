package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class NanochipModulePower {

    private final NanochipModuleMachine module;
    private final LongSupplier readBuffer;
    private final LongConsumer writeBuffer;
    private final LongSupplier readCapacity;
    private final LongConsumer writeCapacity;

    NanochipModulePower(NanochipModuleMachine module, LongSupplier readBuffer, LongConsumer writeBuffer,
                        LongSupplier readCapacity, LongConsumer writeCapacity) {
        this.module = module;
        this.readBuffer = readBuffer;
        this.writeBuffer = writeBuffer;
        this.readCapacity = readCapacity;
        this.writeCapacity = writeCapacity;
    }

    void setBufferCapacity(long capacity) {
        if (capacity > readCapacity.getAsLong()) {
            writeCapacity.accept(capacity);
        }
        if (readBuffer.getAsLong() > readCapacity.getAsLong()) {
            writeBuffer.accept(readCapacity.getAsLong());
        }
    }

    void ensureBufferCovers(long fullRecipeEU) {
        if (fullRecipeEU > readCapacity.getAsLong()) {
            writeCapacity.accept(fullRecipeEU);
        }
    }

    long fillBufferEU(long amount) {
        if (amount <= 0L || readCapacity.getAsLong() <= 0L) {
            return 0L;
        }
        long room = readCapacity.getAsLong() - readBuffer.getAsLong();
        long take = Math.min(room, amount);
        if (take <= 0L) {
            return 0L;
        }
        writeBuffer.accept(readBuffer.getAsLong() + take);
        return take;
    }

    boolean consumeBufferEU(long amount) {
        if (amount <= 0L) {
            return true;
        }
        if (readBuffer.getAsLong() < amount) {
            return false;
        }
        writeBuffer.accept(readBuffer.getAsLong() - amount);
        return true;
    }

    void clearBufferEU() {
        writeBuffer.accept(0L);
    }

    int energyPriority(NanochipModuleSpec kind) {
        return kind == null ? 1 : kind.energyPriority();
    }

    long availableInputPower(long ownVoltage) {
        long cap = ownVoltage;
        NanochipAssemblyComplexMachine host = module.getHost();
        if (host != null && module.hostConnected()) {
            long hostPower = host.getMaxInputPower();
            if (hostPower > cap) {
                cap = hostPower;
            }
        }
        return cap;
    }

    boolean drawingFromHost(com.gregtechceu.gtceu.api.misc.EnergyContainerList own) {
        return (own == null || own.getEnergyCapacity() <= 0L) && module.hostConnected();
    }

    int getTier(IntSupplier upstream) {
        if (module.drawingFromHost()) {
            NanochipAssemblyComplexMachine host = module.getHost();
            if (host != null) {
                return host.getTier();
            }
        }
        return upstream.getAsInt();
    }

    long getOverclockVoltage(LongSupplier upstream) {
        if (module.drawingFromHost()) {
            NanochipAssemblyComplexMachine host = module.getHost();
            if (host != null) {
                return host.getOverclockVoltage();
            }
        }
        return upstream.getAsLong();
    }

    com.gregtechceu.gtceu.api.misc.EnergyContainerList getEnergyContainer(
            com.gregtechceu.gtceu.api.misc.EnergyContainerList own,
            Supplier<? extends com.gregtechceu.gtceu.api.capability.IEnergyContainer> hostContainer) {
        if (own != null && own.getEnergyCapacity() > 0L) {
            return own;
        }
        NanochipAssemblyComplexMachine host = module.getHost();
        if (!module.hostConnected() || host == null) {
            return own;
        }
        // EnergyContainerList 在构造时记录电压，每次重新包装才能看到主机供电变化。
        return new com.gregtechceu.gtceu.api.misc.EnergyContainerList(List.of(hostContainer.get()));
    }

    private NanochipAssemblyComplexMachine host() {
        NanochipAssemblyComplexMachine h = module.getHost();
        return (h != null && h.isFormed()) ? h : null;
    }

    long getEnergyStored() {
        NanochipAssemblyComplexMachine h = host();
        return h == null ? 0L : h.getTotalStoredEU();
    }

    long getEnergyCapacity() {
        NanochipAssemblyComplexMachine h = host();
        return h == null ? 0L : h.getTotalCapacityEU();
    }

    long getInputVoltage() {
        NanochipAssemblyComplexMachine h = host();
        return h == null ? 0L : h.getMaxInputVoltage();
    }

    long changeEnergy(long amount) {
        if (amount >= 0L) {
            return 0L;
        }
        long need = -amount;
        return module.consumeBufferEU(need) ? -need : 0L;
    }

    /** 缓存与逐 tick 扣电读取同一份合批结果，避免校准折扣后仍按原配方预蓄电。 */
    boolean drawRecipeEnergy(GTRecipe recipe, IntSupplier progress, Consumer<Component> waitForEnergy) {
        NanochipAssemblyComplexMachine host = module.getHost();
        if (host == null || !module.hostConnected() || !host.isWorkingEnabled()) {
            waitForEnergy.accept(Component.translatable("gtlmodular.recipe.fail.host_unavailable"));
            return false;
        }
        long perTick = Math.max(1L, RecipeHelper.getInputEUt(recipe));
        long ticks = Math.max(1, recipe.duration);
        long full = perTick > Long.MAX_VALUE / ticks ? Long.MAX_VALUE : perTick * ticks;   // 按需电源逐 tick 实扣，避免整批电量超出 long；有限能源仓供电则须先攒够整批缓存。
        final boolean onDemand = host.isEnergySourceEffectivelyUnlimited();
        if (!onDemand && progress.getAsInt() == 0) {
            module.ensureBufferCovers(full);   // 配方可能长于种类参数给定的缓存时间，须按实际总耗电扩容。
            if (module.getBufferEU() < full) {
                host.requestBufferTopUp(module);
            }
            if (module.getBufferEU() < full) {
                waitForEnergy.accept(Component.translatable("gtlmodular.nanochip.module.buffer",
                        com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(full),
                        com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(module.getBufferEU())));
                return false;
            }
        }
        // 优先使用剩余缓存，避免切换到按需供电后遗留电量无法使用。
        final boolean paid = module.consumeBufferEU(perTick)
                || (onDemand && host.requestModuleEnergy(perTick, module.energyPriority() < 0));
        if (paid) {
            return true;
        }
        waitForEnergy.accept(org.gtlcore.gtlcore.api.recipe.RecipeResult.FAIL_NO_ENOUGH_EU_IN.reason());
        return false;
    }

    /** 本机能源仓本身保管预蓄电，避免转入未持久化的中央供电缓存而在读档时丢电。 */
    boolean localRecipePrecharged(GTRecipe recipe, int progress, Consumer<Component> waitForEnergy) {
        var own = module.getEnergyContainer();
        boolean onDemand = own != null
                && own.getEnergyCapacity() >= NanochipAssemblyComplexMachine.ON_DEMAND_POWER_FLOOR_EU;
        if (onDemand) return true;
        long perTick = Math.max(0L, RecipeHelper.getInputEUt(recipe));
        long remainingTicks = Math.max(1L, (long) recipe.duration - progress);
        long need = NanochipModulePowerDistribution.saturatingMul(perTick, remainingTicks);
        long stored = own == null ? 0L : Math.max(0L, own.getEnergyStored());
        // 饱和值只是显示上限，不能把超出 long 的整批需求当作已备足。
        if (perTick > Long.MAX_VALUE / remainingTicks || stored < need) {
            waitForEnergy.accept(Component.translatable(
                    "gtlmodular.nanochip.module.buffer",
                    com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(need),
                    com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(stored)));
            return false;
        }
        return true;
    }
}
