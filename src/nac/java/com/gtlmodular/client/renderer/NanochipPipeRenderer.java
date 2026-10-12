package com.gtlmodular.client.renderer;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.client.renderer.machine.MachineRenderer;
import com.gtlmodular.common.machine.nanochip.NanochipPipeMachine;
import com.lowdragmc.lowdraglib.client.bakedpipeline.FaceQuad;
import com.lowdragmc.lowdraglib.client.model.ModelFactory;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.function.Consumer;

/**
 * <b>真空传送管道的细管渲染器</b> —— 把"整方块"换成原版 GTNH 那种"中心细芯 + 六向管臂"。
 *
 * <h2>为什么走"动态渲染器"而不是 blockstate multipart</h2>
 * 本模组的机器方块<b>没有 blockstate json</b>：{@code MachineBuilder.register} 里是
 * {@code blockstate(NonNullBiConsumer.noop())}，方块模型完全由 LDLib 的
 * {@code LDLRendererModel$RendererBakedModel} 在渲染时向
 * {@code IBlockRendererProvider.getRenderer(state)} 要 quads —— 也就是 {@code MachineDefinition}
 * 上挂的这个渲染器。好处是纯客户端、纯几何：
 * <ul>
 *   <li>方块/方块实体/机器/NBT/螺丝刀换色/自动取色<b>一行都不用改</b>（仍然 extends {@code MetaMachine}）；</li>
 *   <li>不需要给方块加 6 个 boolean 属性 + 6 处邻居更新（那正是 multipart 路线必须付的代价：
 *       {@code MetaMachineBlock.createBlockStateDefinition} 加属性要自定义方块类，
 *       而 {@code .machine(id, factory)} 这条注册链不暴露 blockFactory）；</li>
 *   <li>几何按<b>当前世界</b>现算，不存在"连接状态不同步"。</li>
 * </ul>
 * 这条路线与 GTCEu 自己的管道完全同构：{@code PipeBlock.getRenderer} 返回
 * {@code PipeBlockRenderer}，它在渲染时读 {@code IPipeNode.getVisualConnections()} 再交给
 * {@code PipeModel.bakeQuads} 生成 quads。本类只是把 {@code IPipeNode} 换成了
 * {@link NanochipPipeMachine#connectionMask()}，并用标签更多、能分别给两层 tintindex 的
 * {@code FaceQuad.Builder} 直接出 quads（GTCEu 的 {@code PipeModel} 内部用的就是它）。
 *
 * <h2>分层与染色（逐条照原版）</h2>
 * <table border="1">
 *   <tr><th>层</th><th>贴图</th><th>tintindex</th></tr>
 *   <tr><td>管身</td><td>{@code gtlmodular:block/overlay/vacuum_pipe}</td>
 *       <td>{@code -1} = 不染（原版 {@code TextureFactory.of(CCPipe)} 单参重载，无 RGBA 调制）</td></tr>
 *   <tr><td>叠加条（外扩一点）</td><td>{@code gtlmodular:block/overlay/vacuum_pipe_bar}</td>
 *       <td>{@link NanochipPipeMachine#TINT_BAR} = 3 → {@code NanochipPipeMachine.tintColor(3)}
 *           → {@code NanochipColor.paintArgb()}</td></tr>
 * </table>
 * 染色链路：quad 的 tintIndex 由 {@code ModelBlockRenderer.putQuadData} 查
 * {@code BlockColors} → GTCEu 在 {@code MachineBuilder.register} 里给每个机器方块注册的
 * {@code IMachineBlock.colorTinted} → {@code MetaMachine.tintColor(index)}。所以 tintindex 3 就是
 * "这一层吃管道颜色"，与真空端口的 {@code TINT_OVERLAY} = 2 各管各的。
 *
 * <h2>两个必须注意的 MC 渲染细节</h2>
 * <ol>
 *   <li><b>quads 必须按"剔除方向"分桶返回</b>：{@code ModelBlockRenderer.tesselateWithAO/WithoutAO}
 *       对 6 个方向各调一次 {@code getQuads(state, side, ...)}（这一个桶会被
 *       {@code Block.shouldRenderFace} 剔除），最后再对 {@code side == null} 调一次（这个桶<b>永不剔除</b>）。
 *       本类因此只把"落在方块边界上的管臂端面"放进方向桶，其余内部面（芯的面、管臂的 4 个长面）
 *       一律走无剔除桶 —— 否则芯在方块内部（y=5/11 这类平面）却被当成"贴着邻居方块的面"整片剔掉，
 *       细管就会破洞。</li>
 *   <li><b>UV 既不能整片 {@code cubeUV()}、也不能用固定小段</b>：见下面「UV 取样约定」。</li>
 * </ol>
 *
 * <h2>★ UV 取样约定（2026-09-16 改：修"相邻两根管道接缝处纹理歪了"）</h2>
 * <p><b>目标态是两条同时成立</b>：① 相邻两根同向管道的表面花纹<b>无缝续接</b>（像 NH 那样沿管子连续）；
 * ② 管面<b>永不取到 {@code CC_PIPE} 四角那圈蓝灰底色</b>。做法是把这两件事拆到两个轴上：
 * <ul>
 *   <li><b>沿臂轴那一维</b>：按方块内坐标取（{@code u = 16 × 坐标}）—— 这与 GTCEu
 *       {@code PipeModel.bakeQuads} 用的 {@code cubeUV()} 是同一套约定（字节码核对：
 *       {@code PipeModel} 里 12 处 quad <b>全部</b>调 {@code FaceQuad$Builder.cubeUV()}；
 *       而 {@code cubeUV()} 的 {@code from/to} 由 {@code cube(AABB)} 乘过 16 ⇒ 单位就是"16 格像素"）。
 *       贴图按整张 16×16 平铺，所以相邻方块在接缝处只差那点几何缝隙（{@link #ARM_END_INSET}）
 *       ⇒ <b>花纹连成一片</b>（连续性等式见 {@link #armUv}）。</li>
 *   <li><b>横截那一维</b>：钉死在十字带 {@code 5..11}（{@link #BAND_MIN}/{@link #BAND_MAX}）
 *       —— 管子是十字棱柱，四角那 5×5 的蓝灰 {@code rgb(126,147,185)} 永远不会被投影到，
 *       直接用 {@code cubeUV()} 会在管面上取到它（管面横截方向宽 4~6 像素，这就是"管子发蓝灰"的根因）。</li>
 * </ul>
 * <p>⇔ 一句话：<b>"只在十字带内取值"与"按方块内坐标连续"必须同时做到</b> ——
 * 旧版本（固定常量 {@code UV_BAND_EAST} 那一套）只做到了前者，沿臂轴也是固定的一小段，
 * 于是每根管子都从头开始贴一遍 ⇒ 接缝处花纹跳变（玩家截图看到的就是这个）。
 *
 * <h2>十字带 UV 表（每个面取贴图哪一块，单位 = 16 格像素）</h2>
 * <table border="1">
 *   <tr><th>几何</th><th>面</th><th>UV</th><th>为什么</th></tr>
 *   <tr><td>管芯</td><td><b>全部 6 个面</b>（含已连接方向：第六轮起芯臂同粗，那面正好是管臂的内端盖）</td>
 *       <td>切向里<b>恰好一个</b>接了管臂（直管）= 沿轴 {@code 16×坐标} / 横向 {@code 5..11}；
 *           否则（管口、拐角、已连接方向）= {@code 5,5 .. 11,11}</td>
 *       <td>与管臂同一套 1:1 映射 ⇒ 整条管路连续</td></tr>
 *   <tr><td>管臂</td><td>4 个长面</td>
 *       <td>沿臂轴 = {@code 16×坐标}；横向 = {@code 5..11}</td>
 *       <td>接缝续接 + 不取四角底色</td></tr>
 *   <tr><td>任意管臂</td><td>末端端面</td><td>{@code 5,5 .. 11,11}</td>
 *       <td>管口（管-管相接时被 MC 的剔除挡掉）</td></tr>
 * </table>
 * 两层（管身 + 叠加条）用<b>同一套 UV</b>，花纹自然对齐；叠加条仍吃染料（tintindex 3）。
 *
 * <p>物品栏图标不走本类：{@code MachineRenderer.renderItem} 会用 {@code MachineRenderer$1}
 * 以 {@code machine == null} 回调 {@link #renderMachine}，本类原样交给 {@code super}，
 * 于是图标用的是 {@code models/block/machine/part/vacuum_pipe.json}（同一个十字细管的静态模型）。
 */
