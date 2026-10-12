package com.gtlmodular.common.machine;

import com.gtlmodular.GTLModular;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 物品和面板共用档案格式及防覆盖逻辑，避免同一批样板经不同入口得到不同结果。 */
public final class LazyPatternArchiveCodec {

    /** 遇到更新的格式必须拒绝导入，避免误读尚不认识的数据。 */
    public static final int VERSION = 1;

    public static final String NBT_STAMP = "gtlmodular$archive";
    private static final String K_VERSION = "gtlmodular$archive_version";
    private static final String K_SLOT_COUNT = "gtlmodular$archive_slot_count";
    private static final String K_CAPACITY = "gtlmodular$archive_capacity";
    private static final String K_INCLUDE_SETTINGS = "gtlmodular$archive_include_settings";
    private static final String K_INCLUDE_NAME = "gtlmodular$archive_include_name";
    private static final String K_NAME = "gtlmodular$archive_name";
    private static final String K_DIMENSION = "gtlmodular$archive_dimension";
    private static final String K_POS = "gtlmodular$archive_pos";
    private static final String K_PATTERN_COUNT = "gtlmodular$archive_pattern_count";
    private static final String K_SLOTS = "gtlmodular$archive_slots";
    private static final String K_SETTINGS = "gtlmodular$archive_settings";
    /** 剪切档案只有一次落地机会，源仓已清空后不能再把它当作可重复粘贴的复制档案。 */
    private static final String K_CUT_SOURCED = "gtlmodular$archive_cut_sourced";
    private static final String K_SLOT = "s";
    private static final String K_PATTERN = "gtlmodular$archive_pattern";
    private static final String K_RECIPE_TYPE = "gtlmodular$archive_recipe_type";
    private static final String K_MANUAL_TYPE = "gtlmodular$archive_manual_type";
    private static final String K_CATALYST = "gtlmodular$archive_catalyst";

    private static final String LEGACY_CONTAINER_COPY = "tag";
    private static final String LEGACY_CONTAINER_CUT = "cut";
    private static final String LEGACY_PATTERNS = "patterns";
    private static final String LEGACY_PATTERN = "pattern";
    private static final String LEGACY_CACHE_COUNT = "cacheCount";
    private static final String LEGACY_CATALYST_ITEMS = "catalystItems";
    private static final String LEGACY_CATALYST_FLUIDS = "catalystFluids";

    private static final String C_ITEMS = "items";
    private static final String C_FLUIDS = "fluids";
    private static final String C_COUNT = "count";

    private LazyPatternArchiveCodec() {
    }

    public static final class Entry {
        public final int slot;
        public final ItemStack pattern;
        public final Integer recipeType;
        public final ResourceLocation manualType;
        public final CompoundTag catalyst;

        public Entry(int slot, ItemStack pattern, Integer recipeType, ResourceLocation manualType,
                     CompoundTag catalyst) {
            this.slot = slot;
            this.pattern = pattern;
            this.recipeType = recipeType;
            this.manualType = manualType;
            this.catalyst = catalyst;
        }
    }

    public static final class Payload {
        public int version = VERSION;
        public int slotCount;
        public int capacity;
        public int patternCount;
        public boolean includeSettings;
        public boolean includeName;
        public boolean cutSourced;
        public String name = "";
        public String dimension = "";
        public long pos;
        public final List<Entry> entries = new ArrayList<>();
        public CompoundTag settings = new CompoundTag();

        public boolean isEmpty() {
            return entries.isEmpty();
        }
    }

    public enum PlaceMode {
        /** 追加从最高已用槽之后开始，避免填补中间空洞改变既有布局。 */
        APPEND,
        /** 对齐时保留已有样板，已占用槽必须跳过。 */
        ALIGN
    }

    public static final class ApplyResult {
        public final boolean ok;
        public final String messageKey;
        public final Object[] messageArgs;
        public final int patternCount;
        public final int metadataCount;
        public final int rejectedCount;
        public final int neededCapacity;
        public final int skippedCount;

