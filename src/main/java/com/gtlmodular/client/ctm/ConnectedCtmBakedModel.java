package com.gtlmodular.client.ctm;

import com.gtlmodular.GTLModular;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
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

/**
 * <b>邻居感知 BakedModel（方案 B）</b>：把 vanilla 已经烘焙好的 {@code cube_all} 模型包一层，
 * 在<b>渲染时按 4+4 邻居选变体 sprite</b>。
 *
 * <h2>为什么走模型层而不是方块状态</h2>
 * 用方块状态表达连通要新增 24 个布尔属性（3 组 × 8 邻居），而且<b>新增属性会移动 {@code StateDefinition}
 * 里既有状态的编号</b> ⇒ 老存档里已放置的方块可能被解析成别的状态（= 存档风险）。
 * 走模型层则：<b>无新方块、无新属性、无新注册名、{@code blockstates/*.json} 一个字都不用改</b>。
 *
 * <h2>世界与坐标从哪来（本轮已用字节码坐实，不是推测）</h2>
 * Forge 1.20.1 的 {@code BakedModel} 扩展方法签名是
 * {@code getQuads(BlockState, Direction, RandomSource, ModelData, RenderType)}
 * —— <b>没有</b> {@code level} / {@code pos}。邻居信息只能经由 {@code ModelData} 传进来，
 * 而唯一能拿到世界与坐标的钩子是 Forge 的
 * {@code getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data)}。
 *
 * <p>本整合包装了 Embeddium（Sodium 分支，替换原版区块渲染管线），所以必须确认它会不会为
 * <b>非方块实体</b>的普通方块调用 {@code getModelData}。逐字节验证结论（{@code javap -c}）：
 * <pre>
 *   me.jellysquid.mods.sodium...ChunkBuilderMeshingTask：
 *     modelDataMap = ModelDataSnapshotter.getModelDataForSection(level, sectionPos)   // 只含方块实体
 *     每个方块：
 *       var data = modelDataMap.getOrDefault(pos, ModelData.EMPTY);
 *       ModelData md = model.getModelData(ctx.localSlice(), pos, state, data);        // ★ 普通方块也会调用
 *       ctx.update(pos, ..., md, renderType) → BlockRenderer.renderModel(...)
 *         → model.getQuads(state, direction, random, md, renderType)                  // ★ 拿到同一份 md
 *   me.jellysquid...WorldSlice：volume = section ± 2 格（三个轴都扩 2）
 *     ⇒ 1 格邻居（含对角）永远在切片内 ⇒ 8 邻居查询安全，不会在区块边界丢边
 * </pre>
 * 另外 vanilla Forge 的 {@code ChunkRenderDispatcher} 路径同样会为每个方块调用
 * {@code model.getModelData(renderChunkRegion, pos, state, ...)}（见 Forge 源码 patch）。
 * ⇒ <b>两条渲染路径都拿得到世界与坐标</b>，无需 Mixin、无需方块实体、无需新属性。
 *
 * <h2>降级与回退（层层兜底，绝不报错、绝不紫黑格）</h2>
 * <ol>
 *   <li>拿不到 {@code ModelData}（物品栏、粒子图标、破坏进度、其它渲染路径）⇒ 直接转发给原模型
 *       = 第 0 格 = <b>与今天完全一致</b>；</li>
 *   <li>面内坐标帧推不出来（非平面/非轴向几何）⇒ 不提供连接信息 ⇒ 同上；</li>
 *   <li>某张变体 sprite 没进图集（被识别为 missingno）⇒ 该变体退回基础贴图（第 0 格）+ 打日志。</li>
 * </ol>
 */
public final class ConnectedCtmBakedModel implements BakedModel {

    /** 连接位图：6 个面 × 8 邻居位，打包进一个 {@code long}。 */
    public static final ModelProperty<Long> CTM_MASK = new ModelProperty<>();

    private static final int FACE_COUNT = 6;
    private static final int BITS_PER_FACE = 8;
    private static final int FACE_MASK = 0xFF;

    /** 正交 4 位在 position 位序里的顺序（TOP / RIGHT / BOTTOM / LEFT）。 */
    private static final int[] ORTHO_BITS = { 0, 2, 4, 6 };
    /** 对角 4 位（TOP_RIGHT / BOTTOM_RIGHT / BOTTOM_LEFT / TOP_LEFT）。 */
    private static final int[] DIAG_BITS = { 1, 3, 5, 7 };

    /** compact 每面切几个子块（2×2）。 */
    private static final int QUADRANTS = 4;

