package com.gtlmodular.common.machine.pattern;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.diag.SwallowedErrors;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class PatternAutoSwitch {

    public interface Host {
        IRecipeLogicMachine firstRecipeLogicController();
        List<GTRecipeType> availableRecipeTypes();
        Level level();
        int activeRecipeTypeIndex();
        void setActiveRecipeTypeIndex(int index);
        boolean modeCursorTouched();
        int modeCursorIndex();
        void setModeCursorIndex(int index);
        void clearObservedModeCaches(int previous, int live);
        int findModeWithWaitingStock(List<GTRecipeType> types, int active, long now);
        int recipeTypeOfSlot(int slot);
        boolean hasRecipeCacheInSlot(int slot);
        boolean hasSlotWaitingStock(int slot);
        void logCheck(List<GTRecipeType> types, RecipeLogic logic, int active, int target, long now);
        void clearAutoSwitchCaches(int previous, int target);
        void syncCustomNameToMode(int index);
        void onAutoSwitched(int previous, int target);
        boolean blocksPushModeSwitch(RecipeLogic logic);
        void clearPushModeSwitchCaches(int slot, int index);
        /** 该模式下是否有料在等：用来防止推料把正在干活的模式拽走。 */
        boolean modeHasWaitingStock(int modeIndex);
        /** 玩家在模式页手动选过模式；此后自动切换与推料都不得改回来。 */
        boolean manualModePinned();
        void pinManualMode();
        /** 宿主已被外部调度接管（增强版跨配方/隔离 IV 模式）时不切模式：那里没有"当前模式"。 */
        boolean pausesAutoSwitchForExternalScheduler();
    }

    /** 两个模式都有活时，各自至少拿到的连续加工时长；避免积压模式永远霸占机器。 */
    private static final long FAIR_SHARE_TICKS = 100L;

    private final Host host;
    private final int intervalTicks;
    private final int graceTicks;
    private final int blockTicks;
    private final int stocklessTicks;
    private final boolean resetStocklessWhileWorking;
    private int autoSwitchCooldown;
    private int pendingAutoSwitchType = -1;
    private long pendingAutoSwitchDeadline;
    private final Map<Integer, Long> autoSwitchBlockedUntil = new HashMap<>();
    private long activeModeBusySince = -1L;
    private long lastSwitchTick = Long.MIN_VALUE;
    private int diagActivePatterns;
    private int diagActiveStock;
    private int diagActiveCold;

    public PatternAutoSwitch(Host host, int intervalTicks, int graceTicks, int blockTicks,
                             int stocklessTicks, boolean resetStocklessWhileWorking) {
        this.host = host;
        this.intervalTicks = intervalTicks;
        this.graceTicks = graceTicks;
        this.blockTicks = blockTicks;
        this.stocklessTicks = stocklessTicks;
        this.resetStocklessWhileWorking = resetStocklessWhileWorking;
    }

    public void tick() {
        IRecipeLogicMachine controller = host.firstRecipeLogicController();
        if (controller != null) {
            int live = controller.getActiveRecipeType();
            if (host.activeRecipeTypeIndex() != live) {
                host.clearObservedModeCaches(host.activeRecipeTypeIndex(), live);
                host.setActiveRecipeTypeIndex(live);
            }
            if (!host.modeCursorTouched() && host.modeCursorIndex() != live) {
                host.setModeCursorIndex(live);
            }
        }
        if (!GTLModularConfig.isAutoRecipeModeSwitchEnabled()) {
            return;
        }
        List<GTRecipeType> types = host.availableRecipeTypes();
        if (types.size() <= 1 || controller == null) {
            return;
        }
        if (host.pausesAutoSwitchForExternalScheduler()) {
            // 外部调度按订单派工，切换只会重置它的配方逻辑；顺手清掉待切换，
            // 否则恢复那一刻会按早已过期的宽限期立刻切走并把目标模式拉黑。
            pendingAutoSwitchType = -1;
            activeModeBusySince = -1L;
            return;
        }
        if (host.manualModePinned()) {
            // 玩家点了模式页就以他的选择为准；自动切换再切走会让"手动指定"看起来完全没用。
            pendingAutoSwitchType = -1;
            activeModeBusySince = -1L;
            return;
        }
        RecipeLogic logic = controller.getRecipeLogic();
        if (logic.isWorking()) {
            pendingAutoSwitchType = -1;
            if (resetStocklessWhileWorking) {
                activeModeBusySince = -1L;
            }
            return;
        }
        Level level = host.level();
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        if (pendingAutoSwitchType >= 0) {
            if (now < pendingAutoSwitchDeadline) {
                return;
            }
            autoSwitchBlockedUntil.put(pendingAutoSwitchType, now + blockTicks);
            pendingAutoSwitchType = -1;
        }
        // 观察期和工作状态会暂停倒计时，不能用绝对时间代替逐次递减。
        if (autoSwitchCooldown > 0) {
            autoSwitchCooldown--;
            return;
        }
        autoSwitchCooldown = intervalTicks;
        int active = controller.getActiveRecipeType();
        int target = host.findModeWithWaitingStock(types, active, now);
        host.logCheck(types, logic, active, target, now);
        if (target < 0 || target == active) {
            return;
        }
        controller.setActiveRecipeType(target);
        logic.resetRecipeLogic();
        host.clearAutoSwitchCaches(active, target);
        host.setActiveRecipeTypeIndex(target);
        if (!host.modeCursorTouched()) {
            host.setModeCursorIndex(target);
        }
        host.syncCustomNameToMode(target);
        pendingAutoSwitchType = target;
        pendingAutoSwitchDeadline = now + graceTicks;
        lastSwitchTick = now;
        host.onAutoSwitched(active, target);
    }

    public void lockRecipeTypeForSlot(int slot, int index, List<GTRecipeType> types) {
        if (index < 0 || index >= types.size()) {
            return;
        }
        IRecipeLogicMachine controller = host.firstRecipeLogicController();
        if (controller == null) {
            return;
        }
        if (controller.getActiveRecipeType() == index) {
            host.setActiveRecipeTypeIndex(index);
            return;
        }
        if (host.pausesAutoSwitchForExternalScheduler()) {
            // 同模式只同步下标那一段仍要放行：外部调度下这个下标是诊断与名字表的唯一锚点。
            return;
        }
        if (host.manualModePinned()) {
            return;
        }
        if (host.modeHasWaitingStock(controller.getActiveRecipeType())) {
            // 当前模式还有活：先让它干完，之后由自动切换按公平份额轮转，推料不能直接拽走。
            return;
        }
        RecipeLogic logic = controller.getRecipeLogic();
        if (logic.isWorking() || host.blocksPushModeSwitch(logic)) {
            return;
        }
        controller.setActiveRecipeType(index);
        // 仅切换类型仍会复用 lastRecipe，必须重置逻辑才能按新模式搜索。
        logic.resetRecipeLogic();
        host.clearPushModeSwitchCaches(slot, index);
        host.setActiveRecipeTypeIndex(index);
        Level level = host.level();
        if (level != null) {
            lastSwitchTick = level.getGameTime();
        }
    }

    public int findModeWithWaitingStock(List<GTRecipeType> types, int active, long now,
                                       int slots, IntPredicate patternEmpty) {
        diagActivePatterns = 0;
        diagActiveStock = 0;
        diagActiveCold = 0;
        boolean activeHasWaitingStock = false;
        for (int slot = 0; slot < slots; slot++) {
            if (patternEmpty.test(slot) || host.recipeTypeOfSlot(slot) != active) {
                continue;
            }
            diagActivePatterns++;
            if (!host.hasRecipeCacheInSlot(slot)) {
                diagActiveCold++;
            }
            if (host.hasSlotWaitingStock(slot)) {
                diagActiveStock++;
                activeHasWaitingStock = true;
                break;
            }
        }
        int candidate = -1;
        for (int slot = 0; slot < slots && candidate < 0; slot++) {
            if (patternEmpty.test(slot)) {
                continue;
            }
            int type = host.recipeTypeOfSlot(slot);
            if (type < 0 || type >= types.size() || type == active) {
                continue;
            }
            Long blocked = autoSwitchBlockedUntil.get(type);
            if (blocked != null && now < blocked) {
                continue;
            }
            if (host.hasSlotWaitingStock(slot)) {
                candidate = type;
            }
        }
        if (candidate < 0) {
            activeModeBusySince = -1L;
            return -1;
        }
        if (activeHasWaitingStock) {
            // 两个模式都有料：按公平份额轮流，否则积压的那个会一直霸占机器、另一个永远等不到。
            activeModeBusySince = -1L;
            boolean due = lastSwitchTick == Long.MIN_VALUE || now - lastSwitchTick >= FAIR_SHARE_TICKS;
            return due ? candidate : -1;
        }
        if (diagActivePatterns > 0) {
            // 宽限期容纳 AE 在途来料；尚未认出配方只能用于诊断，不能挡住其他模式。
            if (activeModeBusySince < 0L) {
                activeModeBusySince = now;
                return -1;
            }
            if (now - activeModeBusySince < stocklessTicks) {
                return -1;
            }
        } else {
            activeModeBusySince = -1L;
        }
        return candidate;
    }

    public static <T> boolean hasWaitingStock(Supplier<T> readSlot, Predicate<T> hasItems, Predicate<T> hasFluids) {
        try {
            T internal = readSlot.get();
            return internal != null && (hasItems.test(internal) || hasFluids.test(internal));
        } catch (Throwable ignored) {
            SwallowedErrors.report("PatternAutoSwitch#hasWaitingStock", ignored);
            return false;
        }
    }

    public void clearBlocked() { autoSwitchBlockedUntil.clear(); }

    /** 单测注入切换时刻用；正式路径由 tick 与推料锁模式两处写入。 */
    void noteSwitchForTest(long tick) { lastSwitchTick = tick; }

    public long idleTicks(long now) { return activeModeBusySince < 0L ? 0L : now - activeModeBusySince; }

    public int activePatterns() { return diagActivePatterns; }

    public int activeStock() { return diagActiveStock; }

    public int activeCold() { return diagActiveCold; }
}
