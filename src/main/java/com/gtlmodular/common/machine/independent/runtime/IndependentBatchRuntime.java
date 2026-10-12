package com.gtlmodular.common.machine.independent.runtime;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gtlmodular.common.machine.independent.io.IndependentRecipeIo;
import com.gtlmodular.common.machine.independent.persistence.IndependentRecipePayload;
import com.gtlmodular.common.machine.independent.recipe.SingleRecipeCandidate;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class IndependentBatchRuntime {
    private final Map<IndependentRecipePayload, Prepared> prepared = new IdentityHashMap<>();
    private final Map<IndependentRecipePayload, String> waits = new IdentityHashMap<>();
    private String admissionReason;

    public void beginTick() { prepared.clear(); waits.clear(); admissionReason = null; }
    public String admissionReason() { return admissionReason; }
    public String waitReason(IndependentRecipePayload payload) { return waits.get(payload); }
    void reject(String reason) { admissionReason = reason; }
    boolean waitFor(IndependentRecipePayload payload, String reason) { waits.put(payload, reason); return false; }
    void prepare(IndependentRecipePayload payload, SingleRecipeCandidate source) { prepared.put(payload, new Prepared(source)); }
    Prepared prepared(IndependentRecipePayload payload) {
        var found = prepared.get(payload);
        if (found == null) throw new IllegalStateException("Missing unconsumed candidate");
        return found;
    }
    void consumed(IndependentRecipePayload payload) { prepared.remove(payload); }

    static final class Prepared {
        final SingleRecipeCandidate source;
        IndependentRecipeIo.Route inputs;
        Map<RecipeCapability<?>, List<Content>> beforeInputs;
        Prepared(SingleRecipeCandidate source) { this.source = source; }
    }
}
