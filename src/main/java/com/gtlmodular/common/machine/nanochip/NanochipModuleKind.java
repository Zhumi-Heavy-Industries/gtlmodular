package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipRecipeTypes;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import java.util.Locale;

/**
 * 纳米芯片组装复合体（NAC）的 14 个模块种类。
 *
 * <p><b>其中 11 个来自 NH</b>，名称与职能取自 GTNH 2.9.0 的 GT5U 语言文件（{@code gregtech-5.09.54.20.jar}
 * 的 {@code assets/gregtech/lang/en_US.lang}）：
 * <pre>
 * gt.recipe.nanochip.assemblymatrix        = Nanochip Assembly Matrix
 * gt.recipe.nanochip.smdprocessor          = Part Preparation Apparatus
 * gt.recipe.nanochip.boardprocessor        = Full-Board Immersion Device
 * gt.recipe.nanochip.etchingarray          = Ultra-high Energy Etching Array
 * gt.recipe.nanochip.cuttingchamber        = Nanoprecision Cutting Chamber
 * gt.recipe.nanochip.wiretracer            = Nanoprecision Wire Tracer
 * gt.recipe.nanochip.superconductorsplitter= Superconductive Strand Splitter
 * gt.recipe.nanochip.opticalorganizer      = Optically Optimized Organizer
 * gt.recipe.nanochip.biologicalcoordinator = Accelerated Biological Coordinator
 * gt.recipe.nanochip.encasementwrapper     = Nanometer Encasement Wrapper
 * （Nanopart Splitter —— 语言文件里的 {@code GT5U.tooltip.nac.module.splitter.name}）
 * </pre>
 *
 * <p><b>★ 另外 3 个是本移植的扩展</b>（{@link #EXOTIC_ORGANIZER} / {@link #COSMIC_ORGANIZER} /
 * {@link #SUPRACAUSAL_ORGANIZER}，2026-09-18 队长指示新增）：NH 原版 NAC 只有 11 个模块，
 * 这三台是**光学优化组织器的同构镜像** —— 塔身逐格照抄、只把框架位换成各自那味材料
 * （山铜 / 魔金 / 塔兰），各自服务本族（奇异 / 寰宇 / 超因果）的处理器核心与 RAM 晶片。
 * 它们**没有** NH 图标集（{@code OVERLAY_FRONT_*}）可对齐 ⇒ 贴图从光学那台复制改名，
 * 这一点在 {@code _census/check_module_overlays.js} 与施工记录里如实登记为已知偏离。
 *
 * <p><b>本移植的取舍</b>：原版 NAC 用「真空传送带 + CC/PC 电路元件」这套物品路由把 11 个模块串起来。
 * 移植版保留了颜色路由与 CC/PC 分级（虚拟通道，见 {@link NanochipVacuumNetwork}），
 * 并让每个模块<b>保留自己的独立加工能力</b>（各自沿用一张真实配方表，可单独使用），
 * 同时在运转中为 NAC 提供原版那份加成、并<b>消耗原版那份耗材</b>
 * （见 {@link NanochipModuleUpkeep} 与下面的 {@code upkeep*} 字段）。
 *
 * <h2>★ 正脸图标集：每个 kind ↔ NH 的 {@code OVERLAY_FRONT_*}（§75）</h2>
 * <p>NH 的 11 个模块控制器<b>各自有一整套正脸图标集</b>；本枚举第 2 个构造参数
 * （{@link #overlayPrefix()}）就是那张表的<b>唯一来源</b>，实际贴图放在
 * {@code assets/gtlmodular/textures/block/multiblock/<machineId>/overlay_front*.png}。
 *
 * <p><b>两个名字的关系（2026-09-17 复核后写清，别再混）</b>：
 * <ul>
 *   <li>{@link #overlayPrefix()} = <b>NH 侧图标集名</b>（{@code OVERLAY_FRONT_<本值>}）——
 *       「kind ↔ NH 图标集」的唯一来源，运行时由本类构造器的<b>不变量</b>消费（见下）。</li>
 *   <li>{@link #machineId()} = <b>本模组侧方块 id</b>，也是贴图目录名；
 *       {@code NanochipMachines.moduleOverlayTex(kind)} 用<b>它</b>拼出运行时 {@code ResourceLocation}。</li>
 * </ul>
 * 二者可以互相推导（规则见 {@code overlayDirOf}），且构造器会强制它们一致 ——
 * 所以「唯一来源」这句话现在是可执行的，不再是注释里的一厢情愿。
 * <p>⚠️ 早先这里只写了"overlayPrefix() 就是唯一来源"，而实现其实是用 machineId() 拼路径、
 * overlayPrefix() 一个调用点都没有 ⇒ 那是典型的"注释说 A、实现是 B"。现已由构造器不变量接住。
 * <pre>
 * kind                         machineId()                         NH 图标集前缀（OVERLAY_FRONT_*）
 * ASSEMBLY_MATRIX              nanochip_assembly_matrix            ASSEMBLY_MATRIX
 * SMD_PROCESSOR                nanochip_smd_processor              SMD_PROCESSOR
 * BOARD_PROCESSOR              nanochip_board_processor            BOARD_PROCESSOR
 * ETCHING_ARRAY                nanochip_etching_array              ETCHING_ARRAY
 * CUTTING_CHAMBER              nanochip_cutting_chamber            CUTTING_CHAMBER
 * WIRE_TRACER                  nanochip_wire_tracer                WIRE_TRACER
 * SUPERCONDUCTOR_SPLITTER      nanochip_superconductor_splitter    SUPERCONDUCTOR_SPLITTER
 * OPTICAL_ORGANIZER            nanochip_optical_organizer          OPTICAL_ORGANIZER
 * EXOTIC_ORGANIZER             nanochip_exotic_organizer           EXOTIC_ORGANIZER        ← 新增（无 NH 图标集）
 * COSMIC_ORGANIZER             nanochip_cosmic_organizer           COSMIC_ORGANIZER        ← 新增（无 NH 图标集）
 * SUPRACAUSAL_ORGANIZER        nanochip_supracausal_organizer      SUPRACAUSAL_ORGANIZER   ← 新增（无 NH 图标集）
 * BIOLOGICAL_COORDINATOR       nanochip_biological_coordinator     BIOLOGICAL_COORDINATION   ← 名字不一样
 * ENCASEMENT_WRAPPER           nanochip_encasement_wrapper         ENCASEMENT_WRAPPER
 * SPLITTER                     nanochip_splitter                   SPLITTER
 * </pre>
 * <p><b>别拿错</b>：{@code OVERLAY_FRONT_BEAM_SPLITTER} 是另一台机器
 * （TecTech 光束裂解，{@code gt.blockmachines.multimachine.beamcrafting.beamsplitter}），
 * <b>不是</b> NAC 模块；NAC 的分流器用的是 {@code OVERLAY_FRONT_SPLITTER}。
 */
