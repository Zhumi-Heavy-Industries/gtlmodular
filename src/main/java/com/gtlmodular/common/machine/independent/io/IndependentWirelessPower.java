package com.gtlmodular.common.machine.independent.io;

import com.gtladd.gtladditions.api.machine.IWirelessElectricMultiblockMachine;
import com.gtladd.gtladditions.api.machine.trait.IWirelessNetworkEnergyHandler;
import com.gtladd.gtladditions.api.recipe.WirelessGTRecipe;
import com.gtlmodular.common.machine.independent.persistence.BatchRecipeExtensions.Power;
import com.gtlmodular.common.machine.independent.persistence.IndependentRecipePayload;

public final class IndependentWirelessPower {
    private IndependentWirelessPower() {}

    public static IWirelessNetworkEnergyHandler handler(Object machine, Power power) {
        if (power == Power.WIRED) return null;
        if (!(machine instanceof IWirelessElectricMultiblockMachine wireless)) throw new IllegalArgumentException("缺少无线供电接口");
        var handler = wireless.getWirelessNetworkEnergyHandler();
        if (handler == null) return null;
        String expected = power == Power.WIRELESS_ACCOUNT
                ? "com.gtladd.gtladditions.api.machine.wireless.GTLAddWirelessWorkableElectricMultipleRecipesMachine$SelfWirelessNetworkHandler"
                : "com.gtladd.gtladditions.common.machine.multiblock.part.WirelessEnergyNetworkTerminalPartMachine$WirelessNetworkEnergyHandler";
        if (!handler.getClass().getName().equals(expected)) throw StorageTransfers.unsupported(handler);
        return handler;
    }

    public static boolean ready(Object machine, IndependentRecipePayload payload) {
        if (payload.power() == Power.WIRED || payload.wirelessPaid()) return true;
        var handler = handler(machine, payload.power());
        var amount = amount(payload);
        return handler != null && handler.isOnline() &&
                (amount.signum() >= 0 || handler.getMaxAvailableEnergy().compareTo(amount.negate()) >= 0);
    }

    public static java.math.BigInteger available(Object machine) {
        if (!(machine instanceof IWirelessElectricMultiblockMachine wireless)) return java.math.BigInteger.ZERO;
        var handler = wireless.getWirelessNetworkEnergyHandler();
        return handler == null || !handler.isOnline() ? java.math.BigInteger.ZERO
                : handler.getMaxAvailableEnergy().max(java.math.BigInteger.ZERO);
    }

    public static boolean pay(Object machine, IndependentRecipePayload payload, SharedTickPower power) {
        if (payload.power() == Power.WIRED || payload.wirelessPaid()) return true;
        var amount = amount(payload);
        if (amount.signum() < 0 && !power.reserve(amount.negate())) return false;
        return pay(machine, payload);
    }

    public static boolean pay(Object machine, IndependentRecipePayload payload) {
        if (payload.power() == Power.WIRED || payload.wirelessPaid()) return true;
        var handler = handler(machine, payload.power());
        if (handler == null || !handler.isOnline() || !handler.consumeEnergy(amount(payload))) return false;
        payload.setWirelessPaid(true);
        return true;
    }

    private static java.math.BigInteger amount(IndependentRecipePayload payload) {
        if (!(payload.recipe() instanceof WirelessGTRecipe recipe) || recipe.getWirelessEuTickInputs() == null) {
            throw new IllegalArgumentException("Missing wireless tick energy");
        }
        return recipe.getWirelessEuTickInputs();
    }
}