public class NanochipPipeRenderer extends MachineRenderer {

    /** 管身贴图（= 原版 {@code CC_PIPE} 逐字节拷贝）。 */
    private static final ResourceLocation PIPE_TEXTURE =
            NanochipModContext.assetId("block/overlay/vacuum_pipe");

    /** 叠加条贴图（= 原版 {@code CC_BAR_OVERLAY}，吃染料）。 */
    private static final ResourceLocation BAR_TEXTURE =
            NanochipModContext.assetId("block/overlay/vacuum_pipe_bar");

    /**
     * <b>管径（唯一可调旋钮，单位 = 16 格坐标的像素）</b>：管芯与<b>所有管臂</b>共用这一组上下界。
     *
     * <h2>★ 2026-09-17 第六轮：按 NH 定成 5..11（= 6 像素 = 0.375 格），且芯与臂同粗</h2>
     * <p>玩家原话：「管道的芯跟管道也要一样粗啊，为什么不能直接读取 NH 里面的管道粗细值呢」。
     * NH 的答案（<b>类 + 方法 + 字节码行</b>）：
     *
     * <h3>① 渲染真正读的是哪个值 —— 是 {@code 0.375f}，不是 {@code mThickness = 0.5f}</h3>
     * <pre>
     * // GT5U MetaPipeEntity.renderInWorld(ISBRWorldContext)  ← 管道在世界上就是这么画的
     * 14: aload_2
     * 15: invokevirtual BaseMetaPipeEntity.getConnections:()B     // = mConnections 位掩码
     * 18: istore_3
     * 19: aload_0
     * 20: invokevirtual #170  Method getThickness:()F              // ★ 渲染只认这一个值
     * 23: fstore 4
     * ...
     * 112: fconst_1
     * 113: fload 4
     * 115: fsub
     * 116: fconst_2
     * 117: fdiv
     * 118: fstore 5          // min = (1 - thickness) / 2
     * 120: fconst_1
     * 121: fload 5
     * 123: fsub
     * 124: fstore 6          // max = 1 - min
     *
     * // MetaPipeEntity.getThickness()  （同名文件 1323 行起）
     * 22: aload_0
     * 23: invokevirtual #308  Method getCollisionThickness:()F     // ★ 虚分派 —— 走子类覆写
     * 26: freturn
     * (0..21 = GTMod.isClientSide() &amp;&amp; GTClient.shouldHideThings() ⇒ 返回 0.0625f，仅"隐藏遮挡"调试模式)
     *
     * // MTEVacuumConveyorPipe.getCollisionThickness()  （覆写了 MTEBaseFactoryPipe 的同名方法）
     * 18: ldc #203  // float 0.0625f     ← 同上，仅 shouldHideThings()
     * 20: freturn
     * 21: ldc #204  // float 0.375f      ← ★ 正常路径就是它
     * 23: freturn
     *
     * // MTEBaseFactoryPipe.&lt;init&gt; / &lt;init&gt;(MTEBaseFactoryPipe)
     * 8: ldc #27   // float 0.5f
     * 10: putfield #29 // Field mThickness:F      ← 0.5 只被写进这个字段……
     * // …而 MTEBaseFactoryPipe.getCollisionThickness() 读它（偏移 1: getfield mThickness / 4: freturn），
     * //   但真空管道**覆写**了 getCollisionThickness() ⇒ 那条读路径根本到不了。
     * </pre>
     * ⇒ <b>结论：真空管道的渲染粗细 = {@code MTEVacuumConveyorPipe.getCollisionThickness()} = 0.375f（6 像素）。
     * {@code mThickness = 0.5f} 是"写了但渲染不会读到"的死值。</b>
     * 这也解释了旧注释里 {@code 4..12 = mThickness 0.5} 的来源 —— 那是 MTEBaseFactoryPipe 的字段值，
     * 不是真空管道实际渲染的值。
     *
     * <h3>② 管身与接头是不是同一粗细 —— 是，完全同一个值</h3>
     * <p>{@code renderInWorld} 里 <b>每一处</b> {@code RenderBlocks.func_147782_a(DDDDDD)}
     * （= {@code setRenderBounds}，设这一个盒子的 6 个坐标）用的都是同一对
     * {@code fload 5} / {@code fload 6}：
     * <ul>
     *   <li>偏移 232（{@code mConnections == 0}，孤立管口）：{@code 5,5,5 .. 6,6,6}；</li>
     *   <li>偏移 306 / 376 / 446（{@code 48=W|E}、{@code 3=D|U}、{@code 12=N|S} 三条直管快路）：
     *       横截两轴 = {@code fload5..fload6}，只有沿轴那一轴被拉到 {@code 0} / {@code 1}；</li>
     *   <li>偏移 543 / 567 / 637 / 661 / 730 / 754 / 823 / 847 / 916 / 940 …（默认分支逐方向的管臂）：
     *       同样是 {@code fload5..fload6} 作横截，沿轴那一轴拉到 {@code 0} 或 {@code 1}。</li>
     * </ul>
     * 全方法内除了 {@code dconst_0} / {@code dconst_1}（沿轴端点）以外，
     * <b>没有任何别的浮点常量参与盒子的建立</b> —— 也就是说 NH 的"管芯"和"管臂"
     * <b>是同一种粗细的棱柱</b>，不存在"芯比臂粗"。
     *
     * <h3>③ 于是本模组把两套常量合成同一组</h3>
     * <p>{@link #PIPE_CORE_MIN}/{@link #PIPE_CORE_MAX} 与
     * {@link #PIPE_ARM_MIN}/{@link #PIPE_ARM_MAX} 现在<b>必须相等</b>（{@code scripts/gen_pipe_model.js}
     * 与 {@code _census/check_pipe_uv.js} 都会断言这一点）。旧版本"芯 6 像素 / 臂 4 像素、芯必须包住臂"
     * 是移植版自己发明的口径，<b>NH 没有</b>，已废弃。
     *
     * <h3>④ 历史约束的重新评估（按任务书要求逐条给结论）</h3>
     * <ul>
     *   <li><b>"芯必须包住臂"</b>：<b>作废</b> —— NH 里芯与臂同粗，根本不需要"包"。</li>
     *   <li><b>"芯/臂要比仓室正脸那个黑方块细"</b>：<b>仍然成立，但是巧合、不再是设计依据</b>。
     *       仓室正脸黑方块实心区正好也是 6 像素（格像素 5..11、贴图像素现算，见
     *       {@code _census/check_pipe_uv.js} Ⅵ 节），含 1 像素柔边是 8 像素。
     *       NH 的 6 像素 {@code =} 纯黑区、{@code <} 含柔边黑方块 ⇒ 玩家"把黑方块露出来"的诉求
     *       与 NH 值不冲突，两个口径可以同时满足。所以自检改成
     *       "与 NH 数值一致（0.375）" 为第一断言，黑方块关系降级为附带的"≤ / <"关系断言。</li>
     * </ul>
     */
    public static final int PIPE_CORE_MIN = 5;    // 16 格坐标
    public static final int PIPE_CORE_MAX = 11;   // 16 格坐标