public enum NanochipModuleKind {

    /** 组装矩阵：NAC 的<b>主加工环节</b>，也是「全流程档」的唯一门票。 */
    ASSEMBLY_MATRIX("nanochip_assembly_matrix", "ASSEMBLY_MATRIX", "纳米芯片组装矩阵", "Nanochip Assembly Matrix",
            NanochipRecipeTypes.ASSEMBLY_MATRIX, 1.0D, 1.0D, 1.0D, true, null,
            null, 0,
            "组装电路；解锁全流程档（无限并行 + 配方时长下限 5 秒）"),

    /** 元件预处理装置：SMD 元件。 */
    SMD_PROCESSOR("nanochip_smd_processor", "SMD_PROCESSOR", "元件预处理装置", "Part Preparation Apparatus",
            NanochipRecipeTypes.SMD_PROCESSOR, 0.95D, 1.0D, 1.0D, false, null,
            null, 0,
            "精炼元件；每台为复合体减免 5% 能耗"),

    /** 全板浸没设备：电路板浸洗。原版：内部 100 万 L 浸泡液槽、至少半满才能跑、杂质影响效率。 */
    BOARD_PROCESSOR("nanochip_board_processor", "BOARD_PROCESSOR", "全板浸没设备", "Full-Board Immersion Device",
            NanochipRecipeTypes.BOARD_PROCESSOR, 0.90D, 1.0D, 1.0D, false, null,
            "gtceu:grade_1_purified_water", 100,
            "精炼电路板；每台减免 10% 能耗；持续消耗净化水，杂质影响耗电"),