        private ApplyResult(boolean ok, String messageKey, Object[] messageArgs,
                            int patternCount, int metadataCount, int rejectedCount, int neededCapacity,
                            int skippedCount) {
            this.ok = ok;
            this.messageKey = messageKey;
            this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
            this.patternCount = patternCount;
            this.metadataCount = metadataCount;
            this.rejectedCount = rejectedCount;
            this.neededCapacity = neededCapacity;
            this.skippedCount = skippedCount;
        }

        static ApplyResult fail(String key, Object... args) {
            return new ApplyResult(false, key, args, 0, 0, 0, 0, 0);
        }

        static ApplyResult success(String key, Object[] args,
                                   int patterns, int metadata, int rejected, int needed, int skipped) {
            return new ApplyResult(true, key, args, patterns, metadata, rejected, needed, skipped);
        }

        public boolean changedAnything() {
            return patternCount > 0 || metadataCount > 0;
        }
    }

    public static boolean writeToStack(ItemStack stack, Payload payload) {
        return writeToStack(stack, payload, false);
    }

    public static boolean writeToStack(ItemStack stack, Payload payload, boolean cutSourced) {
        if (payload == null) {
            return false;
        }
        // 剪切来源与档案一起编码，避免调用方设置标记却漏写 NBT。
        payload.cutSourced = cutSourced;
        return writeToStackInternal(stack, payload);
    }

