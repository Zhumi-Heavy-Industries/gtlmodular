package com.gtlmodular.common.machine.pattern;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 自动切换的公平轮转：两个模式都有料时不能互相饿死，同一个模式也不能被推料或积压长期霸占。
 */
class PatternAutoSwitchFairnessTest {

    private static final int TYPE_A = 0;
    private static final int TYPE_B = 1;

    @Test
    void busyModeWithBacklogStillYieldsToTheOtherModeAfterFairShare() {
        FakeHost host = new FakeHost(4);
        host.slot(0, TYPE_A, true);
        host.slot(1, TYPE_B, true);
        host.active = TYPE_A;
        PatternAutoSwitch autoSwitch = new PatternAutoSwitch(host, 20, 40, 200, 600, false);

        // 刚切换过（未满公平份额）：先让当前模式把这一批做完。
        autoSwitch.noteSwitchForTest(1_000L);
        assertEquals(-1, autoSwitch.findModeWithWaitingStock(host.types(), TYPE_A, 1_050L, 4, host::empty));

        // 满公平份额：轮到另一个模式，积压的一侧不再独占。
        assertEquals(TYPE_B, autoSwitch.findModeWithWaitingStock(host.types(), TYPE_A, 1_100L, 4, host::empty));
    }

    @Test
    void modeWithStockAndNoRivalKeepsTheMachine() {
        FakeHost host = new FakeHost(4);
        host.slot(0, TYPE_A, true);
        host.slot(1, TYPE_A, false);
        host.active = TYPE_A;
        PatternAutoSwitch autoSwitch = new PatternAutoSwitch(host, 20, 40, 200, 600, false);

        autoSwitch.noteSwitchForTest(0L);
        assertEquals(-1, autoSwitch.findModeWithWaitingStock(host.types(), TYPE_A, 10_000L, 4, host::empty));
    }

    @Test
    void stocklessActiveModeWaitsForArrivalsThenHandsOver() {
        FakeHost host = new FakeHost(4);
        host.slot(0, TYPE_A, false);
        host.slot(1, TYPE_B, true);
        host.active = TYPE_A;
        PatternAutoSwitch autoSwitch = new PatternAutoSwitch(host, 20, 40, 200, 600, false);
        autoSwitch.noteSwitchForTest(0L);

        // 第一次观察只记录起点，仍在等 AE 在途来料。
        assertEquals(-1, autoSwitch.findModeWithWaitingStock(host.types(), TYPE_A, 1_000L, 4, host::empty));
        // 宽限期内不切走。
        assertEquals(-1, autoSwitch.findModeWithWaitingStock(host.types(), TYPE_A, 1_100L, 4, host::empty));
        // 宽限期满，交给另一个模式。
        assertEquals(TYPE_B, autoSwitch.findModeWithWaitingStock(host.types(), TYPE_A, 1_600L, 4, host::empty));
    }

    @Test
    void emptyActiveModeSwitchesImmediately() {
        FakeHost host = new FakeHost(4);
        host.slot(3, TYPE_B, true);
        host.active = TYPE_A;
        PatternAutoSwitch autoSwitch = new PatternAutoSwitch(host, 20, 40, 200, 600, false);

        assertEquals(TYPE_B, autoSwitch.findModeWithWaitingStock(host.types(), TYPE_A, 500L, 4, host::empty));
    }

    private static final class FakeHost implements PatternAutoSwitch.Host {
        private final int[] recipeTypes;
        private final boolean[] waiting;
        private final List<GTRecipeType> types;
        private int active;
        private boolean touched;
        private boolean pinned;

        FakeHost(int slots) {
            this.recipeTypes = new int[slots];
            this.waiting = new boolean[slots];
            Arrays.fill(this.recipeTypes, -1);
            this.types = Arrays.asList(new GTRecipeType[2]);
        }

        void slot(int slot, int type, boolean hasStock) {
            recipeTypes[slot] = type;
            waiting[slot] = hasStock;
        }

        List<GTRecipeType> types() {
            return types;
        }

        boolean empty(int slot) {
            return recipeTypes[slot] < 0;
        }

        @Override public IRecipeLogicMachine firstRecipeLogicController() { return null; }
        @Override public List<GTRecipeType> availableRecipeTypes() { return types; }
        @Override public Level level() { return null; }
        @Override public int activeRecipeTypeIndex() { return active; }
        @Override public void setActiveRecipeTypeIndex(int index) { active = index; }
        @Override public boolean modeCursorTouched() { return touched; }
        @Override public int modeCursorIndex() { return active; }
        @Override public void setModeCursorIndex(int index) { }
        @Override public void clearObservedModeCaches(int previous, int live) { }
        @Override public int findModeWithWaitingStock(List<GTRecipeType> types, int active, long now) { return -1; }
        @Override public int recipeTypeOfSlot(int slot) { return recipeTypes[slot]; }
        @Override public boolean hasRecipeCacheInSlot(int slot) { return true; }
        @Override public boolean hasSlotWaitingStock(int slot) { return waiting[slot]; }
        @Override public void logCheck(List<GTRecipeType> types, RecipeLogic logic, int active, int target, long now) { }
        @Override public void clearAutoSwitchCaches(int previous, int target) { }
        @Override public void syncCustomNameToMode(int index) { }
        @Override public void onAutoSwitched(int previous, int target) { }
        @Override public boolean blocksPushModeSwitch(RecipeLogic logic) { return false; }
        @Override public void clearPushModeSwitchCaches(int slot, int index) { }
        @Override public boolean modeHasWaitingStock(int modeIndex) { return false; }
        @Override public boolean manualModePinned() { return pinned; }
        @Override public void pinManualMode() { pinned = true; }
        @Override public boolean pausesAutoSwitchForExternalScheduler() { return false; }
    }
}
