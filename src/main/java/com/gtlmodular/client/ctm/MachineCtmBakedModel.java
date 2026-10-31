package com.gtlmodular.client.ctm;

import com.gtlmodular.GTLModular;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * <b>机器方块的「外壳面」CTM</b> —— 让真空仓 / 主机 / 11 个模块控制器的外壳与周围机壳连成一片
 * （NH 的 {@code connect=tile}）。
 *
 * <h2>为什么不能复用 {@link ConnectedCtmBakedModel}（逐字节复核过的两条硬事实）</h2>
 * GTCEu 机器的烘焙模型被 LDLib 的 {@code ModelBakeryMixin} 换成了
 * {@code ldlib:block/renderer_model}，其 baked 形态是 {@code LDLRendererModel$RendererBakedModel}。
 * 该 mixin 注入在 {@code ModelBakery.loadModel(ResourceLocation)} 的 HEAD（{@code @Inject(method="loadModel",
 * at=@At("HEAD"), cancellable=true)}），判据是
 * {@code BuiltInRegistries.BLOCK.get(new ResourceLocation(loc.getNamespace(), loc.getPath())) instanceof
 * IBlockRendererProvider} —— 也就是说<b>烘焙表里的键就是该机器方块状态的 {@code ModelResourceLocation}，
 * 其 {@code getPath()} 就是方块注册名</b>（所以按注册名认方块这条路对机器一样成立）。
 *
 * <pre>
 * javap -p -c …LDLRendererModel$RendererBakedModel：
 *   ① 3 参 getQuads：
 *        public List m_213637_(BlockState, Direction, RandomSource);
 *          Code:
 *             0: invokestatic  Collections.emptyList:()Ljava/util/List;
 *             3: areturn                                  ← ★ 恒返回空表
 *      ⇒ 既有的「预烘焙变体 + 3 参采样」路线（resolve() 要拿 6 个面的基准 quad）对机器<b>永远解析不出来</b>。
 *   ② getModelData：
 *        public ModelData getModelData(BlockAndTintGetter, BlockPos, BlockState, ModelData);
 *          Code:
 *            36: invokestatic  ModelData.builder:()LModelData$Builder;      ← ★ 新建 builder（丢掉传入的 data）
 *            39/47/54: with(IRENDERER/WORLD/POS, …)
 *            61: getstatic     MODEL_DATA
 *            64: aload 4      // 把**传入的 data** 塞进 MODEL_DATA 属性里
 *            69: Builder.build / 74: aload 4 / 76: areturn
 *      ⇒ 谁照抄这个形状（用 {@code ModelData.builder()} 起头）而<b>不</b>把 delegate 的结果接上，
 *        就会把 {@code IRENDERER/WORLD/POS} 丢掉 —— 此时 5 参 getQuads 里
 *        `data.get(IRENDERER) == null` ⇒ 直接 `Collections.emptyList()`（偏移 39-41 / 134-137）
 *        ⇒ <b>机器 quads 变空 = 机器隐形</b>（比"不连"糟得多）。
 * </pre>
 * ⇒ 本类因此走<b>渲染期替换 sprite</b>：5 参 {@code getQuads} 里拿到 delegate 的真 quads，
 * <b>只把「外壳那一张 sprite」</b>换成按邻居选出的变体，其余原样。
 *
 * <h2>四道安全防线（机器隐形是本项目最严重的回归）</h2>
 * <ol>
 *   <li><b>{@code getModelData} 是<b>纯转发</b></b>（2026-09-17 本轮独立复核后重写）：
 *       {@code return delegate.getModelData(level, pos, state, data);} —— <b>不 derive、不 catch、不换对象</b>。
 *       <pre>
 *       ① Forge 1.20.1 的区块网格化线程**就是**把本方法的返回值当渲染用的 ModelData：
 *          ChunkRenderDispatcher$RenderChunk$RebuildTask：305: getModelData(BlockPos)（= ModelDataManager 缓存的
 *          **方块实体**数据；普通机器 = ModelData.EMPTY）→ 308: BakedModel.getModelData(…, 它)
 *          → 451: renderBatched(…, **返回值**, renderType) ⇒ 返回值原样流进 5 参 getQuads（getRenderTypes 同）；
 *          Embeddium 0.3.31 同款：ChunkBuilderMeshingTask 312: getModelData(localSlice, pos, state,
 *          modelDataMap.getOrDefault(pos, EMPTY)) → 415: BlockRenderContext.update(…, **返回值**, …)。
 *       ② LDLib 的 RendererBakedModel.getModelData 才是**唯一**往数据里装 IRENDERER/WORLD/POS 的地方
 *          （它自己 ModelData.builder() 起头，并把传入 data 塞进 MODEL_DATA 属性）；传给我们的 data **不含**它们。
 *       ③ 5 参 getQuads 里 `data.get(IRENDERER) == null` ⇒ Collections.emptyList()（LDLib 偏移 39-41 / 134-137）。
 *       ⇒ 任何"拿不到就 return 传入的 data"分支 = 返回 **ModelData.EMPTY** = 没有 IRENDERER
 *         ⇒ delegate 逐面空表 ⇒ **机器隐形**（与旧注释写的"宁可'不连'"恰好相反：那条才是隐形路径）。
 *       ⇒ 所以本方法一个分支都不留：delegate 给什么就返回什么。</pre></li>
 *   <li><b>渲染期从 5 参 {@code getQuads} 的 {@code data} 里取世界 + 坐标</b>（不再自建 ModelProperty 槽位）：
 *       LDLib 装好的那份数据里 WORLD/POS 与 IRENDERER 在**同一个对象**里（它自己的 getQuads 也是从那里读出来
 *       交给 {@code IRenderer.renderModel} 的）。取法 = 按**类型**遍历 {@code data.getProperties()}
 *       （值 instanceof {@code BlockAndTintGetter} / {@code BlockPos} 各一个）⇒ 零 LDLib 编译期耦合，
 *       也不怕它改字段名。拿不到 ⇒ 一个 quad 都不改（物品栏 / JEI / 预览 / 粒子路径）。
 *       <p>不需要第二套槽位也不会"少算"：GTCEu {@code MachineRenderer.renderModel} 在
 *       {@code getMachine(level, pos) == null} 时同样返回空表（偏移 49: getMachine / 56: ifnull 416 /
 *       416: emptyList）⇒ <b>delegate 给非空 quads 必然经过了真实的 (level,pos)</b>。</li>
 *   <li><b>delegate 给空表 / null ⇒ 原样返回（"不恶化"，<u>不是</u>"回退未连接贴图"）+ 限流 WARN</b>：
 *       机器这条**没有第二份几何来源**（3 参 delegate 恒返回 {@code Collections.emptyList()}），
 *       所以能拿到的"未改写的原 quads"就是 delegate 这一份 ⇒ 原样转发；
 *       <b>我们自己绝不构造空表</b>（delegate 给 null 就连 null 一起照传，交给调用方的原有约定）。
 *       "我们主动放弃"那几条路（取不到世界/坐标、47 格变体未就绪、面内帧推不出、缺 tier 机壳图）
 *       各有**可区分的限流 WARN**，且一律返回未改写的原 quads。</li>
 *   <li><b>{@code getQuads} 兜底 try/catch</b>：CTM 自己的任何异常（帧反推 / 掩码 / 重贴图）都被吞掉并
 *       <b>原样返回 delegate 的 quads</b> + 限流 WARN —— 异常绝不从区块网格化线程抛出去
 *       （抛出去最坏是整个区块不渲染）。delegate 自己抛的异常**不**吞（那是它的行为，包装层不改写）。</li>
 * </ol>
 *
 * <h2>只换「外壳那一张」</h2>
 * 判据 = {@code quad.getSprite()} 的<b>路径</b>等于 {@link CtmHullGroup#MESH_BASE_SPRITE}
 * （{@code block/casings/nanochip_mesh_interface_casing}）。正脸 overlay
 * （{@code block/multiblock/nanochip_assembly_complex} 系列、真空仓两层前脸）、发光 / bloom /
 * 管道叠加条<b>不是这张 sprite</b> ⇒ 天然不会被替换（{@code _census/check_ctm_machine.js} 有断言盯着）。
 *
 * <h2>真空仓两态（NH 口径）</h2>
 * 未并入任何成形结构 ⇒ 外壳 = GTCEu 的 tier 机壳（{@code gtceu:block/casings/voltage/<tier>/*}，
 * 见 {@link CtmHullGroup#tierCasingSprite}），且<b>不</b>参与 CTM；
 * 已并入成形结构 ⇒ 外壳 = 纳米芯片接口机壳 + 参与 CTM（本类主线）。
 * 主机 / 11 个模块控制器<b>恒为</b>接口机壳（NH 由结构定义决定，没有第二态）。
 *
 * <h2>★ 2026-09-18 扩展：白名单之外 ——「任何进了 NAC 的仓」也换外壳（NH 的等价落地）</h2>
 * NH 在结构成形时对<b>任意</b> {@code MTEHatch} 调 {@code updateTexture(CASING_INDEX_WHITE)}
 * （{@code MTENanochipAssemblyModuleBase#ignoreAndAcceptHatch} / {@code addConveyorToMachineList}）
 * ⇒ NH 里别的 mod 的仓（GTMThings 的无线能源仓、GTCEu 自己的能源仓/总线…）一进 NAC，外壳同样变成
 * 纳米芯片接口机壳 ⇒ 同样与周围机壳连片（玩家已在实机确认）。
 *
 * <p>本轮把包装范围从"我们自己的 14 台白名单"扩到 <b>任何 {@code IMachineBlock}</b>
 * （{@code MetaMachineBlock implements IMachineBlock}，javap 确认 —— 与既有的
 * {@code IBlockRendererProvider} 判据同一条链），渲染期按<b>两条条件</b>决定要不要换
 * （见 {@link #retexture}）：
 * <pre>
 *   条件一（谁）：{@link NacSlotIndex#isNacSlot}(level, pos) —— 这一格是某台<b>成形 NAC</b> 的仓位；
 *                 ★ 拿不到索引 ⇒ false ⇒ 一个字节都不改（外观 = 现状，绝不猜、绝不抛）。
 *   条件二（哪张）：该 quad 的 sprite 路径属于 GTCEu 的 <b>tier 机壳族</b>
 *                 （{@code gtceu:block/casings/voltage/<tier>/<side|top|bottom>}，
 *                  判据 = {@link CtmHullGroup#isTierCasingSpritePath}，字节码取证写在它那里）——
 *                 别的 mod 的仓走的是 GTCEu 的 {@code TieredHullMachineRenderer}，同属这一族。
 *   两条都满足 ⇒ <b>只把这些 quad</b> 的 sprite 换成接口机壳的 47 格变体（overlay / 发光层 /
 *   bloom / 管道叠加条一律不碰）；否则原样返回 delegate 的结果。
 * </pre>
 * <p>★ 我们自己的 14 台<b>行为不变</b>：它们的外壳本来就是接口机壳那张
 * （{@link #isMeshCasingTile}），走上面"基准 sprite 匹配"那条路，压根不进 tier 分支。
 * <p>★ 白名单因此<b>只</b>承担"这 14 台走老路"这一个职责（{@code CtmHullGroup.MACHINE_HULL_IDS}
 * 与自检 {@code check_ctm_machine.js} 的逐条核对照旧）；"谁参与 CTM"的判据已经换成
 * {@code IMachineBlock + 仓位索引 + tier 机壳族 sprite}（自检 {@code check_ctm_foreign.js}）。
 *
 * <p>★ <b>已知限制（单侧连接 / 半条缝）</b>：连通判据（{@link CtmHullGroup#rendersMeshCasingTile}）是
 * "这一格是成形 NAC 的仓位"，而换贴图还要求"该 quad 属 GTCEu 的 tier 机壳族" ⇒ 若某台仓的外壳
 * <b>不走</b> {@code TieredHullMachineRenderer}（自绘别的机壳贴图），会出现"邻居连它、它不换"的半条缝。
 * 实机验收请<b>抽查一台自绘外壳的仓</b>；详见 {@link CtmHullGroup} 类注释的"已知限制"一节。
 */
public final class MachineCtmBakedModel implements BakedModel {

    /**
     * 世界 / 坐标**不再**自建 {@code ModelProperty} 槽位（2026-09-17 本轮复核后删除）。
     *
     * <p>原因：纯转发的 {@code getModelData} 已经没有地方"追加"槽位了；而渲染期真正需要的那份数据
     * （LDLib 的 IRENDERER/WORLD/POS）本来就在 5 参 {@code getQuads} 的 {@code data} 里。
     * 取用见 {@link #retexture}：按<b>值类型</b>遍历 {@code data.getProperties()} ⇒ 与 LDLib 零编译期耦合。
     */

    private static final int FACE_COUNT = 6;
    private static final int FACE_MASK = 0xFF;
    /** {@code Direction.values()} 的缓存（它每次调用都会 clone 一个数组；这里同时用作 ordinal 的合法域）。 */
    private static final Direction[] DIRECTIONS = Direction.values();
    /** 正交 4 位在 position 位序里的顺序（TOP / RIGHT / BOTTOM / LEFT）。 */
    private static final int[] RING_ORTHO = { 0, 2, 4, 6 };
    /** 对角 4 位（TOP_RIGHT / BOTTOM_RIGHT / BOTTOM_LEFT / TOP_LEFT）。 */
    private static final int[] RING_DIAG = { 1, 3, 5, 7 };

    /**
     * 面内坐标帧的**打包**编码：{@code (rightOrdinal + 1) << 8 | (upOrdinal + 1)}；{@code 0} = 未写。
     *
     * <p>为什么打包成<b>一个</b> int（2026-09-17 本轮复核修掉的竞态）：旧实现把一对面内方向分<b>两步</b>
     * 写进 {@code int[12]}（{@code frame[slot] = d0; frame[slot+1] = d1;}），而读方一见
     * {@code frame[slot] != NO_DIR} 就返回 ⇒ 可能读到"右方向已写、上方向还是 -1"的半写状态
     * ⇒ {@code Direction.values()[-1]} ⇒ {@code ArrayIndexOutOfBoundsException} 从<b>区块网格化线程</b>
     * 抛出（最坏整个区块不渲染）。现在两块信息一起编码、用一次
     * {@link java.util.concurrent.atomic.AtomicIntegerArray#set} 发布 ⇒ 读方只能看到
     * "全没写"(0) 或"一对都写好"两种状态，半写状态<b>不可能</b>被观察到（且元素读写是 volatile 语义）。
     */
    private static final int FRAME_UNSET = 0;

    /** 把一对面内方向编码成一个 int（ordinal 合法域 0..5，编码后非 0 ⇒ 与 {@link #FRAME_UNSET} 不冲突）。 */
    private static int encodeFrame(int rightOrdinal, int upOrdinal) {
        return ((rightOrdinal + 1) << 8) | (upOrdinal + 1);
    }

    /** ordinal 是否落在 {@code Direction} 的合法域内（解码 / 索引前的唯一守卫）。 */
    private static boolean validOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < FACE_COUNT;
    }

    /** 解码出世界方向；编码损坏（越界）时返回 {@code null} ⇒ 调用方放弃本次 CTM（不索引、不抛）。 */
    private static Direction directionOf(int encoded) {
        int ordinal = encoded - 1;
        return validOrdinal(ordinal) ? DIRECTIONS[ordinal] : null;
    }

    private final BakedModel delegate;
    private final Block block;
    /** 该方块是不是两态真空仓（只有它们的外壳随结构状态变）。 */
    private final boolean vacuumHatch;

    /** 47 张变体 sprite（下标 = {@code CtmConnectivity.ctmIndex(mask)}）；解析完成前为 null。 */
    private volatile TextureAtlasSprite[] sprites;
    /** 已并入成形结构时的外壳贴图 = 这 47 张里的某一张。 */
    private volatile TextureAtlasSprite hullSide;
    private volatile TextureAtlasSprite hullTop;
    private volatile TextureAtlasSprite hullBottom;

    /**
     * 「已并入态」的帧缓存：{@code BlockState → AtomicIntegerArray[6]}，元素 = {@link #encodeFrame} 的打包值
     * （{@code 0} = 该面还没推出来）。
     *
     * <p>为什么要按 state 缓存：机器的模型随 {@code frontFacing} 旋转（{@code ModelFactory.getRotation}），
     * 同一个包装实例可能服务多个状态 ⇒ 帧必须逐状态推（从该状态真实吐出的那个外壳 quad 反推）。
     * 状态数有限（一台机器十几种），不会无限增长。
     *
     * <p>为什么是 {@link java.util.concurrent.atomic.AtomicIntegerArray} 而不是 {@code int[12]}：
     * 见 {@link #FRAME_UNSET} 的注释（一次写发布一整对面内方向，消除"半写"窗口 + AIOOBE）。
     */
    private final ConcurrentHashMap<BlockState, java.util.concurrent.atomic.AtomicIntegerArray> frameCache =
            new ConcurrentHashMap<>();

    private volatile boolean resolved;
    private volatile int missed;
    private volatile int hullMissed;
    /**
     * 懒解析钩子（由 {@code CtmClientSetup} 注入）：渲染线程发现还没就绪时经它再试一次。
     * 与 {@link ConnectedCtmBakedModel#lazyResolver} 同一个用途，但这里用 {@link Runnable}
     * （机器这条不需要把"自己"传回去，避免上游改成用参数时出现 null 解引用）。
     */
    static volatile Runnable lazyResolver;

    /** 懒解析（图集晚就绪 / 事件没送到时的兜底）：与 {@link ConnectedCtmBakedModel} 同款节流。 */
    private volatile int lazyAttempts;
    private volatile long lastLazyAttemptAt;
    private static final int MAX_LAZY_ATTEMPTS = 20;
    /** 诊断打印额度（每个包装实例一份，避免某一条把额度用光）。 */
    private final java.util.concurrent.atomic.AtomicInteger emptyQuadsWarn = new java.util.concurrent.atomic.AtomicInteger(2);
    /** ★ 2026-09-18：空表 WARN 的**第二层**（全局）限流 —— 见 {@link #allowEmptyQuadsWarn()}。 */
    private static final int EMPTY_QUADS_TOTAL_CAP = 12;
    private static final int EMPTY_QUADS_LOGGED_CAP = 64;
    private static final java.util.concurrent.atomic.AtomicInteger EMPTY_QUADS_TOTAL =
            new java.util.concurrent.atomic.AtomicInteger(EMPTY_QUADS_TOTAL_CAP);
    /** 已打过空表 WARN 的方块注册名（资源重载边界由 {@link #invalidateSharedVariants()} 清空）。 */
    private static final java.util.Set<String> EMPTY_QUADS_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicInteger noFrameWarn = new java.util.concurrent.atomic.AtomicInteger(2);
    private final java.util.concurrent.atomic.AtomicInteger hullMissingWarn = new java.util.concurrent.atomic.AtomicInteger(2);
    /** ★ 本轮新增：区分"delegate 空表"（上面那条）与"我们主动放弃"的三条路（各 1 条额度，长文本不刷屏）。 */
    private final java.util.concurrent.atomic.AtomicInteger noWorldPosWarn = new java.util.concurrent.atomic.AtomicInteger(1);
    private final java.util.concurrent.atomic.AtomicInteger variantsNotReadyWarn = new java.util.concurrent.atomic.AtomicInteger(1);
    private final java.util.concurrent.atomic.AtomicInteger ctmFailedWarn = new java.util.concurrent.atomic.AtomicInteger(1);

    // ------------------------------------------------------------------
    // ★ 2026-09-18 新增：47 格变体全局共享 + 外来机器的包装范围（硬上限 + (delegate,方块) 复用）
    // ------------------------------------------------------------------

    /**
     * ★ <b>47 张变体 sprite 全局共享</b>（所有机器方块共用同一套）。
     *
     * <p>为什么改成共享：包装范围扩到"任何 {@code IMachineBlock}"之后，同一批变体要被上千个包装实例
     * 使用；每个实例各解析 47 张 = 几万次图集查找 + 几万个 {@code ResourceLocation} 分配（每次资源
     * 重载都要来一遍）。47 张变体与方块无关 ⇒ 解析一次、全局复用。
     *
     * <p>发布语义：数组<b>填满之后</b>才写这个 volatile 引用 ⇒ 读方要么拿到 null、要么拿到完整数组
     * （两个线程同时解析只会各建一份、后写的赢，结果等价；不存在"半填数组"被观察到）。
     */
    private static volatile TextureAtlasSprite[] sharedVariants;
    /** 共享数组缺了几张（0 = 齐；负 = 还没解析过）。 */
    private static volatile int sharedMissed = -1;
    /** 共享数组的重试节流（图集没就绪时别被上千个实例反复重算）。 */
    private static volatile long sharedAttemptAt;
    private static final long SHARED_RETRY_MILLIS = 250L;

    /**
     * ★ <b>外来机器包装实例的硬上限</b>：本轮"任何 {@code IMachineBlock}"的包装实例数超过它 ⇒
     * <b>回退</b>成"只包装白名单那 14 台"+ WARN（见 {@code CtmClientSetup.planForeignHulls}）。
     *
     * <p>为什么需要它：{@code MACHINES} 是 {@code CopyOnWriteArrayList}（add 是 O(n)），
     * 条目数一爆炸就变成 O(n²)；存疑的整合包宁可"退回本模组那 14 台"也不要卡在资源重载上。
     * 实测参照：本模组 14 台机器方块在玩家的整合包里是 <b>598 条状态模型条目</b>（≈43 条/方块），
     * 所以（delegate,方块）复用之后的实例数才是有意义的计数 —— 见 {@link #REUSE}。
     */
    public static final int MAX_FOREIGN_WRAPPERS = 3000;

    /**
     * 「(delegate 实例, 方块) → 包装实例」<b>复用表</b>：同一个方块的几十条状态模型条目通常共用同一个
     * 烘焙模型（LDLib 的 {@code ModelBakeryMixin} 把所有机器模型的 {@code loadModel} 都换
     * {@code computeIfAbsent} 出来的同一份 {@code ldlib:block/renderer_model}），逐条新建包装实例
     * 既费内存、又让 {@code MACHINES} 的 add 变成 O(n²)。
     *
     * <p>安全前提：<b>只有 delegate 与方块都相同才复用</b>（键 = 方块 + {@link IdentityHashMap} 的
     * delegate）⇒ 复用同一个包装实例时渲染行为逐字节等价。{@code frameCache} 本来就是按
     * {@code BlockState} 分槽的，天生支持"一个实例服务多个状态"（见它自己的注释）。
     *
     * <p>值 {@code null} = "计划期已计数、实例还没建"（见 {@code CtmClientSetup.planForeignHulls}）。
     * 只在烘焙线程访问；资源重载开始时由 {@link #clearReuse()} 清空。
     */
    private static final Map<Block, IdentityHashMap<BakedModel, MachineCtmBakedModel>> REUSE = new HashMap<>();

    /** 资源重载边界：清掉共享变体缓存（图集换了 ⇒ 旧 sprite 全部作废）。 */
    public static void invalidateSharedVariants() {
        sharedVariants = null;
        sharedMissed = -1;
        sharedAttemptAt = 0L;
        // ★ 2026-09-18：空表 WARN 的两层限流账也一起复位（新一轮资源要能重新报一遍，
        //   否则"关掉再打开资源包"之后永远看不到诊断）。
        EMPTY_QUADS_TOTAL.set(EMPTY_QUADS_TOTAL_CAP);
        EMPTY_QUADS_LOGGED.clear();
    }

    /** 资源重载边界：清掉复用表（上一轮的包装实例全部作废）。 */
    public static void clearReuse() {
        REUSE.clear();
    }

    /** 已解析的共享变体（诊断 / 自检 / 渲染期懒取用）。{@code null} = 还没就绪。 */
    static TextureAtlasSprite[] sharedVariants() {
        return sharedVariants;
    }

    /**
     * ★ 2026-09-18 新增：47 格变体是不是<b>全部命中</b>图集（不是"靠第 0 格兜底"）。
     *
     * <p>为什么要问这个：{@link #resolveSharedVariants} 在缺图时会用<b>第 0 格兜底</b>
     * （缺的那几格先画孤立变体）。对逐帧选格的机壳 / 机器面那条路，"下一帧重解析"就能自愈；
     * 但外壳罩（{@code NacMachineShellRenderer}）会把 6×47 个 quad <b>一次展开并锁死</b>
     * （里面嵌的是 sprite 引用），一旦在图集没齐时锁死，那一整局都会画着兜底的第 0 格。
     * ⇒ 它用本方法做闸门：**没全命中就不锁**，节流重试到齐为止（拿不到 ⇒ 本帧不画罩子，外观 = 现状）。
     *
     * @return {@code true} = 47 张全部真实命中（{@code false} = 还没解析过 / 有缺格 / 靠兜底）
     */
    public static boolean sharedVariantsComplete() {
        return sharedVariants != null && sharedMissed == 0;
    }

    /**
     * 计划期记账：把「(delegate, 方块)」登记进复用表（值 = null，表示"已计数、实例还没建"）。
     *
     * @return {@code true} = 这一对是<b>新</b>的（计划里要新建一个包装实例）
     */
    static boolean planOnce(BakedModel delegate, Block block) {
        IdentityHashMap<BakedModel, MachineCtmBakedModel> byDelegate =
                REUSE.computeIfAbsent(block, ignored -> new IdentityHashMap<>());
        if (byDelegate.containsKey(delegate)) {
            return false;
        }
        byDelegate.put(delegate, null);
        return true;
    }

    /** 取已建好的复用实例（{@code null} = 还没建 ⇒ 调用方新建一个并 {@link #bindReuse}）。 */
    static MachineCtmBakedModel reuseOf(BakedModel delegate, Block block) {
        IdentityHashMap<BakedModel, MachineCtmBakedModel> byDelegate = REUSE.get(block);
        return byDelegate == null ? null : byDelegate.get(delegate);
    }

    /** 把新建的包装实例绑进复用表（同一 (delegate, 方块) 的后续条目直接复用它）。 */
    static void bindReuse(BakedModel delegate, Block block, MachineCtmBakedModel wrapper) {
        REUSE.computeIfAbsent(block, ignored -> new IdentityHashMap<>()).put(delegate, wrapper);
    }

    public MachineCtmBakedModel(BakedModel delegate, Block block) {
        this.delegate = delegate;
        this.block = block;
        this.vacuumHatch = CtmHullGroup.isVacuumHatch(block);
    }

    /** 供自检 / 日志用。 */
    public Block block() {
        return this.block;
    }

    public TextureAtlasSprite[] variantSprites() {
        return this.sprites;
    }

    /** 是否全部 47 张变体都已就绪。 */
    public boolean fullyResolved() {
        return this.resolved;
    }

    /** 最近一次解析缺了几张变体 sprite。 */
    public int missedSprites() {
        return this.missed;
    }

    /** 两态真空仓的 tier 机壳外壳缺了几张（0 = 齐）。 */
    public int missedHullSprites() {
        return this.hullMissed;
    }

    /** 日志用的一句话。 */
    public String summary() {
        return CtmHullGroup.MESH_SPRITE_PREFIX + " × " + CtmConnectivity.CTM_TILES
                + (this.resolved ? "（已就绪）" : "（缺 " + this.missed + " 张）")
                + (this.vacuumHatch ? "｜两态：未并入 ⇒ " + CtmHullGroup.tierCasingSprite(
                        CtmHullGroup.hatchTier(), "side") + "（缺 " + this.hullMissed + "）" : "");
    }

    // ------------------------------------------------------------------
    // ① ModelData：**纯转发**（不 derive / 不 catch / 不换对象）
    // ------------------------------------------------------------------

    /**
     * ★★ 守卫 1（机器隐形防线）—— <b>纯转发</b>，一个分支都不留。
     *
     * <p>为什么连 {@code try/catch} 都不留（2026-09-17 本轮独立复核的结论）：
     * 本方法的返回值<b>就是</b>渲染时喂给 5 参 {@code getQuads} 的那份 ModelData
     * （Forge 的 RebuildTask 305→308→451、Embeddium 的 ChunkBuilderMeshingTask 312→415，见类注释）。
     * 而往数据里装 {@code IRENDERER/WORLD/POS} 的**唯一**地方就是 delegate 自己的 getModelData
     * （它 {@code ModelData.builder()} 起头；传给我们的 {@code data} 里只有方块实体数据，普通机器 = EMPTY）。
     * ⇒ 旧代码那两条兜底（{@code catch → return data;} / {@code fromDelegate == null → return data;}）
     * 返回的是**不含 IRENDERER 的 data** ⇒ LDLib 的 5 参 getQuads 走 {@code Collections.emptyList()}
     * ⇒ <b>逐面空表 = 机器隐形</b>。所以这里不吞异常、不派生、不换对象：delegate 给什么就返回什么。
     *
     * <p>（世界 / 坐标不再由本方法追加：渲染期直接从同一份数据里按类型取 —— 见 {@link #retexture}。）
     */
    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        return this.delegate.getModelData(level, pos, state, data);
    }

    // ------------------------------------------------------------------
    // ② 渲染期：只换「外壳那一张 sprite」
    // ------------------------------------------------------------------

    /**
     * 5 参 {@code getQuads}：先把 delegate 的结果拿到手，再（在**兜底 try/catch** 里）决定要不要重贴图。
     *
     * <p>★ 结构上刻意分两段（本轮复核后重排）：
     * <ol>
     *   <li>delegate 的调用<b>在 try 之外</b>：包装层绝不改写 delegate 自己的行为（它抛就抛，与没有本包装时一致）；
     *       也正因为如此，"异常时原样返回 delegate 的 quads" 才总是有东西可返回 —— 见第 2 段。</li>
     *   <li>我们自己的全部逻辑（取世界/坐标 → 找外壳 quad → 反推面内帧 → 邻居掩码 → 重贴图）包在
     *       {@code try { … } catch (Throwable) { 限流 WARN; return quads; }} 里 ⇒ CTM 的任何 bug
     *       都只会退化成"原样外观"，<b>绝不从区块网格化线程抛异常</b>（抛出最坏整个区块不渲染）。</li>
     * </ol>
     */
    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
        List<BakedQuad> quads = this.delegate.getQuads(state, side, rand, data, renderType);
        try {
            return retexture(state, side, data, quads);
        } catch (Throwable failure) {
            // 兜底：CTM 自己出错 ⇒ 原样返回 delegate 的结果（可能是空表 —— 但也**只**可能是 delegate 给的）
            warnCtmFailed(state, side, failure);
            return quads;
        }
    }

    /**
     * 决定这一面要不要换 sprite。**每一条放弃路径都原样返回入参 {@code quads}**（= delegate 的结果），
     * 且各自有可区分的限流 WARN（"delegate 空表" vs "我们主动放弃"，见 {@code warn*} 各方法）。
     *
     * <p>★ 语义正名（本轮复核修正注释）：下面第一条守卫是<b>"不恶化"</b>——原样把 delegate 的结果转出去，
     * <b>不是</b>"回退未连接贴图"。机器这条<b>没有第二份几何来源</b>：delegate 的 3 参
     * {@code getQuads} 恒返回 {@code Collections.emptyList()}（LDLib 字节码 0: invokestatic emptyList / 3: areturn），
     * 所以"未改写的原 quads"就是 delegate 这一份 —— 能做的和必须做的都是"一个字节都不改"。
     * 真正"回退未连接贴图"的动作发生在<b>非空</b>路径（下面每一条 {@code return quads;} 都是它）。
     */
    private List<BakedQuad> retexture(BlockState state, Direction side, ModelData data, List<BakedQuad> quads) {
        // ★★ 守卫 2（机器隐形防线 / "不恶化"）：delegate 给 null 或空表 ⇒ 原样转出。
        //    ★ 这里**不再** `return quads == null ? Collections.emptyList() : quads;` —— 那等于我们自己
        //      造了一张空表（复核指出的语义错误）；delegate 给 null 就照传 null，责任边界与不包装时完全一致。
        if (quads == null || quads.isEmpty()) {
            warnEmptyQuads(state, side);
            return quads;
        }
        if (side == null || data == null) {
            return quads; // 物品栏 / 粒子：原样
        }
        // ★ 世界 + 坐标：按值类型从**同一份 data** 里取（LDLib 装的就是这两个值；零编译期耦合）。
        //   取不到 ⇒ 原样返回（物品栏 / JEI / 结构预览 / 别的渲染路径）。
        BlockAndTintGetter level = null;
        BlockPos pos = null;
        for (ModelProperty<?> property : data.getProperties()) {
            Object value = data.get(property);
            if (level == null && value instanceof BlockAndTintGetter foundLevel) {
                level = foundLevel;
            } else if (pos == null && value instanceof BlockPos foundPos) {
                pos = foundPos;
            }
        }
        if (level == null || pos == null) {
            warnNoWorldPos(state, side, data);
            return quads;
        }
        // 找「外壳 quad」。两条出路，互相排斥：
        //   ① 我们自己的 14 台 = 接口机壳那一张 sprite（老路，行为不变）；
        //   ② ★ 别的 mod / GTCEu 自己的仓 = GTCEu 的 tier 机壳族 —— **只有"本格是成形 NAC 的仓位"
        //      时才接手**（条件一）；拿不到索引就一个字节都不改。
        // ★ 第 ① 条**不打 WARN**：一台机器有多层（外壳层 + 正脸 overlay 层 + 发光层），
        //   其余层落到这里返回是**正常**的，不是"放弃"。
        BakedQuad hull = null;
        for (BakedQuad q : quads) {
            if (isMeshCasingTile(q.getSprite())) {
                hull = q;
                break;
            }
        }
        boolean foreignHull = false;
        if (hull == null) {
            BakedQuad tierQuad = null;
            for (BakedQuad q : quads) {
                if (isTierCasingTile(q.getSprite())) {
                    tierQuad = q;
                    break;
                }
            }
            if (tierQuad == null) {
                return quads; // 这一层没有外壳面（overlay / 发光 / bloom / 管道叠加条…）⇒ 原样
            }
            if (!NacSlotIndex.isNacSlot(level, pos)) {
                // ★ 条件一不满足：不是任何成形 NAC 的仓位（或拿不到索引）⇒ 原样（= 现状外观）。
                return quads;
            }
            hull = tierQuad;
            foreignHull = true;
        }
        // 这一面选哪张 sprite
        TextureAtlasSprite target = pickSprite(level, pos, side, state, hull);
        if (target == null) {
            return quads; // 解析不出来 / 帧未知 / 缺 tier 机壳图 ⇒ 原样（= 现状外观，绝不变空；各自的 WARN 在 pickSprite 里）
        }
        // ★ 只替换"外壳那一张"：其余 quad（正脸 overlay / 发光 / bloom / 管道叠加条）原样搬过去。
        List<BakedQuad> out = new ArrayList<>(quads.size());
        boolean changed = false;
        for (BakedQuad q : quads) {
            if (isMeshCasingTile(q.getSprite()) && q.getSprite() != target) {
                out.add(ConnectedCtmBakedModel.retexture(q, target));
                changed = true;
            } else if (foreignHull && isTierCasingTile(q.getSprite()) && q.getSprite() != target) {
                // ★ 新增分支：外来机器（tier 机壳族）的外壳面 —— overlay / 发光层不是这一族 ⇒ 天然不进来
                out.add(ConnectedCtmBakedModel.retexture(q, target));
                changed = true;
            } else {
                out.add(q);
            }
        }
        return changed ? out : quads;
    }

    /** 3 参重载：照抄 delegate 的行为（RendererBakedModel 这里恒为空表）。 */
    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
        return this.delegate.getQuads(state, side, rand);
    }

    /** 「这一张 sprite 就是外壳那张」的判据（只比路径，见 {@link CtmHullGroup#MESH_BASE_SPRITE}）。 */
    static boolean isMeshCasingTile(TextureAtlasSprite sprite) {
        return sprite != null
                && CtmHullGroup.MESH_BASE_SPRITE.equals(sprite.contents().name().getPath());
    }

    /**
     * ★ <b>这一张 sprite 属于 GTCEu 的 tier 机壳族</b>（{@code block/casings/voltage/<tier>/<face>}）。
     *
     * <p>用途（本轮新增）：白名单之外<b>任何机器方块</b>（别的 mod 的仓 / GTCEu 自己的仓）的外壳面画的
     * 就是这一族 ⇒ 只有"本格是成形 NAC 的仓位"时才把它换成接口机壳的 47 格变体。
     *
     * <p>★ 只认 {@link CtmHullGroup#TIER_CASING_TEXTURE_PREFIX} 之下、面名 ∈ {side,top,bottom} 的路径：
     * 放宽成"任何 sprite"会把正脸 overlay / 发光层 / bloom / 管道叠加条一起换掉。
     * 判据本体在 CtmHullGroup（它拥有那个前缀常量），这里只是按 sprite 路径转发。
     */
    static boolean isTierCasingTile(TextureAtlasSprite sprite) {
        return sprite != null
                && CtmHullGroup.isTierCasingSpritePath(sprite.contents().name().getPath());
    }

    /**
     * 这一面该画哪张 sprite（{@code null} = 不改，原样返回 delegate 的结果）。
     *
     * <pre>
     *   真空仓 && 未并入成形结构  → GTCEu tier 机壳（up→top / down→bottom / 其它→side），**不参与 CTM**
     *   其余（含已并入的仓 / 控制器）→ 47 格变体里的第 ctmIndex(mask) 张（NH 的 connect=tile）
     * </pre>
     */
    private TextureAtlasSprite pickSprite(BlockAndTintGetter level, BlockPos pos, Direction side, BlockState state,
                                          BakedQuad hullQuad) {
        if (!this.resolved) {
            maybeResolveLazily();
        }
        if (this.vacuumHatch && !CtmHullGroup.isCasingFromStructure(level, pos)) {
            // ① 未并入成形结构：NH = MACHINE_CASINGS[tier][0]（这里按玩家裁决直接引用 GTCEu 的 tier 机壳）。
            TextureAtlasSprite hullSprite = hullSprite(side);
            if (hullSprite == null) {
                warnHullMissing();
            }
            return hullSprite;
        }
        // ② 接口机壳 47 格：按 connect=tile 邻居掩码选变体
        TextureAtlasSprite[] variants = this.sprites;
        if (variants == null) {
            // ★ 2026-09-18：47 张变体是**全局共享**的 ⇒ 没轮到本实例 resolve 时（例如条目数超过
            //   MACHINES 记账范围的懒解析路径），直接取共享缓存即可（已就绪就返回，否则 null）。
            variants = sharedVariants();
        }
        if (variants == null) {
            warnVariantsNotReady(state, side); // ★ 主动放弃（可区分）：47 格还没解析出来 ⇒ 保持原贴图
            return null;
        }
        int frame = ensureFrame(state, side, hullQuad);
        if (frame == FRAME_UNSET) {
            return null; // 帧推不出（warnNoFrame 已在 ensureFrame 里打过）
        }
        int mask = computeMask(level, pos, side, frame);
        if (mask < 0) {
            return null; // 帧解码不出合法世界方向（防御性：编码损坏）⇒ 原样
        }
        TextureAtlasSprite sprite = variants[CtmConnectivity.ctmIndex(mask)];
        return sprite != null ? sprite : null;
    }

    private TextureAtlasSprite hullSprite(Direction side) {
        if (side == Direction.UP) {
            return this.hullTop;
        }
        if (side == Direction.DOWN) {
            return this.hullBottom;
        }
        return this.hullSide;
    }

    /**
     * 反推 / 取缓存「该状态下这一面的面内坐标帧」（贴图右 / 上分别对应哪个世界方向）。
     *
     * <p>从<b>该状态真实吐出的外壳 quad</b>推（{@link CtmFaceFrame} 同一套数学）：机器模型会按
     * {@code frontFacing} 旋转，写死 6 面表必然把接缝画到错的边上。
     *
     * <p>★ 发布语义（本轮复核修掉的竞态）：一对面内方向被 {@link #encodeFrame} 打包成<b>一个</b> int，
     * 用 {@link java.util.concurrent.atomic.AtomicIntegerArray#set} <b>一次</b>写出去
     * ⇒ 读方只可能看到 {@link #FRAME_UNSET}（全没写）或"一对都写好"，**不可能**看到半写状态
     * （旧实现是 {@code frame[slot] = d0; frame[slot+1] = d1;} 两步写，读方一见 slot 就把
     * {@code frame[slot+1] == -1} 当 ordinal 用 ⇒ {@code Direction.values()[-1]} ⇒ AIOOBE 从网格化线程抛出）。
     *
     * @return 打包后的面内帧，或 {@link #FRAME_UNSET}（几何推不出来 ⇒ 本次不改；已限流 WARN）
     */
    private int ensureFrame(BlockState state, Direction side, BakedQuad hullQuad) {
        java.util.concurrent.atomic.AtomicIntegerArray frames =
                this.frameCache.computeIfAbsent(state, ignored -> new java.util.concurrent.atomic.AtomicIntegerArray(FACE_COUNT));
        int slot = side.ordinal();
        int packed = frames.get(slot);
        if (packed != FRAME_UNSET) {
            return packed; // 已推过（一整对都在里面）
        }
        int[] derived = CtmFaceFrame.deriveFace(hullQuad, side);
        if (derived == null || !validOrdinal(derived[0]) || !validOrdinal(derived[1])) {
            if (this.noFrameWarn.getAndDecrement() > 0) {
                GTLModular.LOGGER.warn("[gtlmodular] CTM: 机器 {} 的面 {} 推不出面内坐标帧（几何不满足判据）"
                        + "—— 该面退回原贴图（不连），不会变空。", this.block, side);
            }
            return FRAME_UNSET;
        }
        // ★ 一次性发布：右 + 上两个方向在这一个 int 里
        frames.set(slot, encodeFrame(derived[0], derived[1]));
        return frames.get(slot);
    }

    /**
     * 8 位邻居掩码：本面内 8 个邻居里，外壳贴图也是接口机壳的那些位。
     *
     * @return 掩码（0..255），或 {@code -1}（打包帧解不出合法世界方向 —— 防御性分支，调用方按"放弃"处理）
     */
    private int computeMask(BlockAndTintGetter level, BlockPos pos, Direction face, int packedFrame) {
        Direction rightDir = directionOf((packedFrame >> 8) & FACE_MASK);
        Direction upDir = directionOf(packedFrame & FACE_MASK);
        if (rightDir == null || upDir == null) {
            return -1; // ★ 绝不用非法 ordinal 索引 Direction.values()（AIOOBE 防线）
        }
        int mask = 0;
        for (int p : RING_ORTHO) {
            if (connects(level, pos, rightDir, upDir, p)) {
                mask |= 1 << p;
            }
        }
        // 47 格：正交签名一旦单值，对角不可能改变结果 ⇒ 省 4 次查询（与机壳那条同款剪枝）
        if (CtmConnectivity.singletonIndex(mask) >= 0) {
            return mask;
        }
        for (int p : RING_DIAG) {
            if (connects(level, pos, rightDir, upDir, p)) {
                mask |= 1 << p;
            }
        }
        return mask;
    }

    /**
     * position {@code p} 处的邻居是不是"外壳贴图同为接口机壳"的格子 —— <b>NH 的 {@code connect=tile}</b>。
     *
     * <p>注意与老实现（{@code state.is(this.block)} = 按方块身份）的区别：那时机器的掩码恒为 0
     * ⇒ 永远选第 0 格（孤立变体）⇒ 玩家看到的"机器面与周围机壳之间那条缝"。
     */
    private boolean connects(BlockAndTintGetter level, BlockPos pos, Direction rightDir, Direction upDir, int p) {
        int sr = CtmConnectivity.STEP_RIGHT[p];
        int su = CtmConnectivity.STEP_UP[p];
        int dx = sr * rightDir.getStepX() + su * upDir.getStepX();
        int dy = sr * rightDir.getStepY() + su * upDir.getStepY();
        int dz = sr * rightDir.getStepZ() + su * upDir.getStepZ();
        if (dx == 0 && dy == 0 && dz == 0) {
            return false;
        }
        BlockPos neighbour = pos.offset(dx, dy, dz);
        return CtmHullGroup.rendersMeshCasingTile(level, neighbour, level.getBlockState(neighbour));
    }

    // ------------------------------------------------------------------
    // ③ 解析：47 张变体 + （两态真空仓）3 张 tier 机壳外壳
    // ------------------------------------------------------------------

    /**
     * 解析 sprite（与 {@link ConnectedCtmBakedModel#resolve} 同款：没全命中就<b>不锁死</b>，留待重试）。
     *
     * <p>★ 2026-09-18：47 张变体改成<b>全局共享</b>（{@link #sharedVariants}）—— 包装范围扩到"任何
     * {@code IMachineBlock}"之后，同一套变体不必（也不该）被每个包装实例各解析一遍。本方法的语义
     * 与从前一致：把共享数组挂到本实例上、把缺失数记进 {@code missed}、全齐才算 {@code resolved}。
     *
     * @return 是否 47 张变体全齐
     */
    public boolean resolve(Function<ResourceLocation, TextureAtlasSprite> atlas) {
        if (this.resolved) {
            return true;
        }
        TextureAtlasSprite[] built = resolveSharedVariants(atlas);
        this.sprites = built;
        this.missed = sharedMissed;
        if (this.vacuumHatch) {
            int tier = CtmHullGroup.hatchTier();
            CtmHullGroup.warnIfTierCasingMissing(tier);
            TextureAtlasSprite[] hull = new TextureAtlasSprite[3];
            String[] faces = { "side", "top", "bottom" };
            int hullMissing = 0;
            for (int i = 0; i < faces.length; i++) {
                TextureAtlasSprite sprite = lookup(atlas, CtmHullGroup.tierCasingSprite(tier, faces[i]));
                if (ConnectedCtmBakedModel.isMissing(sprite)) {
                    hullMissing++;
                    sprite = null;
                }
                hull[i] = sprite;
            }
            this.hullSide = hull[0];
            this.hullTop = hull[1];
            this.hullBottom = hull[2];
            this.hullMissed = hullMissing;
        }
        if (this.missed == 0) {
            this.resolved = true;
            this.lazyAttempts = 0;
            return true;
        }
        return false;
    }

    /**
     * 解析（或复用）全局共享的 47 张变体。
     *
     * <p>缺图时的口径与旧实现逐字节一致：缺的那几格退回第 0 格（连第 0 格都没有就 null ⇒
     * 调用方 {@code pickSprite} 返回 null ⇒ 原样外观），并且 {@code missing > 0} 时不发布"已就绪"。
     *
     * <p>重试节流 {@link #SHARED_RETRY_MILLIS}：图集没就绪时，{@code resolveAll()} 会遍历几千个包装
     * 实例逐个调 {@code resolve} —— 没有节流就是"几千 × 47 次图集查找"每个事件轮一遍。
     *
     * <p>★ 2026-09-18 起<b>改为 public</b>："自己叠壳"那条路
     * （{@code com.gtlmodular.client.renderer.NacMachineShellRenderer}）用的是<b>同一套 47 格变体</b>；
     * 而本轮把 {@code MACHINE_HULL_CTM} 默认关掉之后 {@code MACHINES} 表是空的 ⇒ {@code resolveAll()}
     * 不会替它解析。让外壳罩直接调这里 ⇒ **一份实现、一份缓存**（资源重载时由
     * {@link #invalidateSharedVariants()} 作废，两条路一起重来）。
     */
    public static TextureAtlasSprite[] resolveSharedVariants(Function<ResourceLocation, TextureAtlasSprite> atlas) {
        TextureAtlasSprite[] cached = sharedVariants;
        long now = System.currentTimeMillis();
        if (cached != null && (sharedMissed == 0 || now - sharedAttemptAt < SHARED_RETRY_MILLIS)) {
            return cached;
        }
        sharedAttemptAt = now;
        TextureAtlasSprite[] built = new TextureAtlasSprite[CtmConnectivity.CTM_TILES];
        TextureAtlasSprite zero = null;
        int missing = 0;
        for (int v = 0; v < built.length; v++) {
            TextureAtlasSprite sprite = lookup(atlas, CtmHullGroup.MESH_SPRITE_PREFIX + "/" + v);
            if (ConnectedCtmBakedModel.isMissing(sprite)) {
                missing++;
                if (zero == null && v != 0) {
                    zero = lookup(atlas, CtmHullGroup.MESH_SPRITE_PREFIX + "/0");
                }
                sprite = (zero != null && !ConnectedCtmBakedModel.isMissing(zero)) ? zero : null;
            }
            built[v] = sprite;
        }
        sharedMissed = missing;
        sharedVariants = built; // ★ 数组填满后才发布（读方要么 null、要么完整）
        return built;
    }

    /** 先试本模组命名空间，再试 {@code minecraft}（与机壳 / 玻璃那两条同款两次查找）。 */
    private static TextureAtlasSprite lookup(Function<ResourceLocation, TextureAtlasSprite> atlas, String path) {
        if (path == null) {
            return null;
        }
        TextureAtlasSprite sprite = atlas.apply(new ResourceLocation(GTLModular.MOD_ID, path));
        if (!ConnectedCtmBakedModel.isMissing(sprite)) {
            return sprite;
        }
        return atlas.apply(new ResourceLocation("minecraft", path));
    }

    /** 渲染路径上的兜底重试（1 秒节流 + 次数上限，异常一律吞掉 —— 渲染绝不能因 CTM 崩）。 */
    private void maybeResolveLazily() {
        Runnable resolver = lazyResolver;
        if (resolver == null || this.lazyAttempts >= MAX_LAZY_ATTEMPTS) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.lastLazyAttemptAt < 1000L) {
            return;
        }
        this.lastLazyAttemptAt = now;
        this.lazyAttempts++;
        try {
            resolver.run();
        } catch (Throwable ignored) {
            // 故意吞掉：CTM 只是观感优化，绝不允许影响正常渲染
        }
    }

    /**
     * 情况 A（<b>delegate 空表</b>）：delegate 这次采样给的是 null / 空表。
     *
     * <p>与之相对的是下面 {@link #warnNoWorldPos} / {@link #warnVariantsNotReady} / {@link #warnCtmFailed}
     * 三条"<b>我们主动放弃</b>"——两类日志的措辞刻意不同，实机排查时一眼能分清是谁的问题。
     *
     * <h3>★ 2026-09-18 本轮改动：话术正名 + <b>两侧限流</b></h3>
     * <b>实机证据（20:26:07，玩家日志原文）</b>：<b>每一个</b>机器方块在区块渲染时 delegate 都返回
     * null/空 quads ⇒ 根因不是"这次没给 ModelData"，而是<b>机器根本不是用烘焙 quad 画的</b>：
     * GTCEu 机器走 LDLib 的 {@code IRenderer} 逐 tile 绘制，{@code RendererBakedModel} 的 3 参
     * {@code getQuads} 字节码就是 {@code Collections.emptyList(); areturn}（见类注释）。
     * ⇒ <b>本路径对机器已被证明无效</b>（守卫起效、机器没隐形，但什么也没换）。
     * 因此：① 措辞改成"已证明本路径对机器无效"，并指向新渲染器
     * {@code NacMachineShellRenderer}；② 这条 WARN 改成<b>双层限流</b>——
     * 既有的"每实例 2 条"额度（{@link #emptyQuadsWarn}）之外，再加一层<b>全局</b>闸门
     * （同一方块名一条 + 全局总数上限，见 {@link #allowEmptyQuadsWarn()}），
     * 因为实测"一屏几十条"来自一屏里的几十台不同机器（每台自己的额度都还没用完）。
     */
    private void warnEmptyQuads(BlockState state, Direction side) {
        if (this.emptyQuadsWarn.getAndDecrement() > 0 && allowEmptyQuadsWarn()) {
            GTLModular.LOGGER.warn("[gtlmodular] CTM: 【delegate 空表】机器 {} 的 delegate 返回了 null/空 quads"
                            + "（state={} face={}）—— 已**原样转发**。★ 2026-09-18 实机已证明：**本路径对机器无效**"
                            + "（机器由 LDLib 的 IRenderer 逐 tile 绘制，烘焙 quad 恒为空表，见本类类注释的字节码证据）"
                            + "⇒ 机器外壳改由 NacMachineShellRenderer（RenderLevelStageEvent.AFTER_SOLID_BLOCKS）"
                            + "自己叠一层壳；本类保留是给「未来某条真给 quad 的渲染路径」用的。"
                            + "本 WARN 现为双层限流（每实例 {} 条 + 每方块名一条 / 全局最多 {} 条）。",
                    this.block, state, side, EMPTY_QUADS_LOGGED_CAP, EMPTY_QUADS_TOTAL_CAP);
        }
    }

    /**
     * ★ <b>空表 WARN 的全局闸门</b>（本轮新增的第二层限流）：
     * <b>每个方块名最多一条</b>（同一台机器不重复刷）+ <b>全局总数上限</b>
     * （即使整合包里有几千种机器方块，也最多 {@link #EMPTY_QUADS_TOTAL_CAP} 条）。
     *
     * <p>为什么需要第二层：既有的 {@link #emptyQuadsWarn} 是<b>每包装实例</b>的额度，
     * 而"一屏几十条"来自一屏里的<b>几十台不同机器</b> —— 每台只花了 1 条额度，谁都拦不住。
     * 键用方块注册名（渲染热路径上只做一次 {@code getKey} + 一次 map 读写；
     * 判重表在资源重载边界由 {@link #invalidateSharedVariants()} 清空）。
     */
    private boolean allowEmptyQuadsWarn() {
        if (EMPTY_QUADS_TOTAL.getAndDecrement() <= 0) {
            return false; // 全局额度用尽
        }
        String name = String.valueOf(ForgeRegistries.BLOCKS.getKey(this.block));
        if (EMPTY_QUADS_LOGGED.size() >= EMPTY_QUADS_LOGGED_CAP) {
            return false; // 已登记的方块名够多了（防"几千种机器方块"把 map 撑大）
        }
        return EMPTY_QUADS_LOGGED.add(name); // 同一方块名只放行一次（newKeySet 的 add 是原子的 putIfAbsent）
    }

    /**
     * 情况 B（<b>我们主动放弃</b>）：delegate 给了 quads，但这份 ModelData 里没有世界 / 坐标。
     *
     * <p>只在 {@code data} 里**确实有属性**时打（世界渲染的那份数据由 LDLib 装，必有 WORLD/POS）；
     * 物品栏 / JEI / 粒子那份是 {@code ModelData.EMPTY}（零属性）⇒ 静默，属正常路径。
     */
    private void warnNoWorldPos(BlockState state, Direction side, ModelData data) {
        if (data.getProperties().isEmpty()) {
            return; // 物品栏 / JEI / 粒子：正常路径，不吵
        }
        if (this.noWorldPosWarn.getAndDecrement() > 0) {
            GTLModular.LOGGER.warn("[gtlmodular] CTM: 【我们主动放弃】机器 {} 的 ModelData 里有 {} 个属性，"
                            + "但按类型找不到世界/坐标（state={} face={}）—— 本次不换 sprite，"
                            + "**原样返回 delegate 的 quads**（外观 = 现状，绝不会变空）。",
                    this.block, data.getProperties().size(), state, side);
        }
    }

    /** 情况 B（<b>我们主动放弃</b>）：47 格变体还没解析出来（图集未就绪 / 资源缺失）⇒ 保持原贴图。 */
    private void warnVariantsNotReady(BlockState state, Direction side) {
        if (this.variantsNotReadyWarn.getAndDecrement() > 0) {
            GTLModular.LOGGER.warn("[gtlmodular] CTM: 【我们主动放弃】机器 {} 的 47 格变体 sprite 还没解析出来"
                            + "（state={} face={}）—— 本次保持接口机壳原贴图（不会变空）；若是资源缺失，"
                            + "启动日志里应同时有 CtmClientSetup 的「尚未就绪」那条。",
                    this.block, state, side);
        }
    }

    /**
     * 情况 C（<b>兜底 catch</b>）：CTM 自己的代码抛了异常 —— 只应发生在真 bug 时。
     *
     * <p>吞掉并原样返回 delegate 的 quads：绝不从区块网格化线程抛异常（抛出最坏整个区块不渲染）。
     */
    private void warnCtmFailed(BlockState state, Direction side, Throwable failure) {
        if (this.ctmFailedWarn.getAndDecrement() > 0) {
            GTLModular.LOGGER.warn("[gtlmodular] CTM: 【兜底 catch】机器 {} 的连接纹理计算抛异常"
                            + "（state={} face={}）—— 已吞掉并**原样返回 delegate 的 quads**（不会让区块渲染失败）；"
                            + "这是 CTM 的 bug，请把下面这条堆栈连同复现步骤回传。",
                    this.block, state, side, failure);
        }
    }

    private void warnHullMissing() {
        if (this.hullMissingWarn.getAndDecrement() > 0) {
            GTLModular.LOGGER.warn("[gtlmodular] CTM: 真空仓 {} 的 tier 机壳外壳 sprite 没在图集里找到"
                            + "（推导路径 {}）—— 该仓这一面保持接口机壳外观（不会变空）。",
                    this.block, CtmHullGroup.tierCasingSprite(CtmHullGroup.hatchTier(), "side"));
        }
    }

    // ------------------------------------------------------------------
    // ④ 其余一律转发（不改渲染类型 / AO / GUI / 粒子 / 物品变换）
    // ------------------------------------------------------------------

    @Override
    public boolean useAmbientOcclusion() {
        return this.delegate.useAmbientOcclusion();
    }

    @Override
    public boolean useAmbientOcclusion(BlockState state) {
        return this.delegate.useAmbientOcclusion(state);
    }

    @Override
    public boolean isGui3d() {
        return this.delegate.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return this.delegate.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return this.delegate.isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return this.delegate.getParticleIcon();
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        return this.delegate.getParticleIcon(data);
    }

    @Override
    public ItemTransforms getTransforms() {
        return this.delegate.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return this.delegate.getOverrides();
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return this.delegate.getRenderTypes(state, rand, data);
    }
}
