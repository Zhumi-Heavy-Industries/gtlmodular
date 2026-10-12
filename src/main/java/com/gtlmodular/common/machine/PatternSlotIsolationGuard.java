package com.gtlmodular.common.machine;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.minecraft.resources.ResourceLocation;

/** 样板原料能相互拼合时可能跨槽匹配，按已记录的归属槽判定可避免串配方。 */
public final class PatternSlotIsolationGuard {

    public static boolean STRICT_SLOT_ISOLATION = true;

    private static final ResourceLocation DUMMY_TYPE = new ResourceLocation("gtceu", "dummy");

    private static long lastLogMs = 0L;

    private PatternSlotIsolationGuard() {
    }

    public static boolean rejects(Object ownerMachine, GTRecipe recipe) {
        if (!STRICT_SLOT_ISOLATION || recipe == null) {
            return false;
        }
        try {
            if (isAuxiliaryIORecipe(recipe)) {
                return false;
            }
            if (!(ownerMachine instanceof MoltenPatternAssemblyPartMachine machine)) {
                return false;
            }
            int owner = machine.slotOwning(recipe);
            if (owner < 0) {
                return false;   // 归属未知时缺少拦截依据，继续交给原匹配逻辑。
            }
            int[] active = machine.activePatternSlotsForIsolation();
            if (active == null || active.length == 0) {
                return false;
            }
            for (int slot : active) {
                if (slot == owner) {
                    return false;
                }
            }
            long now = System.currentTimeMillis();
            if (now - lastLogMs > 1000L) {
                lastLogMs = now;
                com.gtlmodular.GTLModular.LOGGER.info(
                        "[gtlmodular] 槽隔离：拒绝跨槽配方 {}（归属槽 {} 不在参与集内）@{}",
                        recipe.getId(), owner, machine.getPos());
            }
            return true;
        } catch (Throwable ignored) {
            return false;   // 隔离诊断失败不能阻止原有加工。
        }
    }

    public static boolean isAuxiliaryIORecipe(GTRecipe recipe) {
        try {
            ResourceLocation id = recipe.getType() == null ? null : recipe.getType().registryName;
            return id != null && id.equals(DUMMY_TYPE);
        } catch (Throwable ignored) {
            return false;
        }
    }
}