    private final BakedModel delegate;
    private final Block block;
    private final String spritePrefix;
    private final boolean compact;
    /**
     * ★ 2026-09-17 新增：是否走 NH 的 <b>{@code connect=tile}「同贴图组」</b>判邻居。
     *
     * <p>只有<b>网格接口机壳</b>那一条 target 是 {@code true}（NH 的 properties 里写的就是
     * {@code connect=tile}，而 GT5U 的机器面（真空仓 / 主机 / 11 个模块）外壳用的就是这张贴图）
     * ⇒ 机壳朝机器那一侧也算连通（{@code connect=tile} 是双向的）。
     *
     * <p><b>玻璃（{@code compact}，已冻结）与线圈一律 {@code false}</b>：它们<b>不</b>走"同贴图组"，
     * 行为必须与今天<b>逐字节一致</b>（各自只认同方块 / 同贴图组之外的旧判据）。
     *
     * <p>⚠️ <b>口径澄清（2026-09-18 复核修正的注释，代码不变）</b>：玻璃这一条**不是**照抄 NH ——
     * NH 原文写的是 {@code connect=block}（{@code matchBlocks=… metadata=…}：只认同方块同 metadata），
     * 而<b>本仓资源里写的是 {@code connect=tile}</b>；这是一处<b>我们有意的偏离</b>
     * （玻璃的 5 格 compact 变体是本项目自己排的，用 {@code connect=tile} 的邻居口径与机壳那条统一，
     * 观感更连贯）。它在 {@code _census/check_ctm_assets.js} 里以 {@code allowConnectDiff} 显式登记为
     * "有意偏离"，而不是"抄错了"。后人改这里之前请先读那条登记。
     */
    private final boolean tileGroup;
    private final int variantCount;
    private final CtmFaceFrame frame;

    /** 基础（= 变体 0 所对应的）每面 quad，仅在解析期使用。 */
    private final BakedQuad[] baseQuad = new BakedQuad[FACE_COUNT];
    /**
     * compact 的每面子块几何模板（4 个，下标 = {@code hu * 2 + hv}）；某面为 {@code null} =
     * 该面的基础 quad 不是"UV 四角各一次"的平面矩形 ⇒ 不敢切，整体退回整面选格（绝不少画）。
     */
    private final SubQuad[][] subTemplates = new SubQuad[FACE_COUNT][];
    /** 基础贴图（= 第 0 格那张），用于缺失变体的回退。 */
    private TextureAtlasSprite baseSprite;

    /** 展开后的变体 quad 列表：下标 = faceOrdinal * variantCount + variantIndex；解析完成前为 null。 */
    private volatile List<BakedQuad>[] variantQuads;
    /**
     * compact 的子象限 quad：下标 = {@code (faceOrdinal * variantCount + variant) * 4 + (hu * 2 + hv)}。
     *
     * <p>每格是<b>单元素列表</b>（一个子块就是一个 quad）；{@code null} = 该模型不走子块路径
     * （几何切不出、或还没解析）。取不到时 {@link #getQuads} 回退到整面选格 —— 保证"任何情况下不少画 quad"。
     */
    private volatile List<BakedQuad>[] compactSubQuads;
    /**
     * ★ 只有当<b>全部变体 sprite 都命中图集</b>时才置 true。
     *
     * <p>实机教训：第一版不管命中与否都置 true ⇒ 解析发生得太早（图集还没缝合）时被"锁死"，
     * 之后即使图集就绪也永不重试。日志证据：解析在 `19:03:18`，而
     * `Created: 16384x16384x2 minecraft:textures/atlas/blocks.png-atlas` 在 `19:03:26`（晚 8 秒）。
     */
    private volatile boolean resolved;
    private volatile boolean warned;
    /** 最近一次解析缺了几张（供 {@code CtmClientSetup} 打准日志）。 */
    private volatile int missed;
    /** 懒解析尝试上限：图集里永远没有这些 sprite 时，别在渲染热路径上无限重试。 */
    private static final int MAX_LAZY_ATTEMPTS = 20;
    /**
     * ★ 临时诊断计数器（定位"整片玻璃墙仍带窗框"用；根因确定后删掉）。
     *
     * <p><b>注意这是实例字段</b>（每个模型各一份）—— 2026-09-16 踩过坑：写成 `static` 时会被**机壳**
     * 那 4 个模型条目把额度用光，玻璃一条都不打 ⇒ 看起来像"玻璃没走这条路"，其实是日志被吃掉了 ✗。
     */
    private final java.util.concurrent.atomic.AtomicInteger diagGetModelData = new java.util.concurrent.atomic.AtomicInteger(4);
    private final java.util.concurrent.atomic.AtomicInteger diagGetQuads = new java.util.concurrent.atomic.AtomicInteger(6);
    private volatile int lazyAttempts;
    private volatile long lastLazyAttemptAt;

    /**
     * 懒解析回调（由 {@code CtmClientSetup} 注入）：渲染线程发现"还没全部就绪"时经它再试一次，
     * 兜住"缝合事件没来 / 事件来得太早"的情况。
     */
    static volatile java.util.function.Consumer<ConnectedCtmBakedModel> lazyResolver;

    /**
     * 实际取到 sprite 的命名空间（先试本模组，再试 {@code minecraft}）。
     *
     * <p>为什么要试两个：Forge 会把各资源包的 {@code assets/minecraft/atlases/blocks.json} 合并进同一张方块图集
     * （本包内 AE2 / curios / ftb-library / ftb-quests / Placebo / Re-Avaritia / ToolBelt 七个模组都把声明放在那里，
     * 而原版自己也有同一份 ⇒ 合并成立；本模组的声明 2026-09-16 起也放那里），
     * 但 {@code directory} source 产出的 sprite 究竟注册在"资源所属命名空间"还是 {@code minecraft} 命名空间下，
     * 取决于 Forge/原版实现细节 ⇒ 用"两次查找"一次覆盖，并在日志里说明实际命中哪个。
     */
    private volatile String spriteNamespace;

