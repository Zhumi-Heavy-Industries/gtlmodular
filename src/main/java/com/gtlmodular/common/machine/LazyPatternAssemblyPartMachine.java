package com.gtlmodular.common.machine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
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
import com.gtlmodular.common.machine.pattern.ui.PatternCatalystWidgets;
import com.gtlmodular.common.machine.pattern.ui.PatternArchiveConfigurator;
import com.gtlmodular.common.machine.pattern.ui.PatternConfiguratorWidgets;
import com.gtlmodular.common.machine.pattern.ui.PatternSlotRightClickOverlay;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.AETextInputButtonWidget;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.misc.FluidStorage;
import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.fluid.FluidHelper;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import com.lowdragmc.lowdraglib.side.item.ItemTransferHelper;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.syncdata.managed.IRef;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.api.gui.MEPatternCatalystUIManager;
import org.gtlcore.gtlcore.api.gui.PatternCircuitConfigurator;
import org.gtlcore.gtlcore.api.machine.trait.IMERecipeHandlerTrait;
// 依赖只提供 MEPart 的嵌套类型，直接导入外层会使 javac 找不到符号。
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEIOTrait;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternTrait;
import org.gtlcore.gtlcore.common.data.GTLItems;
import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;
import org.gtlcore.gtlcore.common.machine.multiblock.part.PaginationUIManager;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEIOPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferRecipeHandlerTraitBase;
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.integration.ae2.handler.MEBufferPatternHelper;

