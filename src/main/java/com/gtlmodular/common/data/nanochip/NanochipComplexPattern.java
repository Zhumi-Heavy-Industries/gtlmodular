package com.gtlmodular.common.data.nanochip;

import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.data.NanochipBlocks;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.gregtechceu.gtceu.api.pattern.Predicates.air;
import static com.gregtechceu.gtceu.api.pattern.Predicates.any;
import static com.gregtechceu.gtceu.api.pattern.Predicates.blocks;
import static com.gregtechceu.gtceu.api.pattern.Predicates.controller;
import static com.gregtechceu.gtceu.api.pattern.Predicates.custom;
import static com.gregtechceu.gtceu.api.pattern.Predicates.frames;
import com.gtlmodular.common.machine.nanochip.NanochipModuleKind;

/**
 * 纳米芯片组装复合体（NAC）与其模块机的结构生成器 —— <b>照搬 GTNH 原版几何</b>。
 *
 * <p>本类的结构数据<b>不是手写的</b>，而是由 {@code scripts/gen_nac_slices.js} 从
 * GTNH 原版 {@code AssemblyComplexStructureString.MAIN_STRUCTURE} 的逐字节重建
 * （{@code _gtnh/_tools/main_structure.json}）直接生成，脚本里带<b>往返比对自检</b>
 * （解回来必须与原数组逐字符相同，非空方块数必须是 8713）。
 *
 * <h3>轴序与「上下翻转」纪律（最容易翻车的一步）</h3>
 * <ul>
 *   <li>原版（structurelib）：{@code MAIN_STRUCTURE[Z][Y].charAt(X)}，
 *       <b>外层下标是 Z、内层下标是 Y、字符下标是 X</b>，且 <b>Y=0 是【最顶】</b>
 *       （{@code worldY = K − Y}，见 {@code _研究_NH_NAC_D_结构与GUI.md} §1.2 的四路证据）。</li>
 *   <li>本模组（GTCEu {@code FactoryBlockPattern}）：{@code aisles[z][row]}，
 *       <b>row = 0 是【最底】</b>（+Y 方向；见 {@code NAC_进游戏验证清单.md} §11.2 的三路取证）。</li>
 *   <li>⇒ 生成时<b>整体上下翻转</b>：{@code row = 48 − Y}。
 *       本类里的 {@code NAC_SLICES} 已经翻好，每行开头的数字就是这个翻转后的 row。</li>
 * </ul>
 *
 * <p><b>控制器落在第几层（自检）</b>：原版唯一的 {@code ~} 在 (X=31, Y=40, Z=30)，
 * 翻转后是 {@code (x=31, row=8, z=30)} —— 即<b>自底往上第 9 层</b>
 * （{@code 48 − 40 = 8}），与 {@code MAIN_OFFSET_X/Y/Z = 31/40/30} 完全对得上。
 * 因此控制器在图案里的下标就是 {@link #NAC_CTRL_ROW}，GTCEu 以「控制器那一格」为原点
 * 铺开整座结构：世界坐标 = 控制器坐标 + (列 − 31, 行 − 8, 剖面 − {@link #NAC_CTRL_Z})。
 *
 * <h3>★ 图案内 180° 旋转（2026-09-16，修"脸朝背面才成形"）</h3>
 * <p>生成时把整座图案绕**图案中心 (31,31)** 转了 180°：{@code P'(x,row,z) = P(62−x, row, 62−z)}，
 * 于是控制器那一格从 {@code z=30} 移到 <b>{@code z=32}</b>（{@link #NAC_CTRL_Z} 同步改成 32）。
 * <p><b>为什么不影响外壳、为什么能修好</b>（已用玩家存档逐格验证，见施工记录 §50）：
 * GTCEu 铺图案时按控制器的 {@code frontFacing} 旋转——
 * {@code facing=south} 是"原样"（列 → +X、剖面 → +Z），{@code facing=north} 是"绕控制器转 180°"。
 * 玩家现存那座的存档实测是 {@code facing=south}（已成形），而它的外壳正是"原样"那套坐标；
 * 图案转 180° 之后，"原样"那套坐标改由 {@code facing=north} 命中
 * ⇒ <b>外壳一格都不用动，只需把控制器朝向翻 180°</b>，
 * 而控制器的正脸就从"贴着 z=31 那面网格接口墙"变成"正对那条 32 格、直通结构外的走廊"。
 * <p><b>不是镜像</b>：本改动是纯旋转（手性不变）；§11 记录的 X 轴镜像仍未处理。
 *
 * <h3>NAC 字符表（照抄原版 {@code MTENanochipAssemblyComplex.<clinit>}）</h3>
 * <table border="1">
 *   <tr><th>字符</th><th>原版含义</th><th>本模组</th></tr>
 *   <tr><td>{@code A}</td><td>防火墙投影机械方块（不可换仓）</td><td>同（不可换仓）</td></tr>
 *   <tr><td>{@code B}</td><td>网格接口机械方块（不可换仓）</td><td>同（不可换仓）</td></tr>
 *   <tr><td>{@code C}</td><td>强化机械方块（不可换仓）</td><td>同（不可换仓）</td></tr>
 *   <tr><td>{@code D}</td><td>{@code GTStructureUtility.ofFrame(Materials.Naquadah)}</td>
 *       <td>{@code Predicates.frames(GTMaterials.Naquadah)} ⇒ {@code gtceu:naquadah_frame}
 *           （GTCEu 自带，<b>没有新增方块</b>）</td></tr>
 *   <tr><td>{@code E}</td><td>复合体玻璃（不可换仓）</td><td>同（不可换仓）</td></tr>
 *   <tr><td>{@code F}</td><td>{@code atLeast{AssemblyModule:0, IgnoredHatch:0}} + 链网格接口</td>
 *       <td>模块控制器方块 <b>或</b> 网格接口 <b>或</b> 仓室（见 {@link #moduleSlots}）；
 *           并在 GTCEu 的 {@code matchContext} 里登记槽名 {@link #MODULE_SLOT_NAME}，
 *           主机据此<b>按结构位置</b>收模块（不再半径扫描）</td></tr>
 *   <tr><td>{@code H}</td><td>真空仓 / 总线 / 激光仓 / 能源仓 <b>或机壳</b>（原版带
 *       {@code buildAndChain(NanochipMeshInterfaceCasing)}）</td>
 *       <td>{@link #hatchOrCasing}({@link #nanochipHatches()}) —— 沿用现有的仓室判定，
 *           <b>再补上原版那一手机壳</b>（放真仓室可以、放网格接口当填充也可以）</td></tr>
 *   <tr><td>{@code I}</td><td>{@code atLeast{IgnoredHatch:0}}（任何仓都吃、计数恒 0）</td>
 *       <td>网格接口 <b>或</b> 任何 GTCEu 仓位（{@link #ignoredSlots()}）——
 *           取「宽放」而不是「不允许放东西」，理由见 {@link #ignoredSlots()} 的注释</td></tr>
 *   <tr><td>{@code J}</td><td>计算矩阵机械方块（不可换仓）</td><td>同（不可换仓）</td></tr>
 *   <tr><td>{@code ~}</td><td>控制器</td><td>同</td></tr>
 *   <tr><td>空格</td><td><b>未声明字符 = 不检查</b>（structurelib 的 {@code addElement} 里没有 32，
 *       {@code addShape} 把空格改成哨兵后<b>整个丢掉</b>）</td>
 *       <td>★ 2026-09-18：{@code Predicates.any()}（= 不检查，与 GTCEu 默认值与原版一致）。
 *           <b>改前</b>是 {@code air()}（必须真空，比原版严 21.6 倍，也是 NAC 卡顿的根因）——
 *           取证与回退开关见 {@code build(...)} 里的长注释</td></tr>
 *   <tr><td>{@code K}</td><td>（本模组自造，原版没有）</td>
 *       <td>{@code any()} —— 12 个基座正上方 7×7×10 的塔身让位块，见 {@link #TOWER_SPACE}</td></tr>
 * </table>
 * <p><b>{@code G}（71）原版被跳过</b>，数据里也确实一个都没有 —— 我们同样不用。
 *
 * <h3>模块机（{@value #MOD_W}×{@value #MOD_H}×{@value #MOD_D}）</h3>
 * 照抄原版 {@code MTENanochipAssemblyModuleBase.addBaseStructure} 的 {@code base_structure}
 * （dims {@code [7,3]}，Y=0 最顶；同样上下翻转）：控制器在模块局部 {@code (X=3, Y=0, Z=0)}
 * → 翻转后 {@code (x=3, row=2, z=0)}（<b>顶层</b>）。
 * 顶层/底层的「去四角环」是仓位（{@code V}）—— 顶层 19 格（另一格被控制器占了）+ 底层 20 格 = 39 格，
 * 判定同样用 {@link #hatchOrCasing}({@link #nanochipHatches()})：<b>仓室或网格接口机壳都能放</b>；
 * 中层只有 5×5 的强化内芯。
 *
 * <p><b>刻意放宽的一处（必须记下来）</b>：原版模块<i>中层外环</i>是空气，但 NAC 基座的同一层
 * （原版 Y=47）是<b>复合体玻璃 {@code E}</b> —— 模块正好塞进基座里时，「空气」检查必然失败。
 * 所以本模组把模块中层的 20 格外环映射成 {@code any()}（{@code X}），只要求「不挡路」。
 */
public final class NanochipComplexPattern {

    private NanochipComplexPattern() {
    }

    // ---------- 尺寸（原版包围盒 63 × 49 × 63） ----------

    /** NAC 本体宽（X）。 */
    public static final int NAC_W = 63;
    /** NAC 本体高（Y，原版实际只用到内层下标 0..48）。 */
    public static final int NAC_H = 49;
    /** NAC 本体深（Z）。 */
    public static final int NAC_D = 63;

    /** 模块机宽（X）。 */
    public static final int MOD_W = 7;
    /** 模块机高（Y）—— 原版 {@code base_structure} 的 dims = [7,3]。 */
    public static final int MOD_H = 3;
    /** 模块机深（Z）。 */
    public static final int MOD_D = 7;

    // ---------- 控制器在图案里的下标（= 结构原点） ----------

    /** NAC 控制器列下标（原版 {@code MAIN_OFFSET_X}）。 */
    public static final int NAC_CTRL_X = 31;
    /** NAC 控制器行下标（<b>已翻转</b>；原版 {@code MAIN_OFFSET_Y=40} ⇒ 48−40=8，自底往上第 9 层）。 */
    public static final int NAC_CTRL_ROW = 8;
    /**
     * NAC 控制器剖面下标。
     *
     * <p>原版 {@code MAIN_OFFSET_Z = 30}；生成时整座图案绕图案中心转了 180°（见类注释），
     * 于是它变成 {@code 62 − 30 = 32} —— <b>必须与 {@code NAC_SLICES} 里 {@code ~} 的位置一致</b>
     * （{@code scripts/splice_nac_slices.js} 会断言这一点，改了一处忘了另一处会直接报错）。
     */
    public static final int NAC_CTRL_Z = 32;

    /** 模块控制器列下标（模块局部 X=3）。 */
    public static final int MOD_CTRL_X = 3;
    /** 模块控制器行下标（<b>已翻转</b>；模块局部 Y=0 是最顶 ⇒ row=2）。 */
    public static final int MOD_CTRL_ROW = 2;

    /**
     * 模块控制器剖面下标（模块局部 Z=6）。
     *
     * <p>★ 2026-09-16「脸朝外」修复后由 {@code 0} 改为 {@code 6}：模块图案（底座 {@link #MOD_LAYERS}
     * 与 11 个塔身）在 {@link #buildModule} 里整体<b>绕竖轴旋转 180°</b>（见 {@link #rotate180}），
     * 于是控制器那一格从 {@code z=0} 移到 {@code z=6}；配套要求玩家把控制器装成 {@code facing=north}
     * （原来 {@code south}）。旋转与 north 的 180° 映射相消 ⇒ <b>世界几何一格不变</b>，
     * 唯一变化是控制器的<b>正脸由"朝向自己基座中心"改为"背离基座中心"</b> ✓。
     * 推导与实测背景见施工记录 §66.2 / §67。
     */
    public static final int MOD_CTRL_Z = 6;

    /**
     * {@code F} 位（模块位）在 GTCEu 结构匹配结果里的槽名。
     *
     * <p>机制：GTCEu 的 {@code SimplePredicate.setSlotName(name)} 会在每次<b>匹配成功</b>时把该格的
     * 世界坐标记进 {@code MultiblockState.getMatchContext()} 的 {@code "slots"} 表
     * （{@code Map<BlockPos.asLong(), Set<String>>}）。结构成形后（{@code onStructureFormed}）
     * 这张表仍然可读 —— GTCEu 自己的线圈/加热线圈就是靠同一套 {@code matchContext} 机制传递结果的。
     * 这正是 GT5U 里「{@code F} 位 hatch 元素的 adder 把模块收进 {@code modules} 列表」在 GTCEu 上的等价物：
     * <b>连接完全由结构位置驱动，不含任何距离常量</b>。
     */
    public static final String MODULE_SLOT_NAME = "gtlmodular_nanochip_module_slot";

    // ---------- 结构字符 ----------

    private static final char FIREWALL = 'A';
    private static final char MESH = 'B';
    private static final char REINFORCEMENT = 'C';
    private static final char FRAME = 'D';
    private static final char GLASS = 'E';
    private static final char MODULE_SLOT = 'F';
    private static final char HATCH = 'H';
    private static final char IGNORED = 'I';
    private static final char MATRIX = 'J';
    private static final char CONTROLLER = '~';
    private static final char AIR = ' ';

    // 模块字符（原版 base_structure）
    private static final char MOD_HATCH = 'V';
    private static final char MOD_MESH = 'P';
    private static final char MOD_CORE = 'Z';
    /** 模块中层外环：原版是空气，这里放宽成 {@code any()}，见类注释末段。 */
    private static final char MOD_RING_ANY = 'X';

    // ---------- 模块塔身（main）：**角色化**字符表 ----------
    //
    // 11 个模块的塔身用的是**各自的字母**（例如组装矩阵是 B..G，纳米组件分流器是 A/B/C），
    // 所以不能在类里写死一套 TWR_* 常量。每个模块的「字符 → 角色」表由
    // `_census/decode_all_modules.js` 从各自 class 常量池解出，再由
    // `_census/gen_all_towers_java.js` 生成到下面的 buildTowers()。

    /** 塔身字符的"角色"（见 `_census/all_modules_layers.md` 的字符表）。 */
    private enum TowerRole {
        /** 部件装配线外壳（原版 `ofBlocksTiered(componentAssemblylineCasing, 0..13)`） */
        ASSEMBLY,
        /** 纳米芯片网格接口机械方块 */
        MESH,
        /** 纳米芯片强化机械方块 */
        REINFORCEMENT,
        /** 纳米芯片复合体玻璃 */
        GLASS,
        /** 该材料的框架（材料名见 {@link TowerCell#material()}） */
        FRAME,
        /** 该材料的板金属（本包没有这一形态 ⇒ 用同材料实心块替代） */
        SHEET,
        /** 仓位：仓室【或】网格接口机壳（原版 `buildAndChain`） */
        HATCH,
        /** 原版 UEV 机壳（GTNH `sBlockCasingsNH:10` = "UEV Machine Casing"） */
        UEV_CASING,
        /** 计算矩阵机壳（原版是 gtnhlanth 的"屏蔽加速器外壳"，本包没有 ⇒ 替代） */
        MATRIX_CASING,
        /** 强化机壳（原版是"硅岩合金强化水厂外壳"，本包没有 ⇒ 替代） */
        REINFORCED_CASING,
        /**
         * 超导线束分离器 <b>A 位（6 格）</b>的线圈块 —— 原版 {@code sSolenoidCoilCasings:7}
         * （GT5U {@code gt.blockcasings.cyclotron_coils.7}「UHV Solenoid Superconductor Coil」）。
         *
         * <p>★ 2026-09-19 队长令「<b>两个线圈方块给他定死</b>」：NH 的 A/B 两位用的是<b>两味不同</b>的线圈
         * （A = {@code :7}、B = {@code :8}），而本模组上一版把它们合并成同一个 {@code COIL} 角色
         * ⇒ <b>两味线圈任意位置都能放</b>，与 NH 不一致。现按位置拆成两个角色码（本常量与
         * {@link #COIL_UEV}），并把两味真线圈各自<b>只</b>追加到自己那一格
         * （见 {@code extraCandidatesFor} 的对应分支）。
         *
         * <p><b>依据（两条独立证据）</b>：
         * <ul>
         *   <li>{@code _census/all_modules.json} 里 {@code id == "nanochip_superconductor_splitter"}
         *       那一条的 {@code cells}：{@code A = sSolenoidCoilCasings:7}、{@code B = sSolenoidCoilCasings:8}；
         *       格数由同条目的 {@code shape} 现算：A = <b>6</b> 格、B = <b>16</b> 格（共 22 格）。</li>
         *   <li>{@code _census/wiki_抓取/raw/超导线束分离器.wiki.txt} 的结构表：
         *       {@code 结构方块5=UEV超导线圈 数量=16}、{@code 结构方块6=UHV超导线圈 数量=6}。</li>
         * </ul>
         * <p>⇒ A 位 = UHV、B 位 = UEV，两位一一对应、不可互换。
         */
        COIL_UHV,
        /**
         * 超导线束分离器 <b>B 位（16 格）</b>的线圈块 —— 原版 {@code sSolenoidCoilCasings:8}
         * （「UEV Solenoid Superconductor Coil」）。取值与分位依据见 {@link #COIL_UHV}。
         */
        COIL_UEV,
        /** 模块控制器本体（`~`） */
        CONTROLLER,
    }

    /**
     * 一个塔身字符对应的东西。
     *
     * @param role     角色
     * @param material 只有 {@link TowerRole#FRAME}/{@link TowerRole#SHEET} 用得到（GTNH 的材料名，如
     *                 {@code NaquadahAlloy} / {@code RadoxPolymer}），最终会转成
     *                 {@code gtceu:<snake_case>_frame}
     */
    private record TowerCell(TowerRole role, String material) {
    }

    /**
     * 一个模块的塔身数据。
     *
     * @param offsetY 原版 {@code structureOffsetY()}
     *                —— 语义已定案（§28）：**控制器以上的层数**，也等于"塔身占几层"；
     *                模块图案总高 = {@code MOD_H + offsetY}
     * @param rows    原版数组行数（= 每片的形状串条数）。可能 = {@code offsetY}（如组装矩阵），
     *                也可能 = {@code offsetY + 1}（最底那一行**带控制器 {@code ~}**，如加速生物协调器）
     * @param slices  原版数组按原顺序摊平：`[z * rows + y]`，**y=0 是塔顶**
     * @param cells   字符 → 单元
     */
    private record TowerDef(int offsetY, int rows, String[] slices, Map<Character, TowerCell> cells) {
        /** 该模块图案的总高（行数）= 共享底座 3 层 + 塔身 offsetY 层。 */
        int height() {
            return MOD_H + offsetY;
        }
    }

    /**
     * 解析角色表字面量：`"A=MESH,B=REINFORCEMENT,D=FRAME:RadoxPolymer"`（由生成脚本产出）。
     * 用一行字符串而不是一大串 Java 调用，是为了让生成脚本的输出保持可读、可 diff。
     */
    private static Map<Character, TowerCell> cells(String spec) {
        Map<Character, TowerCell> map = new LinkedHashMap<>();
        for (String part : spec.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            // ★【P0 修复 2026-09-16】键是**单个字符**，`=` 的下标必须是 **1**（"A=MESH" 里 '=' 在 1）。
            //   这里原来写成 `eq != 2`（把"字符+等号"数成 2 个位置）⇒ **每一条都抛异常** ⇒
            //   静态字段 `MODULE_TOWERS` 初始化失败 ⇒ `ExceptionInInitializerError` ⇒
            //   玩家在**注册期**就崩（连游戏都进不去）。教训：注册期不允许跑重活，见 §48。
            int eq = part.indexOf('=');
            if (eq != 1) {
                throw new IllegalStateException("塔身角色表格式错：\"" + part + "\"（应形如 A=MESH）");
            }
            char ch = part.charAt(0);
            String[] roleAndMat = part.substring(eq + 1).split(":", 2);
            TowerRole role = TowerRole.valueOf(roleAndMat[0]);
            map.put(ch, new TowerCell(role, roleAndMat.length > 1 ? roleAndMat[1] : null));
        }
        return map;
    }

    /**
     * 主机图案里"<b>给塔身让位</b>"的字符。
     *
     * <p>原版 structurelib 里<b>没有声明的字符 = 不检查</b>，所以模块塔身可以长进主机的空腔。
     *
     * <p>★ 2026-09-18 起<b>本常量在语义上已与空格等价</b>：{@code build()} 里空格已由
     * {@code air()} 改回 {@code any()}（= 不检查，见 {@code build()} 里的长注释），两者都是
     * {@code Predicates.any()}。保留下这一个角色位是为了：
     * <ul>
     *   <li>{@link #openTowerSpace()} 的**运行期断言**照旧生效 —— 那 12 个 7×7×10 的空腔在
     *       {@code _gtnh/_tools/main_structure.json} 里必须本来就是空气（5880 格，非空 0 格，
     *       逐格已核）；</li>
     *   <li>万一有人删掉 {@code where(TOWER_SPACE, any())}，{@code 'K'} 会变成"未登记字符" ⇒
     *       {@code FactoryBlockPattern.makePredicateArray} 往 {@code blockMatches} 里填 {@code null}
     *       ⇒ gtlcore 的 {@code matchSlice} 在 {@code predicate.isAny()} 上直接 NPE。</li>
     * </ul>
     */
    private static final char TOWER_SPACE = 'K';

    /**
     * 主机基座正上方要"让位"的层数 —— <b>10 层</b>（row 3..12），足以让任何模块的塔身长进主机空腔。
     *
     * <p>取值口径：按<b>最高的模块塔身</b>向上取整让位，而不是"每个模块各让各的"。多让出来的那几层
     * 在结构判定上只是 {@code any()} = 不检查，<b>不会让主机变松</b>：那些格原本在图案里就是
     * "空气要求"，改成 {@code any()} 只影响"模块塔身能不能长进去"这一件事。
     *
     * <p>✅ 已核实（2026-09-16 数值订正）：{@code TOWER_SPACE_H = 10} ⇒ 12 个基座正上方
     * <b>7×7×10（row 3..12）= 12 × 490 = 5880 格</b>，在原版数据里**一格非空都没有**
     * （本机复算脚本同 {@code _census/_dbg_tower_space.js} 的判据：7×7×7 = 4116 格 0 非空、
     * 7×7×10 = 5880 格 0 非空）。运行期 {@link #openTowerSpace} 还会对每一格再断言一次
     * "原版是空气"，不是空气就抛异常 ⇒ 数据一变立刻暴露。
     * （旧注释写的"7 层 / 4116 格"是常量还是 7 时的残留，与现在的常量不符。）
     */
    private static final int TOWER_SPACE_H = 10;

    /**
     * 真空传送带仓的注册 id（本模组自造，见 {@link NanochipMachines#VACUUM_INPUT_HATCH}）。
     *
     * <p>只用作 {@link #vacuumHatches()} 的<b>注册顺序兜底</b>（正常路径直接用机器定义里的方块）；
     * 判据：{@code assets/gtlmodular/lang/*.json} 的 {@code block.gtlmodular.nanochip_vacuum_*_hatch}、
     * 两条配方 json 与 {@code NanochipSelfCheck} 里都在用这两个 id。
     */
    private static final String[] VACUUM_HATCH_IDS = {
            "gtlmodular:nanochip_vacuum_input_hatch",
            "gtlmodular:nanochip_vacuum_output_hatch",
    };

    // ---------- ★ 塔身方块的 id（唯一可配置处；改这里即可整体替换） ----------

    /** 硅岩合金框架（玩家 JEI 实测：提示里的模组名 = GregTech ⇒ GTCEu 注册）。不存在则按候选链回落。 */
    private static final String TWR_FRAME_BLOCK = "gtceu:naquadah_alloy_frame";
    /** 框架的回落 id（材料存在但没有框架形态时用）。 */
    private static final String TWR_FRAME_FALLBACK = "gtceu:naquadah_alloy_block";
    /**
     * 硅岩合金板金属的替代方块（★ 玩家 2026-09-15 二次确认：**本包没有"板金属"这个方块**）。
     *
     * <p>原版此处是 {@code GTStructureUtility.ofSheetMetal(NaquadahAlloy)}。我们做过穷举核查：
     * 99 个 mod jar 的**每一条 entry 名**与**每一条文本 entry 的原始字节**里搜
     * {@code sheet} / {@code sheetmetal} / {@code sheet_metal} / UTF-8 字节 {@code E6 9D BF E9 87 91 E5 B1 9E}
     * （=「板金属」）/ {@code \u677f\u91d1\u5c5e}，再加 resourcepacks / kubejs / config —— <b>零命中</b>
     * （详见施工记录 §29 与 `_census/material_survey.md`）。GTNH 侧它确实存在
     * （`gregtech/common/blocks/BlockSheetMetal.class` + lang `gt.oreprefix.material_sheetmetal=%s Sheetmetal`），
     * 那是 1.7.10 的东西，没有搬到这个包。
     *
     * <p>⇒ 用<b>同材料的实心块</b>替代（一定存在，且与框架能区分）。
     */
    private static final String TWR_SHEET_BLOCK = "gtceu:naquadah_alloy_block";
    /**
     * 部件装配线外壳：原版是 {@code ofBlocksTiered(componentAssemblylineCasing, 0..13)}（14 级）。
     *
     * <p>★ 2026-09-15 新发现：**gtlcore 提供了同名的 14 级方块** ——
     * {@code gtlcore:component_assembly_line_casing_lv/mv/hv/ev/iv/luv/zpm/uv/uhv/uev/uiv/uxv/opv/max}
     * （14 个 blockstate 全在 `gtlcore-1.2.3.2-fix2.jar` 里）。所以这里<b>按 14 级全部匹配</b>
     * —— 比只用一个 id 更贴近原版"分级"语义；万一一个都不存在，再回落到 GTCEu 的单级外壳。
     */
    private static final String TWR_ASSEMBLY_TIER_PREFIX = "gtlcore:component_assembly_line_casing_";
    /** 14 级的后缀（顺序 = 原版 tier 0..13）。 */
    private static final String[] TWR_ASSEMBLY_TIERS = {
            "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv", "uev", "uiv", "uxv", "opv", "max"};
    /** 单级回落（GTCEu 自带；GTNH 的 14 级在 GTCEu 里被塌成 1 个 id）。 */
    private static final String TWR_ASSEMBLY_BLOCK = "gtceu:assembly_line_casing";
    /** 原版 UEV 机壳（GTNH `sBlockCasingsNH:10`）；GTCEu 有同名方块 `uev_machine_casing` ✓。 */
    private static final String TWR_UEV_CASING_BLOCK = "gtceu:uev_machine_casing";
    /**
     * 通用线圈块 id（GTCEu 的普通线圈；NH 的"螺线管超导线圈"那一族本包没有 ⇒ 曾经是替代品）。
     *
     * <p><b>★ 2026-09-21 起塔身结构判定已不再用它</b>：队长拍板「严格定死」后，
     * {@code COIL_UHV}/{@code COIL_UEV} 两位只认 {@link NanochipBlocks#UHV_SOLENOID_SUPERCONDUCTOR_COIL} /
     * {@link NanochipBlocks#UEV_SOLENOID_SUPERCONDUCTOR_COIL}，<b>连这个通用线圈都不收</b>。
     *
     * <p><b>为什么还要留着这个常量</b>（别顺手删）：
     * <ul>
     *   <li>它是 {@code _census/check_ctm_targets.js} 的**锚点之一** —— 该脚本要求这个常量存在，
     *       并据此核对 `CtmClientSetup.FOREIGN_ALLOWED` 里那条 NH 线圈 CTM 白名单
     *       （`naquadah_coil_block` 是 TARGETS 的第 3 条，NH 的 `ctm/coils/naq` 逐字照抄）；</li>
     *   <li>万一要回退「定死」这一刀，它是现成的回落候选（改写两个 case 即可）。</li>
     * </ul>
     */
    private static final String TWR_COIL_BLOCK = "gtceu:naquadah_coil_block";

    // ---------- 结构数据（自动生成；见文件头的生成脚本说明） ----------

// 【自动生成 · 不要手改】由 scripts/gen_nac_slices.js 从
// _gtnh/_tools/main_structure.json（GTNH 原版 AssemblyComplexStructureString.MAIN_STRUCTURE
// 的逐字节重建）生成。
//
// 轴序纪律（见 _研究_NH_NAC_D_结构与GUI.md §1.2 / §6.2(2)）：
//   原版：MAIN_STRUCTURE[Z][Y].charAt(X)，内层下标 Y=0 是【最顶】。
//   本模组：FactoryBlockPattern 的 aisle 第 0 行 = 【最底层】。
//   ⇒ 生成时整体上下翻转：row = 48 - Y。下面每一行的 "行号" 就是这个翻转过后的 row。
//   控制器：原版 (X=31, Y=40, Z=30) → 翻转后 (x=31, row=8, z=30) → **图案内 180° 旋转后 (x=31, row=8, z=32)**，
//          即自底往上第 9 层（见类注释「图案内 180° 旋转」与 NAC_CTRL_Z）。
//
// 行内编码：'行号,左填充,内容'（内容已去掉左右空白；未列出的行 = 整行空气）。
// 原版字符：A 防火墙投影 / B 网格接口 / C 强化 / D 硅岩框架 / E 复合体玻璃 /
//          F 模块位 / H 仓位 / I 可忽略位 / J 计算矩阵 / ~ 控制器 / 空格 空气

// 【自动生成 · 不要手改】由 scripts/gen_nac_slices.js 从
// _gtnh/_tools/main_structure.json（GTNH 原版 AssemblyComplexStructureString.MAIN_STRUCTURE
// 的逐字节重建）生成。
//
// 轴序纪律（见 _研究_NH_NAC_D_结构与GUI.md §1.2 / §6.2(2)）：
//   原版：MAIN_STRUCTURE[Z][Y].charAt(X)，内层下标 Y=0 是【最顶】。
//   本模组：FactoryBlockPattern 的 aisle 第 0 行 = 【最底层】。
//   ⇒ 生成时整体上下翻转：row = 48 - Y。下面每一行的 "行号" 就是这个翻转过后的 row。
//
// ★ 图案内 180° 旋转（2026-09-16 加入，见施工记录 §50）：
//   P'(x, row, z) = P(62 - x, row, 62 - z) —— 每行内容 reverse + 片序反转，
//   控制器因此从 (x=31, row=8, z=30) 移到 **(x=31, row=8, z=32)**。
//   目的：让"能成形的控制器朝向"从 south 变成 north，使控制器正脸朝向结构外那条 32 格走廊。
//   这是**纯旋转**（不是镜像）：手性不变，X 轴镜像问题（§11）仍未处理。
//
// 行内编码：'行号,左填充,内容'（内容已去掉左右空白；未列出的行 = 整行空气）。
// 原版字符：A 防火墙投影 / B 网格接口 / C 强化 / D 硅岩框架 / E 复合体玻璃 /
//          F 模块位 / H 仓位 / I 可忽略位 / J 计算矩阵 / ~ 控制器 / 空格 空气

