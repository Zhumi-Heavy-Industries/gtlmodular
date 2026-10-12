package com.gtlmodular.common.data;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 纳米芯片组装复合体（NAC）的专属结构方块。
 *
 * <p>贴图全部取自 GTNH 2.9.0-beta-2 的 {@code gregtech-5.09.54.20.jar}
 * （{@code assets/gregtech/textures/blocks/iconsets/}），已解包到
 * {@code assets/gtlmodular/textures/block/} 下：
 * <ul>
 *   <li>{@code NANOCHIP_MESH_INTERFACE_CASING} → 网格接口机械方块（结构外壳/平台）</li>
 *   <li>{@code NANOCHIP_REINFORCEMENT_CASING} → 强化机械方块（结构主材）</li>
 *   <li>{@code NANOCHIP_COMPUTATIONAL_MATRIX_CASING} → 计算矩阵机械方块（控制室/顶盖）</li>
 *   <li>{@code NANOCHIP_FIREWALL_PROJECTION_CASING} → 防火墙投影机械方块（有独立顶面贴图）</li>
 *   <li>{@code NANOCHIP_GLASS} → 复合体玻璃</li>
 * </ul>
 *
 * <p>对应 GTNH 的 {@code gt.blockcasings12.1~4} 与 {@code gt.blockglass1.8}
 * （见 GT5U {@code en_US.lang}：Nanochip Mesh Interface / Reinforcement / Computational Matrix /
 * Firewall Projection Casing、Nanochip Complex Glass）。
 *
 * <h2>★ 2026-09-17 新增：把 NH 有、本包没有的「缺件方块」造出来（7 个，纯加法）</h2>
 *
 * <p>依据 {@code _census/role_reference.md} §三「缺件清单」（P0–P4）与本机 NH 2.9.0-beta-2 原始资源
 * （{@code _gtnh/jarfull/**}）。贴图由 {@code gen_missing_blocks_assets.js} <b>逐字节</b>从 NH 拷贝
 * （脚本内硬编码 NH 原文件的 sha256 并当场比对 ⇒ "贴图来自 NH"是机器可验证的事实），
 * 本类只负责注册。
 *
 * <p><b>其中 5 件是"追加候选"、不是替代</b>：结构判定侧（{@link com.gtlmodular.common.data.nanochip.NanochipComplexPattern}）
 * 用 {@code .or(blocks(新方块))} 把它们<b>追加</b>到对应角色位的候选集里，
 * <b>原有候选一个都没删</b> ⇒ 玩家 2026-09-17 凌晨刚搭好并成形的 11 台模块与主机<b>依旧成形</b>。
 * <b>例外（2 味线圈，2026-09-21 队长拍板「严格定死」）</b>：见下面表格后的说明 ——
 * 它们已提升为<b>基础判定</b>，不再回落到通用线圈。
 *
 * <table border="1">
 *   <caption>缺件 → 新方块</caption>
 *   <tr><th>NH 原方块</th><th>本包新方块 id</th><th>接进哪个角色位</th></tr>
 *   <tr><td>{@code gt.blockcasings.cyclotron_coils:7}</td><td>{@code uhv_solenoid_superconductor_coil}</td><td>COIL_UHV</td></tr>
 *   <tr><td>{@code gt.blockcasings.cyclotron_coils:8}</td><td>{@code uev_solenoid_superconductor_coil}</td><td>COIL_UEV</td></tr>
 *   <tr><td>{@code gt.blockcasings9:12}</td><td>{@code naquadria_reinforced_water_plant_casing}</td><td>REINFORCED_CASING</td></tr>
 *   <tr><td>gtnhlanth {@code SHIELDED_ACCELERATOR_CASING}</td><td>{@code shielded_accelerator_casing}</td><td>MATRIX_CASING</td></tr>
 *   <tr><td>{@code gt.blockglass1:3}</td><td>{@code non_photonic_matter_exclusion_glass}</td><td>GLASS</td></tr>
 *   <tr><td>{@code BlockSheetMetal(NaquadahAlloy)}</td><td>{@code naquadah_alloy_sheetmetal}</td><td>SHEET:NaquadahAlloy</td></tr>
 *   <tr><td>{@code BlockSheetMetal(SuperconductorUEVBase)}</td><td>{@code superconductor_uev_base_sheetmetal}</td><td>SHEET:SuperconductorUEVBase</td></tr>
 * </table>
 *
 * <p>★ 2026-09-19（队长令「两个线圈方块给他定死」）⇒ <b>2026-09-21 队长拍板「严格定死（B 案）」</b>：
 * 上表前两行的<b>角色位已按位置拆开，且各自只认自己那一味真线圈</b> ——
 * NH 的 {@code sSolenoidCoilCasings:7}（UHV）只挂超导线束分离器的 <b>A 位 6 格</b>
 * （{@code TowerRole.COIL_UHV}），{@code :8}（UEV）只挂 <b>B 位 16 格</b>（{@code TowerRole.COIL_UEV}）。
 * <p>与其余 5 件不同，这 2 味线圈<b>不是"追加候选"</b>：它们写在 {@code predicateFor} 的
 * {@code COIL_UHV}/{@code COIL_UEV} 里作为<b>唯一</b>接受物，<b>连 {@code gtceu:naquadah_coil_block} 也不收</b>
 * ⇒ 老存档里用通用线圈搭的那 22 格会不成形（代价已由队长确认）。
 * 分位依据见 {@code NanochipComplexPattern} 里 {@code COIL_UHV} 的 javadoc
 * （{@code _census/all_modules.json} 该模块的 {@code cells} + 维基结构表的 UHV 6 / UEV 16）。
 */
public final class NanochipBlocks {

    private NanochipBlocks() {
    }

    /** 网格接口机械方块（Nanochip Mesh Interface Casing）—— 可换装仓室的结构外壳。 */
    public static final BlockEntry<Block> MESH_INTERFACE_CASING =
            cube("nanochip_mesh_interface_casing", 6.0F, 8.0F);

    /** 强化机械方块（Nanochip Reinforcement Casing）—— 结构主材。 */
    public static final BlockEntry<Block> REINFORCEMENT_CASING =
            cube("nanochip_reinforcement_casing", 8.0F, 10.0F);

    /** 计算矩阵机械方块（Nanochip Computational Matrix Casing）—— 控制室/顶盖。 */
    public static final BlockEntry<Block> COMPUTATIONAL_MATRIX_CASING =
            cube("nanochip_computational_matrix_casing", 8.0F, 10.0F);

    /** 防火墙投影机械方块（Nanochip Firewall Projection Casing）—— 侧面 + 独立顶面。 */
    public static final BlockEntry<Block> FIREWALL_PROJECTION_CASING =
            REGISTRATE_BLOCK("nanochip_firewall_projection_casing")
                    .properties(p -> p.strength(8.0F, 10.0F))
                    .item()
                    .build()
                    .register();

    /**
     * 复合体玻璃（Nanochip Complex Glass）—— 透明、不遮挡、不透光判定。
     *
     * <p><b>用 {@link com.gtlmodular.common.block.NanochipComplexGlassBlock} 而不是裸 {@code Block}</b>：
     * 只有覆写 {@code skipRendering} 才能让相邻玻璃的<b>共享面不重复绘制</b>，
     * 玻璃的花纹才会连成一片（见该类的注释与施工记录 §24.4）。贴图/模型/结构判定都不变。
     */
    public static final BlockEntry<com.gtlmodular.common.block.NanochipComplexGlassBlock> COMPLEX_GLASS =
            NanochipModContext.machineRegistrate()
                    .block("nanochip_complex_glass", com.gtlmodular.common.block.NanochipComplexGlassBlock::new)
                    .initialProperties(() -> Blocks.GLASS)
                    .properties(p -> p.strength(3.0F)
                            .sound(SoundType.GLASS)
                            .noOcclusion()
                            .isValidSpawn((s, l, p2, e) -> false)
                            .isRedstoneConductor((s, l, p2) -> false)
                            .isSuffocating((s, l, p2) -> false)
                            .isViewBlocking((s, l, p2) -> false))
                    .item()
                    .build()
                    .register();

    // ---------- ★ 2026-09-17 缺件方块（P0–P4）：全部是"追加候选"，不动任何既有映射 ----------

    /**
     * UHV 螺线管超导线圈（NH {@code gt.blockcasings.cyclotron_coils:7}）—— 超导线束分裂器的 {@code A} 位（6 格）。
     *
     * <p>贴图两张：侧面 {@code ..._side.png}（← {@code iconsets/UHV_SIDE_CYCLOTRON_SOLENOID.png}）、
     * 端面 {@code ..._top.png}（← {@code iconsets/UHV_TOP_CYCLOTRON_SOLENOID.png}）。
     *
     * <p><b>模型是 {@code cube_bottom_top}，且 {@code bottom} 与 {@code top} 指向同一张
     * {@code _top}</b>：这不是"没给底面做贴图"的将就，而是 NH 的行为 ——
     * {@code gregtech.common.blocks.BlockCyclotronCoils.func_149691_a(side, meta)}（= {@code getIcon}）
     * 对 meta 7 的字节码是
     * {@code iload_1; ifeq → UHV_TOP_CYCLOTRON_SOLENOID} / {@code iload_1; iconst_1; if_icmpne → UHV_SIDE_…}
     * ⇒ <b>side 0(DOWN) 与 side 1(UP) 都取 TOP 图标</b>，只有 2..5 才取 SIDE（meta 8 的分支同构）。
     * ⇒ 六面映射：{@code up/down = _top}、{@code north/south/east/west = _side}。
     * <b>2026-09-17 更正</b>：本 javadoc 早先写的是"上下端面 TOP"、而模型 json 写的是
     * {@code bottom = _side} —— 文字与模型自相矛盾，且模型那侧是错的；两边现已统一为 NH 口径。
     *
     * <p>⚠️ NH 对这个侧面还挂了 compact CTM（{@code mcpatcher/ctm/coilsother/solenoiduhv/solenoid.properties}，
     * {@code matchTiles=…UHV_SIDE_CYCLOTRON_SOLENOID}、{@code faces=sides}、{@code tiles=0 1 1 0 1}）。
     * <b>本次没有接 CTM</b>（CTM 目标清单属于 {@code client/ctm/**}，不是本文件的权限范围）
     * ⇒ 现在是一格一张静态贴图；要"连片"需要 CTM 负责人把这条登记进 {@code CtmClientSetup.TARGETS}
     * 并同步 {@code _census/check_ctm_targets.js} 的期望值（见施工记录）。
     */
    public static final BlockEntry<Block> UHV_SOLENOID_SUPERCONDUCTOR_COIL =
            REGISTRATE_BLOCK("uhv_solenoid_superconductor_coil", 8.0F, 10.0F);

    /** UEV 螺线管超导线圈（NH {@code gt.blockcasings.cyclotron_coils:8}）—— 同一模块的 {@code B} 位（16 格）。 */
    public static final BlockEntry<Block> UEV_SOLENOID_SUPERCONDUCTOR_COIL =
            REGISTRATE_BLOCK("uev_solenoid_superconductor_coil", 8.0F, 10.0F);

    /**
     * 硅岩合金强化水厂外壳（NH {@code gt.blockcasings9:12}「Naquadria-Reinforced Water Plant Casing」）
     * —— 纳米精度切割室塔身四角立柱 16 格。
     *
     * <p>贴图 = {@code iconsets/NAQUADRIA_REINFORCED_WATER_PLANT_CASING.png}（逐字节）。
     * 这条 iconset 正是 NH {@code mcpatcher/ctm/waterline/t8/t8.properties} 的 {@code matchTiles}
     * （{@code method=compact_expanded}、{@code tiles=0-4}、{@code connect=tile}）
     * ⇒ NH 在游戏里看到的是那 5 张 CTM 变体；本包同样<b>暂未接 CTM</b>（同上，属 CTM 负责人）。
     */
    public static final BlockEntry<Block> NAQUADRIA_REINFORCED_WATER_PLANT_CASING =
            REGISTRATE_BLOCK("naquadria_reinforced_water_plant_casing", 8.0F, 10.0F);

    /**
     * 屏蔽加速器外壳（GTNH-Lanthanides 的 {@code SHIELDED_ACCELERATOR_CASING}）—— 超高能蚀刻阵列 9 格。
     *
     * <p>★ 取证结论：{@code gtnhlanth/textures/blocks/casing.shielded_accelerator.png} 与
     * {@code gregtech/textures/blocks/iconsets/MACHINE_CASING_SHIELDED_ACCELERATOR.png}
     * <b>sha256 完全相同</b>（{@code fa53660d…}）⇒ 取哪个源都是同一字节；这里取 gregtech 侧那份。
     */
    public static final BlockEntry<Block> SHIELDED_ACCELERATOR_CASING =
            REGISTRATE_BLOCK("shielded_accelerator_casing", 8.0F, 10.0F);

    /**
     * 非光子物质排斥玻璃（NH {@code gt.blockglass1:3}）—— 超高能蚀刻阵列 4 格。
     *
     * <p>贴图 = {@code iconsets/GLASS_QUARK_CONTAINMENT.png}（16×320 <b>动画</b>，20 帧）+ 配套 {@code .png.mcmeta}
     * （{@code {"animation":{"frametime":2}}}），与 NH 原文件逐字节相同（{@code 50c994da…}）。
     *
     * <p>★ <b>2026-09-17 换源（原为 {@code mcpatcher/ctm/blockglass1/quarkglass/0.png}）</b>：NH 对该方块
     * 注册的<b>基底贴图</b>是 {@code GLASS_QUARK_CONTAINMENT} —— 证据是
     * {@code gregtech.common.blocks.BlockGlass1.func_149691_a(side, meta)}（= {@code getIcon}）
     * 的 {@code tableswitch} 在 meta 3 分支取 {@code Textures$BlockIcons.GLASS_QUARK_CONTAINMENT}
     * （偏移 93），而 {@code GTBlockIconContainer} 把图标名拼成
     * {@code gregtech:textures/blocks/iconsets/<名>.png}。CTM 目录里那 5 张
     * （{@code quarkglass/{0..4}.png}，{@code method=compact} / {@code tiles=0-4} / {@code connect=block}）
     * 是**连接状态的变体**，不是基底。
     * 两者关系已取证：{@code 0.png} 与 {@code GLASS_QUARK_CONTAINMENT.png} 是<b>同一张图的两种 PNG 编码</b>
     * （都是 16×320 / 20 帧，解码后 20480 字节<b>逐字节全等</b>，只有压缩体积不同 1072 B vs 3193 B）
     * ⇒ 换源后观感完全不变，但 provenance 变成可机器核对的"基底 icon"。
     *
     * <p><b>旧注释的错处（一并更正）</b>：原 javadoc 写「取第 0 张的理由与本模组复合体玻璃一致
     * （{@code mcpatcher/ctm/nanochipglass} 的 properties 注释：第 0 张 = CTM 的'四边都有线的孤立变体'）」。
     * 事实是：<b>NH 原文件里根本没有那条注释</b> —— NH 的 {@code mcpatcher/ctm/blockglass1/} 下
     * 每份 properties（含 {@code quarkglass} 与 {@code nanochipglass}）都只有 6 行纯配置，
     * 没有任何"第 0 张 = 孤立变体"的说明。那是<b>我们自己的解读</b>（只存在于本模组自己写的
     * {@code nanochipglass} properties 注释里），不是 NH 事实，因此不能当引文用。
     * 本 javadoc 现在只引可执行的证据（字节码 + sha256 + 逐像素比对）。
     *
     * <p>复用 {@link com.gtlmodular.common.block.NanochipComplexGlassBlock}：只多一件事 ——
     * 相邻同类玻璃共享面不重复绘制。不改贴图 / 不改 UV / 不影响结构判定。
     */
    public static final BlockEntry<com.gtlmodular.common.block.NanochipComplexGlassBlock>
            NON_PHOTONIC_MATTER_EXCLUSION_GLASS =
            REGISTRATE_BLOCK("non_photonic_matter_exclusion_glass",
                    com.gtlmodular.common.block.NanochipComplexGlassBlock::new)
                    .initialProperties(() -> Blocks.GLASS)
                    .properties(p -> p.strength(3.0F)
                            .sound(SoundType.GLASS)
                            .noOcclusion()
                            .isValidSpawn((s, l, p2, e) -> false)
                            .isRedstoneConductor((s, l, p2) -> false)
                            .isSuffocating((s, l, p2) -> false)
                            .isViewBlocking((s, l, p2) -> false))
                    .item()
                    .build()
                    .register();

    /**
     * 硅岩合金板金属（NH {@code BlockSheetMetal(NaquadahAlloy)}）—— 组装矩阵塔身 {@code G} 位 3 格。
     *
     * <p>母版 = {@code materialicons/<b>METALLIC</b>/sheetmetal.png}（290 B，{@code 06abbb80…}）——
     * NaquadahAlloy 的 {@code TextureSet} 在 {@code MaterialsInit.loadNaquadahAlloy()} 偏移 25 是
     * {@code SET_METALLIC}；而 NH 取图走的是
     * {@code BlockSheetMetal.getIcon} 偏移 20..41
     * {@code material.getTextureSet().mTextures[OrePrefixes.sheetmetal.getTextureIndex()]}。
     *
     * <p>⚠️ <b>已知偏差（需要玩家裁决，见施工记录）</b>：这块母版是<b>灰底可染色</b>的
     * （实测首帧 RGB 恒等、亮度 122..217、均值 175），游戏里由材料的 {@code getRGBA()} 染色。
     * 本模组的普通方块<b>没有客户端染色入口</b>（那要 {@code client/**} 或彩色处理器），
     * 所以这一块目前呈现<b>未染色的灰底母版</b>；贴图本身与 NH 逐字节相同（硬要求满足），
     * 但"按材料上色"还没做。
     */
    public static final BlockEntry<Block> NAQUADAH_ALLOY_SHEETMETAL =
            REGISTRATE_BLOCK("naquadah_alloy_sheetmetal", 6.0F, 8.0F);

    /**
     * 板金属（NH {@code BlockSheetMetal(SuperconductorUEVBase)}）—— 线材描绘器塔身 {@code C} 位 1 格。
     *
     * <p>★ <b>母版必须按材料的 TextureSet 选，不能沿用上一块的</b>：SuperconductorUEVBase 的
     * {@code TextureSet} 在 {@code MaterialsInit.loadSuperconductorUEVBase()} 偏移 25 是
     * {@code SET_SHINY} ⇒ 母版是 {@code materialicons/<b>SHINY</b>/sheetmetal.png}
     * （363 B，{@code bf772cc1…}），与 NaquadahAlloy 那块<b>不是同一张图</b>。
     * <b>2026-09-17 更正</b>：此前 {@code gen_missing_blocks_assets.js} 把两个材料都写死成 METALLIC
     * ⇒ 两块板金属的贴图 sha256 <b>完全相同</b>（{@code 06abbb80…}），已按 TextureSet 修好。
     *
     * <p>染色偏差同上（两块都是可染色母版；本模组无客户端染色入口 ⇒ 现为灰底）。
     */
    public static final BlockEntry<Block> SUPERCONDUCTOR_UEV_BASE_SHEETMETAL =
            REGISTRATE_BLOCK("superconductor_uev_base_sheetmetal", 6.0F, 8.0F);

    // ---------- 工具 ----------

    /** Registrate 的入口（复用本模组的 GTRegistrate；GTRegistrate extends Registrate）。 */
    private static com.tterrag.registrate.builders.BlockBuilder<Block, com.tterrag.registrate.Registrate> REGISTRATE_BLOCK(String name) {
        return NanochipModContext.machineRegistrate().block(name, Block::new);
    }

    /**
     * 同上，但强度可调（六面同贴图的那种结构方块）。
     *
     * <p><b>为什么保留 {@code REGISTRATE_BLOCK("id", …)} 这个形态而不是内部转调 {@code cube(...)}</b>：
     * {@code verify_nac.js} 与 {@code _census/check_ctm_targets.js} 都是从源码里用正则
     * {@code REGISTRATE_BLOCK\(\s*"([a-z0-9_]+)"} 抽"本模组注册了哪些方块 id"的
     * （{@code cube("id", …)} 那条路径只认 {@code nanochip_} 前缀）。
     * 换句话说：<b>这里是给审计脚本留的可解析锚点，不是风格偏好</b>。
     */
    private static BlockEntry<Block> REGISTRATE_BLOCK(String name, float destroyTime, float explosionResistance) {
        return REGISTRATE_BLOCK(name)
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties((BlockBehaviour.Properties p) -> p.strength(destroyTime, explosionResistance))
                .item()
                .build()
                .register();
    }

    /**
     * 同上，但方块类可指定（玻璃那种要覆写 {@code skipRendering} 的用）。
     *
     * <p>签名必须与 {@code AbstractRegistrate.block(String, NonNullFunction<Properties, T>)} 一致；
     * 调用点同样保留 {@code REGISTRATE_BLOCK("id", …)} 形态（理由见上一条）。
     */
    private static <T extends Block> com.tterrag.registrate.builders.BlockBuilder<T, com.tterrag.registrate.Registrate>
    REGISTRATE_BLOCK(String name,
                     com.tterrag.registrate.util.nullness.NonNullFunction<BlockBehaviour.Properties, T> factory) {
        return NanochipModContext.machineRegistrate().block(name, factory);
    }

    /**
     * 六面同一贴图的机械方块。
     *
     * <p><b>为什么这里不做 datagen</b>：本项目没跑 {@code runData}，Registrate 的
     * {@code .blockstate(...)} / {@code .model(...)} 只在数据生成阶段写文件，运行时不会生效；
     * 而 GTCEu 的机器方块是靠 {@code IMachineBlock} 的动态模型加载器渲染的，普通方块没有这个待遇。
     * 所以 NAC 的 5 种结构方块的 <b>blockstate / block model / item model / 掉落表</b>
     * 全部手写在 {@code src/main/resources} 里随包发布（见 {@code assets/gtlmodular/blockstates/*.json}、
     * {@code models/block/casings/*.json}、{@code models/item/*.json}、
     * {@code data/gtlmodular/loot_tables/blocks/*.json}）。
     * 另外这里刻意不加 {@code requiresCorrectToolForDrops()}：没有工具标签的掉落表配上它，
     * 挖下来会什么都不掉。
     */
    private static BlockEntry<Block> cube(String name, float destroyTime, float explosionResistance) {
        return REGISTRATE_BLOCK(name)
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties((BlockBehaviour.Properties p) -> p
                        .strength(destroyTime, explosionResistance))
                .item()
                .build()
                .register();
    }

    /** 本模组命名空间的资源位置。 */
    private static ResourceLocation modLoc(String path) {
        return NanochipModContext.assetId(path);
    }

    /** 强制类加载（在机器注册链中被调用一次即可）。 */
    public static void init() {
        NanochipModContext.logger().info("[gtlmodular] NAC 结构方块已注册: 网格接口/强化/计算矩阵/防火墙投影/玻璃"
                + " + 缺件方块 7 个（UHV/UEV 螺线管线圈、硅岩强化水厂外壳、屏蔽加速器外壳、"
                + "非光子排斥玻璃、2 个板金属）—— 全部是追加候选，既有替代码不变");
    }
}
