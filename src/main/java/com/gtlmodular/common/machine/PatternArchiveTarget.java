package com.gtlmodular.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * <b>片 C：样板仓「归档」引擎的机器侧接口（惰性猪咪样板总成 与 现役猪咪样板总成 共用同一套引擎）。</b>
 *
 * <p>为什么必须有它：归档引擎 {@link LazyPatternArchiveCodec#apply} 的三条硬承诺
 * （<b>先算后写</b> / <b>装不下整体拒绝</b> / <b>绝不覆盖已有样板</b>）是"动玩家存档真数据"的唯一防线，
 * 这套判断只允许有<b>一份</b>实现。而两台机器的坐标系与容量语义不同：
 * <ul>
 *   <li><b>惰性机</b>（{@link LazyPatternAssemblyPartMachine}）：容量<b>动态</b>（32/48/…，只增不减），
 *       有"预留空位"概念（{@code RESERVE}=2），有"批量一次扩容"入口，收尾走
 *       {@code onPatternInventoryChanged()}；</li>
 *   <li><b>现役机</b>（{@link MoltenPatternAssemblyPartMachine}，继承 gtlcore 具体类
 *       {@code MEPatternBufferPartMachine}）：容量<b>注册期固定、不可变</b>
 *       （{@code slotsForTier(tier)} = 128/256/384/512/640，六个并行数组硬绑 {@code maxPatternCount}），
 *       <b>没有</b> {@code growCapacityFor*} / {@code getCapacity()} / {@code onPatternInventoryChanged()}，
 *       收尾必须走它自己的既有通道（{@code refreshAllByProduct()}）。</li>
 * </ul>
 *
 * <p>⇒ 本接口把"引擎需要问机器 / 需要让机器做"的那几件事抽出来，两台机器各写一个
 * <b>适配器</b>（{@link LazyPatternArchiveTarget} / {@link MoltenPatternArchiveTarget}）转调各自
 * <b>已经有</b>的方法；<b>引擎方法体几乎一字不改</b>（只把机器参数从具体类换成这个接口）。
 *
 * <h3>纪律（每个方法都必须守住）</h3>
 * <ol>
 *   <li>所有 {@code 只读}方法（{@link #capacity()}、{@link #maxCapacity()}、{@link #reserve()}、
 *       {@link #canFit(int)}、{@link #highestUsedSlot()}、{@link #patternAt(int)}、
 *       {@link #isRemote()}、{@link #pos()}）<b>绝不许有任何副作用</b> ——
 *       引擎的"先算"阶段全程只读，就靠这一条；</li>
 *   <li>{@link #patternAt(int)} 只读原始槽内容，且<b>读异常照原样外抛</b>（引擎按"这一槽有东西"处理）；</li>
 *   <li>所有 {@code 写}方法单槽独立：某一槽失败<b>只影响那一槽</b>（引擎已经在外面逐槽 try/catch，
 *       适配器内部再兜一层也无害）；</li>
 *   <li>{@link #afterBulkChange()} 是<b>唯一一次</b>收尾入口：绝不许逐槽调用（640 槽会退化成
 *       O(n²)，惰性机 0.3.5 与现役机 gtlcore 的 pasteFromTag 都踩过）；</li>
 *   <li>整条路径 {@code try/catch(Throwable)}：它会被面板回调 / 物品右键 / 网络路径调到。</li>
 * </ol>
 *
 * <p><b>现役机上"扩容"退化成"预检 + 拒绝"</b>：{@link #growForBatchInsert(int)} /
 * {@link #growToCapacity(int)} 在现役机上都是 <b>no-op</b>（容量不可变），
 * 能否装下全部由 {@link #canFit(int)} + 引擎第 ② 步的整体拒绝负责
 * （现役机 {@code capacity() == maxCapacity()} ⇒ 这个分支会比惰性机更早、更频繁地命中，
 * 这是<b>正确行为</b>，不是缺陷）。
 */
public interface PatternArchiveTarget {

    // ==================================================================================
    // 一、只读判据（引擎"先算"阶段唯一允许碰的东西；一律零副作用）
    // ==================================================================================

    /**
     * 当前容量 = 逻辑槽位数（<b>可能小于硬上限</b>）。
     * <p>惰性机 = {@code getCapacity()}（= 已用最高槽 + 预留，只增不减）；
     * 现役机 = {@code getPatternInventory().getSlots()}（构造期定死，恒等于硬上限）。
     */
    int capacity();

    /** 硬上限（容量最多能到多少）。惰性机 = {@code getMaxPatternCount()}；现役机 = 同 {@link #capacity()}。 */
    int maxCapacity();

    /**
     * 预留空位（惰性机 = {@code LazyPatternAssemblyPartMachine.RESERVE} = 2；现役机 = <b>0</b>）。
     *
     * <p>现役机没有"预留"概念 ⇒ 返回 0 时 {@code needed = 最高目标槽 + 1}，
     * 语义正好是"要装到这格就得有这么多槽"，正是我们要的。
     */
    int reserve();

    /**
     * 本次落地需要 {@code needed} 个槽位（含预留）时，本机装得下吗？
     *
     * <p><b>只读、零副作用</b>：引擎拿它在"写第一个字节之前"做<b>整体拒绝</b>
     * （见 {@link LazyPatternArchiveCodec} 第 ② 步；拒绝时机器连容量都不动）。
     * <p>两台机器的判据都是"{@code needed} 不得超过硬上限"，但分成两个方法是为了让
     * "现役机容量固定"这件事在类型层面写明（现役机实现里写死了"不存在扩容这条路"）。
     */
    boolean canFit(int needed);

    /** 本机当前<b>已用最高槽</b>（0 起；空仓返回 -1）。追加模式的起点 = 它 + 1。 */
    int highestUsedSlot();

    /**
     * 只读地读一个槽的原始内容（引擎判空口径：{@code != null && !isEmpty()}）。
     *
     * <p>★ <b>读异常必须照原样外抛、不许在这里吞</b>：引擎把"读不出来"当成"这一槽有东西"
     * （⇒ 少写一条，绝不覆盖玩家已有的样板）；吞成 {@code EMPTY} 会反过来把那一槽当成空槽去写。
     *
     * <p><b>绝不许</b>在这里实体化槽位、建催化剂仓、扩容或写档（惰性机对应的懒实体化纪律见
     * {@code LazyPatternAssemblyPartMachine#getInternalSlot(int)} 的注释）。
     */
    ItemStack patternAt(int slot);

    /** 是不是客户端侧（客户端一律放过：归档是服务端权威的动作）。 */
    boolean isRemote();

    /** 机器坐标（日志/提示里的诊断用；拿不到可以返回 {@code null}）。 */
    BlockPos pos();

    // ==================================================================================
    // 二、档案读取（复制 / 剪切的数据来源：只读地把全仓 + 逐槽元数据打包）
    // ==================================================================================

    /** 「归档」页/物品的「含设置」开关（这台机器当前的设置）。 */
    boolean includeSettingsInArchive();

    /** 「归档」页/物品的「含名字」开关。 */
    boolean includeNameInArchive();

    /** 粘贴落位模式（默认 {@link LazyPatternArchiveCodec.PlaceMode#APPEND} 追加，绝不覆盖）。 */
    LazyPatternArchiveCodec.PlaceMode placeMode();

    /**
     * <b>只读</b>地把本机全仓打成一个档案（样板 + 逐槽配方类型缓存 + 手动指定 + 逐槽催化剂 + 阈值
     * [+ 设置 / 名字]）。<b>不动本机一个字节</b>。
     */
    LazyPatternArchiveCodec.Payload buildArchive(boolean includeSettings, boolean includeName);

    // ==================================================================================
    // 三、档案写入（引擎"落盘"阶段调用；逐槽，单槽失败只影响该槽）
    // ==================================================================================

    /** 把一张样板写进这个槽（引擎保证：这一槽在写之前是空的）。 */
    void writePattern(int slot, ItemStack stack);

    /** 写该槽的"配方类型缓存"（{@link #buildArchive} 搬走的那一份）。 */
    void writeRecipeType(int slot, int recipeType);

    /** 写该槽的"手动指定"（与存档同口径：存配方类型 id，不存下标）。 */
    void writeManualType(int slot, ResourceLocation typeId);

    /**
     * 写该槽的催化剂内容（子键 {@code items}/{@code fluids}/{@code count}，与
     * {@link #buildArchive} 写进档案的那一份**逐字对应**）。
     *
     * @return 是否真的写了东西（用于结果里的元数据条数）
     */
    boolean writeCatalyst(int slot, CompoundTag catalyst);

    /** 写设置类（只有档案带了、且调用方允许时才调）。 */
    void writeSettings(CompoundTag settings);

    /** 写机器名字（只有档案带了、且调用方允许时才调）。 */
    void writeName(String name);

    // ==================================================================================
    // 四、容量准备 / 清源 / 收尾
    // ==================================================================================

    /**
     * 追加模式：为本批 {@code count} 张样板准备容量（<b>只调一次</b>）。
     * <p>惰性机 = {@code growCapacityForBatchInsert(count)}（一次扩到位）；
     * 现役机 = <b>no-op</b>（容量不可变 ⇒ 装不下在 {@link #canFit(int)} 那一步已经整体拒绝了）。
     */
    void growForBatchInsert(int count);

    /**
     * 对齐模式：把容量准备到至少 {@code needed}（<b>只调一次</b>）。
     * <p>惰性机 = {@code growCapacityFor(needed)}；现役机 = <b>no-op</b>（同 {@link #growForBatchInsert(int)}）。
     */
    void growToCapacity(int needed);

    /**
     * <b>清空本仓的归档内容</b>（剪切语义的第二半：只有调用方确认"档案已写进工具"之后才许调）。
     *
     * <p>清空的内容必须与 {@link #buildArchive} 搬走的内容<b>一一对应</b>：
     * 样板 + 逐槽配方类型缓存 + 手动指定 + 逐槽催化剂（含阈值）；
     * 容量、共享催化剂 / 共享电路 / 镜像绑定等<b>不在本档案范围内</b>的东西一律不许动
     * （"没搬走却删掉"= 数据事故）。
     *
     * <p>收尾<b>不在这里</b>：由调用方随后调一次 {@link #afterBulkChange()}。
     */
    void clearAll();

    /**
     * <b>批量改动样板仓之后的唯一收尾入口</b>（粘贴 / 剪切清源都只调它一次）。
     *
     * <ul>
     *   <li>惰性机 = {@code onPatternInventoryChanged()}（既有的"作废映射 + 逐槽收尾 +
     *       {@code needPatternSync}"合并通道）；</li>
     *   <li>现役机 = {@code refreshAllByProduct()}（gtlcore 自己在 {@code pasteFromTag} 收尾时调的也是它：
     *       清 {@code slot2PatternMap} → <b>单趟</b>重扫全仓 → {@code reCalculatePatternSlotMap()} →
     *       {@code needPatternSync = true}）+ 静默作废"改写样板"缓存 —— <b>绝不</b>逐槽调
     *       {@code onPatternChange}（那会逐槽捅 AE，640 槽 = 卡死几秒）。</li>
     * </ul>
     *
     * <p>实现者<b>不许</b>在这里新增任何订阅 / 第二条刷新路径（红线）。
     */
    void afterBulkChange();
}