    /** 超高能蚀刻阵列：芯片蚀刻（激光源仓机制）。 */
    ETCHING_ARRAY("nanochip_etching_array", "ETCHING_ARRAY", "超高能蚀刻阵列", "Ultra-high Energy Etching Array",
            NanochipRecipeTypes.ETCHING_ARRAY, 1.0D, 0.95D, 1.0D, false, null,
            null, 0,
            "蚀刻晶体芯片；每台缩短 5% 时长；晶体电路满校准后激光源仓额外生效"),

    /** 纳米精度切割仓：晶圆切割。四条链都带，通常并两台。 */
    CUTTING_CHAMBER("nanochip_cutting_chamber", "CUTTING_CHAMBER", "纳米精度切割仓", "Nanoprecision Cutting Chamber",
            NanochipRecipeTypes.CUTTING_CHAMBER, 1.0D, 0.95D, 1.0D, false, null,
            null, 0,
            "切割芯片/螺栓/板材/框架；每台缩短 5% 时长"),

    /** 纳米精度导线追踪器：导线。四条链都带。 */
    WIRE_TRACER("nanochip_wire_tracer", "WIRE_TRACER", "纳米精度导线追踪器", "Nanoprecision Wire Tracer",
            NanochipRecipeTypes.WIRE_TRACER, 0.95D, 1.0D, 1.0D, false, null,
            null, 0,
            "为 NAC 减免 5% 能耗"),

    /** 超导线束分离器：超导线。原版：需要 1000 L/s 超导冷却液。 */
    SUPERCONDUCTOR_SPLITTER("nanochip_superconductor_splitter", "SUPERCONDUCTOR_SPLITTER", "超导线束分离器", "Superconductive Strand Splitter",
            NanochipRecipeTypes.SUPERCONDUCTOR_SPLITTER, 1.0D, 0.90D, 1.0D, false, null,
            "gtceu:super_coolant", 1000,
            "为 NAC 缩短 10% 时长；需持续供给 1000 mB/s 超级冷却液（NH 原文 Materials.SuperCoolant）"),

    /** 光学优化组织器：光学电路（净化水机制，等级越高加成越大）。 */
    OPTICAL_ORGANIZER("nanochip_optical_organizer", "OPTICAL_ORGANIZER", "光学优化组织器", "Optically Optimized Organizer",
            NanochipRecipeTypes.OPTICAL_ORGANIZER, 0.90D, 0.90D, 1.0D, false, null,
            "gtceu:grade_1_purified_water", 100,
            "整理光学芯片；每台减免 10% 能耗并缩短 10% 时长；净化水等级越高加成越大，光学满校准后翻倍"),

