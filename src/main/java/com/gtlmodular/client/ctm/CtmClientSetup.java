package com.gtlmodular.client.ctm;

import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.NanochipBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * <b>连接纹理的客户端接线</b>：模型烘焙完成后，把 {@link #TARGETS} 里登记的方块（各自都是 {@code cube_all}
 * 模型）包成 {@link ConnectedCtmBakedModel}，再把变体 sprite（47 张 / 5 张）预烘焙进去。
 *
 * <p>只订阅<b>客户端 mod 总线</b>（{@code value = Dist.CLIENT}）—— 服务端不会加载本类，
 * 也不会因为缺少客户端类而崩（{@code @Mod.EventBusSubscriber} 的按侧分发是 Forge 官方做法）。
 *
 * <h2>为什么用「包一层」而不是自定义 model loader</h2>
 * 自定义 loader 要在 {@code models/block/**} 里改 {@code "loader": ...}，还要连带处理
 * {@code render_type} 的归属；而「包一层」把改动全部收在客户端：<b>blockstates 与 block model 都不动</b>，
 * 现有资源自检（matchTiles / 模型引用贴图）也全部保持原样。
 *
 * <h2>时序</h2>
 * <ol>
 *   <li>{@code ModifyBakingResult}：包装模型，并尽力解析一次 sprite（此时图集通常已缝合）；</li>
 *   <li>{@code BakingCompleted}：再解析一次兜底（此时图集必然就绪）。</li>
 * </ol>
 * 两次都解析不出来 ⇒ 变体退回第 0 格 = <b>与今天完全一致的外观</b>，只多一条 WARN 日志（可诊断）。
 *
 * <p>本类<b>不在静态初始化里碰方块注册</b>：方块只在烘焙回调里经 {@link Supplier} 取（那时注册早已完成）。
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CtmClientSetup {

    /**
     * 方块注册名 → （变体 sprite 前缀, 是否走 5 格 compact）。
     *
     * <p>{@code blockstates/*.json} 一个字节都不改：这里只按**注册名**认方块（模型烘焙结果里的键是
     * 方块状态模型 id，其 {@code getPath()} 就是注册名），与模型文件走哪条 loader 无关。
     *
     * <p>⚠️ <b>默认只许登记本模组自己的方块</b>（{@code gtlmodular:*}）：这些方块是全包通用件之外的
     * 本模组专属品，改外观不影响别人；{@code gtceu:*} / {@code gtlcore:*} 是整合包通用方块
     * （装配线外壳 / UEV 机壳 / 线圈块等），一旦登记就是**全包外观一起变**。
     *
     * <p>★ <b>只有 NH 直接要求的那一条 {@code gtceu:} 例外</b>：见 {@link #FOREIGN_ALLOWED}。
     */
    private static final Map<String, Target> TARGETS = new LinkedHashMap<>();

    /**
     * ★ <b>命名空间例外白名单 —— 只有 NH 的 CTM 配置直接要求的这一个方块</b>（不允许无差别放宽）。
     *
     * <h4>为什么必须开这个口子（NH 的依据，逐字）</h4>
     * NH 的线圈 CTM 是按 <b>iconset</b> 全局匹配的（不是按方块名）：
     * <pre>
     *   assets/gregtech/mcpatcher/ctm/coils/naq/coil.properties
     *     matchTiles=gregtech:iconsets/MACHINE_COIL_NAQUADAH_BACKGROUND
     *     method=compact   faces=all   tiles=0 1 1 0 1   connect=block
     *   assets/gregtech/mcpatcher/ctm/coils/naq/litcoil.properties
     *     matchTiles=gregtech:iconsets/MACHINE_COIL_NAQUADAH_FOREGROUND
     *     method=compact   faces=all   tiles=10 11 11 10 11   connect=block
     * </pre>
     * {@code matchTiles} 匹配的是<b>贴图名</b> ⇒ NH 里凡是用这张 iconset 的线圈块（所有机器多方块的线圈位）
     * <b>全都连片</b>。1.20.1 这边对应的方块是 GTCEu 的 {@code gtceu:naquadah_coil_block}
     * （本模组不动它的注册；只登记它的模型）。
     *
     * <h4>取舍与影响面（写清楚，便于审计）</h4>
     * <ul>
     *   <li>影响面 = <b>全包凡是用 {@code gtceu:naquadah_coil_block} 的地方外观一起变</b>
     *       （多方块结构的线圈位、机器外壳、JEI/物品栏图标等）—— 这<b>正是 NH 的行为</b>，不是副作用；</li>
     *   <li>所以它是<b>白名单</b>而不是"放开命名空间"：其它 {@code gtceu:*} / {@code gtlcore:*} 方块
     *       照旧一律跳过（{@code _census/check_ctm_targets.js} 的守门断言会逐条报红）；</li>
     *   <li>加新条目必须先在 NH 侧找到对应的 {@code mcpatcher/ctm} 配置并写进自检，不许凭感觉加。</li>
     * </ul>
     */
    private static final Set<String> FOREIGN_ALLOWED = Set.of(
            "gtceu:naquadah_coil_block");

    static {
        // ⚠️ sprite 前缀**必须写字面量**（不要抽成常量）：`_census/check_all_patterns.js` ⑨
        //   是用正则 `TARGETS\.put\("…",\s*new Target\(\s*"([^"]+)",\s*(true|false)` 解析这张表的，
        //   抽成常量会让那道自检报红。
        // 网格接口机壳：GTNH 是 method=ctm，47 格（8 邻域，含对角）
        TARGETS.put("nanochip_mesh_interface_casing", new Target(
                "block/casings/ctm/nanochip_mesh_interface_casing", false,
                () -> NanochipBlocks.MESH_INTERFACE_CASING.get()));
        // 复合体玻璃：GTNH 是 method=compact，5 格（★ 已冻结：路径 / 5 张贴图 / 既有断言都不要动）
        TARGETS.put("nanochip_complex_glass", new Target(
                "block/ctm/nanochip_complex_glass", true,
                () -> NanochipBlocks.COMPLEX_GLASS.get()));
        // ★ 钠夸达线圈（NH 唯一一条跨命名空间例外；见 FOREIGN_ALLOWED 的取证）：
        //   底层 `coil.properties` tiles=0 1 1 0 1 ⇒ 槽 0/3 ← 0.png，槽 1/2/4 ← 1.png
        //   前景层 `litcoil.properties` tiles=10 11 11 10 11 ⇒ 槽 0/3 ← 10.png，槽 1/2/4 ← 11.png
        //   底层走 `block/ctm/naquadah_coil`；前景层（GTCEu 的 active=true 两层模型）走
        //   `block/ctm/naquadah_coil_lit`，由 LayeredCtmBakedModel 分层包装。
        //   ⚠️ 键 = **方块注册名**（1.20.1 的烘焙表键是 ModelResourceLocation，其 getPath() 就是注册名
        //      —— `ModelResourceLocation` 没有覆写 getPath()，带 `#active=false` 这类变体也不进 path，
        //      所以同一方块的 active=false / active=true / 物品三条条目都会命中这一条）。
        TARGETS.put("naquadah_coil_block", new Target(
                "block/ctm/naquadah_coil", true, "block/ctm/naquadah_coil_lit",
                () -> ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu", "naquadah_coil_block"))));
    }

    // ────────────────────────────────────────────────────────────────────────────────────────────
    // ★ 2026-09-17 撤销：**强化机壳 / 计算矩阵机壳 不许登记进 TARGETS**
    //   （玩家口径：「我要它跟 GTNH 里面一样，只要跟 GTNH 一样，我都接受」）
    //
    // 曾经加过这两条（把强化机壳 402+16 格、计算矩阵机壳 9 格也画成接口机壳那套 47 格蓝黑花纹）。
    // **那是发明 NH 没有的外观**，已被取证否掉：
    //
    //   对实例 `mods/` 下全部 244 个 jar 做 entries 级扫描（凡路径含 `ctm` 的 `*.properties`，
    //   找 `NANOCHIP|blockcasings12|ASSEMBLY_LINE|blockcasingsNH|blockcasings9`）——命中只有两条：
    //     ① …/ctm/nanochipmeshinterfacecasing/nanochipmeshinterfacecasing.properties
    //          matchTiles=gregtech:iconsets/NANOCHIP_MESH_INTERFACE_CASING
    //          method=ctm  faces=all  tiles=0-46  connect=tile        ⇒ 只有接口机壳连片
    //     ② …/ctm/formingcore/formingcore.properties
    //          matchBlocks=gregtech:gt.blockcasings12  metadata=14    ⇒ 是 forming core，与 NAC 无关
    //   ⇒ **NH 没给 NANOCHIP_REINFORCEMENT_CASING 任何 CTM 配置**（UEV 机壳 / 装配线外壳 / water-plant 机壳同样零命中）。
    //   ⇒ NH 的 NAC 塔身 = 只有接口机壳那 280 格连片，强化机壳那 418 格是**普通单张贴图**；
    //     我们的塔身早与之逐格一致（`check_all_patterns.js` 第 ⑧ 条）⇒ 外观本来就一致，无需 CTM。
    //
    // 守门断言：`_census/check_ctm_targets.js`（TARGETS 必须**恰好**是 3 条：网格接口机壳 + 复合体玻璃
    // + NH 要求的那一个 gtceu:naquadah_coil_block；名单里出现
    // `nanochip_reinforcement_casing` / `nanochip_computational_matrix_casing` / 白名单外的任何 `gtceu:*` 一律报红）。
    //
    // 仍然存在的**真实** NH 差距（另一档工作量，见 `_结构照搬_施工记录.md` §72）：
    //   NH 给线圈块配了 CTM（`coils/naq`：coil.properties + litcoil.properties，method=compact，
    //   tiles=0 1 1 0 1，connect=block，iconset MACHINE_COIL_NAQUADAH_BACKGROUND / _FOREGROUND）
    //   ⇒ NH 塔身那 22 格线圈是**连片**的。
    //   ★ 2026-09-17 本轮已照抄落地：TARGETS 新增 `naquadah_coil_block`（底层 5 格 + 前景层 5 格，
    //     贴图逐字节取自 NH 的 0/1.png 与 10/11.png；前景层走 LayeredCtmBakedModel）。
    //     命名空间例外只在 FOREIGN_ALLOWED 里放行这一个 gtceu: 方块。
    // ────────────────────────────────────────────────────────────────────────────────────────────

    private static final List<ConnectedCtmBakedModel> WRAPPED = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** ★ 分层包装（多层的 {@code active=true} 线圈模型）：底层 + 前景层各一套变体。 */
    private static final List<LayeredCtmBakedModel> LAYERED = new java.util.concurrent.CopyOnWriteArrayList<>();
    /**
     * ★ 2026-09-17 新增：<b>机器方块</b>（真空仓 / 主机 / 11 个模块控制器）的包装。
     *
     * <p>它们<b>不</b>进 {@link #TARGETS}：那张表是"cube_all 方块模型"的目标表（机壳 / 玻璃 / 线圈），
     * 走"预烘焙 47 格变体 + 3 参采样"；机器的烘焙模型被 LDLib 换成了
     * {@code ldlib:block/renderer_model}（{@code RendererBakedModel}，3 参 {@code getQuads} 恒返回空表），
     * 3 参采样路线对它<b>永远解析不出来</b>，只能走渲染期换 sprite —— 见
     * {@link MachineCtmBakedModel} 的类注释（两条字节码证据都在那里）。
     * 名单 = {@link CtmHullGroup#MACHINE_HULL_IDS}（显式白名单 + 自检逐条核对）。
     */
    private static final List<MachineCtmBakedModel> MACHINES = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static volatile boolean loggedReady;
    private static volatile int notReadyLogged;

    /**
     * ★ 2026-09-18 新增：本轮「外来机器方块」（白名单 / TARGETS 之外的任何 {@code IMachineBlock}）的
     * 预扫描统计（只统计、不改烘焙结果；见 {@link #planForeignHulls}）。
     *
     * <p>为什么要"预扫描"而不是边遍历边包：硬上限（{@link MachineCtmBakedModel#MAX_FOREIGN_WRAPPERS}）
     * 必须在**动手之前**定下 —— 边包边判断会出现"前 3000 条包了、后面的没包"的半截状态
     * （同一个方块的不同状态条目外观不一致，比不包更难排查）。
     */
    private static int foreignEntries;
    private static int foreignBlocks;
    private static int foreignInstances;
    private static boolean foreignFallback;

    /** describe() 里最多列出几台机器（包装实例数可能上千，日志不能跟着炸）。 */
    private static final int MAX_DESCRIBED_MACHINES = 12;

    /**
     * ★ <b>机器面 CTM（"换 quad 贴图"那条路）的总开关</b> ——
     * ★★ <b>2026-09-18 本轮起默认改为 {@code false}</b>：<b>已被实机证明对本项目要覆盖的机器无效。</b>
     *
     * <h3>证据（玩家实机日志，2026-09-18 20:26:07）</h3>
     * <pre>
     *   CTM: 【delegate 空表】机器 Block{gtlmodular:nanochip_vacuum_input_hatch} 的 delegate
     *        在这次采样里返回了 null/空 quads（state=…）—— 已原样转发
     *   …（同一条 WARN 在**每一个**机器方块上都出现，一屏几十条）
     * </pre>
     * 根因（不是"这次 ModelData 没给到"，而是结构性的）：<b>GTCEu 机器不是用烘焙 quad 画的</b>，
     * 而是走 LDLib 的 {@code IRenderer} 逐 tile 绘制；{@code LDLRendererModel$RendererBakedModel} 的
     * 3 参 {@code getQuads} 字节码就是 {@code 0: invokestatic Collections.emptyList / 3: areturn}
     * （{@link MachineCtmBakedModel} 类注释里有完整字节码）。
     * ⇒ 本开关那条"在 5 参 {@code getQuads} 里把外壳那一张 sprite 换掉"的路，机器上<b>永远拿不到 quad</b>
     * ⇒ <b>什么也换不了</b>（守卫起效、机器没隐形，这是唯一的好消息）。
     *
     * <h3>结论：改用「外壳罩」</h3>
     * 本轮新增 {@code com.gtlmodular.client.renderer.NacMachineShellRenderer}：
     * <b>不改别人的模型 / 渲染器</b>，只读方块 + 在 {@code RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS}
     * 里<b>自己叠一层外壳</b>（正脸不画，其余 5 面用同一套 47 格 CTM 选格 + 逐面取光）。
     * ⇒ 对外观的负责方从本开关换成了 {@code NacMachineShellRenderer.ENABLED}（默认 true）。
     *
     * <h3>为什么保留代码与开关（不删）</h3>
     * <ul>
     *   <li>它是"quad 路径"的唯一实现，且四道防线（纯转发 getModelData / 原样转发空表 /
     *       限流 WARN / 兜底 catch）都还正确 —— 未来若某个整合包 / 某条渲染路径真的给了机器 quad
     *       （例如 GTCEu 改了渲染实现），把本开关改回 {@code true} 即可重新启用，无需重写；</li>
     *   <li>关掉（{@code false}）的语义 = <b>与没有这个功能完全等价</b>：不包装任何机器模型
     *       （{@code new MachineCtmBakedModel(…)} 唯一构造点被本开关短路）、不进 {@link #MACHINES}、
     *       不装懒解析钩子 ⇒ 机器模型原样是 LDLib 的 {@code RendererBakedModel}；
     *       机壳 / 玻璃 / 线圈那三条 {@link #TARGETS} 路径<b>不受</b>本开关影响。</li>
     * </ul>
     *
     * <p>★ 自检：{@code _census/check_nac_shell.js} 断言本常量为 {@code false}，
     * 并断言 {@code NacMachineShellRenderer.ENABLED} 为 {@code true}（两个默认值一起钉住）。
     */
    public static final boolean MACHINE_HULL_CTM = false;

    private CtmClientSetup() {
    }

    /**
     * 一条 CTM 目标。
     *
     * @param spritePrefix    变体 sprite 前缀（底层；必须写字面量，见 static 块里的说明）
     * @param compact         {@code true} = 5 格 compact（NH 的 {@code method=compact}）
     * @param litSpritePrefix 前景层（点亮态）的变体前缀；{@code null} = 该方块没有独立前景层。
     *                        只在"该模型确实多于一层"时生效（GTCEu 的 {@code active=true} 两层模型），
     *                        见 {@link LayeredCtmBakedModel}。
     * @param block           方块（烘焙回调里才取，避免静态初始化碰注册表）
     */
    private record Target(String spritePrefix, boolean compact, String litSpritePrefix, Supplier<Block> block) {

        /** 单层方块的写法（机壳 / 玻璃用它，保持 `new Target("…", true|false, () -> …)` 的既有形态）。 */
        Target(String spritePrefix, boolean compact, Supplier<Block> block) {
            this(spritePrefix, compact, null, block);
        }
    }

    /**
     * ★ <b>预扫描（本轮新增）</b>：把"白名单 / TARGETS 之外的任何 {@code IMachineBlock}"都算成
     * 机器面 CTM 的目标 —— 这正是 NH 的行为（NH 对**任意** {@code MTEHatch} 调
     * {@code updateTexture(CASING_INDEX_WHITE)}：别的 mod 的仓进了 NAC，外壳也变成纳米芯片接口机壳）。
     *
     * <p><b>为什么在烘焙期按 {@code IMachineBlock} 认、而不是按注册名白名单</b>：
     * 白名单只登记了"外壳<b>恒为</b>接口机壳"的 14 台（{@link CtmHullGroup#MACHINE_HULL_IDS}），
     * 而"谁参与 CTM"的真实判据在<b>渲染期</b>：① 这一格是某台成形 NAC 的仓位（{@code NacSlotIndex}）、
     * ② 该 quad 的 sprite 属于 GTCEu 的 tier 机壳族。烘焙期做不到这两条判断（那时没有世界、也没有
     * 邻居），所以这里只负责"把可能用到的模型都包上一层薄壳"（包一层本身不分配、不预烘焙：
     * 真正的活都在每个 quad 上按条件做）。
     *
     * <p><b>硬上限 / 回退</b>（{@link MachineCtmBakedModel#MAX_FOREIGN_WRAPPERS}）：本条路会把范围从
     * "14 台"扩到"全包任何机器方块"，一旦某个整合包的机器方块数量爆炸，包装实例数会跟着爆炸
     * （{@code MACHINES} 是 {@code CopyOnWriteArrayList}，add 是 O(n)）。所以：
     * <ul>
     *   <li>先按 {@code (delegate 实例, 方块)} 去重（同一个方块的几十条状态模型条目共用同一份 LDLib
     *       烘焙模型 ⇒ 通常只算 1 个实例），见 {@link MachineCtmBakedModel#planOnce}；</li>
     *   <li>去重后的实例数 &gt; 上限 ⇒ <b>回退</b>：本轮一个外来机器都不包，只保留原本的白名单那 14 台，
     *       并打一条带具体数字的 WARN（宁可少一层观感，也不要卡在资源重载上）。</li>
     * </ul>
     *
     * @return 本轮接受的「外来机器」条目键集合（空集 = 不包装任何外来机器）
     */
    private static Set<ResourceLocation> planForeignHulls(Map<ResourceLocation, BakedModel> models) {
        foreignEntries = 0;
        foreignBlocks = 0;
        foreignInstances = 0;
        foreignFallback = false;
        if (!MACHINE_HULL_CTM) {
            return Set.of(); // 总开关关掉 ⇒ 连预扫描都不做（零包装，与没有这个功能等价）
        }
        Set<ResourceLocation> accepted = new LinkedHashSet<>();
        Set<Block> blocks = new HashSet<>();
        int instances = 0;
        for (Map.Entry<ResourceLocation, BakedModel> entry : models.entrySet()) {
            ResourceLocation key = entry.getKey();
            // TARGETS（机壳 / 玻璃 / 线圈）走各自的老路，白名单那 14 台走"基准 sprite 匹配"，都不在这里
            if (TARGETS.get(key.getPath()) != null) {
                continue;
            }
            if (GTLModular.MOD_ID.equals(key.getNamespace())
                    && CtmHullGroup.machineHullIdSet().contains(key.getPath())) {
                continue;
            }
            BakedModel delegate = entry.getValue();
            if (delegate instanceof MachineCtmBakedModel || delegate instanceof ConnectedCtmBakedModel
                    || delegate instanceof LayeredCtmBakedModel) {
                continue; // 已经是我们包过的（理论上不会，防御性）
            }
            // ★ 包装判据：任何 IMachineBlock（MetaMachineBlock implements IMachineBlock，javap 确认）
            // ⚠️ 不能写成 `instanceof IMachineBlock machineBlock` —— Java 的**模式变量类型 = 模式类型**
            //   （不是 Block∩IMachineBlock）⇒ 直接拿去当 Block 用会编译失败（实测 3 条编译错误之一）。
            Block block = ForgeRegistries.BLOCKS.getValue(key);
            if (block == null || !(block instanceof IMachineBlock)) {
                continue;
            }
            accepted.add(key);
            blocks.add(block);
            if (MachineCtmBakedModel.planOnce(delegate, block)) {
                instances++;
            }
        }
        foreignEntries = accepted.size();
        foreignBlocks = blocks.size();
        foreignInstances = instances;
        if (instances > MachineCtmBakedModel.MAX_FOREIGN_WRAPPERS) {
            foreignFallback = true;
            GTLModular.LOGGER.warn("[gtlmodular] CTM: 【硬上限回退】白名单之外的机器方块太多 —— "
                            + "命中 {} 个方块 / {} 条状态模型条目 / {} 个（去重后）包装实例，"
                            + "超过上限 {} ⇒ 本轮**只包装白名单那 {} 台**（外来机器一个都不包）。"
                            + "机器面 CTM 的判据本身没问题，是「全包任何 IMachineBlock」这条路在本整合包里太宽；"
                            + "要放开请调 MachineCtmBakedModel.MAX_FOREIGN_WRAPPERS 并回传这条日志。",
                    foreignBlocks, foreignEntries, instances, MachineCtmBakedModel.MAX_FOREIGN_WRAPPERS,
                    CtmHullGroup.MACHINE_HULL_IDS.length);
            MachineCtmBakedModel.clearReuse();
            return Set.of();
        }
        return accepted;
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        // ★ 2026-09-17 本轮复核修掉的泄漏：**每次资源重载都要清空重建这三张表**。
        //   旧实现只有 add(...) 没有 clear() ⇒ F3+H / 资源包切换每重载一次就多留一批旧包装实例
        //   （旧模型 + 47×sprite + frameCache 常驻），且 resolveAll()/describe() 会继续处理旧模型、日志越打越长。
        //   顺带把"已就绪只打一次"的两个计数器也复位（新一轮资源要能重新报一次）。
        //   ⚠️ 三张表是 CopyOnWriteArrayList：写只在烘焙线程，读在渲染线程（懒解析钩子 / describe）——
        //   清空 + 追加与并发遍历都安全（换成 ArrayList 后这里会引入 ConcurrentModificationException 风险）。
        WRAPPED.clear();
        LAYERED.clear();
        MACHINES.clear();
        loggedReady = false;
        notReadyLogged = 0;
        // ★ 2026-09-18：三份"跨实例共享"的派生态也要在本轮开头作废 ——
        //   ① 47 张变体的全局共享缓存（图集换了 ⇒ 旧 sprite 全部作废）；
        //   ② (delegate, 方块) 复用表（上一轮的包装实例全部作废）；
        //   ③ NAC 仓位索引（世界的机器实例/同步值都可能已经变了）。
        MachineCtmBakedModel.invalidateSharedVariants();
        MachineCtmBakedModel.clearReuse();
        NacSlotIndex.reset();
        // ★ 2026-09-18：④ 新渲染器（「NAC 机器外壳罩」）的派生态也要在本轮开头作废 ——
        //   它的 6×47 罩子 quad 里嵌的是**旧图集**的 sprite、它的面内帧来自旧烘焙模型。
        //   同处复位保证"换资源包之后罩子不会花屏"，并顺手打一条含两个开关现值的日志。
        com.gtlmodular.client.renderer.NacMachineShellRenderer.reset();
        // ★ 单一来源断言（"注册名集合 == 方块集合"）：两个视图键集必须完全一致，
        //   否则会出现"自己换了 sprite、邻居不认它"的单侧连接（外观 = 连了一半的缝）。
        String hullMismatch = CtmHullGroup.hullViewMismatch();
        if (hullMismatch != null) {
            GTLModular.LOGGER.error("[gtlmodular] CTM: 机器外壳白名单的两个视图不一致：{} —— 这会造成"
                    + "「自己换了 sprite、邻居不认它」的单侧连接；请检查 CtmHullGroup.MACHINE_HULL_IDS 与注册处。",
                    hullMismatch);
        }
        // ★ 注入懒解析钩子：渲染线程发现"还没全部就绪"时会回调 resolveAll()（见 ConnectedCtmBakedModel.maybeResolveLazily）
        ConnectedCtmBakedModel.lazyResolver = model -> resolveAll();
        if (MACHINE_HULL_CTM) {
            MachineCtmBakedModel.lazyResolver = CtmClientSetup::resolveAll;
        } else {
            MachineCtmBakedModel.lazyResolver = null; // 总开关关掉 ⇒ 一个机器包装都不存在，钩子也不装
        }
        Map<ResourceLocation, BakedModel> models = event.getModels();
        // ★ 2026-09-18：预扫描"外来机器方块"（任何 IMachineBlock）—— 只统计 + 定下硬上限是否回退，
        //   不动烘焙结果；返回的是本轮接受的条目键集合（见 planForeignHulls 的注释）。
        Set<ResourceLocation> foreignHullKeys = planForeignHulls(models);
        int wrapped = 0;
        int layeredCount = 0;
        int machines = 0;
        int machineEntries = 0;
        for (Map.Entry<ResourceLocation, BakedModel> entry : new ArrayList<>(models.entrySet())) {
            ResourceLocation key = entry.getKey();
            // ★ 机器方块（真空仓 / 主机 / 11 个模块）与"外来机器"（别的 mod 的仓 / GTCEu 自己的仓）：
            //   **渲染期**换外壳 sprite，绝不走预烘焙那条路。
            boolean whitelistedHull = GTLModular.MOD_ID.equals(key.getNamespace())
                    && CtmHullGroup.machineHullIdSet().contains(key.getPath());
            boolean foreignHull = foreignHullKeys.contains(key);
            // ★ 命名空间闸门：默认**只认本模组**；两条例外都是**显式、可审计**的：
            //   ① FOREIGN_ALLOWED —— NH 的 CTM 配置直接要求的那一个 gtceu: 方块（线圈）；
            //   ② foreignHull —— ★ 2026-09-18 新增：任何 IMachineBlock（别的 mod 的仓）。
            //      "是否真的换贴图"由渲染期的两条条件决定（NacSlotIndex 仓位 + tier 机壳族 sprite），
            //      烘焙期只是把它包一层薄壳 ⇒ 不影响任何机器的既有外观（见 MachineCtmBakedModel 类注释）。
            if (!GTLModular.MOD_ID.equals(key.getNamespace())
                    && !foreignHull
                    && !FOREIGN_ALLOWED.contains(key.getNamespace() + ":" + key.getPath())) {
                continue;
            }
            //   ★★ 总开关 MACHINE_HULL_CTM 是这条路的**唯一**入口：它关掉时下面的 if 恒假 ⇒ 零包装。
            boolean machineHull = MACHINE_HULL_CTM
                    && TARGETS.get(key.getPath()) == null
                    && (whitelistedHull || foreignHull);
            if (machineHull) {
                if (!(entry.getValue() instanceof MachineCtmBakedModel)) {
                    Block machineBlock = ForgeRegistries.BLOCKS.getValue(
                            new ResourceLocation(key.getNamespace(), key.getPath()));
                    if (machineBlock != null) {
                        // ★ 同一个 (delegate, 方块) 的几十条状态模型条目复用同一个包装实例
                        //   （见 MachineCtmBakedModel.REUSE）：既省内存，也让 MACHINES（CopyOnWriteArrayList）
                        //   的 add 不会退化成 O(n²)；frameCache 本来就是按 BlockState 分槽的。
                        MachineCtmBakedModel reused = MachineCtmBakedModel.reuseOf(entry.getValue(), machineBlock);
                        MachineCtmBakedModel model = reused;
                        if (model == null) {
                            model = new MachineCtmBakedModel(entry.getValue(), machineBlock);
                            MACHINES.add(model);
                            machines++;
                            MachineCtmBakedModel.bindReuse(entry.getValue(), machineBlock, model);
                        }
                        models.put(key, model);
                        machineEntries++;
                    }
                }
                continue;
            }
            Target target = TARGETS.get(key.getPath());
            if (target == null || entry.getValue() instanceof ConnectedCtmBakedModel
                    || entry.getValue() instanceof LayeredCtmBakedModel) {
                continue;
            }
            Block block = target.block().get();
            if (block == null) {
                continue;
            }
            BlockState frameState = block.defaultBlockState();
            // ★ 登记了前景层前缀时先试分层包装（NH 的 litcoil 前景层）：只有"该模型确实多于一层"
            //   （GTCEu 的 active=true 两层模型）才会成功；单层模型（含 active=false 与物品模型）
            //   返回 null ⇒ 走下面那条**与玻璃/机壳完全相同**的老路。
            LayeredCtmBakedModel layered = target.litSpritePrefix() == null ? null
                    : LayeredCtmBakedModel.tryCreate(entry.getValue(), block,
                            List.of(target.spritePrefix(), target.litSpritePrefix()), frameState);
            if (layered != null) {
                models.put(key, layered);
                LAYERED.add(layered);
                layeredCount++;
            } else {
                // ★ 最后一个参数 = NH connect=tile 的「同贴图组」：
                //   只有**网格接口机壳**那一条为 true（NH 的 properties 就是 connect=tile，且 GT5U 的
                //   机器面 —— 真空仓/主机/模块 —— 外壳画的就是这张贴图 ⇒ 机壳朝机器那一侧也算连通）；
                //   玻璃（compact，已冻结）与线圈（connect=block）恒 false ⇒ 行为逐字节不变。
                ConnectedCtmBakedModel model = new ConnectedCtmBakedModel(
                        entry.getValue(), block, target.spritePrefix(), target.compact(), frameState,
                        CtmHullGroup.MESH_CASING_ID.equals(key.getPath()));
                models.put(key, model);
                WRAPPED.add(model);
            }
            wrapped++;
        }
        // ★ 断言（本轮复核要求）：**重载后表大小 == 本轮包装数** —— 三张表在方法开头被 clear() 过，
        //   所以这里必须精确相等；不等就说明有人把包装实例加到了别处（或 clear 被删掉 ⇒ 线性泄漏）。
        assertTableSize("WRAPPED", WRAPPED.size(), wrapped - layeredCount);
        assertTableSize("LAYERED", LAYERED.size(), layeredCount);
        assertTableSize("MACHINES", MACHINES.size(), machines);
        if (wrapped == 0 && machines == 0) {
            GTLModular.LOGGER.warn("[gtlmodular] CTM: 模型烘焙结果里没找到任何目标方块的条目 —— 连接纹理会退回「孤立变体」（= 现状）。"
                    + (MACHINE_HULL_CTM ? "" : "（机器面 CTM 总开关已关闭，机器条目**按设计**不包装。）")
                    + "若玩家反馈花纹仍未连片，请把这条日志连同 mods 列表一起回传。");
        } else {
            GTLModular.LOGGER.info("[gtlmodular] CTM: 已包装 {} 个方块模型条目 + {} 个机器方块条目（{}）",
                    wrapped, machines, describe());
            // ★ 2026-09-18 新增：单独报一行「机器面 CTM 的覆盖范围」——
            //   含 N 个机器方块条目（= 真实被换装的模型条目数，含白名单那 14 台的每一条状态条目），
            //   以及"别的 mod 的仓"这一路到底进来了多少、有没有触发硬上限回退。
            GTLModular.LOGGER.info("[gtlmodular] CTM: 机器面覆盖范围：含 {} 个机器方块条目"
                            + "（白名单 {} 台【外壳恒为接口机壳】+ 外来 IMachineBlock {} 个方块 / {} 条状态模型条目"
                            + " / {} 个包装实例）{}",
                    machineEntries, CtmHullGroup.MACHINE_HULL_IDS.length,
                    foreignBlocks, foreignEntries, foreignInstances,
                    foreignFallback ? "—— ★ 已触发硬上限回退：外来机器一个都没包（见上一条 WARN）"
                            : (MACHINE_HULL_CTM ? "" : "（总开关已关闭 ⇒ 零包装）"));
        }
        resolveAll();
    }

    /**
     * 表大小断言：清空重建之后，表的条目数必须等于本轮的包装数（不等 = 累积 / 加错了地方）。
     *
     * <p>只打 ERROR 日志、不抛异常（客户端线程上抛异常会连带整个资源重载失败）。
     */
    private static void assertTableSize(String name, int actual, int expected) {
        if (actual != expected) {
            GTLModular.LOGGER.error("[gtlmodular] CTM: 内部不一致 —— {} 表里有 {} 条，本轮只包装了 {} 条"
                    + "（要么重载没清空（线性泄漏），要么有别的代码往表里加）；功能仍可用但请回传这条日志。",
                    name, actual, expected);
        }
    }

    @SubscribeEvent
    public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        resolveAll();
    }

    /**
     * ★ <b>图集缝合完成</b>时再解析一次。
     *
     * <p>实机教训：模型烘焙（19:03:18）会**早于**图集缝合完成（`Created: …blocks.png-atlas`，19:03:26）
     * ⇒ 只靠烘焙事件解析必然"全部找不到 sprite"。这里在缝合完成后补一次。
     * （渲染路径上还有一层懒解析兜底，见 {@link ConnectedCtmBakedModel#maybeResolveLazily}。）
     */
    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Post event) {
        if (!InventoryMenu.BLOCK_ATLAS.equals(event.getAtlas().location())) {
            return;
        }
        resolveAll();
    }

    private static void resolveAll() {
        if (WRAPPED.isEmpty() && LAYERED.isEmpty() && MACHINES.isEmpty()) {
            return;
        }
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        int ready = 0;
        int missing = 0;
        int total = WRAPPED.size() + LAYERED.size() + MACHINES.size();
        for (ConnectedCtmBakedModel model : WRAPPED) {
            if (model.resolve(atlas, model.block().defaultBlockState())) {
                ready++;
            }
            missing += model.missedSprites();
        }
        for (LayeredCtmBakedModel model : LAYERED) {
            if (model.resolve(atlas, model.block().defaultBlockState())) {
                ready++;
            }
            missing += model.missedSprites();
        }
        for (MachineCtmBakedModel model : MACHINES) {
            if (model.resolve(atlas)) {
                ready++;
            }
            missing += model.missedSprites();
        }
        if (ready == total) {
            if (!loggedReady) {
                loggedReady = true;
                GTLModular.LOGGER.info("[gtlmodular] CTM: 全部变体 sprite 已就绪（命名空间 {}），连接纹理按邻居选变体生效（{}）",
                        spriteNamespaceOfFirst(), describe());
            }
            return;
        }
        // 还没齐：可能是"图集还没缝合好"（会被后续事件/懒解析重试），也可能是资源没进图集（需要人看日志）
        int attempts = ++notReadyLogged;
        if (attempts <= 3 || attempts % 10 == 0) {
            GTLModular.LOGGER.warn("[gtlmodular] CTM: 变体 sprite 尚未就绪（就绪 {}/{}，共缺 {} 张，第 {} 次尝试）——"
                            + "会继续重试；若一直不齐，把这条日志连同 mods 列表给我。{}",
                    ready, total, missing, attempts, describe());
        }
    }

    private static String spriteNamespaceOfFirst() {
        if (!WRAPPED.isEmpty()) {
            return WRAPPED.get(0).spriteNamespace();
        }
        if (!LAYERED.isEmpty()) {
            return LAYERED.get(0).spriteNamespace();
        }
        return GTLModular.MOD_ID;
    }

    private static String describe() {
        StringBuilder sb = new StringBuilder();
        for (ConnectedCtmBakedModel model : WRAPPED) {
            ConnectedCtmBakedModel.Dump d = model.describe();
            if (sb.length() > 0) {
                sb.append('；');
            }
            sb.append(d.spritePrefix()).append(" × ").append(d.variantCount())
                    .append(d.frameComplete() ? "（面内坐标帧 OK）" : "（面内坐标帧未知 ⇒ 回退第 0 格）");
        }
        for (LayeredCtmBakedModel model : LAYERED) {
            if (sb.length() > 0) {
                sb.append('；');
            }
            sb.append('[').append(model.layerCount()).append(" 层] ").append(model.summary());
        }
        if (!MACHINES.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('；');
            }
            // 机器条目按方块去重显示（一台机器可能有多条状态模型条目）；
            // ★ 2026-09-18：包装范围扩到"任何 IMachineBlock"之后实例数可能上千 ⇒ 日志最多列
            //   MAX_DESCRIBED_MACHINES 台，其余只报数量（否则这一行能长到几 KB）。
            java.util.Set<String> shown = new java.util.LinkedHashSet<>();
            for (MachineCtmBakedModel model : MACHINES) {
                if (shown.size() >= MAX_DESCRIBED_MACHINES) {
                    break;
                }
                if (shown.add(model.block().getDescriptionId())) {
                    sb.append("[机器] ").append(model.block().getName().getString()).append(' ').append(model.summary())
                            .append(' ');
                }
            }
            sb.append("（共 ").append(MACHINES.size()).append(" 个包装实例");
            if (MACHINES.size() > shown.size()) {
                sb.append("，日志只列前 ").append(shown.size()).append(" 台");
            }
            sb.append("）");
        }
        return sb.toString();
    }
}
