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

/**
 * <b>P2：样板仓「归档」档案的 NBT 编解码 + 机器侧应用引擎。</b>
 *
 * <p>这个类把「从一台猪咪样板总成取出全仓 / 把一份档案写进一台猪咪样板总成」
 * 这两件事<b>从机器类里独立出来</b>，因为：
 * <ol>
 *   <li>它要同时服务两个入口 —— 机器面板上的「归档」页，以及「样板仓归档」物品的右键；</li>
 *   <li>它是「动玩家存档里的真东西」的那一段（样板 NBT），必须能被单独审阅：
 *       所有判断都集中在 {@link #apply} 里，读侧不写、写侧不猜。</li>
 * </ol>
 *
 * <p>★ <b>片 C</b>：{@link #apply} 的机器参数已从具体类泛化成接口 {@link PatternArchiveTarget}
 * ⇒ <b>惰性机</b>（动态容量）与<b>现役机</b>（gtlcore 具体类，容量注册期固定）<b>共用这一套引擎</b>。
 * 两个适配器 {@link LazyPatternArchiveTarget} / {@link MoltenPatternArchiveTarget}
 * 逐方法转调各自已有的实现；本类只认接口，不认识任何一台具体的机器。
 * 这一步<b>只加不删</b>：{@code apply(LazyPatternAssemblyPartMachine, ...)} 的两个旧签名保留为
 * "包一层适配器"的兼容桥（惰性机那两个内部调用点一个字都不用改）。
 * 接口形状的来历与两个机种的差异见 {@link PatternArchiveTarget} 的类注释。
 *
 * <h3>档案结构（{@link #NBT_STAMP} 那一个 CompoundTag 里，键名全部自带前缀，便于将来加版本）</h3>
 * <pre>
 * gtlmodular$archive            （物品 NBT 根键；档案本体的容器）
 * ├── gtlmodular$archive_version   int      档案格式版本（当前 {@link #VERSION} = 1）
 * ├── gtlmodular$archive_slot_count int     档案覆盖的槽位跨度（0 = 空档）
 * ├── gtlmodular$archive_capacity  int      源机当时的容量（仅作诊断/展示，粘贴时**不用**它）
 * ├── gtlmodular$archive_include_settings byte 这份档案是否含设置类（默认 0）
 * ├── gtlmodular$archive_include_name     byte 这份档案是否含名字（默认 0）
 * ├── gtlmodular$archive_name      string   源机名字（仅当 include_name）
 * ├── gtlmodular$archive_dimension string   源机所在维度（诊断用）
 * ├── gtlmodular$archive_pos       long     源机坐标（诊断用；BlockPos.asLong）
 * ├── gtlmodular$archive_pattern_count int  非空样板张数（只读展示；读侧会**重数**一遍，不信任它）
 * ├── gtlmodular$archive_slots     List&lt;CompoundTag&gt;   **按槽下标对齐的稀疏表**（只写非空槽，
 * │                                                    每个元素带 s = 槽下标）：
 * │   └── s                              int   槽下标（0 起）
 * │       gtlmodular$archive_pattern     CompoundTag  ItemStack.serializeNBT() 原样
 * │       gtlmodular$archive_recipe_type int   逐槽配方类型缓存（-2 = 试过识别不出来；缺 = 没试过）
 * │       gtlmodular$archive_manual_type string 手动指定（配方类型 id）
 * │       gtlmodular$archive_catalyst    CompoundTag 逐槽催化剂：items / fluids / count（阈值）
 * ├── gtlmodular$archive_settings  CompoundTag  设置类（仅当 include_settings）：
 * │   gtlmodular$lazy_pattern_scale / ..._div_scale / ..._foa_mode / ..._foa_multiplier / ..._output_mode
 * └── （兼容）gtlcore 的两个旧键名也会被**读侧**认出来：
 *     "tag"（gtlcore copyToTag 的容器）与 "cut"（gtlcore cutToTag 的容器），其中的
 *     "name" / "patterns"[{pattern, cacheCount, catalystItems, catalystFluids}] 会被翻译成本档案格式。
 *     **写侧永远只写新键名** —— 不往 gtlcore 的键里塞我们的动态容量坐标系（两边槽位坐标系不同，
 *     见 LazyPatternAssemblyPartMachine 类 javadoc 的互通说明）。
 * </pre>
 *
 * <h3>版本兼容考虑</h3>
 * <ul>
 *   <li>{@link #VERSION} 只增；读侧 <b>大于</b> 当前版本的档案一律<b>拒绝导入</b>（宁可让玩家看到
 *       "档案版本太新"，也不要按旧规则解读新结构而把样板写错位）；小于等于则按本版规则读，
 *       缺键 = 缺省（不是错误）。</li>
 *   <li>所有键名都在 {@code gtlmodular$archive_} 命名空间下：将来若要支持"一份档案带多台机器的内容"
 *       或改成定长坐标系，直接在版本号上分支即可，不必猜旧键语义。</li>
 *   <li>槽下标一律**按档案自己的坐标系**解读（惰性机的槽位坐标系 = 动态容量）。
 *       因此本档案<b>不能</b>直接喂给 gtlcore 的 pasteFromTag（它按固定 128/640 读），反之亦然。</li>
 * </ul>
 *
 * <h3>落位模式（{@link PlaceMode}）—— 粘贴时"档案里的槽下标"要不要当真</h3>
 * <ul>
 *   <li><b>{@link PlaceMode#APPEND}（默认）</b>：从目标机<b>当前已用最高槽的下一格</b>开始，
 *       按档案里的槽下标<b>升序</b>依次往后排（"往后延伸"）。目标机是空的 ⇒ 自然从 0 起，
 *       与 {@link PlaceMode#ALIGN} 结果一致 ⇒ 26 台空机迁移场景逐槽等价。</li>
 *   <li><b>{@link PlaceMode#ALIGN}</b>：按档案里的槽下标落位（克隆/迁移到<b>另一台同布局</b>的机器用）。
 *       目标槽<b>已被占用 ⇒ 跳过该槽</b>（不写、不覆盖），跳过条数在结果里报告。</li>
 *   <li><b>两种模式都绝不覆盖已有样板</b>：追加模式的起点在"已用最高槽"之后（那些槽按定义是空的），
 *       并且仍会逐槽确认"这个槽是空的"才把它算进计划；对齐模式见到占用就跳过。</li>
 * </ul>
 *
 * <h3>纪律（动玩家存档真数据）</h3>
 * <ul>
 *   <li><b>先算后写</b>：整份档案读完之后才决定要不要动机器；任何一处解析失败都只是"少搬一条"，
 *       绝不会把已经决定的写入回滚成半成品（写入前所有 ItemStack 都已解码完成）。</li>
 *   <li><b>整体拒绝优于半成品</b>：容量装不下时在写第一个字节之前就返回失败（见 {@link #apply}）。</li>
 *   <li>整段包 try/catch(Throwable) —— 绝不外抛掀翻 tick / 网络 / 控件回调。</li>
 * </ul>
 */