    private static boolean writeToStackInternal(ItemStack stack, Payload payload) {
        if (stack == null || stack.isEmpty() || payload == null) {
            return false;
        }
        try {
            CompoundTag stamp = encode(payload);
            if (stamp == null) {
                return false;
            }
            stack.getOrCreateTag().put(NBT_STAMP, stamp);
            return stamp.getInt(K_PATTERN_COUNT) > 0;
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：写档案进物品失败（物品保持不变）原因={}",
                    failure.toString());
            return false;
        }
    }

    /** 编码集中于此，保证工具与机器入口使用同一格式。 */
    public static CompoundTag encode(Payload payload) {
        if (payload == null) {
            return null;
        }
        try {
            CompoundTag stamp = new CompoundTag();
            stamp.putInt(K_VERSION, VERSION);
            stamp.putInt(K_SLOT_COUNT, Math.max(0, payload.slotCount));
            stamp.putInt(K_CAPACITY, Math.max(0, payload.capacity));
            stamp.putBoolean(K_INCLUDE_SETTINGS, payload.includeSettings);
            stamp.putBoolean(K_INCLUDE_NAME, payload.includeName);
            stamp.putBoolean(K_CUT_SOURCED, payload.cutSourced);
            stamp.putString(K_NAME, payload.name == null ? "" : payload.name);
            stamp.putString(K_DIMENSION, payload.dimension == null ? "" : payload.dimension);
            stamp.putLong(K_POS, payload.pos);

            ListTag slots = new ListTag();
            int count = 0;
            for (Entry entry : payload.entries) {
                if (entry == null || entry.pattern == null || entry.pattern.isEmpty() || entry.slot < 0) {
                    continue;
                }
                CompoundTag e = new CompoundTag();
                e.putInt(K_SLOT, entry.slot);
                e.put(K_PATTERN, entry.pattern.serializeNBT());
                if (entry.recipeType != null) {
                    e.putInt(K_RECIPE_TYPE, entry.recipeType);
                }
                if (entry.manualType != null) {
                    e.putString(K_MANUAL_TYPE, entry.manualType.toString());
                }
                if (entry.catalyst != null && !entry.catalyst.isEmpty()) {
                    e.put(K_CATALYST, entry.catalyst.copy());
                }
                slots.add(e);
                count++;
            }
            stamp.put(K_SLOTS, slots);
            stamp.putInt(K_PATTERN_COUNT, count);
            if (payload.includeSettings && payload.settings != null && !payload.settings.isEmpty()) {
                stamp.put(K_SETTINGS, payload.settings.copy());
            }
            return stamp;
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：档案序列化失败 原因={}", failure.toString());
            return null;
        }
    }

    public static Payload readFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return null;
        }
        return readFromTag(stack.getTag());
    }

    public static Payload readFromTag(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            return null;
        }
        try {
            if (root.contains(NBT_STAMP, Tag.TAG_COMPOUND)) {
                return read(root.getCompound(NBT_STAMP));
            }
            if (root.contains(LEGACY_CONTAINER_CUT, Tag.TAG_COMPOUND)) {
                return read(root.getCompound(LEGACY_CONTAINER_CUT));
            }
            if (root.contains(LEGACY_CONTAINER_COPY, Tag.TAG_COMPOUND)) {
                return read(root.getCompound(LEGACY_CONTAINER_COPY));
            }
            return read(root);
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：读档案失败（按空档处理）原因={}",
                    failure.toString());
            return null;
        }
    }

    public static boolean isCutSourced(ItemStack stack) {
        try {
            Payload payload = readFromStack(stack);
            return payload != null && payload.cutSourced;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void clearStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return;
        }
        try {
            CompoundTag root = stack.getTag();
            root.remove(NBT_STAMP);
            // 同时移除上游容器键，避免工具清空后仍被上游提示识别为有内容。
            root.remove(LEGACY_CONTAINER_CUT);
            root.remove(LEGACY_CONTAINER_COPY);
            if (root.isEmpty()) {
                stack.setTag(null);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 损坏档案也显示为有内容，避免误导玩家以为档案已丢失。 */
    public static boolean hasStackArchive(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return false;
        }
        CompoundTag root = stack.getTag();
        return root.contains(NBT_STAMP, Tag.TAG_COMPOUND)
                || root.contains(LEGACY_CONTAINER_CUT, Tag.TAG_COMPOUND)
                || root.contains(LEGACY_CONTAINER_COPY, Tag.TAG_COMPOUND);
    }

    /** 提示每帧读取，只取计数键以避免反复解码整份档案。 */
    public static int stackPatternCount(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return 0;
        }
        try {
            CompoundTag root = stack.getTag();
            CompoundTag stamp = root.contains(NBT_STAMP, Tag.TAG_COMPOUND)
                    ? root.getCompound(NBT_STAMP) : null;
            if (stamp != null) {
                if (stamp.contains(K_PATTERN_COUNT, Tag.TAG_INT)) {
                    return Math.max(0, stamp.getInt(K_PATTERN_COUNT));
                }
                return stamp.getList(K_SLOTS, Tag.TAG_COMPOUND).size();
            }
            for (String key : new String[]{LEGACY_CONTAINER_CUT, LEGACY_CONTAINER_COPY}) {
                if (root.contains(key, Tag.TAG_COMPOUND)) {
                    return root.getCompound(key).getList(LEGACY_PATTERNS, Tag.TAG_COMPOUND).size();
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public static String stackSource(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return "";
        }
        try {
            CompoundTag root = stack.getTag();
            if (!root.contains(NBT_STAMP, Tag.TAG_COMPOUND)) {
                return "";
            }
            CompoundTag stamp = root.getCompound(NBT_STAMP);
            String dimension = stamp.getString(K_DIMENSION);
            if (!stamp.contains(K_POS, Tag.TAG_LONG)) {
                return dimension;
            }
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(stamp.getLong(K_POS));
            return dimension + " (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** 格式版本过新时拒绝解析；单条坏数据跳过，避免写入无效样板。 */
    public static Payload read(CompoundTag stamp) {
        if (stamp == null || stamp.isEmpty()) {
            return null;
        }
        boolean legacy = !stamp.contains(K_VERSION, Tag.TAG_INT);
        if (!legacy) {
            int version = stamp.getInt(K_VERSION);
            if (version > VERSION) {
                GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：档案版本 {} 比本程序支持的 {} 新 ⇒ 拒绝导入",
                        version, VERSION);
                return null;
            }
        }
        Payload payload = new Payload();
        try {
            payload.includeSettings = stamp.getBoolean(K_INCLUDE_SETTINGS);
            payload.includeName = stamp.getBoolean(K_INCLUDE_NAME);
            payload.cutSourced = stamp.getBoolean(K_CUT_SOURCED);
            payload.name = stamp.getString(K_NAME);
            payload.dimension = stamp.getString(K_DIMENSION);
            payload.pos = stamp.getLong(K_POS);
            payload.capacity = Math.max(0, stamp.getInt(K_CAPACITY));
            payload.slotCount = Math.max(0, stamp.getInt(K_SLOT_COUNT));

            if (legacy) {
                // 上游列表不含原槽号，只能按列表顺序分配下标，不能承诺还原源仓空洞。
                payload.includeName = true;
                ListTag patterns = stamp.getList(LEGACY_PATTERNS, Tag.TAG_COMPOUND);
                for (int i = 0; i < patterns.size(); i++) {
                    CompoundTag e = patterns.getCompound(i);
                    ItemStack pattern = readLegacyPattern(e);
                    if (pattern.isEmpty()) {
                        continue;
                    }
                    CompoundTag catalyst = new CompoundTag();
                    if (e.contains(LEGACY_CATALYST_ITEMS, Tag.TAG_COMPOUND)) {
                        catalyst.put(C_ITEMS, e.getCompound(LEGACY_CATALYST_ITEMS).copy());
                    }
                    if (e.contains(LEGACY_CATALYST_FLUIDS, Tag.TAG_COMPOUND)) {
                        catalyst.put(C_FLUIDS, e.getCompound(LEGACY_CATALYST_FLUIDS).copy());
                    }
                    byte cache = e.getByte(LEGACY_CACHE_COUNT);
                    if (cache > 1) {
                        catalyst.putByte(C_COUNT, cache);
                    }
                    payload.entries.add(new Entry(i, pattern, null, null,
                            catalyst.isEmpty() ? null : catalyst));
                    payload.slotCount = Math.max(payload.slotCount, i + 1);
                }
            } else {
                payload.settings = stamp.getCompound(K_SETTINGS).copy();
                ListTag slots = stamp.getList(K_SLOTS, Tag.TAG_COMPOUND);
                for (int i = 0; i < slots.size(); i++) {
                    CompoundTag e = slots.getCompound(i);
                    int slot = e.getInt(K_SLOT);
                    if (slot < 0) {
                        continue;
                    }
                    ItemStack pattern = readStack(e.getCompound(K_PATTERN));
                    if (pattern.isEmpty()) {
                        continue;
                    }
                    Integer recipeType = e.contains(K_RECIPE_TYPE, Tag.TAG_INT) ? e.getInt(K_RECIPE_TYPE) : null;
                    ResourceLocation manual = null;
                    String manualText = e.getString(K_MANUAL_TYPE);
                    if (!manualText.isEmpty()) {
                        manual = ResourceLocation.tryParse(manualText);
                    }
                    CompoundTag catalyst = e.contains(K_CATALYST, Tag.TAG_COMPOUND)
                            ? e.getCompound(K_CATALYST).copy() : null;
                    payload.entries.add(new Entry(slot, pattern, recipeType, manual, catalyst));
                    payload.slotCount = Math.max(payload.slotCount, slot + 1);
                }
            }
            payload.patternCount = payload.entries.size();
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：解析档案失败（已解析的部分保留）原因={}",
                    failure.toString());
        }
        return payload;
    }

    private static ItemStack readLegacyPattern(CompoundTag entry) {
        if (entry == null) {
            return ItemStack.EMPTY;
        }
        if (entry.contains(LEGACY_PATTERN, Tag.TAG_COMPOUND)) {
            return readStack(entry.getCompound(LEGACY_PATTERN));
        }
        return readStack(entry);
    }

    private static ItemStack readStack(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            ItemStack stack = ItemStack.of(tag);
            return stack == null ? ItemStack.EMPTY : stack;
        } catch (Throwable failure) {
            return ItemStack.EMPTY;
        }
    }

    public static ApplyResult apply(PatternArchiveTarget machine, Payload payload,
                             boolean applySettings, boolean applyName) {
        return apply(machine, payload, applySettings, applyName, PlaceMode.APPEND);
    }

    public static ApplyResult apply(LazyPatternAssemblyPartMachine machine, Payload payload,
                             boolean applySettings, boolean applyName) {
        return apply(targetOf(machine), payload, applySettings, applyName, PlaceMode.APPEND);
    }

    public static ApplyResult apply(LazyPatternAssemblyPartMachine machine, Payload payload,
                             boolean applySettings, boolean applyName, PlaceMode mode) {
        return apply(targetOf(machine), payload, applySettings, applyName, mode);
    }

    public static ApplyResult apply(PatternArchiveTarget machine, Payload payload,
                             boolean applySettings, boolean applyName, PlaceMode mode) {
        if (machine == null || payload == null || payload.entries.isEmpty()) {
            return ApplyResult.fail("gtlmodular.pattern_assembly.archive.result.empty");
        }
        if (machine.isRemote()) {
            return ApplyResult.fail("gtlmodular.pattern_assembly.archive.result.client");
        }
        try {
            final PlaceMode placeMode = mode == null ? PlaceMode.APPEND : mode;
            final int maxPatterns = machine.maxCapacity();
            final int capacity = machine.capacity();

            // 先只读规划全部落位，按源槽排序以保留样板相对顺序。
            List<Entry> ordered = new ArrayList<>(payload.entries);
            ordered.sort(Comparator.comparingInt((Entry entry) -> entry.slot));

            List<Placement> plan = new ArrayList<>(ordered.size());
            Set<Integer> claimed = new HashSet<>();
            int skipped = 0;
            int cursor = placeMode == PlaceMode.APPEND
                    ? Math.max(0, machine.highestUsedSlot() + 1) : 0;
            for (Entry entry : ordered) {
                if (entry == null || entry.pattern == null || entry.pattern.isEmpty()) {
                    continue;
                }
                int target;
                if (placeMode == PlaceMode.ALIGN) {
                    target = entry.slot;
                    if (target < 0) {
                        continue;
                    }
                    // 已占用槽和档案内的重复目标均须跳过，避免覆盖已有样板。
                    if (claimed.contains(target) || isSlotOccupied(machine, target, capacity)) {
                        skipped++;
                        continue;
                    }
                } else {
                    // 再次确认目标为空，避免防覆盖依赖最高已用槽的读数。
                    target = nextFreeSlot(machine, cursor, capacity);
                    cursor = target + 1;
                }
                claimed.add(target);
                plan.add(new Placement(entry, target));
            }

            if (plan.isEmpty()) {
                if (skipped > 0) {
                    return ApplyResult.fail("gtlmodular.pattern_assembly.archive.result.align_occupied_all",
                            skipped);
                }
                return ApplyResult.fail("gtlmodular.pattern_assembly.archive.result.nothing");
            }

            // 先校验整批容量，再扩容和写入，避免容量不足时只落地半批。
            int highestTarget = plan.get(0).slot;
            for (Placement placement : plan) {
                if (placement.slot > highestTarget) {
                    highestTarget = placement.slot;
                }
            }
            final int needed = highestTarget + 1 + machine.reserve();
            if (!machine.canFit(needed) || needed > maxPatterns) {
                return ApplyResult.fail("gtlmodular.pattern_assembly.archive.result.reject",
                        needed, maxPatterns);
            }

            if (placeMode == PlaceMode.APPEND) {
                machine.growForBatchInsert(plan.size());
            } else {
                machine.growToCapacity(Math.min(maxPatterns, needed));
            }

            int written = 0;
            int failed = 0;
            boolean[] landed = new boolean[plan.size()];
            for (int i = 0; i < plan.size(); i++) {
                Placement placement = plan.get(i);
                // 写入前再次判空，宁可少写一条，也不覆盖已有样板。
                if (isSlotOccupied(machine, placement.slot, machine.capacity())) {
                    failed++;
                    GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：第 {} 槽在写入前已非空 ⇒ 跳过该条"
                                    + "（绝不覆盖）pos={}", placement.slot + 1, machine.pos());
                    continue;
                }
                try {
                    machine.writePattern(placement.slot, placement.entry.pattern.copy());
                    landed[i] = true;
                    written++;
                } catch (Throwable failure) {
                    failed++;
                    GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：第 {} 槽样板写入失败（该槽保持原样）pos={} 原因={}",
                            placement.slot + 1, machine.pos(), failure.toString());
                }
            }

            // 元数据只跟随已成功落地的样板，不能改写失败槽的催化剂或模式。
            int metadata = 0;
            for (int i = 0; i < plan.size(); i++) {
                if (!landed[i]) {
                    continue;
                }
                Placement placement = plan.get(i);
                Entry entry = placement.entry;
                if (entry.recipeType != null) {
                    machine.writeRecipeType(placement.slot, entry.recipeType);
                    metadata++;
                }
                if (entry.manualType != null) {
                    machine.writeManualType(placement.slot, entry.manualType);
                    metadata++;
                }
                if (entry.catalyst != null && !entry.catalyst.isEmpty()) {
                    if (machine.writeCatalyst(placement.slot, entry.catalyst)) {
                        metadata++;
                    }
                }
            }

            // 设置和名字属于机器，只有调用方与档案都允许时才写入。
            if (applySettings && payload.includeSettings && payload.settings != null
                    && !payload.settings.isEmpty()) {
                machine.writeSettings(payload.settings);
            }
            if (applyName && payload.includeName && payload.name != null && !payload.name.isEmpty()) {
                machine.writeName(payload.name);
            }

            if (written > 0 || metadata > 0) {
                try {
                    machine.afterBulkChange();
                } catch (Throwable failure) {
                    GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：粘贴后的收尾失败"
                                    + "（样板已写好，缓存最迟由下一 tick 的签名轮询兜住）pos={} 原因={}",
                            machine.pos(), failure.toString());
                }
            }

            int neededFinal = Math.min(machine.maxCapacity(), needed);
            if (written <= 0 && metadata <= 0) {
                // 全部写入失败与空档案不同，提示必须保留这个区别。
                return ApplyResult.fail("gtlmodular.pattern_assembly.archive.result.paste_failed", failed);
            }
            return ApplyResult.success(
                    placeMode == PlaceMode.ALIGN
                            ? "gtlmodular.pattern_assembly.archive.result.pasted.align"
                            : "gtlmodular.pattern_assembly.archive.result.pasted",
                    new Object[]{written, metadata, failed, skipped, neededFinal},
                    written, metadata, failed, neededFinal, skipped);
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：粘贴异常（已中止）pos={} 原因={}",
                    machine == null ? "?" : machine.pos(), failure.toString());
            return ApplyResult.fail("gtlmodular.pattern_assembly.archive.result.error");
        }
    }

    private static PatternArchiveTarget targetOf(LazyPatternAssemblyPartMachine machine) {
        return machine == null ? null : new LazyPatternArchiveTarget(machine);
    }

    private static final class Placement {
        final Entry entry;
        final int slot;

        Placement(Entry entry, int slot) {
            this.entry = entry;
            this.slot = slot;
        }
    }

    /** 游标只向后推进，避免每张样板都从头扫描整仓。 */
    private static int nextFreeSlot(PatternArchiveTarget machine, int from, int capacity) {
        for (int i = Math.max(0, from); i < capacity; i++) {
            if (!isSlotOccupied(machine, i, capacity)) {
                return i;
            }
        }
        return Math.max(Math.max(0, from), capacity);
    }

    /** 读异常按已占用处理，宁可少写一条也不覆盖已有样板；查询不能实体化槽。 */
    private static boolean isSlotOccupied(PatternArchiveTarget machine, int slot, int capacity) {
        if (slot < 0 || slot >= capacity) {
            return false;
        }
        try {
            ItemStack stack = machine.patternAt(slot);
            return stack != null && !stack.isEmpty();
        } catch (Throwable ignored) {
            return true;
        }
    }
}