    /** 等价厚度：{@code (11−5)/16 = 0.375} —— <b>等于 NH 的 {@code MTEVacuumConveyorPipe.getCollisionThickness()}</b>。 */
    public static final double PIPE_THICKNESS = (PIPE_CORE_MAX - PIPE_CORE_MIN) / 16.0;

    /**
     * <b>管臂的横截面（16 格坐标）</b>：<b>与管芯完全相同</b>（{@code 5..11}）。
     *
     * <p>★ 2026-09-17 第六轮起，这四个常量必须两两相等 —— 依据见 {@link #PIPE_CORE_MIN} 的长注释
     * ② 节（{@code MetaPipeEntity.renderInWorld} 里管芯与每一根管臂共用同一对
     * {@code fload 5}/{@code fload 6}）。玩家原话：「管道的芯跟管道也要一样粗啊」。
     *
     * <p><b>留成两个常量而不是合并成一个</b>：{@code scripts/gen_pipe_model.js} 与
     * {@code _census/check_pipe_uv.js} 会分别读这两组名字做"两处同口径"对账；
     * 合并会让"有人把臂改细回去"这类回退失去报警点。
     */
    public static final int PIPE_ARM_MIN = 5;     // 16 格坐标（= PIPE_CORE_MIN）
    public static final int PIPE_ARM_MAX = 11;    // 16 格坐标（= PIPE_CORE_MAX）