import appeng.crafting.pattern.ProcessingPatternItem;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMaps;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 按需实体化槽和催化剂仓，避免空仓预建全部对象；动态容量仍须覆盖所有存档样板和槽状态。 */
public class LazyPatternAssemblyPartMachine extends MEPatternBufferPartMachineBase
        implements PatternArchiveConfigurator.Host {

    public static final int RESERVE = 2;

    /** 基类遍历成本随容量增长，起步仅分配一档，批量写入再一次扩到所需容量。 */
    public static final int INITIAL_CAPACITY = 32;

    public static final int MIN_GROW_STEP = 16;

    public static final int PATTERNS_PER_ROW = 16;

    public static final int ROWS_PER_PAGE = 8;

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(LazyPatternAssemblyPartMachine.class,
                    MEPatternBufferPartMachineBase.MANAGED_FIELD_HOLDER);

    /** 通知方法不在公开接口上，缓存反射入口以唤醒主机；失败时仍可退回主机轮询。 */
    private static final Method NOTIFY_LISTENERS = resolveNotifyListeners();

    private static Method resolveNotifyListeners() {
        try {
            Method method = Class.forName(
                            "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.NotifiableMERecipeHandlerTrait")
                    .getMethod("notifyListeners");
            method.setAccessible(true);
            return method;
        } catch (Throwable failure) {
            // 探测：通知入口随上游版本可能缺失，此处仅解析方法，缺失时保留主机轮询退路。
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：拿不到 notifyListeners() ⇒ 缓冲内容变化将不通知主机配方逻辑"
                    + "（主机会退回自行轮询）。原因: {}", failure.toString());
            return null;
        }
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    private final int tier;

    private final int maxPatternCount;

    /** 字段名决定样板存档键，不能改名；扩容保留仓对象，避免托管字段引用失效。 */
    @Persisted
    private final ItemStackTransfer patternInventory;

    private final List<InternalSlot> internalInventory = new ArrayList<>();

    private final InternalInventory terminalPatternInventory = new InternalInventory() {

        @Override
        public int size() {
            return LazyPatternAssemblyPartMachine.this.getCapacity();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return LazyPatternAssemblyPartMachine.this.patternInventory.getStackInSlot(slot);
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            LazyPatternAssemblyPartMachine.this.patternInventory.setStackInSlot(slot, stack);
            // ItemStackTransfer.setStackInSlot 不触发内容回调，终端写入后必须主动通知。
            LazyPatternAssemblyPartMachine.this.onPatternInventoryChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return LazyPatternAssemblyPartMachine.this.patternInventory.isItemValid(slot, stack);
        }
    };

    /** 上游具体处理器的构造器只接受具体机器类，这里需要适配抽象基类的处理器。 */
    private final LazyPatternBufferRecipeHandler recipeHandler;

    private final Map<IPatternDetails, Integer> patternSlotMap = new LinkedHashMap<>();
    private boolean patternSlotMapDirty = true;

    private long lastUsageSignature;
    private boolean usageSignatureValid = false;
    private TickableSubscription usageSubscription;

    private long lastOrphanPushLog;

    private boolean loggedBaseSyncState;

    /** 上游默认名不代表玩家命名，自动命名需要识别它并主动让出玩家已锁定的名字。 */
    public static boolean enableActiveCustomNaming = true;

    /** 按模式注册 ID 保存名字，避免类型表下标或显示语言变化后串名。 */
    private final java.util.Map<String, String> modeNames = new java.util.LinkedHashMap<>();

    private String lockedName = "";

    private PatternNameController patternNameController;

    private static final int MAX_SAVED_MODE_NAMES = 64;

    private static final int MAX_SAVED_NAME_LENGTH = 128;

    private static final String NBT_MODE_NAMES = "gtlmodular$lazy_mode_names";

    private static final String NBT_LOCKED_NAME = "gtlmodular$lazy_locked_name";

    private int formedNudgeTicks = FormedRenderSyncNudge.WINDOW_TICKS;

    private final PatternClientSyncRefresh clientSyncRefresh = new PatternClientSyncRefresh();

    /** 副产物会参与样板身份识别，不能为了识别方便随意改变此默认值。 */
    @Persisted
    private boolean keepByProduct = false;

    /** 外部写仓不一定触发回调，需用逐槽快照识别样板替换。 */
    private final List<ItemStack> patternSnapshot = new ArrayList<>();

    /** 读档首次只建立快照，避免把存档样板当作新替换而误退原料。 */
    private boolean patternSnapshotReady = false;

    private static final int AUTO_SWITCH_INTERVAL_TICKS = 20;
    private static final int AUTO_SWITCH_GRACE_TICKS = 40;
    private static final int AUTO_SWITCH_BLOCK_TICKS = 200;

    /** 空转宽限期容纳 AE 在途来料，避免刚切换就因尚未到料切走。 */
    private static final int AUTO_SWITCH_STOCKLESS_TICKS = 600;

    private static final int TYPE_NOT_TRIED = -1;
    private static final int TYPE_UNKNOWN = -2;

    private static final int MATCH_NONE = -2;

    public static boolean COLD_SLOT_CACHE_HEAL = true;

    /** 重识别需要扫描配方表，必须限频。 */
    private static final int COLD_SLOT_HEAL_INTERVAL_TICKS = 200;
    /** 限制总尝试量，避免无法识别的样板持续扫描配方表。 */
    private static final int COLD_SLOT_HEAL_BUDGET = 64;

    private static final String NBT_SLOT_RECIPE_TYPE_SPARSE = "gtlmodular$lazy_slot_recipe_type_sparse";
    private static final String NBT_MANUAL_SLOT_TYPES = "gtlmodular$lazy_manual_slot_types";

    private static final String NBT_BOUND_MODE_TYPE = "gtlmodular$lazy_bound_mode_type";

    @DescSynced
    private int activeRecipeTypeIndex = 0;

    /** 模式页游标与主机实际模式分离，避免主机同步覆盖玩家待指定的选择。 */
    @DescSynced
    private int modeCursorIndex = 0;

    private boolean modeCursorTouched = false;

    /** 玩家在模式页点过模式：自动切换与推料都不再改他的选择，直到重进存档。 */
    private boolean manualModePinned = false;

    /** 按注册 ID 持久化绑定，避免类型表重排后绑定到错误模式。 */
    private ResourceLocation boundModeType;

    /** 读档时空类型表可能只是控制器未就绪，不能据此清除有效绑定。 */
    private boolean boundModePendingResolve;

    /** 类型缓存随容量只增不减，避免扩容时丢掉已经识别的结果。 */
    private final List<Integer> slotRecipeTypes = new ArrayList<>();

    /** 手动指定存注册 ID，避免更换控制器或类型表重排后指错模式。 */
    private final Map<Integer, ResourceLocation> manualSlotTypes = new HashMap<>();

    @DescSynced
    private int manualAssignSlot = 1;

    @DescSynced
    private String manualAssignTypeId = "";

    @DescSynced
    private String manualAssignAutoTypeId = "";

    private long coldSlotHealNextTick;
    private final Set<Integer> coldSlotHealTried = new HashSet<>();
    private final Map<Integer, ItemStack> coldSlotHealPattern = new HashMap<>();
    private int coldSlotHealWarmed;

    private PatternAutoSwitch autoSwitch;

    private PatternAutoSwitch patternAutoSwitch() {
        if (autoSwitch == null) {
            autoSwitch = new PatternAutoSwitch(new AutoSwitchHost(), AUTO_SWITCH_INTERVAL_TICKS,
                    AUTO_SWITCH_GRACE_TICKS, AUTO_SWITCH_BLOCK_TICKS, AUTO_SWITCH_STOCKLESS_TICKS, false);
        }
        return autoSwitch;
    }

    private final class AutoSwitchHost implements PatternAutoSwitch.Host {

        @Override
        public IRecipeLogicMachine firstRecipeLogicController() { return LazyPatternAssemblyPartMachine.this.firstRecipeLogicController(); }

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
        public void clearObservedModeCaches(int previous, int live) {}

        @Override
        public int findModeWithWaitingStock(List<GTRecipeType> types, int active, long now) {
            return LazyPatternAssemblyPartMachine.this.findModeWithWaitingStock(types, active, now);
        }

        @Override
        public int recipeTypeOfSlot(int slot) { return LazyPatternAssemblyPartMachine.this.recipeTypeOfSlot(slot); }

        @Override
        public boolean hasRecipeCacheInSlot(int slot) { return LazyPatternAssemblyPartMachine.this.hasRecipeCacheInSlot(slot); }

        @Override
        public boolean hasSlotWaitingStock(int slot) { return LazyPatternAssemblyPartMachine.this.hasSlotWaitingStock(slot); }

        @Override
        public void logCheck(List<GTRecipeType> types, RecipeLogic logic, int active, int target, long now) {
            logAutoSwitchCheck(types, logic, active, target, now);
        }

        @Override
        public void clearAutoSwitchCaches(int previous, int target) { clearAllSlotRecipeCaches(target); }

        @Override
        public void syncCustomNameToMode(int index) { LazyPatternAssemblyPartMachine.this.syncCustomNameToMode(index); }

        @Override
        public void onAutoSwitched(int previous, int target) {
            GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪自动切换配方模式: {} -> {} pos={}",
                    previous, target, getPos());
        }

        @Override
        public boolean blocksPushModeSwitch(RecipeLogic logic) {
            // WAITING 的 NAC 仍可能持有已扣料批次，重置会丢失这些批次。
            return !ALLOW_MODE_SWITCH_WITH_RUNNING_BATCHES
                    && logic instanceof com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic threaded
                    && threaded.runningThreadCount() > 0;
        }

        @Override
        public void clearPushModeSwitchCaches(int slot, int index) { clearAllSlotRecipeCaches(index); }

        @Override
        public boolean modeHasWaitingStock(int modeIndex) {
            int slots = getPatternSlotCount();
            for (int slot = 0; slot < slots; slot++) {
                if (recipeTypeOfSlot(slot) == modeIndex && hasSlotWaitingStock(slot)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean manualModePinned() { return manualModePinned; }

        @Override
        public void pinManualMode() { manualModePinned = true; }
    }

    private void logAutoSwitchCheck(List<GTRecipeType> types, RecipeLogic logic, int active, int target, long now) {
        long idleTicks = patternAutoSwitch().idleTicks(now);
        String signature = active + "|" + target + "|" + logic.getStatus() + "|" + patternAutoSwitch().activePatterns()
                + "|" + patternAutoSwitch().activeStock() + "|" + patternAutoSwitch().activeCold() + "|" + (idleTicks / 100);
        if (!signature.equals(lastAutoSwitchSignature)) {
            lastAutoSwitchSignature = signature;
            GTLModular.LOGGER.info(
                    "[gtlmodular] 惰性猪咪自动切模式检查: 当前={}/{} 状态={} 找到目标={} 当前模式样板={} 其中有料={} 未认配方={} 空转={}tick（满 {}tick 才允许切走）",
                    active, types.size(), logic.getStatus(), target, patternAutoSwitch().activePatterns(), patternAutoSwitch().activeStock(),
                    patternAutoSwitch().activeCold(), idleTicks, AUTO_SWITCH_STOCKLESS_TICKS);
        }
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
        public void setRecipeType(int index) { LazyPatternAssemblyPartMachine.this.setRecipeType(index); }

        @Override
        public boolean isRemote() { return LazyPatternAssemblyPartMachine.this.isRemote(); }

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
        public void rememberBoundModeType() { LazyPatternAssemblyPartMachine.this.rememberBoundModeType(); }

        @Override
        public Map<Integer, ResourceLocation> manualSlotTypes() { return manualSlotTypes; }

        @Override
        public int manualTypeIndexOf(int slot, List<GTRecipeType> types) { return LazyPatternAssemblyPartMachine.this.manualTypeIndexOf(slot, types); }

        @Override
        public void prepareSlotRecipeTypes() { ensureRecipeTypeSlotCapacity(); }

        @Override
        public int slotRecipeTypeCount() { return slotRecipeTypes.size(); }

        @Override
        public int cachedSlotRecipeType(int slot) { return recipeTypeCacheAt(slot); }

        @Override
        public void cacheSlotRecipeType(int slot, int type) { setRecipeTypeCache(slot, type); }

        @Override
        public int detectRecipeType(int slot, List<GTRecipeType> types) { return LazyPatternAssemblyPartMachine.this.detectRecipeType(slot, types); }

        @Override
        public int manualAssignmentSlotCount() { return getCapacity(); }

        @Override
        public int maxPatternCount() { return maxPatternCount; }

        @Override
        public void invalidateManualAssignment(int slot) {
            setRecipeTypeCache(slot, TYPE_NOT_TRIED);
            clearSlotRecipeCache(slot);
        }

        @Override
        public void clearAutoSwitchBlocked() { patternAutoSwitch().clearBlocked(); }

        @Override
        public void refreshManualAssignDisplay() { LazyPatternAssemblyPartMachine.this.refreshManualAssignDisplay(); }

        @Override
        public void syncCustomNameToMode(int index) { LazyPatternAssemblyPartMachine.this.syncCustomNameToMode(index); }

        @Override
        public void pinManualMode() { manualModePinned = true; }
    }

    private String lastAutoSwitchSignature;

    private final Map<Integer, String> lastPushDiagnostic = new HashMap<>();

    public static final int MIN_FOA_MULTIPLIER = 1;
    public static final int MAX_FOA_MULTIPLIER = 30;
    public static final int DEFAULT_FOA_MULTIPLIER = 15;

    public static final int MIN_SCALE = 1;
    public static final int MAX_SCALE = 100;

    /** 由 AE 负责倍增时返回原样板，避免视图改写与上游倍增叠加。 */
    public static boolean PATTERN_REWRITE_DISABLED = true;

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

    private String lastQuerySignature;

    private long lastPushLogTick = -1000;

    private boolean machineScaleDisabledWarned;

    private static final int STUCK_LOG_INTERVAL_TICKS = 600;
    private static final int STUCK_WARNED_LIMIT = 64;
    private long lastStuckLogTick;
    private final Set<String> stuckWarned = new HashSet<>();

    private static final boolean STUCK_WARN_REPEAT_WHEN_TRACKED = true;

    private final Map<String, Long> stuckWarnedTick = new HashMap<>();

    /** 这些字段只含机器设置，掉落后仍须保留，避免搬机丢失倍率。 */
    private static final String NBT_PATTERN_SCALE = "gtlmodular$lazy_pattern_scale";
    private static final String NBT_PATTERN_DIV_SCALE = "gtlmodular$lazy_pattern_div_scale";
    private static final String NBT_FOA_MODE = "gtlmodular$lazy_foa_mode";
    private static final String NBT_FOA_MULTIPLIER = "gtlmodular$lazy_foa_multiplier";
    private static final String NBT_OUTPUT_MODE = "gtlmodular$lazy_output_mode";

    private static final int CATALYST_ITEM_SLOTS = 9;
    private static final int CATALYST_FLUID_TANKS = 9;
    private static final long CATALYST_TANK_CAPACITY = 16L * FluidHelper.getBucket();
    private static final int DEFAULT_CACHE_RECIPE_COUNT = 1;
    /** 阈值以 byte 保存，界面上限必须避免溢出为负数。 */
    private static final int MAX_CACHE_RECIPE_COUNT = 127;

    private static final String NBT_LAZY_CATALYST_SLOTS = "gtlmodular$lazy_catalyst_slots";
    private static final String NBT_CATALYST_ITEMS = "items";
    private static final String NBT_CATALYST_FLUIDS = "fluids";
    private static final String NBT_CATALYST_COUNT = "count";

    private final List<ItemStackTransfer> catalystItems = new ArrayList<>();
    private final List<FluidTransferList> catalystFluids = new ArrayList<>();
    private final List<Byte> cacheRecipeCount = new ArrayList<>();

    @DescSynced
    private int catalystViewSlot = 1;

    @DescSynced
    private boolean catalystPageOpen = false;

    /** 页面切换可能不改变槽内容，原版增量同步不会重发；用机器同步字段补齐当前查看槽的内容。 */
    @DescSynced
    private String catalystViewPayload = "";

    public String getCatalystViewPayload() {
        return catalystViewPayload;
    }

    /** 分页器两侧必须使用服务端容量，否则扩容后客户端会缺少新增槽位。 */
    @DescSynced
    private int syncedPatternCapacity = 0;

    public int getSyncedPatternCapacity() {
        return syncedPatternCapacity;
    }

    transient CatalystAccessConfigurator activeCatalystPage;

    public LazyPatternAssemblyPartMachine(IMachineBlockEntity holder, int tier, int maxPatterns) {
        super(holder, IO.BOTH);
        this.tier = tier;
        this.maxPatternCount = Math.max(RESERVE, maxPatterns);
        int initial = Math.max(RESERVE, Math.min(maxPatternCount, INITIAL_CAPACITY));
        this.patternInventory = new ItemStackTransfer(initial);
        this.patternInventory.setFilter(AEUtils.PROCESS_FILTER);
        this.patternInventory.setOnContentsChanged(this::onPatternInventoryChanged);
        for (int i = 0; i < initial; i++) {
            this.internalInventory.add(null);
            this.slotRecipeTypes.add(null);
        }
        this.recipeHandler = new LazyPatternBufferRecipeHandler(this, IO.BOTH);
        if (io == IO.BOTH) {
            getMainNode().addService(IGridTickable.class, new LazyBufferTicker());
        }
    }

    public int getMachineTier() {
        return tier;
    }

    public int getMaxPatternCount() {
        return maxPatternCount;
    }

    public int getCapacity() {
        ItemStackTransfer inventory = this.patternInventory;
        // 基类构造期间可能调用此入口，字段尚未赋值时必须容错。
        return inventory == null ? 0 : inventory.getSlots();
    }

    public void growCapacityFor(int needed) {
        int current = getCapacity();
        // 容量已经足够时不能再按步长增长，否则读档和普通内容变化也会不断扩容。
        if (needed <= current) {
            return;
        }
        // 单张写入按步长增长以减少扩容频次，批量写入则直接覆盖所需容量。
        int target = Math.min(maxPatternCount, Math.max(needed, current + MIN_GROW_STEP));
        if (target <= current) {
            return;
        }
        resizePatternInventory(target);
        while (internalInventory.size() < target) {
            internalInventory.add(null);
        }
        try {
            while (slotRecipeTypes.size() < target) {
                slotRecipeTypes.add(null);
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#growCapacityFor.recipeTypes", ignored);
        }
        patternSlotMapDirty = true;
        // 所有扩容入口在此汇合，发布权威容量才能让客户端分页槽数同步增长。
        publishPatternCapacity();
    }

    public void growCapacityForBatchInsert(int count) {
        if (count <= 0) {
            return;
        }
        growCapacityFor(highestUsedSlot() + 1 + count + RESERVE);
    }

    public void publishPatternCapacity() {
        if (isRemote()) {
            return;
        }
        try {
            int capacity = Math.min(getCapacity(), maxPatternCount);
            if (capacity == syncedPatternCapacity) {
                return;
            }
            syncedPatternCapacity = capacity;
            GTLModular.LOGGER.info("[gtlmodular] 惰性机样板页: 服务端发布视图 槽={} 容量={} 签名={}",
                    countNonEmptyPatterns(), capacity, patternViewSignature());
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#publishPatternCapacity", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性机样板页: 服务端发布容量失败（已忽略）pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    /** 客户端只补容量，不跑内容变化收尾，避免本地缓存反向影响服务端样板状态。 */
    public void applySyncedPatternCapacity() {
        if (!isRemote()) {
            return;
        }
        try {
            int published = syncedPatternCapacity;
            if (published <= 0) {
                return;
            }
            int target = Math.min(published, maxPatternCount);
            int local = getCapacity();
            if (target <= local) {
                return;
            }
            resizePatternInventory(target);
            GTLModular.LOGGER.info("[gtlmodular] 惰性机样板页: 客户端应用视图 槽={} 容量={} 本侧原容量={}",
                    countNonEmptyPatterns(), target, local);
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#applySyncedPatternCapacity", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性机样板页: 客户端应用容量失败（已忽略）pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    private int patternWidgetCapacity() {
        int local = getCapacity();
        if (isRemote()) {
            applySyncedPatternCapacity();
            local = getCapacity();
            int published = getSyncedPatternCapacity();
            if (published > local) {
                local = Math.min(published, maxPatternCount);
            }
        }
        return Math.max(PATTERNS_PER_ROW, Math.min(local, maxPatternCount));
    }

    public int countNonEmptyPatterns() {
        try {
            int count = 0;
            int capacity = Math.min(getCapacity(), maxPatternCount);
            for (int i = 0; i < capacity; i++) {
                if (!patternInventory.getStackInSlot(i).isEmpty()) {
                    count++;
                }
            }
            return count;
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#countNonEmptyPatterns", ignored);
            return -1;
        }
    }

    public String patternViewSignature() {
        try {
            StringBuilder builder = new StringBuilder(48);
            int capacity = Math.min(getCapacity(), maxPatternCount);
            int nonEmpty = 0;
            builder.append(capacity).append(':');
            StringBuilder slots = new StringBuilder(32);
            for (int i = 0; i < capacity; i++) {
                if (!patternInventory.getStackInSlot(i).isEmpty()) {
                    nonEmpty++;
                    if (slots.length() > 0) {
                        slots.append(',');
                    }
                    slots.append(i + 1);
                }
            }
            builder.append(nonEmpty).append(':').append(highestUsedSlot() + 1).append(':').append(slots);
            return builder.toString();
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#patternViewSignature", ignored);
            return "?";
        }
    }

    /** ItemStackTransfer.setSize 会清空内容，必须先备份再恢复；仓对象保持不变以保留托管引用。 */
    private void resizePatternInventory(int newCapacity) {
        int old = patternInventory.getSlots();
        if (newCapacity <= old) {
            return;
        }
        ItemStack[] backup = new ItemStack[old];
        for (int i = 0; i < old; i++) {
            backup[i] = patternInventory.getStackInSlot(i);
        }
        patternInventory.setSize(newCapacity);
        for (int i = 0; i < old; i++) {
            if (!backup[i].isEmpty()) {
                patternInventory.setStackInSlot(i, backup[i]);
            }
        }
    }

    /** 存档 Size 可能超过硬上限，需截断以避免基类遍历空槽引用；截断会丢弃越界样板，必须记录告警。 */
    private void clampCapacityToMax() {
        int capacity = patternInventory.getSlots();
        if (capacity <= maxPatternCount) {
            return;
        }
        ItemStack[] backup = new ItemStack[maxPatternCount];
        int dropped = 0;
        for (int i = 0; i < capacity; i++) {
            ItemStack stack = patternInventory.getStackInSlot(i);
            if (i < maxPatternCount) {
                backup[i] = stack;
            } else if (!stack.isEmpty()) {
                dropped++;
            }
        }
        patternInventory.setSize(maxPatternCount);
        for (int i = 0; i < maxPatternCount; i++) {
            if (!backup[i].isEmpty()) {
                patternInventory.setStackInSlot(i, backup[i]);
            }
        }
        while (internalInventory.size() > maxPatternCount) {
            internalInventory.remove(internalInventory.size() - 1);
        }
        GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：存档容量 {} 超过本机上限 {} ⇒ 已截断（丢弃 {} 张样板）pos={}",
                capacity, maxPatternCount, dropped, getPos());
        publishPatternCapacity();
    }

    public int highestUsedSlot() {
        for (int i = getCapacity() - 1; i >= 0; i--) {
            if (!patternInventory.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private int firstEmptySlot() {
        int capacity = getCapacity();
        for (int i = 0; i < capacity; i++) {
            if (patternInventory.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public int insertPattern(ItemStack pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return -1;
        }
        int slot = firstEmptySlot();
        if (slot < 0) {
            if (getCapacity() >= maxPatternCount) {
                return -1;
            }
            slot = getCapacity();
            growCapacityFor(slot + 1);
        }
        patternInventory.setStackInSlot(slot, pattern.copy());
        growCapacityFor(slot + 1 + RESERVE);
        onPatternInventoryChanged();
        return slot;
    }

    public int insertPatterns(List<ItemStack> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return 0;
        }
        growCapacityForBatchInsert(patterns.size());
        int inserted = 0;
        for (ItemStack stack : patterns) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int slot = firstEmptySlot();
            if (slot < 0) {
                if (getCapacity() >= maxPatternCount) {
                    break;
                }
                slot = getCapacity();
                growCapacityFor(slot + 1);
            }
            patternInventory.setStackInSlot(slot, stack.copy());
            inserted++;
        }
        growCapacityFor(highestUsedSlot() + 1 + RESERVE);
        onPatternInventoryChanged();
        return inserted;
    }

    @Override
    protected Integer getSlotIndexForPattern(IPatternDetails pattern) {
        if (pattern == null) {
            return null;
        }
        ensurePatternSlotMap();
        Integer index = patternSlotMap.get(pattern);
        if (index != null) {
            return index;
        }
        Level level = getLevel();
        if (level == null) {
            return null;
        }
        int capacity = getCapacity();
        for (int i = 0; i < capacity; i++) {
            ItemStack stack = patternInventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            IPatternDetails decoded = PatternDetailsHelper.decodePattern(stack, level);
            if (decoded != null && (decoded == pattern || decoded.equals(pattern))) {
                patternSlotMap.put(decoded, i);
                return i;
            }
        }
        return null;
    }

    @Override
    protected int getInternalSlotCount() {
        // 基类按槽数遍历并调用 InternalSlot.isActive，不能返回超过可实体化范围的数量。
        return Math.min(getCapacity(), maxPatternCount);
    }

    @Override
    protected InternalSlot getInternalSlot(int index) {
        if (index < 0) {
            return null;
        }
        if (index >= getCapacity()) {
            if (index >= maxPatternCount) {
                return null;
            }
            growCapacityFor(index + 1);
            if (index >= getCapacity()) {
                return null;
            }
        }
        // 托管仓与自定义数据的恢复顺序不可依赖，访问前需补齐槽表，避免高槽位无法恢复。
        int capacity = getCapacity();
        while (internalInventory.size() < capacity) {
            internalInventory.add(null);
        }
        if (internalInventory == null || index >= internalInventory.size()) {
            return null;
        }
        InternalSlot slot = internalInventory.get(index);
        if (slot == null) {
            slot = new LazyPatternBufferInternalSlot(index);
            // 槽收到原料后必须通知配方处理器，才能唤醒已休眠的主机。
            slot.setOnContentsChanged(this::notifyMERecipeHandlers);
            internalInventory.set(index, slot);
        }
        return slot;
    }

    private void notifyMERecipeHandlers() {
        Method notify = NOTIFY_LISTENERS;
        boolean fired = false;
        if (notify != null && recipeHandler != null) {
            try {
                notify.invoke(recipeHandler.getMeItemHandler());
                notify.invoke(recipeHandler.getMeFluidHandler());
                fired = true;
            } catch (Throwable ignored) {
                SwallowedErrors.report("LazyPatternAssemblyPartMachine#notifyMERecipeHandlers", ignored);
            }
        }
        if (!fired) {
            wakeHostRecipeLogic();
        }
    }

    private void wakeHostRecipeLogic() {
        try {
            for (IMultiController controller : getControllers()) {
                if (controller instanceof IRecipeLogicMachine logicMachine) {
                    RecipeLogic logic = logicMachine.getRecipeLogic();
                    if (logic != null) {
                        logic.updateTickSubscription();
                    }
                }
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#wakeHostRecipeLogic", ignored);
            // 唤醒失败时保留机器状态，交由主机自行轮询。
        }
    }

    @Override
    protected boolean hasPatternInSlot(int slot) {
        return slot >= 0 && slot < getCapacity() && !patternInventory.getStackInSlot(slot).isEmpty();
    }

    @Override
    public ItemStack getCircuitForRecipe(int slotIndex) {
        if (getInternalSlot(slotIndex) instanceof LazyPatternBufferInternalSlot slot) {
            slot.syncPatternCircuit();
        }
        return super.getCircuitForRecipe(slotIndex);
    }

    @Override
    protected boolean hasRecipeCacheInSlot(int slot) {
        LazyMEPatternTrait trait = (LazyMEPatternTrait) getMETrait();
        if (trait == null) {
            return false;
        }
        ObjectSet<GTRecipe> cached = trait.slotRecipes(slot);
        return cached != null && !cached.isEmpty() && cached.size() >= cacheRecipeCountAt(slot);
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
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#lookupNarrowingModeIndex", failure);
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
                this::manualTypeIndexOf, this::recipeTypeCacheAt, () -> lookupNarrowingModeIndex);
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
    public Pair<IMERecipeHandlerTrait<Ingredient, ItemStack>,
            IMERecipeHandlerTrait<FluidIngredient, FluidStack>> getMERecipeHandlerTraits() {
        return Pair.of(recipeHandler.getMeItemHandler(), recipeHandler.getMeFluidHandler());
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        IPatternDetails original = patternDetails;
        boolean aePushedOriginal = true;
        int mul = 1;
        int div = 1;
        boolean scalingApplies = false;
        boolean machineSideScaled = false;
        try {
            // 按对象身份反查 AE 推回的改写实例，数量变化后值相等判定未必能匹配原样板。
            original = rewriteCache.originalPattern(patternDetails);
            aePushedOriginal = original == patternDetails;
            mul = effectiveMul();
            div = effectiveDiv();
            scalingApplies = aePushedOriginal && isRewriteEnabled()
                    && inputHolder != null && (mul != 1 || div != 1);
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#pushPattern.originalPattern", ignored);
            original = patternDetails;
            aePushedOriginal = true;
            scalingApplies = false;
        }
        Level pushLevel = getLevel();

        // 网络可用量可能受合成 CPU 预留影响，此处只作诊断，不能凭这个读数拒收样板。
        try {
            boolean stockShort = false;
            String shortName = "";
            long shortNeed = 0;
            long shortHave = 0;
            AEKey shortKey = null;
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
                            } catch (Throwable ex) {
                                SwallowedErrors.reportDebug("LazyPatternAssemblyPartMachine#pushPattern.shortageName", ex);
                                shortName = entry.getKey().getId().toString();
                            }
                            break outer;
                        }
                    }
                }
            }
            if (stockShort && pushLevel != null) {
                long nowTick = pushLevel.getGameTime();
                long inFlight = 0;
                // 在途量可能短暂为零，还需查询单项和全局任务追踪状态，避免把正常空窗期当成死锁。
                boolean aeRequesting = false;
                boolean aeRequestingAny = false;
                try {
                    if (shortKey != null) {
                        inFlight = stockGrid.getCraftingService().getRequestedAmount(shortKey);
                        aeRequesting = stockGrid.getCraftingService().isRequesting(shortKey);
                        aeRequestingAny = stockGrid.getCraftingService().isRequestingAny();
                    }
                } catch (Throwable ignored) {
                    SwallowedErrors.report("LazyPatternAssemblyPartMachine#pushPattern.craftingRequests", ignored);
                    // 查询失败时不伪造在途量或任务追踪读数。
                }
                boolean trueDeadlock = shortHave <= 0 && inFlight <= 0 && aeRequestingAny;
                String stuckId = shortKey == null ? "?" : String.valueOf(shortKey.getId());
                // 任务仍在追踪时允许限频复报，避免首条诊断永久遮住后续状态。
                boolean firstTimeForId = stuckWarned.size() < STUCK_WARNED_LIMIT && stuckWarned.add(stuckId);
                boolean repeatAllowed = STUCK_WARN_REPEAT_WHEN_TRACKED && !trueDeadlock
                        && nowTick - stuckWarnedTick.getOrDefault(stuckId, Long.MIN_VALUE) >= STUCK_LOG_INTERVAL_TICKS;
                if (nowTick - lastStuckLogTick >= STUCK_LOG_INTERVAL_TICKS
                        && (!(shortHave <= 0 && inFlight <= 0) || firstTimeForId || repeatAllowed)) {
                    lastStuckLogTick = nowTick;
                    stuckWarnedTick.put(stuckId, nowTick);
                    if (shortHave > 0) {
                        GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪提示：{} 存量不足（网络可用={}，在途={}，"
                                        + "AE追踪本件={}，AE全局有任务={}，本次需要={}）@{}（本条 30 秒最多一条）",
                                shortName, shortHave, inFlight, aeRequesting, aeRequestingAny, shortNeed, getPos());
                    } else if (inFlight > 0) {
                        GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪提示：{} 还没进网络（可用=0，**在合成中={}**，"
                                        + "AE追踪本件={}，本次需要={}）@{} —— 上游还在做，等它就好（本条 30 秒最多一条）",
                                shortName, inFlight, aeRequesting, shortNeed, getPos());
                    } else if (aeRequesting || trueDeadlock) {
                        GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪提示：{} **料暂时取不到：网络可用=0、在途=0，"
                                        + "但 AE 侧仍有合成任务**（AE追踪本件={}，AE全局有任务={}，本次需要={}）@{} —— "
                                        + "这是「料已交付、还没被消费/产出」的**空窗期**：请等下一轮 `AE 推送样板`"
                                        + "（对照 `累计推来` 看是不是刚被吃光），**不要**「全部取消」"
                                        + "（那对空窗期没有帮助，只会把已排好的任务丢掉）。（本条 30 秒最多一条）",
                                shortName, aeRequesting, aeRequestingAny, shortNeed, getPos());
                    } else {
                        GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪提示：{} **被占用，且整个 AE 当前没有任何手工合成任务**"
                                        + "（网络可用=0，在途=0，AE追踪本件={}，AE全局有任务={}，本次需要={}）@{} —— "
                                        + "这是真占用：料很可能卡在本机内部槽里（推来后没被消费掉）。"
                                        + "可先在 AE 终端「全部取消」再重新下单；若反复出现，请把本行与同一时刻的 "
                                        + "`AE 推送样板` / `累计推来` 两行一起留档。（每个物品只提醒一次）",
                                shortName, aeRequesting, aeRequestingAny, shortNeed, getPos());
                    }
                }
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#pushPattern.stockDiagnostic", ignored);
        }

        // AE 声明的推入量必须与机器消费量对应，不能在机器侧再次放大 inputHolder。
        machineSideScaled = false;

        try {
            if (scalingApplies && !this.machineScaleDisabledWarned) {
                this.machineScaleDisabledWarned = true;
                GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪样板总成 @{}：AE 推送的是【原样板】而机器侧二次放大"
                                + "已撤销 ⇒ 本次按 AE 声明量执行，倍率/神锻未在机器侧生效（有效缩放[{}]）。"
                                + "这是与 AE 一致的回落，不是故障。",
                        getPos(), describeEffectiveScale());
            }
            if (GTLModularConfig.isPatternDebugLogsEnabled()
                    && pushLevel != null && pushLevel.getGameTime() - lastPushLogTick >= 20) {
                lastPushLogTick = pushLevel.getGameTime();
                long pushed = 0;
                StringBuilder detail = new StringBuilder();
                int shown = 0;
                if (inputHolder != null) {
                    for (KeyCounter c : inputHolder) {
                        if (c == null) {
                            continue;
                        }
                        for (var entry : c) {
                            pushed += entry.getLongValue();
                            if (shown++ < 5) {
                                detail.append(' ').append(entry.getKey().getId()).append('=')
                                        .append(entry.getLongValue());
                            }
                        }
                    }
                }
                GTLModular.LOGGER.info(
                        "[gtlmodular] 惰性猪咪 AE 推送样板 @{}: 是否缩放样板={} 机器侧放大={} 输入总量={} 有效缩放[{}] 明细:{}",
                        getPos(), !aePushedOriginal, machineSideScaled, pushed, describeEffectiveScale(), detail);
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#pushPattern.inputDiagnostic", ignored);
            // 诊断日志不能影响样板推送。
        }

        int diagSlot = -1;
        try {
            Integer slot = getSlotIndexForPattern(original);
            if (slot != null && slot >= 0) {
                diagSlot = slot;
                lockRecipeTypeForSlot(slot);
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#pushPattern.lockRecipeType", ignored);
            // 锁模式失败不能中断原本的样板推送。
        }
        try {
            if (diagSlot >= 0) {
                logPushDiagnostic(diagSlot);
            } else {
                long now = getLevel() == null ? 0L : getLevel().getGameTime();
                if (now - lastOrphanPushLog >= 100L) {
                    lastOrphanPushLog = now;
                    GTLModular.LOGGER.warn(
                            "[gtlmodular] 惰性猪咪收到一张本机样板仓里没有的样板推送（AE 侧样板表可能已过期）pos={} 样板={}",
                            getPos(), original == null ? "?" : String.valueOf(original));
                }
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#pushPattern.slotDiagnostic", ignored);
            // 诊断日志不能影响样板推送。
        }
        // 父类的槽映射和扣料以原样板为准，改写实例只供 AE 展示。
        return super.pushPattern(original, inputHolder);
    }

    private void logPushDiagnostic(int slot) {
        if (isRemote()) {
            return;
        }
        List<GTRecipeType> types = getAvailableRecipeTypes();
        int manual = manualTypeIndexOf(slot, types);
        int auto = autoDetectTypeOfSlot(slot);
        IRecipeLogicMachine controller = firstRecipeLogicController();
        int live = controller == null ? -1 : controller.getActiveRecipeType();
        int locked = recipeTypeOfSlot(slot);
        String signature = slot + "|" + manual + "|" + auto + "|" + live + "|" + locked;
        if (signature.equals(lastPushDiagnostic.get(slot))) {
            return;
        }
        lastPushDiagnostic.put(slot, signature);
        GTLModular.LOGGER.info(
                "[gtlmodular] 推样板诊断: 槽={} 样板={} 手动指定={} 自检={} 主机模式={} 实际锁定={}{}",
                slot + 1, patternItemName(slot), typeIdName(manual, types), typeIdName(auto, types),
                typeIdName(live, types), typeIdName(locked, types),
                manual >= 0 && auto >= 0 && manual != auto
                        ? " ★冲突：手动指定与自检不一致（主机可能被锁到错模式、按错配方吃掉这批料）"
                        : "");
    }

    private String patternItemName(int slot) {
        try {
            ItemStack stack = patternInventory.getStackInSlot(slot);
            return stack.isEmpty() ? "(空)" : stack.getHoverName().getString();
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#patternItemName", ignored);
            return "?";
        }
    }

    private static String typeIdName(int index, List<GTRecipeType> types) {
        if (index < 0 || index >= types.size()) {
            return "-";
        }
        GTRecipeType type = types.get(index);
        if (type == null || type.registryName == null) {
            return "?";
        }
        return type.registryName.toString();
    }

    private List<IPatternDetails> baseAvailablePatterns() {
        ensurePatternSlotMap();
        return new ArrayList<>(patternSlotMap.keySet());
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        if (PATTERN_REWRITE_DISABLED) {
            return baseAvailablePatterns();
        }
        return rewriteCache.getAvailablePatterns(this::baseAvailablePatterns,
                this::invalidateRewriteCacheQuietly, this::logUnrewrittenPatterns, this::rebuildRewriteCache);
    }

    private void logUnrewrittenPatterns(List<IPatternDetails> base) {
        String sig = base.size() + "|" + slotCount() + "|" + rewriteCache.isRewriteEnabled() + "|" + patternScale + "|"
                + patternDivScale + "|" + foaMode + "|" + isOutputMode;
        if (!sig.equals(lastQuerySignature)) {
            lastQuerySignature = sig;
            int patterns = 0;
            for (int i = 0; i < getCapacity(); i++) {
                if (!patternInventory.getStackInSlot(i).isEmpty()) {
                    patterns++;
                }
            }
            if (base.size() > 0 || patterns > 0) {
                GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪 取样板(未改写): @{} {} 原始={} 容量槽={} 仓内样板={} 开关={} 倍率={} 除率={} 神锻={}({}) 输出模式={}",
                        getLevel() == null ? "?" : getLevel().dimension().location(), getPos(), base.size(),
                        slotCount(), patterns, rewriteCache.isRewriteEnabled(), patternScale, patternDivScale, foaMode,
                        foaMultiplier, isOutputMode);
            }
        }
    }

    private int slotCount() {
        return getCapacity();
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
        invalidateRewriteCacheQuietly();
        refreshPatternsNow();
    }

    /** 读取热路径和批量内容变化另有通知入口，此处静默失效以避免重入或重复刷新。 */
    private void invalidateRewriteCacheQuietly() {
        rewriteCache.invalidate();
    }

    private void rebuildRewriteCache(List<IPatternDetails> base) {
        int changedCount = rewriteCache.rebuild(base, this::rewritePattern);
        GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪 缩放缓存重建 @{}: 样板={} 已改写={} 有效缩放[{}] 倍率={} 除率={} 神锻={}({})",
                getPos(), base.size(), changedCount, describeEffectiveScale(), patternScale, patternDivScale,
                foaMode, foaMultiplier);
    }

    private IPatternDetails rewritePattern(IPatternDetails details) {
        return rewriteCache.rewritePattern(details, this::getLevel);
    }

    public int getPatternScale() {
        return patternScale;
    }

    /** 值不变时不触发 AE 刷新，避免界面读写回声放大成整仓重算。 */
    public void setPatternScale(int scale) {
        int next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        if (next == this.patternScale) {
            return;
        }
        GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪 缩放设置: 倍率 {} -> {}（{}）",
                this.patternScale, next, isRemote() ? "客户端" : "服务端");
        this.patternScale = next;
        invalidateRewriteCache();
    }

    public int getPatternDivScale() {
        return patternDivScale;
    }

    public void setPatternDivScale(int scale) {
        int next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        if (next == this.patternDivScale) {
            return;
        }
        GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪 缩放设置: 除率 {} -> {}（{}）",
                this.patternDivScale, next, isRemote() ? "客户端" : "服务端");
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
        GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪 缩放设置: 输出模式={}（{}，已废弃：不再影响缩放）",
                isOutputMode, isRemote() ? "客户端" : "服务端");
    }

    public boolean isFoaModeEnabled() {
        return foaMode;
    }

    public void setFoaModeEnabled(boolean enabled) {
        if (enabled == this.foaMode) {
            return;
        }
        GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪 缩放设置: 神锻模式={}（{}）",
                enabled, isRemote() ? "客户端" : "服务端");
        this.foaMode = enabled;
        invalidateRewriteCache();
    }

    public int getFoaPatternOutputMultiplier() {
        return foaMultiplier;
    }

    public void setFoaPatternOutputMultiplier(int multiplier) {
        int next = Math.max(MIN_FOA_MULTIPLIER, Math.min(MAX_FOA_MULTIPLIER, multiplier));
        if (next == this.foaMultiplier) {
            return;
        }
        this.foaMultiplier = next;
        invalidateRewriteCache();
    }

    @Override
    public appeng.api.inventories.InternalInventory getTerminalPatternInventory() {
        return terminalPatternInventory;
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        MachineDefinition definition = getDefinition();
        Component name = Component.translatable(definition.getDescriptionId());
        String custom = getCustomName();
        if (custom != null && !custom.isEmpty()) {
            name = Component.literal(custom);
        }
        return new PatternContainerGroup(AEItemKey.of(definition.asStack()), name, Collections.emptyList());
    }

    /** 解码整仓样板有成本，映射只在脏标记置位后重建。 */
    private void ensurePatternSlotMap() {
        if (!patternSlotMapDirty) {
            return;
        }
        patternSlotMapDirty = false;
        patternSlotMap.clear();
        Level level = getLevel();
        if (level == null) {
            return;
        }
        int capacity = getCapacity();
        for (int i = 0; i < capacity; i++) {
            ItemStack stack = patternInventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            IPatternDetails details = PatternDetailsHelper.decodePattern(stack, level);
            if (details != null) {
                patternSlotMap.putIfAbsent(details, i);
            }
        }
    }

    public static class LazyPatternBufferRecipeHandler extends MEPatternBufferRecipeHandlerTraitBase {

        /** MachineTrait 没有提供 holder，处理器子类需要自己的字段容器。 */
        protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
                new ManagedFieldHolder(LazyPatternBufferRecipeHandler.class);

        @Override
        public ManagedFieldHolder getFieldHolder() {
            return MANAGED_FIELD_HOLDER;
        }

        public LazyPatternBufferRecipeHandler(MEPatternBufferPartMachineBase machine, IO io) {
            super(machine, io);
        }

        @Override
        protected MEItemInputHandlerBase createMEItemHandler(IO io) {
            return new GatedItemHandler(getMachine(), io);
        }

        @Override
        protected MEFluidHandlerBase createMEFluidHandler(IO io) {
            return new GatedFluidHandler(getMachine(), io);
        }

        static final class GatedItemHandler extends MEItemInputHandlerBase {

            private final LazyPatternAssemblyPartMachine machine;

            GatedItemHandler(MEPatternBufferPartMachineBase machine, IO io) {
                super(machine, io);
                this.machine = machine instanceof LazyPatternAssemblyPartMachine lazy ? lazy : null;
            }

            @Override
            public boolean meHandleRecipeInner(GTRecipe recipe, Object2LongMap<Ingredient> left,
                                               boolean simulate, int slot) {
                if (machine != null && !machine.slotAllowsRecipe(slot, recipe)) {
                    machine.logGateReject(slot, recipe, "物品");
                    return false;
                }
                return super.meHandleRecipeInner(recipe, left, simulate, slot);
            }
        }

        static final class GatedFluidHandler extends MEFluidHandlerBase {

            private final LazyPatternAssemblyPartMachine machine;

            GatedFluidHandler(MEPatternBufferPartMachineBase machine, IO io) {
                super(machine, io);
                this.machine = machine instanceof LazyPatternAssemblyPartMachine lazy ? lazy : null;
            }

            @Override
            public boolean meHandleRecipeInner(GTRecipe recipe, Object2LongMap<FluidIngredient> left,
                                               boolean simulate, int slot) {
                if (machine != null && !machine.slotAllowsRecipe(slot, recipe)) {
                    machine.logGateReject(slot, recipe, "流体");
                    return false;
                }
                return super.meHandleRecipeInner(recipe, left, simulate, slot);
            }
        }
    }

    /** 上游出料先放进机器 buffer，必须挂接 AE ticker 才能将产物推回网络。 */
    public class LazyBufferTicker implements IGridTickable {

        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(ConfigHolder.INSTANCE.MEPatternOutputMin,
                    ConfigHolder.INSTANCE.MEPatternOutputMax, false, true);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!getMainNode().isActive()) {
                isSleeping = true;
                return TickRateModulation.SLEEP;
            }
            if (buffer.isEmpty()) {
                if (ticksSinceLastCall >= ConfigHolder.INSTANCE.MEPatternOutputMax) {
                    isSleeping = true;
                    return TickRateModulation.SLEEP;
                }
                return TickRateModulation.SLOWER;
            }
            // 推网失败时保留 buffer，后续 tick 重试，避免网络拥堵导致丢失产物。
            return AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource)
                    ? TickRateModulation.URGENT
                    : TickRateModulation.SLOWER;
        }
    }

    /** 基类 getMETrait 会强转 IMEPatternTrait，普通 MEIOTrait 不满足这个契约。 */
    @Override
    protected IMEIOTrait createMETrait() {
        return new LazyMEPatternTrait();
    }

    public class LazyMEPatternTrait extends MEIOPartMachine.MEIOTrait implements IMEPatternTrait {

        private final Int2ReferenceMap<ObjectSet<GTRecipe>> slot2RecipesCache = new Int2ReferenceOpenHashMap<>();
        private IntConsumer onPatternChange = slot -> {};

        public LazyMEPatternTrait() {
            super(LazyPatternAssemblyPartMachine.this);
        }

        @Override
        public LazyPatternAssemblyPartMachine getMachine() {
            return LazyPatternAssemblyPartMachine.this;
        }

        @Override
        public ObjectSet<GTRecipe> getCachedGTRecipe() {
            // 主机可能直接取非空缓存中的配方，读侧必须排除缓存未齐或当前无料的槽。
            ObjectSet<GTRecipe> all = new ObjectOpenHashSet<>();
            ObjectIterator<Int2ReferenceMap.Entry<ObjectSet<GTRecipe>>> iterator =
                    Int2ReferenceMaps.fastIterator(slot2RecipesCache);
            while (iterator.hasNext()) {
                Int2ReferenceMap.Entry<ObjectSet<GTRecipe>> entry = iterator.next();
                ObjectSet<GTRecipe> recipes = entry.getValue();
                if (recipes == null || recipes.isEmpty()) {
                    iterator.remove();
                    continue;
                }
                int slot = entry.getIntKey();
                // 查询不能为冷槽实体化对象，未实体化槽按无料处理。
                InternalSlot internal = slot >= 0 && slot < internalInventory.size()
                        ? internalInventory.get(slot) : null;
                if (internal == null || !internal.isActive()) {
                    continue;
                }
                if (recipes.size() < cacheRecipeCountAt(slot)) {
                    continue;
                }
                // 读侧校验可用类型，避免主机快路径拿到其他控制器留下的陈旧配方。
                if (FILTER_CACHED_RECIPES_BY_AVAILABLE_TYPE_ON_READ) {
                    for (GTRecipe cached : recipes) {
                        if (recipeTypeIsUsableByThisMachine(cached)) {
                            all.add(cached);
                        }
                    }
                } else {
                    all.addAll(recipes);
                }
            }
            return all;
        }

        @Override
        public void setSlotCacheRecipe(int slot, GTRecipe recipe) {
            if (recipe == null) {
                slot2RecipesCache.remove(slot);
                return;
            }
            // 占位配方不能进入缓存，否则主机快路径可能把它当作可运行配方。
            if (recipe.recipeType == GTRecipeTypes.DUMMY_RECIPES) {
                return;
            }
            slot2RecipesCache.computeIfAbsent(slot, key -> new ObjectOpenHashSet<>()).add(recipe);
        }

        @Override
        public Int2ReferenceMap<ObjectSet<GTRecipe>> getSlot2RecipesCache() {
            return slot2RecipesCache;
        }

        @Override
        public void setOnPatternChange(IntConsumer consumer) {
            this.onPatternChange = consumer == null ? slot -> {} : consumer;
        }

        public ObjectSet<GTRecipe> slotRecipes(int slot) {
            return slot2RecipesCache.get(slot);
        }

        @Override
        public boolean hasCacheInSlot(int slot) {
            ObjectSet<GTRecipe> set = slotRecipes(slot);
            return set != null && !set.isEmpty() && set.size() >= cacheRecipeCountAt(slot);
        }

        public void notifyPatternChange(int slot) {
            onPatternChange.accept(slot);
        }

        /** 样板替换后需移除该槽的配方缓存，避免主机继续执行已经撤下的样板配方。 */
        public void clearSlotCache(int slot) {
            slot2RecipesCache.remove(slot);
        }
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

    public int getControllerRecipeTypeIndex() {
        IRecipeLogicMachine controller = firstRecipeLogicController();
        if (controller != null) {
            int live = controller.getActiveRecipeType();
            if (live >= 0) {
                return live;
            }
        }
        return modeCursorIndex;
    }

    private void rememberBoundModeType() {
        modeContext().rememberBoundModeType();
    }

    private void applyBoundModeTypeIfPossible() {
        modeContext().applyBoundModeTypeIfPossible();
    }

    /** 容量增长时只追加缓存项，重建整表会丢掉已识别的类型。 */
    private void ensureRecipeTypeSlotCapacity() {
        int capacity = getCapacity();
        while (slotRecipeTypes.size() < capacity) {
            slotRecipeTypes.add(null);
        }
    }

    private int recipeTypeCacheAt(int slot) {
        if (slot < 0 || slot >= slotRecipeTypes.size()) {
            return TYPE_NOT_TRIED;
        }
        Integer value = slotRecipeTypes.get(slot);
        return value == null ? TYPE_NOT_TRIED : value;
    }

    private void setRecipeTypeCache(int slot, int value) {
        if (slot < 0) {
            return;
        }
        ensureRecipeTypeSlotCapacity();
        while (slotRecipeTypes.size() <= slot) {
            slotRecipeTypes.add(null);
        }
        slotRecipeTypes.set(slot, value);
    }

    private int recipeTypeOfSlot(int slot) {
        return modeContext().recipeTypeOfSlot(slot, TYPE_UNKNOWN);
    }

    private int manualTypeIndexOf(int slot, List<GTRecipeType> types) {
        return PatternModeContext.manualTypeIndexOf(slot, types, manualSlotTypes);
    }

    public int getManualAssignSlot() {
        return manualAssignSlot;
    }

    public int getPatternSlotCount() {
        return Math.max(1, getInternalSlotCount());
    }

    public void setManualAssignSlot(int slot) {
        PatternModeContext.setManualAssignSlot(slot, this::getPatternSlotCount,
                value -> this.manualAssignSlot = value, this::refreshManualAssignDisplay);
    }

    public String getManualAssignTypeId() {
        return manualAssignTypeId;
    }

    public String getManualAssignAutoTypeId() {
        return manualAssignAutoTypeId;
    }

    private void refreshManualAssignDisplay() {
        if (isRemote()) {
            return;
        }
        ResourceLocation id = manualSlotTypes.get(manualAssignSlot - 1);
        this.manualAssignTypeId = id == null ? "" : id.toString();
        try {
            List<GTRecipeType> types = getAvailableRecipeTypes();
            int auto = autoDetectTypeOfSlot(manualAssignSlot - 1);
            GTRecipeType autoType = auto >= 0 && auto < types.size() ? types.get(auto) : null;
            this.manualAssignAutoTypeId =
                    autoType == null || autoType.registryName == null ? "" : autoType.registryName.toString();
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#refreshManualAssignDisplay", ignored);
            this.manualAssignAutoTypeId = "";
        }
    }

    private int autoDetectTypeOfSlot(int slot) {
        if (slot < 0 || slot >= getCapacity()) {
            return -1;
        }
        List<GTRecipeType> types = getAvailableRecipeTypes();
        if (types.isEmpty()) {
            return -1;
        }
        int cached = recipeTypeCacheAt(slot);
        if (cached >= 0 && cached < types.size()) {
            return cached;
        }
        if (cached == TYPE_UNKNOWN) {
            return -1;
        }
        int detected = detectRecipeType(slot, types);
        setRecipeTypeCache(slot, detected >= 0 ? detected : TYPE_UNKNOWN);
        return detected;
    }

    /** 主机缓存快路径可能不再校验类型，读侧须排除不属于当前可用表的配方。 */
    public static final boolean FILTER_CACHED_RECIPES_BY_AVAILABLE_TYPE_ON_READ = true;

    /** 切模式保留其他类型的缓存，避免跨表独立批次只剩当前激活表可用。 */
    public static final boolean KEEP_FOREIGN_SLOT_RECIPE_CACHE_ON_MODE_SWITCH = true;

    private void clearSlotRecipeCache(int slot) {
        try {
            ((LazyMEPatternTrait) getMETrait()).clearSlotCache(slot);
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#clearSlotRecipeCache", ignored);
        }
    }

    private void clearAllSlotRecipeCaches() {
        clearAllSlotRecipeCaches(getActiveRecipeTypeIndex());
    }

    private void clearAllSlotRecipeCaches(int activeTypeIndex) {
        try {
            LazyMEPatternTrait trait = (LazyMEPatternTrait) getMETrait();
            if (!KEEP_FOREIGN_SLOT_RECIPE_CACHE_ON_MODE_SWITCH) {
                trait.getSlot2RecipesCache().clear();
                return;
            }
            List<GTRecipeType> types = getAvailableRecipeTypes();
            GTRecipeType current = activeTypeIndex >= 0 && activeTypeIndex < types.size()
                    ? types.get(activeTypeIndex) : null;
            if (current == null) {
                trait.getSlot2RecipesCache().clear();
                return;
            }
            ObjectIterator<Int2ReferenceMap.Entry<ObjectSet<GTRecipe>>> iterator =
                    Int2ReferenceMaps.fastIterator(trait.getSlot2RecipesCache());
            while (iterator.hasNext()) {
                Int2ReferenceMap.Entry<ObjectSet<GTRecipe>> entry = iterator.next();
                ObjectSet<GTRecipe> recipes = entry.getValue();
                if (recipes == null || recipes.isEmpty()) {
                    continue;
                }
                recipes.removeIf(recipe -> recipe == null
                        || recipe.recipeType == null
                        || recipe.recipeType == current);
                if (recipes.isEmpty()) {
                    iterator.remove();
                }
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#clearAllSlotRecipeCaches", ignored);
        }
    }

    private boolean recipeTypeIsUsableByThisMachine(GTRecipe recipe) {
        if (!FILTER_CACHED_RECIPES_BY_AVAILABLE_TYPE_ON_READ || recipe == null) {
            return true;
        }
        try {
            GTRecipeType type = recipe.recipeType;
            if (type == null) {
                return false;
            }
            List<GTRecipeType> types = getAvailableRecipeTypes();
            if (types.isEmpty()) {
                return true;
            }
            return types.contains(type);
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#recipeTypeIsUsableByThisMachine", ignored);
            return true;
        }
    }

    public void assignManualType(int slotNumber, boolean clear) {
        try {
            modeContext().assignManualType(slotNumber, clear);
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#assignManualType", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：手动指定配方模式失败 pos={} 槽={} 原因={}",
                    getPos(), slotNumber, failure.toString());
        }
    }

    public boolean hasManualType(int slot) {
        return manualSlotTypes.containsKey(slot);
    }

    private int detectRecipeType(int slot, List<GTRecipeType> types) {
        Level level = getLevel();
        if (level == null || slot < 0 || slot >= getCapacity()) {
            return -1;
        }
        ItemStack stack = patternInventory.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return -1;
        }
        PatternRecipeMatching.DecodedPattern pattern;
        try {
            pattern = PatternRecipeMatching.decode(getRealPatternHelper(), stack, level, keepByProduct);
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#detectRecipeType.decodePattern", ignored);
            return -1;
        }
        if (pattern == null) {
            return -1;
        }
        int withoutHost = scanTypesForMatch(types, pattern, level, true);
        if (withoutHost != MATCH_NONE) {
            supplyResolvedRecipeToMachine(slot, withoutHost);
            return withoutHost;
        }
        int hostTableOnly = scanTypesForMatch(types, pattern, level, false);
        supplyResolvedRecipeToMachine(slot, hostTableOnly);
        return hostTableOnly;
    }

    /** 先清空中转，避免扫描失败时沿用上一次扫描的配方。 */
    private int scanTypesForMatch(List<GTRecipeType> types, PatternRecipeMatching.DecodedPattern pattern, Level level,
                                  boolean skipHostTable) {
        this.lastMatchedSlotRecipe = null;
        return PatternRecipeTypeSearch.scan(types, pattern, level, skipHostTable,
                type -> type == com.gtlmodular.common.data.nanochip.NanochipRecipeTypes.ASSEMBLY_COMPLEX,
                recipe -> this.lastMatchedSlotRecipe = recipe).typeIndex();
    }

    public static boolean DIRECT_SLOT_RECIPE_SUPPLY = true;

    private GTRecipe lastMatchedSlotRecipe;

    /** 只有类型唯一时才直供，避免把歧义样板交给机器。 */
    private void supplyResolvedRecipeToMachine(int slot, int detectedTypeIndex) {
        if (!DIRECT_SLOT_RECIPE_SUPPLY || detectedTypeIndex < 0) {
            return;
        }
        GTRecipe recipe = this.lastMatchedSlotRecipe;
        if (recipe == null || isRemote()) {
            return;
        }
        PatternRecipeTypeSearch.supplyIfAbsent(slot, recipe, this::getMETrait);
    }

    /** WAITING 的独立线程仍可能持有已扣料批次，默认禁止推送锁模式重置这些批次。 */
    public static final boolean ALLOW_MODE_SWITCH_WITH_RUNNING_BATCHES = false;

    private void lockRecipeTypeForSlot(int slot) {
        try {
            List<GTRecipeType> types = getAvailableRecipeTypes();
            if (types.size() <= 1) {
                return;
            }
            int index = recipeTypeOfSlot(slot);
            patternAutoSwitch().lockRecipeTypeForSlot(slot, index, types);
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#lockRecipeTypeForSlot", ignored);
            // 锁模式失败不能阻止原本的样板推送。
        }
    }

    private void autoSwitchRecipeModes() {
        try {
            if (isRemote()) {
                return;
            }
            applyBoundModeTypeIfPossible();
            syncCustomNameToMode(activeRecipeTypeIndex);
            patternAutoSwitch().tick();
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#autoSwitchRecipeModes", ignored);
            // 调度失败不能打断同一 tick 的其他机器维护。
        }
    }

    private int findModeWithWaitingStock(List<GTRecipeType> types, int active, long now) {
        int capacity = getCapacity();
        ensureRecipeTypeSlotCapacity();
        int slots = Math.min(capacity, slotRecipeTypes.size());
        return patternAutoSwitch().findModeWithWaitingStock(types, active, now, slots,
                slot -> patternInventory.getStackInSlot(slot).isEmpty());
    }

    private boolean hasSlotWaitingStock(int slot) {
        return PatternAutoSwitch.hasWaitingStock(() -> slot >= 0 && slot < internalInventory.size()
                ? internalInventory.get(slot) : null,
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
        public void setCustomName(String name) { LazyPatternAssemblyPartMachine.this.setCustomName(name); }

        @Override
        public void markDirty() { LazyPatternAssemblyPartMachine.this.markDirty(); }

        @Override
        public boolean isRemote() { return LazyPatternAssemblyPartMachine.this.isRemote(); }

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
        public IRecipeLogicMachine firstRecipeLogicController() { return LazyPatternAssemblyPartMachine.this.firstRecipeLogicController(); }

        @Override
        public int namingModeIndexOf(boolean bound, int hint, List<GTRecipeType> types) {
            return LazyPatternAssemblyPartMachine.this.namingModeIndexOf(bound, hint, types);
        }

        @Override
        public int currentModeIndexForNaming(int hint) { return LazyPatternAssemblyPartMachine.this.currentModeIndexForNaming(hint); }

        @Override
        public java.util.Map<String, String> modeNames() { return modeNames; }

        @Override
        public String lockedName() { return lockedName; }

        @Override
        public void setLockedName(String name) { lockedName = name; }

        @Override
        public boolean allowDefaultName(IRecipeLogicMachine controller, List<GTRecipeType> types, int mode) {
            return true;
        }
    }

    private void syncCustomNameToMode(int modeIndexHint) {
        if (isRemote()) {
            return;
        }
        try {
            patternNameController().syncCustomNameToMode(modeIndexHint);
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#syncCustomNameToMode", ignored);
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

    /** 限流哨兵不能使用 Long.MIN_VALUE，否则时间差溢出可能使日志永久静默。 */
    private long lastGateRejectLogTick = -1000;

    private final Set<String> hostTableMisidentifyNotes = new HashSet<>();

    private static final int MISIDENTIFY_NOTE_LIMIT = 64;

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
                    if (slot >= getCapacity()) {
                        return ItemStack.EMPTY;
                    }
                    return patternInventory.getStackInSlot(slot);
                }, this::getLevel, (stack, level) -> PatternRecipeMatching.decode(getRealPatternHelper(),
                        stack, level, keepByProduct));
    }

    /** 拒绝发生在扣料入口，限频日志便于定位握着料却不工作的原因。 */
    void logGateReject(int slot, GTRecipe recipe, String side) {
        try {
            long now = getLevel() == null ? 0 : getLevel().getGameTime();
            if (now - lastGateRejectLogTick < 40) {
                return;
            }
            lastGateRejectLogTick = now;
            PatternIdentityGate.logReject(slot, recipe, side, this::getAvailableRecipeTypes,
                    () -> recipeTypeOfSlot(slot));
            noteIfHostTableMisidentified(slot, recipe);
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#logGateReject", ignored);
            // 诊断日志绝不允许影响扣料本身。
        }
    }

    /** 主机表可能包含代理配方，需在拒绝后留下有界诊断供排查误识别。 */
    private void noteIfHostTableMisidentified(int slot, GTRecipe recipe) {
        PatternIdentityGate.noteIfHostTableMisidentified(slot, recipe, this::getAvailableRecipeTypes,
                () -> recipeTypeOfSlot(slot),
                type -> type == com.gtlmodular.common.data.nanochip.NanochipRecipeTypes.ASSEMBLY_COMPLEX,
                hostTableMisidentifyNotes, MISIDENTIFY_NOTE_LIMIT, this::getPos);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        getMainNode().setExposedOnSides(EnumSet.allOf(Direction.class));
        // 外部写仓不一定触发回调，需签名轮询；重挂 tick 前先退订，避免 onLoad 重入导致双跑。
        if (this.usageSubscription != null) {
            this.usageSubscription.unsubscribe();
        }
        this.usageSubscription = subscribeServerTick(this::lazyTick);
        // 节点可能已处于在线状态，不再触发状态变化回调，加载时也需确保样板同步订阅存在。
        updateSubscription();
    }

    /** 控制器名单到达可能晚于区块烘焙，接收同步后需安排重绘。 */
    @Override
    public void onSyncChanged(IRef ref, boolean isClient) {
        clientSyncRefresh.onSyncChanged(ref, isClient, () -> getDefinition().getId(), this::getPos,
                () -> getControllers().size(), this::scheduleRenderUpdate);
    }

    /** 界面持续打开时也可能扩容，客户端 tick 需补齐仓容量，不能只在建界面时同步。 */
    @Override
    public void clientTick() {
        super.clientTick();
        try {
            applySyncedPatternCapacity();
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#clientTick", ignored);
        }
    }

    private void lazyTick() {
        if (isRemote()) {
            return;
        }
        if (formedNudgeTicks > 0) {
            formedNudgeTicks = FormedRenderSyncNudge.tick(this, formedNudgeTicks, "惰性猪咪");
        }
        autoSwitchRecipeModes();
        long signature = usageSignature();
        if (!usageSignatureValid || signature != lastUsageSignature) {
            usageSignatureValid = true;
            lastUsageSignature = signature;
            onPatternInventoryChanged();
        }
        // 基类订阅尚未挂上时也要消费 needPatternSync，避免 AE 长期持有过期样板表。
        if (needPatternSync) {
            needPatternSync = false;
            refreshPatternsNow();
        }
        staleSlotCache.drain(this::invalidateSlotRecipeCache, this::getPos);
        selfHealColdSlotCaches();
        if (!loggedBaseSyncState) {
            loggedBaseSyncState = true;
            GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪基类样板同步 tick 取证: updateSubs={} needPatternSync={}",
                    this.updateSubs == null ? "未订阅(基类那条链没挂上)" : "已订阅", needPatternSync);
        }
    }

    /** 冷槽的未知类型缓存可能阻止再次识别；按节拍和总预算重试，避免持续扫描全部配方表。 */
    private void selfHealColdSlotCaches() {
        if (!COLD_SLOT_CACHE_HEAL) {
            return;
        }
        try {
            if (isRemote()) {
                return;
            }
            if (coldSlotHealTried.size() >= COLD_SLOT_HEAL_BUDGET) {
                return;
            }
            long now = getOffsetTimer();
            if (now < coldSlotHealNextTick) {
                return;
            }
            if (firstRecipeLogicController() == null) {
                return;
            }
            List<GTRecipeType> types = getAvailableRecipeTypes();
            if (types.isEmpty()) {
                return;
            }
            int capacity = getCapacity();
            for (int slot = 0; slot < capacity; slot++) {
                if (patternInventory.getStackInSlot(slot).isEmpty()) {
                    continue;
                }
                if (hasRecipeCacheInSlot(slot)) {
                    continue;
                }
                if (!hasSlotWaitingStock(slot)) {
                    continue;
                }
                if (coldSlotHealTried.size() >= COLD_SLOT_HEAL_BUDGET) {
                    return;
                }
                ItemStack patternNow = patternInventory.getStackInSlot(slot);
                if (coldSlotHealTried.contains(slot)) {
                    ItemStack patternBefore = coldSlotHealPattern.get(slot);
                    if (patternBefore != null && ItemStack.matches(patternBefore, patternNow)) {
                        continue;
                    }
                }
                setRecipeTypeCache(slot, TYPE_NOT_TRIED);
                coldSlotHealTried.add(slot);
                coldSlotHealPattern.put(slot, patternNow.copy());
                coldSlotHealNextTick = now + COLD_SLOT_HEAL_INTERVAL_TICKS;
                int detected = recipeTypeOfSlot(slot);
                if (detected >= 0 && hasRecipeCacheInSlot(slot)) {
                    coldSlotHealWarmed++;
                    GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪冷槽重识别成功: pos={} 槽={} 识别类型下标={}"
                                    + "（已喂热，累计 {} 次；冷槽重识别≈『重置缓存+读取缓存』）",
                            getPos(), slot + 1, detected, coldSlotHealWarmed);
                }
                return;   // 每 tick 最多重识别一个槽，分摊配方表扫描成本。
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#selfHealColdSlotCaches", failure);
            GTLModular.LOGGER.debug("[gtlmodular] 惰性猪咪冷槽重识别失败（已忽略，不影响机器本身）：pos={} {}",
                    getPos(), failure.toString());
        }
    }

    private long usageSignature() {        long signature = 31L * 31 + getCapacity();
        int capacity = getCapacity();
        for (int i = 0; i < capacity; i++) {
            ItemStack stack = patternInventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                signature = signature * 31 + i;
            } else {
                signature = signature * 31 + (i + 1) * 1_000_003L + stack.hashCode();
            }
        }
        return signature;
    }

    void onPatternInventoryChanged() {
        patternSlotMapDirty = true;
        needPatternSync = true;
        diffSlotPatterns();
        growCapacityFor(highestUsedSlot() + 1 + RESERVE);
        invalidateRewriteCacheQuietly();
        // 批量写入只置标记，由 tick 合并通知 AE，避免每写一槽都重读整仓形成平方开销。
    }

    private void refreshPatternsNow() {
        if (isRemote()) {
            return;
        }
        try {
            providerRefresh.refresh(this::getMainNode);
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#refreshPatternsNow", ignored);
            // 网络暂不可用时缓存仍已失效，下次读取会重建。
        }
    }

    private void diffSlotPatterns() {
        if (isRemote()) {
            return;
        }
        int capacity = getCapacity();
        while (patternSnapshot.size() < capacity) {
            patternSnapshot.add(ItemStack.EMPTY);
        }
        boolean first = !patternSnapshotReady;
        for (int i = 0; i < capacity; i++) {
            ItemStack now = patternInventory.getStackInSlot(i);
            ItemStack before = patternSnapshot.get(i);
            boolean changed = !ItemStack.matches(before, now);
            if (changed) {
                patternSnapshot.set(i, now.copy());
                if (!first) {
                    onSlotPatternChanged(i);
                }
            }
        }
        patternSnapshotReady = true;
    }

    private void onSlotPatternChanged(int slot) {
        InternalSlot internal = getInternalSlot(slot);
        if (internal == null) {
            return;
        }
        try {
            internal.getCacheManager().clearAllCaches();
            internal.clearVirtualSupply();
            refundSlot(internal.getItemInventory(), internal.getFluidInventory());
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#onSlotPatternChanged.refundSlot", ignored);
        }
        // 退款先进入 buffer，需立即尝试推网并唤醒 ticker，否则休眠时残料可能长期滞留。
        try {
            AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource);
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#onSlotPatternChanged.refundToGrid", ignored);
        }
        try {
            getMETrait().notifySelfIO();
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#onSlotPatternChanged.notifySelfIO", ignored);
        }
        // 镜像也维护配方到槽的映射，移除本机槽缓存时必须同步通知镜像。
        try {
            notifyProxySlotRemoved(slot);
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#onSlotPatternChanged.notifyProxy", ignored);
        }
        LazyMEPatternTrait patternTrait = (LazyMEPatternTrait) getMETrait();
        if (patternTrait != null) {
            patternTrait.clearSlotCache(slot);
            patternTrait.notifyPatternChange(slot);
        }
        // 样板替换后必须清除旧类型结论，避免新样板沿用错误模式。
        try {
            setRecipeTypeCache(slot, TYPE_NOT_TRIED);
            patternAutoSwitch().clearBlocked();
            coldSlotHealTried.remove(slot);
            coldSlotHealPattern.remove(slot);
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#onSlotPatternChanged.resetRecipeType", ignored);
        }
    }

    @Override
    public void onMachineRemoved() {
        if (usageSubscription != null) {
            usageSubscription.unsubscribe();
            usageSubscription = null;
        }
        super.onMachineRemoved();
        clearInventory(this.patternInventory);
        try {
            for (ItemStackTransfer catalyst : catalystItems) {
                if (catalyst != null) {
                    clearInventory(catalyst);
                }
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#onMachineRemoved", ignored);
        }
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        // 样板和催化剂已由拆机掉落归还，forDrop 不能再写入物品内容或按槽历史，避免复制。
        if (!forDrop) {
            tag.put("patternInventory", patternInventory.serializeNBT());
            ListTag slotList = new ListTag();
            int lastBuilt = -1;
            for (int i = 0; i < internalInventory.size(); i++) {
                if (internalInventory.get(i) != null) {
                    lastBuilt = i;
                }
            }
            for (int i = 0; i <= lastBuilt; i++) {
                InternalSlot slot = internalInventory.get(i);
                slotList.add(slot == null ? new CompoundTag() : slot.serializeNBT());
            }
            tag.put("internalInventory", slotList);
        }
        String name = getCustomName();
        tag.putString("customName", name == null ? "" : name);

        tag.putInt(NBT_PATTERN_SCALE, patternScale);
        tag.putInt(NBT_PATTERN_DIV_SCALE, patternDivScale);
        tag.putBoolean(NBT_FOA_MODE, foaMode);
        tag.putInt(NBT_FOA_MULTIPLIER, foaMultiplier);
        tag.putBoolean(NBT_OUTPUT_MODE, isOutputMode);

        if (boundModeType != null) {
            tag.putString(NBT_BOUND_MODE_TYPE, boundModeType.toString());
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
                tag.put(NBT_MODE_NAMES, names);
            }
        }
        if (!lockedName.isEmpty()) {
            tag.putString(NBT_LOCKED_NAME, lockedName.length() > MAX_SAVED_NAME_LENGTH
                    ? lockedName.substring(0, MAX_SAVED_NAME_LENGTH) : lockedName);
        }

        // 槽位元数据只随世界存档保存；掉落时样板已分离，留下元数据会绑定到错误的新样板。
        if (forDrop) {
            return;
        }
        ensureRecipeTypeSlotCapacity();
        IntArrayList sparse = new IntArrayList();
        for (int i = 0; i < slotRecipeTypes.size(); i++) {
            Integer value = slotRecipeTypes.get(i);
            if (value == null || value == TYPE_NOT_TRIED) {
                continue;
            }
            sparse.add(i);
            sparse.add(value);
        }
        if (!sparse.isEmpty()) {
            tag.putIntArray(NBT_SLOT_RECIPE_TYPE_SPARSE, sparse.toIntArray());
        }
        if (!manualSlotTypes.isEmpty()) {
            CompoundTag manual = new CompoundTag();
            manualSlotTypes.forEach((key, id) -> manual.putString("s" + key, id.toString()));
            tag.put(NBT_MANUAL_SLOT_TYPES, manual);
        }

        saveCatalystSlots(tag);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);

        if (tag.contains("patternInventory", Tag.TAG_COMPOUND)) {
            patternInventory.deserializeNBT(tag.getCompound("patternInventory"));
        }
        // 存档容量可能超过硬上限，先截断再恢复槽状态，避免基类遍历得到空引用。
        clampCapacityToMax();

        // 槽状态按索引兼容长表与短表，不能因状态表长度不同而丢弃样板仓。
        ListTag slotList = tag.getList("internalInventory", Tag.TAG_COMPOUND);
        int lastStateful = -1;
        for (int i = 0; i < slotList.size(); i++) {
            if (!slotList.getCompound(i).isEmpty()) {
                lastStateful = i;
            }
        }
        int used = highestUsedSlot() + 1;
        growCapacityFor(Math.max(used, lastStateful + 1) + RESERVE);
        while (internalInventory.size() < getCapacity()) {
            internalInventory.add(null);
        }
        for (int i = 0; i < slotList.size() && i < getCapacity(); i++) {
            CompoundTag slotTag = slotList.getCompound(i);
            if (slotTag.isEmpty()) {
                continue;
            }
            InternalSlot slot = getInternalSlot(i);
            if (slot != null) {
                slot.deserializeNBT(slotTag);
            }
        }

        if (tag.contains("customName", Tag.TAG_STRING)) {
            String name = tag.getString("customName");
            String current = getCustomName();
            if (!name.isEmpty() && (current == null || current.isEmpty())) {
                setCustomName(name);
            }
        }

        // 读档直接恢复设置，避免 setter 在节点未就绪时触发 AE 刷新。
        try {
            if (tag.contains(NBT_PATTERN_SCALE, Tag.TAG_INT)) {
                patternScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, tag.getInt(NBT_PATTERN_SCALE)));
            }
            if (tag.contains(NBT_PATTERN_DIV_SCALE, Tag.TAG_INT)) {
                patternDivScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, tag.getInt(NBT_PATTERN_DIV_SCALE)));
            }
            if (tag.contains(NBT_FOA_MODE, Tag.TAG_BYTE)) {
                foaMode = tag.getBoolean(NBT_FOA_MODE);
            }
            if (tag.contains(NBT_FOA_MULTIPLIER, Tag.TAG_INT)) {
                foaMultiplier = Math.max(MIN_FOA_MULTIPLIER,
                        Math.min(MAX_FOA_MULTIPLIER, tag.getInt(NBT_FOA_MULTIPLIER)));
            }
            if (tag.contains(NBT_OUTPUT_MODE, Tag.TAG_BYTE)) {
                isOutputMode = tag.getBoolean(NBT_OUTPUT_MODE);
            }
            invalidateRewriteCacheQuietly();
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#loadCustomPersistedData.rewriteSettings", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：视图改写设置读档失败（已忽略，按默认值）pos={} 原因={}",
                    getPos(), failure.toString());
        }

        patternSlotMapDirty = true;
        needPatternSync = true;
        usageSignatureValid = false;   // 读档后首次对比只重建快照，避免把已有样板误判为替换并退料。
        patternSnapshotReady = false;
        patternSnapshot.clear();
        diffSlotPatterns();

        try {
            ensureRecipeTypeSlotCapacity();
            int capacity = getCapacity();
            int[] sparse = tag.getIntArray(NBT_SLOT_RECIPE_TYPE_SPARSE);
            for (int i = 0; i + 1 < sparse.length; i += 2) {
                int slot = sparse[i];
                int value = sparse[i + 1];
                if (slot < 0 || slot >= capacity) {
                    continue;
                }
                if (value < TYPE_UNKNOWN) {
                    continue;
                }
                setRecipeTypeCache(slot, value);
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#loadCustomPersistedData.slotRecipeTypes", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：逐槽配方类型读档失败（已忽略，按未识别处理）pos={} 原因={}",
                    getPos(), failure.toString());
        }

        try {
            manualSlotTypes.clear();
            CompoundTag manual = tag.getCompound(NBT_MANUAL_SLOT_TYPES);
            int capacity = getCapacity();
            for (String key : manual.getAllKeys()) {
                if (!key.startsWith("s")) {
                    continue;
                }
                try {
                    int slot = Integer.parseInt(key.substring(1));
                    if (slot < 0 || slot >= capacity) {
                        continue;
                    }
                    ResourceLocation id = ResourceLocation.tryParse(manual.getString(key));
                    if (id != null) {
                        manualSlotTypes.put(slot, id);
                    }
                } catch (RuntimeException ignored) {
                }
            }
            refreshManualAssignDisplay();
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#loadCustomPersistedData.manualTypes", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：手动指定表读档失败（已忽略）pos={} 原因={}",
                    getPos(), failure.toString());
        }

        try {
            // 客户端以同步字段显示模式，不能把区块 NBT 当作服务端权威绑定。
            if (!isRemote() && tag.contains(NBT_BOUND_MODE_TYPE, Tag.TAG_STRING)) {
                ResourceLocation bound = ResourceLocation.tryParse(tag.getString(NBT_BOUND_MODE_TYPE));
                if (bound != null) {
                    this.boundModeType = bound;
                    this.boundModePendingResolve = true;
                    applyBoundModeTypeIfPossible();
                }
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#loadCustomPersistedData.boundMode", failure);
            this.boundModeType = null;
            this.boundModePendingResolve = false;
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：绑定模式读档失败（已忽略，按未绑定处理）pos={} 原因={}",
                    getPos(), failure.toString());
        }

        try {
            if (!isRemote()) {
                modeNames.clear();
                this.lockedName = "";
                if (patternNameController != null) {
                    patternNameController.reset();
                }
                if (tag.contains(NBT_MODE_NAMES, Tag.TAG_COMPOUND)) {
                    CompoundTag names = tag.getCompound(NBT_MODE_NAMES);
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
                if (tag.contains(NBT_LOCKED_NAME, Tag.TAG_STRING)) {
                    String locked = tag.getString(NBT_LOCKED_NAME);
                    this.lockedName = locked.length() > MAX_SAVED_NAME_LENGTH ? "" : locked;
                }
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#loadCustomPersistedData.modeNames", failure);
            modeNames.clear();
            this.lockedName = "";
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：名字表读档失败（已忽略，按「该模式还没有名字」处理）pos={} 原因={}",
                    getPos(), failure.toString());
        }

        syncCustomNameToMode(activeRecipeTypeIndex);

        // 催化剂最后恢复，才能使用已还原的容量和槽状态判断下标是否合法。
        loadCatalystSlots(tag);
    }

    public class LazyPatternBufferInternalSlot extends InternalSlot {

        private final Object2LongMap<AEItemKey> itemCatalystInventory = new Object2LongArrayMap<>();
        private final Object2LongMap<AEFluidKey> fluidCatalystInventory = new Object2LongArrayMap<>();

        private ItemStack circuitSource;
        private int patternCircuit = -1;

        LazyPatternBufferInternalSlot(int index) {
            super(index);
            itemCatalystInventory.defaultReturnValue(0L);
            fluidCatalystInventory.defaultReturnValue(0L);
        }

        /**
         * 上游按槽电路缓存校验配方电路，而本机不经过上游样板解码，缓存只能自己跟随样板里的电路。
         * 槽可能在读档、扩容时才实体化，且读档期间没有 Level 无法解码，所以在用到电路的地方按需同步。
         */
        void syncPatternCircuit() {
            int index = getSlotIndex();
            ItemStack stack = index < getCapacity() ? patternInventory.getStackInSlot(index) : ItemStack.EMPTY;
            if (stack != circuitSource) {
                Level level = getLevel();
                if (level == null) {
                    return;
                }
                int previous = patternCircuit;
                PatternRecipeMatching.DecodedPattern decoded = stack.isEmpty() ? null
                        : PatternRecipeMatching.decode(getRealPatternHelper(), stack, level, keepByProduct);
                patternCircuit = decoded == null ? -1 : decoded.circuit();
                circuitSource = stack;
                if (patternCircuit < 0 && previous >= 0) {
                    getCacheManager().clearCircuitCache();
                }
            }
            if (patternCircuit >= 0 && getCacheManager().getCircuitCache() != patternCircuit) {
                getCacheManager().setCircuitCache(patternCircuit);
            }
        }

        @Override
        public boolean handleItemInternal(Object2LongMap<Ingredient> left, int leftCircuit, boolean simulate) {
            syncPatternCircuit();
            return super.handleItemInternal(left, leftCircuit, simulate);
        }

        @Override
        public Object2LongMap<AEItemKey> getItemCatalystInventory() {
            return itemCatalystInventory;
        }

        @Override
        public Object2LongMap<AEFluidKey> getFluidCatalystInventory() {
            return fluidCatalystInventory;
        }

        @Override
        protected boolean hasItemCatalystInventory() {
            return !itemCatalystInventory.isEmpty();
        }

        @Override
        protected boolean hasFluidCatalystInventory() {
            return !fluidCatalystInventory.isEmpty();
        }

        @Override
        protected void appendLimitItemCatalystInputs(ObjectList<ItemStack> inputs) {
            for (Object2LongMap.Entry<AEItemKey> entry : Object2LongMaps.fastIterable(itemCatalystInventory)) {
                inputs.add(entry.getKey().toStack((int) Math.min(entry.getLongValue(), Integer.MAX_VALUE)));
            }
        }

        @Override
        protected void appendLimitFluidCatalystInputs(ObjectList<FluidStack> inputs) {
            for (Object2LongMap.Entry<AEFluidKey> entry : Object2LongMaps.fastIterable(fluidCatalystInventory)) {
                inputs.add(FluidStack.create(entry.getKey().getFluid(), entry.getLongValue()));
            }
        }

        /** 催化剂不能同时作为消耗性输入，避免把催化剂误当成待扣原料。 */
        @Override
        public boolean testCatalystItemInternal(GTRecipe recipe) {
            if (recipe == null) {
                return true;
            }
            for (Content content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
                if (content.chance <= 0) {
                    continue;
                }
                Object raw = content.getContent();
                if (!(raw instanceof Ingredient ingredient)) {
                    continue;
                }
                for (ItemStack stack : ingredient.getItems()) {
                    if (itemCatalystInventory.containsKey(AEItemKey.of(stack))) {
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public boolean testCatalystFluidInternal(GTRecipe recipe) {
            if (recipe == null) {
                return true;
            }
            for (Content content : recipe.getInputContents(FluidRecipeCapability.CAP)) {
                if (content.chance <= 0) {
                    continue;
                }
                Object raw = content.getContent();
                if (!(raw instanceof FluidIngredient ingredient)) {
                    continue;
                }
                for (FluidStack stack : ingredient.getStacks()) {
                    if (fluidCatalystInventory.containsKey(AEFluidKey.of(stack.getFluid()))) {
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = super.serializeNBT();
            ListTag items = AEUtils.createListTag(AEItemKey::toTag, itemCatalystInventory);
            if (!items.isEmpty()) {
                tag.put("catalystInventory", items);
            }
            ListTag fluids = AEUtils.createListTag(AEFluidKey::toTag, fluidCatalystInventory);
            if (!fluids.isEmpty()) {
                tag.put("catalystFluidInventory", fluids);
            }
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            super.deserializeNBT(tag);
            itemCatalystInventory.clear();
            AEUtils.loadInventory(tag.getList("catalystInventory", Tag.TAG_COMPOUND),
                    AEItemKey::fromTag, itemCatalystInventory);
            fluidCatalystInventory.clear();
            AEUtils.loadInventory(tag.getList("catalystFluidInventory", Tag.TAG_COMPOUND),
                    AEFluidKey::fromTag, fluidCatalystInventory);
        }
    }

    private void ensureCatalystTableLength(int index) {
        while (catalystItems.size() <= index) {
            catalystItems.add(null);
        }
        while (catalystFluids.size() <= index) {
            catalystFluids.add(null);
        }
        while (cacheRecipeCount.size() <= index) {
            cacheRecipeCount.add((byte) DEFAULT_CACHE_RECIPE_COUNT);
        }
    }

    private void ensureCatalystTables(int index) {
        if (index < 0 || index >= maxPatternCount) {
            return;
        }
        ensureCatalystTableLength(index);
        if (catalystItems.get(index) == null) {
            ItemStackTransfer transfer = new ItemStackTransfer(CATALYST_ITEM_SLOTS);
            // 催化剂仓拒收处理样板，避免与样板仓混用。
            transfer.setFilter(stack -> !(stack.getItem() instanceof ProcessingPatternItem));
            if (!isRemote()) {
                transfer.setOnContentsChanged(() -> onCatalystItemContentsChanged(index));
            }
            catalystItems.set(index, transfer);
        }
        if (catalystFluids.get(index) == null) {
            FluidStorage[] tanks = new FluidStorage[CATALYST_FLUID_TANKS];
            for (int i = 0; i < CATALYST_FLUID_TANKS; i++) {
                tanks[i] = new FluidStorage(CATALYST_TANK_CAPACITY);
            }
            FluidTransferList fluids = new FluidTransferList(tanks);
            if (!isRemote()) {
                for (FluidStorage storage : tanks) {
                    storage.setOnContentsChanged(() -> onCatalystFluidContentsChanged(index));
                }
            }
            catalystFluids.set(index, fluids);
        }
    }

    private ItemStackTransfer catalystItemTransferAt(int index) {
        return index >= 0 && index < catalystItems.size() ? catalystItems.get(index) : null;
    }

    private FluidTransferList catalystFluidTransferAt(int index) {
        return index >= 0 && index < catalystFluids.size() ? catalystFluids.get(index) : null;
    }

    private int cacheRecipeCountAt(int slot) {
        if (slot < 0 || slot >= cacheRecipeCount.size()) {
            return DEFAULT_CACHE_RECIPE_COUNT;
        }
        Byte value = cacheRecipeCount.get(slot);
        return value == null ? DEFAULT_CACHE_RECIPE_COUNT : Math.max(1, value);
    }

    private void setCacheRecipeCount(int slot, int value) {
        if (slot < 0 || slot >= maxPatternCount) {
            return;
        }
        ensureCatalystTableLength(slot);
        cacheRecipeCount.set(slot, (byte) Math.max(1, Math.min(MAX_CACHE_RECIPE_COUNT, value)));
    }

    private void reCalculateCatalystItemMap(int index) {
        if (isRemote()) {
            return;
        }
        ItemStackTransfer transfer = catalystItemTransferAt(index);
        if (transfer == null) {
            return;
        }
        try {
            InternalSlot raw = getInternalSlot(index);
            if (!(raw instanceof LazyPatternBufferInternalSlot slot)) {
                return;
            }
            Object2LongMap<AEItemKey> map = slot.getItemCatalystInventory();
            map.clear();
            slot.getConfiguredVirtualItems().clear();
            slot.getConfiguredVirtualFluids().clear();
            for (int i = 0; i < transfer.getSlots(); i++) {
                ItemStack stack = transfer.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                if (GTLItems.VIRTUAL_INGREDIENT.isIn(stack)) {
                    AEItemKey itemKey = VirtualIngredientBehavior.payloadItemKey(stack);
                    if (itemKey != null) {
                        slot.getConfiguredVirtualItems().add(itemKey);
                    }
                    AEFluidKey fluidKey = VirtualIngredientBehavior.payloadFluidKey(stack);
                    if (fluidKey != null) {
                        slot.getConfiguredVirtualFluids().add(fluidKey);
                    }
                } else {
                    map.mergeLong(AEItemKey.of(stack), stack.getCount(), Long::sum);
                }
            }
            invalidateSlotRecipeCache(index);
            Runnable callback = raw.getOnContentsChanged();
            if (callback != null) {
                callback.run();
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#reCalculateCatalystItemMap", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：催化剂物品重算失败（已忽略）pos={} 槽={} 原因={}",
                    getPos(), index + 1, failure.toString());
        }
    }

    private void reCalculateCatalystFluidMap(int index) {
        if (isRemote()) {
            return;
        }
        FluidTransferList fluids = catalystFluidTransferAt(index);
        if (fluids == null) {
            return;
        }
        try {
            InternalSlot raw = getInternalSlot(index);
            if (!(raw instanceof LazyPatternBufferInternalSlot slot)) {
                return;
            }
            Object2LongMap<AEFluidKey> map = slot.getFluidCatalystInventory();
            map.clear();
            for (int i = 0; i < fluids.getTanks(); i++) {
                FluidStack stack = fluids.getFluidInTank(i);
                if (stack.isEmpty()) {
                    continue;
                }
                map.mergeLong(AEFluidKey.of(stack.getFluid()), stack.getAmount(), Long::sum);
            }
            Runnable callback = raw.getOnContentsChanged();
            if (callback != null) {
                callback.run();
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#reCalculateCatalystFluidMap", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：催化剂流体重算失败（已忽略）pos={} 槽={} 原因={}",
                    getPos(), index + 1, failure.toString());
        }
    }

    /** 回调可能位于 tick 或网络路径，催化剂维护失败不能中断外层处理。 */
    private void onCatalystItemContentsChanged(int index) {
        try {
            reCalculateCatalystItemMap(index);
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#onCatalystItemContentsChanged", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：催化剂物品回调异常（已忽略）pos={} 槽={} 原因={}",
                    getPos(), index + 1, failure.toString());
        }
    }

    private void onCatalystFluidContentsChanged(int index) {
        try {
            reCalculateCatalystFluidMap(index);
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#onCatalystFluidContentsChanged", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：催化剂流体回调异常（已忽略）pos={} 槽={} 原因={}",
                    getPos(), index + 1, failure.toString());
        }
    }

    /** 催化剂或阈值变化后旧配方结论不再可靠，需要同时清理本机和镜像缓存。 */
    private void invalidateSlotRecipeCache(int slot) {
        try {
            LazyMEPatternTrait trait = (LazyMEPatternTrait) getMETrait();
            if (trait != null) {
                trait.clearSlotCache(slot);
                trait.notifyPatternChange(slot);
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#invalidateSlotRecipeCache.local", ignored);
        }
        try {
            notifyProxySlotRemoved(slot);
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#invalidateSlotRecipeCache.proxy", ignored);
        }
    }

    /** 催化剂表按需建仓且允许空占位，显式稀疏存档可避免实体化未使用的仓。 */
    private void saveCatalystSlots(CompoundTag tag) {
        try {
            int slots = Math.max(catalystItems.size(), Math.max(catalystFluids.size(), cacheRecipeCount.size()));
            CompoundTag root = new CompoundTag();
            for (int i = 0; i < slots; i++) {
                CompoundTag entry = new CompoundTag();
                boolean any = false;
                ItemStackTransfer items = catalystItemTransferAt(i);
                if (PatternStacks.hasAnyCatalystItem(items)) {
                    entry.put(NBT_CATALYST_ITEMS, items.serializeNBT());
                    any = true;
                }
                FluidTransferList fluids = catalystFluidTransferAt(i);
                if (PatternStacks.hasAnyCatalystFluid(fluids)) {
                    entry.put(NBT_CATALYST_FLUIDS, fluids.serializeNBT());
                    any = true;
                }
                int count = cacheRecipeCountAt(i);
                if (count != DEFAULT_CACHE_RECIPE_COUNT) {
                    entry.putByte(NBT_CATALYST_COUNT, (byte) count);
                    any = true;
                }
                if (any) {
                    root.put("s" + i, entry);
                }
            }
            if (!root.isEmpty()) {
                tag.put(NBT_LAZY_CATALYST_SLOTS, root);
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#saveCatalystSlots", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：催化剂表写档失败（已忽略）pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    /** 反序列化不触发内容回调，恢复后需重算催化剂表，使其与仓内容一致。 */
    private void loadCatalystSlots(CompoundTag tag) {
        try {
            CompoundTag root = tag.getCompound(NBT_LAZY_CATALYST_SLOTS);
            if (root.isEmpty()) {
                return;
            }
            int slots = getPatternSlotCount();
            for (String key : root.getAllKeys()) {
                if (!key.startsWith("s")) {
                    continue;
                }
                int index;
                try {
                    index = Integer.parseInt(key.substring(1));
                } catch (RuntimeException ignored) {
                    continue;
                }
                if (index < 0 || index >= slots) {
                    continue;
                }
                CompoundTag entry = root.getCompound(key);
                if (entry.contains(NBT_CATALYST_COUNT)) {
                    setCacheRecipeCount(index, entry.getByte(NBT_CATALYST_COUNT));
                }
                boolean hasItems = entry.contains(NBT_CATALYST_ITEMS, Tag.TAG_COMPOUND);
                boolean hasFluids = entry.contains(NBT_CATALYST_FLUIDS, Tag.TAG_COMPOUND);
                if (!hasItems && !hasFluids) {
                    continue;
                }
                ensureCatalystTables(index);
                if (hasItems) {
                    ItemStackTransfer items = catalystItemTransferAt(index);
                    if (items != null) {
                        items.deserializeNBT(entry.getCompound(NBT_CATALYST_ITEMS));
                    }
                }
                if (hasFluids) {
                    FluidTransferList fluids = catalystFluidTransferAt(index);
                    if (fluids != null) {
                        fluids.deserializeNBT(entry.getCompound(NBT_CATALYST_FLUIDS));
                    }
                }
                reCalculateCatalystItemMap(index);
                reCalculateCatalystFluidMap(index);
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#loadCatalystSlots", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：催化剂表读档失败（已忽略）pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    /** 控件槽号属于菜单协议，切页只换代理目标，不能动态增删控件造成两侧槽号错位。 */
    final class CatalystItemView implements IItemTransfer {

        private int target;

        /** 客户端真仓不是服务端库存，渲染和点击预测必须读取同步下来的本地缓存。 */
        private final ItemStack[] clientCache = new ItemStack[CATALYST_ITEM_SLOTS];

        CatalystItemView() {
            for (int i = 0; i < clientCache.length; i++) {
                clientCache[i] = ItemStack.EMPTY;
            }
        }

        void setTarget(int index) {
            this.target = index;
        }

        private ItemStackTransfer serverTransfer() {
            if (isRemote()) {
                return null;
            }
            ensureCatalystTables(target);
            return catalystItemTransferAt(target);
        }

        @Override
        public int getSlots() {
            // 代理槽数必须两侧固定一致，不能随尚未同步的本侧容量变化。
            return CATALYST_ITEM_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (isRemote()) {
                return slot >= 0 && slot < clientCache.length ? clientCache[slot] : ItemStack.EMPTY;
            }
            ItemStackTransfer transfer = serverTransfer();
            return transfer != null ? transfer.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            ItemStack value = stack == null ? ItemStack.EMPTY : stack;
            if (isRemote()) {
                if (slot >= 0 && slot < clientCache.length) {
                    clientCache[slot] = value;
                }
                return;
            }
            if (slot < 0 || slot >= CATALYST_ITEM_SLOTS) {
                return;
            }
            ItemStackTransfer transfer = serverTransfer();
            if (transfer == null) {
                return;
            }
            // ItemStackTransfer.setStackInSlot 不触发内容回调，代理写入后必须补通知。
            transfer.setStackInSlot(slot, value);
            onCatalystItemContentsChanged(target);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate, boolean notify) {
            if (isRemote()) {
                // 客户端模拟插入供槽位预测查询，不能因没有服务端真仓就一律拒绝点击。
                return simulatedInsert(slot, stack, simulate);
            }
            if (slot < 0 || slot >= CATALYST_ITEM_SLOTS) {
                return stack;
            }
            ItemStackTransfer transfer = serverTransfer();
            if (transfer == null) {
                return stack;
            }
            ItemStack remainder = transfer.insertItem(slot, stack, simulate, notify);
            if (!simulate) {
                onCatalystItemContentsChanged(target);
            }
            return remainder;
        }

        private ItemStack simulatedInsert(int slot, ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (slot < 0 || slot >= clientCache.length || !isItemValid(slot, stack)) {
                return stack;
            }
            ItemStack cached = clientCache[slot];
            int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
            if (!cached.isEmpty()) {
                if (!ItemTransferHelper.canItemStacksStack(stack, cached)) {
                    return stack;
                }
                limit -= cached.getCount();
            }
            if (limit <= 0) {
                return stack;
            }
            int moved = Math.min(limit, stack.getCount());
            if (!simulate) {
                ItemStack grown = cached.isEmpty() ? stack.copy() : cached.copy();
                grown.setCount(cached.isEmpty() ? moved : cached.getCount() + moved);
                clientCache[slot] = grown;
            }
            if (moved >= stack.getCount()) {
                return ItemStack.EMPTY;
            }
            ItemStack remainder = stack.copy();
            remainder.setCount(stack.getCount() - moved);
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate, boolean notify) {
            if (isRemote()) {
                // 客户端提取预测须根据同步缓存回答，否则服务端有物品时仍会拒绝点击。
                if (slot < 0 || slot >= clientCache.length || amount <= 0) {
                    return ItemStack.EMPTY;
                }
                ItemStack cached = clientCache[slot];
                if (cached.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                ItemStack taken = cached.copy();
                taken.setCount(Math.min(amount, cached.getCount()));
                return taken;
            }
            if (slot < 0 || slot >= CATALYST_ITEM_SLOTS) {
                return ItemStack.EMPTY;
            }
            ItemStackTransfer transfer = serverTransfer();
            if (transfer == null) {
                return ItemStack.EMPTY;
            }
            ItemStack extracted = transfer.extractItem(slot, amount, simulate, notify);
            if (!simulate) {
                onCatalystItemContentsChanged(target);
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack == null || !(stack.getItem() instanceof ProcessingPatternItem);
        }

        @Override
        public void onContentsChanged() {
            if (!isRemote()) {
                onCatalystItemContentsChanged(target);
            }
        }

        @Override
        public Object createSnapshot() {
            ItemStack[] copy = new ItemStack[CATALYST_ITEM_SLOTS];
            for (int i = 0; i < copy.length; i++) {
                copy[i] = getStackInSlot(i).copy();
            }
            return copy;
        }

        @Override
        public void restoreFromSnapshot(Object snapshot) {
            if (!(snapshot instanceof ItemStack[] stacks) || stacks.length != CATALYST_ITEM_SLOTS) {
                return;
            }
            for (int i = 0; i < stacks.length; i++) {
                setStackInSlot(i, stacks[i] == null ? ItemStack.EMPTY : stacks[i].copy());
            }
        }
    }

    /** 上游 UI 为 FluidTransferList 的每个元素建一个罐控件，因此需一罐一个代理。 */
    final class CatalystFluidView implements IFluidTransfer {

        private final int tankIndex;

        private int target;

        /** FluidStack.empty 是共享单例，返回副本并整体替换缓存，避免原地修改污染其他空罐。 */
        private FluidStack clientFluid = FluidStack.empty();

        CatalystFluidView(int tankIndex) {
            this.tankIndex = tankIndex;
        }

        void setTarget(int index) {
            this.target = index;
        }

        private FluidTransferList serverFluids() {
            if (isRemote()) {
                return null;
            }
            ensureCatalystTables(target);
            return catalystFluidTransferAt(target);
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (isRemote()) {
                return clientFluid.copy();
            }
            FluidTransferList fluids = serverFluids();
            return fluids != null ? fluids.getFluidInTank(tankIndex) : FluidStack.empty();
        }

        @Override
        public void setFluidInTank(int tank, FluidStack stack) {
            FluidStack value = stack == null ? FluidStack.empty() : stack.copy();
            if (isRemote()) {
                clientFluid = value;
                return;
            }
            FluidTransferList fluids = serverFluids();
            if (fluids == null) {
                return;
            }
            fluids.setFluidInTank(tankIndex, value);
            onCatalystFluidContentsChanged(target);
        }

        @Override
        public long getTankCapacity(int tank) {
            return CATALYST_TANK_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        @Override
        public long fill(int tank, FluidStack resource, boolean simulate, boolean notify) {
            if (isRemote()) {
                return 0L;
            }
            FluidTransferList fluids = serverFluids();
            if (fluids == null) {
                return 0L;
            }
            long filled = fluids.fill(tankIndex, resource, simulate, notify);
            if (!simulate) {
                onCatalystFluidContentsChanged(target);
            }
            return filled;
        }

        @Override
        public boolean supportsFill(int tank) {
            return true;
        }

        @Override
        public FluidStack drain(int tank, FluidStack resource, boolean simulate, boolean notify) {
            if (isRemote()) {
                return FluidStack.empty();
            }
            FluidTransferList fluids = serverFluids();
            if (fluids == null) {
                return FluidStack.empty();
            }
            FluidStack drained = fluids.drain(tankIndex, resource, simulate, notify);
            if (!simulate) {
                onCatalystFluidContentsChanged(target);
            }
            return drained;
        }

        @Override
        public boolean supportsDrain(int tank) {
            return true;
        }

        @Override
        public void onContentsChanged() {
            if (!isRemote()) {
                onCatalystFluidContentsChanged(target);
            }
        }

        @Override
        public Object createSnapshot() {
            return new FluidStack[]{ getFluidInTank(0).copy() };
        }

        @Override
        public void restoreFromSnapshot(Object snapshot) {
            if (!(snapshot instanceof FluidStack[] stacks) || stacks.length != 1) {
                return;
            }
            setFluidInTank(0, stacks[0] == null ? FluidStack.empty() : stacks[0].copy());
        }
    }

    /** UI 管理器直接写阈值缓冲，必须回填正规表并失效配方缓存，设置才会用于加工。 */
    private void onCacheCountChangedFromUi(int slot, byte[] buffer) {
        try {
            if (buffer != null && buffer.length > 0 && slot >= 0) {
                setCacheRecipeCount(slot, buffer[0]);
            }
            invalidateSlotRecipeCache(slot);
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#onCacheCountChangedFromUi", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：缓存阈值同步失败（已忽略）pos={} 槽={} 原因={}",
                    getPos(), slot + 1, failure.toString());
        }
    }

    public int getCatalystViewSlot() {
        return catalystViewSlot;
    }

    /** 客户端容量可能尚未同步，不能用它钳位服务端选中的槽号。 */
    public void setCatalystViewSlot(int slot) {
        if (isRemote()) {
            this.catalystViewSlot = slot;
            return;
        }
        this.catalystViewSlot = Math.max(1, Math.min(getPatternSlotCount(), slot));
    }

    public boolean isCatalystPageOpen() {
        return catalystPageOpen;
    }

    public void setCatalystPageOpen(boolean open) {
        this.catalystPageOpen = open;
    }

    @Override
    public Widget createUIWidget() {
        int capacity = patternWidgetCapacity();
        int rows = Math.max(1, Math.min(ROWS_PER_PAGE,
                (capacity + PATTERNS_PER_ROW - 1) / PATTERNS_PER_ROW));
        int uiWidth = Math.max(PATTERNS_PER_ROW * 18 + 16, 106);
        int uiHeight = rows * 18 + 36;
        PaginationUIManager pager = new PaginationUIManager(PATTERNS_PER_ROW, rows, capacity, uiWidth, uiHeight,
                this::onSlotChangedFromGui, patternInventory);
        WidgetGroup group = new WidgetGroup(0, 0, uiWidth, uiHeight);
        group.addWidget(new LabelWidget(8, 2, () -> Component.translatable(isOnline()
                ? "gtceu.gui.me_network.online"
                : "gtceu.gui.me_network.offline").getString()));
        group.addWidget(new AETextInputButtonWidget(uiWidth - 78, 2, 70, 10)
                .setText(getCustomName())
                .setOnConfirm(this::onPlayerConfirmedCustomName)
                .setButtonTooltips(Component.translatable("gui.gtceu.rename.desc")));
        group.addWidget(pager.createPaginationUI(slot -> {
            setCatalystViewSlot(slot + 1);
            setCatalystPageOpen(true);
        }));
        // LDLib 从末尾开始派发点击；覆盖层必须放在分页控件之后。
        group.addWidget(new PatternSlotRightClickOverlay(group, uiWidth, uiHeight,
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

    /** 客户端控件会把动作发给服务端，本地不能同时跑内容变化收尾或改容量。 */
    private void onSlotChangedFromGui(int slot) {
        if (isRemote()) {
            return;
        }
        onPatternInventoryChanged();
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel panel) {
        super.attachConfigurators(panel);
        try {
            if (getAvailableRecipeTypes().size() > 1) {
                panel.attachConfigurators(new RecipeModeConfigurator(this));
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#attachConfigurators.recipeModePage", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：配方模式页挂载失败 pos={} 原因={}",
                    getPos(), failure.toString());
        }
        try {
            panel.attachConfigurators(new ScaleConfigurator(this));
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#attachConfigurators.scalePage", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：样板倍率页挂载失败 pos={} 原因={}",
                    getPos(), failure.toString());
        }
        try {
            panel.attachConfigurators(new FoaConfigurator(this));
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#attachConfigurators.foaPage", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：神锻样板模式页挂载失败 pos={} 原因={}",
                    getPos(), failure.toString());
        }
        try {
            CatalystAccessConfigurator catalystPage = new CatalystAccessConfigurator(this);
            this.activeCatalystPage = catalystPage;
            int before = panel.getTabs().size();
            panel.attachConfigurators(catalystPage);
            List<ConfiguratorPanel.Tab> tabs = panel.getTabs();
            catalystPage.bindPanel(panel, tabs.size() > before ? tabs.get(before) : null);
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#attachConfigurators.catalystPage", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：专属催化剂槽页挂载失败 pos={} 原因={}",
                    getPos(), failure.toString());
        }
        try {
            panel.attachConfigurators(new PatternCircuitConfigurator(
                    this::getEmbeddedCircuitConfig,
                    this::setEmbeddedCircuitConfig,
                    this::isSkipExistingCircuitPatterns,
                    (data, value) -> setSkipExistingCircuitPatterns(Boolean.TRUE.equals(value)),
                    this::applyConfiguredCircuit,
                    this::removeAllPatternCircuits));
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#attachConfigurators.circuitPage", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：样板电路写入页挂载失败 pos={} 原因={}",
                    getPos(), failure.toString());
        }
        try {
            panel.attachConfigurators(new ArchiveConfigurator(this));
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#attachConfigurators.archivePage", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：归档页挂载失败 pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    public static final int MIN_EMBEDDED_CIRCUIT = 1;
    public static final int MAX_EMBEDDED_CIRCUIT = 32;

    @DescSynced
    @Persisted
    private int embeddedCircuitConfig = 1;

    @DescSynced
    @Persisted
    private boolean skipExistingCircuitPatterns = true;

    private static final int CIRCUIT_EMBED_LOG_INTERVAL_TICKS = 10;

    private static final int CIRCUIT_EMBED_LOG_SLOTS = 8;

    /** 限流哨兵不能使用 Long.MIN_VALUE，否则时间差溢出可能使日志永久静默。 */
    private long lastCircuitEmbedLogTick = -1000;

    public int getEmbeddedCircuitConfig() {
        return embeddedCircuitConfig;
    }

    public void setEmbeddedCircuitConfig(int value) {
        this.embeddedCircuitConfig = Math.max(MIN_EMBEDDED_CIRCUIT, Math.min(MAX_EMBEDDED_CIRCUIT, value));
    }

    public boolean isSkipExistingCircuitPatterns() {
        return skipExistingCircuitPatterns;
    }

    public void setSkipExistingCircuitPatterns(boolean value) {
        this.skipExistingCircuitPatterns = value;
    }

    private void applyConfiguredCircuit(ClickData data) {
        if (data == null || data.isRemote) {
            return;
        }
        embedCircuitToPatterns(embeddedCircuitConfig, !skipExistingCircuitPatterns);
    }

    private void removeAllPatternCircuits(ClickData data) {
        if (data == null || data.isRemote) {
            return;
        }
        rewritePatternCircuits(false, MIN_EMBEDDED_CIRCUIT, true);
    }

    private void embedCircuitToPatterns(int circuit, boolean overwrite) {
        rewritePatternCircuits(true, circuit, overwrite);
    }

    private void rewritePatternCircuits(boolean embed, int circuit, boolean overwrite) {
        if (isRemote()) {
            return;
        }
        try {
            Level level = getLevel();
            if (level == null) {
                return;
            }
            MEBufferPatternHelper helper;
            try {
                helper = getRealPatternHelper();
            } catch (Throwable ignored) {
                SwallowedErrors.report("LazyPatternAssemblyPartMachine#rewritePatternCircuits.patternHelper", ignored);
                helper = null;
            }
            if (helper == null) {
                GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪样板电路{}：拿不到样板助手，本次不动任何样板 pos={}",
                        embed ? "写入" : "清空", getPos());
                return;
            }
            int config = Math.max(MIN_EMBEDDED_CIRCUIT, Math.min(MAX_EMBEDDED_CIRCUIT, circuit));

            List<Integer> targetSlots = new ArrayList<>();
            List<ItemStack> targetStacks = new ArrayList<>();
            int calcFailed = 0;
            int capacity = getCapacity();
            for (int i = 0; i < capacity; i++) {
                ItemStack current;
                try {
                    current = patternInventory.getStackInSlot(i);
                } catch (Throwable ignored) {
                    SwallowedErrors.report("LazyPatternAssemblyPartMachine#rewritePatternCircuits.readSlot", ignored);
                    continue;
                }
                if (current == null || current.isEmpty()) {
                    continue;
                }
                ItemStack next;
                try {
                    next = embed
                            ? helper.createPatternWithCircuit(current, config, overwrite, level)
                            : helper.removeCircuitFromPattern(current, level);
                } catch (Throwable failure) {
                    SwallowedErrors.report("LazyPatternAssemblyPartMachine#rewritePatternCircuits.calculateSlot", failure);
                    calcFailed++;
                    continue;
                }
                if (next == null || next.isEmpty()) {
                    continue;
                }
                if (ItemStack.matches(current, next)) {
                    continue;
                }
                targetSlots.add(i);
                targetStacks.add(next);
            }

            int applied = 0;
            int writeFailed = 0;
            for (int k = 0; k < targetSlots.size(); k++) {
                try {
                    patternInventory.setStackInSlot(targetSlots.get(k), targetStacks.get(k));
                    applied++;
                } catch (Throwable ignored) {
                    SwallowedErrors.report("LazyPatternAssemblyPartMachine#rewritePatternCircuits.writeSlot", ignored);
                    writeFailed++;
                }
            }

            if (applied > 0) {
                // 批量修改只统一收尾一次，避免逐槽刷新导致 AE 重读整仓。
                try {
                    onPatternInventoryChanged();
                } catch (Throwable failure) {
                    SwallowedErrors.report("LazyPatternAssemblyPartMachine#rewritePatternCircuits.afterWrite", failure);
                    GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪样板电路{}：写入后的收尾失败"
                                    + "（样板本身已经写好，缓存最迟由下一 tick 的签名轮询兜住）pos={} 原因={}",
                            embed ? "写入" : "清空", getPos(), failure.toString());
                }
            }

            try {
                long now = level.getGameTime();
                if (now - lastCircuitEmbedLogTick >= CIRCUIT_EMBED_LOG_INTERVAL_TICKS) {
                    lastCircuitEmbedLogTick = now;
                    StringBuilder slotText = new StringBuilder();
                    for (int k = 0; k < targetSlots.size() && k < CIRCUIT_EMBED_LOG_SLOTS; k++) {
                        if (k > 0) {
                            slotText.append(',');
                        }
                        slotText.append(targetSlots.get(k) + 1);
                    }
                    if (targetSlots.size() > CIRCUIT_EMBED_LOG_SLOTS) {
                        slotText.append("...");
                    }
                    GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪样板电路{}: 目标电路={} 覆盖已有={} 改动槽数={}"
                                    + " 槽位(1起)=[{}] 算不动={} 写入失败={}"
                                    + " —— 已作废样板缓存，AE 样板表由 lazyTick 合并重取 pos={}",
                            embed ? "写入" : "清空",
                            embed ? String.valueOf(config) : "-",
                            embed ? String.valueOf(overwrite) : "-",
                            applied, slotText.toString(), calcFailed, writeFailed, getPos());
                }
            } catch (Throwable ignored) {
                SwallowedErrors.report("LazyPatternAssemblyPartMachine#rewritePatternCircuits.summary", ignored);
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#rewritePatternCircuits", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪样板电路{}异常（本次操作已中止，仓内样板保持操作前状态）pos={} 原因={}",
                    embed ? "写入" : "清空", getPos(), failure.toString());
        }
    }

    @DescSynced
    private boolean archiveIncludeSettings = false;

    @DescSynced
    private boolean archiveIncludeName = false;

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

    /** 默认追加避免覆盖已有样板；按原槽对齐时占用槽也必须跳过。 */
    @DescSynced
    private boolean archiveAlignSlots = false;

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

    public void afterArchiveBulkChange() {
        onPatternInventoryChanged();
    }

    ItemStackTransfer getPatternInventory() {
        return this.patternInventory;
    }

    private String archiveDimensionId() {
        return PatternArchiveSnapshots.dimensionId(this::getLevel);
    }

    public LazyPatternArchiveCodec.Payload buildArchivePayload(boolean includeSettings, boolean includeName) {
        LazyPatternArchiveCodec.Payload payload = new LazyPatternArchiveCodec.Payload();
        payload.capacity = getCapacity();
        payload.includeSettings = includeSettings;
        payload.includeName = includeName;
        payload.name = getCustomName() == null ? "" : getCustomName();
        payload.dimension = archiveDimensionId();
        payload.pos = getPos() == null ? 0L : getPos().asLong();
        if (includeSettings) {
            CompoundTag settings = new CompoundTag();
            settings.putInt(NBT_PATTERN_SCALE, patternScale);
            settings.putInt(NBT_PATTERN_DIV_SCALE, patternDivScale);
            settings.putBoolean(NBT_FOA_MODE, foaMode);
            settings.putInt(NBT_FOA_MULTIPLIER, foaMultiplier);
            settings.putBoolean(NBT_OUTPUT_MODE, isOutputMode);
            payload.settings = settings;
        }
        return PatternArchiveSnapshots.buildPayload(payload, getCapacity(),
                i -> patternInventory.getStackInSlot(i), i -> {
                    int cached = recipeTypeCacheAt(i);
                    return cached == TYPE_NOT_TRIED ? null : cached;
                }, i -> manualSlotTypes.get(i), this::buildArchivedCatalyst);
    }

    /** 只归档有内容的催化剂仓，避免粘贴实体化空仓。 */
    private CompoundTag buildArchivedCatalyst(int slot) {
        try {
            return PatternArchiveSnapshots.buildCatalyst(entry -> {
                ItemStackTransfer items = catalystItemTransferAt(slot);
                if (PatternStacks.hasAnyCatalystItem(items)) {
                    entry.put(NBT_CATALYST_ITEMS, items.serializeNBT());
                }
                FluidTransferList fluids = catalystFluidTransferAt(slot);
                if (PatternStacks.hasAnyCatalystFluid(fluids)) {
                    entry.put(NBT_CATALYST_FLUIDS, fluids.serializeNBT());
                }
            }, () -> cacheRecipeCountAt(slot), DEFAULT_CACHE_RECIPE_COUNT);
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#buildArchivedCatalyst", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：第 {} 槽催化剂打包失败（该槽只搬样板）pos={} 原因={}",
                    slot + 1, getPos(), failure.toString());
            return null;
        }
    }

    /** 仅在档案成功写入后清源，且清除范围须与归档内容一致，不能删除未搬运状态。 */
    void clearArchiveContent() {
        try {
            int capacity = getCapacity();
            for (int i = 0; i < capacity; i++) {
                try {
                    patternInventory.setStackInSlot(i, ItemStack.EMPTY);
                } catch (Throwable ignored) {
                    SwallowedErrors.report("LazyPatternAssemblyPartMachine#clearArchiveContent.patternInventorySlot", ignored);
                }
                try {
                    setRecipeTypeCache(i, TYPE_NOT_TRIED);
                } catch (Throwable ignored) {
                    SwallowedErrors.report("LazyPatternAssemblyPartMachine#clearArchiveContent.recipeTypeSlot", ignored);
                }
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#clearArchiveContent.patterns", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：清空样板失败（已尽力而为）pos={} 原因={}",
                    getPos(), failure.toString());
        }
        try {
            manualSlotTypes.clear();
            refreshManualAssignDisplay();
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#clearArchiveContent.manualTypes", ignored);
        }
        try {
            int tables = Math.max(catalystItems.size(),
                    Math.max(catalystFluids.size(), cacheRecipeCount.size()));
            for (int i = 0; i < tables; i++) {
                ItemStackTransfer items = catalystItemTransferAt(i);
                if (items != null) {
                    for (int s = 0; s < items.getSlots(); s++) {
                        items.setStackInSlot(s, ItemStack.EMPTY);
                    }
                    reCalculateCatalystItemMap(i);
                }
                FluidTransferList fluids = catalystFluidTransferAt(i);
                if (fluids != null) {
                    for (int t = 0; t < fluids.getTanks(); t++) {
                        fluids.setFluidInTank(t, FluidStack.empty());
                    }
                    reCalculateCatalystFluidMap(i);
                }
                if (i < cacheRecipeCount.size()) {
                    cacheRecipeCount.set(i, (byte) DEFAULT_CACHE_RECIPE_COUNT);
                }
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#clearArchiveContent.catalysts", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：清空催化剂失败（已忽略）pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    void writeArchivedRecipeType(int slot, int recipeType) {
        try {
            ensureRecipeTypeSlotCapacity();
            setRecipeTypeCache(slot, recipeType);
        } catch (Throwable ignored) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#writeArchivedRecipeType", ignored);
        }
    }

    void writeArchivedManualType(int slot, ResourceLocation typeId) {
        modeContext().writeArchivedManualType(slot, typeId);
    }

    boolean writeArchivedCatalyst(int slot, CompoundTag catalyst) {
        if (catalyst == null || catalyst.isEmpty() || slot < 0 || slot >= maxPatternCount) {
            return false;
        }
        boolean wrote = false;
        try {
            boolean hasItems = catalyst.contains(NBT_CATALYST_ITEMS, Tag.TAG_COMPOUND);
            boolean hasFluids = catalyst.contains(NBT_CATALYST_FLUIDS, Tag.TAG_COMPOUND);
            if (hasItems || hasFluids) {
                ensureCatalystTables(slot);
            }
            if (hasItems) {
                ItemStackTransfer items = catalystItemTransferAt(slot);
                if (items != null) {
                    items.deserializeNBT(catalyst.getCompound(NBT_CATALYST_ITEMS));
                    // 反序列化不会触发内容回调，必须补通知以更新催化剂表。
                    try {
                        items.onContentsChanged();
                    } catch (Throwable ignored) {
                        SwallowedErrors.report("LazyPatternAssemblyPartMachine#writeArchivedCatalyst.itemsContentsChanged", ignored);
                    }
                    reCalculateCatalystItemMap(slot);
                    wrote = true;
                }
            }
            if (hasFluids) {
                FluidTransferList fluids = catalystFluidTransferAt(slot);
                if (fluids != null) {
                    fluids.deserializeNBT(catalyst.getCompound(NBT_CATALYST_FLUIDS));
                    try {
                        fluids.onContentsChanged();
                    } catch (Throwable ignored) {
                        SwallowedErrors.report("LazyPatternAssemblyPartMachine#writeArchivedCatalyst.fluidsContentsChanged", ignored);
                    }
                    reCalculateCatalystFluidMap(slot);
                    wrote = true;
                }
            }
            if (catalyst.contains(NBT_CATALYST_COUNT, Tag.TAG_BYTE)) {
                setCacheRecipeCount(slot, catalyst.getByte(NBT_CATALYST_COUNT));
                invalidateSlotRecipeCache(slot);
                wrote = true;
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#writeArchivedCatalyst", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：第 {} 槽催化剂写入失败（该槽催化剂保持原样）pos={} 原因={}",
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
            if (settings.contains(NBT_PATTERN_SCALE, Tag.TAG_INT)) {
                patternScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, settings.getInt(NBT_PATTERN_SCALE)));
            }
            if (settings.contains(NBT_PATTERN_DIV_SCALE, Tag.TAG_INT)) {
                patternDivScale = Math.max(MIN_SCALE,
                        Math.min(MAX_SCALE, settings.getInt(NBT_PATTERN_DIV_SCALE)));
            }
            if (settings.contains(NBT_FOA_MODE, Tag.TAG_BYTE)) {
                foaMode = settings.getBoolean(NBT_FOA_MODE);
            }
            if (settings.contains(NBT_FOA_MULTIPLIER, Tag.TAG_INT)) {
                foaMultiplier = Math.max(MIN_FOA_MULTIPLIER,
                        Math.min(MAX_FOA_MULTIPLIER, settings.getInt(NBT_FOA_MULTIPLIER)));
            }
            if (settings.contains(NBT_OUTPUT_MODE, Tag.TAG_BYTE)) {
                isOutputMode = settings.getBoolean(NBT_OUTPUT_MODE);
            }
            invalidateRewriteCacheQuietly();
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#writeArchivedSettings", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：设置类写入失败（已忽略，按原设置）pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    void writeArchivedName(String name) {
        patternNameController().writeArchivedName(name);
    }

    public int[] archiveSummary() {
        return PatternArchiveSnapshots.summary(getCapacity(), i -> patternInventory.getStackInSlot(i));
    }

    public CompoundTag copyToTag(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        try {
            LazyPatternArchiveCodec.Payload payload =
                    buildArchivePayload(archiveIncludeSettings, archiveIncludeName);
            CompoundTag written = LazyPatternArchiveCodec.encode(payload);
            if (written != null) {
                tag.put(LazyPatternArchiveCodec.NBT_STAMP, written);
            }
            return tag;
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#copyToTag", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：复制失败（本机未改动）pos={} 原因={}",
                    getPos(), failure.toString());
            return tag;
        }
    }

    public boolean pasteFromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }
        try {
            LazyPatternArchiveCodec.Payload payload = readPayloadFromTag(tag);
            if (payload == null || payload.isEmpty()) {
                return false;
            }
            LazyPatternArchiveCodec.ApplyResult result = LazyPatternArchiveCodec.apply(
                    this, payload, archiveIncludeSettings, archiveIncludeName, archivePlaceMode());
            return result != null && result.ok;
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#pasteFromTag", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：粘贴失败（已中止，仓内容按操作前）pos={} 原因={}",
                    getPos(), failure.toString());
            return false;
        }
    }

    public void copyFromTag(CompoundTag tag, net.minecraft.server.level.ServerPlayer player) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        try {
            LazyPatternArchiveCodec.Payload payload = readPayloadFromTag(tag);
            if (payload == null || payload.isEmpty()) {
                return;
            }
            LazyPatternArchiveCodec.ApplyResult result = LazyPatternArchiveCodec.apply(
                    this, payload, archiveIncludeSettings, archiveIncludeName, archivePlaceMode());
            GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪归档：按档案覆盖本仓 player={} pos={} 结果={} 样板={} 元数据={}",
                    player == null ? "-" : player.getGameProfile().getName(), getPos(),
                    result == null ? "?" : (result.ok ? result.messageKey : "拒绝/空"),
                    result == null ? 0 : result.patternCount, result == null ? 0 : result.metadataCount);
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#copyFromTag", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：按档案覆盖失败（已中止）pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    public CompoundTag cutToTag(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        try {
            LazyPatternArchiveCodec.Payload payload =
                    buildArchivePayload(archiveIncludeSettings, archiveIncludeName);
            // 先序列化并写入目标 tag，失败时不能清源。
            CompoundTag written = writePayloadToTag(payload);
            if (written == null) {
                GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：剪切中止 —— 档案序列化失败，本机一张样板都没动 pos={}",
                        getPos());
                return tag;
            }
            tag.put(LazyPatternArchiveCodec.NBT_STAMP, written);
            clearArchiveContent();
            try {
                onPatternInventoryChanged();
            } catch (Throwable failure) {
                SwallowedErrors.report("LazyPatternAssemblyPartMachine#cutToTag.afterClear", failure);
                GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：剪切后的收尾失败"
                                + "（样板已清空，缓存最迟由下一 tick 的签名轮询兜住）pos={} 原因={}",
                        getPos(), failure.toString());
            }
            GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪归档：剪切完成 张数={} 元数据={} pos={}",
                    payload.patternCount, payload.entries.size(), getPos());
            return tag;
        } catch (Throwable failure) {
            SwallowedErrors.report("LazyPatternAssemblyPartMachine#cutToTag", failure);
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：剪切异常（已中止）pos={} 原因={}",
                    getPos(), failure.toString());
            return tag;
        }
    }

    private static CompoundTag writePayloadToTag(LazyPatternArchiveCodec.Payload payload) {
        return LazyPatternArchiveCodec.encode(payload);
    }

    private static LazyPatternArchiveCodec.Payload readPayloadFromTag(CompoundTag tag) {
        return LazyPatternArchiveCodec.readFromTag(tag);
    }

    static final class ScaleConfigurator implements IFancyConfigurator {

        private final LazyPatternAssemblyPartMachine machine;

        ScaleConfigurator(LazyPatternAssemblyPartMachine machine) {
            this.machine = machine;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.pattern_assembly.scale.title");
        }

        @Override
        public IGuiTexture getIcon() {
            return new ResourceTexture("gtceu:textures/item/portable_debug_scanner.png");
        }

        @Override
        public List<Component> getTooltips() {
            return List.of(Component.translatable("gtlmodular.pattern_assembly.scale.title"));
        }

        @Override
        public Widget createConfigurator() {
            WidgetGroup group = new WidgetGroup(0, 0, 124, 56);
            group.addWidget(new LabelWidget(4, 2,
                    Component.translatable("gtlmodular.pattern_assembly.scale.multiply.label").getString()));
            group.addWidget(PatternConfiguratorWidgets.scaleInput(4, 14, 58, 14,
                    machine::getPatternScale, machine::setPatternScale, MIN_SCALE, MAX_SCALE));
            group.addWidget(new LabelWidget(62, 2,
                    Component.translatable("gtlmodular.pattern_assembly.scale.divide.label").getString()));
            group.addWidget(PatternConfiguratorWidgets.scaleInput(58, 14, 58, 14,
                    machine::getPatternDivScale, machine::setPatternDivScale, MIN_SCALE, MAX_SCALE));
            group.addWidget(PatternConfiguratorWidgets.applyButton(4, 34, 110, 14,
                    machine::invalidateRewriteCache));
            return group;
        }
    }

    static final class ArchiveConfigurator extends PatternArchiveConfigurator {
        ArchiveConfigurator(LazyPatternAssemblyPartMachine machine) {
            super(machine, Layout.LAZY, null);
        }
    }

    private static final class FoaConfigurator implements IFancyConfigurator {

        private final LazyPatternAssemblyPartMachine machine;

        FoaConfigurator(LazyPatternAssemblyPartMachine machine) {
            this.machine = machine;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.pattern_assembly.foa.title");
        }

        @Override
        public IGuiTexture getIcon() {
            try {
                var definition = com.gtladd.gtladditions.common.machine.multiblock.MultiBlockMachine.INSTANCE
                        .getFORGE_OF_THE_ANTICHRIST();
                if (definition != null) {
                    return new ItemStackTexture(definition.asStack());
                }
            } catch (RuntimeException ignored) {
            }
            return new ItemStackTexture(machine.getDefinition().asStack());
        }

        @Override
        public List<Component> getTooltips() {
            return List.of(Component.translatable("gtlmodular.pattern_assembly.foa.title"));
        }

        @Override
        public Widget createConfigurator() {
            WidgetGroup group = new WidgetGroup(0, 0, 124, 56);
            group.addWidget(PatternConfiguratorWidgets.foaToggle(6, 5, 20, 20,
                    machine::isFoaModeEnabled, machine::setFoaModeEnabled));
            group.addWidget(new LabelWidget(32, 10,
                    Component.translatable("gtlmodular.pattern_assembly.foa.mode").getString()));
            group.addWidget(new LabelWidget(6, 36,
                    Component.translatable("gtlmodular.pattern_assembly.foa.multiplier.label").getString()));
            group.addWidget(PatternConfiguratorWidgets.scaleInput(58, 31, 54, 20,
                    machine::getFoaPatternOutputMultiplier, machine::setFoaPatternOutputMultiplier,
                    MIN_FOA_MULTIPLIER, MAX_FOA_MULTIPLIER));
            return group;
        }
    }

    static final class RecipeModeConfigurator implements IFancyConfigurator {

        private final LazyPatternAssemblyPartMachine machine;

        RecipeModeConfigurator(LazyPatternAssemblyPartMachine machine) {
            this.machine = machine;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.pattern_assembly.mode.title");
        }

        @Override
        public IGuiTexture getIcon() {
            return new ResourceTexture("gtceu:textures/item/portable_debug_scanner.png");
        }

        @Override
        public List<Component> getTooltips() {
            List<GTRecipeType> types = machine.getAvailableRecipeTypes();
            if (types.isEmpty()) {
                return List.of(Component.translatable("gtlmodular.pattern_assembly.mode.none"));
            }
            int pick = Math.max(0, Math.min(types.size() - 1, machine.getModeCursorIndex()));
            int live = Math.max(0, Math.min(types.size() - 1, machine.getControllerRecipeTypeIndex()));
            return List.of(
                    Component.translatable("gtlmodular.pattern_assembly.mode.pick",
                            PatternConfiguratorWidgets.modeComponent(types.get(pick)).getString(), pick + 1, types.size()),
                    Component.translatable("gtlmodular.pattern_assembly.mode.live",
                            PatternConfiguratorWidgets.modeComponent(types.get(live)).getString(), live + 1, types.size()));
        }

        @Override
        public Widget createConfigurator() {
            WidgetGroup group = new WidgetGroup(0, 0, 240, 96);

            final int MODEPAGE_BAND = 0x33000000;
            final int MODEPAGE_ACCENT = 0xCC00D5FF;
            group.addWidget(new ImageWidget(4, 2, 232, 20, new ColorRectTexture(MODEPAGE_BAND)));
            group.addWidget(new ImageWidget(4, 2, 2, 20, new ColorRectTexture(MODEPAGE_ACCENT)));
            group.addWidget(new ImageWidget(4, 24, 232, 22, new ColorRectTexture(MODEPAGE_BAND)));
            group.addWidget(new ImageWidget(4, 24, 2, 22, new ColorRectTexture(MODEPAGE_ACCENT)));
            group.addWidget(new ButtonWidget(4, 4, 18, 16,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture("\u25c0")),
                    data -> cycle(data, false)));
            group.addWidget(new ImageWidget(26, 4, 178, 16,
                    new TextTexture(this::modeLabel).setType(TextTexture.TextType.LEFT)));
            group.addWidget(new ButtonWidget(208, 4, 18, 16,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture("\u25b6")),
                    data -> cycle(data, true)));

            group.addWidget(new LabelWidget(4, 30, () -> Component.translatable(
                    "gtlmodular.pattern_assembly.mode.manual_slot").getString()));
            group.addWidget(new IntInputWidget(46, 26, 92, 16,
                    machine::getManualAssignSlot, machine::setManualAssignSlot)
                    .setMin(1).setMax(machine.getPatternSlotCount()));
            group.addWidget(PatternConfiguratorWidgets.manualActionButton(144, 26, 42, 16, false,
                    () -> {
                        if (!machine.isRemote()) {
                            machine.assignManualType(machine.getManualAssignSlot(), false);
                        }
                    }));
            group.addWidget(PatternConfiguratorWidgets.manualActionButton(190, 26, 42, 16, true,
                    () -> {
                        if (!machine.isRemote()) {
                            machine.assignManualType(machine.getManualAssignSlot(), true);
                        }
                    }));
            group.addWidget(new LabelWidget(4, 50,
                    () -> PatternConfiguratorWidgets.assignStateText(machine.getManualAssignTypeId())));
            group.addWidget(new LabelWidget(4, 64, this::autoDetectStateText));
            group.addWidget(new LabelWidget(4, 78, this::liveModeText));
            return group;
        }

        private void cycle(com.lowdragmc.lowdraglib.gui.util.ClickData data, boolean forward) {
            int times = data != null && data.isShiftClick ? 5 : 1;
            for (int i = 0; i < times; i++) {
                machine.cycleRecipeType(forward);
            }
        }

        private String modeName(int index) {
            return PatternConfiguratorWidgets.modeName(machine.getAvailableRecipeTypes(), index);
        }

        private String modeLabel() {
            List<GTRecipeType> types = machine.getAvailableRecipeTypes();
            if (types.isEmpty()) {
                return Component.translatable("gtlmodular.pattern_assembly.mode.none").getString();
            }
            int index = Math.max(0, Math.min(types.size() - 1, machine.getModeCursorIndex()));
            return Component.translatable("gtlmodular.pattern_assembly.mode.pick",
                    modeName(index), index + 1, types.size()).getString();
        }

        private String autoDetectStateText() {
            String auto = machine.getManualAssignAutoTypeId();
            if (auto == null || auto.isEmpty()) {
                return Component.translatable("gtlmodular.pattern_assembly.mode.slot_auto.none").getString();
            }
            String autoName = PatternConfiguratorWidgets.recipeTypeName(auto);
            String manual = machine.getManualAssignTypeId();
            if (manual != null && !manual.isEmpty() && !manual.equals(auto)) {
                return Component.translatable("gtlmodular.pattern_assembly.mode.slot_conflict", autoName).getString();
            }
            return Component.translatable("gtlmodular.pattern_assembly.mode.slot_auto.assigned", autoName).getString();
        }

        private String liveModeText() {
            List<GTRecipeType> types = machine.getAvailableRecipeTypes();
            if (types.isEmpty()) {
                return "";
            }
            int live = Math.max(0, Math.min(types.size() - 1, machine.getControllerRecipeTypeIndex()));
            return Component.translatable("gtlmodular.pattern_assembly.mode.live",
                    modeName(live), live + 1, types.size()).getString();
        }
    }

    static final class CatalystAccessConfigurator implements IFancyConfigurator {

        private final LazyPatternAssemblyPartMachine machine;

        private int shownIndex = -1;

        private boolean wasPageOpen;

        private String lastAppliedPayload = "";

        private boolean loggedPayloadApply;

        private WidgetGroup root;
        private MEPatternCatalystUIManager catalystUI;

        private CatalystItemView itemView;
        private CatalystFluidView[] fluidViews;

        private byte[] countBuffer;

        private ConfiguratorPanel panel;
        private ConfiguratorPanel.Tab tab;

        private final ItemStack[] lastSentItems = new ItemStack[CATALYST_ITEM_SLOTS];
        private final FluidStack[] lastSentFluids = new FluidStack[CATALYST_FLUID_TANKS];

        private int lastSentIndex = Integer.MIN_VALUE;

        /** 固定面板尺寸，避免展开后顶出屏幕或触发重复定位。 */
        private static final int PAGE_WIDTH = 202;
        private static final int PAGE_CLOSED_HEIGHT = 44;
        private static final int PAGE_OPEN_HEIGHT = 128;

        CatalystAccessConfigurator(LazyPatternAssemblyPartMachine machine) {
            this.machine = machine;
            for (int i = 0; i < lastSentItems.length; i++) {
                lastSentItems[i] = ItemStack.EMPTY;
            }
            for (int i = 0; i < lastSentFluids.length; i++) {
                lastSentFluids[i] = FluidStack.empty();
            }
        }

        void bindPanel(ConfiguratorPanel panel, ConfiguratorPanel.Tab tab) {
            this.panel = panel;
            this.tab = tab;
        }

        private int slotCount() {
            return machine.getPatternSlotCount();
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

            // 代理只在真正查看目标槽时建仓，打开界面本身不应实体化全部催化剂仓。
            this.itemView = machine.new CatalystItemView();
            List<IFluidTransfer> tanks = new ArrayList<>(CATALYST_FLUID_TANKS);
            this.fluidViews = new CatalystFluidView[CATALYST_FLUID_TANKS];
            for (int i = 0; i < CATALYST_FLUID_TANKS; i++) {
                fluidViews[i] = machine.new CatalystFluidView(i);
                tanks.add(fluidViews[i]);
            }
            this.countBuffer = new byte[]{(byte) DEFAULT_CACHE_RECIPE_COUNT};
            catalystUI = new MEPatternCatalystUIManager(0,
                    new IItemTransfer[]{itemView},
                    new FluidTransferList[]{new FluidTransferList(tanks)},
                    countBuffer,
                    ignored -> machine.onCacheCountChangedFromUi(currentViewIndex(), countBuffer));
            catalystUI.setSelfPosition(2, 42);
            catalystUI.setSize(PAGE_WIDTH - 8, PAGE_OPEN_HEIGHT - 48);
            catalystUI.setVisible(false);
            catalystUI.setActive(false);
            root.waitToAdded(catalystUI);
            // 两侧在建界面时一次创建全部槽控件，避免切页改变菜单槽号映射。
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
                if (itemView != null) {
                    itemView.setTarget(index);
                }
                if (fluidViews != null) {
                    for (CatalystFluidView view : fluidViews) {
                        view.setTarget(index);
                    }
                }
            }
            if (!catalystUI.isVisible()) {
                catalystUI.setVisible(true);
                catalystUI.setActive(true);
            }
            if (root != null && root.getSizeHeight() != PAGE_OPEN_HEIGHT) {
                root.setSize(PAGE_WIDTH, PAGE_OPEN_HEIGHT);
            }
            if (machine.isRemote()) {
                applyCatalystViewFromMachine();
            } else {
                publishCatalystViewToMachine();
            }
        }

        /** 只在内容或槽号变化时序列化，避免每 tick 重建同步文本。 */
        private void publishCatalystViewToMachine() {
            if (itemView == null || fluidViews == null) {
                return;
            }
            int index = currentViewIndex();
            boolean indexChanged = index != lastSentIndex;
            boolean itemsChanged = indexChanged;
            for (int i = 0; i < CATALYST_ITEM_SLOTS && !itemsChanged; i++) {
                if (!ItemStack.matches(itemView.getStackInSlot(i), lastSentItems[i])) {
                    itemsChanged = true;
                }
            }
            boolean fluidsChanged = indexChanged;
            for (int i = 0; i < CATALYST_FLUID_TANKS && !fluidsChanged; i++) {
                FluidStack now = fluidViews[i].getFluidInTank(0);
                if (!now.isFluidEqual(lastSentFluids[i]) || now.getAmount() != lastSentFluids[i].getAmount()) {
                    fluidsChanged = true;
                }
            }
            if (!itemsChanged && !fluidsChanged) {
                return;
            }
            try {
                CompoundTag payload = new CompoundTag();
                net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
                for (int i = 0; i < CATALYST_ITEM_SLOTS; i++) {
                    ItemStack stack = itemView.getStackInSlot(i).copy();
                    lastSentItems[i] = stack;
                    items.add(stack.serializeNBT());
                }
                payload.put("items", items);
                net.minecraft.nbt.ListTag fluids = new net.minecraft.nbt.ListTag();
                for (int i = 0; i < CATALYST_FLUID_TANKS; i++) {
                    FluidStack fluid = fluidViews[i].getFluidInTank(0).copy();
                    lastSentFluids[i] = fluid;
                    fluids.add(fluid.saveToTag(new CompoundTag()));
                }
                payload.put("fluids", fluids);
                machine.catalystViewPayload = payload.toString();
                lastSentIndex = index;
            } catch (Throwable failure) {
                SwallowedErrors.report("LazyPatternAssemblyPartMachine.CatalystAccessConfigurator#publishCatalystViewToMachine", failure);
                GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪催化剂页: 内容同步失败（已忽略）pos={} 槽={} 原因={}",
                        machine.getPos(), index + 1, failure.toString());
            }
        }

        private void applyCatalystViewFromMachine() {
            if (itemView == null || fluidViews == null) {
                return;
            }
            String payload = machine.getCatalystViewPayload();
            if (payload == null || payload.isEmpty() || payload.equals(lastAppliedPayload)) {
                return;
            }
            lastAppliedPayload = payload;
            try {
                CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(payload);
                net.minecraft.nbt.ListTag items = tag.getList("items", Tag.TAG_COMPOUND);
                int nonEmpty = 0;
                for (int i = 0; i < CATALYST_ITEM_SLOTS && i < items.size(); i++) {
                    ItemStack stack = ItemStack.of(items.getCompound(i));
                    if (!stack.isEmpty()) {
                        nonEmpty++;
                    }
                    itemView.setStackInSlot(i, stack);
                }
                net.minecraft.nbt.ListTag fluids = tag.getList("fluids", Tag.TAG_COMPOUND);
                for (int i = 0; i < CATALYST_FLUID_TANKS && i < fluids.size(); i++) {
                    fluidViews[i].setFluidInTank(0, FluidStack.loadFromTag(fluids.getCompound(i)));
                }
                if (!loggedPayloadApply) {
                    loggedPayloadApply = true;
                    GTLModular.LOGGER.info(
                            "[gtlmodular] 惰性猪咪催化剂页: 客户端收到内容同步 非空物品={}/{} 槽={}",
                            nonEmpty, CATALYST_ITEM_SLOTS, currentViewIndex() + 1);
                }
            } catch (Throwable ignored) {
                SwallowedErrors.report("LazyPatternAssemblyPartMachine.CatalystAccessConfigurator#applyCatalystViewFromMachine", ignored);
            }
        }

        private void layoutSideBySide() {
            if (catalystUI == null) {
                return;
            }
            PatternCatalystWidgets.layoutSideBySide(catalystUI, root, PAGE_WIDTH, PAGE_OPEN_HEIGHT, false);
        }
    }

}
