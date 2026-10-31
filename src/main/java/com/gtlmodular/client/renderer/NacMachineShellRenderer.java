package com.gtlmodular.client.renderer;

import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtlmodular.GTLModular;
import com.gtlmodular.client.ctm.ConnectedCtmBakedModel;
import com.gtlmodular.client.ctm.CtmConnectivity;
import com.gtlmodular.client.ctm.CtmFaceFrame;
import com.gtlmodular.client.ctm.CtmHullGroup;
import com.gtlmodular.client.ctm.MachineCtmBakedModel;
import com.gtlmodular.client.ctm.NacSlotIndex;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * <b>「NAC 机器外壳罩」——只读方块 + 自己叠一层壳的客户端渲染器</b>。
 *
 * <h2>为什么需要它（上一轮实机结论，当成前提）</h2>
 * 上一轮试图"给机器模型套一层、在 {@code getQuads} 里换外壳 sprite"
 * （{@code client/ctm/MachineCtmBakedModel.java}，开关 {@code CtmClientSetup.MACHINE_HULL_CTM}）。
 * <b>实机日志（20:26:07）证明那条路对机器永远无效</b>：
 * <pre>
 *   CTM: 【delegate 空表】机器 Block{gtlmodular:nanochip_vacuum_input_hatch} 的 delegate
 *        在这次采样里返回了 null/空 quads —— 已原样转发
 * </pre>
 * <b>每一个</b>机器方块在区块渲染时 delegate 都返回空 quads ⇒ 根因：GTCEu 机器不是用烘焙 quad
 * 画的，而是走 LDLib 的 {@code IRenderer} 逐 tile 画
 * （{@code LDLRendererModel$RendererBakedModel} 的 3 参 {@code getQuads} 字节码就是
 * {@code Collections.emptyList(); areturn} —— 见 {@code MachineCtmBakedModel} 的类注释）。
 * ⇒ "换 quad 贴图"这条路对机器<b>永远无效</b>（守卫起效、机器没隐形，但什么也没换）。
 *
 * <p>⇒ 本轮改走<b>"自己画"</b>：<b>不改别人的模型 / 渲染器</b>，只读方块 + 在正确的渲染阶段
 * <b>叠加绘制一层外壳</b>。这与 {@link NanochipPipeRenderer} 是同一个思路（那一套"逐格自己算几何 +
 * 自己画"的先例就在本包），区别是管道换掉整块几何，而这里只<b>盖住</b>原壳。
 *
 * <h2>① 挂哪个阶段：{@code Stage.AFTER_SOLID_BLOCKS}（有依据，不是猜）</h2>
 * <ol>
 *   <li><b>阶段枚举名与语义（Forge 源码原文，{@code forge-1.20.1-47.3.7-sources.jar} →
 *       {@code net/minecraftforge/client/event/RenderLevelStageEvent.java}）</b>：
 *       <pre>
 *   AFTER_SOLID_BLOCKS                 = register("after_solid_blocks",           RenderType.solid());
 *   AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS  = register("after_cutout_mipped_blocks",   RenderType.cutoutMipped());
 *   AFTER_CUTOUT_BLOCKS                = register("after_cutout_blocks",          RenderType.cutout());
 *   AFTER_TRANSLUCENT_BLOCKS           = register("after_translucent_blocks",     RenderType.translucent());
 *       </pre>
 *       注释原文："Use this to render custom block-like geometry into the world."
 *       ⇒ 这几个阶段是<b>按 RenderType 绑定</b>的，选阶段 = 选"我的几何跟哪一层同批"。</li>
 *   <li><b>网格接口机壳与机器各自落在哪一层</b>（逐字节确证）：
 *       <pre>
 *   机壳：assets/gtlmodular/models/block/casings/nanochip_mesh_interface_casing.json
 *           = {"parent":"minecraft:block/cube_all", …}   ← 没有 render_type
 *   机器：MachineBuilder.register 里是 blockstate(NonNullBiConsumer.noop()) ⇒ 没有 blockstate json
 *   两者的渲染层都走 Forge 的默认值：
 *     javap -c IForgeBakedModel.getRenderTypes →
 *         invokestatic ItemBlockRenderTypes.getRenderLayers(BlockState)   // 默认值 = SOLID
 *     javap -c ItemBlockRenderTypes.<clinit> →
 *         BLOCK_RENDER_TYPES.defaultReturnValue(SOLID)                    // 没登记过的方块 ⇒ solid
 *       </pre>
 *       ⇒ <b>机壳与机器都在 {@code RenderType.solid()} 这一层</b>；任务要求的"渲染类型与网格接口机壳的
 *       blockstate 一致"= <b>solid</b> ⇒ 阶段取 {@code AFTER_SOLID_BLOCKS}，正好在两者都画完之后
 *       再叠壳（同层、且在后面 ⇒ 深度比较必然由外移 0.001 的罩子赢）。</li>
 *   <li><b>Embeddium（本包已装）会不会发这个事件</b>：会。
 *       {@code javap -p -c embeddium-0.3.31+mc1.20.1.jar →
 *        me.jellysquid.mods.sodium.mixin.core.render.world.WorldRendererMixin.m_172993_}：
 *       <pre>
 *   … drawChunkLayer(RenderType, …) → enterManagedCode/exitManagedCode
 *   32: RenderType.m_110185_()                                  // = setupRenderState()
 *   61: ForgeHooksClient.dispatchRenderStage(RenderType, …)      // ★ 按 RenderType 派发到同一个 Stage
 *   65: RenderType.m_110188_()                                  // = clearRenderState()
 *       </pre>
 *       而 vanilla Forge 的 {@code LevelRenderer.renderChunkLayer} 在方法尾（偏移 789）也调同一个
 *       {@code dispatchRenderStage(RenderType, …)} ⇒ <b>两条区块渲染路径都会发</b>。</li>
 * </ol>
 *
 * <h2>② 光照与明暗：逐顶点平滑光照 + AO + 亮度上限（★ 2026-09-18 改版）</h2>
 * <pre>
 *   int   light = 4 格平均(getBrightness(SKY/BLOCK)) ，再 min(自己那格的 6 邻居最大值)  // uv2
 *   float bright = getShade(face, true) * AO(4 格遮挡系数 ×0.25)                      // 顶点颜色
 * </pre>
 * <ol>
 *   <li><b>为什么不再是"整面一个 {@code LevelRenderer.getLightColor(level, state, pos.relative(face))}"</b>：
 *       那条路有两个毛病 —— ① {@code javap -c} 显示它首句是
 *       {@code if (state.emissiveRendering(level, pos)) return 15728880;}（自发光 ⇒ 全亮），
 *       且我们当时把<b>机器的 state</b> 传给了<b>面外那一格</b>；② 取"面外那一格"的天光，
 *       当机器埋在暗室里、而某一面朝着露天时那一格天光是 15 ⇒ 罩子整片发亮，与它罩住的机器所在格
 *       （暗）以及周围方块格格不入。玩家 2026-09-18 反馈的正是这个："罩子比周围亮、很突兀"。</li>
 *   <li><b>现在的口径对齐原版 AO</b>（{@code javap -c ModelBlockRenderer$AmbientOcclusionFace.calculate}
 *       读出）：每个顶点取它周围 4 格（面外格 + 两个边格 + 角格），
 *       <b>光照</b>= 4 格 {@code getBrightness(SKY/BLOCK)} 平均；<b>AO</b>= 4 格
 *       {@code getShadeBrightness} 平均（原版不遮挡 1.0 / 遮挡 0.2，逐顶点 {@code ×0.25}）；
 *       两者都按原版那样乘到顶点上（{@code putBulkData(pose, quad, bright[], r,g,b,a, light[], …)}）。</li>
 *   <li><b>"主动调节自身亮度"的关键一句</b>：最后再与<b>机器自己那格</b>的光照取小
 *       （不透明方块自身格子的光不可靠 ⇒ 按原版 {@code RenderChunkRegion} 的做法取 6 邻居最大值）
 *       ⇒ <b>罩子永远不会比它罩住的那格更亮</b>：暗室里跟着变暗、露天下才满亮。</li>
 *   <li><b>为什么还要 {@code getShade(face, true)}</b>：vanilla 的 6 面明暗差
 *       （上/下 1.0、0.5，南北 0.8，东西 0.6）不是烘焙在 quad 里的，而是模型渲染时乘到顶点颜色上的
 *       —— 同一份字节码偏移 1969：{@code level.getShade(Direction, boolean)} →
 *       乘进 4 个顶点的 brightness。少了它，罩子会像"贴了一张发光纸"（顶/底面尤其明显）。</li>
 * </ol>
 *
 * <h2>③ 正脸判定：机器自己的朝向优先，方块状态属性兜底（两条都给依据）</h2>
 * <ol>
 *   <li><b>主判据 = {@code MetaMachine.getFrontFacing()}</b>（{@code javap -p MetaMachine} 确认存在）。
 *       用它是因为"渲染器画正脸用的就是它"：{@code MachineRenderer.renderModel} 拿到的
 *       {@code frontFacing} 就是这个值 ⇒ 我们与真正的正脸必然一致，不受模型旋转 / {@code upwards_facing}
 *       这些因素的影响。</li>
 *   <li><b>兜底 = 方块状态的朝向属性</b>：{@code javap -c MetaMachineBlock.m_7926_}
 *       （= {@code createBlockStateDefinition}）逐条：
 *       <pre>
 *   addProperty(BlockProperties.SERVER_TICK);
 *   RotationState state = RotationState.get();
 *   if (state != RotationState.NONE) addProperty(state.property);            // ★ 朝向属性
 *   if (definition instanceof MultiblockMachineDefinition mbd && mbd.isAllowExtendedFacing())
 *       addProperty(IMachineBlock.UPWARDS_FACING_PROPERTY);
 *       </pre>
 *       而 {@code RotationState.property} 是 {@code public final DirectionProperty}
 *       ⇒ 经 {@code IMachineBlock.getRotationState().property} + {@code state.hasProperty(..)} 读，
 *       拿到的就是"这台机器实际站在哪个朝向"。
 *       （{@code UPWARDS_FACING_PROPERTY} 只被"允许扩展朝向的多方块控制器"用来表达上下翻转，
 *       我们<b>刻意不参与</b>：那种机器的正脸仍由 {@code getFrontFacing()} 表达，走主判据即可。）</li>
 *   <li>两条都拿不到（理论上的第三方奇怪方块）⇒ <b>整格不罩</b>（计入"排除"）。
 *       取舍写清楚：宁可少罩一格，也<b>绝不</b>把认不出正脸的机器整块糊死 —— 正脸上是
 *       机器的身份识别面（overlay），被罩住比不罩严重得多。</li>
 *   <li><b>为什么正脸必须跳过</b>：机器的正脸 = {@code 外壳 + overlay}，两层<b>共面</b>画在方块表面上；
 *       而我们的罩子外移 0.001 且在 solid 层里画得更晚 ⇒ 若连正脸一起罩，overlay 会被深度测试挡掉
 *       （= 机器失去正脸图案）。跳过正脸后 overlay 照旧可见，其余 5 面则由罩子与周围机壳连成一片。</li>
 * </ol>
 *
 * <h2>④ 47 格 CTM 的选格：与机壳那条**同一套**几何与判据</h2>
 * <ul>
 *   <li><b>面内坐标帧</b>：<b>不写 6 面表</b>，而是拿<b>机壳方块自己的烘焙 quad</b>去
 *       {@link CtmFaceFrame#derive} ⇒ 罩子的 u/v 方向与机壳（{@code cube_all}）逐字节一致
 *       ⇒ 罩子与相邻机壳的 47 格接缝必然对得上（这正是玻璃那轮"tile 2/3 选反 ⇒ 线画在接缝上"
 *       的教训：判据必须来自同一处，不能各自约定）。</li>
 *   <li><b>几何</b>：直接沿用机壳那张 {@code cube_all} 的面 quad（4 个顶点位置 + UV 全在 sprite 四角），
 *       只做两件事：① 沿该面法线<b>外移 {@link #SHELL_OFFSET}</b>（避免与机器原壳 z-fighting）；
 *       ② 用 {@link ConnectedCtmBakedModel#retexture} 换成选中的变体 sprite
 *       （与机壳 / 机器面共用<b>同一份</b>重贴图数学）。</li>
 *   <li><b>邻居判据</b>：{@link CtmHullGroup#rendersMeshCasingTile} —— 与机壳方块那一条
 *       <b>完全相同</b>（机壳本体 ∪ 白名单 14 台 ∪ 任何进了成形 NAC 仓位的机器）⇒ 不会出现
 *       "机壳认为连、罩子认为不连"的半条缝。</li>
 *   <li><b>掩码</b>：8 位（4 正交 + 4 对角），{@link CtmConnectivity#singletonIndex} 剪枝与
 *       {@link ConnectedCtmBakedModel} 里非 compact 那条一模一样。</li>
 * </ul>
 *
 * <h2>⑤ 性能：候选来源 / 剔除 / 上限</h2>
 * <ol>
 *   <li><b>候选</b>不靠扫世界：{@link NacSlotIndex#snapshot()}（缓存里的仓位并集，纯内存）+
 *       节流的 {@link NacSlotIndex#seedNearby}（扫相机附近几<b>区块</b>的<b>方块实体表</b>，
 *       {@code getChunk(cx,cz,false)} 明确不加载区块）。
 *       ⇒ 每帧只对"已知是 NAC 仓位的那几格"做距离 / 视锥 / 逐格复核，而不是盲扫附近 4096 格。</li>
 *   <li><b>剔除</b>：距离上限 {@link #MAX_DISTANCE}（相机中心到格中心，平方比较，零开方）+
 *       视锥近似 —— 用事件自带的 {@code Frustum.isVisible(AABB)}（整格 1×1×1 立方体）。
 *       取舍：<b>不做逐面背面剔除</b>（罩子是 5 个面的小方壳，逐面算"这个面朝不朝相机"要多 6 次点积，
 *       而 {@code MAX_BLOCKS_PER_FRAME} 已经把总量摁住了）；背面剔除交给 GPU 的 depth test。</li>
 *   <li><b>上限</b>：{@link #MAX_BLOCKS_PER_FRAME}（超过就只画最近的那些，按距离升序截断）
 *       ⇒ 最坏情况每帧 5 × 上限 个 quad。</li>
 * </ol>
 *
 * <h2>⑥ 开关与排除名单</h2>
 * <ul>
 *   <li>{@link #ENABLED}（默认 <b>true</b>）= 本渲染器的总开关；关掉 ⇒ 一行几何都不画。</li>
 *   <li>{@link #EXCLUDED_BLOCKS}（默认<b>空</b>）= "故意不罩"的方块注册名集合；
 *       扩法见它自己的注释。</li>
 * </ul>
 *
 * <h2>⑦ 安全纪律（照抄本项目既有口径）</h2>
 * 渲染路径上<b>任何</b>异常都被本类吞掉并打一条限流 WARN —— 绝不从渲染线程抛出去；
 * 认不出 / 拿不到数据 / 几何解析失败 ⇒ <b>什么都不画</b>（外观 = 现状），绝不猜。
 * 本类的<b>静态初始化只有常量</b>（无注册表查询、无 RenderType 取用、无图集访问）——
 * {@code @Mod.EventBusSubscriber} 会在模组构造期就让 JVM 加载本类并跑 {@code <clinit>}，
 * 那时碰这些东西等于把风险提前到"玩家进不去游戏"（参考 {@code check_registration_path.js} 的 P0 教训）。
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, value = Dist.CLIENT)
public final class NacMachineShellRenderer {

    // ------------------------------------------------------------------
    // 开关
    // ------------------------------------------------------------------

    /**
     * ★ <b>本渲染器的总开关</b>（默认开）。
     *
     * <p>关掉（{@code false}）的语义 = <b>与没有这个功能完全等价</b>：事件回调第一句就返回，
     * 一行几何都不生成（连候选枚举都不做）。它与 {@code CtmClientSetup.MACHINE_HULL_CTM}
     * 是<b>两件事</b>：
     * <ul>
     *   <li>{@code MACHINE_HULL_CTM}（现默认 <b>false</b>）= 旧的"换 quad 贴图"那条路，
     *       已被实机证明对机器无效而关掉（保留代码，未来某条渲染路径给了 quad 还能用）；</li>
     *   <li>{@code ENABLED}（默认 true）= 本渲染器这条"自己叠壳"的路。</li>
     * </ul>
     */
    public static final boolean ENABLED = true;

    /**
     * ★ <b>"故意不罩"的排除名单</b>（方块<b>注册名</b>，默认<b>空</b>）。
     *
     * <h3>什么时候该往这里加</h3>
     * 若某台机器在<b>侧面</b>（正脸之外的那 5 面之一）有自己独立的图案 / 叠加条 / 发光层，
     * 而"外移 0.001 的罩子"把它盖住了（实机表现：图案消失、只剩接口机壳花纹），
     * 就把它的注册名加到这里。
     *
     * <h3>怎么加（两种粒度）</h3>
     * <pre>
     *   // ① 这台机器整个不罩（最常用）：
     *   public static final Set&lt;String&gt; EXCLUDED_BLOCKS = Set.of(
     *           "gtlmodular:some_machine_with_side_art",
     *           "othermod:special_hatch");
     *   // ② 只想排除"某个面"：把它的注册名加进 {@link #EXCLUDED_FACES} 里对应的集合（键相同）。
     * </pre>
     * <p>名单<b>默认空</b>是有意的：先按"全罩"上线，实机看到哪台不对再逐个加 —— 这样"漏罩/多罩"
     * 都能从日志里的"排除 X 格"读出来（见 {@link #reportStats}）。
     */
    public static final Set<String> EXCLUDED_BLOCKS = Set.of();

    /**
     * ★ 更细的排除：<b>注册名 → 不罩的面</b>（默认空）。
     *
     * <p>用途：某台机器只有<b>顶面</b>有独立图案 ⇒ 这里写 {@code Map.of("mod:block", Set.of(Direction.UP))}，
     * 那样只跳过顶面，另外 4 面照罩。这里用 {@code Set<String>} 形式的名字
     * （{@code "mod:block=up,north"}）而不是 {@code Map}，是为了让"名单"保持**常量、可在静态初始化里构造**
     * （{@code Map.of} 也可以，但字符串表更好自检、也更好从日志里抄）。
     * 空集 = 不额外排除任何面。
     */
    public static final Set<String> EXCLUDED_FACES = Set.of();

    // ------------------------------------------------------------------
    // 几何 / 剔除常量
    // ------------------------------------------------------------------

    /**
     * 罩子沿面法线<b>外移</b>的量（格）。
     *
     * <p>机器原壳就在方块边界上 ⇒ 与它<b>共面</b>会 z-fighting。0.001 格与 GTCEu 自己的
     * {@code PipeModel.bakeQuads} 用的 {@code inflate(-0.001)} 同一量级
     * （{@link NanochipPipeRenderer} 的叠加条也用它），肉眼不可见、深度上必然赢。
     *
     * <p><b>为什么 0.001 就够了（不只是"看起来小"）</b>：
     * <ol>
     *   <li>外移方向 = 该面的外法线 ⇒ 罩子的深度<b>恒不比原壳更远</b>；</li>
     *   <li>{@code RenderType.solid()} 的深度函数是 <b>LEQUAL</b>，而我们在
     *       {@code AFTER_SOLID_BLOCKS} 里<b>后画</b> ⇒ 即便远处深度精度不够、两者被量化成同一个值，
     *       罩子仍然通过测试并覆盖原壳 ⇒ 结果是"罩子稳定赢"，而不是"闪烁"。</li>
     * </ol>
     * <p>★ 已知的一个观感副作用（写在这里便于实机对号入座）：若紧邻的方块是<b>半透明</b>的
     * （例如 NAC 的复合体玻璃），它朝向机器的那一层内表面正好在方块边界上、而罩子在边界往里 0.001
     * ⇒ 那一层内表面会被罩子挡住（玻璃看起来少一点内部染色）；玻璃朝外的面不受影响。
     */
    public static final float SHELL_OFFSET = 0.001F;

    /** 距离剔除上限（格）。超过它的仓位连方块状态都不读。 */
    public static final double MAX_DISTANCE = 96.0D;

    /** 每帧最多画几格（按距离升序截断）。最坏每帧 5 × 这个数 个 quad。 */
    public static final int MAX_BLOCKS_PER_FRAME = 256;

    /** 播种（{@link NacSlotIndex#seedNearby}）的<b>区块</b>半径。 */
    public static final int SEED_CHUNK_RADIUS = 4;

    /** 播种的最小间隔（帧）。相机换区块时立即补一次，其余情况按这个节流。 */
    public static final int SEED_EVERY_FRAMES = 20;

    /** 统计日志的最小间隔（帧）。数字没变就不重复打。 */
    public static final int STATS_EVERY_FRAMES = 600;

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int FACE_COUNT = 6;

    /** 正交 4 位在 position 位序里的顺序（TOP / RIGHT / BOTTOM / LEFT），与机壳那条同款。 */
    private static final int[] RING_ORTHO = { 0, 2, 4, 6 };
    /** 对角 4 位（TOP_RIGHT / BOTTOM_RIGHT / BOTTOM_LEFT / TOP_LEFT）。 */
    private static final int[] RING_DIAG = { 1, 3, 5, 7 };

    /**
     * 兜底朝向属性的查找顺序（只在"没有机器实例、也不是 {@code IMachineBlock} 的 rotationState"
     * 时才用）。只用原版 {@code BlockStateProperties} 里最常见的两个。
     */
    private static final DirectionProperty[] FALLBACK_FACING = {
            BlockStateProperties.FACING, BlockStateProperties.HORIZONTAL_FACING,
    };

    /** 候选排序用的打包位宽（index 占低 12 位 ⇒ 一次最多排 4096 个候选）。 */
    private static final int CANDIDATE_BITS = 12;
    private static final int CANDIDATE_MASK = (1 << CANDIDATE_BITS) - 1;
    private static final int MAX_CANDIDATES = 1 << CANDIDATE_BITS;
    /** 距离平方的量化倍率（配合 {@link #MAX_DISTANCE} 保证不会溢出打包位）。 */
    private static final double DIST_QUANT = 16.0D;

    // ------------------------------------------------------------------
    // 渲染期状态（全部懒初始化；<clinit> 里一个都不碰）
    // ------------------------------------------------------------------

    /** 6 个面 × 47 格变体，展开好的罩子 quad（下标 = {@code faceOrdinal * 47 + variant}）。 */
    private static volatile BakedQuad[][] variantQuads;
    /** 面内坐标帧（来自机壳自己那张 {@code cube_all} 的 quad）—— 与机壳的 47 格**同一套**。 */
    private static volatile CtmFaceFrame frame;

    private static volatile boolean geometryTried;
    private static volatile String geometryFailure;
    private static final java.util.concurrent.atomic.AtomicInteger GEOMETRY_WARN = new java.util.concurrent.atomic.AtomicInteger(2);

    /** 相机换区块检测。 */
    private static int lastChunkX = Integer.MIN_VALUE;
    private static int lastChunkZ = Integer.MIN_VALUE;
    private static int seedCountdown;

    /** 统计（每帧刷新，日志按 {@link #STATS_EVERY_FRAMES} 节流）。 */
    private static int lastCandidates = -1;
    private static int lastDrawn = -1;
    private static int lastExcluded = -1;
    private static int framesSinceStats;
    private static boolean statsLogged;

    /** 本渲染器自己的异常限流（渲染路径绝不刷屏）。 */
    private static final java.util.concurrent.atomic.AtomicInteger FAILURE_WARN = new java.util.concurrent.atomic.AtomicInteger(3);

    /** 每个"原因"只打一次的诊断（键 = 原因短名）。 */
    private static final Set<String> ONCE = ConcurrentHashMap.newKeySet();

    private NacMachineShellRenderer() {
    }

    // ------------------------------------------------------------------
    // 资源重载边界
    // ------------------------------------------------------------------

    /**
     * ★ <b>资源重载 / 换世界边界</b>：作废所有派生态（quad 表 + 帧 + 统计），
     * 由 {@code CtmClientSetup.onModifyBakingResult} 调用（与 {@code MachineCtmBakedModel}、
     * {@code NacSlotIndex} 的复位同一处，顺序无所谓：三者互不依赖）。
     *
     * <p>为什么必须清：quad 里嵌的是<b>旧图集</b>的 sprite（换资源包 / F3+T 之后全部作废），
     * 帧来自旧烘焙模型的几何。不清 ⇒ 换资源包后罩子会花屏或干脆画不出来。
     */
    public static void reset() {
        variantQuads = null;
        frame = null;
        geometryTried = false;
        geometryFailure = null;
        lastGeometryAttemptAt = 0L;
        GEOMETRY_WARN.set(2);
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
        seedCountdown = 0;
        lastCandidates = -1;
        lastDrawn = -1;
        lastExcluded = -1;
        framesSinceStats = 0;
        statsLogged = false;
        ONCE.clear();
        if (ENABLED) {
            // ★ 任务要求"在 ModifyBakingResult（或首次使用）打一条"⇒ 这里保证资源重载后必然有一条，
            //   即使这一轮玩家一直看不到 NAC（那样首次使用那条就不会触发）。
            GTLModular.LOGGER.info("[gtlmodular] NAC 机器外壳罩：已复位（开关 ENABLED={}；旧 quad 表 / 面内帧 / "
                    + "统计已作废，下一帧重新解析）。旧「换 quad 贴图」那条路 MACHINE_HULL_CTM={}（实机证明对机器无效）。",
                    ENABLED, com.gtlmodular.client.ctm.CtmClientSetup.MACHINE_HULL_CTM);
        }
    }

    // ------------------------------------------------------------------
    // 事件入口
    // ------------------------------------------------------------------

    /**
     * ★ <b>唯一入口</b>：Forge 主总线上的 {@link RenderLevelStageEvent}。
     *
     * <p>只处理 {@link RenderLevelStageEvent.Stage#AFTER_SOLID_BLOCKS}（依据见类注释 ①）。
     * 整个方法体包在 try/catch 里：渲染线程上<b>绝不</b>抛异常。
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!ENABLED) {
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }
        try {
            renderShells(event);
        } catch (Throwable failure) {
            warnFailure(failure);
        }
    }

    private static void renderShells(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Camera camera = event.getCamera();
        if (level == null || camera == null || event.getPoseStack() == null) {
            return;
        }
        Vec3 eye = camera.getPosition();
        BlockPos eyePos = BlockPos.containing(eye);

        // ① 候选取自"仓位索引"（纯内存并集 + 有界播种），不盲扫方块
        seedIfDue(level, eyePos);
        Set<Long> slots = NacSlotIndex.snapshot();
        if (slots.isEmpty()) {
            reportStats(0, 0, 0);
            return;
        }

        // ② 几何 / 变体 sprite 就绪性（各自只报一次，绝不刷屏）
        if (!ensureGeometry()) {
            return;
        }
        TextureAtlasSprite[] variants = MachineCtmBakedModel.resolveSharedVariants(
                minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS));
        if (variants == null) {
            reportOnce("variants", "[gtlmodular] NAC 机器外壳罩：47 格变体 sprite 还没进图集 —— 本帧不画"
                    + "（外观 = 现状）；若一直这样，看 CtmClientSetup 的「尚未就绪」那条日志。");
            return;
        }
        CtmFaceFrame frames = frame;
        if (frames == null || !frames.complete()) {
            reportOnce("frame", "[gtlmodular] NAC 机器外壳罩：机壳的面内坐标帧不完整 —— 本帧不画"
                    + "（宁可保持现状，也不要把 47 格的接缝画到错的边上）。");
            return;
        }

        // ③ 距离 + 视锥剔除 + 排序（上限截断）
        double maxDistanceSq = MAX_DISTANCE * MAX_DISTANCE;
        long[] inRange = new long[Math.min(slots.size(), MAX_CANDIDATES)];
        double[] distanceSq = new double[inRange.length];
        int count = 0;
        for (long packed : slots) {
            if (count >= inRange.length) {
                break; // 候选数量异常大 ⇒ 只看前 MAX_CANDIDATES 个（正常整合包里远达不到）
            }
            BlockPos pos = BlockPos.of(packed);
            double dx = pos.getX() + 0.5D - eye.x;
            double dy = pos.getY() + 0.5D - eye.y;
            double dz = pos.getZ() + 0.5D - eye.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > maxDistanceSq) {
                continue; // 距离剔除：连 getBlockState 都不做
            }
            var frustum = event.getFrustum();
            if (frustum != null && !frustum.isVisible(new AABB(pos))) {
                continue; // 视锥剔除（整格 1×1×1；不做逐面背面剔除，见类注释 ⑤）
            }
            inRange[count] = packed;
            distanceSq[count] = distSq;
            count++;
        }

        int drawn = 0;
        int excluded = 0;
        if (count > 0) {
            VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.solid());
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            // ★ 世界 → 视图：事件给的 PoseStack 只含"相机旋转"（依据见类注释 ① 的
            //   GameRenderer.renderLevel 字节码：局部 4 号 PoseStack 只 mulPose 了 XRot / YRot+180），
            //   而 chunk 几何靠 CHUNK_OFFSET uniform 平移 ⇒ 我们在这里做 T(-camera) 即得世界系。
            poseStack.translate(-eye.x, -eye.y, -eye.z);
            int limit = Math.min(count, MAX_BLOCKS_PER_FRAME);
            long[] order = count > limit ? nearestFirst(inRange, distanceSq, count) : null;
            for (int i = 0; i < limit; i++) {
                BlockPos pos = BlockPos.of(order == null ? inRange[i] : order[i]);
                BlockState state = level.getBlockState(pos);
                Block block = state.getBlock();
                if (!(block instanceof IMachineBlock)) {
                    continue; // 这一格已经不是机器了（索引是上一帧的/缓存过期）⇒ 不画
                }
                if (!NacSlotIndex.isNacSlot(level, pos)) {
                    continue; // ★ 逐格复核（候选 ≠ 一定是仓位；同步值可能刚变）
                }
                ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
                String blockId = id == null ? "" : id.toString();
                if (EXCLUDED_BLOCKS.contains(blockId)) {
                    excluded++; // 排除名单①：整台不罩
                    continue;
                }
                Direction front = frontFace(level, pos, state, block);
                if (front == null) {
                    excluded++; // 认不出正脸 ⇒ 整格不罩（见类注释 ③）
                    continue;
                }
                Set<Direction> skipFaces = excludedFaces(blockId);
                if (drawOne(consumer, poseStack, level, pos, state, front, skipFaces, variants, frames)) {
                    drawn++;
                } else {
                    excluded++;
                }
            }
            poseStack.popPose();
            minecraft.renderBuffers().bufferSource().endBatch(RenderType.solid());
        }
        reportStats(count, drawn, excluded);
    }

    // ------------------------------------------------------------------
    // 候选来源
    // ------------------------------------------------------------------

    /** 节流播种：相机换区块立刻补一次，其余按 {@link #SEED_EVERY_FRAMES} 帧一次。 */
    private static void seedIfDue(ClientLevel level, BlockPos eyePos) {
        int cx = eyePos.getX() >> 4;
        int cz = eyePos.getZ() >> 4;
        boolean chunkChanged = cx != lastChunkX || cz != lastChunkZ;
        if (!chunkChanged && --seedCountdown > 0) {
            return;
        }
        lastChunkX = cx;
        lastChunkZ = cz;
        seedCountdown = SEED_EVERY_FRAMES;
        NacSlotIndex.seedNearby(level, eyePos, SEED_CHUNK_RADIUS);
    }

    /**
     * 距离升序取前 N 个候选（只需要"最近的若干个"，所以不做全排序）。
     *
     * <p>做法：把 {@code (量化距离² << 12) | index} 打进 {@code long} 再 {@code Arrays.sort}
     * —— 一次排序、零装箱、零对象分配。量化倍率 {@link #DIST_QUANT} 与 {@link #MAX_DISTANCE}
     * 保证高位不会溢出（96² × 16 ≈ 1.5e5 < 2^18，腾出 46 位）。
     */
    private static long[] nearestFirst(long[] packedPositions, double[] distanceSq, int count) {
        long[] keys = new long[count];
        for (int i = 0; i < count; i++) {
            long quantized = (long) Math.min(distanceSq[i] * DIST_QUANT, (double) Integer.MAX_VALUE);
            keys[i] = (quantized << CANDIDATE_BITS) | (i & CANDIDATE_MASK);
        }
        Arrays.sort(keys);
        long[] sorted = new long[Math.min(count, MAX_BLOCKS_PER_FRAME)];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = packedPositions[(int) (keys[i] & CANDIDATE_MASK)];
        }
        return sorted;
    }

    // ------------------------------------------------------------------
    // 逐格绘制
    // ------------------------------------------------------------------

    /**
     * 画一格的罩子（正脸之外的面）。
     *
     * @return {@code true} = 至少画了一个面；{@code false} = 这一格一个面都没画（计入"排除"）
     */
    private static boolean drawOne(VertexConsumer consumer, PoseStack poseStack, Level level, BlockPos pos,
                                   BlockState state, Direction front, Set<Direction> skipFaces,
                                   TextureAtlasSprite[] variants, CtmFaceFrame frames) {
        BakedQuad[][] quads = variantQuads;
        if (quads == null) {
            return false;
        }
        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        PoseStack.Pose pose = poseStack.last();
        boolean any = false;
        for (Direction face : DIRECTIONS) {
            // ★ 正脸不画（见类注释 ③）：正脸 = 外壳 + overlay 共面，罩住了 overlay 就没了。
            if (face == front || skipFaces.contains(face) || frames.rightOf(face) == null) {
                continue;
            }
            int mask = computeMask(level, pos, face, frames);
            int index = CtmConnectivity.ctmIndex(mask);
            TextureAtlasSprite sprite = variants[index];
            BakedQuad quad = quads[face.ordinal()][index];
            if (sprite == null || quad == null) {
                continue;
            }
            float shade = faceShade(level, face);
            vertexLighting(level, pos, face, frames, quad, shade, VERTEX_LIGHT, VERTEX_BRIGHT);
            consumer.putBulkData(pose, quad, VERTEX_BRIGHT, 1.0F, 1.0F, 1.0F, 1.0F,
                    VERTEX_LIGHT, OverlayTexture.NO_OVERLAY, false);
            any = true;
        }
        poseStack.popPose();
        return any;
    }

    /**
     * ★ 2026-09-18（玩家反馈「罩子比周围亮、很突兀，让它学会主动调节自身亮度」）：
     * <b>逐顶点平滑光照 + AO，并以"机器自己所在格的光照"为上限</b>。
     *
     * <p>旧做法是「整面一个 {@code LevelRenderer.getLightColor(level, state, pos.relative(face))}」，
     * 它有两个毛病：
     * <ol>
     *   <li><b>取的是"面外那一格"</b>——机器埋在暗室里、而罩子某一面朝着露天时，那一格天光是 15，
     *       罩子就整片发亮；可它<u>罩住的机器所在的格子</u>是暗的 ⇒ 与周围格格不入。</li>
     *   <li><b>没有 AO、没有逐顶点过渡</b>——整面一个常量亮度，跟原版方块（有 AO 渐暗）并排就显得平板、偏亮。</li>
     * </ol>
     *
     * <p>现在改为（对齐原版 {@code ModelBlockRenderer$AmbientOcclusionFace} 的口径，常量已用
     * {@code javap -c} 读出）：每个顶点取<b>它周围那 4 格</b>（面外格 + 两个边格 + 角格）：
     * <ul>
     *   <li><b>光照</b>= 4 格的 {@code getBrightness(SKY/BLOCK)} 平均，再与原版一样
     *       <b>截到"机器自己那格"的光照</b>（不透明方块自己的光不可靠 ⇒ 按原版 {@code RenderChunkRegion}
     *       的做法取 6 邻居的最大值代替）⇒ <b>罩子永远不会比它罩住的那格更亮</b>；</li>
     *   <li><b>AO</b>= 4 格遮挡系数平均（原版 {@code getShadeBrightness}：不遮挡 1.0 / 遮挡 0.2，
     *       逐顶点 {@code ×0.25}），再乘原版的方向性明暗 {@code getShade}</li>
     * </ul>
     * ⇒ 暗室里变暗、洞口与凹角处自然压暗，不再突兀。
     */
    private static void vertexLighting(Level level, BlockPos pos, Direction face, CtmFaceFrame frames,
                                       BakedQuad quad, float shade, int[] lights, float[] bright) {
        Direction right = frames.rightOf(face);
        Direction up = frames.upOf(face);
        if (right == null || up == null) {
            // 认不出面内帧（理论上不会走到）：退回"整面自己那格的光"。
            int sky = 0, blk = 0;
            for (Direction d : DIRECTIONS) {
                BlockPos n = pos.relative(d);
                sky = Math.max(sky, level.getBrightness(LightLayer.SKY, n));
                blk = Math.max(blk, level.getBrightness(LightLayer.BLOCK, n));
            }
            int packed = LightTexture.pack(blk, sky);
            for (int i = 0; i < 4; i++) {
                lights[i] = packed;
                bright[i] = shade;
            }
            return;
        }
        // 机器自己那格的光照上限：它是不透明方块，自身格子的光不可靠 ⇒ 取 6 邻居最大值（原版同法）。
        int ownSky = 0, ownBlock = 0;
        for (Direction d : DIRECTIONS) {
            BlockPos n = pos.relative(d);
            ownSky = Math.max(ownSky, level.getBrightness(LightLayer.SKY, n));
            ownBlock = Math.max(ownBlock, level.getBrightness(LightLayer.BLOCK, n));
        }
        final int[] v = quad.getVertices();
        final BlockPos outside = pos.relative(face);
        for (int vi = 0; vi < 4; vi++) {
            // BakedQuad 的顶点坐标是 0..1 的方块局部坐标（DefaultVertexFormat.BLOCK，stride 8）。
            float dx = Float.intBitsToFloat(v[vi * VERTEX_STRIDE]) - 0.5F;
            float dy = Float.intBitsToFloat(v[vi * VERTEX_STRIDE + 1]) - 0.5F;
            float dz = Float.intBitsToFloat(v[vi * VERTEX_STRIDE + 2]) - 0.5F;
            int sr = (dx * right.getStepX() + dy * right.getStepY() + dz * right.getStepZ()) >= 0.0F ? 1 : -1;
            int su = (dx * up.getStepX() + dy * up.getStepY() + dz * up.getStepZ()) >= 0.0F ? 1 : -1;
            BlockPos sideRight = outside.relative(right, sr);
            BlockPos sideUp = outside.relative(up, su);
            BlockPos corner = sideRight.relative(up, su);
            int sky = 0, blk = 0;
            float occlusion = 0.0F;
            for (int k = 0; k < 4; k++) {
                BlockPos p = switch (k) {
                    case 0 -> outside;
                    case 1 -> sideRight;
                    case 2 -> sideUp;
                    default -> corner;
                };
                sky += level.getBrightness(LightLayer.SKY, p);
                blk += level.getBrightness(LightLayer.BLOCK, p);
                occlusion += level.getBlockState(p).isSolidRender(level, p) ? OCCLUDED_SHADE : 1.0F;
            }
            sky /= 4;
            blk /= 4;
            lights[vi] = LightTexture.pack(Math.min(blk, ownBlock), Math.min(sky, ownSky));
            bright[vi] = shade * (occlusion * 0.25F);
        }
    }

    /** 顶点步长：{@code DefaultVertexFormat.BLOCK} 每个顶点 8 个 int。 */
    private static final int VERTEX_STRIDE = 8;
    /** 原版 {@code getShadeBrightness} 对"遮挡格"的取值（javap 读出的 0.2F）。 */
    private static final float OCCLUDED_SHADE = 0.2F;
    /** 逐顶点光照/明暗的复用缓冲（渲染线程单线程使用，避免每格每帧分配）。 */
    private static final int[] VERTEX_LIGHT = new int[4];
    private static final float[] VERTEX_BRIGHT = new float[4];

    /** ★ <b>该面的方向性明暗</b>：vanilla 的 {@code getShade}（上/下 1.0/0.5、南北 0.8、东西 0.6）。 */
    private static float faceShade(Level level, Direction face) {
        return level.getShade(face, true);
    }

    /**
     * 8 位邻居掩码 —— 与 {@code ConnectedCtmBakedModel.computeMask}（非 compact 那条）
     * <b>逐条同源</b>：位序 = {@link CtmConnectivity#P_TOP} 等，邻居判据 = {@link CtmHullGroup#rendersMeshCasingTile}。
     */
    private static int computeMask(Level level, BlockPos pos, Direction face, CtmFaceFrame frames) {
        Direction rightDir = frames.rightOf(face);
        Direction upDir = frames.upOf(face);
        if (rightDir == null || upDir == null) {
            return 0;
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

    /** position {@code p} 处的邻居是不是"外壳同为接口机壳"的格子（NH 的 {@code connect=tile}）。 */
    private static boolean connects(Level level, BlockPos pos, Direction rightDir, Direction upDir, int p) {
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
    // 正脸 / 排除
    // ------------------------------------------------------------------

    /**
     * 这一格机器的<b>正脸</b>（= 不罩的那一面）；{@code null} = 认不出 ⇒ 整格不罩。
     *
     * <p>顺序（依据见类注释 ③）：① {@code MetaMachine.getFrontFacing()}（渲染器画正脸用的就是它）
     * → ② {@code IMachineBlock.getRotationState().property} 读方块状态 → ③ 原版常见朝向属性兜底。
     */
    private static Direction frontFace(Level level, BlockPos pos, BlockState state, Block block) {
        try {
            MetaMachine machine = MetaMachine.getMachine(level, pos);
            if (machine != null) {
                Direction facing = machine.getFrontFacing();
                if (facing != null) {
                    return facing;
                }
            }
        } catch (Throwable ignored) {
            // 拿不到机器实例不是致命问题：下面还有方块状态这条兜底；渲染路径绝不抛。
        }
        if (block instanceof IMachineBlock machineBlock) {
            RotationState rotation = machineBlock.getRotationState();
            DirectionProperty property = rotation == null ? null : rotation.property;
            if (property != null && state.hasProperty(property)) {
                return state.getValue(property);
            }
        }
        for (DirectionProperty property : FALLBACK_FACING) {
            if (state.hasProperty(property)) {
                return state.getValue(property);
            }
        }
        return null;
    }

    /** 该方块"额外不罩的面"（{@link #EXCLUDED_FACES} 的解析，格式 {@code "mod:block=up,north"}）。 */
    private static Set<Direction> excludedFaces(String blockId) {
        if (EXCLUDED_FACES.isEmpty() || blockId.isEmpty()) {
            return Set.of();
        }
        for (String entry : EXCLUDED_FACES) {
            int eq = entry.indexOf('=');
            if (eq <= 0 || !entry.substring(0, eq).equals(blockId)) {
                continue;
            }
            Set<Direction> faces = new LinkedHashSet<>();
            for (String name : entry.substring(eq + 1).split(",")) {
                for (Direction face : DIRECTIONS) {
                    if (face.getName().equalsIgnoreCase(name.trim())) {
                        faces.add(face);
                    }
                }
            }
            return faces;
        }
        return Set.of();
    }

    // ------------------------------------------------------------------
    // 几何解析（懒；资源重载后重来）
    // ------------------------------------------------------------------

    /**
     * 解析罩子的几何：<b>借机壳自己那张 {@code cube_all} 的 6 个面 quad</b>（几何 + UV 约定 + 面内帧
     * 全部与机壳同源），外移 {@link #SHELL_OFFSET}，再对 47 个变体各 {@code retexture} 一次。
     *
     * @return {@code true} = {@link #variantQuads} 与 {@link #frame} 都就绪
     */
    /** 几何重试的最小间隔（毫秒）：图集没就绪时别每帧重推 6 个面的几何。 */
    private static final long GEOMETRY_RETRY_MILLIS = 250L;
    private static volatile long lastGeometryAttemptAt;

    private static boolean ensureGeometry() {
        if (variantQuads != null && frame != null) {
            return true;
        }
        if (geometryTried && geometryFailure != null) {
            return false; // 已经失败过（各自只报一次）；下一次资源重载会重来
        }
        long now = System.currentTimeMillis();
        if (geometryTried && now - lastGeometryAttemptAt < GEOMETRY_RETRY_MILLIS) {
            return false; // 节流重试（图集缝合通常晚于模型烘焙几秒，见 CtmClientSetup 的实机记录）
        }
        lastGeometryAttemptAt = now;
        geometryTried = true;
        try {
            Block casing = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation(GTLModular.MOD_ID, CtmHullGroup.MESH_CASING_ID));
            if (casing == null) {
                return failGeometry("机壳方块 " + CtmHullGroup.MESH_CASING_ID + " 取不到（注册名漂移？）");
            }
            BlockState casingState = casing.defaultBlockState();
            Minecraft minecraft = Minecraft.getInstance();
            BakedModel casingModel = minecraft.getBlockRenderer().getBlockModelShaper().getBlockModel(casingState);
            // ★ 面内坐标帧直接来自机壳模型 ⇒ 与机壳的 47 格选格**同一套约定**（类注释 ④）
            CtmFaceFrame derived = CtmFaceFrame.derive(casingModel, casingState);
            if (!derived.complete()) {
                return failGeometry("机壳模型有面推不出面内坐标帧（几何不满足判据）");
            }
            BakedQuad[] base = new BakedQuad[FACE_COUNT];
            for (Direction face : DIRECTIONS) {
                List<BakedQuad> quads = casingModel.getQuads(casingState, face, RandomSource.create(42L));
                if (quads.isEmpty()) {
                    return failGeometry("机壳模型的面 " + face + " 没有 quad");
                }
                // ★ 机壳是 cube_all ⇒ 每面恰一个整面 quad；直接沿用它的几何 + UV 约定（类注释 ④）
                base[face.ordinal()] = offset(quads.get(0), face);
            }
            TextureAtlasSprite[] variants = MachineCtmBakedModel.resolveSharedVariants(
                    minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS));
            if (variants == null) {
                geometryTried = false; // 图集还没就绪：留着重试（不走 failure 分支）
                return false;
            }
            // ★ 图集没齐时**不锁死**：resolveSharedVariants 会用第 0 格兜底，而本表一旦展开就
            //   嵌着 sprite 引用、整局不再重建 ⇒ 那时会把"兜底的第 0 格"固化一整局。
            //   这里要求"47 张全部真实命中"才继续（拿不到 ⇒ 本帧不画罩子，外观 = 现状；节流重试）。
            if (!MachineCtmBakedModel.sharedVariantsComplete()) {
                geometryTried = false;
                return false;
            }
            BakedQuad[][] built = new BakedQuad[FACE_COUNT][];
            for (Direction face : DIRECTIONS) {
                BakedQuad[] row = new BakedQuad[CtmConnectivity.CTM_TILES];
                for (int v = 0; v < row.length; v++) {
                    TextureAtlasSprite sprite = variants[v];
                    row[v] = sprite == null ? null : ConnectedCtmBakedModel.retexture(base[face.ordinal()], sprite);
                }
                built[face.ordinal()] = row;
            }
            variantQuads = built; // ★ 全部填满后才发布引用（读方要么 null、要么完整表）
            frame = derived;
            return true;
        } catch (Throwable failure) {
            warnFailure(failure);
            geometryFailure = "解析抛异常：" + failure;
            return false;
        }
    }

    /** 沿面法线把 quad 的 4 个顶点整体外移 {@link #SHELL_OFFSET}（UV / tint / shade 原样）。 */
    private static BakedQuad offset(BakedQuad quad, Direction face) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        float dx = face.getStepX() * SHELL_OFFSET;
        float dy = face.getStepY() * SHELL_OFFSET;
        float dz = face.getStepZ() * SHELL_OFFSET;
        for (int i = 0; i < 4; i++) {
            int o = i * stride;
            vertices[o] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[o]) + dx);
            vertices[o + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[o + 1]) + dy);
            vertices[o + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[o + 2]) + dz);
        }
        return new BakedQuad(vertices, quad.getTintIndex(), face, quad.getSprite(), quad.isShade());
    }

    private static boolean failGeometry(String reason) {
        geometryFailure = reason;
        if (GEOMETRY_WARN.getAndDecrement() > 0) {
            GTLModular.LOGGER.warn("[gtlmodular] NAC 机器外壳罩：几何解析失败 —— {}（本帧起不画罩子，"
                    + "外观 = 现状；下一次资源重载会重试。请把这条日志回传。）", reason);
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 日志 / 限流
    // ------------------------------------------------------------------

    /**
     * ★ <b>任务要求的统计行</b>：{@code NAC 机器外壳罩：候选 N 格 / 本帧绘制 M 格 / 排除 X 格}。
     *
     * <p>节流：资源重载后的第一条必打；之后每 {@link #STATS_EVERY_FRAMES} 帧检查一次，
     * <b>数字没变就不再重复</b>（渲染路径刷日志比不罩糟得多）。
     */
    private static void reportStats(int candidates, int drawn, int excluded) {
        if (candidates == lastCandidates && drawn == lastDrawn && excluded == lastExcluded) {
            framesSinceStats++;
            if (!statsLogged || framesSinceStats < STATS_EVERY_FRAMES) {
                return;
            }
        }
        lastCandidates = candidates;
        lastDrawn = drawn;
        lastExcluded = excluded;
        framesSinceStats = 0;
        statsLogged = true;
        GTLModular.LOGGER.debug("[gtlmodular] NAC 机器外壳罩：候选 {} 格 / 本帧绘制 {} 格 / 排除 {} 格"
                        + "（距离上限 {} 格、每帧上限 {} 格、罩子外移 {}；开关 ENABLED={}）",
                candidates, drawn, excluded, (int) MAX_DISTANCE, MAX_BLOCKS_PER_FRAME, SHELL_OFFSET, ENABLED);
    }

    private static void reportOnce(String key, String message) {
        if (ONCE.add(key)) {
            GTLModular.LOGGER.warn(message);
        }
    }

    private static void warnFailure(Throwable failure) {
        if (FAILURE_WARN.getAndDecrement() > 0) {
            GTLModular.LOGGER.warn("[gtlmodular] NAC 机器外壳罩：渲染路径抛异常 —— 已吞掉（本帧不画罩子，"
                    + "外观 = 现状）。这是外壳罩的 bug，请把下面这条堆栈连同复现步骤回传。", failure);
        }
    }

    // ------------------------------------------------------------------
    // 诊断（自检 / 日志用；纯读，不改任何渲染状态）
    // ------------------------------------------------------------------

    /** 几何是否已就绪（诊断用）。 */
    public static boolean geometryReady() {
        return variantQuads != null && frame != null;
    }

    /** 最近一次几何解析失败的原因（{@code null} = 没失败过）。 */
    public static String geometryFailure() {
        return geometryFailure;
    }

    /** 供日志/自检：本帧统计的快照 {@code {候选, 绘制, 排除}}。 */
    public static int[] lastStats() {
        return new int[] { lastCandidates, lastDrawn, lastExcluded };
    }

    /** 供自检/诊断：把排除名单渲染成一句话。 */
    public static String describeExclusions() {
        List<String> parts = new ArrayList<>();
        parts.add("整台排除 " + EXCLUDED_BLOCKS.size() + " 条 " + EXCLUDED_BLOCKS);
        parts.add("逐面排除 " + EXCLUDED_FACES.size() + " 条 " + EXCLUDED_FACES);
        return String.join("｜", parts);
    }
}
