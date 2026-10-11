package com.gtlmodular.common.machine;

import appeng.api.inventories.InternalInventory;
import com.gtlmodular.GTLModular;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/**
 * <b>「原版样板总成」适配器</b>：{@link PatternArchiveTarget} 对 gtlcore 具体类
 * {@link MEPatternBufferPartMachine}（以及它的任何子类）的实现 —— 让「样板仓归档」工具
 * 也能对<b>别人家的样板总成</b>做 复制 / 剪切 / 粘贴（追加）。
 *
 * <h3>它管的是哪些机器（判定口径）</h3>
 * <ul>
 *   <li>gtlcore 的原版样板总成 {@code MEPatternBufferPartMachine}
 *       （★ 它的<b>方块 id 本轮未取证</b>，代码里也完全不需要 —— 判定只按类，不按方块）；</li>
 *   <li><b>gtladditions 的超级样板总成</b>
 *       {@code com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine}
 *       （方块 {@code gtladditions:me_super_pattern_buffer}）——
 *       <b>本类不 import 它、也不认识它的名字</b>，它靠 {@code instanceof MEPatternBufferPartMachine} 命中。
 *       <br>取证（对该 .class 的常量池逐个字符串检索，源
 *       {@code gtl-dev/inspect/gtladditions/com/gtladd/gtladditions/common/machine/multiblock/part/MESuperPatternBufferPartMachine.class}）：
 *       ① 池里存在<b>不带 {@code L…;} 的内部名</b>
 *       {@code org/gtlcore/gtlcore/common/machine/multiblock/part/ae/MEPatternBufferPartMachine}
 *       （其 Utf8 长度字节 0x50 = 80，正是该内部名的长度）—— 这是 {@code CONSTANT_Class} 形态，
 *       即"被当作<b>类</b>引用"（超类 / owner），而不是只当字段类型用；
 *       ② 该类<b>没有</b> {@code patternInventory} 字段、也<b>没有</b> {@code maxPatternCount}，
 *       却<b>调用了</b> {@code getPatternInventory()}（而 {@code getPatternInventory()}
 *       在 gtlcore 里只声明在具体类 {@code MEPatternBufferPartMachine} 上：
 *       {@code MEPatternBufferPartMachineBase.class} 的常量池里根本没有这个名字）⇒
 *       它只能是从具体类<b>继承</b>来的；
 *       ③ 它<b>没有</b> {@code pasteFromTag / copyToTag / cutToTag}（一个都没重写）⇒
 *       读写入口就是 gtlcore 那套 ⇒ 本适配器天然覆盖它。
 *       <br>★ <b>gtladditions 缺失时本类照常加载</b>（一个字节都不引用那个类）⇒ 软依赖成立。</li>
 * </ul>
 *
 * <h3>为什么"只走 public API"（本类不是 gtlcore 的子类，够不到 protected）</h3>
 * {@link MoltenPatternArchiveTarget} 能干活是因为 {@link MoltenPatternAssemblyPartMachine}
 * <b>继承</b>了 gtlcore 具体类，于是 {@code maxPatternCount / catalystItems / catalystFluids /
 * refreshAllByProduct()} 这些 {@code protected} 成员都能直接读。本类包的是<b>别人的实例</b>，
 * 那批 protected 成员一个都拿不到 ⇒ 只用 javap 实证过的 public 入口（见下），
 * <b>绝不反射私有/保护成员</b>。
 *
 * <h3>逐个 public API 的取证出处</h3>
 * 反汇编存盘 {@code gtl-dev/api-dump/gtlcore/MEPatternBufferPartMachine.javap.txt}
 * （与工作区 {@code gtl-dev/api-dump/me_pattern_buffer.javap.txt} 是同一份类的两次转储，行号一致）：
 * <ul>
 *   <li>{@code public ItemStackTransfer getPatternInventory()} —— :2104（LDLib 的
 *       {@code getSlots() / getStackInSlot(int) / setStackInSlot(int, ItemStack)} 都在它上面用）；</li>
 *   <li>{@code public InternalInventory getTerminalPatternInventory()} —— :1980，
 *       字节码 {@code getfield internalPatternInventory} ⇒ 返回的<b>就是</b> {@code pasteFromTag}
 *       自己用的那个仓（:1983 与 :1187 是同一个字段）；</li>
 *   <li>{@code public InternalInventory.size() / getStackInSlot(int) /
 *       setItemDirect(int, ItemStack)} —— AE2 接口方法，:1187
 *       {@code invokeinterface appeng/api/inventories/InternalInventory.setItemDirect:(ILnet/minecraft/world/item/ItemStack;)V}
 *       （gtlcore 的 {@code pasteFromTag} 逐槽落盘用的就是这三句）；</li>
 *   <li>{@code public boolean isRemote() / BlockPos getPos() / Level getLevel()} —— 继承自
 *       {@code MetaMachine}（{@code gtl-dev/api-dump/gtceu_MetaMachine.javap.txt} :97 / :104 / :119 都是 public）。</li>
 * </ul>
 *
 * <h3>写侧为什么用 {@code setItemDirect} 而不是 {@code getPatternInventory().setStackInSlot}</h3>
 * {@code setItemDirect} 是 gtlcore 自己那条"逐槽落盘 + 逐槽收尾"的路：
 * 匿名内部类 {@code MEPatternBufferPartMachine$1}（= {@code getTerminalPatternInventory()} 返回的那个）
 * 的 {@code setItemDirect} 依次做
 * {@code patternInventory.setStackInSlot(slot, stack)} → {@code patternInventory.onContentsChanged(slot)}
 * → {@code onPatternChange(slot)}（常量池实证：{@code MEPatternBufferPartMachine$1.class} 同时引用
 * {@code setItemDirect / setStackInSlot / onContentsChanged / onPatternChange}）。
 * 而 {@code onPatternChange(int)}（:279，protected）的收尾是
 * "更新 slot2PatternMap/hasPatternArray → {@code reCalculatePatternSlotMap()} →
 * {@code needPatternSync = true}"（:360–:363）⇒ <b>AE 的样板表刷新由 gtlcore 自己负责</b>，
 * 我们这一侧不需要、也<b>不许</b>再加第二条刷新路径。
 *
 * <p>反过来说：LDLib 的 {@code ItemStackTransfer.setStackInSlot} <b>不</b>触发任何回调
 * （这就是 {@link MoltenPatternAssemblyPartMachine#archiveWritePattern(int, ItemStack)} 敢用它做
 * 批量落盘的原因 —— 那台机器是<b>我们的子类</b>，收尾由它自己的
 * {@code afterArchiveBulkChange()} 统一做一次）。本类够不到那个收尾入口，
 * 所以只能落到 gtlcore 自己的逐槽通知上；代价与 gtlcore 原版粘贴工具<b>完全同档</b>
 * （它也是逐槽 {@code setItemDirect}）。
 *
 * <h3>有意不动的机器状态（"没搬走就绝不删"）</h3>
 * 本档案对原版机只搬<b>样板本身</b>（{@code getPatternInventory()} 里的 {@code ItemStack}），
 * 因此：
 * <ul>
 *   <li><b>不碰</b>逐槽催化剂 {@code catalystItems / catalystFluids}（protected，且 gtlcore 的
 *       {@code copyToTag} 也不搬它们 —— :1029–:1098 只写 {@code pattern / cacheCount}）⇒
 *       剪切时<b>只清样板、保留催化剂</b>（没搬走却删掉 = 数据事故）；</li>
 *   <li><b>不碰</b>共享催化剂仓 / 共享电路 / 共享催化剂罐 / 镜像绑定 {@code proxies}
 *       —— 这正是<b>不走</b> {@code pasteFromTag(CompoundTag)} 的原因：那方法在收尾会
 *       {@code deserializeNBT} 这些键（:1220–:1269）并 {@code releaseProxies()}（:1271），
 *       还会 {@code setCustomName(tag.getString("name"))}（:1132–:1136），
 *       拿一份只带 {@code patterns} 的 tag 去调它会把对面机器的共享仓/代理绑定/名字一起洗掉
 *       （"原版机器的原有功能一个字都不能变"）；</li>
 *   <li><b>不碰</b>名字 {@code customName} 与 {@code keepByProduct}
 *       （{@code includeSettingsInArchive() / includeNameInArchive()} 恒 false ⇒
 *       引擎压根不会调 {@link #writeSettings(CompoundTag)} / {@link #writeName(String)}）。</li>
 * </ul>
 *
 * <h3>与"我们自家机器"的边界</h3>
 * {@link LazyPatternArchiveApi#toTarget(Object)} 的分发顺序是
 * <b>惰性机 → 现役机 → 本适配器</b>。{@link MoltenPatternAssemblyPartMachine} <b>也是</b>
 * {@code MEPatternBufferPartMachine} 的子类 ⇒ 顺序一旦颠倒，自家现役机就会被当"原版机"处理
 * （丢设置/名字、收尾换成 gtlcore 的逐槽通知）⇒ 那里的注释与顺序是硬约束，别动。
 */