    /**
     * 派生的归一化边界（<b>别单独改</b>；改上面四个数即可）。
     * 芯与臂都是 5..11 ⇒ 0.3125..0.6875（6 像素 = NH 的 0.375）。
     */
    private static final double CORE_MIN = PIPE_CORE_MIN / 16.0;
    private static final double CORE_MAX = PIPE_CORE_MAX / 16.0;
    private static final double ARM_MIN = PIPE_ARM_MIN / 16.0;
    private static final double ARM_MAX = PIPE_ARM_MAX / 16.0;

    /** 管臂等价厚度：{@code (11−5)/16 = 0.375}，与 {@link #PIPE_THICKNESS} 相等（NH：芯臂同粗）。 */
    public static final double PIPE_ARM_THICKNESS = (PIPE_ARM_MAX - PIPE_ARM_MIN) / 16.0;

    /**
     * 叠加条比管身外扩一点点，避免两层共面 z-fighting。
     * GTCEu 自己的 {@code PipeModel.bakeQuads} 对管臂用的是同一量级（{@code inflate(-0.001)}，单位=格）。
     */
    private static final float OVERLAY_INFLATE = 0.001F;

    /**
     * 管臂的<b>外端</b>再往方块里缩一点：两根管道首尾相接时，两边管臂的端面正好落在同一个平面上
     * （各在自己的方块边界），完全共面会 z-fighting。GTCEu 的 {@code PipeModel.bakeQuads} 用
     * {@code sideCube.inflate(-0.001)} 解决同一件事；这里取 0.002，好让外扩后的叠加条（+0.001）
     * 仍然缩在方块边界里侧（0.001）、又比管身外端（0.002）更靠外。
     */
    private static final float ARM_END_INSET = 0.002F;