    /**
     * 奇异优化组织器：<b>奇异族</b>版的光学优化组织器（2026-09-18 队长指示新增）。
     *
     * <p>队长原话：「奇异优化组织器，山铜框架…他们结构都跟光学优化组织器一样，只是不同的框架，
     * 当然主机也要有改变，里面的配方也要有改变，这需要你自己注册，他们的配方跟光学的配方相似」。
     * ⇒ 结构 = 光学那台的塔身逐格照抄、<b>只把框架位换成山铜</b>（见 {@code NanochipComplexPattern.buildTowers()}）；
     * 加成机制 = 与光学同构的「净化水等级缩放」（见 {@link #organizerFamily()}）。
     */
    EXOTIC_ORGANIZER("nanochip_exotic_organizer", "EXOTIC_ORGANIZER", "奇异优化组织器", "Exotically Optimized Organizer",
            NanochipRecipeTypes.EXOTIC_ORGANIZER, 0.90D, 0.90D, 1.0D, false, null,
            "gtceu:grade_1_purified_water", 100,
            "整理奇异芯片；每台减免 10% 能耗并缩短 10% 时长；净化水等级越高加成越大"),

    /**
     * 寰宇优化组织器：<b>寰宇族</b>版（框架 = 魔金 {@code infuscolium}）。
     * 结构与机制同 {@link #EXOTIC_ORGANIZER}，只是换族。
     */
    COSMIC_ORGANIZER("nanochip_cosmic_organizer", "COSMIC_ORGANIZER", "寰宇优化组织器", "Cosmically Optimized Organizer",
            NanochipRecipeTypes.COSMIC_ORGANIZER, 0.90D, 0.90D, 1.0D, false, null,
            "gtceu:grade_1_purified_water", 100,
            "整理寰宇芯片；每台减免 10% 能耗并缩短 10% 时长；净化水等级越高加成越大"),

    /**
     * 超因果优化组织器：<b>超因果族</b>版（框架 = 塔兰 {@code taranium}）。
     * 结构与机制同 {@link #EXOTIC_ORGANIZER}，只是换族。
     */
    SUPRACAUSAL_ORGANIZER("nanochip_supracausal_organizer", "SUPRACAUSAL_ORGANIZER", "超因果优化组织器", "Supracausally Optimized Organizer",
            NanochipRecipeTypes.SUPRACAUSAL_ORGANIZER, 0.90D, 0.90D, 1.0D, false, null,
            "gtceu:grade_1_purified_water", 100,
            "整理超因果芯片；每台减免 10% 能耗并缩短 10% 时长；净化水等级越高加成越大"),    /** 加速生物调谐器：活体电路（培养基机制）。湿件 / 生物链专属。 */
    BIOLOGICAL_COORDINATOR("nanochip_biological_coordinator", "BIOLOGICAL_COORDINATION", "加速生物调谐器", "Accelerated Biological Coordinator",
            NanochipRecipeTypes.BIOLOGICAL_COORDINATOR, 1.0D, 0.90D, 1.0D, false,
            new NanochipCalibration.Family[]{NanochipCalibration.Family.WETWARE, NanochipCalibration.Family.BIOWARE},
            "gtceu:raw_growth_medium", 100,
            "调谐生物/湿件核心件；湿件与生物电路缩短 10% 时长；耗生长培养基，满校准后免耗"),

    /** 纳米封装卷绕器：板 + 框架盒 → 卷轴 + 外壳。 */
    ENCASEMENT_WRAPPER("nanochip_encasement_wrapper", "ENCASEMENT_WRAPPER", "纳米封装卷绕器", "Nanometer Encasement Wrapper",
            NanochipRecipeTypes.ENCASEMENT_WRAPPER, 1.0D, 1.0D, 4.0D, false, null,
            null, 0,
            "卷绕封装件（框架盒/箔材/主板外壳/热超导体）；每台提供 4 倍并行"),

