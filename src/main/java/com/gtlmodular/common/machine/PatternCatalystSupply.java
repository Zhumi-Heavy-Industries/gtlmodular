package com.gtlmodular.common.machine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.config.Actionable;
import appeng.api.storage.StorageHelper;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.fluid.FluidHelper;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.common.data.GTLItems;
import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;

import java.util.HashSet;
import java.util.Set;

/**
 * 猪咪样板总成的「库存催化剂 / 免塞催化剂」—— 机器侧实现。
 *
 * <h3>要解决的问题</h3>
 * <p>GT 配方里有一类输入是<b>不消耗</b>的（{@code Content.chance == 0}，例如模具、催化床、各种催化基底）。
 * gtlcore 的样板仓对这种输入的原生做法是：玩家<b>手动</b>把它塞进该样板槽的「专属催化剂槽」
 * （或塞一个 gtlcore 的虚拟原料 {@code GTLItems.VIRTUAL_INGREDIENT} 并指定物品/流体），
 * 机器才会认为这个输入「有着落」。忘了塞 —— 样板进了仓、料也推来了，机器却一条配方都匹配不上。
 *
 * <h3>本类的做法（玩家不再需要手动塞）</h3>
 * <ol>
 *   <li><b>识别催化剂</b>：用该样板槽<b>已经解出来的那条 GT 配方</b>
 *       （{@link MoltenPatternAssemblyPartMachine#resolveCatalystRecipe(int, IPatternDetails)}），
 *       取其中 {@code chance == 0} 的物品/流体输入；编程电路不算（它由 gtlcore 的电路缓存负责）；</li>
 *   <li><b>按 ME 存量自动供给</b>：每个催化剂键先向 ME 网络模拟抽取 1 个 —— 拿得到才供给。
 *       供给的形式是 gtlcore 原生的<b>虚拟原料</b>标记（{@link VirtualIngredientBehavior#wrap(ItemStack)}），
 *       放进该槽的催化剂物品仓；基类 {@code reCalculateCatalystItemMap} 会把它读成
 *       {@code InternalSlot.configuredVirtualItems}，于是配方匹配时它按「无限量、不消耗」参与，
 *       而真正的物品始终留在 ME 网络里；</li>
 *   <li><b>清理陈旧标记</b>：只清「虚拟原料标记」，真实催化剂物品一个字节都不动；
 *       换样板后不再需要的标记会被移除；</li>
 *   <li><b>推料兜底</b>：如果 AE 的旧合成计划仍把催化剂一起推来（例如首次下单时标记还没建好），
 *       在落进缓存前把它从输入里摘掉并<b>原样退回 ME 网络</b> —— 绝不吞玩家的催化剂，
 *       也绝不让它被当成消耗输入吃掉。</li>
 * </ol>
 *
 * <h3>纪律</h3>
 * <ul>
 *   <li>全链路 <b>fail-open</b>：任何一步解不出来 / 抛异常 ⇒ 什么都不做，退回「玩家手动塞」的老行为；</li>
 *   <li><b>绝不供给「配方真会消耗」的输入</b>（{@link #isConsumedInput}）：否则等于凭空造料；</li>
 *   <li>不新增任何持久化字段 —— 标记就存在 gtlcore 原本就存档的催化剂仓里；</li>
 *   <li>不写日志热路径（仅在真正添加/退回时打 debug 级，由 {@code debugPatternLogs} 之外的开关控不到，
 *       所以只在 debug 打开时打）。</li>
 * </ul>
 */
public final class PatternCatalystSupply {

    private PatternCatalystSupply() {
    }

    /** 周期性巡检间隔（tick）：与 gtlcore 基类的盘点节奏同量级，远小于配方周期。 */
    static final int REFRESH_INTERVAL_TICKS = 20;

    /** 每次巡检最多处理几个槽（超级总成 640/2048 槽，必须摊平）。 */
    static final int REFRESH_BATCH = 4;

    // ================= 识别 =================