    private static final Direction[] DIRECTIONS = Direction.values();

    // ---------- 十字带 UV（单位 = 16 格贴图像素；{u0, v0, u1, v1}） ----------

    /** 贴图正中那块：x/y 5..11。管芯的"管口"端面 / 管臂的末端端面都用它。 */
    private static final float[] UV_CENTRE = {5F, 5F, 11F, 11F};

    /** 十字带的横向范围（u 或 v 里"横截那一维"钉死在这个区间，永不取到四角底色）。 */
    private static final float BAND_MIN = 5F;
    private static final float BAND_MAX = 11F;

    /**
     * ★ 管道表面的 UV 规则（<b>本类的核心不变量</b>）：<b>一个轴钉在十字带 5..11、另一个轴按方块内坐标 1:1</b>。
     *
     * <ul>
     *   <li><b>横向轴钉在 {@code 5..11}</b>：管子是十字棱柱，贴图四角那 5×5 的蓝灰
     *       {@code rgb(126,147,185)} 永远不会被投影到 ⇒ 钉住就永不取到它。
     *       （关键事实：十字带的<b>整条</b>横带/竖带都是有效像素 —— 横带 = 第 5..10 行全宽、
     *        竖带 = 第 5..10 列全高 ⇒ 只要有一维落在 5..11，另一维取 0..16 也<b>绝不会</b>碰到四角。）</li>
     *   <li><b>沿轴按坐标 1:1</b>：{@code u = 16 × 方块内坐标}（符号约定与 GTCEu
     *       {@code PipeModel.bakeQuads} 用的 {@code cubeUV()} 逐条一致）。贴图按整张 16×16 平铺，
     *       于是整条管路（管芯 + 两侧管臂）在沿轴方向上是<b>同一套 1:1 映射</b>：
     *       既不会"每根管子重新开始"，也不会出现"管芯被压缩、管臂被拉伸"的周期性抽动
     *       —— 那正是玩家看到的"接缝处纹理歪了"。</li>
     * </ul>
     *
     * <h3>接缝连续性等式（可复现的证明）</h3>
     * 设沿轴世界坐标 t，块 A 占 [0,1]、块 B 占 [1,2]，A 的 +向管臂几何 = [0.75, 1−INSET]，
     * B 的 −向管臂 = [1+INSET, 1.25]（INSET = {@link #ARM_END_INSET}）。因为沿轴 UV 是
     * <b>各块自身坐标</b>的 1:1 线性函数（A：{@code u_A(t)=16t}；B：{@code u_B(t)=16(t−1)}），
     * <pre>
     *   u_A(1−INSET) = 16 − 16·INSET
     *   u_B(1+INSET) = 16·INSET
     *   ⇒ u_A(1−INSET) − u_B(1+INSET) = 16 − 32·INSET ≡ −32·INSET  (mod 16)   ← 只是那点几何缝隙
     * </pre>
     * 即接缝两侧的贴图坐标在 mod 16（= 平铺周期）意义上<b>相等</b>，误差只有几何缝隙
     * （0.002 格 ⇒ 0.032 像素）⇒ 花纹在接缝处<b>连成一片</b>。
     * 横向轴两侧都是同一个常数区间 {@code [5,11]}、作用在同一个截面上 ⇒ 完全一致。
     * 这个等式就是 {@code _census/check_pipe_uv.js} 每次构建前自动核对的不变量。
     *
     * @param face 要画的那个面
     * @param box  该面所属的方盒（0..1 方块坐标）
     * @param along 沿轴（管臂的轴向 / 管路延伸的轴）；{@code null} = 这个面没有"沿轴"
     *              （开口端、拐角）⇒ 用贴图正中那块 {@link #UV_CENTRE}（管口/接头）
     */
    private static float[] uvFor(Direction face, AABB box, Direction.Axis along) {
        if (along == null) {
            return UV_CENTRE;
        }
        int uAxis = cubeUvUAxis(face);
        boolean uNeg = cubeUvUNeg(face);
        if (uAxis == along.ordinal()) {
            // 沿轴 = u 轴 ⇒ u 按坐标连续，v 钉在十字带
            float a = uNeg ? (float) (16.0 * (1.0 - max(box, uAxis))) : (float) (16.0 * min(box, uAxis));
            float b = uNeg ? (float) (16.0 * (1.0 - min(box, uAxis))) : (float) (16.0 * max(box, uAxis));
            return new float[]{a, BAND_MIN, b, BAND_MAX};
        }
        int vAxis = cubeUvVAxis(face);
        boolean vNeg = cubeUvVNeg(face);
        if (vAxis == along.ordinal()) {
            // 沿轴 = v 轴 ⇒ v 按坐标连续，u 钉在十字带
            float a = vNeg ? (float) (16.0 * (1.0 - max(box, vAxis))) : (float) (16.0 * min(box, vAxis));
            float b = vNeg ? (float) (16.0 * (1.0 - min(box, vAxis))) : (float) (16.0 * max(box, vAxis));
            return new float[]{BAND_MIN, a, BAND_MAX, b};
        }
        // 沿轴与这个面平行不了（不该发生）⇒ 退回管口那块，宁可保守也不要取到四角
        return UV_CENTRE;
    }