    public ConnectedCtmBakedModel(BakedModel delegate, Block block, String spritePrefix, boolean compact,
                                  BlockState frameState, boolean tileGroup) {
        this.delegate = delegate;
        this.block = block;
        this.spritePrefix = spritePrefix;
        this.compact = compact;
        this.tileGroup = tileGroup;
        this.variantCount = compact ? CtmConnectivity.COMPACT_TILES : CtmConnectivity.CTM_TILES;
        this.frame = CtmFaceFrame.derive(delegate, frameState);
    }

    public Dump describe() {
        return new Dump(this.block, this.spritePrefix, this.variantCount, this.frame.complete());
    }

    /** 是否**全部**变体都已就绪（自检/日志用）。 */
    public boolean fullyResolved() {
        return this.resolved;
    }

    /** 最近一次解析缺了几张变体 sprite（自检/日志用）。 */
    public int missedSprites() {
        return this.missed;
    }

    /** 实际命中的 sprite 命名空间（{@code null} = 还没命中过）。 */
    public String spriteNamespace() {
        return this.spriteNamespace;
    }

    /** 面内坐标帧是否已解析出来（6 面全成功才算）。 */
    public boolean frameComplete() {
        return this.frame.complete();
    }

    /** 诊断用快照（打日志用，不含任何渲染对象）。 */
    public record Dump(Block block, String spritePrefix, int variantCount, boolean frameComplete) {
    }