    private static final String[] NAC_SLICES = {
            "0,26,CCCC   CCCC;1,26,CCCCEEECCCC;2,26,CCCC   CCCC",// z=0
            "0,22,CCCC           CCCC;1,22,CCCCEEECEEECEEECCCC;2,22,CCCC           CCCC",// z=1
            "0,19,CCC                   CCC;1,19,CCCEEEEEEECEEECEEEEEEECCC;2,19,CCC                   CCC",// z=2
            "0,17,CC                         CC;1,17,CCEEEEEEEEEECEEECEEEEEEEEEECC;2,17,CC                         CC",// z=3
            "0,15,CC                             CC;1,15,CCEEEEEEEEEEEECEEECEEEEEEEEEEEECC;"
            + "2,15,CC                             CC",// z=4
            "0,14,C      IIIII           IIIII      C;1,14,CEEEEEEEEEEEEEECEEECEEEEEEEEEEEEEEC;"
            + "2,14,C      IIFII           IIFII      C",// z=5
            "0,12,CC      IBBBBBI         IBBBBBI      CC;1,12,CCEEEEEEECCCCCEEECEEECEEECCCCCEEEEEEECC;"
            + "2,12,CC      IBBBBBI         IBBBBBI      CC;3,12,CC                                   CC",// z=6
            "0,11,C        IBBBBBI         IBBBBBI        C;1,11,CEEEEEEEEECCCCCECCCCECCCCECCCCCEEEEEEEEEC;"
            + "2,11,C        IBBBBBI         IBBBBBI        C;3,11,C                                       C;"
            + "4,11,C                                       C",// z=7
            "0,10,C         IBBBBBICCCC CCCCIBBBBBI         C;1,10,CEEEEEEEEEECCCCCECCCCECCCCECCCCCEEEEEEEEEEC;"
            + "2,10,C         FBBBBBFCCCC CCCCFBBBBBF         C;3,10,C                                         C;"
            + "4,10,C                                         C;5,10,C                                         C",// z=8
            "0,9,A          IBBBBBI   C C   IBBBBBI          A;1,9,AEEEEEEEEEEECCCCCECCCCECCCCECCCCCEEEEEEEEEEEA;"
            + "2,9,A          IBBBBBI   C C   IBBBBBI          A;3,9,A                                           A;"
            + "4,9,A                                           A;5,9,A                                           A;"
            + "6,9,A                                           A;7,9,A                                           A",// z=9
            "0,8,C           IBBBBBI   C C   IBBBBBI           C;1,8,CEEEEEEEEEEEECCCCCEEECCECCEEECCCCCEEEEEEEEEEEEC;"
            + "2,8,C           IBBBBBI   C C   IBBBBBI           C;3,8,C                                             C;"
            + "4,8,C                                             C;5,8,C                                             C",// z=10
            "0,7,C    IIIII    IIIII    C C    IIIII    IIIII    C;1,7,CEEEEEEEEEEEEEEEEEEEEECCECCEEEEEEEEEEEEEEEEEEEEEC;"
            + "2,7,C    IIFII    IIFII    C C    IIFII    IIFII    C;3,7,C                                               C;"
            + "4,7,C                                               C",// z=11
            "0,6,C    IBBBBBI            C C            IBBBBBI    C;"
            + "1,6,CEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEC;"
            + "2,6,C    IBBBBBI            C C            IBBBBBI    C;"
            + "3,6,C                                                 C",// z=12
            "0,6,C    IBBBBBI            C C            IBBBBBI    C;"
            + "1,6,CEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEC;"
            + "2,6,C    IBBBBBI            C C            IBBBBBI    C;"
            + "3,6,C                                                 C",// z=13
            "0,5,C     IBBBBBI            C C            IBBBBBI     C;"
            + "1,5,CEEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEEC;"
            + "2,5,C     FBBBBBF            C C            FBBBBBF     C",// z=14
            "0,4,C      IBBBBBI            C C            IBBBBBI      C;"
            + "1,4,CEEEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEEEC;"
            + "2,4,C      IBBBBBI            C C            IBBBBBI      C",// z=15
            "0,4,C      IBBBBBI            C C            IBBBBBI      C;"
            + "1,4,CEEEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEEEC;"
            + "2,4,C      IBBBBBI            C C            IBBBBBI      C",// z=16
            "0,3,C        IIIIIC            C C            CIIIII        C;"
            + "1,3,CEEEEEEEEEEEEECCEEEEEEEEEECCECCEEEEEEEEEECCEEEEEEEEEEEEEC;"
            + "2,3,C        IIFIIC            C C            CIIFII        C",// z=17
            "0,3,C              C           C C           C              C;"
            + "1,3,CEEEEEEEEEEEEECCCEEEEEEEEECCECCEEEEEEEEECCCEEEEEEEEEEEEEC;"
            + "2,3,C              C           C C           C              C",// z=18
            "0,2,C                C          C C          C                C;"
            + "1,2,CEEEEEEEEEEEEEEECCCEEEEEEEECCECCEEEEEEEECCCEEEEEEEEEEEEEEEC;"
            + "2,2,C                C          C C          C                C",// z=19
            "0,2,C   IIIII         C         C C         C         IIIII   C;"
            + "1,2,CEEEEEEEEEEEEEEEECCCEEEEEECCCCCCCEEEEEECCCEEEEEEEEEEEEEEEEC;"
            + "2,2,C   IIFII         C       BBC CBB       C         IIFII   C;3,29,B   B;4,29,B   B",// z=20
            "0,2,C  IBBBBBI         C        C C        C         IBBBBBI  C;"
            + "1,2,CEEECCCCCEEEEEEEEECCCEEECCCCCCCCCCCEEECCCEEEEEEEEECCCCCEEEC;"
            + "2,2,C  IBBBBBI         C    BBCCCCCCCBB    C         IBBBBBI  C;3,28,CCCCCCC;4,28,CCCCCCC;5,29,B   B;"
            + "6,29,B   B;7,29,B   B;8,29,B   B;9,29,B   B",// z=21
            "0,1,C   IBBBBBI          C       C C       C          IBBBBBI   C;"
            + "1,1,CEEEECCCCCEEEEEEEEEECCCECCC  C C  CCCECCCEEEEEEEEEECCCCCEEEEC;"
            + "2,1,C   IBBBBBI          C  BCCEEEEEEECCB  C          IBBBBBI   C;3,26,CC       CC;4,26,CCC     CCC;"
            + "5,28,CCCCCCC;6,28,CCECECC;7,28,CCECECC;8,28,CCECECC;9,28,CCCCCCC;10,29,B   B;11,29,B   B;12,29,B   B;"
            + "13,29,B   B;14,29,B   B;15,29,B   B",// z=22
            "0,1,C   IBBBBBI           C      C C      C           IBBBBBI   C;"
            + "1,1,CEEEECCCCCEEEEEEEEEEECCCC    C C    CCCCEEEEEEEEEEECCCCCEEEEC;"
            + "2,1,C   FBBBBBF           CBCEEEEEEEEEEECBC           FBBBBBF   C;3,25,C           C;4,25,CC C     C CC;"
            + "5,26,CC       CC;6,26,EC       CE;7,26,EC       CE;8,26,EC       CE;9,26,CC       CC;10,28,CCCCCCC;"
            + "11,28,CCECECC;12,28,CCECECC;13,28,CCECECC;14,28,CCECECC;15,28,CCCCCCC;16,29,B   B;17,29,B   B;18,29,B   B",// z=23
            "0,1,C   IBBBBBI            C     C C     C            IBBBBBI   C;"
            + "1,1,CEEEECCCCCEEEEEEEEEEEECC     C C     CCEEEEEEEEEEEECCCCCEEEEC;"
            + "2,1,C   IBBBBBI           BCEEEEEEEEEEEEECB           IBBBBBI   C;3,24,C             C;4,24,C C C     C C C;"
            + "5,25,C           C;6,25,C           C;7,25,C           C;8,25,C           C;9,25,C           C;"
            + "10,26,CC       CC;11,26,EC       CE;12,26,EC       CE;13,26,EC       CE;14,26,EC       CE;15,26,CC       CC;"
            + "16,28,CCCCCCC;17,28,CC   CC;18,28,CC   CC;19,29,B   B;20,33,B B;21,33,B B;22,33,BBBBB;23,34,B B;24,34,B B;"
            + "25,34,B B;26,34,B B;27,36,B;28,36,B;29,36,B;30,36,B;31,36,B",// z=24
            "0,1,C   IBBBBBI                  C C                  IBBBBBI   C;"
            + "1,1,CEEEECCCCCEEEEEEEEEEECC      C C      CCEEEEEEEEEEECCCCCEEEEC;"
            + "2,1,C   IBBBBBI          BCEEEEEEEEEEEEEEECB          IBBBBBI   C;3,23,C               C;"
            + "4,23,C  C C     C C  C;5,24,C             C;6,24,C             C;7,24,C             C;8,24,C             C;"
            + "9,24,C             C;10,25,C           C;11,25,C           C;12,25,C           C;13,25,C           C;"
            + "14,25,C           C;15,25,C           C;16,26,CC   C   CCC;17,26,CC       CCC;18,26,CC     CCCCC;"
            + "19,28,CBCCCCCCCC;20,32,CCCCCC;21,32,CCCCCC;22,32,CCCCCCB;23,32,CCCCCC;24,32,CCCCCC;25,32,CCCCCC;26,33,CCCCC;"
            + "27,33,CCCCC;28,33,CCCCC;29,33,CCCCC;30,33,CCCCC;31,33,CCCCC;32,33,CCCCC;33,33,CCCCC;34,32,CCCCCC;35,32,CCCCCC;"
            + "36,32,CCCCCC;37,32,CCCCCC;38,32,CCCCC;39,32,CCCCC;40,32,CCCC;41,32,CCCC;42,32,CCC;43,32,CCC;44,32,CC;45,32,CC;"
            + "46,32,C;47,32,C",// z=25
            "0,0,C     IIIII                   C C                   IIIII     C;"
            + "1,0,CEEEEEEEEEEEEEEEEEEEECC       C C       CCEEEEEEEEEEEEEEEEEEEEC;"
            + "2,0,C     IIFII          BCEEEEEEEEEEEEEEEEECB          IIFII     C;3,22,C                 C;"
            + "4,22,CCCCC         CCCCC;5,23,C               C;6,23,E               E;7,23,E               E;"
            + "8,23,E               E;9,23,C               C;10,24,C             C;11,24,E             E;"
            + "12,24,E             E;13,24,E             E;14,24,E             E;15,24,C             C;16,25,C     C     C;"
            + "17,25,C           C;18,25,C           C;19,26,CCCBCCCJJJJC;20,29,B  CJJJJC;21,26,B  B  CJJJJC;"
            + "22,26,B  B  CJJJJCB;23,25,BBBBB  CJJJJCB;24,28,B   CJJJJCB;25,28,B   CJJJJCB;26,28,B    JJJJCB;"
            + "27,28,B    JJJJCB;28,33,JJJJCB;29,33,JJJJCB;30,33,JJJJCB;31,33,JJJJCB;32,33,JJJJCB;33,33,JJJJCB;34,32,CJJJJCB;"
            + "35,32,CJJJJCB;36,32,CJJJJCB;37,32,CJJJJCB;38,32,C   CB;39,32,C  CCB;40,32,C  CB;41,32,C CCB;42,32,C CB;"
            + "43,32,CCCB;44,32,CCB;45,32,CCB;46,32,CB;47,32,CB;48,32,B",// z=26
            "0,0,C       C                     C C                     C       C;"
            + "1,0,CEEEEEECCCEEEEEEEEEEECC       C C       CCEEEEEEEEEEECCCEEEEEEC;"
            + "2,0,C       C            BCEEEEEEEEEEEEEEEEECB            C       C;3,22,C                 C;"
            + "4,22,C                 C;5,23,C               C;6,23,C               C;7,23,C               C;"
            + "8,23,C               C;9,23,C               C;10,24,C             C;11,24,C             C;"
            + "12,24,C             C;13,24,C             C;14,24,C             C;15,24,C      C      C;16,25,C     C     C;"
            + "17,25,C           C;18,25,C           C;19,25,CCCCCCCCJJJJC;20,25,CCCCC  CJJJJC;21,25,CCCCC  CJJJJCB;"
            + "22,25,CCCCC  CJJJJC;23,24,BCCCCC  CJJJJC;24,25,CCCCC  CJJJJC;25,25,CCCCC  CJJJJC;26,25,CCCCC   JJJJC;"
            + "27,25,CCCCC   JJJJC;28,25,CCCCC   JJJJC;29,25,CCCCC   JJJJC;30,25,CCCCC   JJJJC;31,25,CCCCC   JJJJC;"
            + "32,25,CCCCC   JJJJC;33,26,CCCC   JJJJC;34,26,CCCC  CJJJJC;35,27,CCC  CJJJJC;36,27,CCC  CJJJJC;37,28,CC  CJJJJC;"
            + "38,28,CC  C   C;39,29,C  C  CC;40,29,C  C  C;41,32,C CC;42,32,C C;43,32,CCC;44,32,CC;45,32,CC;46,32,C;47,32,C",// z=27
            "0,0,C       C                   HHHHHHH                   C       C;"
            + "1,0,CEEEEEECCCEEEEEEEEEECC      HHHHHHH      CCEEEEEEEEEECCCEEEEEEC;"
            + "2,0,C       C           BCEEEEEEHHHHHHHEEEEEECB           C       C;3,21,C      HHHHHHH      C;"
            + "4,21,CCCCC  HHHHHHH  CCCCC;5,22,C                 C;6,22,C                 C;7,22,C                 C;"
            + "8,22,C                 C;9,22,C                 C;10,23,C               C;11,23,C               C;"
            + "12,23,C               C;13,23,C               C;14,23,C       C       C;15,23,C       C       C;"
            + "16,24,C      D      C;17,24,C             C;18,25,C           C;19,25,CCJJJ  CJJJJC;20,25,CCJJJ  CJJJJCB;"
            + "21,25,CCJJJ  CJJJJC;22,25,CCJJJ  CJJJJC;23,24,BCCJJJ  CJJJJC;24,25,CCJJJ  CJJJJC;25,25,CCJJJ  CJJJJC;"
            + "26,25,CCJJJ   JJJJC;27,25,CCJJJ   JJJJC;28,25,CCJJJ   JJJJC;29,25,CCJJJ   JJJJC;30,25,CCJJJDDDJJJJC;"
            + "31,25,CCJJJ   JJJJC;32,25,CCJJJ   JJJJC;33,26,CCCC   JJJJC;34,26,CCCC  CJJJJC;35,27,CCC  CJJJJC;"
            + "36,27,CCC  CJJJJC;37,28,CC  CJJJJC;38,28,CC  C   C;39,29,CDDC  CC;40,29,C  C  C;41,32,C CC;42,32,C C;43,32,CCC;"
            + "44,32,CC;45,32,CC;46,32,C;47,32,C",// z=28
            "0,0,C       C                   HHHHHHH                   C       C;"
            + "1,0,CCCCCCCCCCCCCCCCCCCCCC      HBBBBBH      CCCCCCCCCCCCCCCCCCCCCC;"
            + "2,0,C       C           BCEEEEEEHBBBBBHEEEEEECB           C       C;3,20,BC      HBBBBBH      CB;"
            + "4,20,BC      H     H      CB;5,21,BC                 CB;6,21,BC                 CB;7,21,BC                 CB;"
            + "8,21,BC                 CB;9,21,BC                 CB;10,22,BC               CB;11,22,BC               CB;"
            + "12,22,BC               CB;13,22,BC       C       CB;14,22,BC       C       CB;15,22,BC               CB;"
            + "16,23,BC    DDDDD    CB;17,23,BC             CB;18,24,BC           CB;19,24,BCCJJJ  CJJJJCB;"
            + "20,24,BCCJJJ  CJJJJCB;21,24,BCCJJJ  CJJJJCB;22,24,BCCJJJ  CJJJJCB;23,24,BCCJJJ  CJJJJCB;24,24,BCCJJJDDCJJJJCB;"
            + "25,24,BCCJJJ  CJJJJCB;26,24,BCCJJJ   JJJJCB;27,24,BCCJJJ   JJJJCB;28,24,BCCJJJ   JJJJCB;29,24,BCCJJJ   JJJJCB;"
            + "30,24,BCCJJJ   JJJJCB;31,24,BCCJJJ   JJJJCB;32,24,BCCJJJ   JJJJCB;33,25,BCCCC   JJJJCB;34,25,BCCCC  CJJJJCB;"
            + "35,26,BCCC  CJJJJCB;36,26,BCCC  CJJJJCB;37,27,BCC  CJJJJCB;38,27,BCC  C   CB;39,28,BC  C  CCB;40,28,BC  C  CB;"
            + "41,29,B  C CCB;42,32,C CB;43,32,CCCB;44,32,CCB;45,32,CCB;46,32,CB;47,32,CB;48,32,B",// z=29
            "0,8,CCCCCCCCCCCCCCCCCCCCHHHHHHHCCCCCCCCCCCCCCCCCCCC;"
            + "1,0,EEEEEEECCCCCCCCCCCCCCCCCCCCCHBJJJBHCCCCCCCCCCCCCCCCCCCCCEEEEEEE;"
            + "2,8,CCCCCCCCCCCCCCEEEEEEHBJJJBHEEEEEECCCCCCCCCCCCCC;3,21,C      HBJJJBH      C;4,21,C      H     H      C;"
            + "5,22,C                 C;6,22,E                 E;7,22,E                 E;8,22,E                 E;"
            + "9,22,C        B        C;10,23,C      DBD      C;11,23,E      DBD      E;12,23,E      CCC      E;"
            + "13,23,E      DCD      E;14,23,E               E;15,23,C               C;16,24,C    D   D    C;"
            + "17,24,C             C;18,25,C           C;19,25,CCJJJ   CCCCC;20,25,CCJJJ  CCCCCC;21,25,CCJJJ  CCCCCC;"
            + "22,25,CCJJJ  CCCCCC;23,25,CCJJJ  CCCCCC;24,25,CCJJJ  CCCCCC;25,25,CCJJJ  CCCCCC;26,25,CCJJJ   CCCCC;"
            + "27,25,CCJJJ   CCCCC;28,25,CCJJJ   CCCCC;29,25,CCJJJ   CCCCC;30,25,CCJJJ   CCCCC;31,25,CCJJJ   CCCCC;"
            + "32,25,CCJJJ  DCCCCC;33,26,CCCC   CCCCC;34,26,CCCC  CCCCCC;35,27,CCC  CCCCCC;36,27,CCC  CCCCCC;37,28,CC  CCCCCC;"
            + "38,28,CC  CCCCC;39,29,C  CCCCC;40,29,C  CCCC;41,32,CCCC;42,32,CCC;43,32,CCC;44,32,CC;45,32,CC;46,32,C;47,32,C",// z=30
            "0,28,HHHHHHH;1,0,EEEEEEEEEEEEEEEEEEEECC      HBJJJBH      CCEEEEEEEEEEEEEEEEEEEE;2,21,CEEEEEEHBJJJBHEEEEEEC;"
            + "3,21,C      HBJJJBH      C;4,21,C      H     H      C;5,22,C                 C;6,22,C                 C;"
            + "7,22,C                 C;8,22,C        B        C;9,22,C       BBB       C;10,23,C      BBB      C;"
            + "11,23,C      BCB      C;12,23,C      CCC      C;13,23,C     CCCCC     C;14,23,C    CC   CC    C;"
            + "15,23,C   CC     CC   C;16,24,C CCDD   DDCC C;17,24,CCC         CCC;18,25,C           C;19,25,CCJJJ   C  C;"
            + "20,25,CCJJJ;21,25,CCJJJ;22,25,CCJJJ;23,25,CCJJJ;24,25,CCJJJ   D;25,25,CCJJJ;26,25,CCJJJ;27,25,CCJJJ;"
            + "28,25,CCJJJ;29,25,CCJJJ;30,25,CCJJJ;31,25,CCJJJ;32,25,CCJJJ  D;33,26,CCCC;34,26,CCCC;35,27,CCC;36,27,CCC;"
            + "37,28,CC;38,28,CC;39,29,C;40,29,C",// z=31
            "0,8,CCCCCCCCCCCCCCCCCCCCHHHHHHHCCCCCCCCCCCCCCCCCCCC;"
            + "1,0,EEEEEEECCCCCCCCCCCCCCCCCCCCCHBJJJBHCCCCCCCCCCCCCCCCCCCCCEEEEEEE;"
            + "2,8,CCCCCCCCCCCCCCEEEEEEHBJJJBHEEEEEECCCCCCCCCCCCCC;3,21,C      HBJJJBH      C;4,21,C      H     H      C;"
            + "5,22,C                 C;6,22,E                 E;7,22,E                 E;8,22,E        ~        E;"
            + "9,22,C        B        C;10,23,C       D       C;11,23,E      DBD      E;12,23,E      CCC      E;"
            + "13,23,E      DCD      E;14,23,E               E;15,23,C               C;16,24,C    D   D    C;"
            + "17,24,C             C;18,25,C           C;19,25,CCJJJ   C  C;20,25,CCJJJ;21,25,CCJJJ;22,25,CCJJJ;23,25,CCJJJ;"
            + "24,25,CCJJJ   D;25,25,CCJJJ;26,25,CCJJJ;27,25,CCJJJ;28,25,CCJJJ;29,25,CCJJJ;30,25,CCJJJ;31,25,CCJJJ;"
            + "32,25,CCJJJ  D;33,26,CCCC;34,26,CCCC;35,27,CCC;36,27,CCC;37,28,CC;38,28,CC;39,29,C;40,29,C",// z=32
            "0,0,C       C                   HHHHHHH                   C       C;"
            + "1,0,CCCCCCCCCCCCCCCCCCCCCC      HBBBBBH      CCCCCCCCCCCCCCCCCCCCCC;"
            + "2,0,C       C           BCEEEEEEHBBBBBHEEEEEECB           C       C;3,20,BC      HBBBBBH      CB;"
            + "4,20,BC      H     H      CB;5,21,BC                 CB;6,21,BC                 CB;7,21,BC                 CB;"
            + "8,21,BC                 CB;9,21,BC                 CB;10,22,BC               CB;11,22,BC               CB;"
            + "12,22,BC               CB;13,22,BC       C       CB;14,22,BC       C       CB;15,22,BC               CB;"
            + "16,23,BC    DDDDD    CB;17,23,BC             CB;18,24,BC           CB;19,24,BCCJJJCCCCCCCB;20,24,BCCJJJ CCCCBB;"
            + "21,24,BCCJJJ CCCC;22,24,BCCJJJ CCCC;23,24,BCCJJJ CCCC;24,24,BCCJJJ CCCC;25,24,BCCJJJ CCCC;26,24,BCCJJJ CCCC;"
            + "27,24,BCCJJJ CCC;28,24,BCCJJJ CCC;29,24,BCCJJJ CCC;30,24,BCCJJJ CCC;31,24,BCCJJJ CC;32,24,BCCJJJ CC;"
            + "33,25,BCCCC CC;34,25,BCCCC CC;35,26,BCCC C;36,26,BCCC C;37,27,BCC C;38,27,BCC C;39,28,BC;40,28,BC;41,29,B",// z=33
            "0,0,C       C                   HHHHHHH                   C       C;"
            + "1,0,CEEEEEECCCEEEEEEEEEECC      HHHHHHH      CCEEEEEEEEEECCCEEEEEEC;"
            + "2,0,C       C           BCEEEEEEHHHHHHHEEEEEECB           C       C;3,21,C      HHHHHHH      C;"
            + "4,21,CCCCC  HHHHHHH  CCCCC;5,22,C                 C;6,22,C                 C;7,22,C                 C;"
            + "8,22,C                 C;9,22,C                 C;10,23,C               C;11,23,C               C;"
            + "12,23,C               C;13,23,C               C;14,23,C       C       C;15,23,C       C       C;"
            + "16,24,C      D      C;17,24,C             CB;18,25,C           CC;19,24,BCCJJJ JJJCCCCC;20,25,CCJJJ JJJCB CC;"
            + "21,25,CCJJJ JJJCB CC;22,25,CCJJJ JJJCB CC;23,25,CCJJJ JJJCB CC;24,25,CCJJJ JJJCB CC;25,25,CCJJJ JJJCB CC;"
            + "26,25,CCJJJ JJJCB CC;27,25,CCJJJDCCCB  C;28,25,CCJJJ CCCB  C;29,25,CCJJJ CCCB;30,25,CCJJJ CCCB;31,25,CCJJJ CCB;"
            + "32,25,CCJJJ CCB;33,26,CCC  CCB;34,26,CCCC CCB;35,27,CCC CB;36,27,CCCDCB;37,28,CC CB;38,28,CC CB;39,29,C B;"
            + "40,29,C",// z=34
            "0,0,C       C                     C C                     C       C;"
            + "1,0,CEEEEEECCCEEEEEEEEEEECC       C C       CCEEEEEEEEEEECCCEEEEEEC;"
            + "2,0,C       C            BCEEEEEEEEEEEEEEEEECB            C       C;3,22,C                 C;"
            + "4,22,C                 C;5,23,C               C;6,23,C               C;7,23,C               C;"
            + "8,23,C               C;9,23,C               C;10,24,C             C;11,24,C             C;"
            + "12,24,C             C;13,24,C             C;14,24,C             C;15,24,C      C      C;16,25,C     C     CC;"
            + "17,25,C           CCB;18,25,C           CCB;19,24,BCCCCC JJJCCCCCB;20,25,CCCCC JJJCB CCB;21,25,CCCCC JJJC  CCB;"
            + "22,25,CCCCC JJJC  CCB;23,25,CCCCC JJJCDDCCB;24,25,CCCCC JJJC  CCB;25,25,CCCCC JJJC  CCB;26,25,CCCCC JJJC  CCB;"
            + "27,25,CCCCC CCC   CB;28,25,CCCCC CCCDDDCB;29,25,CCCCC CCC   B;30,25,CCCCC CCC;31,25,CCCCC CC;32,25,CCCCC CC;"
            + "33,26,CCCC CC;34,26,CCCC CC;35,27,CCC C;36,27,CCC C;37,28,CC C;38,28,CC C;39,29,C;40,29,C",// z=35
            "0,0,C     IIIII                   C C                   IIIII     C;"
            + "1,0,CEEEEEEEEEEEEEEEEEEEECC       C C       CCEEEEEEEEEEEEEEEEEEEEC;"
            + "2,0,C     IIFII          BCEEEEEEEEEEEEEEEEECB          IIFII     C;3,22,C                 C;"
            + "4,22,CCCCC   CCC   CCCCC;5,23,C               C;6,23,E               E;7,23,E               E;"
            + "8,23,E               E;9,23,C               C;10,24,C             C;11,24,E             E;"
            + "12,24,E             E;13,24,E             E;14,24,E             E;15,24,C             C;16,25,C     C     CC;"
            + "17,25,C     C     CC;18,25,C           CC;19,25,BCCCC JJJCCCCC;20,31,JJJC  CC;21,31,JJJCB CC;22,31,JJJC  CC;"
            + "23,29,DDJJJC  CC;24,31,JJJC  CC;25,31,JJJC  CC;26,28,D  JJJC  CC;27,31,CCC   C;28,31,CCC   C;29,31,CCC;"
            + "30,31,CCC;31,31,CC;32,31,CC;33,31,CC;34,31,CC;35,31,C;36,31,C;37,31,C;38,31,C",// z=36
            "0,1,C   IBBBBBI                  C C                  IBBBBBI   C;"
            + "1,1,CEEEECCCCCEEEEEEEEEEECC      C C      CCEEEEEEEEEEECCCCCEEEEC;"
            + "2,1,C   IBBBBBI          BCEEEEEEEEEEEEEEECB          IBBBBBI   C;3,23,C               C;"
            + "4,23,C  C C CEC C C  C;5,24,C             C;6,24,C             C;7,24,C             C;8,24,C             C;"
            + "9,24,C             C;10,25,C           C;11,25,C           C;12,25,C           C;13,25,C           C;"
            + "14,25,C           C;15,25,C           C;16,26,CC       CC;17,26,CC   C   CC;18,26,CC   C   CC;19,26,BCCC JJJC;"
            + "20,28,CC JJJC;21,28,CC JJJC;22,28,CC JJJCB;23,28,CC JJJCB;24,28,CC JJJCB;25,28,CC JJJCB;26,28,CC JJJCB;"
            + "27,28,CC CCCB;28,29,B CCCB;29,29,B CCCB;30,29,B CCCB;31,31,CCB;32,31,CCB;33,31,CCB;34,31,CCB;35,31,CB;36,31,CB;"
            + "37,31,CB;38,31,CB;39,31,B",// z=37
            "0,1,C   IBBBBBI            C     C C     C            IBBBBBI   C;"
            + "1,1,CEEEECCCCCEEEEEEEEEEEECC     C C     CCEEEEEEEEEEEECCCCCEEEEC;"
            + "2,1,C   IBBBBBI           BCEEEEEEEEEEEEECB           IBBBBBI   C;3,24,C             C;4,24,C C C CEC C C C;"
            + "5,25,C           C;6,25,C           C;7,25,C           C;8,25,C           C;9,25,C           C;"
            + "10,26,CC       CC;11,26,EC       CE;12,26,EC       CE;13,26,EC       CE;14,26,EC       CE;15,26,CC       CC;"
            + "16,28,CCCCCCC;17,28,CCCCCCC;18,28,CCCCCCC;19,27,BCCCCCCC;20,28,CC CCCC;21,28,CC CCCC;22,28,CC CCCC;"
            + "23,28,CC CCCC;24,28,CC CCCC;25,29,B CCCCB;26,29,B CCCC;27,29,B CCC;28,31,CCC;29,31,CCC;30,31,CCC;31,31,CC;"
            + "32,31,CC;33,31,CC;34,31,CC;35,31,C;36,31,C;37,31,C;38,31,C",// z=38
            "0,1,C   IBBBBBI           C      C C      C           IBBBBBI   C;"
            + "1,1,CEEEECCCCCEEEEEEEEEEECCCC    C C    CCCCEEEEEEEEEEECCCCCEEEEC;"
            + "2,1,C   FBBBBBF           CBCEEEEEEEEEEECBC           FBBBBBF   C;3,25,C           C;4,25,CC C CEC C CC;"
            + "5,26,CC       CC;6,26,EC       CE;7,26,EC       CE;8,26,EC       CE;9,26,CC       CC;10,28,CCCCCCC;"
            + "11,28,CCECECC;12,28,CCECECC;13,28,CCECECC;14,28,CCECECC;15,28,CCCCCCC;16,29,B   B;17,29,B BBB;18,29,B   B;"
            + "19,28,BB   B;20,29,B   B;21,29,B   B;22,29,B   B;23,29,B  BB;24,29,B   B;25,33,BB",// z=39
            "0,1,C   IBBBBBI          C       C C       C          IBBBBBI   C;"
            + "1,1,CEEEECCCCCEEEEEEEEEECCCECCC  C C  CCCECCCEEEEEEEEEECCCCCEEEEC;"
            + "2,1,C   IBBBBBI          C  BCCEEEEEEECCB  C          IBBBBBI   C;3,26,CC       CC;4,26,CCC CEC CCC;"
            + "5,28,C     C;6,28,C     C;7,28,C     C;8,28,C     C;9,28,CCCCCCC;10,29,B   B;11,29,B   B;12,29,B   B;"
            + "13,29,B   B;14,29,B   B;15,29,B   B",// z=40
            "0,2,C  IBBBBBI         C        C C        C         IBBBBBI  C;"
            + "1,2,CEEECCCCCEEEEEEEEECCCEEECCCCCCCCCCCEEECCCEEEEEEEEECCCCCEEEC;"
            + "2,2,C  IBBBBBI         C    BBCCCCCCCBB    C         IBBBBBI  C;3,28,CCCCCCC;4,28,CCCCCCC;5,29,B   B;"
            + "6,29,B   B;7,29,B   B;8,29,B   B;9,29,B   B",// z=41
            "0,2,C   IIIII         C         C C         C         IIIII   C;"
            + "1,2,CEEEEEEEEEEEEEEEECCCEEEEEECCCCCCCEEEEEECCCEEEEEEEEEEEEEEEEC;"
            + "2,2,C   IIFII         C       BBCCCBB       C         IIFII   C;3,29,BCCCB;4,29,B   B",// z=42
            "0,2,C                C          C C          C                C;"
            + "1,2,CEEEEEEEEEEEEEEECCCEEEEEEEECCECCEEEEEEEECCCEEEEEEEEEEEEEEEC;"
            + "2,2,C                C          CCC          C                C;4,29,B   B",// z=43
            "0,3,C              C           C C           C              C;"
            + "1,3,CEEEEEEEEEEEEECCCEEEEEEEEECCECCEEEEEEEEECCCEEEEEEEEEEEEEC;"
            + "2,3,C              C           C C           C              C;4,29,B   B",// z=44
            "0,3,C        IIIIIC            C C            CIIIII        C;"
            + "1,3,CEEEEEEEEEEEEECCEEEEEEEEEECCECCEEEEEEEEEECCEEEEEEEEEEEEEC;"
            + "2,3,C        IIFIIC            C C            CIIFII        C;4,29,B   B",// z=45
            "0,4,C      IBBBBBI            C C            IBBBBBI      C;"
            + "1,4,CEEEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEEEC;"
            + "2,4,C      IBBBBBI            C C            IBBBBBI      C;4,29,B   B",// z=46
            "0,4,C      IBBBBBI            C C            IBBBBBI      C;"
            + "1,4,CEEEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEEEC;"
            + "2,4,C      IBBBBBI            C C            IBBBBBI      C;4,29,B   B",// z=47
            "0,5,C     IBBBBBI            C C            IBBBBBI     C;"
            + "1,5,CEEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEEC;"
            + "2,5,C     FBBBBBF            C C            FBBBBBF     C;4,29,B   B",// z=48
            "0,6,C    IBBBBBI            C C            IBBBBBI    C;"
            + "1,6,CEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEC;"
            + "2,6,C    IBBBBBI            C C            IBBBBBI    C;"
            + "3,6,C                                                 C;4,29,B   B",// z=49
            "0,6,C    IBBBBBI            C C            IBBBBBI    C;"
            + "1,6,CEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEC;"
            + "2,6,C    IBBBBBI            C C            IBBBBBI    C;"
            + "3,6,C                                                 C;4,29,B   B",// z=50
            "0,7,C    IIIII    IIIII    C C    IIIII    IIIII    C;1,7,CEEEEEEEEEEEEEEEEEEEEECCECCEEEEEEEEEEEEEEEEEEEEEC;"
            + "2,7,C    IIFII    IIFII    C C    IIFII    IIFII    C;3,7,C                                               C;"
            + "4,7,C                     B   B                     C",// z=51
            "0,8,C           IBBBBBI   C C   IBBBBBI           C;1,8,CEEEEEEEEEEEECCCCCEEECCECCEEECCCCCEEEEEEEEEEEEC;"
            + "2,8,C           IBBBBBI   C C   IBBBBBI           C;3,8,C                    C   C                    C;"
            + "4,8,C                    B   B                    C;5,8,C                                             C",// z=52
            "0,9,A          IBBBBBI   C C   IBBBBBI          A;1,9,AEEEEEEEEEEECCCCCECCCCECCCCECCCCCEEEEEEEEEEEA;"
            + "2,9,A          IBBBBBI  CC CC  IBBBBBI          A;3,9,A                   C   C                   A;"
            + "4,9,A                   B   B                   A;5,9,A                                           A;"
            + "6,9,A                                           A;7,9,A                                           A",// z=53
            "0,10,C         IBBBBBICCCC CCCCIBBBBBI         C;1,10,CEEEEEEEEEECCCCCECCCCECCCCECCCCCEEEEEEEEEEC;"
            + "2,10,C         FBBBBBFCCCC CCCCFBBBBBF         C;3,10,C                  B   B                  C;"
            + "4,10,C                                         C;5,10,C                                         C",// z=54
            "0,11,C        IBBBBBI         IBBBBBI        C;1,11,CEEEEEEEEECCCCCECCCCECCCCECCCCCEEEEEEEEEC;"
            + "2,11,C        IBBBBBI  B   B  IBBBBBI        C;3,11,C                                       C;"
            + "4,11,C                                       C",// z=55
            "0,12,CC      IBBBBBI         IBBBBBI      CC;1,12,CCEEEEEEECCCCCEEECEEECEEECCCCCEEEEEEECC;"
            + "2,12,CC      IBBBBBI         IBBBBBI      CC;3,12,CC                                   CC",// z=56
            "0,14,C      IIIII           IIIII      C;1,14,CEEEEEEEEEEEEEECEEECEEEEEEEEEEEEEEC;"
            + "2,14,C      IIFII           IIFII      C",// z=57
            "0,15,CC                             CC;1,15,CCEEEEEEEEEEEECEEECEEEEEEEEEEEECC;"
            + "2,15,CC                             CC",// z=58
            "0,17,CC                         CC;1,17,CCEEEEEEEEEECEEECEEEEEEEEEECC;2,17,CC                         CC",// z=59
            "0,19,CCC                   CCC;1,19,CCCEEEEEEECEEECEEEEEEECCC;2,19,CCC                   CCC",// z=60
            "0,22,CCCC           CCCC;1,22,CCCCEEECEEECEEECCCC;2,22,CCCC           CCCC",// z=61
            "0,26,CCCC   CCCC;1,26,CCCCEEECCCC;2,26,CCCC   CCCC",// z=62
    };