    /**
     * 取一条配方的「不消耗输入」键（物品 + 流体），并尽量解析成<b>样板里实际声明的那个键</b>。
     *
     * @param recipe  该槽解出来的 GT 配方（必须非 null）
     * @param pattern 该槽的样板；{@code null} ⇒ 每个输入退回配方的第一个候选（用于「推料兜底」这种只看键集合的场景）
     * @return 键 → 每次运行的需求量（虚拟供给不限量，数量只用于诊断/口径对照）
     */
    static Object2LongMap<AEKey> collectCatalystKeys(GTRecipe recipe, IPatternDetails pattern) {
        Object2LongOpenHashMap<AEKey> keys = new Object2LongOpenHashMap<>();
        if (recipe == null) {
            return keys;
        }
        try {
            for (Content content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
                if (content == null || content.content == null || content.chance != 0) {
                    continue;   // 只取「不消耗」；chance > 0 是真消耗，绝不碰
                }
                Ingredient ingredient = ItemRecipeCapability.CAP.of(content.content);
                if (ingredient == null || ingredient.isEmpty() || isCircuitIngredient(ingredient)) {
                    continue;   // 编程电路由 gtlcore 的共享电路/样板内嵌电路负责
                }
                AEKey key = preferredItemKey(pattern, ingredient);
                if (key != null) {
                    keys.addTo(key, Math.max(1L, itemAmount(ingredient)));
                }
            }
            for (Content content : recipe.getInputContents(FluidRecipeCapability.CAP)) {
                if (content == null || content.content == null || content.chance != 0) {
                    continue;
                }
                FluidIngredient ingredient = FluidRecipeCapability.CAP.of(content.content);
                if (ingredient == null || ingredient.isEmpty()) {
                    continue;
                }
                AEKey key = preferredFluidKey(pattern, ingredient);
                if (key != null) {
                    keys.addTo(key, Math.max(1L, ingredient.getAmount()));
                }
            }
        } catch (Throwable ignored) {
            // 解不出来就当没有催化剂（fail-open）
        }
        return keys;
    }