public final class VanillaPatternArchiveTarget implements PatternArchiveTarget {

    private final MEPatternBufferPartMachine machine;

    public VanillaPatternArchiveTarget(MEPatternBufferPartMachine machine) {
        this.machine = machine;
    }

    /** 被包住的机器（诊断 / 将来扩展用）。 */
    public MEPatternBufferPartMachine machine() {
        return this.machine;
    }

    // ==================================================================================
    // 内部小工具：两个 public 入口的取用（一律吞异常 ⇒ 拿不到按"空仓"处理，绝不抛给 tick）
    // ==================================================================================

    /** gtlcore 的样板仓（构造期就 `new ItemStackTransfer(maxPatternCount)`，正常恒非 null）。 */
    private ItemStackTransfer inventory() {
        try {
            return machine.getPatternInventory();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 承载"逐槽写入 + 逐槽收尾"的那个 AE 仓（= gtlcore 自己 pasteFromTag 用的那个）。 */
    private InternalInventory terminalInventory() {
        try {
            return machine.getTerminalPatternInventory();
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ==================================================================================
    // 一、只读判据（全程零副作用）
    // ==================================================================================

    /**
     * 当前容量 = 仓的槽位数。
     * <p>原版机的容量<b>注册期定死</b>：gtlcore 的构造函数拿到的那个 int 参数一路
     * {@code putfield maxPatternCount}（:49–:80），并<b>同时</b>用它建仓
     * {@code patternInventory = new ItemStackTransfer(maxPatternCount)}（:119–:123）⇒
     * 仓的槽位数<b>就是</b>硬上限，所以这里恒等于 {@link #maxCapacity()}。
     * 用 public 的 {@code getSlots()} 读、不碰 protected 的 {@code maxPatternCount}，
     * 也不去猜子类（如 gtladditions 的超级样板总成）把容量算成了几 ——
     * 那个算式未取证，而"读仓本身"永远是对的。
     */
    @Override
    public int capacity() {
        ItemStackTransfer inventory = inventory();
        // 构造期（super() 内部）字段还没赋值 ⇒ 容错返回 0（与现役机 archiveCapacity() 同口径）
        return inventory == null ? 0 : inventory.getSlots();
    }

    /** 硬上限 = 当前容量（原版机不存在"惰性扩容"这一档）。 */
    @Override
    public int maxCapacity() {
        return capacity();
    }

    /** 原版机没有"预留空位"概念 ⇒ 0（`needed` 语义 = 计划里最高目标槽 + 1）。 */
    @Override
    public int reserve() {
        return 0;
    }

    /**
     * 容量预检：本次落地需要 {@code needed} 个槽位时装不装得下。<b>只读、零副作用</b>。
     * <p>引擎在<b>写第一个槽之前</b>调它（{@link LazyPatternArchiveCodec#apply} 第 ② 步）⇒
     * 装不下就整体拒绝，机器连一个槽都没碰。原版机容量不可变 ⇒ 这里就是它唯一也是最后一道容量关。
     */
    @Override
    public boolean canFit(int needed) {
        return needed <= capacity();
    }

    /** 本机当前已用最高槽（0 起；空仓 -1）。追加模式的起点 = 它 + 1。 */
    @Override
    public int highestUsedSlot() {
        ItemStackTransfer inventory = inventory();
        if (inventory == null) {
            return -1;
        }
        int slots;
        try {
            slots = inventory.getSlots();
        } catch (Throwable ignored) {
            return -1;
        }
        for (int i = slots - 1; i >= 0; i--) {
            try {
                ItemStack stack = inventory.getStackInSlot(i);
                if (stack != null && !stack.isEmpty()) {
                    return i;
                }
            } catch (Throwable ignored) {
                // 单槽读不出来 ⇒ 跳过它（绝不因此丢掉别的槽）
            }
        }
        return -1;
    }

    /**
     * 只读一个槽的原始内容。
     * <p>★ 读异常<b>照原样外抛</b>（这里不吞）：引擎把"读不出来"当成"这一槽有东西"
     * ⇒ 少写一条、绝不覆盖玩家已有的样板。
     */
    @Override
    public ItemStack patternAt(int slot) {
        ItemStackTransfer inventory = inventory();
        return inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(slot);
    }

    @Override
    public boolean isRemote() {
        return machine.isRemote();
    }

    @Override
    public BlockPos pos() {
        return machine.getPos();
    }

    // ==================================================================================
    // 二、档案读取（复制 / 剪切的数据来源）
    // ==================================================================================

    /** 原版机在<b>我们的</b>档案格式里没有"设置类"这项（它的设置是 keepByProduct 等机器自身状态）。 */
    @Override
    public boolean includeSettingsInArchive() {
        return false;
    }

    /** 名字同理：不搬 ⇒ 粘贴时既不会覆盖对面机器的名字，也不会把我们的名字写过去。 */
    @Override
    public boolean includeNameInArchive() {
        return false;
    }

    /** 落位模式恒 = 追加（工具的硬口径：任何情况下都不覆盖对面机器已有的样板）。 */
    @Override
    public LazyPatternArchiveCodec.PlaceMode placeMode() {
        return LazyPatternArchiveCodec.PlaceMode.APPEND;
    }

    /**
     * <b>只读</b>地把原版机全仓打成一个档案（逐槽样板 + 槽下标）。
     *
     * <p>为什么<b>直接扫仓</b>而不是用 gtlcore 的 {@code copyToTag}（那也是 public、也只读）：
     * <ol>
     *   <li>{@code copyToTag} 遍历的是 {@code patternSlotMap}（:1045–:1086），
     *       那是"样板 → 槽"的缓存映射；本方法扫的是仓本身 ⇒ <b>不受映射是否已建立影响</b>
     *       （映射空仓/刚读档时都能如实读出槽内容），且槽下标与仓<b>逐格对齐</b>；</li>
     *   <li>{@code copyToTag} 只写 {@code pattern / cacheCount} 两个键（:1070–:1081），
     *       催化剂一样搬不走 ⇒ 两条路的信息量相同，那就选更直接、更少依赖的那条；</li>
     *   <li>与自家现役机的 {@code buildArchivePayload} 口径一致（同样扫
     *       {@code getPatternInventory()}、同样只搬读得出来的槽）⇒ 两边行为可预期。</li>
     * </ol>
     *
     * <p>搬不走的（原版机上对应物要么 protected、要么根本不在本档案格式范围内）：
     * 逐槽催化剂、{@code cacheCount/cacheRecipe}、共享催化剂/共享电路/共享罐、镜像绑定、名字。
     * 单槽读失败 ⇒ <b>跳过那一槽</b>（绝不因此丢掉别的槽），并且<b>不动本机一个字节</b>。
     */
    @Override
    public LazyPatternArchiveCodec.Payload buildArchive(boolean includeSettings, boolean includeName) {
        LazyPatternArchiveCodec.Payload payload = new LazyPatternArchiveCodec.Payload();
        int capacity = capacity();
        payload.capacity = capacity;
        // 两个开关在这条路上恒 false（见本类注释）；如实写进档案，粘贴侧就不会去写设置/名字
        payload.includeSettings = false;
        payload.includeName = false;
        payload.name = "";
        payload.dimension = dimensionId();
        try {
            BlockPos pos = machine.getPos();
            payload.pos = pos == null ? 0L : pos.asLong();
        } catch (Throwable ignored) {
            // 坐标只是诊断信息，拿不到就留 0
        }

        ItemStackTransfer inventory = inventory();
        if (inventory == null) {
            payload.slotCount = 0;
            payload.patternCount = 0;
            return payload;
        }
        int highest = -1;
        for (int i = 0; i < capacity; i++) {
            ItemStack stack;
            try {
                stack = inventory.getStackInSlot(i);
            } catch (Throwable failure) {
                GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：读原版样板总成第 {} 槽失败（跳过该槽）pos={} 原因={}",
                        i + 1, pos(), failure.toString());
                continue;
            }
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            // recipeType / manualType / catalyst 一律 null：原版机这三样要么 protected、要么不在本档案口径内
            payload.entries.add(new LazyPatternArchiveCodec.Entry(i, stack.copy(), null, null, null));
            highest = i;
        }
        payload.slotCount = highest < 0 ? 0 : highest + 1;
        payload.patternCount = payload.entries.size();
        return payload;
    }

    /** 维度 id（诊断用；拿不到返回空串）。 */
    private String dimensionId() {
        try {
            net.minecraft.world.level.Level level = machine.getLevel();
            return level == null ? "" : level.dimension().location().toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    // ==================================================================================
    // 三、档案写入（逐槽；单槽失败只影响该槽）
    // ==================================================================================

    /**
     * 把一张样板写进这个槽（引擎保证：这一槽在写之前是空的，且 {@code slot < capacity()}）。
     *
     * <p>走 {@code getTerminalPatternInventory().setItemDirect(slot, stack)} ——
     * gtlcore 自己的逐槽落盘入口，写入之后自带
     * {@code onContentsChanged(slot)} + {@code onPatternChange(slot)}（见类注释）⇒
     * 映射/缓存/AE 刷新都由 gtlcore 自己按原样处理。
     *
     * <p>拿不到那个仓 ⇒ <b>抛异常</b>（引擎会把它记成"这一槽写失败"并如实报给玩家）。
     * <b>绝不</b>退化成"用 LDLib 的 setStackInSlot 静默写进去"：那条路不触发任何回调，
     * 机器会处于"仓里有样板、机器不知道"的不一致态 —— 宁可如实失败。
     */
    @Override
    public void writePattern(int slot, ItemStack stack) {
        InternalInventory terminal = terminalInventory();
        if (terminal == null) {
            throw new IllegalStateException("gtlcore 原版样板总成的样板仓不可用（getTerminalPatternInventory() == null）");
        }
        terminal.setItemDirect(slot, stack);
    }

    /**
     * 逐槽配方类型缓存：<b>无对应入口</b>（原版机的 {@code cacheRecipeCount/cacheRecipe} 都是 protected），
     * 且本档案从原版机读出来时这一项恒为 {@code null} ⇒ 故意 no-op。
     */
    @Override
    public void writeRecipeType(int slot, int recipeType) {
        // 故意的 no-op（见方法注释）
    }

    /** 逐槽"手动指定"是我们自家机器的概念，原版机上没有对应物 ⇒ 故意 no-op。 */
    @Override
    public void writeManualType(int slot, ResourceLocation typeId) {
        // 故意的 no-op（见方法注释）
    }

    /**
     * 逐槽催化剂：<b>不写</b>。
     *
     * <p>原版机的 {@code catalystItems / catalystFluids} 是 {@code protected}（够不到），
     * 且 gtlcore 自己的 {@code copyToTag} <b>也</b>不搬它们 ⇒ 我们这边写不进去是"与官方工具同口径"，
     * 而不是缺陷。返回 {@code false} = "一个字节都没写"，引擎据此不会把它算进元数据条数。
     */
    @Override
    public boolean writeCatalyst(int slot, CompoundTag catalyst) {
        return false;
    }

    /** 原版机的设置是它自己的机器状态（keepByProduct 等）⇒ 不写（引擎也不会调：includeSettings 恒 false）。 */
    @Override
    public void writeSettings(CompoundTag settings) {
        // 故意的 no-op（见方法注释）
    }

    /** 不写名字：粘贴不该改对面机器的名字（引擎也不会调：includeName 恒 false）。 */
    @Override
    public void writeName(String name) {
        // 故意的 no-op（见方法注释）
    }

    // ==================================================================================
    // 四、容量准备 / 清源 / 收尾
    // ==================================================================================

    /**
     * <b>no-op</b>：原版机容量注册期固定、不可变（{@code maxPatternCount} 是 {@code final}）。
     * <p>安全前提：引擎在调本方法<b>之前</b>已用 {@link #canFit(int)} 做过整体拒绝
     * ⇒ 走到这里 {@code needed <= capacity()} 必然成立。装不下的情况在写第一个槽之前就被拒了。
     */
    @Override
    public void growForBatchInsert(int count) {
        // 故意的 no-op（理由见上）
    }

    /** <b>no-op</b>：同 {@link #growForBatchInsert(int)}。 */
    @Override
    public void growToCapacity(int needed) {
        // 故意的 no-op（理由见上）
    }

    /**
     * 清空本仓的<b>样板</b>（剪切语义的第二半；调用方已确认档案写进工具之后才调）。
     *
     * <p>清空范围与 {@link #buildArchive(boolean, boolean)} 搬走的内容<b>一一对应</b>：
     * 只清样板槽。<b>逐槽催化剂原样保留</b> —— 它们没进档案，删掉就是纯数据事故
     * （gtlcore 自己的 {@code cutToTag} 会连催化剂一起清，但那是"它把催化剂也写进了 tag"的配套动作）。
     * 共享催化剂仓 / 共享电路 / 共享罐 / 镜像绑定 / 名字<b>一律不动</b>。
     *
     * <p>逐槽走 {@code setItemDirect(slot, EMPTY)}：与 gtlcore 的 {@code cutToTag}
     * 清源用的<b>同一个</b>入口（:1407 {@code internalPatternInventory.setItemDirect(i, ItemStack.EMPTY)}）
     * ⇒ 映射/缓存/AE 刷新由 gtlcore 自己按原样处理。
     * <p>单槽失败只记 WARN、继续清后面的槽；整个仓都拿不到 ⇒ 抛异常（调用方按"剪切异常"如实报错，
     * 绝不假装清干净了）。
     */
    @Override
    public void clearAll() {
        int capacity = capacity();
        if (capacity <= 0) {
            return;
        }
        InternalInventory terminal = terminalInventory();
        if (terminal == null) {
            throw new IllegalStateException("gtlcore 原版样板总成的样板仓不可用（getTerminalPatternInventory() == null）");
        }
        for (int i = 0; i < capacity; i++) {
            try {
                ItemStack current = terminal.getStackInSlot(i);
                if (current == null || current.isEmpty()) {
                    continue;
                }
                terminal.setItemDirect(i, ItemStack.EMPTY);
            } catch (Throwable failure) {
                GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：清空原版样板总成第 {} 槽失败（该槽保持原样）pos={} 原因={}",
                        i + 1, pos(), failure.toString());
            }
        }
    }

    /**
     * <b>no-op</b>：这条路上"收尾"已经由 gtlcore 自己在每一槽里做完了 ——
     * {@code setItemDirect} → {@code onPatternChange(slot)} → {@code reCalculatePatternSlotMap()} +
     * {@code needPatternSync = true}（:360–:363），AE 的样板表刷新由机器自己那条既有通道消费。
     *
     * <p>★ <b>这里绝不许再加第二条刷新路径</b>（{@link PatternArchiveTarget} 的红线）：
     * 对面是别人的机器，我们能碰的 public 入口里没有"批量收尾"这一档；
     * 唯一看似可行的是 {@code pasteFromTag(CompoundTag)}，但它会顺手
     * {@code deserializeNBT} 共享催化剂仓/共享电路/共享罐并 {@code releaseProxies()}（:1220–:1271）
     * ⇒ 拿一份只带 {@code patterns} 的 tag 去调它等于洗掉对面机器的共享状态，
     * 直接违反"原版机器的原有功能一个字都不能变"。
     */
    @Override
    public void afterBulkChange() {
        // 故意的 no-op（理由见方法注释）
    }
}