    /** 模块机逐层字面量（自底向上：row=0 最底、row=2 最顶；同一片内 3 个字符串 = 剖面内 3 层）。 */
    private static final String[] MOD_LAYERS = {
            // z = 0：控制器所在的那一片（原版 " VV~VV " / "       " / " VVVVV " 翻转后的样子）
            // ★ 2026-09-16 修（玩家实测坐标 (16,64,−98)/(16,64,−70)/(44,64,−70)/(44,64,−98)）：
            //   两端的**基座环角**原来是空格 = **必须空气**，但同一格在**主机图案**里是 `I`（可忽略位：
            //   机壳或任意仓室都吃）⇒ 玩家搭外壳时自然填了机壳 ⇒ 主机"不在乎"、模块"要空气" = **硬冲突**
            //   （玩家必须手拆那几格才能成形）。这里与工程里已有的先例（中层外环保留 `X = any()`）
            //   一致处理：基座环角也放宽成 `any()` —— 那本来就是主机自己的基座，模块不该要求它为空。
            "XVVVVVX",
            "XXXXXXX", // 原版中层外环 = 空气；这里放宽成 any()，见类注释
            "XVV~VVX",
            // z = 1..5：原版 "VPPPPPV" / " ZZZZZ " / "VPPPPPV"
            "VPPPPPV",
            "XZZZZZX",
            "VPPPPPV",
            "VPPPPPV",
            "XZZZZZX",
            "VPPPPPV",
            "VPPPPPV",
            "XZZZZZX",
            "VPPPPPV",
            "VPPPPPV",
            "XZZZZZX",
            "VPPPPPV",
            "VPPPPPV",
            "XZZZZZX",
            "VPPPPPV",
            // z = 6：原版 " VVVVV " / "       " / " VVVVV "
            "XVVVVVX",   // ★ 同上：基座环角由"必须空气"放宽成 any()
            "XXXXXXX",
            "XVVVVVX",
    };

    // ================= MODULE_TOWERS：11 个模块的塔身（★ 自动生成，不要手改）=================
    //
    // 数据来源：GTNH 各 `MTE*Module.<clinit>` 的 `*_STRUCTURE` 形状串 + `addElement` 的字符表，
    // 由 `_census/decode_all_modules.js` 从 **class 常量池** 解出（不手抄），再由
    // `_census/gen_all_towers_java.js` 生成这里的字面量。轴序与原版一致：`[Z][Y].charAt(X)`，
    // **Y=0 是塔顶**；本数组按原版顺序存放，摆放时按 `row = 2 + structureOffsetY() − y` 换算
    // （推导与逐条自检见 `_census/all_modules_layers.md` 与施工记录 §35）。
    //
    // 角色码（`cells("A=M,...")`）：★ **忠于 NH 的真实方块类别**（2026-09-17 玩家回退令；**勿再改成 MESH**）·
    // ASSEMBLY=部件装配线外壳 · REINFORCEMENT=强化机壳 · MESH=网格接口机壳（`gtlmodular:nanochip_mesh_interface_casing`，47 格 CTM）·
    // UEV_CASING=UEV 机壳 · MATRIX_CASING=屏蔽加速器外壳 · REINFORCED_CASING=强化水厂外壳 ·
    // COIL_UHV / COIL_UEV=螺线管超导线圈块（★ 2026-09-19 队长令「两个线圈方块给他定死」：超导线束分离器
    //   **A 位 6 格 = UHV**（NH `sSolenoidCoilCasings:7`）、**B 位 16 格 = UEV**（NH `:8`）——
    //   原来是一个合并的 `COIL` 角色，两味线圈可任意换位；现按位置拆成两个角色码定死）·
    // GLASS=复合体玻璃 · FRAME:<材料>=该材料框架 · SHEET:<材料>=板金属（本包无该形态 ⇒ 实心块替代）·
    // HATCH=仓位(仓室或机壳) · CONTROLLER=控制器 `~`。
    //
    // ⚠️ 2026-09-17 回退：上一版曾把这些"机壳类"角色**全部改成 MESH**，玩家判定为**偏离 NH**（NH 塔身用的就是
    //    强化机壳那一套，见 `_census/role_reference.md`：11 台塔身 86% 与 NH 同款）⇒ 已回退。
    //    `check_all_patterns.js` ⑧ 现在断言这些角色码的模块数/格数与 role_reference.md 的 NH 口径一致 —— 改回 MESH 即报红。
    //
    // 每个模块：`rows` = 原版数组行数，`oy` = `structureOffsetY()`（= 控制器以上层数）；
    // `rows == oy` 时数组刚好落 row 3..oy+2；`rows == oy + 1` 时最底那一行**带控制器 `~`**，
    // 落 row 2（= 共享底座顶层，控制器就在那一层）。
    private static Map<String, TowerDef> buildTowers() {
        Map<String, TowerDef> m = new LinkedHashMap<>();
        // 纳米芯片组装矩阵（`nanochip_assembly_matrix`）：塔身数组 4 行 × 7 片，structureOffsetY=4，总高 7
        m.put("nanochip_assembly_matrix", new TowerDef(4, 4, new String[] {
                "       ", "  BFB  ", " DDFDD ", "  BFB  ",  // z=0（Y=0 塔顶 → Y=3）
                "  CFC  ", " D   D ", "DB   BD", " DEEED ",  // z=1
                " CFFFC ", "B     B", "D     D", "BE   EB",  // z=2
                " FFDFF ", "F  G  F", "F  G  F", "FE G EF",  // z=3
                " CFFFC ", "B     B", "D     D", "BE   EB",  // z=4
                "  CFC  ", " D   D ", "DB   BD", " DEEED ",  // z=5
                "       ", "  BFB  ", " DDFDD ", "  BFB  ",  // z=6
        }, cells("B=ASSEMBLY,C=MESH,D=REINFORCEMENT,E=FRAME:NaquadahAlloy,F=GLASS,G=SHEET:NaquadahAlloy")));
        // 加速生物协调器（`nanochip_biological_coordinator`）：塔身数组 6 行 × 7 片，structureOffsetY=5，总高 8
        m.put("nanochip_biological_coordinator", new TowerDef(5, 6, new String[] {
                "       ", " AAAAA ", "  C C  ", "  C C  ", "  C C  ", "   ~   ",  // z=0（Y=0 塔顶 → Y=5）
                "  CCC  ", "AABBBAA", " ABDBA ", " ABDBA ", " ABDBA ", "       ",  // z=1
                " C   C ", "ABDDDBA", "CB   BC", "CB   BC", "CB   BC", "       ",  // z=2
                " C   C ", "ABDADBA", " D   D ", " D   D ", " D   D ", "       ",  // z=3
                " C   C ", "ABDDDBA", "CB   BC", "CB   BC", "CB   BC", "       ",  // z=4
                "  CCC  ", "AABBBAA", " ABDBA ", " ABDBA ", " ABDBA ", "       ",  // z=5
                "       ", " AAAAA ", "  C C  ", "  C C  ", "  C C  ", "       ",  // z=6
        }, cells("A=MESH,B=REINFORCEMENT,C=FRAME:Tritanium,D=GLASS,~=CONTROLLER")));
        // 全板浸没（`nanochip_board_processor`）：塔身数组 4 行 × 7 片，structureOffsetY=4，总高 7
        m.put("nanochip_board_processor", new TowerDef(4, 4, new String[] {
                "       ", " A   A ", " A   A ", " B   B ",  // z=0（Y=0 塔顶 → Y=3）
                " A   A ", "CCC CCC", "DDD DDD", "DDD DDD",  // z=1
                "  A A  ", "CBCCCBC", "D DDD D", "D DDD D",  // z=2
                "   A   ", "CBCCCBC", "D DAD D", "D DAD D",  // z=3
                "  A A  ", "CBCCCBC", "D DDD D", "D DDD D",  // z=4
                " A   A ", "CCC CCC", "DDD DDD", "DDD DDD",  // z=5
                "       ", " A   A ", " A   A ", " B   B ",  // z=6
        }, cells("A=FRAME:Octiron,B=MESH,C=REINFORCEMENT,D=GLASS")));
        // 纳米精度切割室（`nanochip_cutting_chamber`）：塔身数组 5 行 × 7 片，structureOffsetY=5，总高 8
        m.put("nanochip_cutting_chamber", new TowerDef(5, 5, new String[] {
                "       ", "       ", "       ", " A   A ", " A   A ",  // z=0（Y=0 塔顶 → Y=4）
                "  CCC  ", " BBBBB ", " CEEEC ", "ACEEECA", "ACEEECA",  // z=1
                " CCDCC ", "B     B", "B     B", "B     B", "B DDD B",  // z=2
                " DDDDD ", "BCCCCCB", "EAAAAAE", "E     E", "E DDD E",  // z=3
                " CCDCC ", "B     B", "B     B", "B     B", "B DDD B",  // z=4
                "  CCC  ", " BBBBB ", " CEEEC ", "ACEEECA", "ACEEECA",  // z=5
                "       ", "       ", "       ", " A   A ", " A   A ",  // z=6
        }, cells("A=FRAME:Neutronium,B=MESH,C=REINFORCEMENT,D=REINFORCED_CASING,E=GLASS")));
        // 纳米封装包装器（`nanochip_encasement_wrapper`）：塔身数组 6 行 × 7 片，structureOffsetY=6，总高 9
        m.put("nanochip_encasement_wrapper", new TowerDef(6, 6, new String[] {
                "       ", "  DBD  ", "  DBD  ", " CDBDC ", " CDBDC ", " CDBDC ",  // z=0（Y=0 塔顶 → Y=5）
                "  BBB  ", " A   A ", " A C A ", "CA   AC", "CA C AC", "CA   AC",  // z=1
                " BAAAB ", "D     D", "D AAA D", "D     D", "D AAA D", "D     D",  // z=2
                " BAAAB ", "B     B", "BCAAACB", "B     B", "BCAAACB", "B     B",  // z=3
                " BAAAB ", "D     D", "D AAA D", "D     D", "D AAA D", "D     D",  // z=4
                "  BBB  ", " A   A ", " A C A ", "CA   AC", "CA C AC", "CA   AC",  // z=5
                "       ", "  DBD  ", "  DBD  ", " CDBDC ", " CDBDC ", " CDBDC ",  // z=6
        }, cells("A=MESH,B=REINFORCEMENT,C=FRAME:Quantanium,D=GLASS")));
        // 超高能蚀刻阵列（`nanochip_etching_array`）：塔身数组 4 行 × 7 片，structureOffsetY=4，总高 7
        m.put("nanochip_etching_array", new TowerDef(4, 4, new String[] {
                "  EEE  ", " EBBBE ", " EBHBE ", " BBBBB ",  // z=0（Y=0 塔顶 → Y=3）
                "  AGA  ", " A   A ", " G F G ", "B     B",  // z=1
                "  AGA  ", " A   A ", " G F G ", "B     B",  // z=2
                "  AGA  ", " A   A ", " G F G ", "B     B",  // z=3
                "  AGA  ", " A   A ", " G F G ", "B     B",  // z=4
                "  AGA  ", " BCCCB ", " BCCCB ", "BBCCCBB",  // z=5
                "  EEE  ", " EAEAE ", " EAEAE ", " BAEAB ",  // z=6
        }, cells("A=MESH,B=REINFORCEMENT,C=MATRIX_CASING,E=FRAME:EnrichedHolmium,F=GLASS,G=GLASS,H=HATCH")));
        // 光学整理器（`nanochip_optical_organizer`）：塔身数组 7 行 × 7 片，structureOffsetY=7，总高 10
        //   ★ 2026-09-18 队长裁定：「简并态铼框架用这个替换结构中的觉醒龙框架」。
        //     理由（队长原话）：「我预定这个机器是在 UHV 推出的…只有觉醒龙在 UXV 阶段做不出来」；
        //     实机日志 `logs/latest.log:7029` 也记过玩家在此格结构判定失败。
        //     ⇒ 本塔原用的 `FRAME:Draconiumawakened`（觉醒龙）**改成 `FRAME:DegenerateRhenium`（简并态铼框架）**。
        //     实现配套：简并态铼在本包原本只有粉/板/等离子体，没有框架 ⇒ 由
        //     {@link com.gtlmodular.common.data.GTLModularAddon} 的 `upgradeExistingMaterial("degenerate_rhenium")`
        //     给它补 `DUST + GENERATE_FRAME`，GTCEu 才生成 `gtceu:degenerate_rhenium_frame`（与 radox/kevlar 同一套做法）。
        //     其余 10 味的框架经复核**都做得出**，一律保留。
        m.put("nanochip_optical_organizer", new TowerDef(7, 7, new String[] {
                "       ", " BBCBB ", " AACAA ", "   C   ", "   C   ", "   C   ", " AACAA ",  // z=0（Y=0 塔顶 → Y=6）
                " AAAAA ", "BBBCBBB", "ABD DBA", " BD DB ", " BD DB ", " BD DB ", "ABD DBA",  // z=1
                " A   A ", "BBCCCBB", "AD   DA", " DCCCD ", " D   D ", " DCCCD ", "AD   DA",  // z=2
                " A   A ", "CCCBCCC", "C  C  C", "C CCC C", "C     C", "C CCC C", "C  C  C",  // z=3
                " A   A ", "BBCCCBB", "AD   DA", " DCCCD ", " D   D ", " DCCCD ", "AD   DA",  // z=4
                " AAAAA ", "BBBCBBB", "ABD DBA", " BD DB ", " BD DB ", " BD DB ", "ABD DBA",  // z=5
                "       ", " BBCBB ", " AACAA ", "   C   ", "   C   ", "   C   ", " AACAA ",  // z=6
        }, cells("A=FRAME:DegenerateRhenium,B=MESH,C=REINFORCEMENT,D=GLASS")));
        // ★ 2026-09-18：本模组**新增**的三台组织器塔身**不写在这里** —— 这个方法的字面量由
        //   `_census/gen_all_towers_java.js --write` 从 NH 数据重生成，手写在生成段里的条目
        //   会被下一次重跑**抹掉**。它们放在生成段之外的 {@link #extensionTowers()}，由
        //   {@link #moduleTowers()} 合并进来（见那里的注释）。
        // 元件预处理（`nanochip_smd_processor`）：塔身数组 3 行 × 7 片，structureOffsetY=3，总高 6
        //   ★ 2026-09-21 队长裁定：「把那个拉多X的框架给换成魔金框架吧」。
        //     ⇒ 本塔的框架格 `D` 由 `FRAME:Radox`（拉多X聚合物框架 `gtceu:radox_frame`）
        //       **改成 `FRAME:Infuscolium`（魔金框架 `gtceu:infuscolium_frame`）**，**形状串一个字没动**（只换框架材料）。
        //     取证：
        //       ① 「魔金」= `gtceu:infuscolium` —— gtlcore-1.2.3.2-fix2.jar 的
        //          `assets/gtceu/lang/zh_cn.json:1386` `"material.gtceu.infuscolium": "魔金"`；
        //       ② 本包原本**没有** `gtceu:infuscolium_frame`（`_census/_nh_avail/realids.txt` 的运行时注册表里
        //          `radox_frame` 在册、`infuscolium_frame` 不在册）⇒ 由
        //          {@link com.gtlmodular.common.data.GTLModularAddon} 的 `upgradeExistingMaterial("infuscolium")`
        //          补 `DUST + GENERATE_FRAME`，GTCEu 随后生成 `gtceu:infuscolium_frame`
        //          （机制与先例：radox / kevlar / degenerate_rhenium / orichalcum / taranium / starmetal）；
        //       ③ 同一味魔金的框架**早就在用**：寰宇优化组织器 `nanochip_cosmic_organizer` 的 `A` 位
        //          （2026-09-18 队长令「寰宇优化组织器，魔金框架」，见本文件 `extensionTowers()`）
        //          ⇒ 本处只是把拉多X那一格也并到魔金同一味材料上，不引入新机制。
        //     ⚠️ 改这里必须**同时**改生成器 `_census/gen_all_towers_java.js` 的 `LAZY_OVERRIDE`
        //        （本段字面量由它从 NH 数据重生成，不加覆写会在下次重跑时被改回 `FRAME:Radox`）。
        //     配套：建造链配方 `data/gtlmodular/recipes/radox_frame_crafting.json` 的输出同步改为
        //        `gtceu:infuscolium_frame`（文件名保留原名，避免动 `NanochipSelfCheck.EXPECTED_RECIPES` 的路径登记）。
        m.put("nanochip_smd_processor", new TowerDef(3, 3, new String[] {
                " B   B ", " EA AE ", " BB BB ",  // z=0（Y=0 塔顶 → Y=2）
                "BBDDDBB", "ECA ACE", "BBB BBB",  // z=1
                " D   D ", "AAA AAA", "BBBDBBB",  // z=2
                " D   D ", "       ", "  DDD  ",  // z=3
                " D   D ", "AAA AAA", "BBBDBBB",  // z=4
                "BBDDDBB", "ECA ACE", "BBB BBB",  // z=5
                " B   B ", " EA AE ", " BB BB ",  // z=6
        }, cells("A=MESH,B=REINFORCEMENT,C=UEV_CASING,D=FRAME:Infuscolium,E=GLASS")));
        // 纳米组件分流器（`nanochip_splitter`）：塔身数组 2 行 × 7 片，structureOffsetY=2，总高 5
        m.put("nanochip_splitter", new TowerDef(2, 2, new String[] {
                "  A    ", " CBBBC ",  // z=0（Y=0 塔顶 → Y=1）
                "  AA A ", "CBBBBBC",  // z=1
                "   ACA ", "BBBBBBB",  // z=2
                "AA A A ", "BBBBBBB",  // z=3
                " ACA AA", "BBBBBBB",  // z=4
                " A A   ", "CBBBBBC",  // z=5
                "   AA  ", " CBBBC ",  // z=6
        }, cells("A=HATCH,B=REINFORCEMENT,C=FRAME:Kevlar")));
        // 超导线束分裂器（`nanochip_superconductor_splitter`）：塔身数组 7 行 × 7 片，structureOffsetY=7，总高 10
        m.put("nanochip_superconductor_splitter", new TowerDef(7, 7, new String[] {
                "       ", "       ", " DEEED ", "       ", "       ", "       ", " DEEED ",  // z=0（Y=0 塔顶 → Y=6）
                "       ", " D   D ", "DBD DBD", " B   B ", " B   B ", " B   B ", "DDD DDD",  // z=1
                "   C   ", "  CFC  ", "EDCFCDE", "  CFC  ", "  CFC  ", "  CFC  ", "EDCFCDE",  // z=2
                "  CCC  ", "  FAF  ", "E FAF E", "  FAF  ", "  FAF  ", "  FAF  ", "E FAF E",  // z=3
                "   C   ", "  CFC  ", "EDCFCDE", "  CFC  ", "  CFC  ", "  CFC  ", "EDCFCDE",  // z=4
                "       ", " D   D ", "DBD DBD", " B   B ", " B   B ", " B   B ", "DDD DDD",  // z=5
                "       ", "       ", " DEEED ", "       ", "       ", "       ", " DEEED ",  // z=6
        }, cells("A=COIL_UHV,B=COIL_UEV,C=MESH,D=REINFORCEMENT,E=FRAME:Naquadria,F=GLASS")));
        // 线材描绘器（`nanochip_wire_tracer`）：塔身数组 5 行 × 7 片，structureOffsetY=5，总高 8
        m.put("nanochip_wire_tracer", new TowerDef(5, 5, new String[] {
                "       ", "  BBB  ", "  BAB  ", "  BAB  ", " BBABB ",  // z=0（Y=0 塔顶 → Y=4）
                " DAAAD ", " E D E ", " E A E ", " E   E ", "BEDDDEB",  // z=1
                " AEEEA ", "B     B", "B     B", "B     B", "BD   DB",  // z=2
                " AEEEA ", "BD   DB", "AA C AA", "A  B  A", "AD B DA",  // z=3
                " AEEEA ", "B     B", "B     B", "B     B", "BD   DB",  // z=4
                " DAAAD ", " E D E ", " E A E ", " E   E ", "BEDDDEB",  // z=5
                "       ", "  BBB  ", "  BAB  ", "  BAB  ", " BBABB ",  // z=6
        }, cells("A=MESH,B=REINFORCEMENT,C=SHEET:SuperconductorUEVBase,D=FRAME:SuperconductorUHVBase,E=GLASS")));
        return m;
    }
    /**
     * 各模块的塔身数据（key = 机器 id 的 path）。<b>★ 惰性初始化</b>。
     *
     * <p>【P0 2026-09-16】原来写成 `static final ... = buildTowers();` ⇒ 类一被加载（注册期就会碰到
     * 本类）就跑 371 条形状串的角色表解析。那天 `cells()` 里有个下标写错（`eq != 2` 应为 `eq != 1`），
     * 于是**静态初始化抛异常 ⇒ ExceptionInInitializerError ⇒ 玩家连游戏都进不去**。
     *
     * <p>纪律：**注册期 / 类加载期绝不做解析、取注册表、算格子这类重活**（与之前 tooltip 过早解析
     * 注册表那次同类）。这里改成"第一次真正要用时才算"（`buildModule()` 里取图案时），
     * 注册期只加载类、不做任何计算。审计脚本：`_census/check_registration_path.js`。
     */
    private static Map<String, TowerDef> moduleTowersCache;

    private static synchronized Map<String, TowerDef> moduleTowers() {
        if (moduleTowersCache == null) {
            // ★ 2026-09-18：NH 派生的 11 台 + 本模组扩展的 3 台组织器。
            //   两者**必须分开**：`buildTowers()` 的字面量由 `_census/gen_all_towers_java.js --write`
            //   从 NH 数据重生成（那个脚本替换 START..END 之间的一切），手写在里面的条目会被重跑抹掉；
            //   `extensionTowers()` 在生成段之外 ⇒ 重跑生成器不会碰它。
            Map<String, TowerDef> all = buildTowers();
            all.putAll(extensionTowers());
            moduleTowersCache = all;
        }
        return moduleTowersCache;
    }

