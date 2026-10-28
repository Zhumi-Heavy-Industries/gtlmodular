package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ★★【结构位置缓存 + 方块移除定点失效】—— NAC 主机的「结构各格属于哪台主机」运行期缓存。★★
 *
 * <h2>这份缓存是干什么的（思路出处）</h2>
 * 另一位作者（秋叶）给的四步（用户转述的群聊原话，逐条对照）：
 * <ol>
 *   <li>「<b>缓存结构位置</b>」⇒ 本类：把「结构里每一格的世界坐标」按<b>主机相对偏移</b>缓存成一张表
 *       （{@link #cells()}，全模组一份，见下方「内存量化」）；</li>
 *   <li>「然后 <b>level 注入</b>一下」⇒ {@code com.gtlmodular.common.event.NanochipBlockRemovalHandler}
 *       （Forge 的 {@code BlockEvent.BreakEvent}，<b>零混入</b>）；</li>
 *   <li>「有个<b>方块移除的位置</b>」⇒ 事件给的 {@code (Level, BlockPos)}；</li>
 *   <li>「这个移除就去<b>缓存里找对应的位置对应的主机</b>」⇒ {@link #noteBlockRemoved(Level, BlockPos)}
 *       用「本维度已登记的主机坐标」逐个做一次相对偏移查表；</li>
 *   <li>「然后<b>判定不成形</b>」/「成型就看这个补了没」⇒ 命中后只把这份待办交给那台主机，
 *       由它调<b>上游自己的</b> {@code MultiblockState.onBlockStateChanged(pos, state)} ——
 *       跑不跑匹配、成形还是失效，<b>全部由 gtlcore 自己决定</b>（本类一行判定都不复制）。</li>
 * </ol>
 *
 * <h2>为什么需要它（上游自己的那份缓存会「死掉」——字节码取证）</h2>
 * gtlcore 的方块变更通知路径（{@code _census/_jtmp/gtl_LevelMixin_c.txt:9-58}）是：
 * <pre>
 *   Level.setBlock → getControllersInChunk(chunkPos) → for state:
 *       if (state.getController() == null) continue;            // 85-90
 *       if (!state.getController().isFormed()) continue;        // 85-90
 *       if (state.isPosInCache(pos)) server.execute(() -&gt; state.onBlockStateChanged(pos, newState));  // 96-120
 * </pre>
 * 两个前提都是<b>纯运行期数据</b>，都不入存档：
 * <ul>
 *   <li>{@code chunkPosMapping}：{@code MultiblockWorldSavedData.save(tag)} 逐字节码是
 *       {@code aload_1; areturn}（<b>原样返回、什么都不写</b>，
 *       {@code _census/_jtmp/MWSD_c.txt:147-150}），而它的 NBT 构造器
 *       {@code <init>(ServerLevel, CompoundTag)} 只是转调 {@code <init>(ServerLevel)}
 *       （同文件 {@code :59-64}）⇒ <b>读档后 chunkPosMapping 恒为空</b>
 *       ⇒ 上面那条 {@code if} 一个都进不去 ⇒ <b>敲掉方块，上游根本不会收到通知</b>；</li>
 *   <li>{@code MultiblockState.cache}：{@code multiblockState} 字段只有 {@code @DescSynced}、
 *       <b>没有 {@code @Persisted}</b>（{@code _census/_jtmp/MCM_v.txt:563-581}），
 *       {@code getMultiblockState()} 是惰性新建（同文件 {@code :735-753}），
 *       而 {@code clean()} 每次匹配都换一个空 {@code LongOpenHashSet}
 *       （{@code _census/_jtmp/MBState_c.txt:60-80}）⇒ 读档后 {@code isPosInCache(pos)} 恒假。</li>
 * </ul>
 * 同时周期路径也帮不上忙：{@code asyncCheckPattern} 第 0–7 字节码是
 * {@code if (!getMultiblockState().hasError() &amp;&amp; isFormed) return}
 * （{@code _census/_jtmp/gtl_MCMMixin_c.txt:16-34}），而 {@code isFormed} 是 {@code @Persisted}
 * （{@code MCM_v.txt:583-590}）⇒ 读档恢复出来的「已成形」会让这一台<b>永久退出周期重扫</b>。
 * <b>两条路同时哑掉 = 用户实测的「敲掉一个方块后主机仍显示结构已成型」。</b>
 * （定位文档：{@code _census/NAC读档部件未连接_定位.md} §②.5 / §③.2；本类补的正是这一环。）
 *
 * <h2>缓存内容 = 上游 {@code MultiblockState.cache} 的「全朝向」超集（依据 = 字节码）</h2>
 * <ul>
 *   <li>gtlcore 的 {@code BlockPatternMixin.gtlcore$matchSlice} 对 {@code predicate.isAny()} 直接
 *       {@code continue}（{@code _census/_jtmp/gtl_BPM_c.txt:323-326}：{@code isAny → goto 406} 循环尾）
 *       ⇒ any() 格既不 {@code updateState} 也不 {@code addPosCache}；</li>
 *   <li>{@code TraceabilityPredicate.addCache()} = {@code !isAny()}
 *       （{@code _census/_census_图案减肥_队员.md} §①.3 证据 9）⇒ <b>只有非 any() 的格进 cache</b>；</li>
 *   <li>⇒ 我们直接取 {@link NanochipComplexPattern#hostCheckCells()}（口径 = 「不是 any() 的格」，
 *       NAC 主机共 <b>8,281</b> 格）作为「结构位置」的<b>权威定义</b>，再对它枚举
 *       {@link NanochipExactPrecheck#ELEMENTS}（= 8）个朝向元素
 *       （{@link NanochipComplexPattern#elementOffset}，与 A+ 精确预检<b>同一套</b>枚举，绝不另写一份）。</li>
 * </ul>
 *
 * <h2>为什么枚举 8 个元素、为什么这是安全的（正确性论证）</h2>
 * 真实朝向元素在「读档故障态」下无法确定（那正是我们需要缓存的时刻），所以这里取
 * <b>8 个元素的并集</b>：
 * <ul>
 *   <li><b>只会多、不会少</b>（superset）：命中集合 ⊇ 真实结构格集合 ⇒ 最坏是"多让上游重判一次"，
 *       而上游会得出「仍然成形」——<b>不可能把成形的判成未成形</b>（失效结论只可能来自
 *       上游在<b>当下世界状态</b>下真的匹配失败）；</li>
 *   <li>朝向守卫与 A+ 精确预检同源（{@code NanochipExactPrecheck} 类注释：facing 水平 +
 *       upwardsFacing == NORTH 时任何被 GTCEu 接受的布局都落在 D4 的 8 个元素里）⇒ 正常玩法下
 *       并集 = 真实布局，几乎不产生多余重判。</li>
 * </ul>
 *
 * <h2>★★ 本轮升级（0.3.121 的「只存坐标」→「坐标 + 指针」）★★</h2>
 * <b>触发原因（用户原话）</b>：「位置表就是一个 long 一个指针，指向的都是一个 be」「我想又快又稳」。
 * 落地形态 = 表里每一条就是一个 {@link HostEntry}：<b>一个坐标（{@code long} packed）
 * + 一个指向那台机器的指针</b>。查表从
 * 「二分命中 ⇒ {@code MetaMachine.getMachine(level, hostPos)} 回世界现取」
 * 变成「二分命中 ⇒ <b>直接读指针</b>」⇒ 查询期<b>零世界访问、零哈希查表、零对象分配</b>
 * （步数对比与内存量化见 {@code _census/位置表指针化_实施记录.md} §①/§⑥）。
 * <ul>
 *   <li><b>指针一律用 {@link WeakReference}</b>：静态表在<b>结构上</b>不可能钉住机器 / 方块实体 / Level
 *       （一旦用强引用而漏摘一条，就会把 {@code MetaMachine → holder(BE) → Level → 该维度区块}
 *       整条引用链钉住）。而机器只要真在世界里，就被 {@code Level → LevelChunk → BlockEntity → machine}
 *       这条<b>强</b>引用链钉着 ⇒ 弱引用<b>不会</b>被提前清掉（"活着"时语义与强引用等价，
 *       差别只在"死了之后"）。同仓先例：{@code _census/shanhai-src/.../MEPatternBufferCacheInvalidationMixin.java:20-23}
 *       「区块卸载场景由 owner 表的 {@code WeakReference} 语义兜底，拆除场景在此主动注销」。</li>
 *   <li><b>每次使用前必过三道存活校验</b>：① {@code pointer.get() != null}（没被 GC）→
 *       ② {@code !host.isInValid()}（{@code MetaMachine.isInValid()} 就是
 *       {@code holder.getSelf().isRemoved()}，字节码 {@code _census/_jtmp/MetaMachine_c.txt:182-188}
 *       ⇒ 专门堵"GC 还没跑、弱引用还活着，但方块实体其实已经被 {@code setRemoved()} 卸载"的窗口）→
 *       ③ {@code host.getPos().asLong() == entry.packed}（<b>还是那台机器</b>）。
 *       <b>任一条不过 ⇒ 不使用该指针</b>（继续下一个候选 ⇒ 最坏回到上游原有行为），顺手摘掉空壳。</li>
 *   <li><b>摘除 = 主动五处 + 兜底一层</b>。主动：机器拆除 / 机器卸载（含<b>区块卸载</b>）/ 结构失效 /
 *       每 tick 对齐 / 维度卸载（清单见实施记录 §③）。兜底：弱引用 + 查询/登记时惰性清空壳
 *       ⇒ 即使将来有人新开一条"机器消失"的路径而忘了注销，残留也只是一个 ≈120 B 的空壳，
 *       且同维度下一次任何活动就会被清掉 —— <b>永远不会钉住任何活对象</b>。</li>
 *   <li><b>注销带身份校验（顺带修掉的一个真缺口）</b>：{@code unregister(...)} 要求"表里那条指针
 *       就是调用者自己"，否则<b>拒绝删除</b>。0.3.121 的坐标口径下，客户端镜像机器的
 *       {@code onUnload()/onStructureInvalid()/onMachineRemoved()} 会按
 *       「同 {@code dimension()} + 同 {@code BlockPos}」把<b>服务端</b>那条登记摘掉，
 *       而服务端因为不变式 {@code formed == registered} 会早退、<b>不会自愈</b>
 *       （静态可推：{@code unregister} 三个调用点都没有 {@code isClientSide} 判断，
 *       见 {@code NanochipAssemblyComplexMachine.java:4286/5260/5322}）。改成身份口径后不可能发生。</li>
 * </ul>
 *
 * <h2>内存量化（全模组一份表 + 每台一条「坐标 + 指针」）</h2>
 * <pre>
 *   共享相对偏移表（与 0.3.121 <b>逐字相同、一个字节没动</b>）
 *     CHECK 格数          n = 8,281（{@code hostCheckCells()}，与上游 cache 同口径）
 *     并集上界            8 × 8,281 = 66,248 项（180° 旋转对称会让实际值更小）
 *     存储形态            {@code long[]}（升序去重，{@code Arrays.binarySearch} 查表）
 *     上限字节            66,248 × 8 B ≈ <b>518 KB</b>（最坏；典型 2~4 倍 ≈ 130~260 KB）
 *     建表临时数组        一次 {@code long[66,248]}（≈518 KB），建完即可回收
 *   每台主机一条「坐标 + 指针」（本轮：载荷 8 B → <b>≈120 B</b>；逐项见实施记录 §⑥）
 *     {@link HostEntry}   ≈ 40 B（对象头 12 + long packed 8 + 3×int 12 + 指针 4）
 *     {@link WeakReference} ≈ 32 B（对象头 + referent + 队列字段）
 *     {@code ConcurrentHashMap} 节点 ≈ 48 B（含装箱 {@code Long} 16 B）
 *     ⇒ <b>≈ 120 B/台</b>（0.3.121 口径：{@code Set<Long>} 节点 + 装箱 {@code Long} ≈ 48 B/台
 *        —— 那 8 B 只是<b>载荷</b>，没算容器）
 *     查表成本            每台主机一次二分（≤17 次比较）+ <b>两次字段读 + 一次坐标比较</b>
 * </pre>
 * ⇒ 与「每台一份 {@code Map<世界坐标, 主机>}」（≈200 KB/台，且读档后重建不出来）相比，
 *   每台仍然小 <b>3 个数量级</b>；总内存<b>不随主机数量增长</b>到表那一量级
 *   （100 台也只多 ≈ 7 KB，相对 518 KB 的表 ≈ +1.4%）。
 * 若将来要更省，可按「已知元素」只建 1/8 的表（那需要先确定朝向，见实施记录 §⑧ 的推广方案）。
 *
 * <h2>纪律</h2>
 * <ul>
 *   <li><b>零存档影响</b>：本类不新增任何 {@code @Persisted}、不写 NBT（运行期派生数据）；
 *       指针也<b>不进存档</b>（它本来就没法序列化，正好与"运行期派生"这条纪律一致）；</li>
 *   <li><b>零混入</b>：不新增/不启用 mixin，入口是 Forge 事件；</li>
 *   <li><b>fail-safe</b>：拿不到世界 / 客户端 / 表建不出来 / 登记表为空 / 指针失效 / 校验不过
 *       ⇒ 直接跳过，逐字回到「上游原有行为」（不报错、不误判、不重复触发失效）；</li>
 *   <li><b>指针纪律</b>：只以 {@link WeakReference} 保存；每次使用前必过三道存活校验；
 *       注销必带身份校验；任何拿不准 ⇒ <b>不使用指针</b>（绝不用"看着像"的对象去动作）；</li>
 *   <li><b>线程</b>：写方 = 服务端线程（主机 tick / 事件 / 卸载都在服务端线程），
 *       登记表用 {@link ConcurrentHashMap} + {@code volatile}/{@code final} 字段
 *       ⇒ 无锁读，读到的永远是完整值（{@link HostEntry} 全字段 {@code final}，安全发布）。</li>
 * </ul>
 */
public final class NanochipStructurePosIndex {

    private NanochipStructurePosIndex() {
    }

    /**
     * ★ 总开关（默认 {@code true} = 开）。
     *
     * <p>置 {@code false}（一行、重新编译）⇒ <b>逐字恢复</b>本轮之前的行为：
     * 事件处理器直接返回（不查表）、主机 {@code tickModuleRefresh()} 里那两行也不做事
     * ⇒ 不登记、不缓存、不建表、不打任何新日志。
     *
     * <p><b>为什么默认开</b>：这组改动只做一件事 —— 把「上游那两条通知路径哑掉时，
     * 敲掉方块却谁也不重判」这个已定位的缺口补上；失败姿态一律回退到上游行为，
     * 且唯一可能的副作用是"多跑一次真匹配"（见类注释的正确性论证），
     * 属于"只删风险、不换结论"的一类，所以默认开。
     */
    public static final boolean ENABLED = true;

    /**
     * ★ <b>本轮新增的独立开关</b>（默认 {@code true} = 用指针快查）。
     *
     * <p>{@code true} ⇒ 查表命中后<b>直接用表里存的指针</b>（{@link WeakReference} + 三道存活校验）；
     * {@code false} ⇒ <b>逐字回到 0.3.121 的坐标口径</b>：表里仍然维护指针（注销/身份校验照旧生效），
     * 但查询时改为 {@code MetaMachine.getMachine(level, hostPos)} 回世界现取机器对象。
     *
     * <p><b>为什么默认开</b>：① <b>快</b> —— 把"每台主机一次世界查询（区块哈希 + 方块实体哈希 +
     * 类型检查 + 一次对象分配）"换成"两次字段读 + 一次坐标比较"；② <b>稳</b> —— 指针只以
     * {@link WeakReference} 保存（结构上不可能钉住机器/BE/Level），且使用前逐条校验
     * 「非空 / 未被移除 / 坐标一致」，两条都不依赖"枚举所有摘除路径"这种证明义务。
     * ③ 与 {@link #ENABLED} 分工明确：{@code ENABLED} 管整条"结构移除 ⇒ 定点失效"链路，
     * 本开关只管"查表信不信指针" ⇒ 万一实机发现指针路径有任何异常，
     * <b>改这一行</b>就把查询退回 0.3.121，而 {@code ENABLED} 那一整套能力一条不减。
     */
    public static final boolean POINTER_LOOKUP = true;

    /** 结构水平包围盒的采样半径与步长（见 {@link #structureChunksLoaded}）。 */
    private static final int LOADED_PROBE_RADIUS = 32;
    private static final int LOADED_PROBE_STEP = 16;

    /**
     * 结构位置表：<b>已按主机相对偏移打包</b>（{@code BlockPos.asLong(dx, dy, dz)}）的升序去重数组。
     *
     * <p>{@code null} = 还没建（或建失败，见 {@link #buildFailed}）。
     */
    private static volatile long[] relativeCells;

    /** 建表出过事 ⇒ 永久回退（上游行为），不再重试、不再刷日志。 */
    private static volatile boolean buildFailed;

    /**
     * 维度 → 本维度里「已成形、且已登记」的 NAC 主机：<b>{@code 主机坐标(long) → 坐标 + 指针}</b>。
     *
     * <p>键 = 主机控制器坐标的 {@code BlockPos.asLong()}（用来算"被移除的那一格"到这台主机的相对偏移）；
     * 值 = {@link HostEntry}（<b>坐标 + 指向那台机器的指针</b>）。
     *
     * <p>为什么指针必须是 {@link WeakReference}：这是<b>静态表</b> —— 一旦用强引用而漏摘一条，
     * 就会把 {@code MetaMachine → holder(BE) → Level → 该维度全部区块} 整条引用链钉住。
     * 弱引用让"不泄漏"成为<b>结构性质</b>（由 GC 保证），而不是"我枚举全了所有摘除时机"这份证明。
     * 反过来，机器只要真在世界里，就被 {@code Level → LevelChunk → BlockEntity → machine}
     * 这条<b>强</b>引用链钉着 ⇒ 弱引用不会被提前清掉。
     */
    private static final Map<ResourceKey<Level>, Map<Long, HostEntry>> HOSTS = new ConcurrentHashMap<>();

    /**
     * <b>结构位置表里的一条 = 一个坐标（{@code long}）+ 一个指向那台机器的指针。</b>
     *
     * <p>字段全 {@code final} ⇒ 天然安全发布（任何线程读到的都是完整值，不需要额外同步）。
     * 预拆出 {@link #x}/{@link #y}/{@link #z} 是有意的：0.3.121 每命中一台都要
     * {@code BlockPos.of(packed)} <b>分配一个对象</b>，现在只读三个 int。
     */
    private static final class HostEntry {

        /** 主机控制器坐标（{@code BlockPos.asLong}）：条目身份（算相对偏移 + 校验"还是那台机器"）。 */
        final long packed;

        /** 预拆的坐标分量（省掉查表期的 {@link BlockPos} 分配）。 */
        final int x;
        final int y;
        final int z;

        /** ★ 指针：指向那台机器的<b>弱</b>引用（决策与理由见类注释「本轮升级」一节）。 */
        final WeakReference<NanochipAssemblyComplexMachine> pointer;

        HostEntry(long packed, int x, int y, int z, NanochipAssemblyComplexMachine host) {
            this.packed = packed;
            this.x = x;
            this.y = y;
            this.z = z;
            this.pointer = new WeakReference<>(host);
        }
    }

    // ------------------------------------------------------------------------------------
    // ① 「缓存结构位置」—— 建表
    // ------------------------------------------------------------------------------------

    /**
     * 结构位置表（惰性建一次；失败即永久回退）。返回 {@code null} 表示<b>本功能本次不可用</b>。
     *
     * <p>建表 = 对 {@link NanochipComplexPattern#hostCheckCells()} 的每一格，
     * 枚举 {@link NanochipExactPrecheck#ELEMENTS} 个朝向元素求相对偏移，排序去重。
     * 纯算术 + 纯静态数据（不读世界、不碰注册表）⇒ 任何线程、任何时刻都可建。
     */
    public static long[] cells() {
        long[] local = relativeCells;
        if (local != null || buildFailed) {
            return local;
        }
        synchronized (NanochipStructurePosIndex.class) {
            if (relativeCells != null || buildFailed) {
                return relativeCells;
            }
            try {
                int[] flat = NanochipComplexPattern.hostCheckCells();
                int count = flat.length / 3;
                if (count == 0) {
                    buildFailed = true;
                    GTLModular.LOGGER.warn("[gtlmodular] NAC 结构位置缓存：hostCheckCells() 为空 ⇒ "
                            + "本功能放弃（结构移除通知回到上游原有行为）");
                    return null;
                }
                long[] all = new long[count * NanochipExactPrecheck.ELEMENTS];
                int[] off = new int[3];
                int k = 0;
                for (int element = 0; element < NanochipExactPrecheck.ELEMENTS; element++) {
                    for (int i = 0; i < count; i++) {
                        int base = i * 3;
                        NanochipComplexPattern.elementOffset(element,
                                NanochipComplexPattern.NAC_CTRL_X - flat[base],
                                flat[base + 1] - NanochipComplexPattern.NAC_CTRL_ROW,
                                NanochipComplexPattern.NAC_CTRL_Z - flat[base + 2], off);
                        all[k++] = BlockPos.asLong(off[0], off[1], off[2]);
                    }
                }
                Arrays.sort(all);
                int uniq = 1;
                for (int i = 1; i < all.length; i++) {
                    if (all[i] != all[uniq - 1]) {
                        all[uniq++] = all[i];
                    }
                }
                long[] built = Arrays.copyOf(all, uniq);
                relativeCells = built;
                GTLModular.LOGGER.info("[gtlmodular] NAC 结构位置缓存已建立：CHECK 格 {} × {} 朝向元素 "
                                + "⇒ 去重后 {} 项（约 {} KB，全模组一份；查表 = 每台主机一次二分）。"
                                + "依据：_census/结构位置缓存与移除失效_实施记录.md",
                        count, NanochipExactPrecheck.ELEMENTS, uniq, built.length * 8L / 1024L);
                return built;
            } catch (Throwable t) {
                buildFailed = true;
                GTLModular.LOGGER.warn("[gtlmodular] NAC 结构位置缓存建表失败 ⇒ 本功能放弃"
                        + "（结构移除通知回到上游原有行为，不影响任何成形/失效判定）", t);
                return null;
            }
        }
    }

    /** 诊断：结构位置表的项数（{@code -1} = 还没建 / 建失败）。 */
    public static int cachedCellCount() {
        long[] local = relativeCells;
        return local == null ? -1 : local.length;
    }

    // ------------------------------------------------------------------------------------
    // ② 主机登记表（「哪台主机在这一维度里」）—— 每条 = 一个坐标 + 一个指针
    // ------------------------------------------------------------------------------------

    /**
     * <b>登记一台已成形的主机</b>（幂等）。服务端线程调用；{@code onStructureFormed} 也可能在
     * GTCEu 异步结构线程上调 —— {@link ConcurrentHashMap} + {@link WeakReference} 都线程安全。
     *
     * <p>客户端世界直接拒绝登记：登记表按 {@code dimension()} 分组，而单人档里客户端与服务端
     * 世界的 {@code dimension()} <b>是同一个键</b> ⇒ 让客户端也登记只会造成两侧互相覆盖/互摘
     * （缺口的完整描述见类注释「注销带身份校验」那一节）。
     */
    public static void register(Level level, NanochipAssemblyComplexMachine host) {
        // ★★ 2026-11-12【原初模式】总开关：不登记、不建表、不缓存 ★★
        //   不接管「结构各格属于哪台主机」这份簿记 ⇒ 结构失效判定完全回到上游自己的
        //   LevelMixin → MultiblockState.onBlockStateChanged 事件路径（我们那条"方块移除 ⇒ 定点失效"
        //   整条链随之闲置：另一入口 noteBlockRemoved() 同样在原初模式下直接返回，
        //   于是主机的 pendingRemovedPos 恒为 null，invalidateOnCachedRemoval() 走稳态快路径）。
        //   代价（如实登记）：读档后上游 cache 为空时敲掉结构方块不会有人重判 ——
        //   这正是「原初模式」要暴露的现状（见 _census/结构归上游_对照山海方案.md §③.1）。
        //   置 false 即逐字回到原行为（一行回退）。
        if (!ENABLED || GTLModularConfig.isNanochipOriginalMode()
                || level == null || host == null || level.isClientSide) {
            return;
        }
        try {
            BlockPos pos = host.getPos();
            if (pos == null) {
                return;
            }
            long packed = pos.asLong();
            Map<Long, HostEntry> hosts = hostsOf(level.dimension(), true);
            if (hosts == null) {
                return;
            }
            hosts.put(packed, new HostEntry(packed, pos.getX(), pos.getY(), pos.getZ(), host));
            purgeDead(hosts);                            // 顺手清空壳（表只有十几条，成本可忽略）
        } catch (Throwable t) {
            GTLModular.LOGGER.debug("[gtlmodular] NAC 结构位置登记失败（本功能对该台不可用，"
                    + "结构判定不受任何影响）：{}", t.toString());
        }
    }

    /**
     * <b>注销一台主机</b>（幂等）。调用点 = 机器被拆 / 机器卸载（含<b>区块卸载</b>）/
     * 结构失效 / 每 tick 对齐（清单见 {@code _census/位置表指针化_实施记录.md} §③）。
     *
     * <p>维度由调用方给出（{@code ResourceKey<Level>}）而<b>不是</b>现读 {@code host.getLevel()}：
     * 卸载路径上 {@code BlockEntity.level} 是否还非空属于上游实现细节，不拿它当必要条件
     * ⇒ 卸载时也能精确摘除（注册那一侧本来就记着这个维度键）。
     *
     * <p><b>★ 身份校验（本轮新增的硬保证）</b>：只有表里那条指针<b>就是调用者自己</b>时才删除。
     * 于是"同维度 + 同坐标的另一台机器"（典型 = 客户端镜像机器的 {@code onUnload()}）
     * <b>摘不掉</b>服务端那条 —— 这是 0.3.121 坐标口径下真实存在、且服务端不会自愈的缺口
     * （类注释有逐行论证）。指针已被 GC 清掉时视为"已死"⇒ 允许删除（清掉空壳只会更好）。
     */
    public static void unregister(ResourceKey<Level> dim, NanochipAssemblyComplexMachine host) {
        if (dim == null || host == null) {
            return;
        }
        try {
            Map<Long, HostEntry> hosts = HOSTS.get(dim);
            if (hosts == null) {
                return;
            }
            BlockPos pos = host.getPos();
            if (pos == null) {
                return;
            }
            long packed = pos.asLong();
            HostEntry entry = hosts.get(packed);
            if (entry == null) {
                return;
            }
            NanochipAssemblyComplexMachine current = entry.pointer.get();
            if (current != null && current != host) {
                return;                                  // ★ 不是自己那条 ⇒ 绝不误摘
            }
            hosts.remove(packed, entry);                 // 按"键 + 值"删（并发下不会误删刚登记的新条目）
        } catch (Throwable t) {
            GTLModular.LOGGER.debug("[gtlmodular] NAC 结构位置注销失败（最坏只留一个空壳，"
                    + "使用前还有三道存活校验兜底）：{}", t.toString());
        }
    }

    /**
     * <b>维度卸载</b>（世界卸载 / 服务器停止 / 单人档退出）⇒ 整个维度的登记表一次性丢掉。
     *
     * <p>这是「摘除时机全覆盖」里最后一道<b>主动</b>闸：即使某台机器没走到自己的
     * {@code onUnload()}，这一清也不会留下条目。弱引用则是它下面的那一层 —— 即使连这一清
     * 也没走到（例如崩溃、或将来新加的卸载路径），也<b>不可能</b>钉住任何机器 / 方块实体 / 世界。
     */
    public static void forgetDimension(ResourceKey<Level> dim) {
        if (dim == null) {
            return;
        }
        try {
            HOSTS.remove(dim);
        } catch (Throwable t) {
            GTLModular.LOGGER.debug("[gtlmodular] NAC 结构位置维度清理失败（弱引用兜底，无对象泄漏）：{}",
                    t.toString());
        }
    }

    /** 诊断：本维度登记了几台主机。 */
    public static int hostCount(Level level) {
        if (level == null) {
            return 0;
        }
        Map<Long, HostEntry> hosts = HOSTS.get(level.dimension());
        return hosts == null ? 0 : hosts.size();
    }

    /** 取（或惰性建）某个维度的登记表；并发下只留一张（{@code putIfAbsent}）。 */
    private static Map<Long, HostEntry> hostsOf(ResourceKey<Level> dim, boolean create) {
        if (dim == null) {
            return null;
        }
        Map<Long, HostEntry> hosts = HOSTS.get(dim);
        if (hosts == null && create) {
            Map<Long, HostEntry> fresh = new ConcurrentHashMap<>();
            Map<Long, HostEntry> prev = HOSTS.putIfAbsent(dim, fresh);
            hosts = prev == null ? fresh : prev;
        }
        return hosts;
    }

    /**
     * 清掉"指针已被 GC 回收 / 方块实体已被移除"的空壳条目。
     *
     * <p>为什么需要它：{@link WeakReference} 只保证<b>不钉住对象</b>，它不会主动来删掉自己所在的
     * 哈希条目 ⇒ 由"登记时 + 每次查表命中失效项时"顺手清（表只有十几条，成本可忽略）。
     * 这一步让条目数也收敛到"当前真实主机数"，而不是"历史上登记过的台数"。
     */
    private static void purgeDead(Map<Long, HostEntry> hosts) {
        for (Map.Entry<Long, HostEntry> row : hosts.entrySet()) {
            HostEntry entry = row.getValue();
            NanochipAssemblyComplexMachine host = entry.pointer.get();
            if (host == null || host.isInValid()) {
                hosts.remove(row.getKey(), entry);
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // ③ 「方块移除的位置 → 对应的主机」查表
    // ------------------------------------------------------------------------------------

    /**
     * <b>一个方块被移除时的入口</b>（由 Forge 事件调用；服务端线程）。
     *
     * <p>对本维度每一台已登记主机做一次「相对偏移是否在结构位置表里」的查表；命中就把这个坐标
     * 记到那台主机上（{@link NanochipAssemblyComplexMachine#noteCachedBlockRemoved(BlockPos)}），
     * 由它在<b>下一 tick</b>（那时方块才真的已经被移除）让上游重判。
     *
     * <h3>★ 命中之后怎么拿机器（本轮升级的核心路径）</h3>
     * <pre>
     *   0.3.121：{@code BlockPos.of(packed)}（分配）→ 逐个 {@code MetaMachine.getMachine(level, hostPos)}
     *            = 区块查表 + 方块实体查表 + 两次 instanceof + {@code getMetaMachine()}
     *   本轮    ：{@code entry.pointer.get()}（一次字段读）→ {@code !isInValid()}（一次字段读）
     *            → {@code getPos().asLong() == entry.packed}（一次比较）⇒ <b>零世界访问、零分配</b>
     * </pre>
     *
     * <h3>存活校验（每次使用指针前，逐条；任一条不过 ⇒ 不用、顺手摘空壳、继续下一台）</h3>
     * <ol>
     *   <li>{@code pointer.get() != null} —— 没被 GC 回收（区块/维度卸载后 {@code Level → chunk → BE}
     *       这条强链断开，弱引用会被清掉）；</li>
     *   <li>{@code !host.isInValid()} —— <b>方块实体没被移除</b>（{@code MetaMachine.isInValid()} 逐字节码
     *       就是 {@code holder.getSelf().isRemoved()}，{@code _census/_jtmp/MetaMachine_c.txt:182-188}）。
     *       这一条专堵"GC 还没跑、弱引用还活着，但 BE 其实已经被 {@code setRemoved()} 卸载"的窗口；</li>
     *   <li>{@code host.getPos().asLong() == entry.packed} —— <b>还是那台机器</b>（同坐标换了新机器、
     *       或异侧镜像机器，都会在这里被挡掉）。</li>
     * </ol>
     * 通过三道之后，还有主机的两道把关（不在本类）：{@code invalidateOnCachedRemoval()} 里的
     * {@code isFormed()}（{@code NanochipAssemblyComplexMachine.java:4587}）与
     * {@code structureChunksLoaded(...)}（同文件 {@code :4591}）。
     *
     * <p><b>为什么这里只记账、不当场判定</b>：{@code BlockEvent.BreakEvent} 在
     * 「方块真的被移除」<b>之前</b>触发（原版 {@code ServerPlayerGameMode#destroyBlock} 的流程是先
     * 问 Forge「让不让挖」，再 {@code removeBlock}）⇒ 当场判定读到的是<b>旧世界</b>
     * （那一格还是原方块）⇒ 只会白跑一次匹配、并且会把"真结果"污染给同 tick 折叠。
     * 延到下一 tick 与队长钉死的口径「拆一格外壳 ⇒ <b>下一 tick</b> 失效」逐字一致。
     *
     * <p>任何异常都吞掉（只落 debug 日志）：这是纯增量的"早发现"通道，
     * 绝不允许它成为新的故障源。拿不准 ⇒ 什么都不做 = 逐字回到上游原有行为。
     */
    public static void noteBlockRemoved(Level level, BlockPos brokenPos) {
        // ★★ 2026-11-12【原初模式】总开关：不记账、不查表 ★★
        //   两侧一起才构成"整条链不接管"：register() 不在原初模式下登记任何主机 ⇒ 本方法即使
        //   被事件钩子叫到也查不到任何条目；这里再显式早退，省掉一次哈希查表与建表，
        //   并让语义一目了然（见 register() 的注释）。
        //   置 false 即逐字回到原行为（一行回退）。
        if (!ENABLED || GTLModularConfig.isNanochipOriginalMode()
                || level == null || level.isClientSide || brokenPos == null) {
            return;
        }
        try {
            Map<Long, HostEntry> hosts = HOSTS.get(level.dimension());
            if (hosts == null || hosts.isEmpty()) {
                return;
            }
            long[] table = cells();
            if (table == null) {
                return;                                  // 建不出来 ⇒ 回到上游行为
            }
            int bx = brokenPos.getX();
            int by = brokenPos.getY();
            int bz = brokenPos.getZ();
            for (Map.Entry<Long, HostEntry> row : hosts.entrySet()) {
                HostEntry entry = row.getValue();
                long key = BlockPos.asLong(bx - entry.x, by - entry.y, bz - entry.z);
                if (Arrays.binarySearch(table, key) < 0) {
                    continue;                            // 这一格不属于这台主机的结构（任何朝向）
                }
                NanochipAssemblyComplexMachine host = entry.pointer.get();
                if (host == null) {
                    hosts.remove(row.getKey(), entry);   // 指针已被 GC 回收 ⇒ 顺手摘空壳
                    continue;
                }
                if (!POINTER_LOOKUP) {
                    // ★ 一行回退口径（0.3.121）：不信指针，回世界现取。
                    //   注意这里"取不到"只代表这一次不动作（可能只是区块没加载），**不**摘条目。
                    BlockPos hostPos = BlockPos.of(entry.packed);
                    if (MetaMachine.getMachine(level, hostPos) instanceof NanochipAssemblyComplexMachine world) {
                        host = world;
                    } else {
                        continue;
                    }
                }
                if (host.isInValid()) {
                    hosts.remove(row.getKey(), entry);   // ★ 校验②：方块实体已被移除/卸载
                    continue;
                }
                if (host.getPos().asLong() != entry.packed) {
                    hosts.remove(row.getKey(), entry);   // ★ 校验③：不是同一台机器
                    continue;
                }
                host.noteCachedBlockRemoved(brokenPos);  // 记账 ⇒ 那台主机下一 tick 让上游自己重判
            }
        } catch (Throwable t) {
            GTLModular.LOGGER.debug("[gtlmodular] NAC 结构位置查表异常（已忽略，结构判定不受影响）：{}",
                    t.toString());
        }
    }

    /**
     * <b>这座结构的区块是不是都加载着</b>（保守判据，用于"别把未加载当成结构坏了"）。
     *
     * <p>为什么需要它：上游的整图案匹配对未加载的格会置 {@code MultiblockState.UNLOAD_ERROR}
     * 并判失败（{@code MultiblockState.update} 的 {@code world.isLoaded}），而 gtlcore 的失效分支
     * 见到匹配失败就会 {@code onStructureInvalid()} ⇒ <b>那会是一次"不该发生的失效"</b>。
     * 本判据 = 先按结构水平包围盒（{@code dx, dz ∈ [-32, 32]}，见类注释的偏移范围）逐<b>区块列</b>
     * 采样 {@code level.isLoaded(...)}（最多 5×5 = 25 次），任一列没加载就返回 {@code false}
     * ⇒ 调用方保留待办、下一 tick 再试。
     *
     * <p>保守方向说明：采样点是"包围盒 ⊇ 真实格集合"的列，所以它比"真的会读到的格"更严 ——
     * 最坏只是把一次重判推迟几秒，绝不放行一次基于未加载状态的判定。
     */
    public static boolean structureChunksLoaded(Level level, BlockPos controllerPos) {
        if (level == null || controllerPos == null) {
            return false;
        }
        try {
            for (int dx = -LOADED_PROBE_RADIUS; dx <= LOADED_PROBE_RADIUS; dx += LOADED_PROBE_STEP) {
                for (int dz = -LOADED_PROBE_RADIUS; dz <= LOADED_PROBE_RADIUS; dz += LOADED_PROBE_STEP) {
                    if (!level.isLoaded(controllerPos.offset(dx, 0, dz))) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Throwable t) {
            return false;                                // 拿不准 ⇒ 当作"没全加载"（下一 tick 再试）
        }
    }
}