public final class LazyPatternArchiveCodec {

    // 可见性说明：本类对 {@code com.gtlmodular.common.machine} 之外是 public 但"只读友好" ——
    // LazyPatternArchiveApi（编排层）与物品/界面只通过它读写档案，
    // 绝不直接碰机器内部那几张按槽下标对齐的表。

    /** 档案格式版本（读侧见到更大的版本号一律拒绝导入）。 */
    public static final int VERSION = 1;

    // ---------------------------------------------------------------- 键名（物品侧）
    /** 物品 NBT 里档案本体的根键。 */
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
    /**
     * ★ 0.3.14 新增：这份档案是不是「剪切」来的（= 只能落一次）。
     *
     * <p>为什么必须有这个标记（队长口径）：「剪切完以后应该自动清除。保存的，不然玩家就可以无限粘贴出样板了」
     * —— 剪切的语义是<b>搬运</b>（源机已被清空、样板只该在新的地方出现一次）；
     * 如果剪出来的档案还能反复粘贴，就等于"剪切 = 免费复制"。
     * 而<b>复制</b>出来的档案不受此限（迁移用途：复制一次 → 往多台各贴一次，
     * 代价由"复制时按张扣空白样板"承担，见 {@code LazyPatternArchiveApi.copyFrom}）。
     *
     * <p>落点是"档案自己的属性"（而不是工具物品的属性）：同一件工具先复制再剪切、或先剪切再复制，
     * 档案一换标记就跟着换 —— 不需要额外记住"这把工具现在是哪种模式"。
     */
    private static final String K_CUT_SOURCED = "gtlmodular$archive_cut_sourced";
    private static final String K_SLOT = "s";
    private static final String K_PATTERN = "gtlmodular$archive_pattern";
    private static final String K_RECIPE_TYPE = "gtlmodular$archive_recipe_type";
    private static final String K_MANUAL_TYPE = "gtlmodular$archive_manual_type";
    private static final String K_CATALYST = "gtlmodular$archive_catalyst";