    /** 这一格输入是不是编程电路（不当作催化剂，交给 gtlcore 的电路通道）。 */
    private static boolean isCircuitIngredient(Ingredient ingredient) {
        try {
            for (ItemStack stack : ingredient.getItems()) {
                if (stack != null && !stack.isEmpty() && IntCircuitBehaviour.isIntegratedCircuit(stack)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // 读不出来就当不是电路
        }
        return false;
    }

    /** 配料里第一项的数量（只用于「需求量」口径，不参与供给判定）。 */
    private static long itemAmount(Ingredient ingredient) {
        try {
            ItemStack[] stacks = ingredient.getItems();
            return stacks.length == 0 || stacks[0] == null ? 1L : Math.max(1, stacks[0].getCount());
        } catch (Throwable ignored) {
            return 1L;
        }
    }

    /** 在样板声明的输入里找这个催化剂配料对应的<b>精确 AE 键</b>；找不到就退回配料第一个候选。 */
    private static AEKey preferredItemKey(IPatternDetails pattern, Ingredient ingredient) {
        if (pattern != null) {
            try {
                for (IPatternDetails.IInput input : pattern.getInputs()) {
                    if (input == null) {
                        continue;
                    }
                    GenericStack[] possible = input.getPossibleInputs();
                    if (possible == null) {
                        continue;
                    }
                    for (GenericStack stack : possible) {
                        if (stack == null || !(stack.what() instanceof AEItemKey itemKey)) {
                            continue;
                        }
                        ItemStack candidate = itemKey.toStack(1);
                        if (!candidate.isEmpty() && ingredient.test(candidate)) {
                            return itemKey;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // 退回到配料候选
            }
        }
        try {
            for (ItemStack stack : ingredient.getItems()) {
                if (stack != null && !stack.isEmpty() && !IntCircuitBehaviour.isIntegratedCircuit(stack)) {
                    return AEItemKey.of(stack);
                }
            }
        } catch (Throwable ignored) {
            // 无候选
        }
        return null;
    }

    private static AEKey preferredFluidKey(IPatternDetails pattern, FluidIngredient ingredient) {
        if (pattern != null) {
            try {
                for (IPatternDetails.IInput input : pattern.getInputs()) {
                    if (input == null) {
                        continue;
                    }
                    GenericStack[] possible = input.getPossibleInputs();
                    if (possible == null) {
                        continue;
                    }
                    for (GenericStack stack : possible) {
                        if (stack == null || !(stack.what() instanceof AEFluidKey fluidKey)) {
                            continue;
                        }
                        if (ingredient.test(FluidStack.create(fluidKey.getFluid(), 1L))) {
                            return fluidKey;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // 退回到配料候选
            }
        }
        try {
            FluidStack[] stacks = ingredient.getStacks();
            for (FluidStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) {
                    return AEFluidKey.of(stack.getFluid(), stack.getTag());
                }
            }
        } catch (Throwable ignored) {
            // 无候选
        }
        return null;
    }

    // ================= 供给 =================

    /**
     * 该槽当前样板需要的催化剂键里，还有哪些没有「着落」（虚拟标记 / 真实物品 / 真实流体都算）。
     * 空集合 ⇒ 不需要任何催化剂（或已经全部供给）。
     */
    static Set<AEKey> missingSuppliers(MoltenPatternAssemblyPartMachine machine, int slot,
                                       GTRecipe recipe, IPatternDetails pattern) {
        Set<AEKey> missing = new HashSet<>();
        Object2LongMap<AEKey> keys = collectCatalystKeys(recipe, pattern);
        if (keys.isEmpty()) {
            return missing;
        }
        ItemStackTransfer items = machine.catalystItemInventory(slot);
        FluidTransferList fluids = machine.catalystFluidInventory(slot);
        for (AEKey key : keys.keySet()) {
            if (!hasSupplier(items, fluids, key)) {
                missing.add(key);
            }
        }
        return missing;
    }

    /**
     * 刷新一个槽：先清掉不再需要的虚拟标记，再按 ME 网络存量补齐缺的催化剂。
     *
     * @return 是否改动了催化剂仓（调用方据此决定要不要通知 AE 重新取样板）
     */
    static boolean refreshSlot(MoltenPatternAssemblyPartMachine machine, int slot,
                               GTRecipe recipe, IPatternDetails pattern) {
        if (machine == null || recipe == null || slot < 0) {
            return false;
        }
        Object2LongMap<AEKey> keys = collectCatalystKeys(recipe, pattern);
        boolean changed = pruneStaleMarkers(machine, slot, keys.keySet());
        for (AEKey key : keys.keySet()) {
            changed |= ensureMarker(machine, slot, recipe, key);
        }
        return changed;
    }

    /**
     * 清掉该槽催化剂仓里「不再是当前样板催化剂」的<b>虚拟原料标记</b>。
     * 真实催化剂物品/流体一格都不动（玩家手动塞的东西永远归玩家）。
     */
    static boolean pruneStaleMarkers(MoltenPatternAssemblyPartMachine machine, int slot, Set<AEKey> needed) {
        ItemStackTransfer items = machine.catalystItemInventory(slot);
        if (items == null) {
            return false;
        }
        boolean changed = false;
        try {
            for (int i = 0; i < items.getSlots(); i++) {
                ItemStack stack = items.getStackInSlot(i);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                if (!GTLItems.VIRTUAL_INGREDIENT.isIn(stack)) {
                    continue;   // 真实催化剂：不碰
                }
                // ★ 只清「本功能自动写」的标记。gtlcore 的 wrap() 会把自动标记封成 marked=true；
                //   玩家手动放进去的虚拟原料（未密封、可在物品界面改载荷）一格都不动 ——
                //   那是玩家自己配的「不消耗输入」，不属于本功能的台账。
                if (!VirtualIngredientBehavior.isMarked(stack)) {
                    continue;
                }
                AEKey payload = markerPayload(stack);
                if (payload != null && !needed.contains(payload)) {
                    items.setStackInSlot(i, ItemStack.EMPTY);
                    changed = true;
                }
            }
        } catch (Throwable ignored) {
            return changed;
        }
        if (changed) {
            notifyCatalystChanged(items);
        }
        return changed;
    }

    /**
     * 为一个催化剂键补虚拟标记（条件是：ME 网络里拿得到 1 个、且它不是这条配方的消耗输入）。
     *
     * @return 是否真的加了标记
     */
    private static boolean ensureMarker(MoltenPatternAssemblyPartMachine machine, int slot,
                                        GTRecipe recipe, AEKey key) {
        if (key == null || recipe == null) {
            return false;
        }
        if (!(key instanceof AEItemKey) && !(key instanceof AEFluidKey)) {
            return false;   // 不认识的高级键：不猜
        }
        if (isConsumedInput(recipe, key)) {
            return false;   // 安全线：配方真会消耗它 → 绝不虚拟供给
        }
        ItemStackTransfer items = machine.catalystItemInventory(slot);
        if (items == null) {
            return false;
        }
        if (hasSupplier(items, machine.catalystFluidInventory(slot), key)) {
            return false;   // 已有（虚拟标记或真实物品）
        }
        if (!networkHas(machine.catalystGrid(), key, machine.catalystActionSource())) {
            return false;   // 「按 ME 网络可用量自动供给」：网络里没有就不供
        }
        ItemStack marker = createMarker(key);
        if (marker.isEmpty()) {
            return false;
        }
        try {
            for (int i = 0; i < items.getSlots(); i++) {
                if (items.getStackInSlot(i).isEmpty()) {
                    items.setStackInSlot(i, marker);
                    notifyCatalystChanged(items);
                    if (GTLModularConfig.isPatternAssemblyDebugLogsEnabled()) {
                        GTLModular.LOGGER.info("[gtlmodular] 催化剂自动供给：槽 {} 键 {}（ME 有存量，不消耗）@{}",
                                slot + 1, key.getId(), machine.getPos());
                    }
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // 加不进去 = 退回玩家手动塞
        }
        return false;
    }

    /** 把一条样板当前需要的催化剂全部供给到位（推料当 tick 用；已有/不可得的不重复检查）。 */
    static boolean ensureForPush(MoltenPatternAssemblyPartMachine machine, int slot,
                                 GTRecipe recipe, IPatternDetails pattern) {
        if (machine == null || recipe == null || slot < 0) {
            return false;
        }
        Object2LongMap<AEKey> keys = collectCatalystKeys(recipe, pattern);
        boolean changed = false;
        for (AEKey key : keys.keySet()) {
            changed |= ensureMarker(machine, slot, recipe, key);
        }
        return changed;
    }

    /**
     * 该槽的催化剂是否已经全部「有着落」—— AE 样板过滤（{@link PatternCatalystFilter}）是否生效的依据。
     * 至少要有 1 个催化剂，否则返回 false（没有催化剂可摘的样板没必要包一层）。
     */
    static boolean allSupplied(MoltenPatternAssemblyPartMachine machine, int slot,
                               GTRecipe recipe, IPatternDetails pattern) {
        Object2LongMap<AEKey> keys = collectCatalystKeys(recipe, pattern);
        if (keys.isEmpty()) {
            return false;
        }
        ItemStackTransfer items = machine.catalystItemInventory(slot);
        FluidTransferList fluids = machine.catalystFluidInventory(slot);
        for (AEKey key : keys.keySet()) {
            if (!hasSupplier(items, fluids, key)) {
                return false;
            }
        }
        return true;
    }

    /** 该槽催化剂仓里是否已经有这个键的「着落」。 */
    static boolean hasSupplier(ItemStackTransfer items, FluidTransferList fluids, AEKey key) {
        if (key == null) {
            return false;
        }
        try {
            if (items != null) {
                for (int i = 0; i < items.getSlots(); i++) {
                    ItemStack stack = items.getStackInSlot(i);
                    if (stack == null || stack.isEmpty()) {
                        continue;
                    }
                    if (GTLItems.VIRTUAL_INGREDIENT.isIn(stack)) {
                        AEKey payload = markerPayload(stack);
                        if (payload != null && payload.equals(key)) {
                            return true;
                        }
                        continue;
                    }
                    if (key instanceof AEItemKey itemKey && AEItemKey.of(stack).equals(itemKey)) {
                        return true;
                    }
                }
            }
            if (fluids != null && key instanceof AEFluidKey fluidKey) {
                for (int i = 0; i < fluids.getTanks(); i++) {
                    FluidStack stack = fluids.getFluidInTank(i);
                    if (stack == null || stack.isEmpty()) {
                        continue;
                    }
                    if (AEFluidKey.of(stack.getFluid(), stack.getTag()).equals(fluidKey)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    // ================= 推料兜底 =================

    /**
     * 把 AE 推来的输入里属于「催化剂」的键摘掉并<b>原样退回 ME 网络</b>。
     *
     * <p>为什么需要：AE 的合成计划在「样板过滤」还没生效的那一单里，仍会把催化剂当普通输入推来。
     * 它们不消耗，但会占着机器缓存、也会让 AE 以为已消耗。这里在落缓存前退回网络：
     * 退多少就从输入里摘多少（{@code poweredInsert} 返回值），退不回去的<b>原样留在输入里</b>
     * —— 基类缓存本来就会定期把用不完的输入退回网络，绝不会丢。
     *
     * @return 是否真的摘掉了东西
     */
    static boolean stripAndRefund(MoltenPatternAssemblyPartMachine machine, GTRecipe recipe,
                                  IPatternDetails pattern, KeyCounter[] inputHolder) {
        if (machine == null || recipe == null || inputHolder == null) {
            return false;
        }
        Object2LongMap<AEKey> keys = collectCatalystKeys(recipe, pattern);
        if (keys.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (KeyCounter counter : inputHolder) {
            if (counter == null) {
                continue;
            }
            for (AEKey key : keys.keySet()) {
                long amount = counter.get(key);
                if (amount <= 0L) {
                    continue;
                }
                if (isConsumedInput(recipe, key)) {
                    continue;   // 安全线：真消耗的不退
                }
                long refunded = refundToNetwork(machine, key, amount);
                if (refunded > 0L) {
                    counter.remove(key, refunded);
                    changed = true;
                    if (GTLModularConfig.isPatternAssemblyDebugLogsEnabled()) {
                        GTLModular.LOGGER.info("[gtlmodular] 催化剂退回 ME：槽外推来 {} ×{}，已退回 {} @{}",
                                key.getId(), amount, refunded, machine.getPos());
                    }
                }
            }
        }
        return changed;
    }

    // ================= 基础工具 =================

    /** 这个键是不是这条配方「会消耗」的输入（chance > 0）。是 ⇒ 绝不能虚拟供给/退回。 */
    static boolean isConsumedInput(GTRecipe recipe, AEKey key) {
        if (recipe == null || key == null) {
            return false;
        }
        try {
            if (key instanceof AEItemKey itemKey) {
                ItemStack probe = itemKey.toStack(1);
                for (Content content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
                    if (content == null || content.content == null || content.chance <= 0) {
                        continue;
                    }
                    Ingredient ingredient = ItemRecipeCapability.CAP.of(content.content);
                    if (ingredient != null && !ingredient.isEmpty() && ingredient.test(probe)) {
                        return true;
                    }
                }
            } else if (key instanceof AEFluidKey fluidKey) {
                FluidStack probe = FluidStack.create(fluidKey.getFluid(), 1L);
                for (Content content : recipe.getInputContents(FluidRecipeCapability.CAP)) {
                    if (content == null || content.content == null || content.chance <= 0) {
                        continue;
                    }
                    FluidIngredient ingredient = FluidRecipeCapability.CAP.of(content.content);
                    if (ingredient != null && !ingredient.isEmpty() && ingredient.test(probe)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    /** ME 网络里现成能拿出 1 个吗（模拟抽取，不扣能量、不动库存）。 */
    private static boolean networkHas(IGrid grid, AEKey key, IActionSource source) {
        if (grid == null || key == null) {
            return false;
        }
        try {
            if (grid.getStorageService() == null) {
                return false;
            }
            return grid.getStorageService().getInventory()
                    .extract(key, 1L, Actionable.SIMULATE, source) >= 1L;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 退回 ME 网络（带电网计费；拿不到电网就返回 0 = 不摘）。 */
    private static long refundToNetwork(MoltenPatternAssemblyPartMachine machine, AEKey key, long amount) {
        try {
            IGrid grid = machine.catalystGrid();
            if (grid == null || grid.getEnergyService() == null || grid.getStorageService() == null) {
                return 0L;
            }
            return StorageHelper.poweredInsert(grid.getEnergyService(),
                    grid.getStorageService().getInventory(), key, amount, machine.catalystActionSource());
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    /** 造一个 gtlcore 的虚拟原料标记（密封态：玩家不能改载荷，只能看）。 */
    private static ItemStack createMarker(AEKey key) {
        try {
            if (key instanceof AEItemKey itemKey) {
                ItemStack payload = itemKey.toStack(1);
                if (payload.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                return VirtualIngredientBehavior.wrap(payload);
            }
            if (key instanceof AEFluidKey fluidKey) {
                return VirtualIngredientBehavior.wrap(
                        FluidStack.create(fluidKey.getFluid(), FluidHelper.getBucket()));
            }
        } catch (Throwable ignored) {
            // 造不出来 = 不供给
        }
        return ItemStack.EMPTY;
    }

    /** 读虚拟原料标记的载荷键（物品优先、再流体）。 */
    private static AEKey markerPayload(ItemStack stack) {
        try {
            AEItemKey item = VirtualIngredientBehavior.payloadItemKey(stack);
            if (item != null) {
                return item;
            }
            AEFluidKey fluid = VirtualIngredientBehavior.payloadFluidKey(stack);
            if (fluid != null) {
                return fluid;
            }
        } catch (Throwable ignored) {
            // 读不出来 = 当普通物品
        }
        return null;
    }

    /** 催化剂仓变化后要走的基类入口（重算催化剂表 + 通知配方 handler 重新匹配）。 */
    private static void notifyCatalystChanged(ItemStackTransfer items) {
        try {
            items.onContentsChanged();
        } catch (Throwable ignored) {
            // 通知失败由下一次巡检兜底
        }
    }
}
