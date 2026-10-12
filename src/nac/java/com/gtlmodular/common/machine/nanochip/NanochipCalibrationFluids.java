package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.api.machine.trait.ILockRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeExtensionCopier;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** 免耗必须发生在搜索和并行计算之前，否则空培养基仓仍会挡住已经获得减免的配方。 */
public final class NanochipCalibrationFluids {

    private NanochipCalibrationFluids() {
    }

    public static NanochipCalibration.Family exemptionFor(NanochipModuleMachine module) {
        if (!module.getModuleSpec().features().biologicalExemption()) return null;
        NanochipAssemblyComplexMachine host = module.getHost();
        if (host == null || !module.hostConnected()) return null;
        NanochipCalibration calibration = host.getCalibration();
        if (calibration.specialActive(NanochipCalibration.Family.BIOWARE)) {
            return NanochipCalibration.Family.BIOWARE;
        }
        return calibration.specialActive(NanochipCalibration.Family.WETWARE)
                ? NanochipCalibration.Family.WETWARE : null;
    }

    static boolean exemptsMedium(NanochipCalibration.Family family, String fluidId) {
        if (family != NanochipCalibration.Family.WETWARE && family != NanochipCalibration.Family.BIOWARE) {
            return false;
        }
        if ("gtceu:raw_growth_medium".equals(fluidId) || "gtceu:sterilized_growth_medium".equals(fluidId)) {
            return true;
        }
        return family == NanochipCalibration.Family.BIOWARE
                && ("gtceu:biomediumraw".equals(fluidId) || "gtceu:biohmediumsterilized".equals(fluidId));
    }

    /** 返回 null 让未获减免的模块继续走原搜索，避免扩大对上游机器的影响。 */
    public static Iterator<GTRecipe> lookup(NanochipModuleMachine module, ILockRecipe lock,
                                           Predicate<GTRecipe> canHandle) {
        NanochipCalibration.Family family = exemptionFor(module);
        if (family == null) return null;
        if (lock.isLock() && lock.getLockRecipe() != null) {
            GTRecipe modified = exemptInputs(lock.getLockRecipe(), family);
            return canHandle.test(modified) ? List.of(modified).iterator() : List.<GTRecipe>of().iterator();
        }
        var lookup = module.getRecipeType().getLookup();
        Iterable<GTRecipe> indexed = () -> lookup.getRecipeIterator(module, recipe -> true);
        // 配方树需要全部输入才能命中；免耗后须补查树中原配方，同时保留 ME 提供的候选。
        Iterator<GTRecipe> candidates = Stream.concat(StreamSupport.stream(indexed.spliterator(), false),
                lookup.getLookup().getRecipes(false)).distinct().iterator();
        return new Iterator<>() {
            private GTRecipe next;
            private boolean finished;

            @Override
            public boolean hasNext() {
                while (next == null && !finished && candidates.hasNext()) {
                    GTRecipe original = candidates.next();
                    GTRecipe modified = exemptInputs(original, family);
                    if (canHandle.test(modified)) {
                        if (lock.isLock()) {
                            // 锁住原配方，校准失效后仍能恢复它的培养基要求。
                            lock.setLockRecipe(original);
                            finished = true;
                        }
                        next = modified;
                    }
                }
                return next != null;
            }

            @Override
            public GTRecipe next() {
                if (!hasNext()) throw new java.util.NoSuchElementException();
                GTRecipe result = next;
                next = null;
                return result;
            }
        };
    }

    private static GTRecipe exemptInputs(GTRecipe recipe, NanochipCalibration.Family family) {
        List<Content> fluids = recipe.getInputContents(FluidRecipeCapability.CAP);
        List<Content> retained = fluids.stream().filter(content -> !exemptIngredient(content, family)).toList();
        if (retained.size() == fluids.size()) return recipe;
        GTRecipe copy = RecipeExtensionCopier.copy(recipe, recipe.copy());
        if (retained.isEmpty()) {
            copy.inputs.remove(FluidRecipeCapability.CAP);
        } else {
            copy.inputs.put(FluidRecipeCapability.CAP, new java.util.ArrayList<>(retained));
        }
        return copy;
    }

    private static boolean exemptIngredient(Content content, NanochipCalibration.Family family) {
        FluidStack[] alternatives = FluidRecipeCapability.CAP.of(content.content).getStacks();
        if (alternatives.length == 0) return false;
        for (FluidStack fluid : alternatives) {
            var id = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
            // 含非培养基选项的自定义原料不能整体免除，以免绕过其他耗材。
            if (id == null || !exemptsMedium(family, id.toString())) return false;
        }
        return true;
    }
}