    // ---------------------------------------------------------------- 键名（gtlcore 兼容读侧）
    // 出处 A 的【2.1】/【2.4】给了这些键的**写入位置**；本类只在**读**侧认它们。
    // 注：gtlcore 还会写 name / keepByProduct / dimension / bufferPos / proxies /
    //     sharedCatalystInventory / sharedCircuitInventory / sharedCatalystTank，
    //     我们**只**取其中真正能对应到惰性机的那几个（name 与 patterns 里的 pattern/cacheCount/
    //     catalystItems/catalystFluids），其余（共享仓 / 镜像坐标 / keepByProduct）在惰性机上没有对应物，
    //     读了也没有地方放 ⇒ 明确忽略，不假装能迁移。
    private static final String LEGACY_CONTAINER_COPY = "tag";
    private static final String LEGACY_CONTAINER_CUT = "cut";
    private static final String LEGACY_PATTERNS = "patterns";
    private static final String LEGACY_PATTERN = "pattern";
    private static final String LEGACY_CACHE_COUNT = "cacheCount";
    private static final String LEGACY_CATALYST_ITEMS = "catalystItems";
    private static final String LEGACY_CATALYST_FLUIDS = "catalystFluids";

    /** 催化剂子标签里那三个键（与 {@code LazyPatternAssemblyPartMachine} 的存档口径**逐字一致**）。 */
    private static final String C_ITEMS = "items";
    private static final String C_FLUIDS = "fluids";
    private static final String C_COUNT = "count";

    private LazyPatternArchiveCodec() {
    }

    // ==================================================================================
    // 档案本体（内存态）
    // ==================================================================================

    /** 一个槽位上的全部归档内容（样板 + 逐槽元数据）。 */
    public static final class Entry {
        public final int slot;
        public final ItemStack pattern;
        /** 逐槽配方类型缓存；{@code null} = 没记过（= TYPE_NOT_TRIED）。 */
        public final Integer recipeType;
        /** 手动指定（配方类型 id）；{@code null} = 没指定。 */
        public final ResourceLocation manualType;
        /** 逐槽催化剂（items / fluids / count 三个子键）；{@code null} = 该槽没有催化剂内容。 */
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

    /** 一份档案（导出/导入的中间态，与 NBT 一一对应）。 */
    public static final class Payload {
        public int version = VERSION;
        public int slotCount;
        public int capacity;
        public int patternCount;
        public boolean includeSettings;
        public boolean includeName;
        /**
         * ★ 0.3.14：这份档案是不是「剪切」来的（= 只有一次落地机会，粘贴成功即从工具里清掉）。
         * 见 {@link #K_CUT_SOURCED} 的说明。
         */
        public boolean cutSourced;
        public String name = "";
        public String dimension = "";
        public long pos;
        public final List<Entry> entries = new ArrayList<>();
        /** 设置类（只有 includeSettings 时才有内容）。 */
        public CompoundTag settings = new CompoundTag();

        public boolean isEmpty() {
            return entries.isEmpty();
        }
    }