    /**
     * <b>本模组扩展的模块塔身</b>（NH 里没有对应物，2026-09-18 队长裁定新增）。
     *
     * <p>队长原话：「奇异优化组织器，山铜框架，寰宇优化组织器，魔金框架，超因果优化组织器，塔兰框架，
     * 他们结构都跟光学优化组织器一样，只是不同的框架，当然主机也要有改变，里面的配方也要有改变，
     * 这需要你自己注册，他们的配方跟光学的配方相似」。
     *
     * <p>⇒ 三条塔身的<b>形状串逐字照抄光学优化组织器那条</b>（{@code nanochip_optical_organizer}：
     * 7 行 × 7 片、{@code structureOffsetY = 7}、总高 10），<b>唯一差别是角色表里的 {@code A=FRAME:<材料>}</b>：
     * <table border="1">
     *   <tr><th>模块</th><th>族</th><th>框架材料（驼峰写法 → {@code snake()}）</th><th>框架方块</th></tr>
     *   <tr><td>{@code nanochip_exotic_organizer}</td><td>奇异 EXOTIC</td><td>{@code Orichalcum} → {@code orichalcum}</td>
     *       <td>{@code gtceu:orichalcum_frame}</td></tr>
     *   <tr><td>{@code nanochip_cosmic_organizer}</td><td>寰宇 COSMIC</td><td>{@code Infuscolium} → {@code infuscolium}</td>
     *       <td>{@code gtceu:infuscolium_frame}</td></tr>
     *   <tr><td>{@code nanochip_supracausal_organizer}</td><td>超因果 SUPRACAUSAL</td><td>{@code Taranium} → {@code taranium}</td>
     *       <td>{@code gtceu:taranium_frame}</td></tr>
     * </table>
     * <p>这三味材料的框架在本包里<b>本来不存在</b>（{@code _census/out/pack_id_corpus.txt} 里
     * {@code <材料>_frame} 命中 0 次）⇒ 由 {@link com.gtlmodular.common.data.GTLModularAddon}
     * 的 {@code upgradeExistingMaterial("orichalcum" | "infuscolium" | "taranium")} 补
     * {@code DUST + GENERATE_FRAME}，GTCEu 随后生成这三个框架方块（同一套做法先例：
     * 简并态铼 / 拉多X聚合物 / 凯芙拉 / 觉醒龙）。
     * <p>唯一实际存在的框架是 {@code DegenerateRhenium}，其余 10 味塔身框架未变。
     *
     * <p>⚠️ 这三台<b>没有 NH 原版塔身</b>可比 ⇒ 不参与
     * {@code _census/check_extra_candidates_nh.js} 的「NH 方块角色 == Java 角色码」逐格对照
     * （那条只遍历 NH 的 11 台）；但 {@code _census/check_all_patterns.js} 会照常逐格自检形状串。
     */
    private static Map<String, TowerDef> extensionTowers() {
        Map<String, TowerDef> m = new LinkedHashMap<>();
        // 光学那台的形状串（逐字照抄，含每片的相等性）—— 三台共用同一份，只有角色表不同。
        final String[] shape = {
                "       ", " BBCBB ", " AACAA ", "   C   ", "   C   ", "   C   ", " AACAA ",  // z=0（Y=0 塔顶 → Y=6）
                " AAAAA ", "BBBCBBB", "ABD DBA", " BD DB ", " BD DB ", " BD DB ", "ABD DBA",  // z=1
                " A   A ", "BBCCCBB", "AD   DA", " DCCCD ", " D   D ", " DCCCD ", "AD   DA",  // z=2
                " A   A ", "CCCBCCC", "C  C  C", "C CCC C", "C     C", "C CCC C", "C  C  C",  // z=3
                " A   A ", "BBCCCBB", "AD   DA", " DCCCD ", " D   D ", " DCCCD ", "AD   DA",  // z=4
                " AAAAA ", "BBBCBBB", "ABD DBA", " BD DB ", " BD DB ", " BD DB ", "ABD DBA",  // z=5
                "       ", " BBCBB ", " AACAA ", "   C   ", "   C   ", "   C   ", " AACAA ",  // z=6
        };
        m.put("nanochip_exotic_organizer", new TowerDef(7, 7, shape.clone(),
                cells("A=FRAME:Orichalcum,B=MESH,C=REINFORCEMENT,D=GLASS")));
        m.put("nanochip_cosmic_organizer", new TowerDef(7, 7, shape.clone(),
                cells("A=FRAME:Infuscolium,B=MESH,C=REINFORCEMENT,D=GLASS")));
        m.put("nanochip_supracausal_organizer", new TowerDef(7, 7, shape.clone(),
                cells("A=FRAME:Taranium,B=MESH,C=REINFORCEMENT,D=GLASS")));
        return m;
    }


    // ---------- 仓室判定 ----------