    // ------------------------------------------------------------------
    // ① 唯一能拿到世界 + 坐标的钩子：算 6 个面的 8 位连接位图
    // ------------------------------------------------------------------

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        if (!this.frame.complete()) {
            return data; // 帧未知 ⇒ 不提供连接信息 ⇒ 回退第 0 格
        }
        long packed = 0L;
        for (Direction face : Direction.values()) {
            int mask = computeMask(level, pos, face);
            packed |= (long) (mask & FACE_MASK) << (face.ordinal() * BITS_PER_FACE);
        }
        // ★ 诊断（2026-09-16：玩家实测"整片玻璃墙仍每格带窗框" ⇒ 必须坐实 ModelData 到底有没有传到玻璃）
        //   每个模型只打前几次，日志量有上限；定位到根因后可删。
        if (diagGetModelData.getAndDecrement() > 0) {
            GTLModular.LOGGER.info("[gtlmodular] CTM 诊断 getModelData：{} @{} 掩码=0x{} 帧完整={}",
                    this.spritePrefix, pos, Long.toHexString(packed).toUpperCase(), this.frame.complete());
        }
        return data.derive().with(CTM_MASK, packed).build();
    }

    private int computeMask(BlockAndTintGetter level, BlockPos pos, Direction face) {
        Direction rightDir = this.frame.rightOf(face);
        Direction upDir = this.frame.upOf(face);
        if (rightDir == null || upDir == null) {
            return 0;
        }
        int mask = 0;
        for (int p : ORTHO_BITS) {
            if (isSame(level, pos, rightDir, upDir, p)) {
                mask |= 1 << p;
            }
        }
        // ★ 2026-09-16（compact 两级改造，修掉一个真 bug）：原来这里是
        //     `if (this.compact) { return mask; }` —— 对角位永远是 0 ⇒ 象限选格里
        //     "两条边都通"的格子永远看不到对角 ⇒ 第 4 张（MIXED）永远选不到。
        //   规格 §3.3 明确：对角位"只在两条边都连通时被读"，所以 5 格方案**必须**查全 8 位。
        //   而 singletonIndex 的剪枝只对 47 格那套成立（对角不改变结果），compact 一律不剪。
        if (!this.compact && CtmConnectivity.singletonIndex(mask) >= 0) {
            return mask; // 47 格：对角不可能改变结果 ⇒ 省 4 次查询
        }
        for (int p : DIAG_BITS) {
            if (isSame(level, pos, rightDir, upDir, p)) {
                mask |= 1 << p;
            }
        }
        return mask;
    }

    /**
     * position {@code p} 处的邻居是否与本方块连通。
     *
     * <h3>★ 2026-09-17：从"按方块身份"改成 NH 的 {@code connect=tile}（本轮的核心之一）</h3>
     * 依据（两条，一条 NH 原文、一条玩家实机）：
     * <ol>
     *   <li>NH 的机壳 CTM 配置是 {@code connect=tile}：
     *       {@code assets/gregtech/mcpatcher/ctm/nanochipmeshinterfacecasing/nanochipmeshinterfacecasing.properties}
     *       （{@code matchTiles=gregtech:iconsets/NANOCHIP_MESH_INTERFACE_CASING} / {@code method=ctm} /
     *       {@code faces=all} / {@code tiles=0-46} / {@code connect=tile}）——
     *       {@code matchTiles} 匹配<b>贴图名</b>，{@code connect=tile} 判的是"邻居那一侧画出来的 tile
     *       是不是同一张"。GT5U 的机器面走 {@code RenderBlocks} 系路径 ⇒ MCPatcher 的连接查表对
     *       <b>机器面同样生效</b>。</li>
     *   <li>玩家在 GTNH 实机看到：「<b>NH 里面它的舱室依然是有蓝黑色的连接纹理的</b>」——真空仓 / 多方块
     *       的外壳面确实与周围纳米芯片接口机壳连成一片。</li>
     * </ol>
     * 而老实现是 {@code state.is(this.block)}：机器是<b>别的方块</b> ⇒ 机壳永远把机器那一侧判成"没连"，
     * 机器自己那边更是恒为 0（永远画第 0 格孤立变体）⇒ 玩家截图里那条缝就是这么来的。
     *
     * <p>★ 只有 {@link #tileGroup}（机壳那条 target）走组判据：玻璃（{@code compact}，已冻结）与
     * 线圈（NH 是 {@code connect=block}）必须保持"只认同方块"的旧行为，逐字节不变。
     *
     * <p>组里的判据见 {@link CtmHullGroup#rendersMeshCasingTile}：
     * {机壳本体} ∪ {外壳用同一张贴图的机器：主机 / 11 个模块 / <b>已并入成形结构的</b>真空仓}。
     * 白名单之外（能源仓 / 总线 / 消音器 —— 外壳是别的机壳）一律不连，与 NH 一致。
     */
    private boolean isSame(BlockAndTintGetter level, BlockPos pos, Direction rightDir, Direction upDir, int p) {
        int sr = CtmConnectivity.STEP_RIGHT[p];
        int su = CtmConnectivity.STEP_UP[p];
        int dx = sr * rightDir.getStepX() + su * upDir.getStepX();
        int dy = sr * rightDir.getStepY() + su * upDir.getStepY();
        int dz = sr * rightDir.getStepZ() + su * upDir.getStepZ();
        if (dx == 0 && dy == 0 && dz == 0) {
            return false;
        }
        BlockPos neighbour = pos.offset(dx, dy, dz);
        BlockState state = level.getBlockState(neighbour);
        if (state.is(this.block)) {
            return true; // 同方块（玻璃 / 线圈 / 机壳自己的老行为）—— 任何一条 target 都保留
        }
        if (!this.tileGroup) {
            return false; // 玻璃（compact，已冻结）与线圈（connect=block）：不引入任何新连通
        }
        return CtmHullGroup.rendersMeshCasingTile(level, neighbour, state);
    }

    // ------------------------------------------------------------------
    // ② 按连接位图选变体 quad
    // ------------------------------------------------------------------

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
        if (side == null) {
            // 物品栏 / 粒子：完全保持原模型行为
            return this.delegate.getQuads(state, side, rand, data, renderType);
        }
        if (!this.resolved) {
            // ★ 解析可能"太早"（图集还没缝合）⇒ 在这里兜一次（节流 + 有次数上限，失败绝不抛）
            maybeResolveLazily();
        }
        List<BakedQuad>[] variants = this.variantQuads;
        if (variants == null) {
            return this.delegate.getQuads(state, side, rand, data, renderType);
        }
        Long packed = data == null ? null : data.get(CTM_MASK);
        if (packed == null) {
            if (diagGetQuads.getAndDecrement() > 0) {
                GTLModular.LOGGER.info("[gtlmodular] CTM 诊断 getQuads：{} face={} **没拿到 ModelData/掩码** ⇒ 只能退回原模型（第 0 格）",
                        this.spritePrefix, side);
            }
            return this.delegate.getQuads(state, side, rand, data, renderType);
        }
        int mask = (int) ((packed >>> (side.ordinal() * BITS_PER_FACE)) & FACE_MASK);
        // ★ 诊断（2026-09-16 第二轮）：**只抓"邻居≥3"的块**（本该选到"四边全连通/长条"的那几种），
        //   因为那才是"墙中央"的情形 —— 第一轮把额度用在了 1 格宽的边角块上，看不出问题。
        //   同时打出该面推导出的 right/up ⇒ 一次就能分辨「方向推错 / 邻居偏移错 / 掩码打包错」。
        if (Integer.bitCount(mask) >= 3 && diagGetQuads.getAndDecrement() > 0) {
            GTLModular.LOGGER.info("[gtlmodular] CTM 诊断(邻居>=3)：{} face={} mask={} => {}｜该面 right={} up={}（全部就绪={}）",
                    this.spritePrefix, side, Integer.toBinaryString(mask), choice(mask),
                    this.frame.has(side) ? this.frame.rightOf(side) : null,
                    this.frame.has(side) ? this.frame.upOf(side) : null, this.resolved);
        }
        if (this.compact) {
            // ★ compact（5 格）= NH 的两级：**只有** mask==0 / mask==0xFF 整面（第 0 / 第 1 张），
            //   其余一律把该面切成 2×2 四个子 quad，各自按 (hu,hv) 选 sprite
            //   （`_census/compact_quadrant_spec.md` §1.2 / §2.2 / §3.4 / §6.3）。
            return compactQuads(state, side, rand, data, renderType, mask, variants);
        }
        int index = CtmConnectivity.ctmIndex(mask);
        List<BakedQuad> quads = variants[side.ordinal() * this.variantCount + index];
        return quads != null ? quads : this.delegate.getQuads(state, side, rand, data, renderType);
    }

    /** 诊断日志用：本面这次会怎么选格（纯字符串，不影响渲染）。 */
    private String choice(int mask) {
        if (!this.compact) {
            return "tile " + CtmConnectivity.ctmIndex(mask);
        }
        if (mask == 0x00 || mask == FACE_MASK) {
            return "整面 tile " + CtmConnectivity.compactWholeSprite(mask);
        }
        StringBuilder sb = new StringBuilder("四象限 ");
        for (int hu = 0; hu < 2; hu++) {
            for (int hv = 0; hv < 2; hv++) {
                sb.append(CtmConnectivity.compactQuadrantSprite(mask, hu, hv)).append(' ');
            }
        }
        return sb.toString().trim();
    }

    /**
     * compact 的两级选格（规格 §1.2 / §6.3）。
     *
     * <p>整面路径只有两种：{@code mask==0x00} → 第 0 张、{@code mask==0xFF} → 第 1 张
     * （对应 {@code processFace} 里的 {@code if (bits != 0 && bits != 255) renderSplitFace(...)}）。
     * 其余掩码一律 4 个子块 —— 这也是**唯一**会出现第 4 张（MIXED）的地方。
     *
     * <p><b>绝不少画</b>：子块列表被切不出模板（或异常地缺格）时，退回旧整面选格
     * {@link CtmConnectivity#compactIndex(int)}，再不行退回原模型 —— 任何一条路都返回非空 quad。
     */
    private List<BakedQuad> compactQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
                                         RenderType renderType, int mask, List<BakedQuad>[] variants) {
        if (mask == 0x00 || mask == FACE_MASK) {
            List<BakedQuad> whole = variants[side.ordinal() * this.variantCount
                    + CtmConnectivity.compactWholeSprite(mask)];
            if (whole != null && !whole.isEmpty()) {
                return whole;
            }
        } else {
            List<BakedQuad>[] subs = this.compactSubQuads;
            if (subs != null) {
                List<BakedQuad> out = new ArrayList<>(QUADRANTS);
                for (int hu = 0; hu < 2; hu++) {
                    for (int hv = 0; hv < 2; hv++) {
                        int v = CtmConnectivity.compactQuadrantSprite(mask, hu, hv);
                        List<BakedQuad> one = subs[(side.ordinal() * this.variantCount + v) * QUADRANTS + hu * 2 + hv];
                        if (one != null && !one.isEmpty()) {
                            out.addAll(one);
                        }
                    }
                }
                if (out.size() == QUADRANTS) {
                    return out;
                }
            }
        }
        // 兜底：整面选格（旧逻辑）→ 再不行交给原模型。**任何情况下都不返回空表**（否则面上留洞）。
        List<BakedQuad> fallback = variants[side.ordinal() * this.variantCount
                + CtmConnectivity.compactIndex(mask)];
        return fallback != null ? fallback : this.delegate.getQuads(state, side, rand, data, renderType);
    }

    /**
     * 渲染路径上的兜底重试（1 秒节流、最多 {@link #MAX_LAZY_ATTEMPTS} 次）。
     *
     * <p>存在意义：实机证据显示模型烘焙（19:03:18）早于图集缝合完成（19:03:26），
     * 而 `TextureStitchEvent.Post` 也不保证在所有加载路径上都送达 ⇒ 让它自己再试一次。
     * 任何异常都被吞掉（渲染绝不能因为 CTM 解析失败而炸）。
     */
    private void maybeResolveLazily() {
        java.util.function.Consumer<ConnectedCtmBakedModel> resolver = lazyResolver;
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
            resolver.accept(this);
        } catch (Throwable ignored) {
            // 故意吞掉：CTM 只是观感优化，绝不允许影响正常渲染
        }
    }

    // ------------------------------------------------------------------
    // ③ 解析：把 52 张变体 sprite 预烘焙成 quad
    // ------------------------------------------------------------------

    /**
     * 解析变体 sprite 并预烘焙 quad。
     *
     * <p>★ 与第一版的区别（本轮实机修正）：**没全命中就不锁死**（返回 false 且继续允许后续重试），
     * 并且 sprite 查找会**依次试本模组命名空间与 {@code minecraft} 命名空间**。
     *
     * @param atlas 方块图集的 sprite 查询函数（{@code Minecraft#getTextureAtlas(InventoryMenu.BLOCK_ATLAS)}）
     * @return 是否**全部**变体都已就绪
     */
    @SuppressWarnings("unchecked")
    public boolean resolve(java.util.function.Function<ResourceLocation, TextureAtlasSprite> atlas, BlockState frameState) {
        if (this.resolved) {
            return true;
        }
        for (Direction face : Direction.values()) {
            List<BakedQuad> quads = this.delegate.getQuads(frameState, face, RandomSource.create(42L));
            if (quads.isEmpty()) {
                return false;
            }
            this.baseQuad[face.ordinal()] = quads.get(0);
            // compact 才需要子块几何模板（47 格仍走整面 47 选 1）
            this.subTemplates[face.ordinal()] = this.compact ? buildSubQuads(quads.get(0)) : null;
        }
        this.baseSprite = this.baseQuad[0].getSprite();

        List<BakedQuad>[] built = new List[FACE_COUNT * this.variantCount];
        TextureAtlasSprite[] chosen = new TextureAtlasSprite[this.variantCount];
        int missing = 0;
        String firstMissing = null;
        TextureAtlasSprite zero = null;
        for (int v = 0; v < this.variantCount; v++) {
            TextureAtlasSprite sprite = lookup(atlas, v);
            if (isMissing(sprite)) {
                missing++;
                if (firstMissing == null) {
                    firstMissing = this.spritePrefix + "/" + v;
                }
                if (zero == null && v != 0) {
                    zero = lookup(atlas, 0);
                }
                sprite = (zero != null && !isMissing(zero)) ? zero : this.baseSprite;
            }
            chosen[v] = sprite;
            for (Direction face : Direction.values()) {
                built[face.ordinal() * this.variantCount + v] =
                        List.of(retexture(this.baseQuad[face.ordinal()], sprite));
            }
        }
        this.missed = missing;
        this.variantQuads = built;
        if (this.compact) {
            // 子象限 quad（每面每变体 4 个）——与整面那 5 张并行存在，getQuads 按掩码二选一。
            this.compactSubQuads = buildCompactSubQuads(chosen);
        }
        if (missing == 0) {
            this.resolved = true;
            this.lazyAttempts = 0;
            return true;
        }
        if (!this.warned) {
            this.warned = true;
            GTLModular.LOGGER.warn("[gtlmodular] CTM: {}/{} 张变体 sprite 没在图集里找到（前缀 {}，例：{}）——"
                            + "这些变体暂时退回第 0 格；**图集缝合完成前解析会失败，之后会自动重试**。"
                            + "若一直重试失败，说明变体图没进图集（atlas 声明或缓存问题），把这条日志给我。",
                    missing, this.variantCount, this.spritePrefix, firstMissing);
        }
        return false;
    }

    /**
     * 取某张变体的 sprite：先试本模组命名空间，再试 {@code minecraft} 命名空间；
     * 一旦命中就记住是哪个（后续直接走命中那条，避免每次都做两次查找）。
     */
    private TextureAtlasSprite lookup(java.util.function.Function<ResourceLocation, TextureAtlasSprite> atlas, int v) {
        String hit = this.spriteNamespace;
        if (hit != null) {
            return atlas.apply(new ResourceLocation(hit, this.spritePrefix + "/" + v));
        }
        TextureAtlasSprite sprite = atlas.apply(new ResourceLocation(GTLModular.MOD_ID, this.spritePrefix + "/" + v));
        if (!isMissing(sprite)) {
            this.spriteNamespace = GTLModular.MOD_ID;
            return sprite;
        }
        sprite = atlas.apply(new ResourceLocation("minecraft", this.spritePrefix + "/" + v));
        if (!isMissing(sprite)) {
            this.spriteNamespace = "minecraft";
        }
        return sprite;
    }

    /** 这张 sprite 是不是"没进图集"（missingno / null）。★ 包内可见：机器那条也用它。 */
    static boolean isMissing(TextureAtlasSprite sprite) {
        return sprite == null || sprite.contents().name().getPath().equals("missingno");
    }

    /**
     * 把基础 quad 的 sprite 换成变体 sprite，并按两个 sprite 在图集里的矩形<b>线性重映射 UV</b>。
     *
     * <p>1.20.1 的 {@code BakedQuad} 顶点 UV 存的是<b>整张图集空间</b>的归一化坐标（烘焙期由
     * {@code sprite.getU/getV} 算出），所以换 sprite 必须同时改 UV。
     * cube_all 的整面 UV 正好落在 sprite 矩形的四个角上，这里先还原「贴图内 0..16 的角点」再按
     * vanilla 同款 {@code sprite.getU(double)} 写出 —— 与重新烘焙等效，且不依赖 FaceBakery 内部实现。
     *
     * <p>★ 2026-09-17 起<b>包内可见</b>：机器那条（{@code MachineCtmBakedModel}）在渲染期换外壳 sprite 时
     * 复用<b>同一份</b>重贴图数学（两处必须是同一套，否则机壳与机器面的 UV 会不一致）。
     *
     * <p>★ 2026-09-18 起<b>改为 public</b>：新渲染器
     * {@code com.gtlmodular.client.renderer.NacMachineShellRenderer}（在另一个包里）也用它 ——
     * 罩子的几何是"借机壳 {@code cube_all} 的面 quad + 外移 0.001"，换 sprite 这一步必须与机壳 /
     * 机器面逐字节同源，否则罩子与相邻机壳的 47 格接缝会对不上。
     */
    public static BakedQuad retexture(BakedQuad base, TextureAtlasSprite to) {
        TextureAtlasSprite from = base.getSprite();
        int[] vertices = base.getVertices().clone();
        int stride = vertices.length / 4;
        float u0 = from.getU0();
        float v0 = from.getV0();
        float du = from.getU1() - u0;
        float dv = from.getV1() - v0;
        for (int i = 0; i < 4; i++) {
            int b = i * stride;
            float u = Float.intBitsToFloat(vertices[b + 4]);
            float v = Float.intBitsToFloat(vertices[b + 5]);
            float su = du == 0.0F ? 0.0F : (u - u0) / du;
            float sv = dv == 0.0F ? 0.0F : (v - v0) / dv;
            vertices[b + 4] = Float.floatToRawIntBits(to.getU(snapSixteenth(su) * 16.0D));
            vertices[b + 5] = Float.floatToRawIntBits(to.getV(snapSixteenth(sv) * 16.0D));
        }
        return new BakedQuad(vertices, base.getTintIndex(), base.getDirection(), to, base.isShade());
    }

    /** 落在 1/16 网格上的 UV 吸附回去（cube_all 的角点正好在网格上）；否则原样保留。 */
    private static float snapSixteenth(float s) {
        float t = s * 16.0F;
        float r = Math.round(t);
        return Math.abs(t - r) < 1.0E-3F ? r / 16.0F : s;
    }

    // ------------------------------------------------------------------
    // ③b compact 子象限：几何 + UV（规格 `_census/compact_quadrant_spec.md` §4.2/§4.3/§6.4）
    // ------------------------------------------------------------------

    /**
     * 一个子象限的几何模板：4 个顶点的位置 + <b>贴图内归一化坐标</b>（0 / 0.5 / 1）。
     *
     * <p>顶点顺序 = 基础 quad 的顶点顺序（⇒ 环绕方向与整面一致）。
     * {@code s} 沿贴图 u（右为正）、{@code t} 沿贴图 v（下为正）——与 1.20 的 {@code sprite.getU/getV} 同向。
     */
    private static final class SubQuad {
        /** 4 个顶点的位置：{@code [i * 3 + 0..2]} = x / y / z。 */
        private final float[] pos = new float[12];
        /** 4 个顶点的贴图内坐标：{@code [i * 2 + 0..1]} = (s, t)。 */
        private final float[] st = new float[8];
    }

    /**
     * 从基础（整面）quad 推出 4 个子象限的几何模板。
     *
     * <h3>怎么切（规格 §4.2「切线 = min/max 的中点」在 1.20 的等价物）</h3>
     * 基础 quad 的 4 个顶点 UV 恰好落在 sprite 四角 ⇒ 每个顶点在贴图内的归一化坐标
     * {@code (a,b) ∈ {0,1}²} 就是它在面上的"角标签"。子块 {@code (hu,hv)} 覆盖
     * {@code s ∈ [0.5hu, 0.5hu+0.5]}、{@code t ∈ [0.5hv, 0.5hv+0.5]}（= 把 16×16 贴图当 2×2 用），
     * 其 4 个角的位置由<b>双线性插值</b>给出（整面是平面矩形 ⇒ 双线性即精确，等价于
     * Angelica 把 render bounds 缩到一半、再由原版面渲染器反推 UV 的效果）：
     * <pre>
     *   pos(s,t) = Σ_j w_j · P_j       w_j = (a_j == 1 ? s : 1-s) · (b_j == 1 ? t : 1-t)
     * </pre>
     * 子块的 4 个角与基础顶点一一对应：{@code (s,t) = (0.5hu + 0.5a_i, 0.5hv + 0.5b_i)}，
     * 按 {@code i} 顺序排列 ⇒ 环绕方向与基础 quad 相同（不需要任何"6 面朝向表"，
     * 也因此不会踩 {@code flipBottom} / u/v 转置那些陷阱）。
     *
     * @return 4 个子块模板（下标 {@code hu * 2 + hv}）；基础 quad 不是"UV 四角各一次"的平面矩形时
     *         返回 {@code null}（调用方退回整面选格，绝不少画）
     */
    private static SubQuad[] buildSubQuads(BakedQuad base) {
        int[] data = base.getVertices();
        if (data.length < 32 || data.length % 4 != 0) {
            return null;
        }
        int stride = data.length / 4;
        TextureAtlasSprite from = base.getSprite();
        float u0 = from.getU0();
        float v0 = from.getV0();
        float du = from.getU1() - u0;
        float dv = from.getV1() - v0;
        if (du == 0.0F || dv == 0.0F) {
            return null;
        }
        float[] px = new float[4];
        float[] py = new float[4];
        float[] pz = new float[4];
        int[] ai = new int[4];
        int[] bi = new int[4];
        int[] seen = new int[4];
        for (int i = 0; i < 4; i++) {
            int o = i * stride;
            px[i] = Float.intBitsToFloat(data[o]);
            py[i] = Float.intBitsToFloat(data[o + 1]);
            pz[i] = Float.intBitsToFloat(data[o + 2]);
            float u = Float.intBitsToFloat(data[o + 4]);
            float v = Float.intBitsToFloat(data[o + 5]);
            int a = Math.round((u - u0) / du);
            int b = Math.round((v - v0) / dv);
            if (a < 0 || a > 1 || b < 0 || b > 1) {
                return null; // 顶点不在贴图四角 ⇒ 这面带不动"贴图内坐标"，不猜
            }
            ai[i] = a;
            bi[i] = b;
            seen[a * 2 + b]++;
        }
        for (int c : seen) {
            if (c != 1) {
                return null; // 4 个顶点没有铺满"贴图四角各一次" ⇒ 同上
            }
        }
        SubQuad[] out = new SubQuad[QUADRANTS];
        for (int hu = 0; hu < 2; hu++) {
            for (int hv = 0; hv < 2; hv++) {
                SubQuad quad = new SubQuad();
                for (int i = 0; i < 4; i++) {
                    float s = (ai[i] == 0) ? 0.5F * hu : 0.5F * hu + 0.5F;
                    float t = (bi[i] == 0) ? 0.5F * hv : 0.5F * hv + 0.5F;
                    quad.st[i * 2] = s;
                    quad.st[i * 2 + 1] = t;
                    float x = 0.0F;
                    float y = 0.0F;
                    float z = 0.0F;
                    for (int j = 0; j < 4; j++) {
                        float w = (ai[j] == 1 ? s : 1.0F - s) * (bi[j] == 1 ? t : 1.0F - t);
                        x += w * px[j];
                        y += w * py[j];
                        z += w * pz[j];
                    }
                    quad.pos[i * 3] = x;
                    quad.pos[i * 3 + 1] = y;
                    quad.pos[i * 3 + 2] = z;
                }
                out[hu * 2 + hv] = quad;
            }
        }
        return out;
    }

    /** 用某张 sprite 把子块模板烤成一个 {@code BakedQuad}（UV 取该 sprite 的对应半格：0 / 8 / 16 像素）。 */
    private static BakedQuad bakeSubQuad(BakedQuad base, SubQuad template, TextureAtlasSprite sprite) {
        int[] from = base.getVertices();
        int stride = from.length / 4;
        int[] out = new int[from.length];
        for (int i = 0; i < 4; i++) {
            // 颜色 / 光照(uv2) / 法线等槽位整段沿用基础顶点：与整面渲染时同一组值，
            // 避免"同一个面被切成 4 份后每份自带一套颜色"（规格 §5 在 1.20 上无法逐顶点照搬，§7.4）。
            System.arraycopy(from, i * stride, out, i * stride, stride);
            out[i * stride] = Float.floatToRawIntBits(template.pos[i * 3]);
            out[i * stride + 1] = Float.floatToRawIntBits(template.pos[i * 3 + 1]);
            out[i * stride + 2] = Float.floatToRawIntBits(template.pos[i * 3 + 2]);
            out[i * stride + 4] = Float.floatToRawIntBits(sprite.getU(template.st[i * 2] * 16.0D));
            out[i * stride + 5] = Float.floatToRawIntBits(sprite.getV(template.st[i * 2 + 1] * 16.0D));
        }
        return new BakedQuad(out, base.getTintIndex(), base.getDirection(), sprite, base.isShade());
    }

    /**
     * 预烘焙 compact 的全部子象限 quad（6 面 × 5 变体 × 4 子块 = 120 个单元素列表）。
     *
     * <p>任何一面切不出模板就整体返回 {@code null}（{@code getQuads} 会退回整面选格）——
     * 宁可回到旧观感，也不要在某个面上留洞。
     */
    @SuppressWarnings("unchecked")
    private List<BakedQuad>[] buildCompactSubQuads(TextureAtlasSprite[] sprites) {
        for (Direction face : Direction.values()) {
            if (this.subTemplates[face.ordinal()] == null) {
                return null;
            }
        }
        List<BakedQuad>[] subs = new List[FACE_COUNT * this.variantCount * QUADRANTS];
        for (int v = 0; v < this.variantCount; v++) {
            TextureAtlasSprite sprite = sprites[v] != null ? sprites[v] : this.baseSprite;
            for (Direction face : Direction.values()) {
                BakedQuad base = this.baseQuad[face.ordinal()];
                SubQuad[] tpl = this.subTemplates[face.ordinal()];
                for (int q = 0; q < QUADRANTS; q++) {
                    subs[(face.ordinal() * this.variantCount + v) * QUADRANTS + q] =
                            List.of(bakeSubQuad(base, tpl[q], sprite));
                }
            }
        }
        return subs;
    }

    // ------------------------------------------------------------------
    // ④ 其余一律转发给原模型（不改渲染类型、不改 AO、不改 GUI/粒子/物品变换）
    // ------------------------------------------------------------------

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
        return this.delegate.getQuads(state, side, rand);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return this.delegate.useAmbientOcclusion();
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
    public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() {
        return this.delegate.getTransforms();
    }

    @Override
    public net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() {
        return this.delegate.getOverrides();
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return this.delegate.getRenderTypes(state, rand, data);
    }

    /** 供自检/诊断：本模型包的是哪个方块。 */
    public Block block() {
        return this.block;
    }
}