    /**
     * 粘贴时的<b>落位模式</b>（谁来定"档案里的第 N 条放进哪个槽"）。
     *
     * <p>默认是 {@link #APPEND}：这是"不覆盖已有样板"的那一支。
     */
    public enum PlaceMode {
        /**
         * <b>追加（默认）</b>：从目标机<b>当前已用最高槽的下一格</b>开始，按档案槽下标升序往后排。
         * <p>目标机为空 ⇒ 起点 0（与 {@link #ALIGN} 结果一致，26 台空机迁移不受影响）。
         */
        APPEND,
        /**
         * <b>按原槽对齐</b>：按档案里的槽下标落位；目标槽已被占用 ⇒ <b>跳过该槽</b>（不写、不覆盖）。
         * <p>用于"克隆/迁移到另一台同布局的机器"——注意它的坑：跳过会让这台机器的布局与档案不完全一致。
         */
        ALIGN
    }

    /**
     * 应用结果。{@code ok == false} 时机器<b>一个字节都没动</b>（这是本类的硬承诺）。
     */
    public static final class ApplyResult {
        public final boolean ok;
        /** 语言键（供界面/聊天展示）。 */
        public final String messageKey;
        public final Object[] messageArgs;
        public final int patternCount;
        public final int metadataCount;
        public final int rejectedCount;
        public final int neededCapacity;
        /** 因为"目标槽已被占用"而跳过的槽数（只可能 &gt; 0 于 {@link PlaceMode#ALIGN}）。 */
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

    // ==================================================================================
    // 物品侧：写 / 读 / 清
    // ==================================================================================

    /** 把一份档案写进物品 NBT（覆盖旧的）。返回是否有内容。 */
    public static boolean writeToStack(ItemStack stack, Payload payload) {
        return writeToStack(stack, payload, false);
    }

    /**
     * ★ 0.3.14：把一份档案写进物品 NBT，并显式标出它是不是「剪切」来的。
     *
     * @param cutSourced {@code true} = 剪切档案（粘贴成功即从工具里清掉，只能落一次）；
     *                   {@code false} = 复制档案（可反复粘贴）
     */
    public static boolean writeToStack(ItemStack stack, Payload payload, boolean cutSourced) {
        if (payload == null) {
            return false;
        }
        // 标记写进档案本体（一处写、一处读，不给调用方留"设了字段忘了编码"的机会）
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

    /**
     * 把一份档案序列化成一个独立的 CompoundTag（档案格式的<b>唯一实现</b>）。
     * <p>{@code payload == null} 返回 {@code null}；空档（0 张样板）返回一个合法的空档 tag。
     */
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
            // ★ 0.3.14：剪切来源标记（缺键 = false = 可反复粘贴的"复制"档案，与旧档案天然兼容）
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

    /** 读物品上的档案；没有内容/版本太新/损坏 ⇒ {@code null}（调用方按"空档案"处理）。 */
    public static Payload readFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return null;
        }
        return readFromTag(stack.getTag());
    }

    /**
     * <b>从一个 CompoundTag 里读出档案（读侧的唯一实现）</b>。依次尝试三种容器：
     * <ol>
     *   <li>我们的 {@code gtlmodular$archive}（物品 NBT 根 / copyToTag 写进调用方 tag 的那个键）；</li>
     *   <li>gtlcore 的 {@code cut} 容器（{@code cutToTag} 写的）—— 只读兼容；</li>
     *   <li>gtlcore 的 {@code tag} 容器（{@code copyToTag} 写的）—— 只读兼容；</li>
     *   <li>tag <b>本身</b>就是一份档案（带 {@code gtlmodular$archive_version}）。</li>
     * </ol>
     * 都没有 ⇒ {@code null}。任何异常都只吞掉（返回 {@code null}）。
     */
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

    /** 这份档案是不是「剪切」来的（= 只有一次落地机会）。读失败一律返回 {@code false}（旧档案按可反复粘贴处理）。 */
    public static boolean isCutSourced(ItemStack stack) {
        try {
            Payload payload = readFromStack(stack);
            return payload != null && payload.cutSourced;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 清空物品上的档案（潜行右键 / 面板"清空"）。 */
    public static void clearStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return;
        }
        try {
            CompoundTag root = stack.getTag();
            root.remove(NBT_STAMP);
            // 兼容：如果这份档案是 gtlcore 工具做的，连它的两个容器键一起摘掉，
            // 否则"清空了却还显示有内容"（gtlcore 的 tooltip 会看这两个键）。
            root.remove(LEGACY_CONTAINER_CUT);
            root.remove(LEGACY_CONTAINER_COPY);
            if (root.isEmpty()) {
                stack.setTag(null);
            }
        } catch (Throwable ignored) {
            // 清不掉不影响任何东西
        }
    }