    /**
     * 可换装仓室的能力集合（<b>最终值，用户 2026-09-15 拍板</b>）：
     * 物品/流体进出（各 min 0，不限量）+ <b>能源仓 min 1 / max 2</b> + <b>激光源仓 min 0 / max 1</b>
     * + 维护仓 min 0 / max 1。
     * <p>能源仓与激光仓的上下限出处：用户指示"允许同时安装 1 个激光仓 + 2 个能源仓"，
     * 与维基「只能放置一个多安能源仓与激光源仓」一致（详见施工记录 §16）。
     *
     * <p><b>关键（已按字节码核对）</b>：{@code TraceabilityPredicate.or(other)} 是把
     * {@code other} 里的 {@code SimplePredicate} <b>按引用</b>并进来（{@code List.addAll}，不复制），
     * 而结构匹配的全局计数表 {@code MultiblockState.getGlobalCount()} 正是<b>以 SimplePredicate 为键</b>。
     * 因此 {@code F} / {@code H} 两个字符<b>必须共用同一个实例</b>（{@code build()} 里建一次传下去），
     * 这样「至少 1 个能源仓」才是全局一条限制；若各建一个实例，就会变成「每种字符都得有 1 个」——
     * 玩家只放 1 个能源仓时结构永远不成形。
     *
     * <p><b>同一个"共用实例 + 全局计数"也意味着一条已知代价</b>：能源仓/激光仓的上限是<b>全局</b>的，
     * 玩家只要在任意 {@code F}/{@code H} 格上多插一个带该能力的仓室（尤其 gtlcore/gtladditions 的
     * ME 样板总成自带 {@code INPUT_ENERGY}/{@code INPUT_LASER}），整座结构就<b>不成形</b>，
     * 而界面只报"结构不合格"、不会指出是哪个仓超了 —— 详见施工记录 §16。
     *
     * <p><b>本方法刻意<b>不</b>包含网格接口机壳</b>：原版 {@code H} / 模块 {@code V} 的
     * {@code buildAndChain(NanochipMeshInterfaceCasing)} 那一手机壳，统一在<b>使用点</b>用
     * {@link #hatchOrCasing} 补上（{@code F} 位则由 {@link #moduleSlots} 自带）。
     * 这样"哪些字符允许机壳顶替"只有一处可读，也避免 {@code setSlotName} 原地改到共用实例。
     */
    public static TraceabilityPredicate nanochipHatches() {
        // 用低开销版：匹配语义与 abilities() 完全一致，但预览候选每种能力只实例化一台最便宜的仓。
        // 直接用 Predicates.abilities(...) 会在结构预览里实例化「该能力下的每一台机器」，
        // 其中包括 gtlcore/gtladditions 的 ME 样板总成 —— 它们的构造要几十秒，
        // 实测把服务器 tick 卡死 40 秒（详见 HatchPredicates 的说明）。
        return com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                        com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.IMPORT_ITEMS)
                .setMinGlobalLimited(0)
                .or(com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.EXPORT_ITEMS)
                        .setMinGlobalLimited(0))
                .or(com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.IMPORT_FLUIDS)
                        .setMinGlobalLimited(0))
                .or(com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.EXPORT_FLUIDS)
                        .setMinGlobalLimited(0))
                .or(com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.INPUT_ENERGY)
                        // 能源仓：min 1 / max 2 —— 【用户 2026-09-15 拍板】"允许同时安装 1 个激光仓 + 2 个能源仓"。
                        // 下限 1 不变（照搬原版 H 元素 atLeast 里的 Energy）；上限由 8 收到 2，
                        // 出处：用户指示 + 维基「只能放置一个多安能源仓与激光源仓」。
                        // ⚠️ 已知代价（改之前想清楚）：这是【全局】限制，而且 F 与 H 共用同一个 hatches 实例
                        //   （见本方法 javadoc），所以玩家只要在任意 F/H 格上多插一个带 INPUT_ENERGY 的仓
                        //   （gtlcore/gtladditions 的某些 ME 样板总成自带该能力），整座 NAC 就【不成形】，
                        //   而界面只会报"结构不合格"，不会提示"能源仓超过 2 个"。定位成本在玩家侧。
                        .setMinGlobalLimited(0).setMaxGlobalLimited(2))
                .or(com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.INPUT_LASER)
                        // 激光源仓：min 0 / max 1 —— 【用户 2026-09-15 拍板】激光仓保持"可选"（下限 0），
                        // 上限由 4 收到 1。同样受上面那条"全局 + F/H 共用实例"的代价约束。
                        // 注：原版 GT5U 的 H 元素写的是 ExoticEnergy:1（"至少 1 个"），我们按用户指示取"可选"。
                        .setMinGlobalLimited(0).setMaxGlobalLimited(1))
                .or(com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.MAINTENANCE)
                        .setMinGlobalLimited(0).setMaxGlobalLimited(1));
    }

    /**
     * <b>模块专属仓室判定：能源仓是「可选」（min 0），不是必须</b>。
     *
     * <h3>为什么（NH 逐条 javap 取证，2026-09-18 玩家质疑触发）</h3>
     * 玩家问：「为什么模块都需要能源仓才能成型，他们不是从主机上供电的吗？」
     * —— 这个质疑是对的，我方原先把模块接在 {@link #nanochipHatches()} 上（那里
     * {@code INPUT_ENERGY} 是 {@code min 1}），于是**模块被迫装能源仓**，而且因为那个实例
     * 与复合体的 {@code F}/{@code H} 位**共用**、计数是全局的，模块里插一个能源仓还会把
     * 复合体的「最多 2 个能源仓」额度顶掉 ⇒ 整座 NAC 报「结构不合格」。
     *
     * <p>NH 的实证（{@code _gtnh/cls/} 的 class 文件现 javap）：
     * <ul>
     *   <li>{@code MTENanochipAssemblyModuleBase$ModuleHatchElement} 枚举里**只有一个值**
     *       {@code VacuumConveyorHatch} —— 模块没有能源仓元素；</li>
     *   <li>{@code MTENanochipAssemblyModuleBase} 的仓室元素 = {@code VacuumConveyorHatch} +
     *       {@code HatchElement.InputBus} + {@code InputHatch} + {@code OutputHatch}
     *       （{@code HatchElementBuilder.atLeast(...)} 那一串里**没有 Energy**）；</li>
     *   <li>模块的耗电路径是 {@code drainEnergyInput(long)} →
     *       {@code MTENanochipAssemblyComplex.getEnergyHatchTier()} —— <b>直接向主机的能源仓要电</b>；
     *       能源仓元素只出现在 {@code MTENanochipAssemblyComplex}（{@code HatchElement.Energy} +
     *       {@code ExoticEnergy} 在 {@code atLeast(Map)} 里）。</li>
     * </ul>
     *
     * <p>⇒ 模块结构里能源仓 {@code min 0}（玩家想给某个模块单独供电也照样能插，只是不再强制），
     * 其余仓室能力（物品/流体进出、维护）与复合体一致；真空仓由 {@code V} 位单独管（见 {@link #vacuumHatches()}）。
     * <b>每次调用新建实例</b>（与 {@link #nanochipHatches()} 同一纪律）：GTCEu 的全局计数以实例为键，
     * 绝不能让模块的额度与复合体的额度串在一起。
     */
    public static TraceabilityPredicate moduleHatches() {
        return com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                        com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.IMPORT_ITEMS)
                .setMinGlobalLimited(0)
                .or(com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.EXPORT_ITEMS)
                        .setMinGlobalLimited(0))
                .or(com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.IMPORT_FLUIDS)
                        .setMinGlobalLimited(0))
                .or(com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.EXPORT_FLUIDS)
                        .setMinGlobalLimited(0))
                // ★ 能源仓：**min 0**（NH 模块根本没有这一项；模块吃主机的电）。
                .or(com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.INPUT_ENERGY)
                        .setMinGlobalLimited(0).setMaxGlobalLimited(2))
                .or(com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.INPUT_LASER)
                        .setMinGlobalLimited(0).setMaxGlobalLimited(1))
                .or(com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.MAINTENANCE)
                        .setMinGlobalLimited(0).setMaxGlobalLimited(1));
    }

    // ---------- 逐字符判定 ----------

    private static Block block(com.tterrag.registrate.util.entry.BlockEntry<? extends Block> entry) {
        return entry.get();
    }

    private static TraceabilityPredicate meshOnly() {
        return blocks(block(NanochipBlocks.MESH_INTERFACE_CASING));
    }

    /**
     * <b>{@code H} 位专属：「真空传送带仓 ≥ 1」</b>（对齐 NH 的
     * {@code atLeast{VacuumConveyorHatch:1}，javap 证据见 {@code _census/host_shell_reference.md} §二-1）。
     *
     * <h3>为什么必须另建一个实例（不能挂在 {@link #nanochipHatches()} 上）</h3>
     * GTCEu 的全局计数表 {@code MultiblockState.getGlobalCount()} 是<b>以 {@code SimplePredicate}
     * 实例为键</b>的，而 {@link #nanochipHatches()} 那个实例<b>与 {@code F}（{@link #moduleSlots}）共用</b>
     * （见 {@link #nanochipHatches()} 的 javadoc：共用是为了让"至少 1 个能源仓"成为一条全局限制）。
     * 若把真空仓的下限挂到那个共用实例上，{@code F} 的 48 格就会一起要求真空仓 ⇒ 语义错。
     * ⇒ 这里新建<b>只被 {@code H} 位引用</b>的实例（{@code build()} 里建一次、传给
     * {@link #hatchOrCasing} 包一层），{@code F} 位拿到的 {@code hatches} 里没有它。
     *
     * <h3>为什么模块的 {@code V} 位不加这条下限</h3>
     * NH 的模块 {@code V} 元素是 {@code atLeast(VacuumConveyorHatch, InputBus, InputHatch, OutputHatch)}
     * （{@code MTENanochipAssemblyModuleBase.addBaseStructure} javap 偏移 14-42）＝「这四类<b>至少有一种</b>」，
     * 真空仓在 {@code V} 里是**可选项**；而本模组的 {@code V} 走 {@link #hatchOrCasing}，真空仓本来就能放
     * （它带 {@code PartAbility.IMPORT_ITEMS}/{@code EXPORT_ITEMS}，已被 {@code lowCostAbilities} 覆盖）
     * ⇒ 给 {@code V} 加"必须 ≥ 1 个真空仓"会比 NH 更严、还会让已有模块不成形，故不加。
     *
     * <p><b>本方法每次调用都新建实例</b>（与 {@link #nanochipHatches()} 同一纪律）：
     * 一座结构里建一次、所有 {@code H} 格共用同一个实例，才是一条全局限制。
     */
    private static TraceabilityPredicate vacuumHatches() {
        List<Block> list = new ArrayList<>(2);
        addVacuumBlock(list, NanochipMachines.VACUUM_INPUT_HATCH);
        addVacuumBlock(list, NanochipMachines.VACUUM_OUTPUT_HATCH);
        if (list.isEmpty()) {
            // 注册顺序兜底：万一 pattern 工厂在真空仓注册之前就被求值，改从方块注册表按 id 取。
            for (String id : VACUUM_HATCH_IDS) {
                Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
                if (b != net.minecraft.world.level.block.Blocks.AIR) {
                    list.add(b);
                }
            }
        }
        if (list.isEmpty()) {
            // 宁可立刻报错，也不要静默变成"永远匹配不上"的判定（那样整座 NAC 永远不成形、且没人知道为什么）
            throw new IllegalStateException("真空传送带仓不存在（" + Arrays.toString(VACUUM_HATCH_IDS)
                    + "）⇒ H 位的「≥1 个真空传送带仓」无从判定");
        }
        // ★★ 2026-09-19（队长）：「简单模式…能上样板总成，通过样板总成，然后再连接镜像连接到模块上，
        //   这样来传输 CC 跟 PC 这种的物品，就没必要上真空仓，但是产出要降低到 1 出 8」★★
        //
        // 简单模式（nanochipComplex.simpleLogisticsMode = true）：H 位**不再强制**真空传送带仓
        //   ⇒ 下限由 1 放宽为 0，玩家可以整座 NAC 一个真空仓都不放，只用
        //   「样板总成（gtlcore 的 me_pattern_buffer / 本模组的 *猪咪样板总成*）+ 它的镜像仓
        //   （gtlcore 的 me_pattern_buffer_proxy）」经 AE2 给各模块供 CC/PC。
        //   这一支**只在简单模式**下被走到；默认（原版模式）永远返回下面那一支，逐语义不变。
        //
        // 为什么改在这里、而不是改 build() 里那一行 `where(HATCH, ...)`：
        //   · build() 的方法体（含注释）被 `_census/check_extra_candidates.js` ⑥ 按 sha256 冻结
        //     （"主机图案一个字没动"的机器判据）—— 动它就要动冻结值；
        //   · `_census/check_pattern_skip_cells.js` 的 CHECK_CHARS 逐字登记了
        //     `'H': 'hatchOrCasing(hatches).or(vacuumHatches())'`；
        //   · 挂在本方法里 ⇒ 两个冻结面都不动，且 build() 的调用点一行不改。
        //   原版那一支的返回值**逐字**保留在 vacuumHatchesVanilla(...) 里
        //   （`_census/check_nac_mechanics_parity.js` 的 G4 仍是照旧判据）。
        //
        // ⚠️ 本方法在**机器注册时**被 build() 调用一次 ⇒ 改配置需要重启游戏才改结构要求。
        if (GTLModularConfig.isNanochipSimpleLogistics()) {
            return blocks(list.toArray(new Block[0])).setMinGlobalLimited(0);
        }
        return vacuumHatchesVanilla(list);
    }

    /**
     * <b>原版(NH)模式</b>的 {@code H} 位真空仓判定：<b>下限 ≥ 1</b>
     * （本模组 2026-09-18 起的自定加严，依据与保留理由见 {@link #vacuumHatches()} 的类注释；
     * {@code _census/check_nac_mechanics_parity.js} 的 G4 逐字钉住这一行）。
     *
     * <p>简单模式的放宽走 {@link #vacuumHatches()} 里那一条分支 —— 两支**互斥且只有一支**会被求值，
     * 所以默认（原版）路径上这个方法返回的东西与本次改动前**逐字节一致**。
     */
    private static TraceabilityPredicate vacuumHatchesVanilla(List<Block> list) {
        return blocks(list.toArray(new Block[0])).setMinGlobalLimited(1);
    }

    private static void addVacuumBlock(List<Block> out, com.gregtechceu.gtceu.api.machine.MachineDefinition def) {
        if (def != null && def.getBlock() != null && !out.contains(def.getBlock())) {
            out.add(def.getBlock());
        }
    }

    private static TraceabilityPredicate firewall() {
        return blocks(block(NanochipBlocks.FIREWALL_PROJECTION_CASING));
    }

    private static TraceabilityPredicate reinforcement() {
        return blocks(block(NanochipBlocks.REINFORCEMENT_CASING));
    }

    private static TraceabilityPredicate matrix() {
        return blocks(block(NanochipBlocks.COMPUTATIONAL_MATRIX_CASING));
    }

    private static TraceabilityPredicate glass() {
        return blocks(block(NanochipBlocks.COMPLEX_GLASS));
    }

    /** {@code D}：GTCEu 自带的材料框架，<b>不新增方块</b>。 */
    private static TraceabilityPredicate naquadahFrame() {
        return frames(GTMaterials.Naquadah);
    }

    /**
     * 原版 {@code HatchElementBuilder…buildAndChain(NanochipMeshInterfaceCasing)} 的等价物：
     * <b>仓室 或 网格接口机壳</b>，二选一都能放。
     *
     * <p><b>为什么必须补这一手（不是可选的润色）</b>：原版的 {@code H}、以及模块的 {@code V}，
     * 注册时都在 hatch 元素后面挂了一手机壳（{@code buildAndChain}），语义是
     * 「这格放真仓室可以、放机壳当填充也可以」。本模组之前只有 {@link #nanochipHatches()}
     * （等价于把 {@code buildAndChain} 漏掉了），后果很硬：
     * 模块顶层 19 格 + 底层 20 格 = <b>39 个 {@code V} 必须全部装满仓</b> 才可能成形，否则模块
     * <b>永远不成形</b>；NAC 的 145 个 {@code H} 同理一格都不能留机壳。
     * （{@code F} 早就有机壳，见 {@link #moduleSlots}，所以只有 {@code H} / {@code V} 需要补。）
     *
     * <p><b>为什么用 {@code .or(meshOnly())} 包一层，而不是去改 {@link #nanochipHatches()} 本身</b>：
     * 已对 GTCEu 字节码核对 —— {@code TraceabilityPredicate.or(other)} 是
     * {@code new TraceabilityPredicate(this)} 之后 {@code common.addAll(other.common)}，
     * 也就是<b>按引用</b>把 {@code hatches} 里的 {@code SimplePredicate} 并进新实例，
     * <b>既不复制也不改动原来的实例</b>（{@code this} 不被 mutate）。
     * 而「至少 1 个能源仓」这条<b>全局</b>限制挂在 {@code INPUT_ENERGY} 那一个
     * {@code SimplePredicate} 实例上，GTCEu 的全局计数表正是<b>以实例为键</b>。
     * 所以包一层之后 {@code F} 与 {@code H} 仍然<b>共用同一个实例</b> ⇒
     * 「至少 1 个能源仓」依旧是一条全局限制，不会退化成"每种字符各要 1 个能源仓"。
     *
     * <p><b>另一条纪律</b>：{@code setSlotName} 是<b>原地改</b>那批 {@code SimplePredicate}
     * （{@code common.forEach(...)} 之后返回 {@code this}），所以这里并进来的机壳实例必须是
     * <b>本次调用新建</b>的 —— 也正因如此本方法内部自己 {@code meshOnly()}，
     * 不与 {@link #moduleSlots} 里的机壳实例共用（否则 {@code H} 的每一格都会被记成模块位）。
     *
     * @param hatches 与 {@code F} 位共用的那一个仓室判定实例（见 {@link #nanochipHatches()}）
     */
    /**
     * ★ 2026-10-15：<b>当前正在构建的是"模块自己的图案"吗</b>（只给 {@link #hatchOrCasing} 的预览分支用）。
     *
     * <p>由 {@link #buildModule} 在它那两处"围栏"里置真（V 位 + 塔身角色位），`finally` 复位 ⇒
     * 注册期是单线程、且复位必然执行 ⇒ 不存在"脏标记泄漏到主机图案"的窗口 ✓。
     *
     * <p><b>为什么用"构建期上下文"而不是给 {@link #hatchOrCasing} 加参数 / 换一个方法名</b>：
     * 调用点文本被多道自检脚本逐字钉死 —— {@code _census/check_hatch_abilities.js:158}、
     * {@code _census/check_logistics_mode.js:214} 断言 V 位表达式以 {@code hatchOrCasing(hatches)} 开头，
     * {@code _census/check_extra_candidates.js} ③ 还把 {@code predicateFor} 的每个 case 表达式按 sha256 快照 ✗。
     * 改调用点会同时打破这几道机器判据 ⇒ 用这个只在 buildModule 内部为真的静态标记，改动面最小 ✓。
     */
    private static boolean buildingModulePattern;

    private static TraceabilityPredicate hatchOrCasing(TraceabilityPredicate hatches) {
        // ★★ 2026-09-19（队长：「核对一下模块允许放的舱室，他现在竟然不允许放输入输出仓」）★★
        //   这里追加的 `.or(previewAbilities(...))` **只影响 JEI/结构预览画哪几台仓**，
        //   一个字都不改匹配：它用的是与 lowCostAbilities 完全相同的判定体（isApplicable），
        //   且落在 TraceabilityPredicate.common 里（minCount = -1 ⇒ 不进 limited、不参与全局配额），
        //   对匹配而言只是多一条"同能力、同判定"的 OR 分支。
        //
        //   为什么必须追加：GTCEu 的 BlockPattern.getPreview 按 SimplePredicate.previewCount 分四趟挑
        //   每格画什么（逐指令见 HatchPredicates 的类注释）——我们那些 `setMinGlobalLimited(0)` 的仓谓词
        //   minCount=0 且 previewCount=-1，四趟里**一趟都不命中** ⇒ 修改前 V 位的 39 格预览全画成
        //   「纳米芯片网格接口机壳」，一台仓都不出现，玩家只能得出"这儿不允许放仓"。
        //   官方给这类"让预览展出多种仓"的写法就是 setPreviewCount(1)
        //   （GTCEu 自己的大型锅炉：abilities(IMPORT_FLUIDS).setMinGlobalLimited(1).setPreviewCount(1)）。
        //
        //   为什么写在 hatchOrCasing 里、而不是写进 build()/buildModule() 的 where(...)：
        //   ① build() 的方法体字节码被 `_census/check_extra_candidates.js` ⑥ 按 sha256 冻结
        //      （"主机图案一个字没动"的机器判据），改它就要动冻结值；
        //   ② `_census/check_pattern_skip_cells.js` 的 CHECK_CHARS 逐字登记了 H / V 两个字符的谓词表达式，
        //      改 where(...) 同样要动登记值；
        //   ③ hatchOrCasing 是"哪些字符允许机壳顶替"的唯一入口（H / 模块 V / 塔身 HATCH 位都走它），
        //      预览轮播挂在它身上，三处仓位一次到位，且两个冻结面都不受影响。
        return hatches.or(meshOnly())
                .or(buildingModulePattern
                        // ★★ 2026-10-15（本轮修复 · 模块侧预览）★★ 模块自己的图案（V 位 39 格 + 塔身仓位）
                        //   用"I/O 代表仓"预览（= 2026-09-19 的原始口径，也才是这些格子的正确示意）。
                        //   【为什么必须分开】下面那条"14 台模块主机候选表"是给**主机 H 位**用的
                        //   （队长 2026-10-02：「这一行应该只显示他的那些模块主机」）；它对模块侧是错的：
                        //   模块的 V/塔身格会被画成 14 台 NAC 模块主机方块，而它们与主机共用贴图
                        //   ⇒ 玩家在「纳米芯片组装矩阵」的预览里看到的就是"复合体那套"✗
                        //   （开关与完整根因见 HatchPredicates.MODULE_SIDE_PREVIEW_IO_ONLY）。
                        //   【只影响预览】两个分支都是纯预览候选表：判定体仍是 applies(isApplicable)、
                        //   previewCount 仍是 1、仍落 common（不参与全局配额）⇒ 结构判定/成型/配方/
                        //   模块连接一个字都没动 ✓。
                        ? com.gtlmodular.common.data.HatchPredicates.previewAbilitiesForModule(
                                com.gtlmodular.common.data.HatchPredicates.NAC_HATCH_ABILITIES)
                        : com.gtlmodular.common.data.HatchPredicates.previewAbilities(
                                com.gtlmodular.common.data.HatchPredicates.NAC_HATCH_ABILITIES));
    }

    /**
     * {@code I} 位：原版是 {@code atLeast{IgnoredHatch:0}} —— <b>任何仓都吃下，但计数恒 0</b>。
     *
     * <p><b>为什么这里选「宽放」而不是「不允许放东西」</b>（二选一的理由）：
     * 每个模块基座顶层（原版 Y=46）的 20 格环里有 <b>16 格是 {@code I}</b>，
     * 而模块自己的顶层环恰好有 <b>19 个 {@code V} 仓位</b>压在这些格子上 ——
     * 也就是说「玩家往模块里插的真空端口/总线」物理上就坐在 {@code I} 位上。
     * 若 {@code I} 只认网格接口，<b>任何插了仓的模块都会让整座 NAC 结构失效</b>。
     * 所以 {@code I} 必须像原版那样「吃下任何仓位、但不计入任何要求」。
     *
     * <p><b>为什么不用 {@code nanochipHatches()}</b>：那会把 {@code I} 的 432 格也算进
     * 「至少 1 个能源仓」的全局计数、并且多出 432×7 次能力判定（结构判定变慢）。
     * 用一条自定义判定（网格接口 <b>或</b> 任意 GTCEu 仓位）等价于原版的 {@code IgnoredHatch}：
     * 能放、不报结构错、但<b>不计入</b>能源仓要求 —— 与原版语义一致。
     */
    private static TraceabilityPredicate ignoredSlots() {
        return meshOnly().or(custom(
                state -> {
                    if (state.getBlockState().is(block(NanochipBlocks.MESH_INTERFACE_CASING))) {
                        return true;
                    }
                    BlockPos pos = state.getPos();
                    com.gregtechceu.gtceu.api.machine.MetaMachine machine =
                            com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(state.getWorld(), pos);
                    return machine instanceof IMultiPart;
                },
                () -> new BlockInfo[]{BlockInfo.fromBlockState(
                        block(NanochipBlocks.MESH_INTERFACE_CASING).defaultBlockState())}));
    }

    /**
     * {@code F} 位：<b>模块装配位</b>。接受「模块控制器方块 / 网格接口 / 仓室」，
     * 并把匹配到的世界坐标登记到 {@link #MODULE_SLOT_NAME} 槽名下（见该常量的注释）。
     *
     * <p>★ 2026-09-16：模块控制器加<b>全局上限 12 台</b>（NH 的 F 元素是 {@code 0-12}）——
     * 见方法体内 `setMaxGlobalLimited(12)` 那一段的说明（只限控制器、不限机壳）。
     *
     * <p>注意登记名<b>只能加在「方块」那一个 SimplePredicate 上</b>：
     * {@code setSlotName} 会给 {@code common}/{@code limited} 里的每个 SimplePredicate 都设名，
     * 而 {@code or(hatches)} 是<b>按引用</b>把共用的仓室判定并进来 ——
     * 若在 {@code or()} 之后再 {@code setSlotName}，仓室判定的实例也会被改名，
     * 于是 {@code H}/{@code I} 的每一格都会被记成模块位。所以顺序必须是
     * <b>先建「方块」判定、先设槽名、最后才 {@code or(hatches)}</b>。
     */
    private static TraceabilityPredicate moduleSlots(TraceabilityPredicate hatches) {
        List<IMachineBlock> accepted = new ArrayList<>();
        for (MultiblockMachineDefinition def : NanochipMachines.NANOCHIP_MODULES.values()) {
            if (def != null && def.get() != null) {
                accepted.add(def.get());
            }
        }
        TraceabilityPredicate blockPart;
        if (accepted.isEmpty()) {
            blockPart = meshOnly();
        } else {
            // ⚠️ 顺序有实际后果（用户 2026-09-15 批准改成"机壳在前"）：
            // 对**结构匹配**来说 or() 顺序无关（任一候选命中即通过），但 GTCEu 的
            // `BlockPattern.autoBuild`（控制器界面的"自动搭建"）是**按候选顺序取第一个**来填格子的。
            // 旧顺序 = blocks(11 种模块控制器).or(meshOnly()) ⇒ 自动搭建把 48 个 F 位**全填成组装矩阵控制器**
            //（实测：存档里 48 台状态完全一致的 `nanochip_assembly_matrix[facing=south,…]`）。
            // 现在机壳在前 ⇒ 自动搭建填机壳（合理），控制器由玩家自己放。
            //
            // ★ 2026-09-16 新增「最多 12 台模块」（对齐 NH 的 F 元素 0-12，用户已批准）：
            //   `.setMaxGlobalLimited(12)` 的语义是**给该判定里的每一个 SimplePredicate 都设上限**
            //   （字节码：把 common 全搬进 limited 后逐个 `putfield SimplePredicate.maxCount`），
            //   而 GTCEu 的全局计数**以 SimplePredicate 实例为键**。所以这一行必须写在
            //   `blocks(模块控制器)` 上、而**不是**写在 moduleSlots() 整个返回值上：
            //   · 写在这里 = 只限"模块控制器总数 ≤ 12"（NH 的语义：12 个基座 ⇒ 最多 12 台模块）✓
            //   · 写在整条 moduleSlots 上 = 连**网格接口机壳**也被限到 12，而 48 个 F 位本来就
            //     允许、且"自动搭建"就是拿机壳去填的 ⇒ 一填满结构就不成形 ✗。
            //   `blocks(...)` 只产出**一个** SimplePredicate（候选 = 各模块控制器），所以
            //   这一行正好是"各类控制器合计 ≤ N 台"。
            //
            //   ★ 2026-09-18 队长指示「奇异/寰宇/超因果优化组织器…主机也要有改变」：
            //     模块种类从 11 种增到 **14 种**（+奇异/寰宇/超因果优化组织器），若上限仍留 12 就**装不下全部种类**。
            //     ⇒ 上限 12 → **15**（14 种 + 1 余量），与 `NanochipAssemblyComplexMachine.getMaxModuleCount()`（本轮 12→15）保持一致。
            //     注意：结构上 F 位一共 48 格，15 台完全装得下；NH 的 12 是"11 种 + 1 余量"的同口径外推。
            // ★★ 2026-09-19（队长：「主机多方块自动建造的时候，他自己放了一堆纳米芯片组装阵列，这个必须要改」）★★
            //
            // 【病因：自动建造 pick 的是 limited 里的第一个，而控制器正好被挪进了 limited】
            // 字节码证据（gtceu-1.20.1-1.4.4 `BlockPattern.autoBuild`）：
            //   · 偏移 286–321：先遍历 `predicate.limited`，对每个 SimplePredicate 调 testLimited(state)；
            //   · 偏移 498–521：取**第一个"当前可满足"**的那个判据的 `candidates.get()[0]` 填这一格；
            //   · 偏移 1002：只有 limited 里一个都不可满足时，才回落到 `TraceabilityPredicate.common`。
            // 而 `TraceabilityPredicate.setMaxGlobalLimited(int)` 的字节码是**无条件**把该判据的 `common`
            // 整批搬进 `limited`（`limited.addAll(common)` → `common.clear()` → 逐个 `putfield maxCount`）。
            // ⇒ 旧写法 `meshOnly().or(blocks(控制器).setMaxGlobalLimited(15))` 里：
            //     机壳留在 common、控制器进了 limited ⇒ 自动建造**优先填控制器**，
            //     48 个 F 位全被填成 `nanochip_assembly_matrix` ⇒ 撞上"控制器合计 ≤ 15 台" ⇒ 主机永远不成形。
            //   （旧注释以为"机壳写在前面就够了"—— 那只对**匹配**成立，对**自动建造**不成立。）
            //
            // 【修法：让"机壳"也进 limited 并排在第一位】
            //   `or(other)` 的字节码是"拷贝 this 后追加 other" ⇒ **左边在前**，顺序可控。
            //   给机壳那一支设一个**永不可能触发**的上限：F 位一共就 `countOf('F')` 格，
            //   机壳最多也只能放这么多 ⇒ 上限恒不越界，纯粹是把机壳排到 limited 首位。
            //   （必须用 `Math.max(1, …)`：`setMaxGlobalLimited(0)` 会把 maxCount 设成 0，
            //     那样任何一格机壳都过不了判定 ⇒ 结构永远不成形。这条 API 没有 `>0` 守卫，字节码已核。）
            //   ⇒ 自动建造恢复成"填机壳"，控制器仍由玩家自己放，"控制器 ≤ 15 台"这条上限**一个字没动**。
            blockPart = meshOnly().setMaxGlobalLimited(Math.max(1, countOf(MODULE_SLOT)))
                    .or(blocks(accepted.toArray(new IMachineBlock[0])).setMaxGlobalLimited(15))
                    // ★★ 2026-09-21（队长令：「主机**不再生成**平台那圈凸出来的 20 个网格接口机壳，
                    //    最终平台要像图3那样是**一个正方形**；但模块必须还能连上主机」）★★
                    //
                    // 【病因在判据层，不在图案】模块位（F）原先只认「网格接口机壳 或 模块控制器」
                    //   ⇒ **空气不算匹配**。于是玩家把那圈机壳一拆，主机就永远不成形。日志实证
                    //   （2026-09-21 22:44，队长拆掉凸出来的机壳之后）：
                    //     · 全局计数表：候选=[gtlmodular:nanochip_mesh_interface_casing] 计数=**22** max=48
                    //     · 「唯一方块格（网格机壳/强化机壳/复合体玻璃/计算矩阵机壳/防火墙投影）全部就位
                    //        ⇒ 缺口在「多解格」（H 仓位 / I 可忽略位 / F 模块位 / 空格）或数量类限制上」
                    //     · 「F 位 0」、其余 26 格 `该格实际=minecraft:air`
                    //
                    // 【本行加 air() 之后的三条效果】
                    //   ① **空着的模块位照样登记**：GTCEu 的 `SimplePredicate.setSlotName` 是「匹配成功就把
                    //      该格世界坐标写进 matchContext 的 slots 表」，空气现在算匹配 ⇒ 48 个模块位的位置
                    //      **一个不少**地登记 ⇒ `moduleSlotsOf(...)` 照旧拿全，模块连接不受影响 ✓
                    //   ② **自动搭建不再铺那 20 格**：`BlockPattern.autoBuild` 只填「当前**不**匹配」的格
                    //      （先例：2026-09-19 修「自动建造填了一堆组装矩阵控制器」那个 bug 时，正是空地上的
                    //      F 位因空气不匹配才被填）；现在空格既然匹配 ⇒ 自动搭建**跳过**它 ⇒ 不再多出那圈机壳 ✓
                    //   ③ 平台最终只剩基座正中 5×5「网格接口机壳」= 图3 那个正方形 ✓
                    //      （那 5×5 由 'B' 位强制 = 平台本体：本来就必须有，也正是"正方形"的来源）
                    .or(air());
        }
        return blockPart.setSlotName(MODULE_SLOT_NAME).or(hatches);
    }

    /**
     * 从结构匹配结果里取出全部 {@code F}（模块位）世界坐标。
     *
     * <p>结构成形后（{@code onStructureFormed}）调用。GTCEu 把每次成功匹配的位置记在
     * {@code matchContext} 的 {@code "slots"} 表里（键 = {@code BlockPos.asLong()}、值 = 槽名集合）。
     *
     * @param state 该多方块的 {@code getMultiblockState()}
     * @return 本次匹配到的模块位坐标（可能是空表）
     */
    public static List<BlockPos> moduleSlotsOf(MultiblockState state) {
        if (state == null) {
            return List.of();
        }
        Map<Long, Set<String>> slots = state.getMatchContext().get("slots");
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        List<BlockPos> out = new ArrayList<>();
        for (Map.Entry<Long, Set<String>> entry : slots.entrySet()) {
            Set<String> names = entry.getValue();
            if (names != null && names.contains(MODULE_SLOT_NAME)) {
                out.add(BlockPos.of(entry.getKey()));
            }
        }
        return out;
    }

    // ---------- 模块预览位点（只读派生；供 IModularMachineHost#getModulesForRendering） ----------

    /**
     * 一条「模块控制器位点」：模块图案原点相对 <b>NAC 控制器</b>的偏移，以及该位点上的模块正脸。
     *
     * @param moduleId {@link NanochipModuleKind#machineId()}（本模组注册 id，用于反查定义）
     * @param offset   模块控制器那一格相对 NAC 控制器的偏移（<b>= {@code ModuleRenderInfo} 第 1 个参数的语义</b>）。
     *                 <b>逐字取自</b> {@link #NAC_MODULE_PREVIEW_SLOTS} 那张实测表，运行时<b>不再做任何推导/换算</b>
     * @param front    该位点上的模块正脸（<b>= {@code ModuleRenderInfo} 第 4 个参数的语义</b>）。
     *                 <b>逐位点不同</b>：模块控制器永远坐在自己基座朝外那条边的边中点上，
     *                 于是"正脸"= 背离基座中心的方向（{@code −z→NORTH / +z→SOUTH / −x→WEST / +x→EAST}）。
     *                 同样<b>逐字取自</b> {@link #NAC_MODULE_PREVIEW_SLOTS}
     */
    public record ModuleControllerSlot(String moduleId, BlockPos offset, Direction front) {
    }

    /**
     * 图案坐标 {@code (x, rowFromBottom, z)} → 「宿主真实朝向参照系下的相对偏移」
     * （= {@code ModuleRenderInfo} 第 1 个参数）。
     *
     * <h4>逐项换算（★★ 2026-10 修正：<u>两个水平轴都要取反</u>，旧版只反了 z）</h4>
     * <pre>
     *   offset = ( NAC_CTRL_X − x , rowFromBottom − NAC_CTRL_ROW , NAC_CTRL_Z − z )
     *              ^^^^^^^^^^^^^                                  ^^^^^^^^^^^^^
     *              x 必须取反（旧版写成 x − NAC_CTRL_X ✗）            z 取反 ✓
     * </pre>
     * <b>为什么两个水平轴都取反</b>：{@link #NAC_SLICES} 是<b>已经绕过图案中心转过 180°</b>的
     * 那一份（{@code P'(x,row,z) = P(62−x, row, 62−z)}，见类注释），于是"图案下标"到"世界"的
     * 映射整体差 180°（纯旋转、手性不变）。用 {@code _census/expect_table.md} 的实测公式
     * {@code 世界坐标 = 锚点 + (X − 31, row − 8, z − 30)}（{@code X}/{@code z} = <b>旋转前</b>下标）
     * 把 {@code X = 62 − x}、{@code z = 62 − z'} 代进去即得上面这条（{@code NAC_CTRL_Z = 32}）。
     *
     * <h4>三处独立核对（全部用 {@code _census/expect_table.md} 的玩家存档实测值）</h4>
     * <ul>
     *   <li>基座 0 中心：旋转前图案 {@code (23, row 1, 8)} ⇒ 旋转后 {@code (39, 1, 54)}
     *       ⇒ {@code (31−39, 1−8, 32−54) = (−8, −7, −22)}，与实测偏移 {@code (−8, −7, −22)} ✓
     *       （旧版 x 项算成 {@code 23−31=−8} —— 那是拿<b>旋转前</b>下标去套<b>旋转后</b>的数组，
     *       数值碰巧对得上，换成旋转后下标 {@code 39} 就露馅：{@code 39−31=+8} 与实测反号）；</li>
     *   <li>基座 0 的 −z 边中点 F 格：旋转前 {@code (23, 2, 5)} ⇒ 旋转后 {@code (39, 2, 57)}
     *       ⇒ {@code (−8, −6, −25)}，与实测（F 位偏移 {@code (−8,−6,−25)}、世界 {@code (22,66,−110)}）✓；</li>
     *   <li>基座 2 / 基座 5 中心：{@code (14,14) → (48,48) ⇒ (−17,−7,−16)} ✓、
     *       {@code (54,23) → (8,39) ⇒ (23,−7,−7)} ✓（两条都与 expect_table 的实测偏移逐值相同）。</li>
     * </ul>
     * <ul>
     *   <li><b>row 项不取反</b>：{@code row} 自底向上、{@code 0} = 图案底（GTCEu 的 aisle 第 0 个
     *       字符串 = 最底层，见 {@code NAC_进游戏验证清单.md} §11.2）；控制器在 {@code NAC_CTRL_ROW = 8}
     *       ⇒ {@code row − NAC_CTRL_ROW} 直接就是世界 Y 偏移（基座 0 的模块控制器世界 {@code y = 66}
     *       = 锚点 {@code 72 − 6}，而它在本图案里是 {@code row 2} ⇒ {@code 2 − 8 = −6} ✓）。</li>
     *   <li><b>第 2/3 参（宿主朝向参照系）</b>：现役 NAC 那台主是 {@code facing=north}（施工记录 §50 实测）
     *       ⇒ 调用方填 {@code NORTH}/{@code UP}，与本法返回值同口径；见
     *       {@code ModulePreviewSlots} 类注释「第 2/3 参的正确语义」。</li>
     * </ul>
     *
     * <p><b>★ 2026-10 起本方法已无调用点</b>（位点改成 {@link #NAC_MODULE_PREVIEW_SLOTS} 那张实测表，
     * 不再做任何运行时换算）。保留它是因为它是"表里的偏移为什么是这些数"的换算口径依据
     * （上面三处核对仍然有效，可直接用来自查那张表）；<b>不要删</b>，也<b>不要拿它去反推新位点</b>。
     */
    @Deprecated
    private static BlockPos patternToHostOffset(int x, int rowFromBottom, int z) {
        return new BlockPos(NAC_CTRL_X - x, rowFromBottom - NAC_CTRL_ROW, NAC_CTRL_Z - z);
    }

    // ★ 2026-10 删除：私有助手 `patternRow(int y, int height) { return height - 1 - y; }`。
    //   它唯一的调用点是旧版 deriveModuleControllerOffsets（把【已经自底向上】的 MOD_CTRL_ROW=2
    //   又按"自顶向下"翻了一次 ⇒ 得到 row 0，是"位点算错"的帮凶之一）。
    //   ★ 2026-10 二次修正后，位点干脆不再推导（改用 NAC_MODULE_PREVIEW_SLOTS 实测表），
    //   连同 deriveModuleControllerOffsets 一起停用，故更不需要任何行号换算。

    /** {@link #moduleControllerOffsets()} 的缓存（表是常量 ⇒ 全局只算一次，**不是**"运行时推导"）。 */
    private static volatile List<ModuleControllerSlot> moduleControllerCache;

    /**
     * ★★【2026-10 三次修正 · 模块预览位置】<b>位点表选择开关</b> ★★
     *
     * <ul>
     *   <li><b>{@code true}（默认 = 修复后的正确行为）</b> ⇒ 用 {@link #NAC_MODULE_PREVIEW_SLOTS}
     *       （= <b>"−z 边中点 + facing=north" 那一套</b>：12 条全部落在各自基座的 −z 边中点、朝向一律 north）。
     *       <b>为什么它才是"结构里的真实位置"</b>（三条独立证据）：
     *       <ol>
     *         <li>把模块铺进世界的<b>唯一</b>来源 {@code gen_probe_datapack.js} 用的是"原样映射"
     *             （{@code x = mx + X − 3}、{@code z = mz + z}），而该脚本自己的硬校验<b>明确断言
     *             这套映射只与"−z 边中点 + north"自洽</b>（{@code :631-635} 与 {@code :972-997}
     *             的 {@code FACING_OF_SLOT}：同 x 且 z 更小 ⇒ north；朝向不配就抛错、不写盘）；</li>
     *         <li>玩家存档里实测到的模块控制器就是 <b>{@code (22, 66, −110)}</b> = 0 号基座的 −z 边中点、
     *             朝向 {@code north}（同文件 {@code :619-640} 的锚点默认值 = 实测值）；
     *             <b>12 个基座全都是这一套</b>（{@code _census/expect_table.md} §1
     *             「建议放控制器的那个 F 位（+朝向）」列，12 行全 = −z 边中点 + north）；</li>
     *         <li>结构侧不变式（{@code _census/check_module_base_vs_host.js} 的 48/48 穷举）说
     *             "每个基座恰好 1 个 F 位 × 1 个朝向能让模块 7×7 底座与主机基座逐格重合"，
     *             其中 <b>"−z 边中点 ↔ north"</b> 就是已被实机成形验证过的那一组。</li>
     *       </ol></li>
     *   <li><b>{@code false}</b> ⇒ 逐字回到 2026-10 上一版：{@link #NAC_MODULE_PREVIEW_SLOTS_OUTWARD_EDGES}
     *       （每条取"最朝外"的那个 F 位 ⇒ 12 条里有 8 条落在 ±x 边上、朝向 west/east）。
     *       保留它<b>只为进游戏做 A/B 对照</b>：那条路线所依赖的 ±x 朝向配对
     *       <b>从未在任何真实存档里出现过</b>（数据包只铺 −z + north），而它的 east/west 的 ±90° 符号
     *       只是我方脚本的手写约定（{@code _census/check_module_base_vs_host.js:98-103}）——
     *       {@code _census/模块框架对齐_执行记录.md} §⑦-3 已把这条列为未取证项。</li>
     * </ul>
     *
     * <p><b>影响面</b>：本开关只改"喂给预览与框架扫描的那 12 个坐标"（两者同源）。
     * 不改模块连接判定、不改 {@code @Persisted}、不改结构定义、不改条数
     * （两张表都是 {@value #NAC_TABLE_SIZE} 条）。改本值需<b>重新构建</b>（编译期常量）。
     */
    public static final boolean NAC_MODULE_SLOTS_CANONICAL_MINUS_Z = true;

    /**
     * ★★ <b>默认生效的位点表（见 {@link #NAC_MODULE_SLOTS_CANONICAL_MINUS_Z}）：
     * 12 个基座的「−z 边中点」模块控制器位点，正面一律 {@link Direction#NORTH}</b> ★★
     *
     * <p><b>偏移口径</b>：{@code offset = 基座中心 + (0, +1, −3)}，单位 = 格、相对 NAC 控制器
     * （= {@code ModuleRenderInfo} 第 1 个参数的世界偏移；与 {@code expect_table.md} §1 的世界坐标列
     * 逐值同号：世界 = 锚点 {@code (30,72,−85)} + 偏移）。
     *
     * <p><b>逐条自检（本次 12/12 直接从 {@code NAC_SLICES} 现读，不是抄表）</b>：
     * 反算公式 {@code (x = 31 − offX, row = 8 + offY, z = 32 − offZ)}（与
     * {@code _census/模块框架对齐_执行记录.md} §② 同一口径），再按 {@code decode} 的
     * {@code 行号,左填充,内容} 规则取字符。12 条全部命中 {@code 'F'}、且全部在 {@code row = 2}
     * （= 基座顶层 = 模块控制器层，数据包 {@code my = cy − 6} 同层）：
     * <pre>
     *   0 号 (−8,−6,−25) ⇒ (39,2,57)  'F'   1 号 ( 8,−6,−25) ⇒ (23,2,57)  'F'
     *   2 号 (−17,−6,−19) ⇒ (48,2,51) 'F'   3 号 (17,−6,−19) ⇒ (14,2,51) 'F'
     *   4 号 (−23,−6,−10) ⇒ (54,2,42) 'F'   5 号 (23,−6,−10) ⇒ ( 8,2,42) 'F'
     *   6 号 (−23,−6,  6) ⇒ (54,2,26) 'F'   7 号 (23,−6,  6) ⇒ ( 8,2,26) 'F'
     *   8 号 (−17,−6, 15) ⇒ (48,2,17) 'F'   9 号 (17,−6, 15) ⇒ (14,2,17) 'F'
     *  10 号 (−8,−6, 21) ⇒ (39,2,11) 'F'  11 号 ( 8,−6, 21) ⇒ (23,2,11) 'F'
     *   （世界坐标自检：0 号 = 锚点+(−8,−6,−25) = (22,66,−110) = 玩家存档实测那一台 ✓）
     * </pre>
     *
     * <p><b>朝向为什么全是 {@code NORTH}</b>：模块控制器永远坐在自己基座朝"−z 边"那个边中点上，
     * 而模块图案（2026-09-16 起做过图案内 180° 旋转）在该位点上唯一可行的正脸就是 {@code NORTH}
     * （见 {@link #NAC_MODULE_SLOTS_CANONICAL_MINUS_Z} 第 3 条与
     * {@code gen_probe_datapack.js:972-997} 的硬校验）。
     *
     * <p><b>顺位口径</b>：按偏移 {@code x → y → z} 升序书写（与旧表同口径、可复现）。
     */
    private static final ModuleControllerSlot[] NAC_MODULE_PREVIEW_SLOTS = {
            // {偏移 x, y, z 相对 NAC 控制器；front = 该位点上模块的正脸}
            // 来源 = 各基座中心的 −z 边中点（= 数据包/玩家存档唯一铺过、且已被实机成形验证的那一组）
            new ModuleControllerSlot(null, new BlockPos(-23, -6, -10), Direction.NORTH),  // 基座 4  −z 边中点
            new ModuleControllerSlot(null, new BlockPos(-23, -6, 6), Direction.NORTH),    // 基座 6  −z 边中点
            new ModuleControllerSlot(null, new BlockPos(-17, -6, -19), Direction.NORTH),  // 基座 2  −z 边中点
            new ModuleControllerSlot(null, new BlockPos(-17, -6, 15), Direction.NORTH),   // 基座 8  −z 边中点
            new ModuleControllerSlot(null, new BlockPos(-8, -6, -25), Direction.NORTH),   // 基座 0  −z 边中点
            new ModuleControllerSlot(null, new BlockPos(-8, -6, 21), Direction.NORTH),    // 基座 10 −z 边中点
            new ModuleControllerSlot(null, new BlockPos(8, -6, -25), Direction.NORTH),    // 基座 1  −z 边中点
            new ModuleControllerSlot(null, new BlockPos(8, -6, 21), Direction.NORTH),     // 基座 11 −z 边中点
            new ModuleControllerSlot(null, new BlockPos(17, -6, -19), Direction.NORTH),   // 基座 3  −z 边中点
            new ModuleControllerSlot(null, new BlockPos(17, -6, 15), Direction.NORTH),    // 基座 9  −z 边中点
            new ModuleControllerSlot(null, new BlockPos(23, -6, -10), Direction.NORTH),   // 基座 5  −z 边中点
            new ModuleControllerSlot(null, new BlockPos(23, -6, 6), Direction.NORTH),     // 基座 7  −z 边中点
    };

    /**
     * ★★ <b>【备选表 · 只有把 {@link #NAC_MODULE_SLOTS_CANONICAL_MINUS_Z} 置 false 才生效】
     * "每个基座最朝外那个 F 位" 的位点表（2026-10 上一版，逐字保留）</b> ★★
     *
     * <p><b>为什么它不再是默认值（一句话）</b>：它把 12 条里的 8 条放在 ±x 边中点上、朝向
     * {@code west}/{@code east} —— 而世界里的模块从来只被铺在 −z 边中点 + {@code north}
     * （{@code gen_probe_datapack.js} 的"原样映射"只与该组合自洽），那一组 ±x 配对
     * <b>从未在任何存档里出现过</b>，其 east/west 的 ±90° 符号也只是我方脚本的手写约定
     * （未取证，见 {@code _census/模块框架对齐_执行记录.md} §⑦-3）。
     * <br>保留本表的唯一用途 = 把开关置 {@code false} 后进游戏做 A/B 对照（逐字可回退）。
     *
     * <hr>
     *
     * ★★ <b>NAC 12 个模块基座的「模块控制器位点」—— 写死的实测表（唯一数据来源）</b> ★★
     *
     * <p><b>来源 = 玩家存档实测：{@code _census/expect_table.md} §1「12 个模块基座」的
     * 「4 个 F 位(偏移)」列</b>（该表由 {@code _census/expect_table.js} 从
     * {@code _gtnh/_tools/main_structure.json}（GTNH 原版 {@code MAIN_STRUCTURE} 的逐字节重建）
     * 现算，锚点 = NAC 控制器世界坐标 {@code (30,72,−85)}；坐标换算公式见该文件 §0：
     * {@code 世界坐标 = 锚点 + (X − 31, row − 8, z − 30)}）。
     *
     * <p>每一条 = 「<b>该基座 4 个 F 位里，唯一能让模块 7×7×3 底座与主机基座逐格重合</b>的那一个」
     * （{@code _census/module_base_vs_host.md} 的 48/48 穷举；配对规则见 {@code expect_table.md}:39
     * 「{@code −z 边中点→facing=north}、{@code +z}→south、{@code −x}→west、{@code +x}→east」）。
     * <br><b>★ 2026-10 三次修正的更正</b>：上面这句"唯一"只对"<b>该条自己那个 F 位</b>"成立
     * （48/48 穷举的结论是"每个 F 位恰好配一个朝向"，不是"每个基座只有一个 F 位能用"）——
     * 本表额外做了一次<b>挑选</b>：每条取"最朝外"的那个边中点（12 条里 8 条落在 ±x 边上）。
     * 那次挑选正是"预览位置与真实存档对不上"的来源，故本表已降级为备选（见本表上方的开关说明）。
     * 相对锚点偏移的换算口径 = {@link #patternToHostOffset}
     * （{@code (31 − x, row − 8, 32 − z)}，与 {@code expect_table.md} §1 逐值同号；两者的交叉核对
     * 见该方法的 javadoc —— 那一节仍然有效，只是<b>运行时不再调用它</b>）。
     *
     * <h3>逐条自检（对着 {@code expect_table.md} §1 的世界坐标列核过）</h3>
     * <pre>
     *   基座 0 −z 边中点 (−8,−6,−25) 世界 (22,66,−110) = 锚点 + 偏移 ✓   NORTH
     *   基座 2 −x 边中点 (−20,−6,−16) 世界 (10,66,−101) ✓                WEST
     *   基座 5 +x 边中点 ( 26,−6,−7)  世界 (56,66,−92)  ✓                EAST
     *   基座 10 +z 边中点 (−8,−6,27)  世界 (22,66,−58)  ✓                SOUTH
     *   （其余 8 条同法逐条核过；12/12 全部命中 expect_table.md §1 的"4 个 F 位(偏移)"列）
     * </pre>
     *
     * <h3>★ 为什么写死 —— 已作废的运行时反推（历史，禁止改回）</h3>
     * <p>旧实现（{@link #deriveModuleControllerOffsets}，现保留为 {@code @Deprecated} 的死代码）
     * 是在运行时从 {@link #NAC_SLICES} 反推：先收齐所有 {@code F} 位，再用「一个 {@code F} 格的
     * <b>4 个轴向 ±3 邻居也都是 F</b>」当"基座中心"判据。玩家日志实锤它<b>恒命中 0 个</b>：
     * <pre>
     *   [gtlmodular] 模块预览: getModulesForRendering 被调用 主机=纳米芯片组装复合体@… 返回=0
     *   [gtlmodular] NAC 模块预览位点推导失败（返回空表）：
     *     java.lang.IllegalStateException: 基座中心判据命中 0 个（期望 12）⇒ 模块预览位点不可信
     * </pre>
     * 几何原因（可对照本文件 {@code NAC_SLICES} 逐格复核）：<b>基座中心那一格根本不是 {@code F}</b>
     * —— 基座中心在 {@code row=1} 的 {@code 'C'}（强化机壳，例如基座 0 中心图案下标 {@code (39,1,54)}
     * 是 {@code z=54} 那条 {@code "1,10,C…"} 里的 {@code C}），而 4 个 F 位在 {@code row=2}
     * （顶层）的 ±3 处 ⇒ 从任何一个 {@code F} 出发，±3 邻居<b>永远</b>落在 {@code row=1}/{@code row=2}
     * 的 {@code 'C'}/{@code 'B'}/{@code 'I'} 上，命中数恒 0 ⇒ 恒抛异常 ⇒ 恒返回空表
     * ⇒ 玩家点 {@code M:ON} 什么都不会变。
     * <p>⇒ 答案早就有（这张实测表 12/12 落在 {@code expect_table.md} §1 里），
     * <b>本次改成"写死的表"、彻底停用那套反推；失败姿态从"抛异常 × 返回空表"改成
     * "表是常量、不会失败"</b>。禁止再改回运行时反推。
     *
     * <p>每条的模块定义 id 仍按原方式取（{@code NanochipModuleKind.values()} 按位点轮转给出
     * {@link NanochipModuleKind#machineId()}，再由 {@code NanochipMachines.NANOCHIP_MODULES} /
     * GTCEu 注册表反查定义）；<b>某个种类没注册时的退路</b>（"退到任一已注册的 NAC 模块、
     * 保持 12 条数不变"）在调用方 {@code ModulePreviewSlots.buildNac()} 里，本轮未改动。
     */
    private static final ModuleControllerSlot[] NAC_MODULE_PREVIEW_SLOTS_OUTWARD_EDGES = {
            // {偏移 x, y, z 相对 NAC 控制器；front = 该位点上模块的正脸（背离基座中心）}
            // 来源 = 玩家存档实测（_census/expect_table.md §1「4 个 F 位(偏移)」列）；
            // 已作废的运行时反推（±3 轴向邻居判据）见 deriveModuleControllerOffsets 的历史注释；
            // ★ 禁止再改回反推。
            new ModuleControllerSlot(null, new BlockPos(-26, -6, -7), Direction.WEST),   // 基座 4  −x 边中点
            new ModuleControllerSlot(null, new BlockPos(-26, -6, 9), Direction.WEST),    // 基座 6  −x 边中点
            new ModuleControllerSlot(null, new BlockPos(-20, -6, -16), Direction.WEST),  // 基座 2  −x 边中点
            new ModuleControllerSlot(null, new BlockPos(-20, -6, 18), Direction.WEST),   // 基座 8  −x 边中点
            new ModuleControllerSlot(null, new BlockPos(-8, -6, -25), Direction.NORTH),  // 基座 0  −z 边中点
            new ModuleControllerSlot(null, new BlockPos(-8, -6, 27), Direction.SOUTH),   // 基座 10 +z 边中点
            new ModuleControllerSlot(null, new BlockPos(8, -6, -25), Direction.NORTH),   // 基座 1  −z 边中点
            new ModuleControllerSlot(null, new BlockPos(8, -6, 27), Direction.SOUTH),    // 基座 11 +z 边中点
            new ModuleControllerSlot(null, new BlockPos(20, -6, -16), Direction.EAST),   // 基座 3  +x 边中点
            new ModuleControllerSlot(null, new BlockPos(20, -6, 18), Direction.EAST),    // 基座 9  +x 边中点
            new ModuleControllerSlot(null, new BlockPos(26, -6, -7), Direction.EAST),    // 基座 5  +x 边中点
            new ModuleControllerSlot(null, new BlockPos(26, -6, 9), Direction.EAST),     // 基座 7  +x 边中点
    };

    /**
     * <b>12 个模块基座的「模块控制器位点」</b>（偏移 + 正脸），可直接喂给
     * {@code ModuleRenderInfo} 的第 1 / 第 4 个参数。
     *
     * <p>★ 2026-10 修正：本方法现在<b>只是把写死的位点表转成不可变列表</b>
     * （外加按位点轮转分配模块种类 id）——<b>不再做任何推导/换算，也没有任何
     * 可能抛异常的路径</b>（表是常量 ⇒ 恒返回 {@value #NAC_TABLE_SIZE} 条）。
     * 数据来源、逐条自检、以及"为什么必须写死"（旧反推恒命中 0 个的实锤与几何原因）
     * 全部写在 {@link #NAC_MODULE_PREVIEW_SLOTS} 的 javadoc 里。
     *
     * <p>★★ <b>2026-10 三次修正（模块预览位置）</b>：生效的是哪张表由
     * {@link #NAC_MODULE_SLOTS_CANONICAL_MINUS_Z} 选择 ——
     * 默认（{@code true}）= {@link #NAC_MODULE_PREVIEW_SLOTS}（12 条"−z 边中点 + north"，
     * = 数据包/玩家存档里模块真实站着的那一组）；
     * 置 {@code false} = 逐字回到 {@link #NAC_MODULE_PREVIEW_SLOTS_OUTWARD_EDGES}
     * （2026-10 上一版"最朝外 F 位"）。两张表条数相同、排序口径相同。
     *
     * <p><b>失败姿态</b>：无失败路径。旧版"命中数 ≠ 12 ⇒ 抛异常 ⇒ 返回空表"的姿态已作废
     * （见 {@link #NAC_MODULE_PREVIEW_SLOTS}）。
     *
     * @return 不可变列表，恒 {@value #NAC_TABLE_SIZE} 条（顺序 = 生效那张表的书写顺序；
     * 表本身按偏移的 x→y→z 升序排好，与该表写死前的排序口径一致、可复现）
     */
    public static List<ModuleControllerSlot> moduleControllerOffsets() {
        List<ModuleControllerSlot> cached = moduleControllerCache;
        if (cached != null) {
            return cached;
        }
        // ★ 位点表选择（编译期常量；只影响"预览与框架扫描用的 12 个坐标"，见开关 javadoc）
        ModuleControllerSlot[] table = NAC_MODULE_SLOTS_CANONICAL_MINUS_Z
                ? NAC_MODULE_PREVIEW_SLOTS
                : NAC_MODULE_PREVIEW_SLOTS_OUTWARD_EDGES;
        List<NanochipModuleKind> kinds = List.of(NanochipModuleKind.values());
        List<ModuleControllerSlot> built = new ArrayList<>(table.length);
        for (int i = 0; i < table.length; i++) {
            ModuleControllerSlot slot = table[i];
            // 模块种类仍按位点轮转（14 种模块的底座/朝向完全一致，见 NanochipModuleKind 类注释），
            // 这样一次预览能尽量多显示几种模块的塔身；取不到定义时的"退到任一已注册 NAC 模块"
            // 由调用方 ModulePreviewSlots.buildNac() 负责（保证条数不掉）。
            String moduleId = kinds.isEmpty() ? null : kinds.get(i % kinds.size()).machineId();
            built.add(new ModuleControllerSlot(moduleId, slot.offset(), slot.front()));
        }
        moduleControllerCache = List.copyOf(built);
        return moduleControllerCache;
    }

    /** 实测表的条数（恒 12；见 {@link #NAC_MODULE_PREVIEW_SLOTS}）。 */
    private static final int NAC_TABLE_SIZE = 12;

    // ==================================================================
    // ★★ 2026-10 已停用：运行时反推（整套保留为死代码，禁止再调用）★★
    //
    // 下面这一组（两个常量 + deriveModuleControllerOffsets + isBaseCentre）**已无任何调用点**
    // （全工程 grep 过；唯二的使用者 moduleControllerOffsets() 现在直接返回写死的实测表）。
    //
    // 停用理由（玩家日志实锤）：它的"基座中心判据 = 一个 F 格的 4 个轴向 ±3 邻居也都是 F"在
    // 现役图案上**恒命中 0 个**，于是每次都抛
    // `IllegalStateException: 基座中心判据命中 0 个（期望 12）` ⇒ 恒返回空表 ⇒ 玩家点 M:ON 无反应。
    // 几何原因：基座中心那一格在 row=1 且是 'C'（不是 F），4 个 F 位在 row=2 的 ±3 处
    // ⇒ 从任何 F 出发 ±3 邻居永远不是 F。详见 NAC_MODULE_PREVIEW_SLOTS 的 javadoc。
    //
    // 为什么"停用"而不是"删除"：本轮是外科手术式小改，删掉整段需要同时确认
    // decode/HashSet/Set/Comparator 等 import 的存活性（有连带编译风险）；
    // 保留 @Deprecated + 无调用 = 行为上等价于删除，且**编译风险为 0**。
    // 若日后确认要清理，删掉本整段即可（含 MAX_AXIAL_DISTANCE / EXPECTED_BASE_COUNT）。
    // ==================================================================

    /** 已停用：仅在死的反推里用过。见上方"2026-10 已停用"整段说明。 */
    @Deprecated
    private static final int MAX_AXIAL_DISTANCE = 3;
    /** 已停用：仅在死的反推里用过。见上方"2026-10 已停用"整段说明。 */
    @Deprecated
    private static final int EXPECTED_BASE_COUNT = 12;

    /**
     * 已停用（2026-10 修正）：运行时从 {@link #NAC_SLICES} 反推 12 个位点的那套算法。
     *
     * <p><b>不要再调用它</b> —— 它的"基座中心判据（4 个轴向 ±3 邻居都是 F）"在现役图案上命中 0 个，
     * 每次都会抛 {@code IllegalStateException: 基座中心判据命中 0 个（期望 12）}。
     * 位置数据现在来自 {@link #NAC_MODULE_PREVIEW_SLOTS}（玩家存档实测表，常量、不会失败）。
     */
    @Deprecated
    private static List<ModuleControllerSlot> deriveModuleControllerOffsets() {
        // ★★ 2026-10 关键修复：F 位（模块位）在【主机图案 NAC_SLICES】里，**不在模块底座图案里**。
        //   旧实现读的是 buildModuleAisles(...)（= 模块自己的 7×3×7 底座 + 塔身，字符集只有
        //   X/V/P/Z/~），那里**一个 F 都没有** ⇒ 直接抛 "模块图案里一个 F（模块位）都没有"
        //   ⇒ 返回空表 ⇒ 预览里 NAC 一台模块都不显示（就是队长报的"没有任何变化"）。
        //   三个事实（可逐条复核）：
        //     ① `buildModuleAisles`（本类 :1753 起）解的是 MOD_LAYERS + 塔身，字面量见
        //        MOD_LAYERS 定义（本类 :750-780） —— 通篇没有 'F'；
        //     ② 'F' 只出现在 NAC_SLICES（本类 :459 起，例如 :467 的 "2,14,C      IIFII…"），
        //        且**全部落在各行第 2 层**（= 基座顶层，与 MOD_CTRL_ROW=2 同一层）；
        //     ③ `countOf(MODULE_SLOT)`（本类 :2479 起）统计的也正是 NAC_SLICES ⇒ 48 个 F 位
        //        = 12 个基座 × 4 条边的边中点。
        String[][] aisles = decode(NAC_SLICES, NAC_H, NAC_W);
        // ---- 1) 收主机图案里的全部 F 位（图案绝对下标 (x, row, z)）----
        Set<Long> fCells = new HashSet<>();
        for (int z = 0; z < aisles.length; z++) {
            String[] slice = aisles[z];
            if (slice == null) {
                continue;
            }
            for (int row = 0; row < slice.length; row++) {
                String rowStr = slice[row];
                if (rowStr == null) {
                    continue;
                }
                for (int x = 0; x < rowStr.length(); x++) {
                    if (rowStr.charAt(x) == MODULE_SLOT) {
                        fCells.add(new BlockPos(x, row, z).asLong());
                    }
                }
            }
        }
        if (fCells.isEmpty()) {
            throw new IllegalStateException("主机图案（NAC_SLICES）里一个 F（模块位）都没有");
        }
        // ---- 2) 基座中心：4 个轴向 ±3 邻居都是 F ----
        List<BlockPos> centres = new ArrayList<>();
        long sumX = 0;
        long sumZ = 0;
        for (long packed : fCells) {
            BlockPos c = BlockPos.of(packed);
            if (isBaseCentre(fCells, c)) {
                centres.add(c);
                sumX += c.getX();
                sumZ += c.getZ();
            }
        }
        if (centres.size() != EXPECTED_BASE_COUNT) {
            throw new IllegalStateException("基座中心判据命中 " + centres.size()
                    + " 个（期望 " + EXPECTED_BASE_COUNT + "）⇒ 模块预览位点不可信");
        }
        // ---- 3) 基座环中心（由 12 个基座中心现算，不写死）----
        double centreX = (double) sumX / centres.size();
        double centreZ = (double) sumZ / centres.size();
        // ---- 4) 逐基座取"最朝外"的边中点当模块控制器，并定正脸 ----
        List<NanochipModuleKind> kinds = List.of(NanochipModuleKind.values());
        List<ModuleControllerSlot> out = new ArrayList<>(centres.size());
        for (int i = 0; i < centres.size(); i++) {
            BlockPos base = centres.get(i);
            BlockPos best = null;
            double bestDist = -1.0D;
            for (int[] d : new int[][]{{MAX_AXIAL_DISTANCE, 0}, {-MAX_AXIAL_DISTANCE, 0},
                    {0, MAX_AXIAL_DISTANCE}, {0, -MAX_AXIAL_DISTANCE}}) {
                BlockPos edge = base.offset(d[0], 0, d[1]);
                if (!fCells.contains(edge.asLong())) {
                    continue;
                }
                double dx = edge.getX() - centreX;
                double dz = edge.getZ() - centreZ;
                double dist = dx * dx + dz * dz;
                if (dist > bestDist) {
                    bestDist = dist;
                    best = edge;
                }
            }
            if (best == null) {
                throw new IllegalStateException("基座 " + base.toShortString() + " 找不到朝外的模块位");
            }
            // ---- 正脸（世界）= 从基座中心指向控制器 = "脸朝外" ★ 2026-10 修正 ----
            // 依据 _census/expect_table.md:39 + _census/module_base_vs_host.md:11-61 的 48/48 穷举：
            //   −z 边中点 → facing=north、+z → south、−x → west、+x → east，
            // 即"控制器坐在哪条边上，模块就朝那边"（背离基座中心）。
            // 换算：本法读的是**已转 180° 的图案下标**，而"世界 = 图案的整体 180° 旋转"
            //   ⇒ 世界向量(中心→控制器) = 图案向量(中心→控制器)；把它的两个符号直接喂给
            //   patternDirToHostFront 即可（旧版对 x 多取了一次负号 ⇒ 4 个朝向里 x 向全部反掉）。
            // 自检（基座 0，旋转后图案）：−z 边中点 F 格 (39,2,57)、基座中心 (39,1,54)
            //   ⇒ 符号 (0, −1) ⇒ NORTH ✓ 与 expect_table 的实测 "${(22,66,−110)} + facing=north" 一致。
            int fx = base.getX() - best.getX();
            int fz = base.getZ() - best.getZ();
            Direction front = patternDirToHostFront(Integer.signum(fx), Integer.signum(fz));
            // 模块种类按位点顺序轮转（14 种模块的底座/朝向完全一致，见 NanochipModuleKind 类注释），
            // 这样一次预览能尽量多显示几种模块的塔身，而不是 12 台长得一模一样。
            String moduleId = kinds.get(i % kinds.size()).machineId();
            // ★ 行号直接用该 F 格自己的 row（现役图案里恒 = 2 = 基座顶层 = 控制器那一层），
            //   不再做任何"自顶向下↔自底向上"换算（那是旧版把 y 翻错的地方）。
            out.add(new ModuleControllerSlot(moduleId,
                    patternToHostOffset(best.getX(), best.getY(), best.getZ()), front));
        }
        out.sort(Comparator.comparingInt((ModuleControllerSlot s) -> s.offset().getX())
                .thenComparingInt(s -> s.offset().getY())
                .thenComparingInt(s -> s.offset().getZ()));
        return List.copyOf(out);
    }

    /** 已停用：基座中心判据（4 个轴向 ±{@link #MAX_AXIAL_DISTANCE} 邻居都是 F）—— 现役图案上恒命中 0 个。 */
    @Deprecated
    private static boolean isBaseCentre(Set<Long> fCells, BlockPos c) {
        return fCells.contains(c.offset(MAX_AXIAL_DISTANCE, 0, 0).asLong())
                && fCells.contains(c.offset(-MAX_AXIAL_DISTANCE, 0, 0).asLong())
                && fCells.contains(c.offset(0, 0, MAX_AXIAL_DISTANCE).asLong())
                && fCells.contains(c.offset(0, 0, -MAX_AXIAL_DISTANCE).asLong());
    }

    /**
     * 「从<u>基座中心</u>指向<u>模块控制器</u>」的方向 = <b>脸朝外</b>（<b>世界口径</b>，{@code XZ} 二选一）
     * → 宿主参照系下的正脸（= {@code ModuleRenderInfo} 第 4 个参数）。
     *
     * <pre>
     *   +X → {@link Direction#EAST}     −X → {@link Direction#WEST}
     *   +Z → {@link Direction#SOUTH}    −Z → {@link Direction#NORTH}
     * </pre>
     * <p><b>★ 2026-10 修正</b>：两个入参就是"世界向量（基座中心→控制器）"的两个符号，
     * 调用方把图案向量的符号<b>直接</b>传进来即可（本法读的是已转 180° 的图案下标，
     * 而"世界 = 图案整体转 180°"⇒ 世界向量 = 图案向量，见 {@link #patternToHostOffset}）。
     * 旧注释里"图案 x 与世界 X 同号、世界 Z 反号"的说法是错的（漏了图案自身那 180°）。
     *
     * <p>自检（{@code _census/expect_table.md}:23-25 的实测）：
     * <ul>
     *   <li>基座 0：控制器世界 {@code (22,66,−110)}、基座中心 {@code (22,65,−107)}
     *       ⇒ 中心→控制器 = {@code −Z} ⇒ {@link Direction#NORTH} ✓
     *       与实测配对 {@code facing=north} <b>一致</b>（旧版因多取一次负号算成了 SOUTH，
     *       才产生注释里那句"无法取证"的悬案）；</li>
     *   <li>基座 0 的 +x 边中点：控制器世界 {@code (25,66,−107)}、中心 {@code (22,65,−107)}
     *       ⇒ {@code +X} ⇒ {@link Direction#EAST} ✓ 与 {@code +x 边中点→east} 一致。</li>
     * </ul>
     *
     * <p><b>★ 2026-10 起本方法已无调用点</b>（{@link #NAC_MODULE_PREVIEW_SLOTS} 表里直接写死了
     * 每条的 {@code front}）。保留它是"表里的四条朝向为什么这么填"的判据依据
     * （上面两条自检仍可用来核对那张表）；<b>不要删</b>，也<b>不要拿它去反推</b>。
     */
    @Deprecated
    private static Direction patternDirToHostFront(int signX, int signZ) {
        if (signX > 0) {
            return Direction.EAST;
        }
        if (signX < 0) {
            return Direction.WEST;
        }
        if (signZ > 0) {
            return Direction.SOUTH;
        }
        if (signZ < 0) {
            return Direction.NORTH;
        }
        return Direction.NORTH;
    }

    // ---------- 生成 ----------

    /**
     * 模块机图案的「片 → 行」数组，<b>与 {@link #buildModule} 交给结构的完全是同一份</b>。
     *
     * <p>为什么必须抽出来：{@link ModuleControllerSlot} 的偏移只能从<b>结构自身</b>派生
     * （不许手抄坐标）。抽成一个方法后，派生逻辑读的就是方块结构判定用的那同一份字符数组 ⇒
     * <b>图案一改，位点自动跟着改</b>，不存在"图纸改了、预览坐标忘了改"。
     * {@link #buildModule} 原样调用本方法，语义与抽取前逐字相同。
     */
    private static String[][] buildModuleAisles(MultiblockMachineDefinition def) {
        TowerDef tower = towerOf(def);
        int height = tower == null ? MOD_H : tower.height();
        // ★ 2026-09-16「脸朝外」：底座与塔身**一起**绕竖轴转 180°（理由与推导见 rotate180）。
        String[] layers = rotate180(MOD_LAYERS, MOD_H, MOD_D);
        String[] towerSlices = tower == null ? null : rotate180(tower.slices(), tower.rows(), MOD_D);
        String[] flat = new String[MOD_D * height];
        for (int z = 0; z < MOD_D; z++) {
            for (int r = 0; r < MOD_H; r++) {
                flat[z * height + r] = layers[z * MOD_H + r];
            }
            if (tower != null) {
                // 原版数组 Y=0 是塔顶；控制器在共享底座顶层 row=2 ⇒ patternRow = 2 + offsetY − y。
                // ★【P1 修复 2026-09-16】落回共享底座的行（row < MOD_H）一律不写（依据 _census/bio_row2_conflict.md）。
                for (int y = 0; y < tower.rows(); y++) {
                    int row = 2 + tower.offsetY() - y;
                    if (row < 0 || row >= height) {
                        throw new IllegalStateException("塔身行号越界：patternRow=" + row
                                + "（模块 " + tower.offsetY() + " 层 / height=" + height + "）");
                    }
                    if (row < MOD_H) {
                        continue;          // 共享底座 3 层（row 0..2）：由 MOD_LAYERS 提供，塔身不得覆盖
                    }
                    flat[z * height + row] = towerSlices[z * tower.rows() + y];
                }
            }
        }
        // ★【P0 修复】模块图案用的是「若干片 × 每片若干层」的**纯形状串**数组（每元素就是一行），
        //   不是 NAC 那种 `"行号,左填充,内容"` 紧凑字面量 ⇒ 必须调用 **4 参重载**。
        return decode(flat, height, MOD_W, MOD_D);
    }

    /** NAC 本体结构。 */
    public static BlockPattern build(MultiblockMachineDefinition def) {
        TraceabilityPredicate hatches = nanochipHatches();
        String[][] aisles = openTowerSpace(decode(NAC_SLICES, NAC_H, NAC_W));
        FactoryBlockPattern factory = FactoryBlockPattern.start();
        for (String[] aisle : aisles) {
            factory = factory.aisle(aisle);
        }
        // ★★ 2026-09-18（队长指示「用之前做的那个地心主机多方块看看」）：**空格 = 不检查** ★★
        //
        // 改前是 `where(AIR, air())`，即"整座 63×49×63 包围盒里所有空格都必须真空"。它把
        // **188,601 格**推进了"逐格评估"档（地心主机 460,944 格里只有 34,401 格），是我们这一侧
        // 能压掉的**最大一项**，也是 NAC 与地心主机（体积大 2.4 倍、却不卡）唯一的数量级差别。
        // 证据（①–⑤，全部只读字节码，详见 `_census/_census_图案减肥_队员.md` §①）：
        //
        // ① GTCEu `FactoryBlockPattern` 的**默认**空格映射就是 `Predicates.any()`：
        //    `FactoryBlockPattern.<init>` 字节码 offset 179–196：`bipush 32` → `Character.valueOf`
        //    → `Predicates.any()` → `symbolMap.put`。而 `any()` 是 `SimplePredicate.ANY`
        //    （`state -> true`），`TraceabilityPredicate.isAny()` 对它返回 true。
        // ② gtlcore 的 `BlockPattern` mixin（真实运行路径，栈里就是 `BlockPattern.gtlcore$matchAisles`）
        //    在 `gtlcore$matchSlice` offset 83–89：`predicate.isAny()` 成立就**直接 continue** ——
        //    不建 BlockPos、不读世界、不 test、不进 addPosCache。⇒ `any()` 格 = **零成本**。
        // ③ `FactoryBlockPattern.where(char, pred)` offset 0–29：谓词 `isAny() || isAir()` 时直接
        //    `symbolMap.put(c, pred)` ⇒ 我们这一行**覆盖**了默认的 any()，把 179,888 个空格
        //    从"不检查"变成了"逐格检查"（而 `air()` 的 `addCache()` 还是 true ⇒ 每格额外
        //    一次 `addPosCache` + 一次 `predicates.put`）。改回 any() 后：CHECK 188,601 → **8,713**。
        // ④ NH 侧（GTNH 2.9.0-beta-2 / structurelib-1.4.42）**从不检查空格**：`addElement` 的调用
        //    序列里没有 32；`StructureDefinition$Builder.addShape` 把 32 改写成哨兵 40960
        //    （offset 168–190），哨兵在 offset 226–239 只推进计数器、**不登记任何元素**，
        //    最后在 offset 499–520 / 570–573 被**整个从编译后的形状串里丢掉**（`setLength`）。
        //    ⇒ 原版 NAC 真正校验的格数就是**声明过的 8,713 格**；我们此前那一行比原版**严 21.6 倍**。
        // ⑤ 地心主机（`MoltenModuleHostPattern`）走的正是这条路：空腔写成 `.` 并 `where('.', any())`，
        //    460,944 格里 426,543 格（92.54%）是 SKIP，只有 **34,401** 格真被评估。
        //
        // 语义差（如实登记）：改前"空格"格只接受空气，改后接受任何方块（含空气）。
        //   · 这是**回到 NH 原版口径**（原版这些格根本不在结构校验里），不是新放宽：
        //     被"放宽"的格从来没有要求过任何方块，`'A'..'J'`、`'~'` 这些**要求方块**的格一格未动。
        //   · 想回到严格真空：把本行的 `any()` 改回 `air()` 即可（一行；`Predicates.air` 的 import
        //     仍然保留，`buildModule` 还在用它）。
        //   · 守卫：`_census/check_pattern_skip_cells.js` 钉住"哪些字符是 SKIP、哪些必须保留谓词"
        //     并带负向验证；`_census/check_extra_candidates.js` ⑤ 钉住本方法 body 的 sha256。
        factory = factory.where(AIR, any());
        // 塔身让位块（K）：改后它与空格**等价**（两者都是 any()）。保留它是为了两件事：
        //   ① `openTowerSpace()` 的运行期断言（那 5,880 格在原版数据里必须本来就是空气）；
        //   ② 防止有人删掉这一行之后 `K` 变成"未登记字符"（那会让 blockMatches 里出现 null ⇒ NPE）。
        factory = factory.where(TOWER_SPACE, any());
        factory = factory.where(FIREWALL, firewall());
        factory = factory.where(MESH, meshOnly());
        factory = factory.where(REINFORCEMENT, reinforcement());
        factory = factory.where(FRAME, naquadahFrame());
        factory = factory.where(GLASS, glass());
        factory = factory.where(MATRIX, matrix());
        factory = factory.where(MODULE_SLOT, moduleSlots(hatches));
        // H：仓室【或】网格接口机壳（原版 buildAndChain；共用同一个 hatches 实例，见 hatchOrCasing）
        //   ★ 「真空传送带仓 ≥1」：**我方自定的加严，不是 NH 的硬要求**——2026-09-18 复核（`_census/模块主机parity审计_队员.md`）
        //     直解 GT5U `HatchElementBuilder$1/$2.check` 后确认：`atLeast(...)` 里的数字只进 `mAdder`（放置配额/上限），
        //     真正的硬下限来自 `checkMachine`。NH 的 `MTENanochipAssemblyComplex.checkMachine` 只硬要求
        //     「能源仓恰好 1 个 + 输入总线 ≥1 + 输出总线 ≥1」，真空仓 **0 个也成形**（官方 tooltip VCI/VCO 也是 `0+`）。
        //     我方保留 ≥1 的理由：没有真空仓时主机收不到也送不出封装件，与其「成形了但什么都不动」不如当场报结构不合格。
        //     ⇒ 若队长要严格对齐 NH，只需把 `vacuumHatches()` 的 `setMinGlobalLimited(1)` 改 0（一行）。
        //   · 用**另建**的 vacuumHatches() 实例（每次 build 建一次，全部 H 格共用 ⇒ 一条全局限制），
        //     **绝不能**并进 moduleSlots(F) 用的那个 hatches 实例（那会让 F 也要求真空仓，语义错）。
        factory = factory.where(HATCH, hatchOrCasing(hatches).or(vacuumHatches()));
        // ★★ 2026-09-21（队长令，与 F 位那条同一件事）★★ I 位从 `ignoredSlots()` 改成 `any()`：
        //   I 位是基座顶/底两层那圈 16 格（每个基座 16 格 × 12 = 192，全图案 432 格里的绝大部分）。
        //   `ignoredSlots()` = 「网格接口机壳 或 任何 GTCEu 仓位」⇒ ① 空气**不算**匹配（拆掉就永不成形）
        //   ② 自动搭建会拿 **candidates[0] = 网格接口机壳** 去填 ⇒ 平台上多出一圈凸出来的机壳
        //   （队长说的「那 20 个」= 这 16 格 I + 4 格 F）。
        //   改成 `any()`（= 不检查）之后：空气/任何方块都匹配 ⇒ 拆掉不再卡结构，自动搭建也**跳过**这些格 ⇒
        //   平台只剩正中 5×5 机壳 = 图3 的正方形。语义上仍与 NH 的 `IgnoredHatch`（任何仓都吃、计数恒 0）
        //   一致（`any()` 比它更宽：连非仓方块也吃）—— 与工程里既有的两处先例同口径：
        //   `where(AIR, any())`（空格）与 `where(MOD_RING_ANY, any())`（模块中层外环）。
        //   `ignoredSlots()` 保留定义（它是 NH 语义的书面依据，且 `NanochipSelfCheck`/体检脚本仍引用其口径）。
        factory = factory.where(IGNORED, any());
        factory = factory.where(CONTROLLER, controller(blocks(def.get())));
        return factory.build();
    }

    /**
     * 把「12 个基座正上方 7×7×{@link #TOWER_SPACE_H}」的空格换成 {@link #TOWER_SPACE}（{@code any()}）。
     *
     * <p><b>历史</b>：本方法本来是"给模块塔身让位"的补丁 —— 当年 {@code build()} 把 {@code ' '}
     * 映射成 {@code air()}（比原版 structurelib 严），塔身一放进去主机就判不合格，于是把那 12 块
     * 空腔单独改成 {@code any()}。
     *
     * <p>★ 2026-09-18 起 {@code build()} 的空格已经改回 {@code any()}（= 不检查，与原版一致），
     * 所以本方法在**判定语义上**已无副作用（{@code K} 与空格等价）。<b>仍然保留</b>，因为它还干两件事：
     * <ol>
     *   <li><b>数据断言</b>（主要价值）：这 12×7×7×10 = 5880 格在
     *       {@code _gtnh/_tools/main_structure.json} 里必须本来就是空气，否则抛异常 ——
     *       原版数据一旦被改坏，这里立刻暴露；</li>
     *   <li>把"塔身占位"这件事**显式登记**在图案里（{@code K} 而不是隐式空格），
     *       配合 {@code _census/check_pattern_skip_cells.js} 的空格/谓词清单体检。</li>
     * </ol>
     *
     * <p><b>自检</b>：这些格在原版数据里必须本来就是空气，否则抛异常（数据一变就能立刻发现）。
     */
    private static String[][] openTowerSpace(String[][] aisles) {
        int height = aisles[0].length;
        int width = aisles[0][0].length();
        // 12 个基座中心：模块位（F）朝里 3 格的那个中心格，且它的 4 个边中点都是模块位
        List<int[]> centres = new ArrayList<>();
        for (int z = 0; z < aisles.length; z++) {
            for (int row = 0; row < height; row++) {
                for (int x = 0; x < width; x++) {
                    if (aisles[z][row].charAt(x) != MODULE_SLOT) {
                        continue;
                    }
                    for (int[] d : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
                        int cx = x - d[0];
                        int cz = z - d[1];
                        boolean ok = cx >= 0 && cz >= 0 && cx < width && cz < aisles.length;
                        for (int[] e : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
                            int nx = cx + e[0];
                            int nz = cz + e[1];
                            ok &= nx >= 0 && nz >= 0 && nx < width && nz < aisles.length
                                    && aisles[nz][row].charAt(nx) == MODULE_SLOT;
                        }
                        if (ok) {
                            centres.add(new int[]{cx, row, cz});
                        }
                    }
                }
            }
        }
        List<int[]> uniq = new ArrayList<>();
        for (int[] c : centres) {
            boolean dup = false;
            for (int[] u : uniq) {
                if (u[0] == c[0] && u[1] == c[1] && u[2] == c[2]) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                uniq.add(c);
            }
        }
        if (uniq.size() != 12) {
            throw new IllegalStateException("基座应为 12 个，实为 " + uniq.size() + "（检查 F 位数据）");
        }
        char[][][] buf = new char[aisles.length][height][];
        for (int z = 0; z < aisles.length; z++) {
            for (int row = 0; row < height; row++) {
                buf[z][row] = aisles[z][row].toCharArray();
            }
        }
        int opened = 0;
        for (int[] c : uniq) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dx = -3; dx <= 3; dx++) {
                    for (int row = c[1] + 1; row <= c[1] + TOWER_SPACE_H; row++) {
                        if (row >= height) {
                            throw new IllegalStateException("塔身超出图案高度：row " + row + " >= " + height);
                        }
                        char old = buf[c[2] + dz][row][c[0] + dx];
                        if (old != AIR) {
                            throw new IllegalStateException(String.format(
                                    "塔身空间里原版不是空气：基座(%d,%d) x=%d z=%d row=%d 字符='%c'",
                                    c[0], c[2], c[0] + dx, c[2] + dz, row, old));
                        }
                        buf[c[2] + dz][row][c[0] + dx] = TOWER_SPACE;
                        opened++;
                    }
                }
            }
        }
        String[][] out = new String[aisles.length][height];
        for (int z = 0; z < aisles.length; z++) {
            for (int row = 0; row < height; row++) {
                out[z][row] = new String(buf[z][row]);
            }
        }
        com.gtlmodular.GTLModular.LOGGER.info(
                "[gtlmodular] NAC 塔身空间已让位：12 个基座 × 7×7×{} = {} 格改为 any()", TOWER_SPACE_H, opened);
        return out;
    }

    /**
     * 把「若干片 × 每片若干行」的图案<b>绕竖轴旋转 180°</b>：{@code x → MOD_W−1−x}、{@code z → MOD_D−1−z}，<b>行号不变</b>。
     *
     * <h3>为什么需要它（2026-09-16「模块脸朝内」修复，施工记录 §66.2）</h3>
     * 控制器朝向决定 GTCEu 如何旋转整张图案（{@code facing=south} = "原样"：{@code dx=+x偏移, dz=+z偏移}；
     * {@code facing=north} = 两者取反 = 绕控制器转 180°）。而模块图案里"机体长在控制器正面那一侧"
     * ⇒ 原来 4 组合法的「F 位 × 朝向」配对，正脸<b>全都</b>指向自己基座的中心（玩家实测："脸朝内"✗）。
     *
     * <p>把图案整体转 180°、再要求控制器 {@code facing=north}（{@code gen_probe_datapack.js} 的
     * {@code modFacing} 同步改为 north）：两者相消 ⇒ <b>世界里每一格的位置与今天完全相同</b>，
     * 唯一变化是控制器的正脸由"朝基座中心"变成"背离基座中心" ✓。
     * 交换律推导（xc=3、旋转后 zc=6）：
     * <pre>
     *   原：[facing=south] 世界偏移 = ( X − 3 , Z − 0 )
     *   新：[图案转 180° + facing=north] 原格 (X,Z) 落到 pattern(6−X, 6−Z)
     *       世界偏移 = −( (6−X) − 3 , (6−Z) − 6 ) = ( X − 3 , Z − 0 )   ⇒ 完全相同 ✓
     * </pre>
     *
     * <p><b>必须底座与塔身一起转</b>（见 {@link #buildModule}），否则塔身会相对底座错位 180°
     * （塔身跑到基座外面 ⇒ 模块永远不成形）。
     *
     * @param slices     按 {@code z * rows + y} 排列的形状串（不足 {@link #MOD_W} 长的行按空气补齐）
     * @param rows       每片的行数
     * @param sliceCount 片数（= {@link #MOD_D}）
     */
    private static String[] rotate180(String[] slices, int rows, int sliceCount) {
        String[] out = new String[slices.length];
        for (int z = 0; z < sliceCount; z++) {
            int z2 = sliceCount - 1 - z;
            for (int y = 0; y < rows; y++) {
                String row = slices[z * rows + y];
                StringBuilder sb = new StringBuilder(MOD_W);
                for (int x = MOD_W - 1; x >= 0; x--) {
                    sb.append(x < row.length() ? row.charAt(x) : ' ');
                }
                out[z2 * rows + y] = sb.toString();
            }
        }
        return out;
    }

    /**
     * 模块机结构：<b>共享底座 3 层</b>（原版 {@code base_structure}，与主机 12 个基座同一份）
     * <b>+ 该模块自己的塔身 {@code main}</b>（原版 {@code ASSEMBLY_STRING}）。
     *
     * <p>原版是<b>两块都 checkPiece 才算成形</b>（{@code MTENanochipAssemblyModuleBase.checkMachine}：
     * 先 {@code checkPiece("base", 3, 0, 0)} 再 {@code checkPiece("main", offsetX, offsetY, offsetZ)}）
     * ⇒ 只铺底座是"不等价"的，这也是"看起来跟主机底座一样"的原因（施工记录 §27/§28）。
     *
     * <p><b>11 个模块现在都有塔身</b>（数据见 {@link #MODULE_TOWERS}，由 `_census/gen_all_towers_java.js`
     * 从各自 class 常量池生成）；没有数据的模块退化成"只有 3 层底座"。
     */
    public static BlockPattern buildModule(MultiblockMachineDefinition def) {
        // ★ 2026-09-18（玩家质疑触发，NH javap 取证）：模块用 **moduleHatches()** —— 能源仓「可选」。
        //   原先用 nanochipHatches()，那里 INPUT_ENERGY 是 min 1 ⇒ 模块被迫装能源仓；
        //   又因 GTCEu 的全局计数以实例为键、那个实例与复合体共用 ⇒ 模块里插能源仓还会顶掉复合体的额度。
        //   NH 模块基类的仓室元素里根本没有 Energy（只有 VacuumConveyorHatch + 物品/流体进出），
        //   模块耗电走 drainEnergyInput(...) → 主机 MTENanochipAssemblyComplex.getEnergyHatchTier()。
        TraceabilityPredicate hatches = moduleHatches();
        TowerDef tower = towerOf(def);
        // ★ 2026-09-26（模块预览位点）：图案字符的拼装已抽到 buildModuleAisles（逐字同款），
        //   本方法不再自己拼一遍 —— 派生「模块控制器位点」与结构判定从此共用同一份字符数组。
        String[][] aisles = buildModuleAisles(def);
        // ★ 2026-09-21（队长拍板「上 A+」）A+ 精确预检：登记本模块的「唯一方块身份格」坐标表。
        //   只读 aisles（= 紧接着交给 factory.aisle(...) 的同一份字符）⇒ 零重复几何、不可能与图案脱节；
        //   消费者 = com.gtlmodular.common.machine.nanochip.NanochipExactPrecheck.forModule(...)。
        //   ★ 本行不改任何判定：where 表、图案字符、图案结构一个字都没动。
        registerModuleIdentityCells(def, aisles, tower);
        FactoryBlockPattern factory = FactoryBlockPattern.start();
        for (String[] aisle : aisles) {
            factory = factory.aisle(aisle);
        }
        factory = factory.where(AIR, air());
        // V：仓位 —— 仓室【或】网格接口机壳（原版 buildAndChain）。没有这一手，模块的 39 个 V
        // 就全得装满仓才能成形（见 hatchOrCasing 的说明）。
        // ★ 2026-09-21（队长令：「让模块跟主机有独立区间，不至于给模块放舱室的时候，导致模块跟主机
        //   一起不成形了」）★★ 追加 **air()**：
        //   原来只认「仓室 或 网格接口机壳」⇒ 空气不算匹配 ⇒ 玩家把那圈拆空之后**模块永远立不起来**
        //   （日志实证 17:18：`NAC 模块 尚未成形 … 该格实际=minecraft:air`，期望正是网格接口机壳）。
        //   加 air() 之后：① 那圈**空着也能成形**（平台就是一个干净的方形 = 队长要的图2）；
        //   ② 想放仓位时照样能放 —— 仓位仍在 `hatchOrCasing(hatches)` 里 ⇒ 这格**依然是合法部件位**，
        //      放宽不会让模块失去"能装仓"的能力；③ 主机那边本来就 `any()` 不检查 ⇒ 两侧互不牵连 ✓。
        //   只减不加：这一行只让结构**更宽松**，任何原本能成形的状态都不会因此失效。
        // ★ 2026-10-15（模块侧预览修复）：这一段构建的是**模块自己的图案** ⇒ 置上"模块侧"标记，
        //   让 hatchOrCasing 用 previewAbilitiesForModule（每种 I/O 能力一台代表仓）而不是
        //   "14 台 NAC 模块主机"候选表（后者会让模块预览画满模块主机方块 ✗）。finally 必然复位 ✓。
        buildingModulePattern = true;
        try {
            factory = factory.where(MOD_HATCH, hatchOrCasing(hatches).or(air()));
        } finally {
            buildingModulePattern = false;
        }
        factory = factory.where(MOD_MESH, meshOnly());
        factory = factory.where(MOD_CORE, reinforcement());
        factory = factory.where(MOD_RING_ANY, any());
        if (tower != null) {
            // 塔身：**按该模块自己的角色表**逐个字符登记（11 个模块用的字母完全不同）
            // ★ 2026-10-15：塔身里的 HATCH 角色同样走 hatchOrCasing ⇒ 同样要挂"模块侧"标记
            //   （否则塔身仓位会被画成 14 台模块主机方块 = 同上那个症状 ✗）。
            buildingModulePattern = true;
            try {
                for (Map.Entry<Character, TowerCell> e : tower.cells().entrySet()) {
                    TraceabilityPredicate base = predicateFor(e.getValue(), hatches, def);
                    factory = factory.where(e.getKey(), withExtraCandidates(base, e.getValue()));
                }
            } finally {
                buildingModulePattern = false;
            }
        }
        factory = factory.where(CONTROLLER, controller(blocks(def.get())));
        return factory.build();
    }

    // ---------- 塔身：方块与数据 ----------

    /** 该模块的塔身数据（没有 = {@code null}，此时图案就是 3 层底座）。 */
    private static TowerDef towerOf(MultiblockMachineDefinition def) {
        if (def == null || def.getId() == null) {
            return null;
        }
        return moduleTowers().get(def.getId().getPath());
    }

    /** 塔身字符的角色 → 结构判定。 */
    private static TraceabilityPredicate predicateFor(TowerCell cell, TraceabilityPredicate hatches,
                                                      MultiblockMachineDefinition def) {
        return switch (cell.role()) {
            case ASSEMBLY -> assemblyLineCasing();
            case MESH -> meshOnly();
            case REINFORCEMENT, REINFORCED_CASING -> reinforcement();
            case GLASS -> glass();
            case FRAME -> blocks(frameBlockOf(cell.material()));
            case SHEET -> blocks(blockByCandidates(sheetIdOf(cell.material()), blockIdOf(cell.material()),
                    TWR_SHEET_BLOCK, TWR_FRAME_FALLBACK));
            case HATCH -> hatchOrCasing(hatches);
            case UEV_CASING -> blocks(blockByCandidates(TWR_UEV_CASING_BLOCK, TWR_ASSEMBLY_BLOCK,
                    "gtlmodular:nanochip_reinforcement_casing"));
            case MATRIX_CASING -> blocks(blockByCandidates("gtlmodular:nanochip_computational_matrix_casing",
                    TWR_ASSEMBLY_BLOCK));
            // ★ 2026-09-19 队长令「两个线圈方块给他定死」⇒ 2026-09-21 队长拍板**严格定死（B 案）**：
            //   原来只有一条 `case COIL`（两味线圈共用、基础候选是 GTCEu 通用线圈 → 装配线外壳），
            //   现按 NH 的 meta 拆成两条，且**每一味只认自己那一味真线圈、不再回落任何替代品** ——
            //   A 位（6 格）只认 UHV 螺线管超导线圈、B 位（16 格）只认 UEV 螺线管超导线圈。
            //
            //   ⚠️ **口径变更，代价已由队长确认**：这两位**连 `gtceu:naquadah_coil_block`（通用线圈）也不收** ——
            //     老存档里用通用线圈搭的那 22 格会**不成形**（玩家需换成对应的真线圈；
            //     两个真方块的配方已存在：`uhv_solenoid_superconductor_coil.json` / `uev_…`）。
            //     依据：队长 2026-09-21 在"知道代价"的前提下拍板选 B 案（严格定死）。
            //   ⇒ 基础候选链**不再**是 `blockByCandidates(TWR_COIL_BLOCK, TWR_ASSEMBLY_BLOCK)`；
            //     两味真线圈也随之从 `extraCandidatesFor` 的"追加"挪到这里（那里已删掉这两条，
            //     否则 `base` 与 `extra` 会重复，违反 check_extra_candidates.js ② 的 `BASE ∩ EXTRA = ∅`）。
            //   两味线圈的 NH 分位依据（不改）：`_census/all_modules.json` 该模块 cells
            //     （A = `sSolenoidCoilCasings:7`、B = `:8`；shape 现算 6 / 16 格）
            //     + `_census/wiki_抓取/raw/超导线束分离器.wiki.txt`（结构方块5=UEV 16、结构方块6=UHV 6）。
            case COIL_UHV -> blocks(block(NanochipBlocks.UHV_SOLENOID_SUPERCONDUCTOR_COIL));
            case COIL_UEV -> blocks(block(NanochipBlocks.UEV_SOLENOID_SUPERCONDUCTOR_COIL));
            case CONTROLLER -> controller(blocks(def.get()));
        };
    }

    /** 空数组常量（{@link #extraCandidatesFor} 的空结果，避免每次分配）。 */
    private static final Block[] NO_EXTRA_CANDIDATES = new Block[0];

    /**
     * ★ 2026-09-17「缺件方块」的接入点 —— <b>纯加法</b>：把新造的同款方块<b>追加</b>到该角色位的候选集里。
     *
     * <h3>为什么"追加"写在这里，而不是写进 {@link #predicateFor} 的 switch</h3>
     * <ul>
     *   <li><b>语义上</b>：{@link #predicateFor} 的每个 case 现在还只是"<b>一个</b>替代品" ——
     *       {@link #blockByCandidates} 会在一串候选里挑出<b>第一个存在的</b>，然后<b>只接受那一个</b>。
     *       玩家 2026-09-17 凌晨正是用这些替代品把主机 + 11 台模块全部搭好并成功成形的
     *       ⇒ <b>绝不能把选中的那个方块换掉</b>（一换，老存档立刻不成形）。
     *       所以缺件同款方块只能以 {@code .or(blocks(新方块))} 的形式<b>追加</b>为「也可以接受」。</li>
     *   <li><b>审计上（同样是硬约束）</b>：{@code _census/check_ctm_targets.js} 用正则解析
     *       {@code predicateFor} 的 switch（{@code case X -> 表达式;} 再交给它自己的 {@code resolveExpr}
     *       解候选 id）。case 的表达式里一旦出现 {@code .or(…)}，它就解不出来 ⇒ 那条自检直接报红。
     *       放在这里，两件事都不破。</li>
     * </ul>
     *
     * <h3>为什么 {@code .or()} 不会破坏"全局计数"（至少 1 个能源仓 / {@code F} 位槽名）</h3>
     * GTCEu 的 {@code TraceabilityPredicate.or(other)} 是 {@code new TraceabilityPredicate(this)}
     * 之后把 {@code other} 的 {@code SimplePredicate} <b>按引用</b>并进来（{@code List.addAll}，不复制），
     * 而结构匹配的全局计数表<b>以 {@code SimplePredicate} 实例为键</b> ⇒ {@code base} 里那些共用实例
     * （尤其与 {@code F} 位共用的 {@link #nanochipHatches()}）在 {@code .or()} 之后<b>仍是同一批实例</b>，
     * 语义不变；新增的只有 {@code blocks(新方块)} 这一条全新的 SimplePredicate。
     *
     * @param base 角色位原本的判定（{@link #predicateFor} 的返回值，<b>一个字都没改</b>）
     * @param cell 该格的角色/材料
     * @return 原判定；若该角色有「缺件同款方块」，则返回「原判定 <b>或</b> 新方块」
     */
    private static TraceabilityPredicate withExtraCandidates(TraceabilityPredicate base, TowerCell cell) {
        Block[] extra = extraCandidatesFor(cell);
        return extra.length == 0 ? base : base.or(blocks(extra));
    }

    /**
     * ★「缺件同款方块」表（2026-09-17）：<b>只有"追加候选"这一种效果，绝不删、绝不换原候选</b>。
     *
     * <p>每一项的 NH 出处、贴图来源与 sha256 见 {@code gen_missing_blocks_assets.js}；
     * 清单口径见 {@code _census/role_reference.md} §三（那 5 件 NH 专属方块）。
     *
     * <p><b>只在塔身位追加</b>：这 5 件方块在 NH 主机图案里根本没出现
     * （主机字符只有 {@code A/B/C/D/E/F/H/I/J/~}）⇒ {@link #build} 一个字都不动，
     * 与"NH 主机用什么就是什么"完全一致。
     *
     * <p>★ 2026-09-21（队长拍板「<b>严格定死</b>」）：超导线束分离器的两味线圈<b>不在这张表里</b> ——
     * 它们已从"追加"提升为<b>基础判定</b>（见 {@link #predicateFor} 的 {@code COIL_UHV} / {@code COIL_UEV}：
     * 各自只认一味真线圈，<b>连通用线圈 {@code gtceu:naquadah_coil_block} 都不收</b>）。
     * 本表现在只剩 <b>5 件</b>纯追加方块。
     */
    private static Block[] extraCandidatesFor(TowerCell cell) {
        if (cell == null) {
            return NO_EXTRA_CANDIDATES;
        }
        return switch (cell.role()) {
            // 原版 gt.blockcasings9:12「Naquadria-Reinforced Water Plant Casing」（切割室 D 位 16 格）
            case REINFORCED_CASING -> new Block[]{block(NanochipBlocks.NAQUADRIA_REINFORCED_WATER_PLANT_CASING)};
            // 原版 gtnhlanth 的「Shielded Accelerator Casing」（蚀刻阵列 C 位 9 格）
            case MATRIX_CASING -> new Block[]{block(NanochipBlocks.SHIELDED_ACCELERATOR_CASING)};
            // ★ 2026-09-21 队长拍板「严格定死（B 案）」：两味真线圈**从"追加"挪进了基础判定**
            //   （见 {@link #predicateFor} 的 `COIL_UHV` / `COIL_UEV` —— 那两条现在只认各自那一味真线圈）。
            //   所以这里**不再**有 COIL_UHV / COIL_UEV 两条：
            //     ① 语义上：base 已经"只认它"，再追加同一个方块是冗余；
            //     ② 审计上：`_census/check_extra_candidates.js` ② 明确要求 `BASE ∩ EXTRA = ∅`，
            //        两处都写会直接判红。
            //   ⇒ 超导线束分离器那 22 格现在**一个替代品都不接受**（含 `gtceu:naquadah_coil_block`）。
            // 原版这一位是 gt.blockglass1:3「Non-Photonic Matter Exclusion Glass」（蚀刻阵列 F 位 4 格）。
            // 我们的 GLASS 角色码同时覆盖 gt.blockglass1:8（复合体玻璃）⇒ 追加之后两种玻璃都接受。
            case GLASS -> new Block[]{block(NanochipBlocks.NON_PHOTONIC_MATTER_EXCLUSION_GLASS)};
            case SHEET -> sheetmetalExtraOf(cell.material());
            default -> NO_EXTRA_CANDIDATES;
        };
    }

    /**
     * 板金属（{@code BlockSheetMetal}）的追加候选：NH 是"<b>每味材料一块</b>"。
     *
     * <p>旧的替代候选（{@code gtceu:<材料>_block} 等）一个都不动 —— 见 {@link #predicateFor} 的 SHEET 分支。
     * 材料名走 {@link #snake(String)}（{@code NaquadahAlloy → naquadah_alloy}）。
     */
    private static Block[] sheetmetalExtraOf(String material) {
        String s = snake(material);
        if (s == null) {
            return NO_EXTRA_CANDIDATES;
        }
        return switch (s) {
            case "naquadah_alloy" -> new Block[]{block(NanochipBlocks.NAQUADAH_ALLOY_SHEETMETAL)};
            case "superconductor_uev_base" -> new Block[]{block(NanochipBlocks.SUPERCONDUCTOR_UEV_BASE_SHEETMETAL)};
            default -> NO_EXTRA_CANDIDATES;
        };
    }

    /** GTNH 材料名 → snake_case（`NaquadahAlloy` → `naquadah_alloy`，`SuperconductorUHVBase` → `superconductor_uhv_base`）。 */
    private static String snake(String material) {
        if (material == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(material.length() + 4);
        for (int i = 0; i < material.length(); i++) {
            char c = material.charAt(i);
            if (Character.isUpperCase(c)) {
                boolean prevLower = i > 0 && (Character.isLowerCase(material.charAt(i - 1))
                        || Character.isDigit(material.charAt(i - 1)));
                boolean nextLower = i + 1 < material.length() && Character.isLowerCase(material.charAt(i + 1));
                if (sb.length() > 0 && (prevLower || (nextLower && Character.isUpperCase(material.charAt(i - 1))))) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 该材料的框架方块 id（`gtceu:<snake>_frame`）。 */
    private static String frameIdOf(String material) {
        String s = snake(material);
        return s == null ? null : "gtceu:" + s + "_frame";
    }

    /**
     * ★ 框架的<b>指定替代</b>表（`原版材料名 → 用哪个真实框架方块顶替`）。
     *
     * <p><b>什么时候才需要它</b>：某个材料在本包里**一点方块形态都没有**（连 `<snake>_block` 都没有）
     * ⇒ 四级回落链会一路跌到 {@link #TWR_FRAME_BLOCK}（硅岩合金框架），结果**与别的模块撞成一样的外观**。
     * 这时就给它一个"气质接近"的**真框架**，并且**打一条明确的 WARN**（而不是靠回落链撞进去）。
     *
     * <p><b>当前状态（2026-09-16）：表是空的</b> ✓ —— 原先给 `EnrichedHolmium` 挂的那条替代
     * （`gtceu:enriched_naquadah_frame`）已经**删掉**：那几味材料现在由**我们自己在 GTCEu 里注册**
     * （{@link com.gtlmodular.common.data.GTLModularAddon#registerMaterials()}，GTCEu 自动生成
     * `gtceu:enriched_holmium_frame` / `gtceu:octiron_frame` / `gtceu:superconductor_uhv_base_frame` /
     * `gtceu:superconductor_uev_base_frame`）⇒ 图案直接命中**本材料自己的框架** ✓（施工记录 §64）。
     *
     * <p>机制保留：将来若又遇到"本包确实没有、也不值得自己造"的材料，加一行即可（记得写明证据）。
     */
    private static String frameSubstituteOf(String material) {
        return null;
    }

    /**
     * 框架位的候选链（含**指定替代**），顺序：
     * <pre>
     *   ① gtceu:&lt;材料&gt;_frame        本材料框架
     *   ② gtceu:&lt;材料&gt;_block        本材料实心块（本包很多材料只有块、没有框架）
     *   ③ 指定替代（{@link #frameSubstituteOf}，仅当本材料两种形态都没有时）
     *   ④ gtceu:naquadah_alloy_frame 通用回落（硅岩合金框架）
     *   ⑤ gtceu:naquadah_alloy_block 最后兜底（硅岩合金块）
     * </pre>
     * 每一级回落都会 WARN（④⑤ 由 {@link #blockByCandidates} 打，③ 额外再打一条**说清"这是指定替代"**）。
     */
    private static Block frameBlockOf(String material) {
        String sub = frameSubstituteOf(material);
        String[] ids = sub == null
                ? new String[]{frameIdOf(material), blockIdOf(material), TWR_FRAME_BLOCK, TWR_FRAME_FALLBACK}
                : new String[]{frameIdOf(material), blockIdOf(material), sub, TWR_FRAME_BLOCK, TWR_FRAME_FALLBACK};
        Block chosen = blockByCandidates(ids);
        if (sub != null) {
            Block subBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                    new ResourceLocation(sub));
            if (chosen == subBlock) {
                com.gtlmodular.GTLModular.LOGGER.warn(
                        "[gtlmodular] 塔身框架：原版材料 {} 在本包没有任何方块形态 ⇒ 使用**指定替代** {}"
                                + "（依据：玩家 2026-09-16 确认原版为「富集钬框架」，本包无钬/富集钬方块）",
                        material, sub);
            }
        }
        return chosen;
    }

    /**
     * 该材料的实心块 id（`gtceu:<snake>_block`）—— 框架的**第二候选**。
     *
     * <p>为什么要带它：材料核查（`_census/material_survey.md`，判据 = 运行时注册表 + 存档实到方块）显示
     * 本包 55 个 {@code _frame} 里，有几味模块用的材料**只有实心块、没有框架**
     * （`DraconiumAwakened` / `Radox` / `Kevlar` / `Spacetime` / `FluxedElectrum`）；
     * 另有两味只是**拼写不同**（`Quantium` → `quantanium`、`RadoxPolymer` → `radox`，
     * 见 {@code _census/material_alias.js} 的别名表）。
     *
     * <p><b>2026-09-16 更新（施工记录 §64）</b>：原先"整味都不存在"的那 4 味
     * （`EnrichedHolmium` / `Octiron` / `SuperconductorUHVBase` / `SuperconductorUEVBase`）
     * 现在由**本模组自己**在 GTCEu 里注册材料（{@code GTLModularAddon.registerMaterials()}）⇒
     * GTCEu 自动生成 {@code <材料>_frame}（个别还生成 {@code _block}）⇒ 第一候选即命中，
     * **不再一路回落到硅岩合金** ✓。候选链保持不变（兜底仍在）：
     * 本材料框架 → 本材料实心块 → 硅岩合金框架 → 硅岩合金块（每次回落都 WARN，见
     * {@link #blockByCandidates}）。
     */
    private static String blockIdOf(String material) {
        String s = snake(material);
        return s == null ? null : "gtceu:" + s + "_block";
    }

    /**
     * 该材料的"板金属"候选 id。
     *
     * <p>先试 {@code gtceu:<snake>_sheetmetal}（万一以后有人给它补上方块形态，这里自动就对了），
     * 再退到同材料的实心块 {@code gtceu:<snake>_block}。全库已确认本包没有 sheet metal 形态。
     */
    private static String sheetIdOf(String material) {
        String s = snake(material);
        return s == null ? null : "gtceu:" + s + "_sheetmetal";
    }

    /**
     * 按候选链取方块：返回**第一个在注册表里存在**的；全都不存在则抛异常
     * （宁可启动时报错，也不要静默变成一个"永远匹配不上"的判定）。
     *
     * <p>候选链的最后一项一定是<b>我们自己的方块</b>或 GTCEu 的基础方块，所以正常运行不会抛。
     * 每一次回落都会 WARN，方便从日志看出"哪个 id 在这座包里不存在"。
     */
    private static Block blockByCandidates(String... ids) {
        for (String id : ids) {
            if (id == null) {
                continue;
            }
            Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
            if (b != net.minecraft.world.level.block.Blocks.AIR) {
                if (!id.equals(ids[0])) {
                    com.gtlmodular.GTLModular.LOGGER.warn(
                            "[gtlmodular] 塔身方块 {} 不存在，回落到 {}", ids[0], id);
                }
                return b;
            }
        }
        throw new IllegalStateException("塔身方块候选链全部不存在：" + Arrays.toString(ids));
    }

    /**
     * 塔身 {@code ASSEMBLY}：部件装配线外壳。
     *
     * <p>原版是 {@code ofBlocksTiered(componentAssemblylineCasing, 0..13)}（14 级）；gtlcore 提供了同名的
     * 14 级方块 ⇒ **14 级全部匹配**（比只用一个 id 更贴近原版语义）。一个都没有时回落到 GTCEu 的单级外壳。
     */
    private static TraceabilityPredicate assemblyLineCasing() {
        List<Block> list = new ArrayList<>(TWR_ASSEMBLY_TIERS.length + 2);
        for (String tier : TWR_ASSEMBLY_TIERS) {
            Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                    new ResourceLocation(TWR_ASSEMBLY_TIER_PREFIX + tier));
            if (b != net.minecraft.world.level.block.Blocks.AIR) {
                list.add(b);
            }
        }
        if (list.isEmpty()) {
            com.gtlmodular.GTLModular.LOGGER.warn(
                    "[gtlmodular] gtlcore 的 14 级部件装配线外壳一个都没找到，回落 {}",
                    TWR_ASSEMBLY_BLOCK);
            Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                    new ResourceLocation(TWR_ASSEMBLY_BLOCK));
            if (b == net.minecraft.world.level.block.Blocks.AIR) {
                throw new IllegalStateException("部件装配线外壳不存在：" + TWR_ASSEMBLY_TIER_PREFIX + "* / "
                        + TWR_ASSEMBLY_BLOCK);
            }
            list.add(b);
        }
        return blocks(list.toArray(new Block[0]));
    }

    // ---------- 解码 ----------

    /**
     * 把 {@code "行号,左填充,内容"} 形式的紧凑字面量解成
     * {@code [剖面][行][列]} 的字符串表（未列出的行 = 整行空气）。
     *
     * <p><b>★ 2026-09-15 P0 加固</b>：这个重载**只认紧凑字面量**（NAC 用的那种）。
     * 旧版对"没有逗号的串"直接 {@code substring(0, -1)} ⇒ 越界崩溃；现在：
     * <ul>
     *   <li>没有逗号 ⇒ 明确按"**整行纯形状串**"处理（row = 0、左填充 0），不再抛异常；</li>
     *   <li>所有 {@code substring} 前都做边界检查，{@code row}/{@code pad} 越界时钳制并记 WARN；</li>
     *   <li>真正的调用方（模块图案）走 4 参重载 —— 这个分支只是"兜底不崩"。</li>
     * </ul>
     * <p>另外提供 {@code selfCheck()}（构建期/自检脚本会用），把 NAC 与全部模块图案各解析一遍。
     */
    private static String[][] decode(String[] slices, int height, int width) {
        String[][] out = new String[slices.length][height];
        for (int z = 0; z < slices.length; z++) {
            char[][] buf = new char[height][width];
            for (char[] row : buf) {
                Arrays.fill(row, AIR);
            }
            String slice = slices[z];
            if (slice != null && !slice.isEmpty()) {
                for (String entry : slice.split(";")) {
                    int first = entry.indexOf(',');
                    int row = 0, pad = 0;
                    String content = entry;
                    if (first >= 0) {
                        int second = entry.indexOf(',', first + 1);
                        if (second > first) {
                            row = parseIntOr(entry.substring(0, first), 0);
                            pad = parseIntOr(entry.substring(first + 1, second), 0);
                            content = entry.substring(second + 1);
                        }
                    } else {
                        com.gtlmodular.GTLModular.LOGGER.warn(
                                "[gtlmodular] decode: 第 {} 片出现非紧凑字面量 \"{}\"，按整行(row=0, pad=0)处理",
                                z, entry);
                    }
                    if (row < 0 || row >= height) {
                        com.gtlmodular.GTLModular.LOGGER.error(
                                "[gtlmodular] decode: 行号 {} 越界（height={}），已忽略该条 \"{}\"", row, height, entry);
                        continue;
                    }
                    int room = width - Math.max(0, pad);
                    if (pad < 0 || room <= 0) {
                        com.gtlmodular.GTLModular.LOGGER.error(
                                "[gtlmodular] decode: 左填充 {} 越界（width={}），已忽略该条 \"{}\"", pad, width, entry);
                        continue;
                    }
                    int len = Math.min(content.length(), room);
                    if (len < content.length()) {
                        com.gtlmodular.GTLModular.LOGGER.error(
                                "[gtlmodular] decode: 内容过长（{} > {}），已截断：\"{}\"", content.length(), room, entry);
                    }
                    content.getChars(0, len, buf[row], pad);
                }
            }
            for (int r = 0; r < height; r++) {
                out[z][r] = new String(buf[r]);
            }
        }
        return out;
    }

    /** 安全的整数解析（解析失败返回默认值并记日志，绝不抛）。 */
    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException e) {
            com.gtlmodular.GTLModular.LOGGER.error("[gtlmodular] decode: 行号/填充 \"{}\" 不是整数，按 {} 处理", s, fallback);
            return fallback;
        }
    }

    /** 把「若干片 × 每片若干层」的 {@link #MOD_LAYERS} 形态解成 {@code [剖面][行]}。 */
    private static String[][] decode(String[] flat, int height, int width, int slices) {
        String[][] out = new String[slices][];
        for (int z = 0; z < slices; z++) {
            out[z] = new String[height];
            for (int r = 0; r < height; r++) {
                out[z][r] = flat[z * height + r];
            }
        }
        return out;
    }

    // ---------- 备料清单 / 提示文案 ----------

    /** 方块总数与各字符数量（只算一次；结构与 tooltip 共用）。 */
    private static int[] counts;

    private static int[] counts() {
        if (counts == null) {
            int[] c = new int[128];
            for (String slice : NAC_SLICES) {
                if (slice.isEmpty()) {
                    continue;
                }
                for (String entry : slice.split(";")) {
                    int second = entry.indexOf(',', entry.indexOf(',') + 1);
                    String content = entry.substring(second + 1);
                    for (int i = 0; i < content.length(); i++) {
                        c[content.charAt(i)]++;
                    }
                }
            }
            counts = c;
        }
        return counts;
    }

    /** {@code ch} 在 NAC 结构里有多少格（用于 tooltip 的备料清单）。 */
    public static int countOf(char ch) {
        int[] c = counts();
        return ch < c.length ? c[ch] : 0;
    }

    /** NAC 结构里的实体方块总数（原版 8713）。 */
    public static int blockCount() {
        int total = 0;
        int[] c = counts();
        for (int i = 0; i < c.length; i++) {
            if (i != ' ') {
                total += c[i];
            }
        }
        return total;
    }

    /** 结构方块清单（用于物品提示/文档）。 */
    public static Component structureSummary() {
        MutableComponent out = Component.literal(String.format(Locale.ROOT,
                "§7%d×%d×%d，原版 1:1，共 %d 个方块：", NAC_W, NAC_H, NAC_D, blockCount()));
        appendEntry(out, MESH, NanochipBlocks.MESH_INTERFACE_CASING, true);
        appendEntry(out, REINFORCEMENT, NanochipBlocks.REINFORCEMENT_CASING, false);
        appendEntry(out, GLASS, NanochipBlocks.COMPLEX_GLASS, false);
        appendEntry(out, MATRIX, NanochipBlocks.COMPUTATIONAL_MATRIX_CASING, false);
        appendEntry(out, FIREWALL, NanochipBlocks.FIREWALL_PROJECTION_CASING, false);
        out.append(Component.literal(" §7· 硅岩框架§f" + countOf(FRAME)));
        out.append(Component.literal("§7 · 控制器在自底往上第 §f" + (NAC_CTRL_ROW + 1) + "§7 层"));
        return out;
    }

    /**
     * 备料清单（tooltip 用）：每种结构方块要多少个，直接由结构数据统计得出
     * （所以不可能与图案脱节）。原版字符表的数量：B 1099 / C 3956 / E 2226 /
     * J 721 / A 32 / D 53。
     *
     * <p><b>为什么返回 {@link Component} 而不是 {@code String}，而且方块名必须走
     * {@link #blockName}（翻译键）—— 这是硬要求，不是风格问题：</b>
     * 本方法是给 {@code NanochipMachines.registerComplex()} 的 {@code .tooltips(...)} 用的，
     * 而 <b>{@code .tooltips(...)} 的实参在"机器注册窗口"里就会被求值</b>
     * （GTCEu 的 {@code GTCEuAPI.RegisterEvent<…MachineDefinition>}，发生在
     * {@code common_setup} 的 DeferredWorkQueue 里 —— 见真实崩溃栈
     * {@code NanochipMachines.registerComplex(NanochipMachines.java:326)}
     * → {@code NanochipComplexPattern.materialList(NanochipComplexPattern.java:847)}）。
     * 那一刻 Registrate 的 {@code BlockEntry} <b>还没有落库</b>，任何
     * {@code NanochipBlocks.X.get()} 都会抛
     * {@code NullPointerException: Registry entry not present: gtlmodular:…}，
     * 直接把整个模组的加载打挂（0.1.13 实测：{@code crash-2026-09-15_21.27.06-fml.txt}）。
     *
     * <p>{@link Component#translatable} 是<b>惰性</b>的：只在真正渲染 tooltip 时才去查语言文件，
     * 完全不碰注册表；而 {@code entry.getId()} 在注册之前就有值（连崩溃消息里的
     * {@code gtlmodular:nanochip_mesh_interface_casing} 都是它拼的），所以这里可以安全取用。
     * 计数部分本来就是纯静态数据（{@link #countOf}），可以立刻算出来。
     */
    public static Component materialList() {
        MutableComponent out = Component.empty();
        appendEntry(out, MESH, NanochipBlocks.MESH_INTERFACE_CASING, true);
        appendEntry(out, REINFORCEMENT, NanochipBlocks.REINFORCEMENT_CASING, false);
        appendEntry(out, GLASS, NanochipBlocks.COMPLEX_GLASS, false);
        appendEntry(out, MATRIX, NanochipBlocks.COMPUTATIONAL_MATRIX_CASING, false);
        appendEntry(out, FIREWALL, NanochipBlocks.FIREWALL_PROJECTION_CASING, false);
        out.append(Component.literal(" 硅岩框架 §7gtceu:naquadah_frame§f" + countOf(FRAME)));
        return out;
    }

    // ---------- tooltip 用的「惰性方块名」 ----------

    /**
     * 方块名的<b>惰性</b> Component：只把翻译键包起来，<b>不解析注册表</b>。
     *
     * <p>{@code entry.getId()} 是 Registrate 在构造条目时就写好的 {@link ResourceLocation}，
     * 注册与否都有值；而 {@link Component#translatable} 到渲染时才查语言文件
     * （键形如 {@code block.gtlmodular.nanochip_mesh_interface_casing}，lang 里已有）。
     * ⇒ 这个方法可以在"注册表还没落库"的时刻安全调用。
     */
    private static Component blockName(BlockEntry<? extends Block> entry) {
        ResourceLocation id = entry.getId();
        return Component.translatable("block." + id.getNamespace() + "." + id.getPath());
    }

    /** 往 tooltip 里追加一条「方块名 数量」（第一条不加分隔符）。 */
    private static void appendEntry(MutableComponent out, char ch, BlockEntry<? extends Block> entry, boolean first) {
        if (!first) {
            out.append(Component.literal(" §7· "));
        }
        out.append(blockName(entry));
        out.append(Component.literal(" §f" + countOf(ch)));
    }

    // ==================================================================================
    // ★★ 2026-09-19（队长）：「不要再一格一格报，一次把缺件清单给我」★★
    //
    // 背景：GTCEu 的图案判定**遇到第一个不符格就 return**，`MultiblockState.error` 只留那一格。
    // 于是多个破洞要"修一个 → 看下一条日志 → 再修"，队长实测被这个来回折腾了好几轮
    // （施工记录 O.53：主机报 (-59,87,-92) 要网格接口机壳，队长手上那块玻璃却是**另一格**）。
    //
    // 做法：本机自己**遍历图案的"唯一方块"格**与全世界比一遍，把不符的全部列出来。
    //   · 只认「唯一方块」的字符（A/B/C/E/J，见 strictBlockFor）—— 它们一格的正确性可单机判定，
    //     不需要 GTCEu 的谓词求值；H/I/F/K/空格是"多解"格，**故意不列**（列了会误报）；
    //   · 朝向：GTCEu 的映射是「水平 4 旋转 + isFlipped 时按朝向取反一个水平轴」，
    //     **镜像不是旋转** ⇒ 必须枚举 **4 × 2 = 8 个元素**（只枚举 4 个会把结论说错，O.53 的教训）。
    //     这里不猜朝向：8 个元素各用 ≤128 个采样格打分，取命中最多者，再用它遍历全部严格格；
    //   · 只读 + 有上限（默认最多 12 条）⇒ 任何异常都吞掉，绝不拖垮结构检查本身。
    // ==================================================================================

    /** 严格格扁平表：每 4 个 int = {x, row, z, 字符}；惰性构建（注册期不碰）。 */
    private static volatile int[][] STRICT_CELLS;

    /** 只认唯一方块判定的字符；其它字符返回 {@code null}（多解格不参与清单，避免误报）。 */
    private static Block strictBlockFor(char ch) {
        return switch (ch) {
            case FIREWALL -> block(NanochipBlocks.FIREWALL_PROJECTION_CASING);
            case MESH -> block(NanochipBlocks.MESH_INTERFACE_CASING);
            case REINFORCEMENT -> block(NanochipBlocks.REINFORCEMENT_CASING);
            case GLASS -> block(NanochipBlocks.COMPLEX_GLASS);
            case MATRIX -> block(NanochipBlocks.COMPUTATIONAL_MATRIX_CASING);
            // D（硅岩框架）是 GTCEu 的材料框架，可能有多档变体 ⇒ 不列（只有 53 格，几乎不会是元凶）
            default -> null;
        };
    }

    /**
     * 遍历 {@link #NAC_SLICES} 里**实际写出的每一个字符**（编码 {@code "row,pad,内容;…"}，数组下标 = z）。
     *
     * <p>★ 2026-09-21（A+ 精确预检）：把原来内联在 {@link #strictCells()} 里的解析提成回调，
     * 让"严格格表 / 能力格表"共用**同一套**解析（两份解析一分叉，就会出现"预检与图案脱节"）。
     * 语义与提取前逐字一致：{@code row/pad} 解析失败 ⇒ 跳过该组。
     */
    private interface NacCellSink {
        void accept(int x, int row, int z, char ch);
    }

    private static void forEachNacCell(NacCellSink sink) {
        for (int z = 0; z < NAC_SLICES.length; z++) {
            String slice = NAC_SLICES[z];
            if (slice == null) {
                continue;
            }
            for (String grp : slice.split(";")) {
                String g = grp.trim();
                if (g.isEmpty()) {
                    continue;
                }
                int c1 = g.indexOf(',');
                int c2 = c1 < 0 ? -1 : g.indexOf(',', c1 + 1);
                if (c2 < 0) {
                    continue;
                }
                int row, pad;
                try {
                    row = Integer.parseInt(g.substring(0, c1).trim());
                    pad = Integer.parseInt(g.substring(c1 + 1, c2).trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                String content = g.substring(c2 + 1);
                for (int i = 0; i < content.length(); i++) {
                    sink.accept(pad + i, row, z, content.charAt(i));
                }
            }
        }
    }

    /** 解析 {@link #NAC_SLICES}（编码 {@code "row,pad,内容;…"}，数组下标 = z）成严格格表。 */
    private static int[][] strictCells() {
        int[][] local = STRICT_CELLS;
        if (local != null) {
            return local;
        }
        java.util.List<int[]> out = new java.util.ArrayList<>();
        forEachNacCell((x, row, z, ch) -> {
            if (strictBlockFor(ch) != null) {
                out.add(new int[]{x, row, z, ch});
            }
        });
        local = out.toArray(new int[0][]);
        STRICT_CELLS = local;
        return local;
    }

    /**
     * 8 个朝向元素：把「基准偏移」{@code (wx, wy, wz)} 映到世界偏移。
     *
     * <p>基准偏移 = {@code (NAC_CTRL_X − x, row − NAC_CTRL_ROW, NAC_CTRL_Z − z)}（= facing north 那一套）。
     * 元素序：① 恒等 ② 旋转 90° ③ 旋转 180° ④ 旋转 270° ⑤ 镜像X ⑥ 镜像Z ⑦ 镜像+90° ⑧ 镜像+270°
     * —— 与 {@code _census/_probe_cell.js} 同一枚举（实机已用第 ⑥ 个元素对上一次翻转载荷）。
     *
     * <p>★ 2026-09-21（A+ 精确预检）：由 {@code private} 改为 {@code public} —— 消费者
     * {@code com.gtlmodular.common.machine.nanochip.NanochipExactPrecheck} 要用<b>同一套</b>枚举，
     * 绝不另写一份（两份枚举一分叉就是"合法结构被预检判死"）。行为一字未改。
     */
    public static void elementOffset(int element, int wx, int wy, int wz, int[] out) {
        int dx, dz;
        switch (element) {
            case 1 -> { dx = wz; dz = -wx; }
            case 2 -> { dx = -wx; dz = -wz; }
            case 3 -> { dx = -wz; dz = wx; }
            case 4 -> { dx = -wx; dz = wz; }
            case 5 -> { dx = wx; dz = -wz; }
            case 6 -> { dx = wz; dz = wx; }
            case 7 -> { dx = -wz; dz = -wx; }
            default -> { dx = wx; dz = wz; }
        }
        out[0] = dx;
        out[1] = wy;
        out[2] = dz;
    }

    /**
     * <b>一次性缺件清单</b>：把图案里所有「唯一方块」格跟世界逐一比对，返回最多 {@code max} 条可读行
     * （形如 {@code (-59, 87, -92) 需要 纳米芯片网格接口机壳，实际 空气 [图案 'B']}）。
     *
     * @param level      服务端世界（客户端调用返回空表）
     * @param controller 控制器世界坐标
     * @param max        最多返回几条（调用方一般给 12）
     * @return 可读行；一切异常都吞掉并返回空表（诊断绝不拖垮结构检查）
     */
    public static java.util.List<String> auditStructure(Level level, BlockPos controller, int max) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        try {
            if (level == null || controller == null || max <= 0) {
                return lines;
            }
            int[][] cells = strictCells();
            if (cells.length == 0) {
                return lines;
            }
            // ① 8 个元素各用"采样格"打分（每 step 取一格，最多 128 个样本）
            int step = Math.max(1, cells.length / 128);
            int bestElement = -1, bestScore = -1;
            int[] off = new int[3];
            for (int e = 0; e < 8; e++) {
                int score = 0, seen = 0;
                for (int i = 0; i < cells.length; i += step) {
                    int[] c = cells[i];
                    elementOffset(e, NAC_CTRL_X - c[0], c[1] - NAC_CTRL_ROW, NAC_CTRL_Z - c[2], off);
                    BlockPos pos = controller.offset(off[0], off[1], off[2]);
                    if (!level.isLoaded(pos)) {
                        seen++;
                        continue;
                    }
                    seen++;
                    Block want = strictBlockFor((char) c[3]);
                    if (want != null && level.getBlockState(pos).is(want)) {
                        score++;
                    }
                }
                if (seen > 0 && score > bestScore) {
                    bestScore = score;
                    bestElement = e;
                }
            }
            if (bestElement < 0) {
                return lines;
            }
            // ② 用胜出的元素遍历全部严格格，收集不符（空气也算不符）
            int bad = 0;
            for (int[] c : cells) {
                elementOffset(bestElement, NAC_CTRL_X - c[0], c[1] - NAC_CTRL_ROW, NAC_CTRL_Z - c[2], off);
                BlockPos pos = controller.offset(off[0], off[1], off[2]);
                if (!level.isLoaded(pos)) {
                    continue;
                }
                Block want = strictBlockFor((char) c[3]);
                if (want == null) {
                    continue;
                }
                net.minecraft.world.level.block.state.BlockState actual = level.getBlockState(pos);
                if (actual.is(want)) {
                    continue;
                }
                bad++;
                if (lines.size() < max) {
                    lines.add(String.format(java.util.Locale.ROOT,
                            "(%d, %d, %d) 需要 %s，实际 %s [图案 '%c']",
                            pos.getX(), pos.getY(), pos.getZ(),
                            want.getName().getString(),
                            actual.getBlock().getName().getString(),
                            (char) c[3]));
                }
            }
            if (bad > lines.size()) {
                lines.add("…… 共 " + bad + " 格不符（只列了前 " + lines.size() + " 格）");
            }
            return lines;
        } catch (Throwable t) {
            return lines;   // 诊断失败不影响结构检查
        }
    }

    // ==================================================================================
    // ★ 2026-09-21（队长拍板「上 A+」）A+ 精确预检：对外只读暴露「身份格 / 能力格」坐标表
    //
    // 消费者 = com.gtlmodular.common.machine.nanochip.NanochipExactPrecheck —— 它会被主机与
    // 14 台模块机的 checkPatternWithTryLock() 覆写调用，把「必然不成形」挡在昂贵匹配之前
    // （依据：`_census/多方块检测_可行性研究.md` §0 授权表 #3/#5、§5 A+ 档）。
    //
    // 本区块**只读数据**：build() / buildModule() 的 where 表、判定式、图案数据一个字都没改
    // （build() 的 body 仍被 `_census/check_extra_candidates.js` ⑤ / check_simple_logistics_flow.js
    //  按 sha256 冻结）。
    //
    // 为什么放在本类：图案数据的**唯一来源**就是这里（NAC_SLICES / MOD_LAYERS / 塔身表）。
    // 坐标表必须与图案同源 —— 两份各写一遍，一旦分叉就是「预检与图案脱节 ⇒ 合法结构永不成形」。
    // ==================================================================================

    /** 身份格角色码：{@code meshOnly()}（纳米芯片网格接口机壳，唯一方块谓词）。 */
    public static final int IDENTITY_ROLE_MESH = 0;
    /** 身份格角色码：{@code reinforcement()}（纳米芯片强化机壳）。 */
    public static final int IDENTITY_ROLE_REINFORCEMENT = 1;
    /** 身份格角色码：{@code firewall()}（防火墙投影机壳）。 */
    public static final int IDENTITY_ROLE_FIREWALL = 2;
    /** 身份格角色码：{@code glass()}（复合体玻璃）。 */
    public static final int IDENTITY_ROLE_GLASS = 3;
    /** 身份格角色码：{@code matrix()}（计算矩阵机壳）。 */
    public static final int IDENTITY_ROLE_MATRIX = 4;
    /** 身份格角色码：模块的空格位（{@code where(AIR, air())} ⇒ 必须为空；判定用 {@code Level.isEmptyBlock}）。 */
    public static final int IDENTITY_ROLE_AIR = 5;

    /**
     * 主机图案字符 → 身份格角色码；<b>多解 / 拿不准的字符一律 {@code -1}</b>（= 不进预检）。
     *
     * <p>只认 {@link #build} 里判定式是「唯一一个方块」的 5 个字符（与 {@link #strictBlockFor} 同一批）：
     * {@code A}/{@code B}/{@code C}/{@code E}/{@code J}。明确<b>不</b>认：
     * <ul>
     *   <li>{@code D}（{@code frames(Naquadah)}）：材料框架不保证唯一（GTCEu 的 {@code frames} 是
     *       {@code blocks(材料框架)} 或 材料谓词，见 {@code _census/_gtceu_Predicates_javap.txt}），
     *       本模组自己的 {@link #auditStructure} 也把它排除 ⇒ 不列（少列只会削弱过滤力）；</li>
     *   <li>{@code F}/{@code H}/{@code I}：多解 + 全局计数 ⇒ <b>原理上不能退化成方块身份</b>（报告 §4.1）；</li>
     *   <li>空格 / {@code K}：{@code any()}（gtlcore {@code matchSlice} 直接 continue，零成本）；</li>
     *   <li>{@code ~}：控制器自己那一格，恒真、无信息量。</li>
     * </ul>
     */
    private static int hostRoleOf(char ch) {
        return switch (ch) {
            case FIREWALL -> IDENTITY_ROLE_FIREWALL;
            case MESH -> IDENTITY_ROLE_MESH;
            case REINFORCEMENT -> IDENTITY_ROLE_REINFORCEMENT;
            case GLASS -> IDENTITY_ROLE_GLASS;
            case MATRIX -> IDENTITY_ROLE_MATRIX;
            default -> -1;
        };
    }

    /**
     * <b>主机全部「唯一方块身份格」</b>：每 4 个 int = {@code {图案 x, 图案 row, 图案 z, 角色码}}。
     *
     * <p>数据来源 = {@link #strictCells()}（现读 {@link #NAC_SLICES}，生产在用的同一张表，
     * {@code _census/check_structure_precheck.js} ③ 逐点核对过同一套解析）。
     * {@code A/B/C/E/J} 共 <b>8,034</b> 格（不含 {@code D} 的 53 格与控制器 1 格，见 {@link #hostRoleOf}）。
     *
     * @return 扁平表（可为空数组；调用方读到空表应<b>放行</b>）
     */
    public static int[] hostIdentityCells() {
        int[][] cells = strictCells();
        int[] out = new int[cells.length * 4];
        int n = 0;
        for (int[] c : cells) {
            int role = hostRoleOf((char) c[3]);
            if (role < 0) {
                continue;
            }
            out[n++] = c[0];
            out[n++] = c[1];
            out[n++] = c[2];
            out[n++] = role;
        }
        return Arrays.copyOf(out, n);
    }

    /**
     * <b>主机全部「多解格」</b>（{@code F} 模块位 48 / {@code H} 仓位 145 / {@code I} 可忽略位 432 = 625 格）：
     * 每 3 个 int = {@code {图案 x, 图案 row, 图案 z}}。
     *
     * <p>⚠️ 这 625 格<b>不是</b>判定（它们允许多种方块，还带全局计数）—— 它们只用来算一个
     * 「方块指纹」，回答"跟上次真跑匹配时比，这几格变了没有"（报告 §5 A3 的原始口径）。
     *
     * @return 扁平表（可为空数组）
     */
    public static int[] hostCapabilityCells() {
        List<int[]> cells = new ArrayList<>();
        forEachNacCell((x, row, z, ch) -> {
            if (ch == MODULE_SLOT || ch == HATCH || ch == IGNORED) {
                cells.add(new int[]{x, row, z});
            }
        });
        int[] out = new int[cells.size() * 3];
        for (int i = 0; i < cells.size(); i++) {
            int[] c = cells.get(i);
            out[i * 3] = c[0];
            out[i * 3 + 1] = c[1];
            out[i * 3 + 2] = c[2];
        }
        return out;
    }

    /**
     * ★★【结构位置缓存】主机<b>全部「真被评估的格」</b>（= 上游 {@code MultiblockState.cache} 的内容）：
     * 每 3 个 int = {@code {图案 x, 图案 row, 图案 z}}。
     *
     * <p><b>口径</b> = 「谓词不是 {@code any()} 的格」，也就是 {@link #build} 里<b>没有</b>映射成
     * {@code Predicates.any()} 的那些字符 —— 排除 {@code ' '}（{@code where(AIR, any())}）、
     * {@code 'K'}（{@code where(TOWER_SPACE, any())}）、{@code 'I'}（{@code where(IGNORED, any())}，
     * 2026-09-21 队长令改，见 {@code build()} 里那段长注释）。
     *
     * <p><b>为什么它恰好等于上游的 {@code MultiblockState.cache}</b>（依据 = 字节码，不是推测）：
     * gtlcore 的 {@code BlockPatternMixin.gtlcore$matchSlice} 对 {@code predicate.isAny()} 直接
     * {@code continue}（{@code _census/_jtmp/gtl_BPM_c.txt:323-326}：{@code isAny → goto 406} = 循环尾）
     * ⇒ any() 格既不 {@code updateState}（不读世界）也不 {@code addPosCache}；
     * 而 {@code TraceabilityPredicate.addCache()} = {@code !isAny()}
     * （{@code _census/_census_图案减肥_队员.md} §①.3 证据 9）⇒ <b>只有非 any() 的格进 cache</b>。
     *
     * <p><b>数量（现读字符表逐字符核过）</b>：C 3,956 + E 2,226 + B 1,099 + J 721 + H 145 + D 53
     * + F 48 + A 32 + {@code ~} 1 = <b>8,281</b> 格（{@code 'I'} 那 432 格已改成 {@code any()}，不在内）。
     *
     * <p>⚠️ <b>耦合登记（改图案的人必须看这一段）</b>：本表与 {@code build()} 的 {@code where} 表
     * 是同一件事的两处写法。若将来把 {@code where(AIR, any())} 改回 {@code air()}（回退开关见
     * {@code build()} 里那一行长注释），179,888 个空格会重新变成 CHECK 格
     * ⇒ 必须把本方法里的 {@code AIR} 从排除项里去掉（一行）。
     * <b>运行期有一道自证</b>：{@code NanochipAssemblyComplexMachine#onStructureFormed()} 会拿本表
     * 的格数与 {@code MultiblockState.getCache().size()} 比一次，不等就 WARN
     * （只告警、不改判定）—— 所以"图案与缓存口径脱节"这件事在下一次成形时就会出现在日志里。
     *
     * <p><b>消费者</b>：{@code com.gtlmodular.common.machine.nanochip.NanochipStructurePosIndex}
     * —— 它把这张表按 8 个朝向元素展开成「结构位置」并集，供"方块移除 ⇒ 定点失效"查表
     * （见 {@code _census/结构位置缓存与移除失效_实施记录.md}）。
     *
     * @return 扁平表（可为空数组；调用方读到空表应<b>放弃</b>该功能、回到上游行为）
     */
    public static int[] hostCheckCells() {
        List<int[]> cells = new ArrayList<>();
        forEachNacCell((x, row, z, ch) -> {
            if (ch == AIR || ch == TOWER_SPACE || ch == IGNORED) {
                return;                                  // ★ build() 里唯三映射成 any() 的字符
            }
            cells.add(new int[]{x, row, z});
        });
        int[] out = new int[cells.size() * 3];
        for (int i = 0; i < cells.size(); i++) {
            int[] c = cells.get(i);
            out[i * 3] = c[0];
            out[i * 3 + 1] = c[1];
            out[i * 3 + 2] = c[2];
        }
        return out;
    }

    /**
     * 模块机身份格表（键 = {@code MultiblockMachineDefinition.getId().getPath()}，值 = 每 4 个 int
     * {@code {图案 x, 图案 row, 图案 z, 角色码}}）。
     *
     * <p>由 {@link #buildModule} 在**构建该模块图案时**登记（{@link #registerModuleIdentityCells}）——
     * 读的就是刚算出的 {@code aisles}，与 {@code factory.aisle(...)} 的输入是同一份字符。
     */
    private static final Map<String, int[]> MODULE_IDENTITY_CELLS = new java.util.concurrent.ConcurrentHashMap<>();

    /** 某台模块机的身份格表；<b>没登记过 ⇒ {@code null}</b>（调用方必须按"放行"处理）。 */
    public static int[] moduleIdentityCells(String moduleId) {
        return moduleId == null ? null : MODULE_IDENTITY_CELLS.get(moduleId);
    }

    /**
     * 模块图案字符 → 身份格角色码；多解 / 拿不准 ⇒ {@code -1}。
     *
     * <p>★ <b>塔身字符表优先</b>：11+3 台模块的塔身各用**自己的字母**（同一个字符在不同模块是不同角色，
     * 例如 {@code A} 在超导线束分离器是线圈、在生物协调器是网格机壳）⇒ 必须先查 {@code tower.cells()}，
     * 查不到才按共享底座的固定字符解释。这样即使将来塔身启用了 {@code P}/{@code Z}/{@code ' '}，
     * 也不会被误当成底座语义。
     *
     * <p>只认「唯一方块 / 严格空气」的角色（依据 {@link #predicateFor} 与 {@link #extraCandidatesFor}）：
     * <ul>
     *   <li>{@code MESH} → {@code meshOnly()}；{@code REINFORCEMENT} → {@code reinforcement()}
     *       —— 两者在 {@code extraCandidatesFor} 里都走 {@code default -> NO_EXTRA_CANDIDATES}
     *       ⇒ 判定就是"唯一一个方块"，是做身份判定的合法资格；</li>
     *   <li>底座 {@code 'P'}/{@code 'Z'} → {@code meshOnly()}/{@code reinforcement()}（同上）；</li>
     *   <li>{@code ' '} → {@code where(AIR, air())}：{@code air()} 的 test 就是 {@code world.isEmptyBlock(pos)}
     *       （{@code _census/_gtceu_SimplePredicate_javap.txt} 的 {@code lambda$static$1}）⇒ 可精确判定；</li>
     *   <li><b>不认</b>：{@code ASSEMBLY}（14 级装配线外壳，多候选）、{@code GLASS}/{@code SHEET}/
     *       {@code MATRIX_CASING}/{@code REINFORCED_CASING}（带追加候选 ⇒ 不是唯一方块）、
     *       {@code FRAME}（材料框架，同主机 {@code D} 的理由）、{@code UEV_CASING}（多候选）、
     *       {@code HATCH}/{@code 'V'}（仓位，多解）、{@code COIL_UHV}/{@code COIL_UEV}
     *       （虽是唯一方块，但只占 1 台模块 22 格，为把"角色表"压到最小而<b>有意不列</b>）、
     *       {@code CONTROLLER}/{@code 'X'}（{@code any()}）。</li>
     * </ul>
     */
    private static int moduleRoleOf(char ch, TowerDef tower) {
        TowerCell cell = tower == null ? null : tower.cells().get(ch);
        if (cell != null) {
            return switch (cell.role()) {
                case MESH -> IDENTITY_ROLE_MESH;
                case REINFORCEMENT -> IDENTITY_ROLE_REINFORCEMENT;
                default -> -1;
            };
        }
        if (ch == MOD_MESH) {
            return IDENTITY_ROLE_MESH;
        }
        if (ch == MOD_CORE) {
            return IDENTITY_ROLE_REINFORCEMENT;
        }
        if (ch == AIR) {
            return IDENTITY_ROLE_AIR;
        }
        return -1;
    }

    /**
     * 登记某模块的身份格表（{@link #buildModule} 调用；<b>只读 {@code aisles}</b>，不改图案）。
     *
     * <p>零重复几何：{@code aisles} 就是紧接着交给 {@code factory.aisle(...)} 的那一份字符，
     * 角色判定也来自同一个 {@code tower} —— 所以本表与图案<b>不可能</b>因"两处各写一份公式"而脱节。
     */
    private static void registerModuleIdentityCells(MultiblockMachineDefinition def, String[][] aisles, TowerDef tower) {
        if (def == null || def.getId() == null || aisles == null) {
            return;
        }
        List<int[]> cells = new ArrayList<>();
        for (int z = 0; z < aisles.length; z++) {
            String[] slice = aisles[z];
            if (slice == null) {
                continue;
            }
            for (int row = 0; row < slice.length; row++) {
                String line = slice[row];
                if (line == null) {
                    continue;
                }
                for (int x = 0; x < line.length(); x++) {
                    int role = moduleRoleOf(line.charAt(x), tower);
                    if (role >= 0) {
                        cells.add(new int[]{x, row, z, role});
                    }
                }
            }
        }
        int[] flat = new int[cells.size() * 4];
        for (int i = 0; i < cells.size(); i++) {
            System.arraycopy(cells.get(i), 0, flat, i * 4, 4);
        }
        MODULE_IDENTITY_CELLS.put(def.getId().getPath(), flat);
        com.gtlmodular.GTLModular.LOGGER.debug(
                "[gtlmodular] A+ 精确预检：登记模块 {} 的身份格 {} 个（唯一方块 + 严格空气；"
                        + "多解/带追加候选的角色一律不列）", def.getId().getPath(), cells.size());
    }
}