    /** 纳米组件分流器：原版「整个系统的调度口」——按颜色/物品/红石规则分流。 */
    SPLITTER("nanochip_splitter", "SPLITTER", "纳米组件分流器", "Nanopart Splitter",
            // ★ 2026-09-19（队长：「那个纳米组件分流器改一下」）：改前借用 GTRecipeTypes.DUMMY_RECIPES
            //   占位 ⇒ 机器界面「机器模式」显示 GTCEu 官方译名「假人」（gtceu.dummy）。
            //   现改用**本模组自己的空表** NanochipRecipeTypes.SPLITTER_SCHEDULE：同样一条配方都没有
            //   （RecipeLogic 永远搜不到 ⇒ 与 DUMMY 语义逐项相同），但模式名 = 中文「虚拟件调度」，
            //   且该表在 JEI 里隐藏（空分类没有意义）。分流器的行为一个字没改。
            NanochipRecipeTypes.SPLITTER_SCHEDULE, 1.0D, 1.0D, 2.0D, false, null,
            null, 0,
            "不加工，只调度：每 20 tick 按规则把虚拟件在颜色通道间搬运；每台提供 2 倍并行");

    private final String machineId;

    /**
     * 该模块在 NH 侧的正脸图标集<b>前缀</b>（{@code OVERLAY_FRONT_<本值>}）——
     * 「kind ↔ NH 图标集」的<b>唯一来源</b>。
     *
     * <p>取值由 GT5U {@code gregtech-5.09.54.20.jar} 的字节码逐类取证得出（§75）：
     * 每个 NH 模块类在自己的 {@code getTexture(...)} 里引用<b>自己那一套</b>四个字段
     * （{@code OVERLAY_FRONT_X} / {@code _GLOW} / {@code _ACTIVE} / {@code _ACTIVE_GLOW}），
     * 并与 {@code ModuleTypes.<常量>} 的 {@code GT5U.tooltip.nac.module.<键>.name} 英文名互印。
     *
     * <p><b>它在运行时的消费者</b>：构造器用它推导贴图目录名（{@link #overlayDirOf}），
     * 并与 {@link #machineId()} 做相等断言 —— 见构造器里的不变量。
     * 贴图目录本身在构建期已按 {@code machineId()} 落地（{@code block/multiblock/<machineId>/}），
     * 由 {@code NanochipMachines.moduleOverlayTex(kind)} 拼进 {@code ResourceLocation}；
     * 两条名字靠这个不变量锁在一起。
     *
     * <p><b>唯一不按枚举名直译的一个</b>：{@code BIOLOGICAL_COORDINATOR} 对应的是
     * {@code OVERLAY_FRONT_BIOLOGICAL_COORDINATION}（NH 类名 {@code MTEBiologicalCoordinationModule}，
     * 图标集名用 COORDINATION，而 tooltip 键却用 {@code biological_coordinator}）——
     * 所以这里必须逐个显式写死，不能靠 {@code name()} 推导；对应的目录名例外登记在
     * {@link #overlayDirOf(String)}。
     *
     * <p>★ 2026-09-18：新增的三台组织器（{@code EXOTIC_ / COSMIC_ / SUPRACAUSAL_ORGANIZER}）
     * <b>在 NH 里没有对应的图标集</b>（原版 NAC 只有 11 个模块）⇒ 它们的 {@code overlayPrefix}
     * 只是按同一命名规则给本模组自己的贴图目录起名（直译：{@code nanochip_exotic_organizer} …），
     * 贴图从光学那台复制改名。这不是"漏取证"，而是"NH 侧本就不存在"，如实登记在此。
     */
    private final String overlayPrefix;

    private final String nameZh;
    private final String nameEn;
    private final GTRecipeType recipeType;
    private final double euFactor;
    private final double durationFactor;
    private final double parallelFactor;
    private final boolean unlocksFullMode;
    private final NanochipCalibration.Family[] familyFilter;
    private final String upkeepFluidId;
    private final int upkeepMbPerSecond;
    private final String mechanicZh;