    /**
     * 管芯某个面该用哪条轴当"沿轴"：看该面的<b>两个切向</b>里有没有、且只有一个方向接了管臂。
     *
     * <ul>
     *   <li>直管：唯一那个切向管臂就是沿轴 ⇒ 管芯与管臂用同一套 1:1 映射（接缝/内部都连续）；</li>
     *   <li>拐角（两个切向都接管臂）或孤立管口（都没接）⇒ {@code null} ⇒ 用正中那块（管口/接头观感）。</li>
     * </ul>
     */
    private static Direction.Axis alongAxisOf(int mask, Direction face) {
        Direction.Axis found = null;
        for (Direction d : DIRECTIONS) {
            if (d.getAxis() == face.getAxis()) continue;
            if ((mask & NanochipPipeMachine.bit(d)) == 0) continue;
            if (found != null && found != d.getAxis()) return null;   // 两个**不同**切向都有臂 ⇒ 拐角
            found = d.getAxis();
        }
        return found;
    }

    private static double min(AABB box, int axis) {
        return switch (axis) {
            case 0 -> box.minX;
            case 1 -> box.minY;
            default -> box.minZ;
        };
    }

    private static double max(AABB box, int axis) {
        return switch (axis) {
            case 0 -> box.maxX;
            case 1 -> box.maxY;
            default -> box.maxZ;
        };
    }

    /**
     * 某个面上"u 轴"是哪个轴（0=x / 2=z；v 轴恒为 y）。
     * 逐条与 LDLib {@code FaceQuad$Builder.cubeUV()} 的字节码一致
     * （UP/DOWN：u↔x、v↔z；NORTH/SOUTH/WEST/EAST：u↔x 或 z、v↔y）。
     */
    private static int cubeUvUAxis(Direction face) {
        return switch (face) {
            case WEST, EAST -> 2;   // u ↔ z
            default -> 0;           // DOWN / UP / NORTH / SOUTH：u ↔ x
        };
    }

    /** 某个面上 u 轴是否取反（{@code cubeUV} 的符号约定）。 */
    private static boolean cubeUvUNeg(Direction face) {
        return face == Direction.NORTH || face == Direction.EAST;
    }

    /** 某个面上"v 轴"：UP 面 v↔z，其余面 v↔y。 */
    private static int cubeUvVAxis(Direction face) {
        return face == Direction.UP || face == Direction.DOWN ? 2 : 1;
    }

    /** 某个面上 v 轴是否取反：只有 UP 面 v↔+z，其余都是 v↔−（z 或 y）。 */
    private static boolean cubeUvVNeg(Direction face) {
        return face != Direction.UP;
    }

    private TextureAtlasSprite pipeSprite;
    private TextureAtlasSprite barSprite;

    /**
     * @param modelLocation 静态模型（{@code models/block/machine/part/vacuum_pipe.json}）：
     *                      它只用于<b>物品图标</b>与"把两张贴图登记进方块图集"，
     *                      世界里那块方块的几何完全由本类现算。
     */
    public NanochipPipeRenderer(ResourceLocation modelLocation) {
        super(modelLocation);
    }

