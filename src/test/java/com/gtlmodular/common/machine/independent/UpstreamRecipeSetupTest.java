package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.control.IndependentHandoff;
import com.gtlmodular.common.machine.independent.control.IndependentSettingsReload;
import com.gtlmodular.common.machine.independent.control.UpstreamRecipeSetup;
import com.gtlmodular.common.machine.independent.eligibility.ThreadEligibilitySettings;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UpstreamRecipeSetupTest {
    @Test
    void alreadyMergedRecipeKeepsItsAdmissionEvenAfterNewSettingsArePublished() {
        var reload = new IndependentSettingsReload();
        var setup = new UpstreamRecipeSetup<Object>();
        assertFalse(IndependentHandoff.decide(false, reload.current().enabled(), true,
                true, false, false, false).blockUpstreamStarts());
        var recipe = new Object();
        setup.remember(recipe);

        reload.request();
        reload.publishPending(() -> new ThreadEligibilitySettings(true, Set.of(), Set.of()));

        assertTrue(IndependentHandoff.decide(false, reload.current().enabled(), true,
                true, false, false, false).blockUpstreamStarts());
        assertTrue(setup.consume(recipe));
        assertFalse(setup.consume(recipe));
        var upstreamRunning = IndependentHandoff.decide(false, reload.current().enabled(), true,
                true, true, false, false);
        assertFalse(upstreamRunning.runIndependent());
        assertTrue(upstreamRunning.blockUpstreamStarts());
    }

    @Test
    void equivalentRecipeCannotBorrowAnotherBatchesAdmission() {
        var setup = new UpstreamRecipeSetup<String>();
        String recipe = new String("same recipe id");
        setup.remember(recipe);
        assertFalse(setup.consume(new String("same recipe id")));
        assertFalse(setup.consume(recipe));
    }

    @Test
    void emptyAndBlockedMergesCannotLeaveASetupPermit() {
        var setup = new UpstreamRecipeSetup<Object>();
        var recipe = new Object();
        setup.remember(recipe);
        setup.remember(null);
        assertFalse(setup.consume(null));
        assertFalse(setup.consume(recipe));

        setup.remember(recipe);
        setup.clear();
        assertFalse(setup.consume(recipe));
    }

    @Test
    void nextMergeReplacesAnUnusedPermit() {
        var setup = new UpstreamRecipeSetup<Object>();
        var first = new Object();
        var second = new Object();
        setup.remember(first);
        setup.clear();
        setup.remember(second);
        assertTrue(setup.consume(second));
        assertFalse(setup.consume(first));
    }
}