    NanochipModuleKind(String machineId, String overlayPrefix, String nameZh, String nameEn,
                       GTRecipeType recipeType,
                       double euFactor, double durationFactor, double parallelFactor,
                       boolean unlocksFullMode, NanochipCalibration.Family[] familyFilter,
                       String upkeepFluidId, int upkeepMbPerSecond, String mechanicZh) {
        this.machineId = machineId;
        this.overlayPrefix = overlayPrefix;
        // ★★ 不变量（2026-09-17）：overlayPrefix（NH 图标集名）是唯一来源，
        //    由它推出的贴图目录名必须 == machineId。
        //    失败 ⇒ 有人只改了一边（"注释说 A、实现是 B" 就是这样长出来的）⇒ 立刻炸，别等贴图 404。
        String expectDir = overlayDirOf(overlayPrefix);
        if (!expectDir.equals(machineId)) {
            throw new IllegalStateException("NanochipModuleKind." + name()
                    + ": NH 图标集名前缀 " + overlayPrefix + " 推出的贴图目录是 " + expectDir
                    + "，而 machineId 声明为 " + machineId
                    + " —— 两者必须一致（要加例外请改 overlayDirOf(String) 并同步注释与贴图目录）");
        }
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.recipeType = recipeType;
        this.euFactor = euFactor;
        this.durationFactor = durationFactor;
        this.parallelFactor = parallelFactor;
        this.unlocksFullMode = unlocksFullMode;
        this.familyFilter = familyFilter;
        this.upkeepFluidId = upkeepFluidId;
        this.upkeepMbPerSecond = upkeepMbPerSecond;
        this.mechanicZh = mechanicZh;
    }

    public String machineId() {
        return machineId;
    }

    /**
     * <b>本模块的最大配方时间（tick）</b> —— wiki 各模块页 {@code |数据=} 段「能量缓存」里逐页写明的那个数。
     *
     * <blockquote>「模块缓存 = NAC最大输入功率 × (2 × 模块最大配方时间)」</blockquote>
     *
     * <p>各页原文（`_census/wiki_抓取/raw/*.wiki.txt`，抓取于 2026-09-18）：
     * <ul>
     *   <li>元件预处理装置 = 100 ⇒ 缓存 ×200</li>
     *   <li>光学优化组织器 / 纳米精度导线追踪器 = 200 ⇒ ×400</li>
     *   <li>全板浸没设备 / 纳米封装卷绕器 / 超导线束分离器 / 超高能蚀刻阵列 = 400 ⇒ ×800</li>
     *   <li>加速生物调谐器 = 600 ⇒ ×1200</li>
     *   <li>纳米精度切割仓 = 800 ⇒ ×1600</li>
     *   <li>纳米芯片组装矩阵 = 10000 ⇒ ×20000</li>
     * </ul>
     *
     * <p>本模组扩展的三台组织器（奇异 / 寰宇 / 超因果，NH 无对应页）按同族「光学优化组织器」取 200；
     * 分流器不加工配方，取 100 占位（它不吃能量缓存）。
     *
     * <p>刻意写成 {@code switch} 的<b>字面量</b>：{@code _census/check_nac_mechanics_parity.js} 的 B8c
     * 直接读这张表与 wiki 页面上的数字逐条比对 —— 不能改成算出来的值。
     */
    public int maxRecipeTicks() {
        return switch (this) {
            case SMD_PROCESSOR -> 100;
            case OPTICAL_ORGANIZER, WIRE_TRACER, EXOTIC_ORGANIZER, COSMIC_ORGANIZER, SUPRACAUSAL_ORGANIZER -> 200;
            case BOARD_PROCESSOR, ENCASEMENT_WRAPPER, SUPERCONDUCTOR_SPLITTER, ETCHING_ARRAY -> 400;
            case BIOLOGICAL_COORDINATOR -> 600;
            case CUTTING_CHAMBER -> 800;
            case ASSEMBLY_MATRIX -> 10000;
            case SPLITTER -> 100;
        };
    }