    @Override
    public void renderMachine(List<BakedQuad> quads, MachineDefinition definition, MetaMachine machine,
                              Direction frontFacing, Direction side, RandomSource rand,
                              Direction modelFacing, ModelState modelState) {
        if (!(machine instanceof NanochipPipeMachine pipe)) {
            // 物品栏 / 预览（machine == null）：走 json 静态模型，图标就是那个十字细管
            super.renderMachine(quads, definition, machine, frontFacing, side, rand, modelFacing, modelState);
            return;
        }
        int mask = pipe.connectionMask();
        // ① 管身：不染（原版 CC_PIPE 那一层没有 RGBA 调制）
        appendLayer(quads, side, mask, pipeSprite(), -1, 0.0F);
        // ② 叠加条：tintindex = 3 → NanochipPipeMachine.tintColor(3) → 染料色（原版 CC_BAR_OVERLAY 那一层）
        appendLayer(quads, side, mask, barSprite(), NanochipPipeMachine.TINT_BAR, OVERLAY_INFLATE);
    }

    /**
     * 把一层几何放进 {@code quads}。
     *
     * @param side    MC 这次要的方向桶（{@code null} = 无剔除桶）
     * @param mask   {@link NanochipPipeMachine#connectionMask()}：哪几个方向要伸管臂
     * @param tint   quad 的 tintindex（{@code -1} = 不染）
     * @param inflate 外扩量（格），叠加条用一点点外扩避免与管身共面
     */
    private static void appendLayer(List<BakedQuad> quads, Direction side, int mask,
                                    TextureAtlasSprite sprite, int tint, float inflate) {
        if (side == null) {
            // 无剔除桶：芯的 6 个面 + 每根管臂的 4 个长面。
            // ★ 芯的 6 个面**全都画**（含已连接方向）。2026-09-17 第六轮起芯与臂同粗
            //   （PIPE_CORE_MIN/MAX == PIPE_ARM_MIN/MAX），已连接方向的那面芯面正好是管臂的"内端盖"、
            //   被管臂包在里面（臂的内端面本来就不画）⇒ 既不会看穿、也不共面。
            //   保留"全都画"而不是按 mask 筛掉：几何上无害（隐藏面），少一处分支就少一处回退风险。
            AABB core = coreBox(inflate);
            for (Direction face : DIRECTIONS) {
                // 沿轴 = 该面两个切向里唯一接了管臂的那条 ⇒ 与管臂用同一套 1:1 映射；
                // 直管就是这样；拐角/孤立管口/已连接方向的芯面没有唯一沿轴 ⇒ 取贴图正中那块
                // （管口/接头/台阶观感，见 uvFor）。
                quads.add(bake(face, core, sprite, tint, false,
                        uvFor(face, core, alongAxisOf(mask, face))));
            }
            for (Direction arm : DIRECTIONS) {
                if ((mask & NanochipPipeMachine.bit(arm)) != 0) {
                    AABB box = armBox(arm, inflate);
                    for (Direction face : DIRECTIONS) {
                        if (face != arm && face != arm.getOpposite()) {
                            // ★ 沿臂轴按坐标 1:1 连续（接缝续接）、横向钉在十字带（不取到四角底色）
                            quads.add(bake(face, box, sprite, tint, false,
                                    uvFor(face, box, arm.getAxis())));
                        }
                    }
                }
            }
            return;
        }
        // 方向桶：本方块唯一落在方块边界上的面是"管臂末端"，它带剔除方向
        // （正对着实心邻居时 MC 会省掉这次绘制，管臂插进机器/机壳里时正好不画多余的面）。
        if ((mask & NanochipPipeMachine.bit(side)) != 0) {
            quads.add(bake(side, armBox(side, inflate), sprite, tint, true, UV_CENTRE));
        }
    }

    /** 中心细芯：{@link #PIPE_CORE_MIN}..{@link #PIPE_CORE_MAX} 见方。 */
    private static AABB coreBox(float inflate) {
        return new AABB(CORE_MIN - inflate, CORE_MIN - inflate, CORE_MIN - inflate,
                CORE_MAX + inflate, CORE_MAX + inflate, CORE_MAX + inflate);
    }

