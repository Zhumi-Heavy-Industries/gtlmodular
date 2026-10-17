package com.gtlmodular.common.machine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.AETextInputButtonWidget;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.diag.SwallowedErrors;
import com.gtlmodular.common.machine.pattern.PatternArchiveSnapshots;
import com.gtlmodular.common.machine.pattern.PatternAutoSwitch;
import com.gtlmodular.common.machine.pattern.PatternClientSyncRefresh;
import com.gtlmodular.common.machine.pattern.PatternIdentityGate;
import com.gtlmodular.common.machine.pattern.PatternLookupNarrowing;
import com.gtlmodular.common.machine.pattern.PatternModeContext;
import com.gtlmodular.common.machine.pattern.PatternModeNaming;
import com.gtlmodular.common.machine.pattern.PatternNameController;
import com.gtlmodular.common.machine.pattern.PatternRecipeMatching;
import com.gtlmodular.common.machine.pattern.PatternRecipeTypeSearch;
import com.gtlmodular.common.machine.pattern.PatternRewriteCache;
import com.gtlmodular.common.machine.pattern.PatternStaleSlotCache;
import com.gtlmodular.common.machine.pattern.PatternProviderRefresh;
import com.gtlmodular.common.machine.pattern.PatternStacks;
import com.gtlmodular.common.machine.pattern.ui.CatalystPageSync;
import com.gtlmodular.common.machine.pattern.ui.CatalystSlotAccess;
import com.gtlmodular.common.machine.pattern.ui.PatternConfiguratorWidgets;
import com.gtlmodular.common.machine.pattern.ui.PatternArchiveConfigurator;
import com.gtlmodular.common.machine.pattern.ui.PatternCatalystWidgets;
import com.gtlmodular.common.machine.pattern.ui.PatternSlotRightClickOverlay;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.fluid.FluidHelper;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.syncdata.managed.IRef;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.api.gui.MEPatternCatalystUIManager;
import org.gtlcore.gtlcore.common.machine.multiblock.part.PaginationUIManager;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferRecipeHandlerTrait;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class MoltenPatternAssemblyPartMachine extends MEPatternBufferPartMachine
        implements PatternArchiveConfigurator.Host, CatalystSlotAccess {

    public static final int PATTERNS_PER_ROW = 16;
    public static final int ROWS_PER_PAGE = 10;
    public static final int PER_PAGE = PATTERNS_PER_ROW * ROWS_PER_PAGE;
    /** 等级容量决定存档能恢复的槽范围，必须与 UI 每页行数解耦，避免调整布局导致缩容丢样板。 */
    public static final int CAPACITY_ROWS_PER_PAGE = 8;
    public static final int CAPACITY_PER_PAGE = PATTERNS_PER_ROW * CAPACITY_ROWS_PER_PAGE;

    private static final int UI_HEIGHT_EXTRA = -8;

    private static final int MIN_TIER = 2;
    private static final int MAX_TIER = 11;

    /** 父类按容量预建内部槽，限制页数可控制对象数量；既有容量不能随 UI 布局缩减。 */
    public static final int MAX_PAGES = 5;

    public static final int MIN_FOA_MULTIPLIER = 1;
    public static final int MAX_FOA_MULTIPLIER = 30;
    public static final int DEFAULT_FOA_MULTIPLIER = 15;

    public static final int MIN_SCALE = 1;
    public static final int MAX_SCALE = 100;

    /** 由 AE 负责倍增时返回原样板，避免视图改写与上游倍增叠加。 */
    public static boolean PATTERN_REWRITE_DISABLED = true;

    private static final int AUTO_SWITCH_INTERVAL_TICKS = 20;
    private static final int AUTO_SWITCH_GRACE_TICKS = 40;
    private static final int AUTO_SWITCH_BLOCK_TICKS = 200;

    /** 空转宽限期容纳 AE 在途来料，避免刚切换就因尚未到料切走。 */
    private static final int AUTO_SWITCH_STOCKLESS_TICKS = 600;

    public static boolean COLD_SLOT_CACHE_HEAL = true;

    /** 重识别需要扫描配方表，必须限频。 */
    private static final int COLD_SLOT_HEAL_INTERVAL_TICKS = 200;
    /** 限制总尝试量，避免无法识别的样板持续扫描配方表。 */
    private static final int COLD_SLOT_HEAL_BUDGET = 64;

    /** 上游默认名不代表玩家命名，自动命名需要识别它并主动让出玩家已锁定的名字。 */
    public static boolean enableActiveCustomNaming = true;

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(MoltenPatternAssemblyPartMachine.class,
                    MEPatternBufferPartMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    private final int tier;

    private final PaginationUIManager paginationUIManager;

    @DescSynced
    private int activeRecipeTypeIndex = 0;

    /** 模式页游标与主机实际模式分离，避免主机同步覆盖玩家待指定的选择。 */
    @DescSynced
    private int modeCursorIndex = 0;

    private boolean modeCursorTouched = false;

    /** 按注册 ID 持久化绑定，避免类型表重排后绑定到错误模式。 */
    private ResourceLocation boundModeType;

    /** 读档时空类型表可能只是控制器未就绪，不能据此清除有效绑定。 */
    private boolean boundModePendingResolve;

    // 上游兜底名允许被自动命名覆盖。

    /** 按模式注册 ID 保存名字，避免类型表下标或显示语言变化后串名。 */
    private final java.util.Map<String, String> modeNames = new java.util.LinkedHashMap<>();

    private String lockedName = "";

    private PatternNameController patternNameController;

    /** 限制名字表大小，避免坏 NBT 导致无界增长。 */
    private static final int MAX_SAVED_MODE_NAMES = 64;

    /** 限制单条名字长度，避免坏 NBT 生成巨串。 */
    private static final int MAX_SAVED_NAME_LENGTH = 128;

    private int[] slotRecipeType;

    private TickableSubscription autoSwitchSubscription;

    private int formedNudgeTicks = FormedRenderSyncNudge.WINDOW_TICKS;

    private final PatternClientSyncRefresh clientSyncRefresh = new PatternClientSyncRefresh();

    private PatternAutoSwitch autoSwitch;

    private PatternAutoSwitch patternAutoSwitch() {
        if (autoSwitch == null) {
            autoSwitch = new PatternAutoSwitch(new AutoSwitchHost(), AUTO_SWITCH_INTERVAL_TICKS,
                    AUTO_SWITCH_GRACE_TICKS, AUTO_SWITCH_BLOCK_TICKS, AUTO_SWITCH_STOCKLESS_TICKS, true);
        }
        return autoSwitch;
    }

    private final class AutoSwitchHost implements PatternAutoSwitch.Host {

        @Override
        public IRecipeLogicMachine firstRecipeLogicController() { return MoltenPatternAssemblyPartMachine.this.firstRecipeLogicController(); }

        @Override
        public List<GTRecipeType> availableRecipeTypes() { return getAvailableRecipeTypes(); }

        @Override
        public Level level() { return getLevel(); }

        @Override
        public int activeRecipeTypeIndex() { return activeRecipeTypeIndex; }

        @Override
        public void setActiveRecipeTypeIndex(int index) { activeRecipeTypeIndex = index; }

        @Override
        public boolean modeCursorTouched() { return modeCursorTouched; }

        @Override
        public int modeCursorIndex() { return modeCursorIndex; }

        @Override
        public void setModeCursorIndex(int index) { modeCursorIndex = index; }

        @Override
        public void clearObservedModeCaches(int previous, int live) {
            clearRecipeCacheForMode(previous);
            clearRecipeCacheForMode(live);
        }

        @Override
        public int findModeWithWaitingStock(List<GTRecipeType> types, int active, long now) {
            return MoltenPatternAssemblyPartMachine.this.findModeWithWaitingStock(types, active, now);
        }

        @Override
        public int recipeTypeOfSlot(int slot) { return MoltenPatternAssemblyPartMachine.this.recipeTypeOfSlot(slot); }

        @Override
        public boolean hasRecipeCacheInSlot(int slot) { return MoltenPatternAssemblyPartMachine.this.hasRecipeCacheInSlot(slot); }

        @Override
        public boolean hasSlotWaitingStock(int slot) { return MoltenPatternAssemblyPartMachine.this.hasSlotWaitingStock(slot); }

        @Override
        public void logCheck(List<GTRecipeType> types, RecipeLogic logic, int active, int target, long now) {
            logAutoSwitchCheck(types, logic, active, target, now);
        }

        @Override
        public void clearAutoSwitchCaches(int previous, int target) {
            clearRecipeCacheForMode(previous);
            clearRecipeCacheForMode(target);
        }

        @Override
        public void syncCustomNameToMode(int index) { MoltenPatternAssemblyPartMachine.this.syncCustomNameToMode(index); }

        @Override
        public void onAutoSwitched(int previous, int target) {
            GTLModular.LOGGER.info("[gtlmodular] 自动切换配方模式: {} -> {}", previous, target);
            lastAutoSwitchSignature = null;
        }

        @Override
        public boolean blocksPushModeSwitch(RecipeLogic logic) { return false; }

        @Override
        public void clearPushModeSwitchCaches(int slot, int index) {
            if (clearSlotRecipeCache(slot)) {
                GTLModular.LOGGER.debug("[gtlmodular] 锁模式清缓存: 槽 {} 旧配方已作废（锁到模式 {}）@{}",
                        slot + 1, index, getPos());
            }
        }
    }

    private void logAutoSwitchCheck(List<GTRecipeType> types, RecipeLogic logic, int active, int target, long now) {
        String stock = describeAutoSwitchState();
        long idleTicks = patternAutoSwitch().idleTicks(now);
        String signature = active + "|" + target + "|" + logic.getStatus() + "|" + stock + "|" + (idleTicks / 100);
        if (!signature.equals(lastAutoSwitchSignature)) {
            lastAutoSwitchSignature = signature;
            GTLModular.LOGGER.info("[gtlmodular] 自动切模式检查: 当前={}/{} 状态={} 找到目标={} {} "
                            + "空转={}tick（满 {}tick 才允许切走） 当前模式样板={} 其中有料={} 未认配方={}",
                    active, types.size(), logic.getStatus(), target, stock, idleTicks,
                    AUTO_SWITCH_STOCKLESS_TICKS, patternAutoSwitch().activePatterns(), patternAutoSwitch().activeStock(), patternAutoSwitch().activeCold());
        }
    }

    private String lastAutoSwitchSignature;

    private long lastAutoSwitchWarnTick;

    private long coldSlotHealNextTick;
    private final Set<Integer> coldSlotHealTried = new java.util.HashSet<>();
    private final Map<Integer, ItemStack> coldSlotHealPattern = new java.util.HashMap<>();
    private int coldSlotHealWarmed;

    private static final int PATTERN_SYNC_MARK_LOG_LIMIT = 8;
    private int patternSyncMarkLogLeft = PATTERN_SYNC_MARK_LOG_LIMIT;

    private static final int STUCK_LOG_INTERVAL_TICKS = 600;
    private static final int STUCK_WARNED_LIMIT = 64;

    /** 宿主休眠属异常，限频以免每次推料都刷屏。 */
    private static final int SLEPT_HOST_WARN_INTERVAL_TICKS = 600;

    private static final int LAZY_INITIAL_CAPACITY = 32;
    private static final int LAZY_MIN_GROW_STEP = 16;
    private static final int LAZY_RESERVE = 2;
    private static final boolean LAZY_CAPACITY_ENABLED = false;   // 上游样板视图按 maxPatternCount 遍历，窄容量可能越界，因此默认关闭惰性容量。
    private static final int LAZY_CAPACITY_LOG_LIMIT = 4;
    private int lazyCapacityLogCount;

    private int patternCapacity() {
        ItemStackTransfer inventory = getPatternInventory();
        return inventory == null ? maxPatternCount : inventory.getSlots();
    }

    private boolean growCapacityFor(int needed) {
        ItemStackTransfer inventory = getPatternInventory();
        if (inventory == null) {
            return false;
        }
        int cap = Math.min(maxPatternCount, Math.max(1, inventory.getSlots()));
        int want = Math.min(maxPatternCount, Math.max(needed, cap + LAZY_MIN_GROW_STEP));
        if (want <= cap) {
            return false;
        }
        inventory.setSize(want);
        if (lazyCapacityLogCount < LAZY_CAPACITY_LOG_LIMIT) {
            lazyCapacityLogCount++;
            GTLModular.LOGGER.info("[gtlmodular] 样板仓惰性扩容：{} → {}（需求 {}，硬上限 {}）",
                    cap, want, needed, maxPatternCount);
        }
        return true;
    }

    private boolean growCapacityForBatchInsert(int count) {
        return count > 0 && growCapacityFor(count + LAZY_RESERVE);
    }

    private void ensureCapacityForSlot(int slot) {
        if (!LAZY_CAPACITY_ENABLED) {
            return;
        }
        if (slot < 0 || slot >= maxPatternCount) {
            return;
        }
        if (slot >= patternCapacity()) {
            growCapacityFor(slot + 1 + LAZY_RESERVE);
        }
    }

    /** 关闭惰性容量后仍需补回旧档中的窄仓，否则高槽样板可能对 AE 不可见。 */
    private void restoreFullCapacity(String phase) {
        try {
            ItemStackTransfer inventory = getPatternInventory();
            if (inventory == null) {
                return;
            }
            int cap = Math.min(maxPatternCount, Math.max(1, inventory.getSlots()));
            if (cap >= maxPatternCount) {
                return;
            }
            // ItemStackTransfer.setSize 会清空内容，扩容前必须备份并在同一仓对象上恢复。
            net.minecraft.world.item.ItemStack[] saved = new net.minecraft.world.item.ItemStack[cap];
            for (int i = 0; i < cap; i++) {
                try { saved[i] = inventory.getStackInSlot(i).copy(); }
                catch (Throwable ignored) {
                    SwallowedErrors.report("MoltenPatternAssemblyPartMachine#restoreFullCapacity.backupSlot", ignored);
                    saved[i] = net.minecraft.world.item.ItemStack.EMPTY;
                }
            }
            inventory.setSize(maxPatternCount);
            int restored = 0;
            for (int i = 0; i < saved.length; i++) {
                if (saved[i] != null && !saved[i].isEmpty()) {
                    try { inventory.setStackInSlot(i, saved[i]); restored++; }
                    catch (Throwable ignored) {
                        SwallowedErrors.report("MoltenPatternAssemblyPartMachine#restoreFullCapacity.restoreSlot", ignored);
                    }
                }
            }
            GTLModular.LOGGER.info("[gtlmodular] 容量修复时保住样板 {} 张（槽位 0..{}）", restored, cap - 1);
            GTLModular.LOGGER.info("[gtlmodular] 样板仓容量修复（{}）：{} → {}（硬上限；修复惰性落盘残留，恢复高槽样板对 AE 可见）",
                    phase, cap, maxPatternCount);
        } catch (Throwable t) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#restoreFullCapacity", t);
            GTLModular.LOGGER.warn("[gtlmodular] 样板仓容量修复失败（已忽略，按存档容量走）: {}", t.toString());
        }
    }

    /** 容量补齐后延迟到首个服务端 tick 重扫并通知 AE，避免加载顺序留下过期样板表。 */
    private boolean archiveRescanAfterLoad = false;

    private void initLazyCapacity() {
        if (!LAZY_CAPACITY_ENABLED) {
            restoreFullCapacity("构造期");
            return;
        }
        try {
            ItemStackTransfer inventory = getPatternInventory();
            if (inventory == null) {
                return;
            }
            int start = Math.min(LAZY_INITIAL_CAPACITY, maxPatternCount);
            int before = Math.min(maxPatternCount, Math.max(1, inventory.getSlots()));
            if (before > start) {
                inventory.setSize(start);
                GTLModular.LOGGER.info("[gtlmodular] 样板仓惰性起步：容量 {} → {}（硬上限 {}）",
                        before, start, maxPatternCount);
            }
        } catch (Throwable t) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#initLazyCapacity", t);
            GTLModular.LOGGER.warn("[gtlmodular] 样板仓惰性起步失败（已忽略，按父类容量走）: {}", t.toString());
        }
    }

    /** 恢复容量须覆盖最高已用槽，避免旧档中的高槽样板被截断。 */
    private void backfillCapacityAfterLoad() {
        if (!LAZY_CAPACITY_ENABLED) {
            restoreFullCapacity("读档");
            archiveRescanAfterLoad = true;
            return;
        }
        try {
            ItemStackTransfer inventory = getPatternInventory();
            if (inventory == null) {
                return;
            }
            int capBefore = Math.min(maxPatternCount, Math.max(1, inventory.getSlots()));
            int highestUsed = -1;
            for (int i = 0; i < capBefore; i++) {
                ItemStack s = inventory.getStackInSlot(i);
                if (s != null && !s.isEmpty()) {
                    highestUsed = i;
                }
            }
            int wanted = highestUsed + 1 + LAZY_RESERVE;
            if (wanted > capBefore) {
                growCapacityFor(wanted);
            }
            GTLModular.LOGGER.info("[gtlmodular] 样板仓容量回填：读档 容量 {} → {}（最高已用槽 {}，硬上限 {}）",
                    capBefore, patternCapacity(), highestUsed, maxPatternCount);
        } catch (Throwable t) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#backfillCapacityAfterLoad", t);
            GTLModular.LOGGER.warn("[gtlmodular] 样板仓容量回填失败（已忽略，不影响读档）: {}", t.toString());
        }
    }
    private long lastStuckLogTick;
    private final java.util.Set<String> stuckWarned = new java.util.HashSet<>();

    private static final boolean STUCK_WARN_REPEAT_WHEN_TRACKED = true;

    private final java.util.Map<String, Long> stuckWarnedTick = new java.util.HashMap<>();

    private String describeAutoSwitchState() {
        ItemStackTransfer inventory = getPatternInventory();
        if (inventory == null) {
            return "样板库存不可用";
        }
        StringBuilder known = new StringBuilder();
        StringBuilder stock = new StringBuilder();
        int patterns = 0;
        int shownKnown = 0;
        int shownStock = 0;
        int slots = Math.min(inventory.getSlots(), slotCount());
        for (int slot = 0; slot < slots; slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                continue;
            }
            patterns++;
            int type = recipeTypeOfSlot(slot);
            if (type >= 0 && shownKnown < 12) {
                if (shownKnown++ > 0) {
                    known.append(' ');
                }
                known.append(slot + 1).append('=').append(type);
            }
            try {
                var internal = getInternalSlot(slot);
                if (internal != null && shownStock < 12
                        && (!internal.getItemStackInputMap().isEmpty() || !internal.getFluidStackInputMap().isEmpty())) {
                    if (shownStock++ > 0) {
                        stock.append(' ');
                    }
                    stock.append(slot + 1);
                }
            } catch (RuntimeException ignored) {
            }
        }
        return "样板=" + patterns + " 类型已知=[" + known + "] 有料槽=[" + stock + "]";
    }

    /** 手动指定存注册 ID，避免更换控制器或类型表重排后指错模式。 */
    private final Map<Integer, net.minecraft.resources.ResourceLocation> manualSlotTypes = new java.util.HashMap<>();

    @DescSynced
    private int manualAssignSlot = 1;

    @DescSynced
    private String manualAssignTypeId = "";

    @DescSynced
    private int patternScale = 1;

    @DescSynced
    private int patternDivScale = 1;

    /** 保留字段及读写以兼容存档和同步布局；缩放按两侧同倍计算。 */
    @DescSynced
    private boolean isOutputMode = true;

    @DescSynced
    private boolean foaMode = false;

    @DescSynced
    private int foaMultiplier = DEFAULT_FOA_MULTIPLIER;

    private final PatternRewriteCache rewriteCache = new PatternRewriteCache(
            () -> foaMode, () -> foaMultiplier, () -> patternScale, () -> patternDivScale,
            MIN_FOA_MULTIPLIER, MAX_FOA_MULTIPLIER, MIN_SCALE, MAX_SCALE);
    private final PatternProviderRefresh providerRefresh = new PatternProviderRefresh();

    @DescSynced
    private int catalystViewSlot = 1;

    @DescSynced
    private boolean catalystPageOpen = false;

    /** 页面槽控件展开时才建，原版首包会被吞掉；用机器字段把当前查看槽的内容点到点补一次。 */
    @DescSynced
    private String catalystViewPayload = "";

    public String getCatalystViewPayload() {
        return catalystViewPayload;
    }

    transient CatalystAccessConfigurator activeCatalystPage;

    @DescSynced
    private boolean archiveAlignSlots = false;

    @DescSynced
    private boolean archiveIncludeSettings = false;

    @DescSynced
    private boolean archiveIncludeName = false;

    /** 归档写入可能只落到底层仓，保留落地槽记录可避免上游激活缓存未更新时漏掉已有样板。 */
    private final it.unimi.dsi.fastutil.ints.IntOpenHashSet archiveLandedSlots =
            new it.unimi.dsi.fastutil.ints.IntOpenHashSet();

    /** 终端写入会触发逐槽回调，批量窗口仍保留父类记账，只合并本模组的额外失效和通知。 */
    private boolean archiveBulkPatternWrite = false;

    void beginArchiveBulkWrite() {
        archiveBulkPatternWrite = true;
    }

    public MoltenPatternAssemblyPartMachine(IMachineBlockEntity holder, int tier) {
        this(holder, tier, slotsForTier(tier));
    }

    public MoltenPatternAssemblyPartMachine(IMachineBlockEntity holder, int tier, int slots) {
        super(holder, slots, IO.BOTH);
        this.tier = tier;
        int uiWidth = Math.max(PATTERNS_PER_ROW * 18 + 16, 106);
        int capRows = Math.max(1, Math.min(ROWS_PER_PAGE, Math.max(1, slots / PATTERNS_PER_ROW)));
        int perPage = PATTERNS_PER_ROW * capRows;
        int uiHeight = capRows * 18 + 36 + UI_HEIGHT_EXTRA;
        initLazyCapacity();
        this.paginationUIManager = new PaginationUIManager(PATTERNS_PER_ROW, capRows, Math.max(1, slots / perPage), uiWidth, uiHeight,
                this::onPatternChange, i -> i >= 0 && i < cacheRecipe.length && cacheRecipe[i],
                getPatternInventory());
    }

    public int getMachineTier() {
        return tier;
    }

    public static int pagesForTier(int tier) {
        int t = Math.max(MIN_TIER, Math.min(MAX_TIER, tier));
        int pages = (t <= 3) ? (t - 1) : (4 + (t - 4));
        return Math.min(MAX_PAGES, pages);
    }

    public static int slotsForTier(int tier) {
        return pagesForTier(tier) * CAPACITY_PER_PAGE;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        getMainNode().setExposedOnSides(EnumSet.allOf(Direction.class));
        // 节点可能已处于在线状态，不再触发状态变化回调，加载时也需确保样板同步订阅存在。
        updateSubscription();
        this.autoSwitchSubscription = subscribeServerTick(this::autoSwitchTick);
    }

    /** 控制器名单到达可能晚于区块烘焙，接收同步后需安排重绘。 */
    @Override
    public void onSyncChanged(IRef ref, boolean isClient) {
        clientSyncRefresh.onSyncChanged(ref, isClient, () -> getDefinition().getId(), this::getPos,
                () -> getControllers().size(), this::scheduleRenderUpdate);
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        getMainNode().setExposedOnSides(EnumSet.allOf(Direction.class));
    }

    @Override
    public void onMachineRemoved() {
        super.onMachineRemoved();
        getMainNode().destroy();
    }

    // 上游构造期间可能调用覆写入口，延迟创建可避免依赖尚未初始化的机器字段。
    private PatternModeContext modeContext;

    private PatternModeContext modeContext() {
        if (modeContext == null) {
            modeContext = new PatternModeContext(new ModeContextHost());
        }
        return modeContext;
    }

    private final class ModeContextHost implements PatternModeContext.Host {

        @Override
        public List<GTRecipeType> availableRecipeTypes() { return getAvailableRecipeTypes(); }

        @Override
        public int activeRecipeTypeIndex() { return activeRecipeTypeIndex; }

        @Override
        public int modeCursorIndex() { return modeCursorIndex; }

        @Override
        public boolean modeCursorTouched() { return modeCursorTouched; }

        @Override
        public void selectModeCursor(int index) {
            modeCursorIndex = index;
            modeCursorTouched = true;
        }

        @Override
        public void setRecipeType(int index) { MoltenPatternAssemblyPartMachine.this.setRecipeType(index); }

        @Override
        public boolean isRemote() { return MoltenPatternAssemblyPartMachine.this.isRemote(); }

        @Override
        public ResourceLocation boundModeType() { return boundModeType; }

        @Override
        public boolean boundModePendingResolve() { return boundModePendingResolve; }

        @Override
        public void storeBoundModeType(ResourceLocation typeId) {
            boundModeType = typeId;
            markDirty();
        }

        @Override
        public void finishBoundModeResolve() { boundModePendingResolve = false; }

        @Override
        public void rememberBoundModeType() { MoltenPatternAssemblyPartMachine.this.rememberBoundModeType(); }

        @Override
        public Map<Integer, ResourceLocation> manualSlotTypes() { return manualSlotTypes; }

        @Override
        public int manualTypeIndexOf(int slot, List<GTRecipeType> types) { return MoltenPatternAssemblyPartMachine.this.manualTypeIndexOf(slot, types); }

        @Override
        public void prepareSlotRecipeTypes() { ensureSlotRecipeTypeArray(); }

        @Override
        public int slotRecipeTypeCount() { return slotRecipeType.length; }

        @Override
        public int cachedSlotRecipeType(int slot) { return slotRecipeType[slot]; }

        @Override
        public void cacheSlotRecipeType(int slot, int type) { slotRecipeType[slot] = type; }

        @Override
        public int detectRecipeType(int slot, List<GTRecipeType> types) { return MoltenPatternAssemblyPartMachine.this.detectRecipeType(slot, types); }

        @Override
        public int manualAssignmentSlotCount() { return slotCount(); }

        @Override
        public int maxPatternCount() { return maxPatternCount; }

        @Override
        public void invalidateManualAssignment(int slot) {
            if (slotRecipeType != null && slot < slotRecipeType.length) {
                slotRecipeType[slot] = TYPE_NOT_TRIED;
            }
            if (clearSlotRecipeCache(slot)) {
                GTLModular.LOGGER.info("[gtlmodular] 手动指定/清除 槽 {} ⇒ 已作废该槽的 GT 配方缓存，主机将按新模式重搜 @{}",
                        slot + 1, getPos());
            }
        }

        @Override
        public void clearAutoSwitchBlocked() { patternAutoSwitch().clearBlocked(); }

        @Override
        public void refreshManualAssignDisplay() { MoltenPatternAssemblyPartMachine.this.refreshManualAssignDisplay(); }

        @Override
        public void syncCustomNameToMode(int index) { MoltenPatternAssemblyPartMachine.this.syncCustomNameToMode(index); }
    }

    public List<GTRecipeType> getAvailableRecipeTypes() {
        return modeContext().availableRecipeTypes(firstRecipeLogicController());
    }

    private IRecipeLogicMachine firstRecipeLogicController() {
        return PatternModeContext.firstRecipeLogicController(getControllers());
    }

    public int getActiveRecipeTypeIndex() {
        return activeRecipeTypeIndex;
    }

    public void setActiveRecipeTypeIndex(int index) {
        this.activeRecipeTypeIndex = index;
    }

    public GTRecipeType getActiveRecipeType() {
        return modeContext().activeRecipeType();
    }

    public void cycleRecipeType(boolean forward) {
        modeContext().cycleRecipeType(forward);
    }

    public void setRecipeType(int index) {
        modeContext().setRecipeType(index);
    }

    public int getModeCursorIndex() {
        return modeCursorIndex;
    }

    /** 芯片点击先更新客户端游标供即时反馈，服务端同步随后确认权威值。 */
    public void applyModeCursorLocally(int index) {
        if (index >= 0 && index < getAvailableRecipeTypes().size()) {
            this.modeCursorIndex = index;
            this.modeCursorTouched = true;
        }
    }

    private void rememberBoundModeType() {
        modeContext().rememberBoundModeType();
    }

    private void applyBoundModeTypeIfPossible() {
        modeContext().applyBoundModeTypeIfPossible();
    }

    @Override
    protected void onPatternChange(int index) {
        ensureCapacityForSlot(index);
        super.onPatternChange(index);
        refreshSlotPatternMirror(index);   // 批量写入仍须执行父类逐槽记账，额外通知留到批量收尾，避免重入整仓刷新。
        if (archiveBulkPatternWrite) {
            if (slotRecipeType != null && index >= 0 && index < slotRecipeType.length) {
                slotRecipeType[index] = -1;
            }
            return;
        }
        if (slotRecipeType != null && index >= 0 && index < slotRecipeType.length) {
            slotRecipeType[index] = -1;
        }
        invalidateRewriteCache();
        // 上游读样板表使用缓存，内容改变后需置同步标记，AE 才会重新取表。
        markPatternTableStale("界面/槽变化#" + (index + 1));
    }

    /** 内容变化只置标记，由 tick 合并通知，避免批量写入变成逐槽整仓重算。 */
    private void markPatternTableStale(String source) {
        try {
            if (isRemote()) {
                return;
            }
            this.needPatternSync = true;
            if (this.patternSyncMarkLogLeft > 0) {
                this.patternSyncMarkLogLeft--;
                GTLModular.LOGGER.info("[gtlmodular] 样板表标记置位（片D）: @{} {} 来源={} 剩余诊断额度={}",
                        getLevel() == null ? "?" : getLevel().dimension().location(), getPos(), source,
                        this.patternSyncMarkLogLeft);
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#markPatternTableStale", ignored);
        }
    }

    private int slotCount() {
        ItemStackTransfer inventory = getPatternInventory();
        return inventory == null ? 0 : inventory.getSlots();
    }

    private final java.util.Map<Integer, appeng.api.crafting.IPatternDetails> slotPatternMirror = new java.util.HashMap<>();

    private void refreshSlotPatternMirror(int index) {
        try {
            if (index < 0) {
                return;
            }
            net.minecraft.world.item.ItemStack stack = null;
            var inv = getPatternInventory();
            if (inv != null && index < inv.getSlots()) {
                stack = inv.getStackInSlot(index);
            }
            if (stack == null || stack.isEmpty()) {
                slotPatternMirror.remove(index);
                return;
            }
            net.minecraft.world.level.Level lv = getLevel();
            appeng.api.crafting.IPatternDetails decoded = PatternDetailsHelper.decodePattern(stack, lv);
            if (decoded == null) {
                slotPatternMirror.remove(index);
            } else {
                slotPatternMirror.put(index, decoded);
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#refreshSlotPatternMirror", ignored);
        }
    }

    public int slotOwning(com.gregtechceu.gtceu.api.recipe.GTRecipe recipe) {
        if (recipe == null) {
            return -1;
        }
        for (java.util.Map.Entry<Integer, appeng.api.crafting.IPatternDetails> e : slotPatternMirror.entrySet()) {
            try {
                if (PatternRecipeMatching.patternIdentityMatches(recipe, e.getValue())) {
                    return e.getKey();
                }
            } catch (Throwable ignored) {
                SwallowedErrors.report("MoltenPatternAssemblyPartMachine#slotOwning.matchIdentity", ignored);
            }
        }
        return -1;
    }

    public int[] activePatternSlotsForIsolation() {
        try {
            return slotPatternMirror.keySet().stream().mapToInt(Integer::intValue).toArray();
        } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#activePatternSlotsForIsolation", ignored);
            return new int[0];
        }
    }
    /** 默认保留部件自己的外观，便于在成形结构中找到样板总成。 */
    public static boolean BLEND_WITH_CASING = false;

    @Override
    public net.minecraft.world.level.block.state.BlockState getBlockAppearance(
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.BlockAndTintGetter level,
            net.minecraft.core.BlockPos pos,
            net.minecraft.core.Direction side,
            net.minecraft.world.level.block.state.BlockState sourceState,
            net.minecraft.core.BlockPos sourcePos) {
        // 渲染读取控制器集合可能遇到并发修改，失败时保留自身模型，避免渲染线程崩溃。
        return super.getBlockAppearance(state, level, pos, side, sourceState, sourcePos);
    }
    private void ensureSlotRecipeTypeArray() {
        int slots = slotCount();
        if (slotRecipeType == null || slotRecipeType.length != slots) {
            int[] fresh = new int[slots];
            Arrays.fill(fresh, -1);
            slotRecipeType = fresh;
        }
    }

    private static final int TYPE_NOT_TRIED = -1;
    private static final int TYPE_UNKNOWN = -2;

    private int recipeTypeOfSlot(int slot) {
        return modeContext().recipeTypeOfSlot(slot, TYPE_UNKNOWN);
    }

    private int manualTypeIndexOf(int slot, List<GTRecipeType> types) {
        return PatternModeContext.manualTypeIndexOf(slot, types, manualSlotTypes);
    }

    public int getManualAssignSlot() {
        return manualAssignSlot;
    }

    public void setManualAssignSlot(int slot) {
        PatternModeContext.setManualAssignSlot(slot, this::getCatalystSlotCount,
                value -> this.manualAssignSlot = value, this::refreshManualAssignDisplay);
    }

    public String getManualAssignTypeId() {
        return manualAssignTypeId;
    }

    private void refreshManualAssignDisplay() {
        if (isRemote()) {
            return;
        }
        net.minecraft.resources.ResourceLocation id = manualSlotTypes.get(manualAssignSlot - 1);
        this.manualAssignTypeId = id == null ? "" : id.toString();
    }

    public void assignManualType(int slotNumber, boolean clear) {
        modeContext().assignManualType(slotNumber, clear);
    }

    /** 手动指定改变后需清除该槽的上游缓存，否则主机仍可能复用旧模式配方。 */
    private boolean clearSlotRecipeCache(int slot) {
        try {
            if (slot < 0 || slot >= getInternalSlotCount()) {
                return false;
            }
            boolean hadCached = hasRecipeCacheInSlot(slot);
            removeSlotFromGTRecipeCache(slot);
            return hadCached;
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#clearSlotRecipeCache", failure);
            GTLModular.LOGGER.debug("[gtlmodular] 清槽 {} 的 GT 配方缓存失败（已忽略）：{}", slot, failure.toString());
            return false;
        }
    }

    private void clearRecipeCacheForMode(int modeIndex) {
        try {
            if (modeIndex < 0) {
                return;
            }
            ItemStackTransfer inventory = getPatternInventory();
            if (inventory == null) {
                return;
            }
            ensureSlotRecipeTypeArray();
            int slots = Math.min(inventory.getSlots(), slotRecipeType.length);
            int cleared = 0;
            for (int slot = 0; slot < slots; slot++) {
                if (inventory.getStackInSlot(slot).isEmpty()) {
                    continue;
                }
                if (recipeTypeOfSlot(slot) != modeIndex) {
                    continue;
                }
                if (clearSlotRecipeCache(slot)) {
                    cleared++;
                }
            }
            if (cleared > 0) {
                GTLModular.LOGGER.info("[gtlmodular] 换模式清 GT 配方缓存: 模式 {} 的 {} 个槽已作废 @{}",
                        modeIndex, cleared, getPos());
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#clearRecipeCacheForMode", failure);
            GTLModular.LOGGER.debug("[gtlmodular] 清模式 {} 的 GT 配方缓存失败（已忽略）：{}", modeIndex, failure.toString());
        }
    }

    private boolean hasSlotWaitingStock(int slot) {
        return PatternAutoSwitch.hasWaitingStock(() -> getInternalSlot(slot),
                internal -> !internal.getItemStackInputMap().isEmpty(),
                internal -> !internal.getFluidStackInputMap().isEmpty());
    }

    private PatternNameController patternNameController() {
        if (patternNameController == null) {
            patternNameController = new PatternNameController(new NameControllerHost());
        }
        return patternNameController;
    }

    private final class NameControllerHost implements PatternNameController.Host {

        @Override
        public List<GTRecipeType> availableRecipeTypes() { return getAvailableRecipeTypes(); }

        @Override
        public String customName() { return getCustomName(); }

        @Override
        public void setCustomName(String name) { MoltenPatternAssemblyPartMachine.this.setCustomName(name); }

        @Override
        public void markDirty() { MoltenPatternAssemblyPartMachine.this.markDirty(); }

        @Override
        public boolean isRemote() { return MoltenPatternAssemblyPartMachine.this.isRemote(); }

        @Override
        public boolean activeCustomNamingEnabled() { return enableActiveCustomNaming; }

        @Override
        public boolean boundModePendingResolve() { return boundModePendingResolve; }

        @Override
        public boolean modeCursorTouched() { return modeCursorTouched; }

        @Override
        public int modeCursorIndex() { return modeCursorIndex; }

        @Override
        public int activeRecipeTypeIndex() { return activeRecipeTypeIndex; }

        @Override
        public IRecipeLogicMachine firstRecipeLogicController() { return MoltenPatternAssemblyPartMachine.this.firstRecipeLogicController(); }

        @Override
        public int namingModeIndexOf(boolean bound, int hint, List<GTRecipeType> types) {
            return MoltenPatternAssemblyPartMachine.this.namingModeIndexOf(bound, hint, types);
        }

        @Override
        public int currentModeIndexForNaming(int hint) { return MoltenPatternAssemblyPartMachine.this.currentModeIndexForNaming(hint); }

        @Override
        public java.util.Map<String, String> modeNames() { return modeNames; }

        @Override
        public String lockedName() { return lockedName; }

        @Override
        public void setLockedName(String name) { lockedName = name; }

        @Override
        public boolean allowDefaultName(IRecipeLogicMachine controller, List<GTRecipeType> types, int mode) {
            com.gregtechceu.gtceu.api.machine.MetaMachine inserted = findInsertedMachine(controller);
            return inserted == null || insertedSupportsType(inserted, types.get(mode));
        }
    }

    private void syncCustomNameToMode(int modeIndexHint) {
        if (isRemote()) {
            return;
        }
        try {
            patternNameController().syncCustomNameToMode(modeIndexHint);
        } catch (RuntimeException | Error ignored) {
            // 起名失败不能影响机器逻辑。
        }
    }

    public void onPlayerConfirmedCustomName(String name) {
        patternNameController().onPlayerConfirmedCustomName(name);
    }

    private int namingModeIndexOf(boolean bound, int modeIndexHint, List<GTRecipeType> types) {
        return patternNameController().namingModeIndexOf(bound, modeIndexHint, types);
    }

    private int currentModeIndexForNaming(int modeIndexHint) {
        return patternNameController().currentModeIndexForNaming(modeIndexHint);
    }

    private com.gregtechceu.gtceu.api.machine.MetaMachine findInsertedMachine(IRecipeLogicMachine controller) {
        try {
            if (!(controller instanceof com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController mc)) {
                return null;
            }
            var state = mc.getMultiblockState();
            if (state == null || state.cache == null || state.world == null) {
                return null;
            }
            var it = state.cache.longIterator();
            while (it.hasNext()) {
                com.gregtechceu.gtceu.api.machine.MetaMachine candidate = com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(state.world, net.minecraft.core.BlockPos.of(it.nextLong()));
                if (candidate == null || candidate == this) {
                    continue;
                }
                if (candidate instanceof com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart) {
                    continue;
                }
                return candidate;
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#findInsertedMachine", ignored);
        }
        return null;
    }

    private static boolean insertedSupportsType(com.gregtechceu.gtceu.api.machine.MetaMachine machine, GTRecipeType type) {
        if (machine == null || type == null) {
            return false;
        }
        try {
            if (machine instanceof IRecipeLogicMachine logic) {
                for (GTRecipeType t : logic.getRecipeTypes()) {
                    if (t == type) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#insertedSupportsType", ignored);
            // 不能确定塞入机器支持该模式时不参与命名，避免写入错误名称。
        }
        return false;
    }

    private static String insertedName(com.gregtechceu.gtceu.api.machine.MetaMachine machine) {
        try {
            var definition = machine.getDefinition();
            if (definition != null) {
                return definition.asStack().getHoverName().getString();
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#insertedName", ignored);
        }
        return "";
    }

    private boolean isNameTakenByOther(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        IRecipeLogicMachine controller = firstRecipeLogicController();
        if (!(controller instanceof com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine mb)) {
            return false;
        }
        try {
            for (com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart part : mb.getParts()) {
                if (part == this) {
                    continue;
                }
                if (part instanceof org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase other
                        && name.equals(other.getCustomName())) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    public boolean hasManualType(int slot) {
        return manualSlotTypes.containsKey(slot);
    }

    /** 主产物可能相同，识别还需核对输入身份，避免仅按产物误配模式。 */
    private int detectRecipeType(int slot, List<GTRecipeType> types) {
        // 每次识别入口先清空中转配方，避免早退时把上一个槽的配方直供给当前槽。
        this.lastMatchedSlotRecipe = null;
        Level level = getLevel();
        ItemStackTransfer inventory = getPatternInventory();
        if (level == null || inventory == null || slot >= inventory.getSlots()) {
            return -1;
        }
        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return -1;
        }
        PatternRecipeMatching.DecodedPattern pattern;
        try {
            pattern = PatternRecipeMatching.decode(getRealPatternHelper(), stack, level, keepByProduct);
        } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#detectRecipeType.decodePattern", ignored);
            return -1;
        }
        if (pattern == null) {
            return -1;
        }
        // 主机代理表可能包含与模块相同的配方，先查非主机表以避免模块样板被误识别为主机模式。
        int withoutHost = scanTypesForMatch(types, pattern, level, true);
        if (withoutHost != MATCH_NONE) {
            int resolvedFirst = resolveAmbiguousTypeFromSlotCache(slot, types, withoutHost, pattern);
            supplyResolvedRecipeToMachine(slot, resolvedFirst);
            return resolvedFirst;
        }
        int hostTableOnly = scanTypesForMatch(types, pattern, level, false);
        int resolvedSecond = resolveAmbiguousTypeFromSlotCache(slot, types, hostTableOnly, pattern);
        supplyResolvedRecipeToMachine(slot, resolvedSecond);
        return resolvedSecond;
    }

    private static final int MATCH_NONE = -2;

    /** 先清空中转，避免扫描失败时沿用上一次扫描的配方。 */
    private int scanTypesForMatch(List<GTRecipeType> types, PatternRecipeMatching.DecodedPattern pattern, Level level,
                                  boolean skipHostTable) {
        this.lastMatchedSlotRecipe = null;
        return PatternRecipeTypeSearch.scan(types, pattern, level, skipHostTable,
                type -> type == com.gtlmodular.common.data.nanochip.NanochipRecipeTypes.ASSEMBLY_COMPLEX,
                recipe -> this.lastMatchedSlotRecipe = recipe).typeIndex();
    }

    /** 样板本身不保存 GT 配方类型，身份匹配歧义时可用同槽已有配方缓存辅助判断。 */
    public static final boolean AMBIGUITY_USE_SLOT_CACHED_RECIPE = true;

    private static final int AMBIGUOUS_NOTE_LIMIT = 64;

    private final java.util.Set<Integer> ambiguousSlotNotes = new java.util.HashSet<>();

    private int resolveAmbiguousTypeFromSlotCache(int slot, List<GTRecipeType> types, int detected,
                                                  PatternRecipeMatching.DecodedPattern pattern) {
        if (detected != -1) {
            return detected;
        }
        if (!AMBIGUITY_USE_SLOT_CACHED_RECIPE) {
            return detected;
        }
        GTRecipe cached = slotCachedRecipe(slot, pattern);
        if (cached == null) {
            noteAmbiguousSlotType(slot);
            return -1;
        }
        int index = typeIndexOfRecipeType(types, cached.recipeType);
        if (index < 0) {
            this.lastMatchedSlotRecipe = cached;
            noteAmbiguousSlotType(slot);
            return -1;
        }
        this.lastMatchedSlotRecipe = cached;
        return index;
    }

    private void noteAmbiguousSlotType(int slot) {
        try {
            if (ambiguousSlotNotes.size() >= AMBIGUOUS_NOTE_LIMIT) {
                return;
            }
            if (!ambiguousSlotNotes.add(slot)) {
                return;
            }
            GTLModular.LOGGER.warn("[gtlmodular] 【样板标识歧义·可见告警】槽位[{}] 的样板身份同时命中"
                            + "**多种**配方表，且该槽的配方缓存也定位不出类型 ⇒ 本次类型保持 -1"
                            + "（该槽不参与按模式收窄、也不会自动切主机模式）。"
                            + "若这台机器本该跑它：Shift+右键该样板槽手动指定正确标识（模式页也能填槽号指定）。"
                            + "本行只读、不改任何判定 @{}",
                    slot + 1, getPos());
        } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#noteAmbiguousSlotType", ignored);
        }
    }

    public static boolean DIRECT_SLOT_RECIPE_SUPPLY = true;

    private GTRecipe lastMatchedSlotRecipe;

    /** 同槽缓存可能已确定配方身份，直供不必总以类型下标已知为前提。 */
    public static final boolean DIRECT_SLOT_RECIPE_SUPPLY_REQUIRES_TYPE_INDEX = false;

    /** 槽缓存可能已确定配方身份，即使类型下标未知也可按本机开关直供。 */
    private void supplyResolvedRecipeToMachine(int slot, int detectedTypeIndex) {
        if (!DIRECT_SLOT_RECIPE_SUPPLY) {
            return;
        }
        if (DIRECT_SLOT_RECIPE_SUPPLY_REQUIRES_TYPE_INDEX && detectedTypeIndex < 0) {
            return;
        }
        GTRecipe recipe = this.lastMatchedSlotRecipe;
        if (recipe == null || isRemote()) {
            return;
        }
        PatternRecipeTypeSearch.supplyIfAbsent(slot, recipe, this::getMETrait);
    }

    public static final boolean LOCK_TYPE_FROM_SLOT_RECIPE_CACHE = true;

    /** 只使用同槽且身份仍匹配的缓存配方，避免样板更换后的陈旧记录参与模式判断。 */
    private GTRecipe slotCachedRecipe(int slot, PatternRecipeMatching.DecodedPattern patternOrNull) {
        try {
            if (slot < 0) {
                return null;
            }
            it.unimi.dsi.fastutil.ints.Int2ReferenceMap<it.unimi.dsi.fastutil.objects.ObjectSet<GTRecipe>> cache =
                    getMETrait().getSlot2RecipesCache();
            if (cache == null) {
                return null;
            }
            it.unimi.dsi.fastutil.objects.ObjectSet<GTRecipe> cached = cache.get(slot);
            if (cached == null || cached.isEmpty()) {
                return null;
            }
            GTRecipe unique = null;
            for (GTRecipe recipe : cached) {
                if (recipe == null || recipe.recipeType == null) {
                    continue;
                }
                if (patternOrNull != null && !PatternRecipeMatching.patternIdentityMatches(recipe, patternOrNull)) {
                    continue;   // 缓存与当前样板身份不符，不能用来确定类型。
                }
                if (unique == null) {
                    unique = recipe;
                } else if (unique.recipeType != recipe.recipeType) {
                    return null;   // 同槽缓存存在多种类型时无法确定目标，不能猜测。
                }
            }
            return unique;
        } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#slotCachedRecipe", ignored);
            return null;
        }
    }

    private static int typeIndexOfRecipeType(List<GTRecipeType> types, GTRecipeType recipeType) {
        if (types == null || recipeType == null) {
            return -1;
        }
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i) == recipeType) {
                return i;
            }
        }
        return -1;
    }

    /** 只切类型仍可能复用 lastRecipe，必须同时重置逻辑才能按新模式搜索。 */
    private void lockRecipeTypeForSlot(int slot) {
        List<GTRecipeType> types = getAvailableRecipeTypes();
        if (types.size() <= 1) {
            return;
        }
        int index = recipeTypeOfSlot(slot);
        if (index < 0 && LOCK_TYPE_FROM_SLOT_RECIPE_CACHE) {
            GTRecipe cachedForSlot = slotCachedRecipe(slot, null);
            if (cachedForSlot != null) {
                index = typeIndexOfRecipeType(types, cachedForSlot.recipeType);
                if (index >= 0) {
                    GTLModular.LOGGER.debug("[gtlmodular] 锁模式【②按槽缓存反查】槽 {} 识别不出类型，"
                                    + "但该槽已解出的配方属于『{}』⇒ 锁到下标 {} @{}",
                            slot + 1, PatternModeNaming.tableNameOf(cachedForSlot.recipeType), index, getPos());
                }
            }
        }
        patternAutoSwitch().lockRecipeTypeForSlot(slot, index, types);
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        // AE 推回的可能是改写实例，父类按原样板映射槽位，需先反查。
        IPatternDetails original = rewriteCache.originalPattern(patternDetails);
        boolean aePushedOriginal = original == patternDetails;
        int mul = effectiveMul();
        int div = effectiveDiv();
        Level pushLevel = getLevel();
        boolean scalingApplies = aePushedOriginal && isRewriteEnabled() && inputHolder != null && (mul != 1 || div != 1);

        // 网络可用量可能受合成 CPU 预留影响，此处只作诊断，不能凭这个读数拒收样板。
        boolean stockShort = false;
        String shortName = "";
        long shortNeed = 0;
        long shortHave = 0;
        appeng.api.stacks.AEKey shortKey = null;
        try {
            var stockGrid = getMainNode().getGrid();
            if (stockGrid != null && inputHolder != null) {
                KeyCounter avail = stockGrid.getStorageService().getInventory().getAvailableStacks();
                outer:
                for (KeyCounter counter : inputHolder) {
                    if (counter == null) {
                        continue;
                    }
                    for (var entry : counter) {
                        // 库存诊断按本次实际推入量比较，避免按未执行的倍率误报缺料。
                        long need = entry.getLongValue();
                        long have = avail.get(entry.getKey());
                        if (have < need) {
                            stockShort = true;
                            shortNeed = need;
                            shortHave = have;
                            shortKey = entry.getKey();
                            try {
                                shortName = "『" + entry.getKey().getDisplayName().getString() + "』("
                                        + entry.getKey().getId() + ")";
                            } catch (RuntimeException ex) {
                                shortName = entry.getKey().getId().toString();
                            }
                            break outer;
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        if (stockShort && pushLevel != null) {
            long nowTick = pushLevel.getGameTime();
            long inFlight = 0;
            boolean aeRequesting = false;
            boolean aeRequestingAny = false;
            try {
                var stockGrid2 = getMainNode().getGrid();
                if (stockGrid2 != null && shortKey != null) {
                    inFlight = stockGrid2.getCraftingService().getRequestedAmount(shortKey);
                    aeRequesting = stockGrid2.getCraftingService().isRequesting(shortKey);
                    // 在途量可能短暂为零，还需查询全局任务状态，避免把正常空窗期当成死锁。
                    aeRequestingAny = stockGrid2.getCraftingService().isRequestingAny();
                }
            } catch (RuntimeException ignored) {
                // 查询失败时不伪造在途量或任务追踪读数。
            }
            boolean deadlock = shortHave <= 0 && inFlight <= 0;
            String stuckId = shortKey == null ? "?" : String.valueOf(shortKey.getId());
            // 任务仍在追踪时允许限频复报，避免首条诊断永久遮住后续状态。
            boolean trueDeadlock = shortHave <= 0 && inFlight <= 0 && aeRequestingAny;
            boolean firstTimeForId = stuckWarned.size() < STUCK_WARNED_LIMIT && stuckWarned.add(stuckId);
            boolean repeatAllowed = STUCK_WARN_REPEAT_WHEN_TRACKED && !trueDeadlock
                    && nowTick - stuckWarnedTick.getOrDefault(stuckId, Long.MIN_VALUE) >= STUCK_LOG_INTERVAL_TICKS;
            if (nowTick - lastStuckLogTick >= STUCK_LOG_INTERVAL_TICKS
                    && (!(shortHave <= 0 && inFlight <= 0) || firstTimeForId || repeatAllowed)) {
                lastStuckLogTick = nowTick;
                stuckWarnedTick.put(stuckId, nowTick);
                if (shortHave > 0) {
                    GTLModular.LOGGER.info("[gtlmodular] 提示：{} 存量不足（网络可用={}，在途={}，AE追踪本件={}，AE全局有任务={}，"
                                    + "本次需要={}）@{}（本条 30 秒最多一条）",
                            shortName, shortHave, inFlight, aeRequesting, aeRequestingAny, shortNeed, getPos());
                } else if (inFlight > 0) {
                    GTLModular.LOGGER.info("[gtlmodular] 提示：{} 还没进网络（可用=0，**在合成中={}**，AE追踪本件={}，"
                                    + "本次需要={}）@{} —— 上游还在做，等它就好（本条 30 秒最多一条）",
                            shortName, inFlight, aeRequesting, shortNeed, getPos());
                } else if (aeRequesting || trueDeadlock) {
                    GTLModular.LOGGER.info("[gtlmodular] 提示：{} **料暂时取不到：网络可用=0、在途=0，但 AE 侧仍有合成任务**"
                                    + "（AE追踪本件={}，AE全局有任务={}，本次需要={}）@{} —— 这是「料已交付、还没被消费/产出」的"
                                    + "**空窗期**：请等下一轮 `AE 推送样板`（对照 `累计推来` 看是不是刚被吃光），"
                                    + "**不要**「全部取消」（那对空窗期没有帮助，只会把已排好的任务丢掉）。"
                                    + "（本条 30 秒最多一条）",
                            shortName, aeRequesting, aeRequestingAny, shortNeed, getPos());
                } else {
                    GTLModular.LOGGER.warn("[gtlmodular] 提示：{} **被占用，且整个 AE 当前没有任何手工合成任务**"
                                    + "（网络可用=0，在途=0，AE追踪本件={}，AE全局有任务={}，本次需要={}）@{} —— "
                                    + "这是真占用：料很可能卡在本机内部槽里（推来后没被消费掉）。"
                                    + "可先在 AE 终端「全部取消」再重新下单；若反复出现，请把本行与同一时刻的 "
                                    + "`AE 推送样板` / `累计推来` / `独立线程` 三行一起留档。（每个物品只提醒一次）",
                            shortName, aeRequesting, aeRequestingAny, shortNeed, getPos());
                }
            }
        }

        // AE 声明的推入量必须与机器消费量对应，不能在机器侧再次放大 inputHolder。
        final boolean machineSideScaled = false;
        if (scalingApplies && !this.machineScaleDisabledWarned) {
            this.machineScaleDisabledWarned = true;
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成 @{}：AE 推送的是【原样板】而机器侧不再二次放大 "
                            + "⇒ 本次按 AE 声明量执行，倍率/神锻未在机器侧生效（有效缩放[{}]）。"
                            + "这是与 AE 一致的回落，不是故障。",
                    getPos(), describeEffectiveScale());
        }
        boolean logThisPush = false;
        if (GTLModularConfig.isPatternDebugLogsEnabled()
                && pushLevel != null && pushLevel.getGameTime() - lastPushLogTick >= 20) {
            lastPushLogTick = pushLevel.getGameTime();
            logThisPush = true;
            long pushed = 0;
            if (inputHolder != null) {
                for (KeyCounter c : inputHolder) {
                    if (c != null) {
                        for (var entry : c) {
                            pushed += entry.getLongValue();
                        }
                    }
                }
            }
            StringBuilder detail = new StringBuilder();
            int shown = 0;
            if (inputHolder != null) {
                for (KeyCounter c : inputHolder) {
                    if (c == null) {
                        continue;
                    }
                    for (var entry : c) {
                        if (shown++ >= 5) {
                            detail.append(" …");
                            break;
                        }
                        detail.append(' ').append(entry.getKey().getId()).append('=').append(entry.getLongValue());
                    }
                    if (shown > 5) {
                        break;
                    }
                }
            }
            // 累计值先计入本次推送再打印，才能与本次处理后的读数对应。
            pushedToMachineTotal = pushedToMachineTotal > Long.MAX_VALUE - pushed
                    ? Long.MAX_VALUE : pushedToMachineTotal + pushed;
            long pushedFluid = 0L;
            if (inputHolder != null) {
                for (KeyCounter c : inputHolder) {
                    if (c == null) {
                        continue;
                    }
                    for (var entry : c) {
                        if (entry.getKey() instanceof appeng.api.stacks.AEFluidKey) {
                            pushedFluid = pushedFluid > Long.MAX_VALUE - entry.getLongValue()
                                    ? Long.MAX_VALUE : pushedFluid + entry.getLongValue();
                        }
                    }
                }
            }
            pushedToMachineFluidTotal = pushedToMachineFluidTotal > Long.MAX_VALUE - pushedFluid
                    ? Long.MAX_VALUE : pushedToMachineFluidTotal + pushedFluid;
            GTLModular.LOGGER.info("[gtlmodular] AE 推送样板 @{}: 是否缩放样板={} 机器侧放大={} 输入总量={} 有效缩放[{}] 累计推来[物品={}；流体={}] 明细:{}",
                    getPos(), !aePushedOriginal, machineSideScaled, pushed, describeEffectiveScale(),
                    pushedToMachineTotal - pushedToMachineFluidTotal, pushedToMachineFluidTotal, detail);
        }
        Integer slot = getSlotIndexForPattern(original);
        if (slot != null && slot >= 0) {
            lockRecipeTypeForSlot(slot);
        }
        boolean pushedOk = super.pushPattern(original, inputHolder);
        if (pushedOk) {
            wakeHostRecipeLogic();
        }
        if (logThisPush && slot != null && slot >= 0) {
            try {
                var internal = getInternalSlot(slot);
                if (internal == null) {
                    GTLModular.LOGGER.info("[gtlmodular] 推送后内部槽[{}]: 取不到（null） 返回值={}", slot + 1, pushedOk);
                } else {
                    GTLModular.LOGGER.info("[gtlmodular] 推送后内部槽[{}]: 返回值={} 物品条目={} 流体条目={}",
                            slot + 1, pushedOk, internal.getItemStackInputMap().size(), internal.getFluidStackInputMap().size());
                }
            } catch (RuntimeException e) {
                GTLModular.LOGGER.info("[gtlmodular] 内部槽读取失败: {}", e.toString());
            }
        }
        if (logThisPush) {
            try {
                var grid = getMainNode().getGrid();
                if (grid != null) {
                    KeyCounter stock = grid.getStorageService().getInventory().getAvailableStacks();
                    StringBuilder cmp = new StringBuilder();
                    int n = 0;
                    if (inputHolder != null) {
                        for (KeyCounter c : inputHolder) {
                            if (c == null) {
                                continue;
                            }
                            for (var e : c) {
                                if (n++ >= 5) {
                                    cmp.append(" …");
                                    break;
                                }
                                cmp.append(' ').append(e.getKey().getId())
                                        .append(" 需要=").append(e.getLongValue())
                                        .append(" 网络可用=").append(stock.get(e.getKey()));
                            }
                            if (n > 5) {
                                break;
                            }
                        }
                    }
                    GTLModular.LOGGER.info("[gtlmodular] 网络存量对比:{}", cmp);
                }
            } catch (RuntimeException e) {
                GTLModular.LOGGER.info("[gtlmodular] 网络存量查询失败: {}", e.toString());
            }
        }
        return pushedOk;
    }

    /**
     * 宿主控制器休眠（方块状态 server_tick=false）时不会自己醒来。料推进内部缓冲后若无人唤醒，
     * 宿主就永远不来取料、产物也交不出去，只能靠拆放或重载（onMachineLoad 里才会重建 tick 订阅）救回。
     * 惰性猪咪早已这么做，这里补齐同样的唤醒，一次覆盖 MV~UIV 与超级共十一台。
     */
    private void wakeHostRecipeLogic() {
        try {
            boolean sleptHost = false;
            for (com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController controller : getControllers()) {
                if (!(controller instanceof IRecipeLogicMachine logicMachine)) {
                    continue;
                }
                com.gregtechceu.gtceu.api.machine.MetaMachine meta = controller.self();
                if (meta != null && Boolean.FALSE.equals(meta.getBlockState()
                        .getValue(com.gregtechceu.gtceu.api.block.BlockProperties.SERVER_TICK))) {
                    sleptHost = true;
                }
                RecipeLogic logic = logicMachine.getRecipeLogic();
                if (logic != null) {
                    logic.updateTickSubscription();
                }
            }
            if (sleptHost && hostSleepWarnAllowed()) {
                GTLModular.LOGGER.info("[gtlmodular] 推料时宿主控制器正处于休眠（server_tick=false），已主动唤醒 @{}；"
                        + "出现本行说明唤醒链被绕过，请留档。", getPos());
            }
        } catch (RuntimeException ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#wakeHostRecipeLogic", ignored);
            // 唤醒失败时保留机器状态，交由主机自行轮询。
        }
    }

    private boolean hostSleepWarnAllowed() {
        long now = getLevel() == null ? 0L : getLevel().getGameTime();
        if (now - lastSleptHostWarnTick < SLEPT_HOST_WARN_INTERVAL_TICKS) {
            return false;
        }
        lastSleptHostWarnTick = now;
        return true;
    }

    private void autoSwitchTick() {
        try {
            autoSwitchTickInner();
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#autoSwitchTick", failure);
            // 维护异常不能中断 tick，同类日志限频以免刷屏。
            long nowMs = System.currentTimeMillis();
            if (nowMs - lastAutoSwitchWarnTick >= 30_000L) {
                lastAutoSwitchWarnTick = nowMs;
                GTLModular.LOGGER.warn("[gtlmodular] 自动切模式 tick 异常（已吞掉，不影响机器本身）：pos={} {}",
                        getPos(), failure.toString());
            }
        }
    }

    private void autoSwitchTickInner() {
        if (isRemote()) {
            return;
        }
        applyBoundModeTypeIfPossible();
        if (formedNudgeTicks > 0) {
            formedNudgeTicks = FormedRenderSyncNudge.tick(this, formedNudgeTicks, "猪咪");
        }
        if (needPatternSync) {
            needPatternSync = false;
            refreshPatternsNow();
        }
        if (archiveRescanAfterLoad) {
            archiveRescanAfterLoad = false;
            try {
                afterArchiveBulkChange();
                GTLModular.LOGGER.info("[gtlmodular] 读档后补一次样板表重扫 + AE 通知（容量修复收尾）pos={}", getPos());
            } catch (Throwable failure) {
                SwallowedErrors.report("MoltenPatternAssemblyPartMachine#autoSwitchTickInner.archiveRescanAfterLoad", failure);
                GTLModular.LOGGER.warn("[gtlmodular] 读档后样板表重扫失败（已忽略，下一 tick 由既有通道兜住）pos={} 原因={}",
                        getPos(), failure.toString());
            }
        }
        staleSlotCache.drain(this::clearSlotRecipeCache, this::getPos);
        syncCustomNameToMode(activeRecipeTypeIndex);
        patternAutoSwitch().tick();
    }

    private int findModeWithWaitingStock(List<GTRecipeType> types, int active, long now) {
        ItemStackTransfer inventory = getPatternInventory();
        if (inventory == null) {
            return -1;
        }
        ensureSlotRecipeTypeArray();
        int slots = Math.min(inventory.getSlots(), slotRecipeType.length);
        return patternAutoSwitch().findModeWithWaitingStock(types, active, now, slots,
                slot -> inventory.getStackInSlot(slot).isEmpty());
    }

    @Override
    protected MEPatternBufferRecipeHandlerTrait createRecipeHandler(IO io) {
        return new IdentityGatedRecipeHandler((MEPatternBufferPartMachine) this, io);
    }

    /** 上游处理器是绑定外层实例的内部类，子类处理器需保留对应外层关系。 */
    static final class IdentityGatedRecipeHandler extends MEPatternBufferRecipeHandlerTrait {

        IdentityGatedRecipeHandler(MEPatternBufferPartMachine machine, IO io) {
            super(machine, io);
        }

        @Override
        protected MEItemInputHandlerBase createMEItemHandler(IO io) {
            return new GatedItemHandler((MEPatternBufferPartMachine) getMachine(), io);
        }

        @Override
        protected MEFluidHandlerBase createMEFluidHandler(IO io) {
            return new GatedFluidHandler((MEPatternBufferPartMachine) getMachine(), io);
        }

        static final class GatedItemHandler extends MEItemInputHandlerBase {

            private final MoltenPatternAssemblyPartMachine machine;

            GatedItemHandler(MEPatternBufferPartMachine machine, IO io) {
                super(machine, io);
                this.machine = (MoltenPatternAssemblyPartMachine) machine;
            }

            @Override
            public boolean meHandleRecipeInner(GTRecipe recipe, Object2LongMap<Ingredient> left,
                                               boolean simulate, int slot) {
                if (!machine.slotAllowsRecipe(slot, recipe)) {
                    machine.logGateReject(slot, recipe, "物品");
                    return false;
                }
                return super.meHandleRecipeInner(recipe, left, simulate, slot);
            }
        }

        static final class GatedFluidHandler extends MEFluidHandlerBase {

            private final MoltenPatternAssemblyPartMachine machine;

            GatedFluidHandler(MEPatternBufferPartMachine machine, IO io) {
                super(machine, io);
                this.machine = (MoltenPatternAssemblyPartMachine) machine;
            }

            @Override
            public boolean meHandleRecipeInner(GTRecipe recipe, Object2LongMap<FluidIngredient> left,
                                               boolean simulate, int slot) {
                if (!machine.slotAllowsRecipe(slot, recipe)) {
                    machine.logGateReject(slot, recipe, "流体");
                    return false;
                }
                return super.meHandleRecipeInner(recipe, left, simulate, slot);
            }
        }
    }

    /** 拒绝发生在扣料入口，限频日志便于定位握着料却不工作的原因。 */
    void logGateReject(int slot, GTRecipe recipe, String side) {
        long now = getLevel() == null ? 0 : getLevel().getGameTime();
        if (now - lastGateRejectLogTick < 40) {
            return;
        }
        lastGateRejectLogTick = now;
        PatternIdentityGate.logReject(slot, recipe, side, this::getAvailableRecipeTypes,
                () -> recipeTypeOfSlot(slot));
        noteIfHostTableMisidentified(slot, recipe);
    }

    /** 主机表可能包含代理配方，需在拒绝后留下有界诊断供排查误识别。 */
    private void noteIfHostTableMisidentified(int slot, GTRecipe recipe) {
        PatternIdentityGate.noteIfHostTableMisidentified(slot, recipe, this::getAvailableRecipeTypes,
                () -> recipeTypeOfSlot(slot),
                type -> type == com.gtlmodular.common.data.nanochip.NanochipRecipeTypes.ASSEMBLY_COMPLEX,
                hostTableMisidentifyNotes, MISIDENTIFY_NOTE_LIMIT, this::getPos);
    }

    private final PatternStaleSlotCache staleSlotCache = new PatternStaleSlotCache();

    /** 槽读取延后到类型判断之后，避免未知类型也触发样板解码。 */
    boolean slotAllowsRecipe(int slot, GTRecipe recipe) {
        boolean allowed = slotIdentityAllows(slot, recipe);
        if (!allowed) {
            staleSlotCache.noteRejected(slot, recipe, getMETrait().getSlot2RecipesCache());
        }
        return allowed;
    }

    private boolean slotIdentityAllows(int slot, GTRecipe recipe) {
        return PatternIdentityGate.allows(slot, recipe, GTLModularConfig::isPatternIdentityGateEnabled,
                () -> recipeTypeOfSlot(slot), this::getAvailableRecipeTypes, () -> {
                    ItemStackTransfer inventory = getPatternInventory();
                    if (inventory == null || slot >= inventory.getSlots()) {
                        return ItemStack.EMPTY;
                    }
                    return inventory.getStackInSlot(slot);
                }, this::getLevel, (stack, level) -> PatternRecipeMatching.decode(getRealPatternHelper(),
                        stack, level, keepByProduct));
    }

    private String lastQuerySignature;

    private long lastPushLogTick = -1000;

    private long pushedToMachineTotal;

    private long pushedToMachineFluidTotal;

    public long[] gtlmodular$pushedTotals() {
        return new long[] { pushedToMachineTotal, pushedToMachineFluidTotal };
    }

    private boolean machineScaleDisabledWarned;

    private long lastGateRejectLogTick = -1000;

    private long lastSleptHostWarnTick = -1000;

    private final java.util.Set<String> hostTableMisidentifyNotes = new java.util.HashSet<>();

    private static final int MISIDENTIFY_NOTE_LIMIT = 64;

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        if (PATTERN_REWRITE_DISABLED) {
            return super.getAvailablePatterns();
        }
        return rewriteCache.getAvailablePatterns(super::getAvailablePatterns,
                this::invalidateRewriteCache, this::logUnrewrittenPatterns, this::rebuildRewriteCache);
    }

    private void logUnrewrittenPatterns(List<IPatternDetails> base) {
        String sig = base.size() + "|" + slotCount() + "|" + rewriteCache.isRewriteEnabled() + "|" + patternScale + "|"
                + patternDivScale + "|" + foaMode + "|" + isOutputMode;
        if (!sig.equals(lastQuerySignature)) {
            lastQuerySignature = sig;
            int patterns = 0;
            ItemStackTransfer inv = getPatternInventory();
            if (inv != null) {
                for (int i = 0; i < inv.getSlots(); i++) {
                    if (!inv.getStackInSlot(i).isEmpty()) {
                        patterns++;
                    }
                }
            }
            if (base.size() > 0 || patterns > 0) {
                GTLModular.LOGGER.info("[gtlmodular] 取样板(未改写): @{} {} 原始={} 库存槽={} 仓内样板={} 开关={} 倍率={} 除率={} 神锻={}({}) 输出模式={}",
                        getLevel() == null ? "?" : getLevel().dimension().location(), getPos(), base.size(),
                        slotCount(), patterns, rewriteCache.isRewriteEnabled(), patternScale, patternDivScale, foaMode,
                        foaMultiplier, isOutputMode);
            }
        }
    }

    private boolean isRewriteEnabled() {
        return rewriteCache.isRewriteEnabled();
    }

    private int effectiveMul() {
        return rewriteCache.effectiveMul();
    }

    private int effectiveDiv() {
        return rewriteCache.effectiveDiv();
    }

    private String describeEffectiveScale() {
        return rewriteCache.describeEffectiveScale();
    }

    /** 改写缓存只在取样板时重建，设置变化后还需通知 AE 主动重取。 */
    public void invalidateRewriteCache() {
        rewriteCache.invalidate();
        notifyPatternsChanged();
    }

    /** 上游批量写入完成后补一次样板表失效，避免 AE 继续使用旧表。 */
    @Override
    public boolean pasteFromTag(net.minecraft.nbt.CompoundTag tag) {
        boolean ok = super.pasteFromTag(tag);
        if (ok) {
            markPatternTableStale("批量粘贴pasteFromTag");
        }
        return ok;
    }

    /** 剪切清仓后补一次样板表失效，避免 AE 仍把已移走的样板当作可用。 */
    @Override
    public CompoundTag cutToTag(CompoundTag tag) {
        CompoundTag result = super.cutToTag(tag);
        markPatternTableStale("批量剪切cutToTag");
        return result;
    }

    /** 批量归档写入后合并失效通知，避免每张样板都触发一次整仓刷新。 */
    @Override
    public void copyFromTag(net.minecraft.nbt.CompoundTag tag, net.minecraft.server.level.ServerPlayer player) {
        super.copyFromTag(tag, player);
        markPatternTableStale("批量覆盖copyFromTag");
    }

    private void notifyPatternsChanged() {
        refreshPatternsNow();
    }

    private void refreshPatternsNow() {
        if (isRemote()) {
            return;
        }
        try {
            providerRefresh.refresh(() -> {
                reCalculatePatternSlotMap();
                return getMainNode();
            });
        } catch (RuntimeException ignored) {
            // 网络暂不可用时缓存仍已失效，下次读取会重建。
        }
    }

    private void rebuildRewriteCache(List<IPatternDetails> base) {
        int changedCount = rewriteCache.rebuild(base, this::rewritePattern);
        GTLModular.LOGGER.info("[gtlmodular] 缩放缓存重建 @{}: 样板={} 已改写={} 有效缩放[{}] 倍率={} 除率={} 神锻={}({})",
                getPos(), base.size(), changedCount, describeEffectiveScale(), patternScale, patternDivScale,
                foaMode, foaMultiplier);
    }

    private IPatternDetails rewritePattern(IPatternDetails details) {
        return rewriteCache.rewritePattern(details, this::getLevel);
    }

    public int getPatternScale() {
        return patternScale;
    }

    public void setPatternScale(int scale) {
        int next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        if (next != this.patternScale) {
            GTLModular.LOGGER.info("[gtlmodular] 缩放设置: 倍率 {} -> {}（{}）",
                    this.patternScale, next, isRemote() ? "客户端" : "服务端");
        }
        this.patternScale = next;
        invalidateRewriteCache();
    }

    public int getPatternDivScale() {
        return patternDivScale;
    }

    public void setPatternDivScale(int scale) {
        int next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        if (next != this.patternDivScale) {
            GTLModular.LOGGER.info("[gtlmodular] 缩放设置: 除率 {} -> {}（{}）",
                    this.patternDivScale, next, isRemote() ? "客户端" : "服务端");
        }
        this.patternDivScale = next;
        invalidateRewriteCache();
    }

    public boolean isOutputMode() {
        return isOutputMode;
    }

    /** @deprecated 两侧同倍后该读口只为兼容存档和同步布局保留。 */
    @Deprecated
    public void toggleOutputMode() {
        this.isOutputMode = !isOutputMode;
        invalidateRewriteCache();
        GTLModular.LOGGER.info("[gtlmodular] 缩放设置: 输出模式={}（{}，已废弃：不再影响缩放）",
                isOutputMode, isRemote() ? "客户端" : "服务端");
    }

    public boolean isFoaModeEnabled() {
        return foaMode;
    }

    public void setFoaModeEnabled(boolean enabled) {
        if (enabled != this.foaMode) {
            GTLModular.LOGGER.info("[gtlmodular] 缩放设置: 神锻模式={}（{}）", enabled, isRemote() ? "客户端" : "服务端");
        }
        this.foaMode = enabled;
        invalidateRewriteCache();
    }

    public int getFoaPatternOutputMultiplier() {
        return foaMultiplier;
    }

    public void setFoaPatternOutputMultiplier(int multiplier) {
        this.foaMultiplier = Math.max(MIN_FOA_MULTIPLIER, Math.min(MAX_FOA_MULTIPLIER, multiplier));
        invalidateRewriteCache();
    }

    public int getCatalystViewSlot() {
        return catalystViewSlot;
    }

    public void setCatalystViewSlot(int slot) {
        this.catalystViewSlot = Math.max(1, Math.min(getCatalystSlotCount(), slot));
    }

    public boolean isCatalystPageOpen() {
        return catalystPageOpen;
    }

    public void setCatalystPageOpen(boolean open) {
        this.catalystPageOpen = open;
    }

    public int getCatalystSlotCount() {
        return Math.max(1, slotCount());
    }

    // ---- CatalystSlotAccess：催化剂页只认槽号，真仓就是逐槽数组 ----

    @Override
    public IItemTransfer catalystItemTransfer(int slot) {
        return slot >= 0 && slot < catalystItems.length ? catalystItems[slot] : null;
    }

    @Override
    public FluidTransferList catalystFluidTransfer(int slot) {
        return slot >= 0 && slot < catalystFluids.length ? catalystFluids[slot] : null;
    }

    @Override
    public void onCatalystItemChanged(int slot) {
        if (slot < 0 || slot >= catalystItems.length) {
            return;
        }
        // 代理直接写仓不会触发上游注册的内容监听；补一次，让催化剂表和配方缓存跟着重算。
        catalystItems[slot].onContentsChanged();
        removeSlotFromGTRecipeCache(slot);
    }

    @Override
    public void onCatalystFluidChanged(int slot) {
        if (slot < 0 || slot >= catalystFluids.length) {
            return;
        }
        catalystFluids[slot].onContentsChanged();
        removeSlotFromGTRecipeCache(slot);
    }

    @Override
    public int catalystCacheCount(int slot) {
        if (slot < 0 || slot >= cacheRecipeCount.length) {
            return DEFAULT_CACHE_RECIPE_COUNT;
        }
        return Math.max(1, (int) cacheRecipeCount[slot]);
    }

    @Override
    public void onCatalystCacheCountChanged(int slot, int value) {
        if (slot < 0 || slot >= cacheRecipeCount.length) {
            return;
        }
        cacheRecipeCount[slot] = (byte) Math.max(1, value);
        removeSlotFromGTRecipeCache(slot);
    }

    public int archiveCapacity() {
        ItemStackTransfer inventory = getPatternInventory();
        // 基类构造期间可能调用此入口，字段尚未赋值时必须容错。
        return inventory == null ? 0 : inventory.getSlots();
    }

    public int archiveMaxCapacity() {
        return maxPatternCount;
    }

    public int archiveReserve() {
        return 0;
    }

    /** 容量预检必须只读，装不下时不能留下部分写入。 */
    public boolean archiveCanFit(int needed) {
        return needed <= maxPatternCount;
    }

    public int archiveHighestUsedSlot() {
        ItemStackTransfer inventory = getPatternInventory();
        if (inventory == null) {
            return -1;
        }
        for (int i = inventory.getSlots() - 1; i >= 0; i--) {
            try {
                ItemStack stack = inventory.getStackInSlot(i);
                if (stack != null && !stack.isEmpty()) {
                    return i;
                }
            } catch (Throwable ignored) {
                SwallowedErrors.report("MoltenPatternAssemblyPartMachine#archiveHighestUsedSlot", ignored);
            }
        }
        return -1;
    }

    /** 读异常须外抛，让引擎按已占用处理，避免覆盖已有样板。 */
    public ItemStack archivePatternAt(int slot) {
        ItemStackTransfer inventory = getPatternInventory();
        return inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(slot);
    }

    public boolean isArchiveIncludeSettings() {
        return archiveIncludeSettings;
    }

    public void setArchiveIncludeSettings(boolean value) {
        this.archiveIncludeSettings = value;
    }

    public boolean isArchiveIncludeName() {
        return archiveIncludeName;
    }

    public void setArchiveIncludeName(boolean value) {
        this.archiveIncludeName = value;
    }

    public boolean isArchiveAlignSlots() {
        return archiveAlignSlots;
    }

    public void setArchiveAlignSlots(boolean value) {
        this.archiveAlignSlots = value;
    }

    public LazyPatternArchiveCodec.PlaceMode archivePlaceMode() {
        return archiveAlignSlots
                ? LazyPatternArchiveCodec.PlaceMode.ALIGN
                : LazyPatternArchiveCodec.PlaceMode.APPEND;
    }

    public int[] archiveSummary() {
        ItemStackTransfer inventory = getPatternInventory();
        return PatternArchiveSnapshots.summary(archiveCapacity(),
                i -> inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(i));
    }

    // 档案采用统一的设置键，才能在两种总成之间互相粘贴；机器自身存档键仍各自保留。
    private static final String ARCHIVE_KEY_PATTERN_SCALE = "gtlmodular$lazy_pattern_scale";
    private static final String ARCHIVE_KEY_PATTERN_DIV_SCALE = "gtlmodular$lazy_pattern_div_scale";
    private static final String ARCHIVE_KEY_FOA_MODE = "gtlmodular$lazy_foa_mode";
    private static final String ARCHIVE_KEY_FOA_MULTIPLIER = "gtlmodular$lazy_foa_multiplier";
    private static final String ARCHIVE_KEY_OUTPUT_MODE = "gtlmodular$lazy_output_mode";
    private static final String MOLTEN_KEY_PATTERN_SCALE = "gtlmodular$pattern_scale";
    private static final String MOLTEN_KEY_PATTERN_DIV_SCALE = "gtlmodular$pattern_div_scale";
    private static final String MOLTEN_KEY_FOA_MODE = "gtlmodular$foa_mode";
    private static final String MOLTEN_KEY_FOA_MULTIPLIER = "gtlmodular$foa_multiplier";
    private static final String MOLTEN_KEY_OUTPUT_MODE = "gtlmodular$output_mode";
    private static final String ARCHIVE_KEY_CATALYST_ITEMS = "items";
    private static final String ARCHIVE_KEY_CATALYST_FLUIDS = "fluids";
    private static final String ARCHIVE_KEY_CATALYST_COUNT = "count";
    private static final int DEFAULT_CACHE_RECIPE_COUNT = 1;

    public LazyPatternArchiveCodec.Payload buildArchivePayload(boolean includeSettings, boolean includeName) {
        LazyPatternArchiveCodec.Payload payload = new LazyPatternArchiveCodec.Payload();
        payload.capacity = archiveCapacity();
        payload.includeSettings = includeSettings;
        payload.includeName = includeName;
        payload.name = getCustomName() == null ? "" : getCustomName();
        payload.dimension = archiveDimensionId();
        payload.pos = getPos() == null ? 0L : getPos().asLong();
        if (includeSettings) {
            CompoundTag settings = new CompoundTag();
            settings.putInt(ARCHIVE_KEY_PATTERN_SCALE, patternScale);
            settings.putInt(ARCHIVE_KEY_PATTERN_DIV_SCALE, patternDivScale);
            settings.putBoolean(ARCHIVE_KEY_FOA_MODE, foaMode);
            settings.putInt(ARCHIVE_KEY_FOA_MULTIPLIER, foaMultiplier);
            settings.putBoolean(ARCHIVE_KEY_OUTPUT_MODE, isOutputMode);
            payload.settings = settings;
        }
        ensureSlotRecipeTypeArray();
        ItemStackTransfer inventory = getPatternInventory();
        return PatternArchiveSnapshots.buildPayload(payload, archiveCapacity(),
                i -> inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(i),
                i -> slotRecipeType != null && i < slotRecipeType.length && slotRecipeType[i] != TYPE_NOT_TRIED
                        ? slotRecipeType[i] : null,
                i -> manualSlotTypes.get(i), this::buildArchivedCatalyst);
    }

    /** 只归档有内容的催化剂仓，避免粘贴实体化空仓。 */
    private CompoundTag buildArchivedCatalyst(int slot) {
        try {
            return PatternArchiveSnapshots.buildCatalyst(entry -> {
                if (slot >= 0 && slot < catalystItems.length && PatternStacks.hasAnyCatalystItem(catalystItems[slot])) {
                    entry.put(ARCHIVE_KEY_CATALYST_ITEMS, catalystItems[slot].serializeNBT());
                }
                if (slot >= 0 && slot < catalystFluids.length && PatternStacks.hasAnyCatalystFluid(catalystFluids[slot])) {
                    entry.put(ARCHIVE_KEY_CATALYST_FLUIDS, catalystFluids[slot].serializeNBT());
                }
            }, () -> slot >= 0 && slot < cacheRecipeCount.length
                    ? Math.max(1, (int) cacheRecipeCount[slot]) : DEFAULT_CACHE_RECIPE_COUNT,
                    DEFAULT_CACHE_RECIPE_COUNT);
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#buildArchivedCatalyst", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成归档：第 {} 槽催化剂打包失败（该槽只搬样板）pos={} 原因={}",
                    slot + 1, getPos(), failure.toString());
            return null;
        }
    }

    private String archiveDimensionId() {
        return PatternArchiveSnapshots.dimensionId(this::getLevel);
    }

    void archiveWritePattern(int slot, ItemStack stack) {
        ItemStackTransfer inventory = getPatternInventory();
        if (inventory == null) {
            return;
        }
        if (slot >= 0 && slot < maxPatternCount && slot >= patternCapacity()) {
            growCapacityForBatchInsert(slot + 1);
        }
        // 上游终端入口会触发逐槽记账，单独写底层仓会漏掉缓存；同时保留底层写入兜底。
        try {
            appeng.api.inventories.InternalInventory terminal = getTerminalPatternInventory();
            if (terminal != null) {
                terminal.setItemDirect(slot, stack);
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#archiveWritePattern.terminal", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成归档：第 {} 槽 AE/终端面写入失败"
                            + "（该槽 AE 里可能看不到，存档面仍会写入）pos={} 原因={}",
                    slot + 1, getPos(), failure.toString());
        }
        inventory.setStackInSlot(slot, stack);
        archiveLandedSlots.add(slot);
    }

    void writeArchivedRecipeType(int slot, int recipeType) {
        try {
            ensureSlotRecipeTypeArray();
            if (slotRecipeType != null && slot >= 0 && slot < slotRecipeType.length) {
                slotRecipeType[slot] = recipeType;
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#writeArchivedRecipeType", ignored);
        }
    }

    void writeArchivedManualType(int slot, ResourceLocation typeId) {
        modeContext().writeArchivedManualType(slot, typeId);
    }

    /** 反序列化不触发内容回调，必须补通知以更新催化剂表。 */
    boolean writeArchivedCatalyst(int slot, CompoundTag catalyst) {
        if (catalyst == null || catalyst.isEmpty() || slot < 0 || slot >= maxPatternCount) {
            return false;
        }
        boolean wrote = false;
        try {
            if (catalyst.contains(ARCHIVE_KEY_CATALYST_ITEMS, Tag.TAG_COMPOUND)
                    && slot < catalystItems.length && catalystItems[slot] != null) {
                ItemStackTransfer items = catalystItems[slot];
                items.deserializeNBT(catalyst.getCompound(ARCHIVE_KEY_CATALYST_ITEMS));
                try {
                    items.onContentsChanged();
                } catch (Throwable ignored) {
                    SwallowedErrors.report("MoltenPatternAssemblyPartMachine#writeArchivedCatalyst.itemsContentsChanged", ignored);
                }
                wrote = true;
            }
            if (catalyst.contains(ARCHIVE_KEY_CATALYST_FLUIDS, Tag.TAG_COMPOUND)
                    && slot < catalystFluids.length && catalystFluids[slot] != null) {
                FluidTransferList fluids = catalystFluids[slot];
                fluids.deserializeNBT(catalyst.getCompound(ARCHIVE_KEY_CATALYST_FLUIDS));
                try {
                    fluids.onContentsChanged();
                } catch (Throwable ignored) {
                    SwallowedErrors.report("MoltenPatternAssemblyPartMachine#writeArchivedCatalyst.fluidsContentsChanged", ignored);
                }
                wrote = true;
            }
            if (catalyst.contains(ARCHIVE_KEY_CATALYST_COUNT, Tag.TAG_BYTE)
                    && slot < cacheRecipeCount.length) {
                byte count = (byte) Math.max(1, (int) catalyst.getByte(ARCHIVE_KEY_CATALYST_COUNT));
                if (cacheRecipeCount[slot] != count) {
                    cacheRecipeCount[slot] = count;
                    // 阈值改变后缓存结论失效；仅在已有缓存时通知，避免空槽产生无用更新。
                    if (hasRecipeCacheInSlot(slot)) {
                        removeSlotFromGTRecipeCache(slot);
                    }
                }
                wrote = true;
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#writeArchivedCatalyst", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成归档：第 {} 槽催化剂写入失败（该槽催化剂保持原样）pos={} 原因={}",
                    slot + 1, getPos(), failure.toString());
        }
        return wrote;
    }

    /** 批量恢复设置直接写字段，避免每个 setter 都让 AE 重读整仓。 */
    void writeArchivedSettings(CompoundTag settings) {
        if (settings == null || settings.isEmpty()) {
            return;
        }
        try {
            Integer scale = archiveSettingInt(settings, ARCHIVE_KEY_PATTERN_SCALE, MOLTEN_KEY_PATTERN_SCALE);
            if (scale != null) {
                patternScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
            }
            Integer div = archiveSettingInt(settings, ARCHIVE_KEY_PATTERN_DIV_SCALE, MOLTEN_KEY_PATTERN_DIV_SCALE);
            if (div != null) {
                patternDivScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, div));
            }
            Integer foaMulti = archiveSettingInt(settings, ARCHIVE_KEY_FOA_MULTIPLIER, MOLTEN_KEY_FOA_MULTIPLIER);
            if (foaMulti != null) {
                foaMultiplier = Math.max(MIN_FOA_MULTIPLIER, Math.min(MAX_FOA_MULTIPLIER, foaMulti));
            }
            Boolean foa = archiveSettingBoolean(settings, ARCHIVE_KEY_FOA_MODE, MOLTEN_KEY_FOA_MODE);
            if (foa != null) {
                foaMode = foa;
            }
            Boolean output = archiveSettingBoolean(settings, ARCHIVE_KEY_OUTPUT_MODE, MOLTEN_KEY_OUTPUT_MODE);
            if (output != null) {
                isOutputMode = output;
            }
            rewriteCache.invalidate();
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#writeArchivedSettings", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成归档：设置类写入失败（已忽略，按原设置）pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    private static Integer archiveSettingInt(CompoundTag settings, String primary, String fallback) {
        if (settings.contains(primary, Tag.TAG_INT)) {
            return settings.getInt(primary);
        }
        if (settings.contains(fallback, Tag.TAG_INT)) {
            return settings.getInt(fallback);
        }
        return null;
    }

    private static Boolean archiveSettingBoolean(CompoundTag settings, String primary, String fallback) {
        if (settings.contains(primary, Tag.TAG_BYTE)) {
            return settings.getBoolean(primary);
        }
        if (settings.contains(fallback, Tag.TAG_BYTE)) {
            return settings.getBoolean(fallback);
        }
        return null;
    }

    void writeArchivedName(String name) {
        patternNameController().writeArchivedName(name);
    }

    /** 只有档案成功写入才可清源；未归档的共享催化剂、共享电路和镜像绑定必须保留。 */
    void clearArchiveContent() {
        ItemStackTransfer inventory = getPatternInventory();
        int capacity = archiveCapacity();
        // 清源同时走终端回调和底层仓写入，避免 AE 与机器缓存残留不同状态。
        beginArchiveBulkWrite();
        try {
            for (int i = 0; i < capacity; i++) {
                try {
                    if (inventory != null) {
                        inventory.setStackInSlot(i, ItemStack.EMPTY);
                    }
                } catch (Throwable ignored) {
                    SwallowedErrors.report("MoltenPatternAssemblyPartMachine#clearArchiveContent.patternInventorySlot", ignored);
                }
                try {
                    appeng.api.inventories.InternalInventory terminal = getTerminalPatternInventory();
                    if (terminal != null && i < terminal.size()) {
                        terminal.setItemDirect(i, ItemStack.EMPTY);
                    }
                } catch (Throwable ignored) {
                    SwallowedErrors.report("MoltenPatternAssemblyPartMachine#clearArchiveContent.terminalSlot", ignored);
                }
                try {
                    if (slotRecipeType != null && i < slotRecipeType.length) {
                        slotRecipeType[i] = TYPE_NOT_TRIED;
                    }
                } catch (Throwable ignored) {
                    SwallowedErrors.report("MoltenPatternAssemblyPartMachine#clearArchiveContent.recipeTypeSlot", ignored);
                }
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#clearArchiveContent.patterns", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成归档：清空样板失败（已尽力而为）pos={} 原因={}",
                    getPos(), failure.toString());
        }
        try {
            manualSlotTypes.clear();
            refreshManualAssignDisplay();
        } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#clearArchiveContent.manualTypes", ignored);
        }
        try {
            for (int i = 0; i < capacity; i++) {
                ItemStackTransfer items = i < catalystItems.length ? catalystItems[i] : null;
                if (items != null) {
                    for (int s = 0; s < items.getSlots(); s++) {
                        items.setStackInSlot(s, ItemStack.EMPTY);
                    }
                    // 清空催化剂仓后补回调，才能让派生催化剂表同步变空。
                    try {
                        items.onContentsChanged();
                    } catch (Throwable ignored) {
                        SwallowedErrors.report("MoltenPatternAssemblyPartMachine#clearArchiveContent.itemsContentsChanged", ignored);
                    }
                }
                FluidTransferList fluids = i < catalystFluids.length ? catalystFluids[i] : null;
                if (fluids != null) {
                    for (int t = 0; t < fluids.getTanks(); t++) {
                        fluids.setFluidInTank(t, FluidStack.empty());
                    }
                    try {
                        fluids.onContentsChanged();
                    } catch (Throwable ignored) {
                        SwallowedErrors.report("MoltenPatternAssemblyPartMachine#clearArchiveContent.fluidsContentsChanged", ignored);
                    }
                }
                if (i < cacheRecipeCount.length) {
                    cacheRecipeCount[i] = (byte) DEFAULT_CACHE_RECIPE_COUNT;
                }
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#clearArchiveContent.catalysts", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成归档：清空催化剂失败（已忽略）pos={} 原因={}",
                    getPos(), failure.toString());
        }
        // 清仓后也要清归档落地标记，避免空槽被兜底识别为有样板。
        try {
            archiveLandedSlots.clear();
        } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#clearArchiveContent.landedSlots", ignored);
        }
    }

    /** refreshAllByProduct 会重建 slot2PatternMap 并通知 AE，只重算反向映射会沿用旧解码结果。 */
    public void afterArchiveBulkChange() {
        // 批量窗口必须关闭，避免后续普通写入继续跳过额外收尾。
        archiveBulkPatternWrite = false;
        if (isRemote()) {
            return;
        }
        try {
            refreshAllByProduct();
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#afterArchiveBulkChange.refreshPatterns", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成归档：收尾重扫样板表失败"
                            + "（样板已写好，缓存最迟由下一 tick 的既有通道兜住）pos={} 原因={}",
                    getPos(), failure.toString());
        }
        try {
            rewriteCache.invalidate();
            markPatternTableStale("归档批量收尾(片C)");
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#afterArchiveBulkChange.markStale", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成归档：收尾置位失败（已忽略）pos={} 原因={}",
                    getPos(), failure.toString());
        }
        try {
            int ldlNonEmpty = 0;
            ItemStackTransfer inv = getPatternInventory();
            if (inv != null) {
                for (int i = 0; i < inv.getSlots(); i++) {
                    ItemStack stack = inv.getStackInSlot(i);
                    if (stack != null && !stack.isEmpty()) {
                        ldlNonEmpty++;
                    }
                }
            }
            int aeNonEmpty = 0;
            appeng.api.inventories.InternalInventory term = getTerminalPatternInventory();
            if (term != null) {
                for (int i = 0; i < term.size(); i++) {
                    ItemStack stack = term.getStackInSlot(i);
                    if (stack != null && !stack.isEmpty()) {
                        aeNonEmpty++;
                    }
                }
            }
            GTLModular.LOGGER.info("[gtlmodular] 归档批量收尾：AE 可见样板={} LDLib 面非空={} AE 面非空={} 容量={} pos={}",
                    getAvailablePatterns().size(), ldlNonEmpty, aeNonEmpty, patternCapacity(), getPos());
        } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#afterArchiveBulkChange.inventorySummary", ignored);
        }
        try { scheduleRenderUpdate(); } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#afterArchiveBulkChange.renderUpdate", ignored);
        }   // 批量修改后让客户端重读，避免界面残留已移走的样板。
    }

    /** 归档写入可能只落到底层仓而未更新上游激活缓存；默认保留落地槽兜底，避免已有样板不参与匹配。 */
    public static final boolean HAS_PATTERN_SLOT_ARCHIVE_FALLBACK = true;

    /** 先核对仓内容以排除清仓后的缓存残留，再结合上游激活缓存与归档落地记录。 */
    @Override
    protected boolean hasPatternInSlot(int slot) {
        if (!HAS_PATTERN_SLOT_ARCHIVE_FALLBACK) {
            return super.hasPatternInSlot(slot);
        }
        try {
            ItemStackTransfer inventory = getPatternInventory();
            if (inventory != null && slot >= 0 && slot < inventory.getSlots()) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack == null || stack.isEmpty()) {
                    return false;
                }
            }
            return super.hasPatternInSlot(slot) || archiveLandedSlots.contains(slot);
        } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#hasPatternInSlot", ignored);
            // 只读判据失败时回到父类判断，避免诊断路径中断 AE 推送或配方匹配。
            try {
                return super.hasPatternInSlot(slot);
            } catch (Throwable ignoredAgain) {
                SwallowedErrors.report("MoltenPatternAssemblyPartMachine#hasPatternInSlot.parentFallback", ignoredAgain);
                return false;
            }
        }
    }

    public static boolean LOOKUP_SIDE_SLOT_NARROWING = true;

    private int lookupNarrowingModeIndex = -1;

    private int isolationOwnModeIndexReadOnly() {
        return PatternLookupNarrowing.ownModeIndex(this::getAvailableRecipeTypes,
                () -> modeCursorIndex, () -> activeRecipeTypeIndex);
    }

    private int lookupNarrowingModeIndex() {
        lookupNarrowingModeIndex = -1;
        if (!LOOKUP_SIDE_SLOT_NARROWING) {
            return -1;
        }
        try {
            int own = PatternLookupNarrowing.lookupModeIndex(this::isolationOwnModeIndexReadOnly,
                    this::getAvailableRecipeTypes, this::firstRecipeLogicController);
            if (own < 0) {
                return -1;
            }
            lookupNarrowingModeIndex = own;
            return own;
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#lookupNarrowingModeIndex", failure);
            lookupNarrowingModeIndex = -1;
            return -1;
        }
    }

    private int[] narrowSlotsToCurrentMode(int[] base) {
        return PatternLookupNarrowing.narrowSlots(base, this::lookupNarrowingModeIndex,
                this::slotParticipatesInLookup);
    }

    private boolean slotParticipatesInLookup(int slot) {
        return PatternLookupNarrowing.slotParticipates(slot, this::getAvailableRecipeTypes,
                this::manualTypeIndexOf, this::lookupRecipeTypeCacheAt, () -> lookupNarrowingModeIndex);
    }

    private int lookupRecipeTypeCacheAt(int slot) {
        final int[] cache = slotRecipeType;
        if (cache == null || slot < 0 || slot >= cache.length) {
            return TYPE_NOT_TRIED;
        }
        return cache[slot];
    }

    @Override
    protected int[] getActiveSlots() {
        return narrowSlotsToCurrentMode(super.getActiveSlots());
    }

    @Override
    protected int[] getActiveAndUnCachedSlots() {
        return narrowSlotsToCurrentMode(super.getActiveAndUnCachedSlots());
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (slotRecipeType != null) {
            // 只写非默认槽位减少存档体积，读侧仍兼容完整数组格式。
            int count = 0;
            for (int v : slotRecipeType) {
                if (v != TYPE_NOT_TRIED) {
                    count++;
                }
            }
            if (count > 0) {
                int[] sparse = new int[count * 2];
                int k = 0;
                for (int i = 0; i < slotRecipeType.length; i++) {
                    if (slotRecipeType[i] != TYPE_NOT_TRIED) {
                        sparse[k++] = i;
                        sparse[k++] = slotRecipeType[i];
                    }
                }
                tag.putIntArray("gtlmodular$slot_recipe_type_sparse", sparse);
            }
        }
        if (!manualSlotTypes.isEmpty()) {
            CompoundTag manual = new CompoundTag();
            manualSlotTypes.forEach((slot, id) -> manual.putString("s" + slot, id.toString()));
            tag.put("gtlmodular$manual_slot_types", manual);
        }
        // 这些设置只有同步注解，需显式保存才能在世界重载后保留。
        tag.putInt("gtlmodular$pattern_scale", patternScale);
        tag.putInt("gtlmodular$pattern_div_scale", patternDivScale);
        tag.putBoolean("gtlmodular$foa_mode", foaMode);
        tag.putInt("gtlmodular$foa_multiplier", foaMultiplier);
        tag.putBoolean("gtlmodular$output_mode", this.isOutputMode);
        if (boundModeType != null) {
            tag.putString("gtlmodular$bound_mode_type", boundModeType.toString());
        }
        if (!modeNames.isEmpty()) {
            CompoundTag names = new CompoundTag();
            int written = 0;
            for (java.util.Map.Entry<String, String> entry : modeNames.entrySet()) {
                if (written >= MAX_SAVED_MODE_NAMES) {
                    break;
                }
                String value = entry.getValue();
                if (entry.getKey() == null || entry.getKey().isEmpty() || value == null || value.isEmpty()) {
                    continue;
                }
                names.putString(entry.getKey(), value.length() > MAX_SAVED_NAME_LENGTH
                        ? value.substring(0, MAX_SAVED_NAME_LENGTH) : value);
                written++;
            }
            if (written > 0) {
                tag.put("gtlmodular$mode_names", names);
            }
        }
        if (!lockedName.isEmpty()) {
            tag.putString("gtlmodular$locked_name", lockedName.length() > MAX_SAVED_NAME_LENGTH
                    ? lockedName.substring(0, MAX_SAVED_NAME_LENGTH) : lockedName);
        }
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        // 恢复容量须覆盖存档高槽位，避免初始窄仓使已有样板不可见。
        backfillCapacityAfterLoad();
        int[] sparse = tag.getIntArray("gtlmodular$slot_recipe_type_sparse");
        if (sparse.length > 0) {
            int slots = slotCount();
            if (slots > 0) {
                int[] fresh = new int[slots];
                Arrays.fill(fresh, TYPE_NOT_TRIED);
                for (int i = 0; i + 1 < sparse.length; i += 2) {
                    int slot = sparse[i];
                    if (slot >= 0 && slot < slots) {
                        fresh[slot] = sparse[i + 1];
                    }
                }
                slotRecipeType = fresh;
            }
        } else {
            int[] saved = tag.getIntArray("gtlmodular$slot_recipe_type");
            if (saved.length == slotCount()) {
                slotRecipeType = saved;
            }
        }
        manualSlotTypes.clear();
        CompoundTag manual = tag.getCompound("gtlmodular$manual_slot_types");
        for (String key : manual.getAllKeys()) {
            if (!key.startsWith("s")) {
                continue;
            }
            try {
                int slot = Integer.parseInt(key.substring(1));
                net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(manual.getString(key));
                if (id != null) {
                    manualSlotTypes.put(slot, id);
                }
            } catch (RuntimeException ignored) {
            }
        }
        try {
            // 客户端以同步字段显示模式，不能把区块 NBT 当作服务端权威绑定。
            if (!isRemote() && tag.contains("gtlmodular$bound_mode_type", Tag.TAG_STRING)) {
                ResourceLocation bound = ResourceLocation.tryParse(tag.getString("gtlmodular$bound_mode_type"));
                if (bound != null) {
                    this.boundModeType = bound;
                    this.boundModePendingResolve = true;
                    applyBoundModeTypeIfPossible();
                }
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#loadCustomPersistedData.boundMode", failure);
            this.boundModeType = null;
            this.boundModePendingResolve = false;
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成：绑定模式读档失败（已忽略，按未绑定处理）pos={} 原因={}",
                    getPos(), failure.toString());
        }
        try {
            if (!isRemote()) {
                modeNames.clear();
                this.lockedName = "";
                if (patternNameController != null) {
                    patternNameController.reset();
                }
                if (tag.contains("gtlmodular$mode_names", Tag.TAG_COMPOUND)) {
                    CompoundTag names = tag.getCompound("gtlmodular$mode_names");
                    int accepted = 0;
                    for (String modeKey : names.getAllKeys()) {
                        if (accepted >= MAX_SAVED_MODE_NAMES) {
                            break;
                        }
                        String value = names.getString(modeKey);
                        if (modeKey.isEmpty() || value.isEmpty() || value.length() > MAX_SAVED_NAME_LENGTH) {
                            continue;
                        }
                        modeNames.put(modeKey, value);
                        accepted++;
                    }
                }
                if (tag.contains("gtlmodular$locked_name", Tag.TAG_STRING)) {
                    String locked = tag.getString("gtlmodular$locked_name");
                    this.lockedName = locked.length() > MAX_SAVED_NAME_LENGTH ? "" : locked;
                }
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#loadCustomPersistedData.modeNames", failure);
            modeNames.clear();
            this.lockedName = "";
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成：名字表读档失败（已忽略，按「该模式还没有名字」处理）pos={} 原因={}",
                    getPos(), failure.toString());
        }
        // 读档直接恢复设置，避免 setter 在节点未就绪时触发 AE 刷新。
        try {
            if (tag.contains("gtlmodular$pattern_scale", Tag.TAG_INT)) {
                this.patternScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, tag.getInt("gtlmodular$pattern_scale")));
            }
            if (tag.contains("gtlmodular$pattern_div_scale", Tag.TAG_INT)) {
                this.patternDivScale = Math.max(MIN_SCALE,
                        Math.min(MAX_SCALE, tag.getInt("gtlmodular$pattern_div_scale")));
            }
            if (tag.contains("gtlmodular$foa_mode", Tag.TAG_BYTE)) {
                this.foaMode = tag.getBoolean("gtlmodular$foa_mode");
            }
            if (tag.contains("gtlmodular$foa_multiplier", Tag.TAG_INT)) {
                this.foaMultiplier = Math.max(MIN_FOA_MULTIPLIER,
                        Math.min(MAX_FOA_MULTIPLIER, tag.getInt("gtlmodular$foa_multiplier")));
            }
            if (tag.contains("gtlmodular$output_mode", Tag.TAG_BYTE)) {
                this.isOutputMode = tag.getBoolean("gtlmodular$output_mode");
            }
            rewriteCache.invalidate();
            markPatternTableStale("读档设置落盘(片E)");
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#loadCustomPersistedData.rewriteSettings", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成：视图改写设置读档失败（已忽略，按默认值）pos={} 原因={}",
                    getPos(), failure.toString());
        }
        syncCustomNameToMode(activeRecipeTypeIndex);
    }

    @Override
    public Widget createUIWidget() {
        int width = paginationUIManager.getUiWidth();
        int height = paginationUIManager.getUiHeight();
        WidgetGroup group = new WidgetGroup(0, 0, width, height);
        group.addWidget(new LabelWidget(8, 2,
                () -> Component.translatable(isOnline()
                        ? "gtceu.gui.me_network.online"
                        : "gtceu.gui.me_network.offline").getString()));
        group.addWidget(new AETextInputButtonWidget(width - 78, 2, 70, 10)
                .setText(getCustomName())
                .setOnConfirm(this::onPlayerConfirmedCustomName)
                .setButtonTooltips(Component.translatable("gui.gtceu.rename.desc")));
        group.addWidget(paginationUIManager.createPaginationUI(slot -> {
            setCatalystViewSlot(slot + 1);
            setCatalystPageOpen(true);
        }));
        // LDLib 从末尾开始派发点击，覆盖层须放在分页控件之后。
        group.addWidget(new PatternSlotRightClickOverlay(group, width, height,
                slot -> {
                    setManualAssignSlot(slot + 1);
                    assignManualType(slot + 1, false);
                }, slot -> {
                    setManualAssignSlot(slot + 1);
                    CatalystAccessConfigurator page = activeCatalystPage;
                    if (page != null) {
                        page.openSlotFromPattern(slot);
                    } else {
                        setCatalystViewSlot(slot + 1);
                        setCatalystPageOpen(true);
                    }
                }));
        return group;
    }

    private static final int PANEL_DOWN_SHIFT = 0;

    private static final Map<ConfiguratorPanel, int[]> PANEL_SHIFT_STATE = new WeakHashMap<>();

    private ConfiguratorPanel lastConfiguratorPanel;

    private void shiftConfiguratorPanelDown(ConfiguratorPanel panel) {
        if (panel == null || PANEL_DOWN_SHIFT == 0) {
            return;
        }
        try {
            synchronized (PANEL_SHIFT_STATE) {
                int[] state = PANEL_SHIFT_STATE.get(panel);
                boolean first = (state == null);
                if (first) {
                    state = new int[] { panel.getSelfPositionY(), 0 };
                    PANEL_SHIFT_STATE.put(panel, state);
                }
                int target = state[0] + PANEL_DOWN_SHIFT;
                if (panel.getSelfPositionY() == target) {
                    return; // 位置不变时不重复写入，避免无谓触发子控件位置更新。
                }
                panel.setSelfPositionY(target);
                if (first) {
                    GTLModular.LOGGER.info("[gtlmodular] 配置器面板下移: 原 Y={} → {}（+{}）设后自身Y={} 绝对Y={}"
                                    + " 屏幕高={} 本机页高={}",
                            state[0], target, PANEL_DOWN_SHIFT, panel.getSelfPositionY(), panel.getPositionY(),
                            screenHeightOf(panel), pageHeight());
                } else if (state[1] < 3) {
                    state[1]++;
                    GTLModular.LOGGER.info("[gtlmodular] 配置器面板下移被改回去了，已重设: 基准={} 目标={} 现值={}（第 {} 次）",
                            state[0], target, panel.getSelfPositionY(), state[1]);
                }
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#shiftConfiguratorPanelDown", failure);
            GTLModular.LOGGER.debug("[gtlmodular] 猪咪样板总成：配置器面板下移失败（已忽略）面板={} 原因={}",
                    panel, failure.toString());
        }
    }

    private static int screenHeightOf(ConfiguratorPanel panel) {
        try {
            return panel.getGui() == null ? -1 : panel.getGui().getScreenHeight();
        } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#screenHeightOf", ignored);
            return -1;
        }
    }

    private int pageHeight() {
        try {
            return paginationUIManager == null ? -1 : paginationUIManager.getUiHeight();
        } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#pageHeight", ignored);
            return -1;
        }
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel panel) {
        super.attachConfigurators(panel);
        List<IFancyConfigurator> pages = new ArrayList<>();
        pages.add(new ModeScaleConfigurator(this));
        CatalystAccessConfigurator catalystPage = new CatalystAccessConfigurator(this);
        this.activeCatalystPage = catalystPage;
        pages.add(catalystPage);
        panel.attachConfigurators(pages.toArray(new IFancyConfigurator[0]));
        int before = panel.getTabs().size() - pages.size();
        List<ConfiguratorPanel.Tab> tabs = panel.getTabs();
        int catalystTabIndex = before + pages.size() - 1;
        catalystPage.bindPanel(panel, tabs.size() > catalystTabIndex ? tabs.get(catalystTabIndex) : null);
        // 催化剂页缓存自己的 tab 下标，归档页须在其后追加，避免右键选槽打开错误页面。
        try {
            int archiveTabBefore = panel.getTabs().size();
            ArchiveConfigurator archivePage = new ArchiveConfigurator(this);
            panel.attachConfigurators(archivePage);
            List<ConfiguratorPanel.Tab> afterTabs = panel.getTabs();
            // 绑定本页 tab，几何诊断才能区分归档页是否展开。
            archivePage.bindPanel(panel, afterTabs.size() > archiveTabBefore ? afterTabs.get(archiveTabBefore) : null);
            GTLModular.LOGGER.debug("[gtlmodular] 猪咪样板总成：归档页已挂载 pos={} 下标={}（挂之前 {} 个 tab）",
                    getPos(), afterTabs.size() > archiveTabBefore ? archiveTabBefore : -1, archiveTabBefore);
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#attachConfigurators.archivePage", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成：归档页挂载失败 pos={} 原因={}",
                    getPos(), failure.toString());
        }
        this.lastConfiguratorPanel = panel;

        try {
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
                org.gtlcore.gtlcore.client.gui.MEStorageConfiguratorTabLayout.setEnabled(panel, true);
                org.gtlcore.gtlcore.client.gui.MEStorageConfiguratorTabLayout.arrange(panel);
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#attachConfigurators.tabLayout", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成：页签多列排布未生效 pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    private static volatile String gtlmodular$lastPageGeometry = "";

    static void logPageGeometry(String tag, Widget page, ConfiguratorPanel panel, ConfiguratorPanel.Tab tab) {
        try {
            int screenH = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
            int absY = page.getPositionY();
            int selfY = page.getSelfPositionY();
            int sizeH = page.getSizeHeight();
            int panelY = panel == null ? -9999 : panel.getPositionY();
            Widget parent = page.getParent();
            int parentY = parent == null ? -9999 : parent.getPositionY();
            int tabs = panel == null ? -1 : panel.getTabs().size();
            boolean mine = panel != null && tab != null && panel.getExpanded() == tab;
            String line = String.format(
                    "%s 屏幕高=%d 页绝对Y=%d 页selfY=%d 页高=%d 上越界=%d 父层Y=%d 面板Y=%d tab数=%d 本页展开=%s",
                    tag, screenH, absY, selfY, sizeH, Math.max(0, -absY), parentY, panelY, tabs, mine);
            if (!line.equals(gtlmodular$lastPageGeometry)) {
                gtlmodular$lastPageGeometry = line;
                GTLModular.LOGGER.info("[gtlmodular] 展开页几何 {}", line);
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("MoltenPatternAssemblyPartMachine#logPageGeometry", ignored);
        }
    }

    static final class ModeScaleConfigurator implements IFancyConfigurator {

        private static final int PAD_Y = 3;
        private static final int SECTION_GAP = 6;
        private static final int ROW_GAP = 3;
        private static final int HEADER_H = 16;
        private static final int HEADER_Y = 4;
        private static final int LABEL_H = 10;
        private static final int ROW_H = 18;
        private static final int CHIP_GAP = 2;
        private static final int CHIP_MIN_W = 26;
        private static final int CHIP_MAX_W = 54;

        /** 数字输入框还会给增减按钮预留宽度，布局必须为可读文本留足空间。 */
        private static final int INPUT_MIN_READABLE_W = 62;
        private static final int SLOT_INPUT_X = 28;
        private static final int SLOT_INPUT_W = INPUT_MIN_READABLE_W;
        private static final int SLOT_ASSIGN_X = SLOT_INPUT_X + SLOT_INPUT_W + 2;
        private static final int SLOT_CLEAR_X = SLOT_ASSIGN_X + 34 + 2;
        private static final int SLOT_BTN_W = 34;
        private static final int FOA_TOGGLE_X = 60;
        private static final int FOA_TOGGLE_W = 20;
        private static final int FOA_LABEL_X = 84;
        private static final int FOA_STATE_X = 112;
        private static final int FOA_INPUT_X = 8;
        private static final int FOA_INPUT_W = 48;
        private static final int FOA_ROW_H = ROW_H;
        private static final int ROW_TEXT_OFFSET = (ROW_H - LABEL_H) / 2;
        private static final int FOA_TEXT_OFFSET = (FOA_ROW_H - LABEL_H) / 2;
        private static final int HEADER_TEXT_OFFSET = (HEADER_H - LABEL_H) / 2;
        private static final int BAND_PAD_X = 4;

        private static final int PAGE_WIDTH = 172;
        private static final int BAND_X = 4;
        private static final int BAND_W = PAGE_WIDTH - 8;
        private static final int BAND_BG = 0x33000000;
        private static final int BAND_ACCENT = 0xCC00D5FF;
        private static final int ACCENT_W = 2;
        private static final int BAND_TEXT_X = BAND_X + BAND_PAD_X;
        private static final int BAND_TITLE_X = BAND_TEXT_X + 2;

        private static final int BAND_MODE_Y = HEADER_Y + HEADER_H + SECTION_GAP;
        private static final int BAND_MODE_TITLE_Y = BAND_MODE_Y + PAD_Y;
        private static final int BAND_MODE_ROW_Y = BAND_MODE_TITLE_Y + LABEL_H + ROW_GAP;
        private static final int BAND_MODE_STATE_Y = BAND_MODE_ROW_Y + ROW_H + ROW_GAP;
        private static final int BAND_MODE_H = PAD_Y + LABEL_H + ROW_GAP + ROW_H + ROW_GAP + LABEL_H + PAD_Y;

        private static final int BAND_SCALE_Y = BAND_MODE_Y + BAND_MODE_H + SECTION_GAP;
        private static final int BAND_SCALE_TITLE_Y = BAND_SCALE_Y + PAD_Y;
        private static final int BAND_SCALE_LABEL_Y = BAND_SCALE_TITLE_Y + LABEL_H + ROW_GAP;
        private static final int BAND_SCALE_ROW_Y = BAND_SCALE_LABEL_Y + LABEL_H + ROW_GAP;
        private static final int BAND_SCALE_FOA_Y = BAND_SCALE_ROW_Y + ROW_H + ROW_GAP;
        private static final int BAND_SCALE_H = PAD_Y + LABEL_H + ROW_GAP + LABEL_H + ROW_GAP + ROW_H + ROW_GAP
                + FOA_ROW_H + PAD_Y;

        private static final int PAGE_HEIGHT = BAND_SCALE_Y + BAND_SCALE_H + SECTION_GAP;

        // 点击后立即刷新芯片文字，避免颜色等到重开界面才更新。
        private final java.util.List<ButtonWidget> chipWidgets = new java.util.ArrayList<>();

        private void refreshChipLabels() {
            for (int i = 0; i < chipWidgets.size(); i++) {
                String lb = (i == machine.getModeCursorIndex() ? "§b§l" : "§7") + modeName(i);
                try {
                    chipWidgets.get(i).setButtonTexture(
                            new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(lb)));
                } catch (Throwable ignored) {
                    SwallowedErrors.report("MoltenPatternAssemblyPartMachine.ModeScaleConfigurator#refreshChipLabels", ignored);
                }
            }
        }

        private final MoltenPatternAssemblyPartMachine machine;

        ModeScaleConfigurator(MoltenPatternAssemblyPartMachine machine) {
            this.machine = machine;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.pattern_assembly.mode_scale.title");
        }

        @Override
        public IGuiTexture getIcon() {
            return new ResourceTexture("gtceu:textures/item/portable_debug_scanner.png");
        }

        @Override
        public List<Component> getTooltips() {
            List<Component> tips = new ArrayList<>(4);
            List<GTRecipeType> types = machine.getAvailableRecipeTypes();
            if (types.isEmpty()) {
                tips.add(Component.translatable("gtlmodular.pattern_assembly.mode.none"));
            } else {
                int index = Math.max(0, Math.min(types.size() - 1, machine.getModeCursorIndex()));
                tips.add(PatternConfiguratorWidgets.modeComponent(types.get(index)));
            }
            tips.add(Component.translatable("gtlmodular.pattern_assembly.mode_scale.tooltip.merge"));
            tips.add(Component.translatable("gtlmodular.pattern_assembly.mode_scale.tooltip.scale"));
            tips.add(Component.translatable("gtlmodular.pattern_assembly.foa.tooltip"));
            return tips;
        }

        @Override
        public Widget createConfigurator() {
            boolean multi = machine.getAvailableRecipeTypes().size() > 1;
            WidgetGroup group = new WidgetGroup(0, 0, PAGE_WIDTH, PAGE_HEIGHT);

            List<GTRecipeType> modeTypes = machine.getAvailableRecipeTypes();
            group.addWidget(new ImageWidget(BAND_X, HEADER_Y, BAND_W, HEADER_H,
                    new ColorRectTexture(BAND_BG)));
            group.addWidget(new ImageWidget(BAND_X, HEADER_Y, ACCENT_W, HEADER_H,
                    new ColorRectTexture(BAND_ACCENT)));
            if (modeTypes.size() <= 1) {
                group.addWidget(new LabelWidget(BAND_X + 6, HEADER_Y + HEADER_TEXT_OFFSET, this::modeNameLabel));
            } else {
                int chipCount = modeTypes.size();
                int usableW = BAND_W - 4 - (chipCount - 1) * CHIP_GAP;
                int chipW = Math.max(CHIP_MIN_W, Math.min(CHIP_MAX_W, usableW / chipCount));
                if (chipW * chipCount + CHIP_GAP * (chipCount - 1) > BAND_W - 4) {
                    ButtonWidget prevBtn = new ButtonWidget(BAND_X + 2, HEADER_Y + 1, 20, HEADER_H - 2,
                            new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture("\u25c0")),
                            data -> cycle(data, false));
                    ButtonWidget nextBtn = new ButtonWidget(BAND_X + BAND_W - 22, HEADER_Y + 1, 20, HEADER_H - 2,
                            new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture("\u25b6")),
                            data -> cycle(data, true));
                    group.addWidget(new LabelWidget(BAND_X + 26, HEADER_Y + HEADER_TEXT_OFFSET, this::modeNameLabel));
                    group.addWidget(new LabelWidget(BAND_X + BAND_W - 66, HEADER_Y + HEADER_TEXT_OFFSET, this::modeIndexLabel));
                    group.addWidget(prevBtn);
                    group.addWidget(nextBtn);
                } else {
                    int active = Math.max(0, Math.min(chipCount - 1, machine.getModeCursorIndex()));
                    for (int i = 0; i < chipCount; i++) {
                        int chipX = BAND_X + 2 + i * (chipW + CHIP_GAP);
                        boolean isActive = i == active;
                        String label = (isActive ? "§b§l" : "§7") + modeName(i);
                        final int target = i;
                        ButtonWidget chip = new ButtonWidget(chipX, HEADER_Y + 1, chipW, HEADER_H - 2,
                                new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(label)),
                                data -> selectMode(data, target));
                        chip.setHoverTooltips(Component.translatable(
                                "gtlmodular.pattern_assembly.mode_scale.chip.tip", modeName(i)));
                        chipWidgets.add(chip);
                        group.addWidget(chip);
                        {
                            group.addWidget(chipUnderline(chipX, chipW).setVisible(isActive));
                        }
                    }
                }
            }

            group.addWidget(new ImageWidget(BAND_X, BAND_MODE_Y, BAND_W, BAND_MODE_H,
                    new ColorRectTexture(BAND_BG)));
            group.addWidget(new ImageWidget(BAND_X, BAND_MODE_Y, ACCENT_W, BAND_MODE_H,
                    new ColorRectTexture(BAND_ACCENT)));
            LabelWidget bandModeHeader = new LabelWidget(BAND_TITLE_X, BAND_MODE_TITLE_Y,
                    Component.translatable("gtlmodular.pattern_assembly.mode_scale.section.mode").getString());
            group.addWidget(bandModeHeader);
            // 单配方类型没有可指定模式，隐藏空的槽位指定栏以免误导。

            LabelWidget slotLabel = new LabelWidget(BAND_TEXT_X, BAND_MODE_ROW_Y + ROW_TEXT_OFFSET,
                    Component.translatable("gtlmodular.pattern_assembly.mode_scale.slot.label").getString());
            Widget slotInput = new IntInputWidget(SLOT_INPUT_X, BAND_MODE_ROW_Y, SLOT_INPUT_W, ROW_H,
                    machine::getManualAssignSlot, machine::setManualAssignSlot)
                    .setMin(1).setMax(machine.getCatalystSlotCount())
                    .setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.mode.manual_slot"));
            group.addWidget(slotLabel);
            group.addWidget(slotInput);

            Widget assignBtn = PatternConfiguratorWidgets.manualActionButton(
                    SLOT_ASSIGN_X, BAND_MODE_ROW_Y, SLOT_BTN_W, ROW_H, false,
                    () -> {
                        if (!machine.isRemote()) {
                            machine.assignManualType(machine.getManualAssignSlot(), false);
                        }
                    });
            Widget clearBtn = PatternConfiguratorWidgets.manualActionButton(
                    SLOT_CLEAR_X, BAND_MODE_ROW_Y, SLOT_BTN_W, ROW_H, true,
                    () -> {
                        if (!machine.isRemote()) {
                            machine.assignManualType(machine.getManualAssignSlot(), true);
                        }
                    });
            group.addWidget(assignBtn);
            group.addWidget(clearBtn);

            LabelWidget stateLabel = new LabelWidget(BAND_TEXT_X, BAND_MODE_STATE_Y,
                    () -> PatternConfiguratorWidgets.assignStateText(machine.getManualAssignTypeId()));
            group.addWidget(stateLabel);

            group.addWidget(new ImageWidget(BAND_X, BAND_SCALE_Y, BAND_W, BAND_SCALE_H,
                    new ColorRectTexture(BAND_BG)));
            group.addWidget(new ImageWidget(BAND_X, BAND_SCALE_Y, ACCENT_W, BAND_SCALE_H,
                    new ColorRectTexture(BAND_ACCENT)));
            group.addWidget(new LabelWidget(BAND_TITLE_X, BAND_SCALE_TITLE_Y,
                    Component.translatable("gtlmodular.pattern_assembly.scale.title").getString()));
            group.addWidget(new LabelWidget(58, BAND_SCALE_TITLE_Y,
                    Component.translatable("gtlmodular.pattern_assembly.mode_scale.scale_hint").getString()));

            group.addWidget(new LabelWidget(8, BAND_SCALE_LABEL_Y,
                    Component.translatable("gtlmodular.pattern_assembly.scale.multiply.label").getString()));
            group.addWidget(PatternConfiguratorWidgets.scaleInput(8, BAND_SCALE_ROW_Y, 48, ROW_H,
                    machine::getPatternScale, machine::setPatternScale, MIN_SCALE, MAX_SCALE));
            group.addWidget(new LabelWidget(60, BAND_SCALE_LABEL_Y,
                    Component.translatable("gtlmodular.pattern_assembly.scale.divide.label").getString()));
            group.addWidget(PatternConfiguratorWidgets.scaleInput(60, BAND_SCALE_ROW_Y, 48, ROW_H,
                    machine::getPatternDivScale, machine::setPatternDivScale, MIN_SCALE, MAX_SCALE));
            group.addWidget(PatternConfiguratorWidgets.applyButton(112, BAND_SCALE_ROW_Y, 50, ROW_H,
                    machine::invalidateRewriteCache));

            group.addWidget(PatternConfiguratorWidgets.foaToggle(FOA_TOGGLE_X, BAND_SCALE_FOA_Y,
                    FOA_TOGGLE_W, FOA_ROW_H, machine::isFoaModeEnabled, machine::setFoaModeEnabled));
            group.addWidget(new LabelWidget(FOA_LABEL_X, BAND_SCALE_FOA_Y + FOA_TEXT_OFFSET,
                    Component.translatable("gtlmodular.pattern_assembly.mode_scale.foa.label").getString()));
            group.addWidget(new LabelWidget(FOA_STATE_X, BAND_SCALE_FOA_Y + FOA_TEXT_OFFSET, this::foaStateText));
            group.addWidget(PatternConfiguratorWidgets.scaleInput(FOA_INPUT_X, BAND_SCALE_FOA_Y,
                    FOA_INPUT_W, FOA_ROW_H,
                    machine::getFoaPatternOutputMultiplier, machine::setFoaPatternOutputMultiplier,
                    MIN_FOA_MULTIPLIER, MAX_FOA_MULTIPLIER)
                    .setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.mode_scale.foa.multiplier.tip")));

            if (!multi) {
                bandModeHeader.setVisible(false);
                slotLabel.setVisible(false);
                slotInput.setVisible(false);
                assignBtn.setVisible(false);
                clearBtn.setVisible(false);
                stateLabel.setVisible(false);
            }
            return group;
        }

        /** 选中条只作视觉提示，必须禁用点击，避免遮住芯片按钮。 */
        private static Widget chipUnderline(int x, int w) {
            ImageWidget underline = new ImageWidget(x, HEADER_Y + HEADER_H - 3, w, 2,
                    new ColorRectTexture(BAND_ACCENT));
            underline.setActive(false);
            return underline;
        }

        private String foaStateText() {
            boolean on = machine.isFoaModeEnabled();
            return Component.translatable(on
                    ? "gtlmodular.pattern_assembly.mode_scale.foa.state.on"
                    : "gtlmodular.pattern_assembly.mode_scale.foa.state.off").getString();
        }

        /** 游标按钮点击会投递到两侧，只由服务端推进，避免重复切换。 */
        private void cycle(com.lowdragmc.lowdraglib.gui.util.ClickData data, boolean forward) {
            if (machine.isRemote()) {
                return;
            }
            int times = data.isShiftClick ? 5 : 1;
            for (int i = 0; i < times; i++) {
                machine.cycleRecipeType(forward);
            }
        }

        /** 芯片点击先给客户端即时反馈，服务端也更新权威游标，避免单侧早退丢失操作。 */
        private void selectMode(com.lowdragmc.lowdraglib.gui.util.ClickData data, int target) {
            List<GTRecipeType> types = machine.getAvailableRecipeTypes();
            if (types.size() <= 1 || target < 0 || target >= types.size()) {
                return;
            }
            if (machine.isRemote()) {
                machine.applyModeCursorLocally(target);
                machine.applyModeCursorLocally(target);
        refreshChipLabels();
            } else {
                machine.setRecipeType(target);
            }
        }

        private String modeName(int index) {
            return PatternConfiguratorWidgets.modeName(machine.getAvailableRecipeTypes(), index);
        }

        private String modeNameLabel() {
            List<GTRecipeType> types = machine.getAvailableRecipeTypes();
            if (types.size() <= 1) {
                return "§8" + Component.translatable("gtlmodular.pattern_assembly.mode.none").getString();
            }
            int index = Math.max(0, Math.min(types.size() - 1, machine.getModeCursorIndex()));
            return "§b§l" + modeName(index);
        }

        private String modeIndexLabel() {
            List<GTRecipeType> types = machine.getAvailableRecipeTypes();
            if (types.size() <= 1) {
                return "";
            }
            int index = Math.max(0, Math.min(types.size() - 1, machine.getModeCursorIndex()));
            return "§8" + (index + 1) + "/" + types.size();
        }
    }

    static final class CatalystAccessConfigurator implements IFancyConfigurator {

        private final MoltenPatternAssemblyPartMachine machine;

        private int shownIndex = -1;

        private WidgetGroup root;
        private MEPatternCatalystUIManager catalystUI;
        private CatalystPageSync pageSync;

        private ConfiguratorPanel panel;
        private ConfiguratorPanel.Tab tab;

        private static final int ITEM_SLOTS = 9;
        private static final int FLUID_TANKS = 9;
        private static final long TANK_CAPACITY = 16L * FluidHelper.getBucket();

        /** 固定面板尺寸，避免展开后顶出屏幕或触发重复定位。 */
        private static final int PAGE_WIDTH = 202;
        private static final int PAGE_CLOSED_HEIGHT = 44;
        private static final int PAGE_OPEN_HEIGHT = 128;

        CatalystAccessConfigurator(MoltenPatternAssemblyPartMachine machine) {
            this.machine = machine;
        }

        void bindPanel(ConfiguratorPanel panel, ConfiguratorPanel.Tab tab) {
            this.panel = panel;
            this.tab = tab;
        }

        private int slotCount() {
            return machine.getCatalystSlotCount();
        }

        private int currentViewIndex() {
            return Math.max(0, machine.getCatalystViewSlot() - 1);
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.pattern_assembly.catalyst.title");
        }

        @Override
        public IGuiTexture getIcon() {
            return new ItemStackTexture(machine.getDefinition().asStack());
        }

        @Override
        public List<Component> getTooltips() {
            return List.of(Component.translatable("gtlmodular.pattern_assembly.catalyst.title"));
        }

        @Override
        public Widget createConfigurator() {
            int slots = slotCount();
            shownIndex = -1;
            root = new WidgetGroup(0, 0, PAGE_WIDTH, PAGE_CLOSED_HEIGHT) {

                @Override
                public void updateScreen() {
                    super.updateScreen();
                    followMachineState();
                }

                @Override
                public void detectAndSendChanges() {
                    super.detectAndSendChanges();
                    followMachineState();
                }
            };
            root.addWidget(new LabelWidget(6, 4,
                    Component.translatable("gtlmodular.pattern_assembly.catalyst.slot").getString()));
            root.addWidget(new IntInputWidget(6, 18, 74, 20,
                    machine::getCatalystViewSlot, machine::setCatalystViewSlot).setMin(1).setMax(slots));
            root.addWidget(new ButtonWidget(86, 18, 110, 20,
                    PatternCatalystWidgets.buttonTexture(machine::isCatalystPageOpen, false), data -> toggle())
                    .setHoverTexture(PatternCatalystWidgets.buttonTexture(machine::isCatalystPageOpen, true)));

            // 页面控件只建一次：切槽只换代理指向，控件结构永不变化，菜单槽号也就不会错位。
            this.pageSync = new CatalystPageSync(machine, ITEM_SLOTS, FLUID_TANKS, TANK_CAPACITY,
                    DEFAULT_CACHE_RECIPE_COUNT, "超级猪咪催化剂页");
            pageSync.setTarget(currentViewIndex());
            catalystUI = new MEPatternCatalystUIManager(0,
                    pageSync.itemTransfers(),
                    pageSync.fluidTransfers(),
                    pageSync.cacheCountBuffer(),
                    pageSync.cacheCountListener());
            catalystUI.setSelfPosition(2, 42);
            catalystUI.setSize(PAGE_WIDTH - 8, PAGE_OPEN_HEIGHT - 48);
            catalystUI.setVisible(false);
            catalystUI.setActive(false);
            root.waitToAdded(catalystUI);
            catalystUI.toggleFor(0);
            layoutSideBySide();
            followMachineState();
            return root;
        }

        private void toggle() {
            machine.setCatalystPageOpen(!machine.isCatalystPageOpen());
        }

        void openSlotFromPattern(int slotIndex) {
            PatternCatalystWidgets.openSlotFromPattern(slotIndex, machine::setCatalystViewSlot,
                    () -> machine.setCatalystPageOpen(true), () -> panel, () -> tab);
        }

        private void followMachineState() {
            logPageGeometry("催化剂页@" + machine.getPos().toShortString(), root, panel, tab);
            if (catalystUI == null) {
                return;
            }
            if (!machine.isCatalystPageOpen()) {
                if (catalystUI.isVisible()) {
                    catalystUI.setVisible(false);
                    catalystUI.setActive(false);
                }
                if (root != null && root.getSizeHeight() != PAGE_CLOSED_HEIGHT) {
                    root.setSize(PAGE_WIDTH, PAGE_CLOSED_HEIGHT);
                }
                return;
            }
            int index = currentViewIndex();
            if (index != shownIndex) {
                shownIndex = index;
                pageSync.setTarget(index);
            }
            if (!catalystUI.isVisible()) {
                catalystUI.setVisible(true);
                catalystUI.setActive(true);
            }
            if (root != null && root.getSizeHeight() != PAGE_OPEN_HEIGHT) {
                root.setSize(PAGE_WIDTH, PAGE_OPEN_HEIGHT);
            }
            if (machine.isRemote()) {
                pageSync.apply(machine.getCatalystViewPayload());
            } else {
                String payload = pageSync.publishIfChanged();
                if (payload != null) {
                    machine.catalystViewPayload = payload;
                }
            }
        }

        private void layoutSideBySide() {
            if (catalystUI == null) {
                return;
            }
            PatternCatalystWidgets.layoutSideBySide(catalystUI, root, PAGE_WIDTH, PAGE_OPEN_HEIGHT, false);
        }
    }

    public static String tierName(int tier) {
        return switch (tier) {
            case 2 -> "MV";
            case 3 -> "HV";
            case 4 -> "EV";
            case 5 -> "IV";
            case 6 -> "LuV";
            case 7 -> "ZPM";
            case 8 -> "UV";
            case 9 -> "UHV";
            case 10 -> "UEV";
            case 11 -> "UIV";
            default -> String.valueOf(tier);
        };
    }

    static final class ArchiveConfigurator extends PatternArchiveConfigurator {
        ArchiveConfigurator(MoltenPatternAssemblyPartMachine machine) {
            super(machine, Layout.MOLTEN, (root, panel, tab) ->
                    logPageGeometry("归档页@" + machine.getPos().toShortString(), root, panel, tab));
        }
    }
}