    /**
     * 本模块正脸图标集前缀（NH 侧名叫 {@code OVERLAY_FRONT_<本值>}）。
     *
     * <p>GTCEu 侧的实际贴图根路径由 {@code NanochipMachines.moduleOverlayTex(kind)} 给出：
     * {@code gtlmodular:block/multiblock/<machineId()>}，目录里放
     * {@code overlay_front.png} / {@code overlay_front_active.png} /
     * {@code overlay_front_active_emissive.png}，分别逐字节对应 NH 的
     * {@code OVERLAY_FRONT_<本值>} / {@code _ACTIVE} / {@code _ACTIVE_GLOW}（§75）。
     *
     * <p>本值与 {@link #machineId()} 的一致性由构造器不变量强制（见 {@link #overlayDirOf(String)}）。
     */
    public String overlayPrefix() {
        return overlayPrefix;
    }

    /**
     * <b>NH 图标集名 → 本模组贴图目录名 的唯一换算点</b>（构造器用它做不变量校验）。
     *
     * <p>规则：{@code "nanochip_" + overlayPrefix.toLowerCase(Locale.ROOT)}。
     *
     * <p><b>唯一例外</b>：{@code BIOLOGICAL_COORDINATION} ⇒ {@code nanochip_biological_coordinator}。
     * NH 侧那个模块类的图标集名用 COORDINATION（类名 {@code MTEBiologicalCoordinationModule}），
     * 但 tooltip 键/方块名是 {@code biological_coordinator}；本模组的贴图目录<b>跟方块名走</b>，
     * 所以这一条必须显式登记 —— 不能靠直译，也不能靠 {@code name()}。
     * 例外只此一条：{@code _census/check_module_overlays.js} 会拿 NH 字节码独立验一遍。
     */
    private static String overlayDirOf(String overlayPrefix) {
        if ("BIOLOGICAL_COORDINATION".equals(overlayPrefix)) {
            return "nanochip_biological_coordinator";
        }
        return "nanochip_" + overlayPrefix.toLowerCase(Locale.ROOT);
    }

    public String nameZh() {
        return nameZh;
    }

    public String nameEn() {
        return nameEn;
    }

    /** 模块机独立运行用的配方表（原版模块的加工环节）。 */
    public GTRecipeType recipeType() {
        return recipeType;
    }

    public double euFactor() {
        return euFactor;
    }

    public double durationFactor() {
        return durationFactor;
    }

    public double parallelFactor() {
        return parallelFactor;
    }

    /** 该模块是否是「全流程档」的门票（只有组装矩阵是）。 */
    public boolean unlocksFullMode() {
        return unlocksFullMode;
    }

    /** 该模块的能耗/时长加成是否只作用于这些电路族（null = 不限）。 */
    public boolean appliesTo(NanochipCalibration.Family family) {
        if (familyFilter == null || family == null) {
            return true;
        }
        for (NanochipCalibration.Family f : familyFilter) {
            if (f == family) {
                return true;
            }
        }
        return false;
    }

    /** 该模块是否是「调度型」模块（分流器：不加工，只搬通道）。 */
    public boolean isRouter() {
        return this == SPLITTER;
    }

    /**
     * 该模块是不是「<b>净化水等级缩放</b>」那一族的组织器（光学 + 2026-09-18 新增的三台）。
     *
     * <p>为什么需要这个方法：主机的 {@code moduleEuMultiplier} / {@code moduleDurationMultiplier}
     * 对这族模块<b>不是</b>简单地乘一次 {@code euFactor}，而是按 {@code pow(euFactor, waterStrength)}
     * 表达「同类加成互相叠加」，并且<b>没供水就不给加成</b>；本模块机的界面也只为这族多显示一行
     * 「净化水等级」。原先这两处都写死 {@code kind == OPTICAL_ORGANIZER}，新增三台后必须收口到一处，
     * 否则三台会退化成「无条件 ×0.90」（供水机制形同虚设）。
     */
    public boolean isWaterScaledOrganizer() {
        return organizerFamily() != null;
    }