    /** 这份物品上有没有档案内容（只读展示用；损坏的档案也算"有东西"，免得玩家以为丢了）。 */
    public static boolean hasStackArchive(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return false;
        }
        CompoundTag root = stack.getTag();
        return root.contains(NBT_STAMP, Tag.TAG_COMPOUND)
                || root.contains(LEGACY_CONTAINER_CUT, Tag.TAG_COMPOUND)
                || root.contains(LEGACY_CONTAINER_COPY, Tag.TAG_COMPOUND);
    }

    /** 档案里的样板张数（读物品 NBT 的计数键，不再解码整份档案 —— tooltip 每帧都会问）。 */
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
            // 读不出来就当空
        }
        return 0;
    }

    /** 档案的来源描述（tooltip 用）；没有来源信息返回空串。 */
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

    // ==================================================================================
    // 读：CompoundTag → Payload
    // ==================================================================================

    /**
     * 解析一份档案。<b>只读</b>：不碰机器，任何一条坏数据都只是被跳过。
     *
     * @return 解析结果；档案版本比本类新 ⇒ 返回 {@code null}（调用方必须当成"不支持的档案"）
     */
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
            // ★ 0.3.14：剪切来源标记（旧档案没有这个键 ⇒ 一律读成 false = 可反复粘贴）
            payload.cutSourced = stamp.getBoolean(K_CUT_SOURCED);
            payload.name = stamp.getString(K_NAME);
            payload.dimension = stamp.getString(K_DIMENSION);
            payload.pos = stamp.getLong(K_POS);
            payload.capacity = Math.max(0, stamp.getInt(K_CAPACITY));
            payload.slotCount = Math.max(0, stamp.getInt(K_SLOT_COUNT));

            if (legacy) {
                // gtlcore 格式（整条流水账，槽位按顺序填）：翻译成"槽下标 0..n-1 对齐"。
                // 注意：这**只是"能读"**，不代表坐标系相同 —— 见类 javadoc 的版本兼容那一节。
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
                        continue;   // 解不出来的那一条直接丢，绝不写进机器
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

    /** 解一个样板物品 NBT；坏数据一律返回 {@link ItemStack#EMPTY}（绝不抛）。 */
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

    // ==================================================================================
    // 写：Payload → 机器
    // ==================================================================================

    /**
     * 把一份档案写进<b>目标样板仓</b>（默认 {@link PlaceMode#APPEND} = <b>追加</b>，绝不覆盖已有样板）。
     *
     * <p>★ <b>片 C</b>：机器参数从 {@code LazyPatternAssemblyPartMachine} 泛化成
     * {@link PatternArchiveTarget} ⇒ 惰性机与现役机<b>共用这同一套引擎</b>
     * （"先算后写 / 装不下整体拒绝 / 绝不覆盖已有样板"只有一份实现，两个适配器各自转调）。
     *
     * <p><b>顺序（每一步的位置都是理由）</b>：
     * <ol>
     *   <li><b>空档</b>：直接返回"没有内容"，机器一个字节都不动；</li>
     *   <li><b>先算（只读）</b>：按 {@link PlaceMode} 算出"档案里每一条要落到目标机的哪个槽"
     *       —— 这一步只读机器（{@link PatternArchiveTarget#highestUsedSlot()} +
     *       逐槽"是不是空的"），<b>不扩容、不写槽、不收尾</b>；
     *       追加 = 从"已用最高槽 + 1"往后排，对齐 = 按档案槽下标落位（占用 ⇒ 跳过、不覆盖）；</li>
     *   <li><b>整体拒绝</b>：需求 = 最高目标槽 + 1 + 预留，{@link PatternArchiveTarget#canFit(int)} 说装不下
     *       （或夹不住硬上限）就在这里返回 —— 此刻<b>还没碰容量、还没碰任何槽</b>
     *       （把"需求量 vs 硬上限"告诉玩家）。★ 现役机容量不可变 ⇒ 这一步是它唯一也是最后一道容量关；</li>
     *   <li><b>一次扩容</b>：{@link PatternArchiveTarget#growForBatchInsert(int)}
     *       （追加）/ {@link PatternArchiveTarget#growToCapacity(int)}（对齐）
     *       —— 都是**只调一次**；<b>现役机上这两个都是 no-op</b>（容量不可变，走不到这里装不下的情况）；</li>
     *   <li><b>逐槽落盘</b>：落到扫出来的那个目标槽，每槽单独 try/catch；写前复核"该槽仍是空的"，
     *       坏的那一槽保持原样（不产生"半张样板"、更不覆盖）；</li>
     *   <li><b>元数据</b>：逐槽配方类型 / 手动指定 / 催化剂（含阈值）—— <b>只对真的落地成功的槽写</b>
     *       （单槽失败 ⇒ 该槽整体保持原样），且只在真的有对应数据时才写，不实体化多余的槽；</li>
     *   <li><b>设置类/名字</b>：只有档案带了、且调用方允许时才写；</li>
     *   <li><b>收尾</b>：只要有写入，就只调一次 {@link PatternArchiveTarget#afterBulkChange()}
     *       （走各机既有的那一条通道），绝不新增刷新路径。</li>
     * </ol>
     *
     * @param applySettings 是否允许写入设置类（档案里也必须带）
     * @param applyName     是否允许写入名字（档案里也必须带）
     */
    public static ApplyResult apply(PatternArchiveTarget machine, Payload payload,
                             boolean applySettings, boolean applyName) {
        // 兼容旧调用方：不给模式 ⇒ 走**默认的追加**（也就是最安全的那一支）。
        return apply(machine, payload, applySettings, applyName, PlaceMode.APPEND);
    }

    /**
     * ★ <b>片 C 兼容桥</b>（"惰性机文件一个字都不改"的那一半）：
     * {@link LazyPatternAssemblyPartMachine} 自己的 {@code pasteFromTag} / {@code copyFromTag}
     * 直接把它自己（{@code this}）传进来 —— 这里包一层 {@link LazyPatternArchiveTarget} 再走同一套引擎。
     * 适配器是<b>逐方法一对一转发</b> ⇒ 语义与泛化之前<b>完全一致</b>。
     */
    public static ApplyResult apply(LazyPatternAssemblyPartMachine machine, Payload payload,
                             boolean applySettings, boolean applyName) {
        return apply(targetOf(machine), payload, applySettings, applyName, PlaceMode.APPEND);
    }

    /**
     * ★ <b>片 C 兼容桥（带落位模式）</b>：同上 ——
     * 惰性机的 {@code pasteFromTag} / {@code copyFromTag} 用的是这个五参签名，本方法让它们
     * <b>一个字都不用改</b>。
     */
    public static ApplyResult apply(LazyPatternAssemblyPartMachine machine, Payload payload,
                             boolean applySettings, boolean applyName, PlaceMode mode) {
        return apply(targetOf(machine), payload, applySettings, applyName, mode);
    }

    /**
     * 同上，但显式指定落位模式（{@link PlaceMode}）。
     *
     * @param mode {@code null} 视为 {@link PlaceMode#APPEND}（默认 = 追加，绝不覆盖已有样板）
     */
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

            // ---------- ① 先算（**全程只读**：这一步只问机器"哪些槽是空的"，一个字节都不写） ----------
            //    算出"档案里的每一条要落在目标机的哪个槽"。两条铁律：
            //      · **绝不覆盖已有样板**（本步只读 + 后面写之前还会复核目标槽是空的）；
            //      · 目标槽在"还没有的容量"之外也算合法目标（后面由**一次**扩容把它们建出来）。
            //    排序：按档案槽下标升序 ⇒ 追加模式下"档案里的相对顺序"被原样保留（迁移要的就是这个）。
            List<Entry> ordered = new ArrayList<>(payload.entries);
            ordered.sort(Comparator.comparingInt((Entry entry) -> entry.slot));

            List<Placement> plan = new ArrayList<>(ordered.size());
            Set<Integer> claimed = new HashSet<>();
            int skipped = 0;
            // 追加模式的起点 = 本机当前**已用最高槽 + 1**（空机 ⇒ 0）。
            // 这就是既有 API {@link LazyPatternAssemblyPartMachine#highestUsedSlot()}（返回 -1 = 空仓）。
            int cursor = placeMode == PlaceMode.APPEND
                    ? Math.max(0, machine.highestUsedSlot() + 1) : 0;
            for (Entry entry : ordered) {
                if (entry == null || entry.pattern == null || entry.pattern.isEmpty()) {
                    continue;   // 解不出来的那一条不占位（与读侧同口径）
                }
                int target;
                if (placeMode == PlaceMode.ALIGN) {
                    target = entry.slot;
                    if (target < 0) {
                        continue;
                    }
                    // ★ 对齐模式的跳过逻辑：已被占用（或同一份档案里重复指向同一个槽）⇒ 跳过，不写、不覆盖。
                    if (claimed.contains(target) || isSlotOccupied(machine, target, capacity)) {
                        skipped++;
                        continue;
                    }
                } else {
                    // ★ 追加模式的落位：从 cursor 起找第一个**确实空着**的槽（cursor 之后按定义都是空的，
                    //   这里再复核一次是为了"绝不覆盖"这条硬约束不依赖任何前提）。
                    target = nextFreeSlot(machine, cursor, capacity);
                    cursor = target + 1;
                }
                claimed.add(target);
                plan.add(new Placement(entry, target));
            }

            if (plan.isEmpty()) {
                // 一个槽都没得放：机器一个字节都不动（容量不扩、也不收尾）
                if (skipped > 0) {
                    return ApplyResult.fail("gtlmodular.pattern_assembly.archive.result.align_occupied_all",
                            skipped);
                }
                return ApplyResult.fail("gtlmodular.pattern_assembly.archive.result.nothing");
            }

            // ---------- ② 整体拒绝：需求 = 最高目标槽 + 1 + 预留；夹不住 ⇒ **在这里**返回 ----------
            //    此刻：还没扩容、还没碰任何槽（上面全程只读）⇒ 机器连容量都不动 ✓
            //    追加模式下的 needed 恰好等于 {@code highestUsedSlot() + 1 + 张数 + 预留}
            //    （= growCapacityForBatchInsert(int) 内部那条表达式），所以"一次扩到位"的语义不变。
            int highestTarget = plan.get(0).slot;
            for (Placement placement : plan) {
                if (placement.slot > highestTarget) {
                    highestTarget = placement.slot;
                }
            }
            final int needed = highestTarget + 1 + machine.reserve();
            if (!machine.canFit(needed) || needed > maxPatterns) {
                // 把"需求量 vs 硬上限"如实告诉玩家（needed 含预留空位）
                // ★ 片 C：现役机 canFit() 是恒等判据（capacity == maxCapacity），所以现役机会
                //   **更早、更频繁**地命中这里 —— 这是正确行为（容量不可变 ⇒ 只能整体拒绝）。
                return ApplyResult.fail("gtlmodular.pattern_assembly.archive.result.reject",
                        needed, maxPatterns);
            }

            // ---------- ④ 一次扩到位（**只调一次**扩容入口；绝不分几次扩、绝不越硬上限） ----------
            if (placeMode == PlaceMode.APPEND) {
                // 追加：需求与 growCapacityForBatchInsert 的定义逐字相同 ⇒ 直接用批量入口（队长硬需求的那条路径）。
                machine.growForBatchInsert(plan.size());
            } else {
                // 对齐：需求 = 最高目标槽 + 1 + 预留（可能比"已用 + 张数"更小或更大）⇒ 走同一个单次扩容实现。
                machine.growToCapacity(Math.min(maxPatterns, needed));
            }

            // ---------- ⑤ 逐槽落盘（落到**计划里算出来的那个槽**，不再按档案槽下标直写） ----------
            int written = 0;
            int failed = 0;
            boolean[] landed = new boolean[plan.size()];
            for (int i = 0; i < plan.size(); i++) {
                Placement placement = plan.get(i);
                // 写之前再复核一次"这个槽还是空的"：本步与规划之间没有任何会让仓内容变化的东西，
                // 这是纯粹的兜底 —— 宁可少写一条（记失败），也绝不覆盖玩家已有的样板。
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

            // ---------- ⑥ 逐槽元数据（★ 只对"样板真的落地了"的槽写：单槽失败 ⇒ 该槽**整体**保持原样） ----------
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

            // ---------- ⑦ 设置类 / 名字（默认不搬：这是"这台机器"的属性，不是"这批样板"的） ----------
            if (applySettings && payload.includeSettings && payload.settings != null
                    && !payload.settings.isEmpty()) {
                machine.writeSettings(payload.settings);
            }
            if (applyName && payload.includeName && payload.name != null && !payload.name.isEmpty()) {
                machine.writeName(payload.name);
            }

            // ---------- ⑧ 唯一一次收尾（既有通道） ----------
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
                // plan 非空 ⇒ written + failed == plan.size() ⇒ 走到这里必然是"每一槽都写失败"：
                // 如实报"一个槽都没写成功"，而不是"档案里没内容"。
                return ApplyResult.fail("gtlmodular.pattern_assembly.archive.result.paste_failed", failed);
            }
            // 两个模式的提示分开：追加要说清"从最高已用槽之后接着放"，
            // 对齐要说清"跳过了 N 个已占用槽（不覆盖）"。
            return ApplyResult.success(
                    placeMode == PlaceMode.ALIGN
                            ? "gtlmodular.pattern_assembly.archive.result.pasted.align"
                            : "gtlmodular.pattern_assembly.archive.result.pasted",
                    new Object[]{written, metadata, failed, skipped, neededFinal},
                    written, metadata, failed, neededFinal, skipped);
        } catch (Throwable failure) {
            // 整段兜底：动存档真数据的路径绝不许外抛
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：粘贴异常（已中止）pos={} 原因={}",
                    machine == null ? "?" : machine.pos(), failure.toString());
            return ApplyResult.fail("gtlmodular.pattern_assembly.archive.result.error");
        }
    }

    /** ★ 片 C 兼容桥：把惰性机包成 {@link PatternArchiveTarget}（{@code null} 原样传下去）。 */
    private static PatternArchiveTarget targetOf(LazyPatternAssemblyPartMachine machine) {
        return machine == null ? null : new LazyPatternArchiveTarget(machine);
    }

    /** 规划结果的一条：档案里的那一条 → 目标机上的那个槽。 */
    private static final class Placement {
        final Entry entry;
        final int slot;

        Placement(Entry entry, int slot) {
            this.entry = entry;
            this.slot = slot;
        }
    }

    /**
     * 从 {@code from} 起找第一个空槽（只看**当前容量之内**）。
     *
     * <p>容量之内全满（或 {@code from} 已经越过容量）⇒ 返回 {@code max(from, capacity)}：
     * 那就是"还没被建出来的槽"，它按定义是空的，由后面**一次**扩容建出来。
     *
     * <p>成本：{@code from} 只会单调向后走 ⇒ 整趟规划最多扫 {@code capacity} 个槽，
     * <b>不破防 O(n²)</b>。
     */
    private static int nextFreeSlot(PatternArchiveTarget machine, int from, int capacity) {
        for (int i = Math.max(0, from); i < capacity; i++) {
            if (!isSlotOccupied(machine, i, capacity)) {
                return i;
            }
        }
        return Math.max(Math.max(0, from), capacity);
    }

    /**
     * 这一槽上有没有样板（<b>只读</b>：只问 {@code patternInventory}，绝不实体化槽、绝不 getInternalSlot）。
     *
     * <p>越界（{@code slot >= capacity}）= 这个槽还没被建出来 ⇒ 一定是空的。
     * <p>读异常 ⇒ 按"<b>有东西</b>"处理（宁可少写一条、跳过它，也绝不覆盖玩家已有的样板）。
     */
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
