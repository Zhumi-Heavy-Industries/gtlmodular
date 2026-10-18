package com.gtlmodular.common.machine.independent.io;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IndependentRecipeIo {
    private final IRecipeLogicMachine machine;

    public IndependentRecipeIo(IRecipeLogicMachine machine) { this.machine = machine; }

    public void requireSupportedMachine() {
        requireSupportedParts();
        for (var route : inputRoutes()) route.validateHandlers();
        outputRoute().validateHandlers();
        tickRoute(IO.IN).validateHandlers();
        tickRoute(IO.OUT).validateHandlers();
    }

    public void requireSupported(GTRecipe recipe) {
        BatchContents.requireSupported(recipe.inputs, false);
        BatchContents.requireSupported(recipe.outputs, false);
        BatchContents.requireSupported(recipe.tickInputs, true);
        BatchContents.requireSupported(recipe.tickOutputs, true);
        if (!recipe.ingredientActions.isEmpty()) throw new IllegalArgumentException("需要 ingredient action 专用适配");
        requireSupportedParts();
        if (MeIo.enabled(machine) && MeIo.hasTickItems(recipe)) {
            throw new IllegalArgumentException("ME tick 物品/流体尚未适配");
        }
        for (var route : inputRoutes()) route.validate(recipe.inputs);
        outputRoute().validate(recipe.outputs);
        tickRoute(IO.IN).validate(recipe.tickInputs);
        tickRoute(IO.OUT).validate(recipe.tickOutputs);
    }

    private void requireSupportedParts() {
        var parts = parts();
        boolean hasMe = !parts.getMEPatternRecipeHandleParts().isEmpty() || !parts.getMEOutputRecipeHandleParts().isEmpty();
        if (hasMe && !MeIo.enabled(machine) && !Boolean.getBoolean("gtlmodular.testHost")) {
            throw new IllegalArgumentException("ME 样板/输出处理器需要开启 independentThreadsAllowMeIo");
        }
    }

    /** ME 输入走上游的整笔匹配/扣料；普通总线仍走逐 handler 的可计数 Route。 */
    public InputRoute selectInput(GTRecipe recipe, Map<RecipeCapability<?>, List<Content>> contents) {
        if (MeIo.enabled(machine) && !parts().getMEPatternRecipeHandleParts().isEmpty()) {
            return new MeInputRoute(recipe);
        }
        for (var route : inputRoutes()) if (route.fits(contents, IO.IN)) return route;
        return null;
    }

    public boolean outputFits(Map<RecipeCapability<?>, List<Content>> contents) {
        if (MeIo.enabled(machine) && !parts().getMEOutputRecipeHandleParts().isEmpty()) {
            return MeIo.outputFits(machine, contents);
        }
        return outputRoute().fits(contents, IO.OUT);
    }

    public boolean transferOutput(Map<RecipeCapability<?>, List<Content>> contents, boolean simulate) {
        if (MeIo.enabled(machine) && !parts().getMEOutputRecipeHandleParts().isEmpty()) {
            return MeIo.transferOutput(machine, contents, simulate);
        }
        return outputRoute().transfer(contents, IO.OUT, simulate);
    }

    public Route outputRoute() {
        var route = new Route();
        var parts = new ArrayList<>(parts().getNormalRecipeHandlePart(IO.OUT));
        parts.sort(RecipeHandlePart.COMPARATOR.reversed());
        for (var part : parts) route.add(part);
        return route;
    }

    public Route tickRoute(IO io) {
        var route = new Route();
        for (var row : new IO[]{io, IO.BOTH}) {
            machine.getCapabilitiesProxy().row(row).forEach((cap, handlers) -> {
                var sorted = new ArrayList<>(handlers);
                sorted.sort(IRecipeHandler.ENTRY_COMPARATOR);
                sorted.forEach(route::add);
            });
        }
        return route;
    }

    private List<Route> inputRoutes() {
        var machine = parts();
        var routes = new ArrayList<Route>();
        if (machine.isDistinct()) {
            for (var part : machine.getNormalRecipeHandlePart(IO.IN)) routes.add(new Route().add(part));
        } else {
            var shared = machine.getSharedRecipeHandlePart();
            for (var part : machine.getNormalRecipeHandlePart(IO.IN)) {
                var route = new Route();
                if (shared != null) shared.getCapability(FluidRecipeCapability.CAP).forEach(route::add);
                part.getCapability(ItemRecipeCapability.CAP).forEach(route::add);
                routes.add(route);
            }
            if (shared != null) routes.add(new Route().add(shared));
        }
        if (routes.isEmpty()) routes.add(new Route());
        return routes;
    }

    private IRecipeCapabilityMachine parts() {
        if (machine instanceof IRecipeCapabilityMachine parts) return parts;
        throw new IllegalArgumentException("缺少 GTLCore 处理器分组");
    }

    public interface InputRoute {
        boolean fits(Map<RecipeCapability<?>, List<Content>> contents, IO io);
        boolean transfer(Map<RecipeCapability<?>, List<Content>> remaining, IO io, boolean simulate);
    }

    private final class MeInputRoute implements InputRoute {
        private final GTRecipe recipe;

        private MeInputRoute(GTRecipe recipe) { this.recipe = recipe; }

        @Override
        public boolean fits(Map<RecipeCapability<?>, List<Content>> contents, IO io) {
            return transfer(BatchContents.copy(contents), io, true);
        }

        @Override
        public boolean transfer(Map<RecipeCapability<?>, List<Content>> remaining, IO io, boolean simulate) {
            if (io != IO.IN) return false;
            boolean okay = simulate ? MeIo.inputFits(machine, recipe) : MeIo.consumeInputs(machine, recipe);
            if (okay) remaining.clear();
            return okay;
        }
    }

    public static final class Route implements InputRoute {
        private final Map<RecipeCapability<?>, List<IRecipeHandler<?>>> handlers = new LinkedHashMap<>();
        private final Map<IRecipeHandler<?>, Boolean> seen = new IdentityHashMap<>();

        private Route add(RecipeHandlePart part) {
            for (var entry : part.getHandlerFastIterable()) entry.getValue().forEach(this::add);
            return this;
        }

        private void add(IRecipeHandler<?> handler) {
            if (seen.put(handler, true) == null) handlers.computeIfAbsent(handler.getCapability(), cap -> new ArrayList<>()).add(handler);
        }

        public void validate(Map<RecipeCapability<?>, List<Content>> contents) {
            contents.keySet().forEach(cap -> handlers.getOrDefault(cap, List.of()).forEach(StorageTransfers::requireSupported));
        }

        private void validateHandlers() {
            handlers.values().forEach(row -> row.forEach(StorageTransfers::requireSupported));
        }

        public boolean fits(Map<RecipeCapability<?>, List<Content>> contents, IO io) {
            return transfer(BatchContents.copy(contents), io, true);
        }

        public boolean fitsTickInput(Map<RecipeCapability<?>, List<Content>> contents, SharedTickPower power) {
            var copy = BatchContents.copy(contents);
            var remaining = power.remaining();
            boolean needsPower = false;
            for (var content : copy.getOrDefault(EURecipeCapability.CAP, List.of())) {
                long requested = (Long) content.content;
                needsPower |= requested > 0;
                long offered = remaining.min(java.math.BigInteger.valueOf(requested)).longValueExact();
                content.content = offered;
                remaining = remaining.subtract(java.math.BigInteger.valueOf(offered));
            }
            if (needsPower && power.remaining().signum() == 0) return false;
            return transfer(copy, IO.IN, true);
        }

        public boolean transfer(Map<RecipeCapability<?>, List<Content>> remaining, IO io, boolean simulate) {
            return transfer(remaining, io, simulate, null);
        }

        public boolean transfer(Map<RecipeCapability<?>, List<Content>> remaining, IO io, boolean simulate, SharedTickPower power) {
            validate(remaining);
            var transfers = new StorageTransfers(simulate, power);
            try {
                for (var entry : remaining.entrySet()) {
                    for (Content content : entry.getValue()) {
                        for (var handler : handlers.getOrDefault(entry.getKey(), List.of())) {
                            if (BatchContents.amount(entry.getKey(), content.content) == 0) break;
                            transfers.move(handler, io, content);
                        }
                    }
                }
            } catch (CountedTransfer.RecordedNotificationFailure exception) {
                // 数量已经回写；本次停止继续 IO，下一 tick 只能接着剩余量执行。
                return false;
            }
            return BatchContents.empty(remaining);
        }
    }
}