    /**
     * 该组织器「水加成」归属的电路族 —— NH 里「满校准后该族加成翻倍」那条规则用它。
     *
     * <p>四个组织器与族的对应（族常量见 {@link NanochipCalibration.Family}）：
     * <ul>
     *   <li>{@link #OPTICAL_ORGANIZER} → {@code OPTICAL}（光学电路，可校准）；</li>
     *   <li>{@link #EXOTIC_ORGANIZER} → {@code EXOTIC}（奇异电路）；</li>
     *   <li>{@link #COSMIC_ORGANIZER} → {@code COSMIC}（寰宇电路）；</li>
     *   <li>{@link #SUPRACAUSAL_ORGANIZER} → {@code TEMPORAL} —— 超因果族在本包的校准表里归
     *       校准条上那条「<b>超因果电路</b>」（{@code NanochipCalibration.Family.of(ResourceLocation)}
     *       对 {@code supracausal} 的归类；2026-09-21 队长令「第5个是合并成一条」后，
     *       原「特殊」（specials）那一段也并进了这一条）。</li>
     * </ul>
     * <p>★ <b>2026-09-21 更新（原注释末尾那段"这三台永远拿不到翻倍"已不成立）</b>：
     * 队长令「<b>那个阈值肯定要设的</b>」⇒ {@code EXOTIC} / {@code COSMIC} / {@code TEMPORAL}
     * 三族阈值已设成 <b>0.01/0.05/0.20</b> ⇒ 这三台的「满校准翻倍」
     * （{@code NanochipCalibration.specialActive}）<b>可达</b>，主机侧一行都不用改
     * （规则本来就写全了）。⚠️ 但原版「<b>同时只能存在一个校准</b>」这条规格不变
     * （{@code activeFamily()} 取档位最高者、同档比数量）⇒ 四台组织器里同一时刻
     * <b>最多只有一台</b>能拿到翻倍，不是"谁先满谁永久生效"。
     */
    public NanochipCalibration.Family organizerFamily() {
        return switch (this) {
            case OPTICAL_ORGANIZER -> NanochipCalibration.Family.OPTICAL;
            case EXOTIC_ORGANIZER -> NanochipCalibration.Family.EXOTIC;
            case COSMIC_ORGANIZER -> NanochipCalibration.Family.COSMIC;
            case SUPRACAUSAL_ORGANIZER -> NanochipCalibration.Family.TEMPORAL;
            default -> null;
        };
    }

    /** 运行耗材的流体 id（null = 不耗）。 */
    public String upkeepFluidId() {
        return upkeepFluidId;
    }

    /** 运行耗材速率（mB/秒）。 */
    public int upkeepMbPerSecond() {
        return upkeepMbPerSecond;
    }

    /** 是否需要耗材。 */
    public boolean hasUpkeep() {
        return upkeepFluidId != null && !upkeepFluidId.isBlank() && upkeepMbPerSecond > 0;
    }

    /** 机制说明（物品提示用）。 */
    public String mechanicZh() {
        return mechanicZh;
    }

    /**
     * 该模块的玩家文案键前缀：{@code gtlmodular.nanochip.module.<machineId>}。
     *
     * <p>用途：{@code .role}（一行职能，机器界面/物品提示用）与 {@code .desc}（功能说明，
     * 吃什么料 / 出什么 / 特殊规则）。中文是主文案，英文放在 en_us.json 同一批键里。
     */
    public String langKey(String suffix) {
        return "gtlmodular.nanochip.module." + machineId + "." + suffix;
    }

    /** 按机器 id 反查（最后一段路径）。 */
    public static NanochipModuleKind byMachineId(String id) {
        if (id == null) {
            return null;
        }
        String key = id.toLowerCase(Locale.ROOT);
        int colon = key.indexOf(':');
        if (colon >= 0) {
            key = key.substring(colon + 1);
        }
        for (NanochipModuleKind kind : values()) {
            if (kind.machineId.equals(key)) {
                return kind;
            }
        }
        return null;
    }
}