    /**
     * 朝 {@code side} 那根管臂：<b>从芯的外表面</b>一路伸到方块边界附近的方盒
     * （外端略缩，见 {@link #ARM_END_INSET}）。
     *
     * <p><b>横截面用 {@link #PIPE_ARM_MIN}/{@link #PIPE_ARM_MAX}，第六轮起与管芯
     * （{@link #PIPE_CORE_MIN}/{@link #PIPE_CORE_MAX} = 5..11）完全相同</b> ——
     * NH 的 {@code MetaPipeEntity.renderInWorld} 对管芯和每一根管臂用的是同一对
     * {@code fload 5}/{@code fload 6}（见 {@link #PIPE_CORE_MIN} 的②节），
     * 所以臂根四周<b>不再有台阶</b>，管芯与管臂沿轴正好拼成一根等粗的管子。
     *
     * <p><b>为什么起点是芯的外表面而不是芯的中心</b>（这是一处真实的 z-fighting 修复）：
     * 旧写法让管臂从 {@code CORE_MIN} 起算，于是<b>相邻两根管臂在整个管芯区域互相重叠</b>、
     * 它们的长面完全共面 —— 一条直管会有 2 根管臂 + 管芯共 3 层共面四边形打架。
     * 改成从芯的外表面起算之后，芯与管臂<b>沿轴方向正好拼合</b>（管臂的内端面不画 = 就是那面芯面），
     * 既无重叠也无缝隙。
     *
     * <p>叠加条那层（{@code inflate > 0}）起点再往芯里缩 {@code inflate}，避免两层之间出现
     * 一条 0.001 格宽的缝。
     */
    private static AABB armBox(Direction side, float inflate) {
        double min = ARM_MIN - inflate;     // ★ 横截面：与芯同一组（第六轮起 PIPE_ARM == PIPE_CORE）
        double max = ARM_MAX + inflate;
        double inset = ARM_END_INSET - inflate;
        double startMax = CORE_MAX - inflate;   // 正向臂的起点：芯的外表面（叠加条层多缩 inflate，避免留缝）
        double startMin = CORE_MIN + inflate;   // 负向臂的起点：同上
        double x0 = min, y0 = min, z0 = min;
        double x1 = max, y1 = max, z1 = max;
        if (side.getStepX() > 0) {
            x0 = startMax;
            x1 = 1.0 - inset;
        } else if (side.getStepX() < 0) {
            x0 = inset;
            x1 = startMin;
        } else if (side.getStepY() > 0) {
            y0 = startMax;
            y1 = 1.0 - inset;
        } else if (side.getStepY() < 0) {
            y0 = inset;
            y1 = startMin;
        } else if (side.getStepZ() > 0) {
            z0 = startMax;
            z1 = 1.0 - inset;
        } else {
            z0 = inset;
            z1 = startMin;
        }
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    /**
     * 一个面。
     *
     * <p><b>UV 必须显式给</b>（{@link #UV_CENTRE} / {@link #bandOf}），不能用 {@code cubeUV()}：
     * {@code cubeUV()} 是按方块坐标 1:1 取整张贴图，会把 {@code CC_PIPE} 四角那片
     * 蓝灰底色贴到管面上（见类注释）。{@code uv0}/{@code uv1} 的单位是
     * <b>16 格贴图像素</b>（与模型 json 的 {@code face.uv} 同一套约定），
     * 不是归一化的 0..1。
     *
     * <p>{@code cull(false)} = 这个 quad 不带剔除方向，会被渲染器放进"永不剔除"的桶里 ——
     * <b>只有贴在方块边界上的面才允许 {@code cull(true)}</b>。
     */
    private static BakedQuad bake(Direction face, AABB box, TextureAtlasSprite sprite, int tint,
                                  boolean cull, float[] uv) {
        return FaceQuad.builder(face, sprite)
                .cube(box)
                .uv0(uv[0], uv[1])
                .uv1(uv[2], uv[3])
                .tintIndex(tint)
                .cull(cull)
                .bake();
    }

    /** 管身贴图（懒取 + 每次图集重载后失效）。 */
    private TextureAtlasSprite pipeSprite() {
        if (pipeSprite == null) {
            pipeSprite = ModelFactory.getBlockSprite(PIPE_TEXTURE);
        }
        return pipeSprite;
    }

    /** 叠加条贴图（懒取 + 每次图集重载后失效）。 */
    private TextureAtlasSprite barSprite() {
        if (barSprite == null) {
            barSprite = ModelFactory.getBlockSprite(BAR_TEXTURE);
        }
        return barSprite;
    }

    /**
     * 图集重载：清掉贴图缓存（旧 sprite 已随旧图集失效），并顺手把两张贴图登记进方块图集
     * —— 与 GTCEu 的 {@code PipeBlockRenderer.onPrepareTextureAtlas} 同一个写法。
     * （静态模型 json 里本来就引用了这两张贴图，这里是双保险。）
     */
    @Override
    public void onPrepareTextureAtlas(ResourceLocation atlas, Consumer<ResourceLocation> register) {
        super.onPrepareTextureAtlas(atlas, register);
        if (TextureAtlas.LOCATION_BLOCKS.equals(atlas)) {
            register.accept(PIPE_TEXTURE);
            register.accept(BAR_TEXTURE);
        }
        this.pipeSprite = null;
        this.barSprite = null;
    }
}
