package com.gtlmodular.client.ctm;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.NanochipBlocks;
import com.gtlmodular.common.data.nanochip.NanochipMachines;
import com.gtlmodular.common.machine.nanochip.NanochipVacuumHatchMachine;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * <b>「外壳贴图 = 网格接口机壳」的方块组</b> —— NH 的 {@code connect=tile} 判据的 1.20.1 落地，
 * 外加真空仓<b>两态外壳</b>的口径常量。
 *
 * <h2>为什么需要它（玩家的定案 + NH 的证据）</h2>
 * 玩家在 GTNH 实机看到：真空仓（以及多方块）的外壳面<b>是</b>带着蓝黑连片纹理、与周围纳米芯片接口机壳
 * 连成一片的。机制上说得通，NH 的机壳 CTM 配置写的是：
 * <pre>
 *   assets/gregtech/mcpatcher/ctm/nanochipmeshinterfacecasing/nanochipmeshinterfacecasing.properties
 *     matchTiles=gregtech:iconsets/NANOCHIP_MESH_INTERFACE_CASING
 *     method=ctm   faces=all   tiles=0-46   connect=tile        ← ★ 关键
 * </pre>
 * {@code matchTiles} 匹配的是<b>贴图名</b>，{@code connect=tile} 判的是「邻居那一侧画出来的 tile 是不是同一张」，
 * <b>不是</b>「邻居是不是同一种方块」。GT5U 的机器面渲染走 {@code RenderBlocks} 系路径 ⇒ MCPatcher 的连接
 * 查表对<b>机器面同样生效</b>。所以我们不能按"方块身份"判邻居 —— 那会让机器面永远画第 0 格
 * （孤立格 = 玩家截图里那条缝的根因）。
 *
 * <h2>判据实现（取舍写清楚）</h2>
 * 1.20.1 这边<b>做不到</b>去读邻居机器"将要画哪张 sprite"（代价极高：LDLib 的
 * {@code RendererBakedModel} 的 3 参 {@code getQuads} 恒返回空表 —— 见 {@link MachineCtmBakedModel} 的类注释，
 * 而带 ModelData 的 5 参只有在<b>那一格自己的</b>渲染期才有世界坐标）。所以按 NH 的语义<b>等价改写</b>
 * 成一张<b>显式白名单</b>：{@code 机壳本体 ∪ 外壳用同一张贴图的机器方块（主机 / 11 个模块 / 2 个真空仓）}。
 * <b>不在 NAC 里</b>的其它机器一律<b>不连</b>（能源仓 / 总线 / 消音器外壳是别的机壳 ⇒ 不在 NAC 时 NH 里也不连；
 * ★ 2026-09-18 起：这类仓<b>一旦被收进成形 NAC 的仓位</b>就会被换上接口机壳贴图 ⇒ 那时就连 ——
 * 见下面「★ 2026-09-18 扩展」一节与 {@link #rendersMeshCasingTile} 的实现）。
 *
 * <p>★ <b>真空仓是两态的</b>（见 {@code NanochipVacuumHatchMachine#isCasingFromStructure}）：
 * 未并入任何成形结构时它的外壳是 GTCEu 的 UIV 机壳（{@code gtceu:block/casings/voltage/uiv/*}，
 * 见 {@link #tierCasingSprite}），<b>不是</b>接口机壳 ⇒ 那一态下它既不吃连接、也不让别人连它
 * （NH 的 {@code connect=tile} 看的是贴图，贴图不同就不连）。
 *
 * <p>★ <b>只读方块 + 一个同步字段，不读烘焙 quads</b>：判定只需要
 * {@code level.getBlockState(pos).getBlock()}、注册名，以及真空仓那一个 {@code @DescSynced} 布尔
 * ⇒ 渲染热路径上就是几次 Set.contains + 偶尔一次 {@code MetaMachine.getMachine}。
 *
 * <h2>⚠️ 口径差异（已知风险，写在这里防以后再纠结）</h2>
 * <b>NH 的 {@code connect=tile} 判的是「贴图」</b>（邻居那一侧画出来的 tile 是不是同一张），
 * <b>我们判的是「方块身份 + 真空仓两态」</b>——这是 1.20.1 这边的等价改写（读邻居"将要画哪张 sprite"
 * 代价极高，见上面第 1 点）。两者**只在一种情况下会不等价**：某个<b>不在名单里</b>的方块
 * 也画了这张接口机壳贴图（那时 NH 会连、我们不连 ⇒ 出现"连了一半"的缝）。
 *
 * <p>当前证据（2026-09-17 复核员逐文件 grep + 本轮抽查）：
 * 本模组内画 {@code block/casings/nanochip_mesh_interface_casing} 的**只有**名单里这 14 台机器
 * 与机壳本体（主机 / 11 个模块控制器 / 2 个真空仓都已登记）；GTCEu 侧没有任何机器用这张贴图
 * （本模组独有的资源）。⇒ <b>当前名单 == "画这张贴图者"，偏差为空</b>。
 * 以后若新增一台"外壳用这张贴图"的机器，<b>必须同时加进 {@link #MACHINE_HULL_IDS}</b>
 * （自检 {@code _census/check_ctm_machine.js} 会与注册处逐条核对，漏加会报红）。
 *
 * <h2>★ 2026-09-18 扩展：白名单之外——「任何仓只要进了 NAC 就换外壳」</h2>
 * NH 对<b>任意</b> {@code MTEHatch} 都调 {@code updateTexture(CASING_INDEX_WHITE)}
 * （{@code MTENanochipAssemblyModuleBase#ignoreAndAcceptHatch}）⇒ NH 里别的 mod 的仓进了 NAC，
 * 外壳同样变成纳米芯片接口机壳 ⇒ 同样连片。1.20.1 这边于是分两条口径：
 * <ol>
 *   <li><b>上面那张 14 台白名单</b> = "外壳<b>恒为</b>接口机壳"的方块 ⇒ 走"基准 sprite 匹配"老路
 *       （{@link #MESH_BASE_SPRITE}）；</li>
 *   <li><b>床位判据</b>（本轮新增）= "这一格是某台<b>成形 NAC</b> 的仓位"（{@link NacSlotIndex}，
 *       数据由主机/模块的 {@code @DescSynced syncedNacSlots} 推下来）⇒ 该格的外壳若是
 *       <b>GTCEu 的 tier 机壳族</b>（{@link #TIER_CASING_TEXTURE_PREFIX}，别的 mod 的仓走的是
 *       GTCEu 的 {@code TieredHullMachineRenderer}，逐字节确认），就换成接口机壳的 47 格变体。</li>
 * </ol>
 * ⇒ 真值表变成「机壳本体 ∪ 白名单 14 台 ∪ <b>任何被收进 NAC 仓位的机器</b>」；拿不到仓位索引时
 * {@link NacSlotIndex#isNacSlot} 恒 false ⇒ 退回"只认白名单"的老行为（外观 = 现状，绝不猜）。
 *
 * <p>★ <b>已知限制（可能出现的"单侧连接 / 半条缝"）</b>：本类的连通判据是"这一格<b>将要</b>被换成
 * 接口机壳贴图"（{@code IMachineBlock + 成形 NAC 仓位}），而**真正换贴图**还要满足
 * "该 quad 的 sprite 属于 GTCEu 的 tier 机壳族"（见 {@link #isTierCasingSpritePath}）。
 * ⇒ 若某台 NAC 仓的外壳<b>不走</b> GTCEu 的 {@code TieredHullMachineRenderer}（自己画别的机壳贴图），
 * 就会出现"邻居把它当连通、它自己却不换贴图" = <b>单侧连接（半条缝）</b>。
 * <p>实机验收建议：<b>特意抽查一台"自绘外壳的仓"</b>（非 GTCEu tier 渲染器的那种），确认没有半条缝；
 * 真要修的话，修法是"让连通判据也变窄"（只认 tier 机壳族 + 白名单），代价是那时候连不连得上取决于
 * 渲染器实现 —— 目前按"宁可多连一格"取舍（GTCEu 自己的 tier 仓与 GTMThings 的无线仓都走
 * {@code TieredHullMachineRenderer}，已逐字节确认）。
 */
public final class CtmHullGroup {

    /** 网格接口机壳的方块注册名（本模组，唯一一个"机壳本体"）。 */
    public static final String MESH_CASING_ID = "nanochip_mesh_interface_casing";

    /**
     * 外壳那一张 sprite 的路径（判据：某个 quad 的 sprite 名等于它 ⇒ 这个 quad 是「外壳面」）。
     *
     * <p>只比<b>路径</b>（不含命名空间）：{@code ConnectedCtmBakedModel.lookup} 也做「先 gtlmodular 再
     * minecraft」两次查找，sprite 究竟注册在哪个命名空间下与渲染无关，路径才是身份。
     */
    public static final String MESH_BASE_SPRITE = "block/casings/nanochip_mesh_interface_casing";

    /** 47 格变体 sprite 前缀（与 {@code CtmClientSetup.TARGETS} 里机壳那条**同一套**贴图）。 */
    public static final String MESH_SPRITE_PREFIX = "block/casings/ctm/nanochip_mesh_interface_casing";

    /**
     * GTCEu 的 tier 机壳贴图目录 + 后缀（真空仓<b>未并入成形结构</b>时的外壳 —— 直接<b>引用</b> GTCEu 的资源，
     * 零拷贝；玩家裁决原文：「GTL里面不也有UIV机械外壳吗？直接引用不就行了」）。
     */
    public static final String TIER_CASING_TEXTURE_PREFIX = "block/casings/voltage/";

    /** GTCEu tier 机壳方块的注册名后缀（{@code <tier>_machine_casing}）。 */
    public static final String TIER_CASING_ID_SUFFIX = "_machine_casing";

    /**
     * ★ <b>tier 索引 → tier 名字</b>：GTCEu 自己的入口是 {@code GTValues.VN[tier]}（{@code String[]}，15 项）。
     *
     * <p><b>为什么是它（逐字节证据，不是猜）</b>：
     * <pre>
     * // ① GTCEu 注册 tier 机壳方块时用的就是这条链
     * javap -p -c libs/gtceu-1.20.1-1.4.4.jar → GTBlocks.createMachineCasingBlock(int)
     *    0: getstatic     GTValues.VN:[Ljava/lang/String;
     *    3: iload_0
     *    4: aaload                              // VN[tier]
     *    5: getstatic     java/util/Locale.ROOT
     *    8: invokevirtual String.toLowerCase:(Ljava/util/Locale;)Ljava/lang/String;
     *   11: astore_1                             // String name = VN[tier].toLowerCase(Locale.ROOT)
     *   15: ldc           String "%s_machine_casing"
     *   26: invokevirtual String.formatted       // block(name + "_machine_casing", …)
     *   83: invokestatic  GTModels.createMachineCasingModel:(Ljava/lang/String;)…   // 用同一个 name 生成模型
     *
     * // ② 模型的贴图路径也由同一个 name 拼出来
     * javap -p -c … → GTModels.lambda$createMachineCasingModel$6(String)
     *         ldc   String "block/casings/voltage/%s/bottom"
     *         ldc   String "block/casings/voltage/%s/top"
     *         ldc   String "block/casings/voltage/%s/side"
     *   ⇒ 落到资源上就是 assets/gtceu/models/block/<name>_machine_casing.json →
     *      {"bottom":"gtceu:block/casings/voltage/<name>/bottom","side":…,"top":…}
     *
     * // ③ VN 的内容（javap -c GTValues.<clinit> 逐项）：索引 11 = "UIV"
     *   [ULV LV MV HV EV IV LuV ZPM UV UHV UEV UIV UXV OpV MAX]
     * </pre>
     * ⇒ 本模组用<b>同一表达式</b>（{@code GTValues.VN[tier].toLowerCase(Locale.ROOT)}）推导，改
     * {@code NanochipMachines.VACUUM_HATCH_TIER} 就自动跟着变，且与 GTCEu 的注册名/
     * 贴图路径永远一致（自检 {@code _census/check_ctm_machine.js} 会拿 gtceu jar 里的
     * {@code uiv_machine_casing} 模型 json 现读核对）。
     *
     * <h4>口径澄清（写死在这里，防以后再纠结）</h4>
     * GTCEu 的 UIV 机壳美术是<b>金色</b>；GT5U 原生 {@code MACHINE_UIV_*} 是白底（玩家 NH 截图里那抹金色
     * 来自他的 {@code Modernity-GTNH} 资源包覆盖：2103 B vs jar 内 406 B）。玩家已明确选择"引用 GTL 的"
     * ⇒ <b>金色是对的</b>，也是本包内与其它 UIV 机器一致的观感；因此<b>不</b>再实现 GT5U 那一步
     * {@code × Dyes.MACHINE_METAL.getRGBA()}（那一步在 NH 原生里调制色是 255/255/255，本来就等于不变色），
     * 也不再往本模组 jar 里拷任何机壳美术。
     */
    public static String tierName(int tier) {
        if (tier < 0 || tier >= GTValues.VN.length) {
            return null;
        }
        return GTValues.VN[tier].toLowerCase(Locale.ROOT);
    }

    /** {@code tier} 档机壳某一面的 sprite 路径（{@code face} ∈ {side,top,bottom}），对齐 GTCEu 的命名。 */
    public static String tierCasingSprite(int tier, String face) {
        String name = tierName(tier);
        if (name == null) {
            return null;
        }
        return TIER_CASING_TEXTURE_PREFIX + name + "/" + face;
    }

    /** {@code tier} 档机壳方块 id（{@code gtceu:<name>_machine_casing}）—— 只用于自检/诊断的交叉核对。 */
    public static ResourceLocation tierCasingBlockId(int tier) {
        String name = tierName(tier);
        return name == null ? null : new ResourceLocation("gtceu", name + TIER_CASING_ID_SUFFIX);
    }

    /** GTCEu tier 机壳模型允许的三个面（与 {@code GTModels.createMachineCasingModel} 的贴图键同名）。 */
    private static final String[] TIER_CASING_FACES = { "side", "top", "bottom" };

    /**
     * ★ <b>tier 机壳族 sprite 判据</b>（本轮新增）：这个 sprite <b>路径</b>是不是 GTCEu 的 tier 机壳面。
     *
     * <p>为什么别的 mod 的仓也走这一族（<b>逐字节取证，不是假设</b>）：
     * <pre>
     * javap -p -c gtceu-1.20.1-1.4.4.jar → TieredHullMachineRenderer
     *   ldc "block/casings/voltage/%s/bottom" + getstatic GTValues.VN
     *   ldc "block/casings/voltage/%s/top"    + getstatic GTValues.VN
     *   ldc "block/casings/voltage/%s/side"   + getstatic GTValues.VN
     * ⇒ GTCEu 所有"带 tier 外壳"的机器/仓（{@code overlayTieredHullRenderer} / {@code workableTieredHullRenderer}）
     *   画的外壳都是 {@code gtceu:block/casings/voltage/<VN[tier].toLowerCase>/<side|top|bottom>}。
     *   实机里那台创造能源仓 = GTMThings 的 {@code gtmthings:*_wireless_energy_input_hatch}：
     *     · 类 {@code WirelessEnergyHatchPartMachine extends TieredIOPartMachine}（= IMultiPart ✓）
     *     · 注册 {@code MachineBuilder.overlayTieredHullRenderer("wireless_energy_hatch")}
     *     · 它自己的模型 json 里<b>只有</b>正脸 overlay（{@code gtmthings:block/overlay/machine/overlay_energy_on_wireless}）
     *       —— 外壳层完全由 GTCEu 的 TieredHullMachineRenderer 提供 ⇒ 就是这个族。
     * </pre>
     *
     * <p>★ <b>判据只放行 {@code block/casings/voltage/} 之下、面名 ∈ {side,top,bottom} 的路径</b>：
     * 放宽成"任何 sprite"会把正脸 overlay / 发光层 / bloom / 管道叠加条一起换掉
     * （{@code _census/check_ctm_foreign.js} 有断言盯着这条前缀）。
     */
    public static boolean isTierCasingSpritePath(String path) {
        if (path == null || !path.startsWith(TIER_CASING_TEXTURE_PREFIX)) {
            return false; // ★ 只认 tier 机壳目录（block/casings/voltage/），别的一律不碰
        }
        String rest = path.substring(TIER_CASING_TEXTURE_PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            return false;
        }
        String face = rest.substring(slash + 1);
        for (String allowed : TIER_CASING_FACES) {
            if (allowed.equals(face)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ★ <b>显式白名单</b>：外壳用的是「纳米芯片接口机壳」那一张贴图的机器方块注册名。
     *
     * <p>逐条对照真实注册处（{@code NanochipMachines} / {@code NanochipModuleKind}）：
     * <ul>
     *   <li>{@code nanochip_assembly_complex} —— 主机控制器（{@code registerComplex()}，
     *       {@code CASING_TEX} 就是这张）；</li>
     *   <li>11 个模块控制器 —— {@code NanochipModuleKind.values()} 的 {@code machineId()}
     *       （{@code MODULE_CASING_TEX === CASING_TEX}，2026-09-17 已按 NH 字节码改成与主机同款）；</li>
     *   <li>{@code nanochip_vacuum_input_hatch} / {@code nanochip_vacuum_output_hatch} —— 真空仓
     *       （{@code VacuumHatchRenderer.HATCH_CASING_TEX}，NH 的 {@code CASING_INDEX_WHITE}）。</li>
     * </ul>
     * 自检 {@code _census/check_ctm_machine.js} 会把这张表与枚举源码/注册处**逐条**核对，
     * 多一个少一个都报红（防手抄漂移）。
     *
     * <p>⚠️ 这份名单<b>不是</b>"所有 GTCEu 机器"：能源仓 / 总线 / 消音器 / 激光仓等外壳是别的机壳，
     * <b>不在 NAC 里</b>时按 NH 的 {@code connect=tile} 它们<b>不连</b>，所以不在这里。
     * ★ 2026-09-18 起它们<b>被收进成形 NAC 仓位</b>时会被换上接口机壳贴图（那时就连）——
     * 那条路不进这张名单，判据是"仓位索引 + tier 机壳族 sprite"，见类注释的扩展一节。
     */
    public static final String[] MACHINE_HULL_IDS = {
            "nanochip_assembly_complex",
            "nanochip_assembly_matrix",
            "nanochip_smd_processor",
            "nanochip_board_processor",
            "nanochip_etching_array",
            "nanochip_cutting_chamber",
            "nanochip_wire_tracer",
            "nanochip_superconductor_splitter",
            "nanochip_optical_organizer",
            "nanochip_exotic_organizer",
            "nanochip_cosmic_organizer",
            "nanochip_supracausal_organizer",
            "nanochip_biological_coordinator",
            "nanochip_encasement_wrapper",
            "nanochip_splitter",
            "nanochip_vacuum_input_hatch",
            "nanochip_vacuum_output_hatch",
    };

    /** 真空仓（两态那两台）的注册名 —— 只有它们的外壳随"是否并入成形结构"变。 */
    public static final String[] VACUUM_HATCH_IDS = {
            "nanochip_vacuum_input_hatch",
            "nanochip_vacuum_output_hatch",
    };

    private static volatile Set<Block> machineHullBlocks;
    private static volatile Set<Block> vacuumHatchBlocks;
    private static volatile Set<String> machineHullIdSet;
    private static volatile Boolean tierCasingRegistered;

    private CtmHullGroup() {
    }

    /**
     * 白名单的**注册名集合** = 烘焙回调按注册名认目标用的那个视图。
     *
     * <p>★ 2026-09-17 本轮复核后改成**由方块视图派生**（原来是照 {@link #MACHINE_HULL_IDS} 字面量另建一份）：
     * 两个视图各自从字面量现建时，注册名一旦漂移就会「字符串视图命中 / 方块视图少一台」⇒
     * 那台自己换了 sprite、邻居不认它 = <b>单侧连接</b>。派生之后键集恒等，
     * 漂移只会变成"这台不参与 CTM（两边一起不认）"，且有 WARN。
     */
    public static Set<String> machineHullIdSet() {
        Set<String> cached = machineHullIdSet;
        if (cached != null) {
            return cached;
        }
        Set<String> built = new LinkedHashSet<>();
        for (Block block : machineHullBlocks()) {
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
            if (key == null) {
                continue; // 方块已注册就必有 key；保守跳过（此时 hullViewMismatch() 会把它报出来）
            }
            built.add(key.getPath());
        }
        machineHullIdSet = built;
        return built;
    }

    /** 机壳本体（懒取：绝不在类加载期碰注册表，见 {@code check_registration_path.js} 的口径）。 */
    static Block meshCasingBlock() {
        return NanochipBlocks.MESH_INTERFACE_CASING.get();
    }

    /** 白名单里的 14 台机器方块（懒建，运行期取一次）。id 取不到时打 WARN —— 便于发现注册名漂移。 */
    static Set<Block> machineHullBlocks() {
        Set<Block> cached = machineHullBlocks;
        if (cached != null) {
            return cached;
        }
        Set<Block> built = new LinkedHashSet<>();
        for (String id : MACHINE_HULL_IDS) {
            Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(GTLModular.MOD_ID, id));
            if (b == null) {
                GTLModular.LOGGER.warn("[gtlmodular] CTM: 白名单里的机器方块 {}:{} 取不到（注册名漂移？）"
                        + "—— 它的外壳面不会参与连接纹理；★ 注册名视图由本视图派生，"
                        + "所以它**两侧一起不认**（不会出现「自己换了 sprite、邻居不认」的单侧连接）。",
                        GTLModular.MOD_ID, id);
                continue;
            }
            built.add(b);
        }
        machineHullBlocks = built;
        return built;
    }

    /** 两态真空仓的方块集合。 */
    static Set<Block> vacuumHatchBlocks() {
        Set<Block> cached = vacuumHatchBlocks;
        if (cached != null) {
            return cached;
        }
        Set<Block> built = new LinkedHashSet<>();
        for (String id : VACUUM_HATCH_IDS) {
            Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(GTLModular.MOD_ID, id));
            if (b != null) {
                built.add(b);
            }
        }
        vacuumHatchBlocks = built;
        return built;
    }

    /**
     * ★ 2026-09-17 本轮复核<b>删除</b>：旧的 {@code public static boolean isMachineHull(Block)}。
     * 全仓无任何调用者（复核员逐文件 grep 过）= 死代码；它的语义已被
     * {@link #rendersMeshCasingTile} 与 {@code machineHullBlocks().contains(...)} 覆盖
     * （前者按 NH 的 {@code connect=tile} 口径判"画的是不是同一张贴图"，后者只回答"在不在白名单"）。
     * 留着它只会诱使以后有人用"方块身份"去代替"同贴图"判据（那正是那条缝的根因）。
     */

    /** 该方块是不是两态真空仓。 */
    public static boolean isVacuumHatch(Block block) {
        return vacuumHatchBlocks().contains(block);
    }

    /**
     * ★ <b>单一来源</b>：两个视图（注册名 ↔ 方块）必须键集完全一致 —— 现算一遍给自检 / 启动日志用。
     *
     * <p>为什么需要它（2026-09-17 本轮复核修掉的"双来源"风险）：烘焙期按**注册名字符串集**认目标
     * （{@code machineHullIdSet()}），渲染期按**方块集**判邻居（{@code machineHullBlocks()}）。
     * 旧实现两个视图各自从 {@link #MACHINE_HULL_IDS} 字面量现建 ⇒ 一旦某个注册名漂移
     * （方块改名 / 没注册上），字符串视图**照样命中**、方块视图**取不到就少一台** ⇒
     * 那台「自己换了 sprite、邻居不认它」= <b>单侧连接</b>（外观上就是"连了一半"的缝）。
     * 现在 {@link #machineHullIdSet()} 由方块视图<b>派生</b>（键集恒等由构造保证），本方法把这条不变量
     * 再**现算一遍**，由 {@code CtmClientSetup} 在每次烘焙开始时调用（不一致就 ERROR）。
     *
     * @return 不一致的描述；{@code null} = 三个视图（方块 / 注册名 / 两态子集）完全自洽（正常）
     */
    public static String hullViewMismatch() {
        Set<Block> blocks = machineHullBlocks();
        Set<String> ids = machineHullIdSet();
        if (blocks.size() != ids.size()) {
            return "方块视图 " + blocks.size() + " 台 ≠ 注册名视图 " + ids.size() + " 条";
        }
        for (Block block : blocks) {
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
            if (key == null || !ids.contains(key.getPath())) {
                return "方块 " + block + " 的注册名 " + key + " 不在注册名视图里";
            }
        }
        for (String id : ids) {
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(GTLModular.MOD_ID, id));
            if (block == null || !blocks.contains(block)) {
                return "注册名 " + GTLModular.MOD_ID + ":" + id + " 解不出方块视图里的同一台";
            }
        }
        for (String id : VACUUM_HATCH_IDS) {
            if (!ids.contains(id)) {
                return "两态名单里的 " + id + " 不在外壳白名单里（两态会套到名单外的方块上）";
            }
        }
        return null;
    }

    /**
     * 交叉核对（诊断用，只在第一批解析时打一次）：我们推导出的 tier 机壳方块在 GTCEu 里真的注册了吗？
     *
     * <p>不做任何"失败就改写路径"的动作：路径由 {@code GTValues.VN} 与 GTCEu 的注册规则推出来
     * （见 {@link #tierName}），这里只是把不一致<b>喊出来</b>，避免静默退化。
     */
    static void warnIfTierCasingMissing(int tier) {
        if (tierCasingRegistered != null) {
            return;
        }
        ResourceLocation id = tierCasingBlockId(tier);
        boolean ok = id != null && ForgeRegistries.BLOCKS.containsKey(id);
        tierCasingRegistered = ok;
        if (!ok) {
            GTLModular.LOGGER.warn("[gtlmodular] CTM: 按 GTValues.VN[{}] 推出的 tier 机壳 {} 不在注册表里"
                    + "—— 真空仓未并入成形结构时的外壳会退回接口机壳（不会缺失贴图）。",
                    tier, id);
        }
    }

    /**
     * ★ <b>NH {@code connect=tile} 的唯一判据</b>：{@code (level, pos)} 这一格<b>渲染出来的外壳贴图</b>
     * 是不是「纳米芯片接口机壳」那一张。
     *
     * <p>真值表（与 NH 一致）：
     * <pre>
     *   机壳本体                              → true（它画的就是这张）
     *   主机 / 11 个模块控制器                 → true（外壳恒为这张，NH 由结构定义决定）
     *   真空仓 && 已并入成形结构                → true（NH 成形时 updateTexture(CASING_INDEX_WHITE)）
     *   真空仓 && 未并入任何成形结构             → false（外壳是 GTCEu 的 tier 机壳 = 另一张贴图）
     *   ★ 别的 mod / GTCEu 自己的仓，被收进 NAC 仓位 → true（本轮新增：外壳会被换成 47 格变体）
     *   白名单之外的任何方块（其它 GTCEu 机器…）  → false
     * </pre>
     *
     * <p>★ 入参 {@code level} 是宽口径的 {@code BlockGetter}（区块网格化时是 Embeddium 的
     * {@code WorldSlice} / vanilla 的 {@code RenderChunkRegion}），照样能查仓位：见
     * {@link NacSlotIndex#isNacSlot} 的注释（BE 查找可用；维度取自控制器机器自己的 level）。
     */
    public static boolean rendersMeshCasingTile(BlockGetter level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block == meshCasingBlock()) {
            return true;
        }
        if (!machineHullBlocks().contains(block)) {
            // ★ 2026-09-18 新增：白名单之外**任何机器方块**，只要它被某台成形 NAC 收进了仓位
            //   （别的 mod 的仓 / GTCEu 自己的仓），它的外壳面就会被 MachineCtmBakedModel 换成
            //   接口机壳的 47 格变体 ⇒ 按 NH 的 connect=tile 口径它就是连通的。
            //   ★ 拿不到仓位索引 ⇒ NacSlotIndex.isNacSlot 恒 false ⇒ 保持"不连"（外观 = 现状，绝不猜）。
            return block instanceof IMachineBlock && NacSlotIndex.isNacSlot(level, pos);
        }
        if (vacuumHatchBlocks().contains(block)) {
            return isCasingFromStructure(level, pos);
        }
        return true;
    }

    /**
     * 真空仓这一格是否处于"已并入成形结构"态（客户端读 {@code @DescSynced} 字段）。
     *
     * <p>★ 拿不到机器实例时按 <b>true（并入态）</b>兜底：宁可保留玩家要的"蓝黑连片"（NH 结构里的样子），
     * 也不要在认不出的渲染路径上把它退回成 tier 机壳 —— 掉纹理/机器隐形是这个项目里最严重的回归。
     */
    public static boolean isCasingFromStructure(BlockGetter level, BlockPos pos) {
        try {
            MetaMachine machine = MetaMachine.getMachine(level, pos);
            if (machine instanceof NanochipVacuumHatchMachine hatch) {
                return hatch.isCasingFromStructure();
            }
        } catch (Throwable ignored) {
            // 渲染路径绝不允许因为 CTM 抛异常（区块烘焙线程里抛 = 区块渲染崩）
        }
        return true;
    }

    /** 真空仓两态里"未并入"那一态用的 tier（= NAC 真空仓的电压档，单一来源）。 */
    public static int hatchTier() {
        return NanochipMachines.VACUUM_HATCH_TIER;
    }
}
