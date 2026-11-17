package com.gtlmodular.common.data.nanochip;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gtlmodular.common.data.NanochipBlocks;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern.ModuleControllerSlot;
import com.gtlmodular.common.machine.nanochip.NanochipModuleKind;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.gtlmodular.common.data.nanochip.NanochipComplexPattern.AIR;
import static com.gtlmodular.common.data.nanochip.NanochipComplexPattern.FIREWALL;
import static com.gtlmodular.common.data.nanochip.NanochipComplexPattern.FRAME;
import static com.gtlmodular.common.data.nanochip.NanochipComplexPattern.GLASS;
import static com.gtlmodular.common.data.nanochip.NanochipComplexPattern.HATCH;
import static com.gtlmodular.common.data.nanochip.NanochipComplexPattern.IGNORED;
import static com.gtlmodular.common.data.nanochip.NanochipComplexPattern.MATRIX;
import static com.gtlmodular.common.data.nanochip.NanochipComplexPattern.MESH;
import static com.gtlmodular.common.data.nanochip.NanochipComplexPattern.MODULE_SLOT;
import static com.gtlmodular.common.data.nanochip.NanochipComplexPattern.MODULE_SLOT_NAME;
import static com.gtlmodular.common.data.nanochip.NanochipComplexPattern.NAC_SLICES;
import static com.gtlmodular.common.data.nanochip.NanochipComplexPattern.REINFORCEMENT;
import static com.gtlmodular.common.data.nanochip.NanochipComplexPattern.TOWER_SPACE;
import static com.gtlmodular.common.data.nanochip.NanochipComplexPattern.block;
import static com.gtlmodular.common.data.nanochip.NanochipComplexPattern.decode;

/**
 * NAC 结构数据的<b>语义层</b> —— P3 M4a（2026-11-08）从 {@link NanochipComplexPattern} <b>逐字搬出</b>。
 *
 * <p>写法对齐参考实现（{@code _NAC重写_01_参考架构_时空破碎_20261108.md} §5.4 / §10.1）：
 * <b>结构数据只有 {@code String[]}，语义（分区 / 偏移 / 绑定）单独一层</b>。
 * 本类只装"从数据算出来的语义"，<b>不装任何 pattern 数据</b>：
 * <ul>
 *   <li>几何常量（{@code NAC_W/H/D}、{@code MOD_*}、控制器下标）；</li>
 *   <li>12 条模块控制器偏移的实测表 {@link #NAC_MODULE_PREVIEW_SLOTS} 与 {@link #moduleControllerOffsets()}
 *       （以及已停用的运行时反推 {@code deriveModuleControllerOffsets}，仅留作判据依据）；</li>
 *   <li>备料清单 / 一次性缺件审计 / A+ 精确预检用的身份格与能力格表。</li>
 * </ul>
 *
 * <p><b>数据本体未动</b>：{@code NAC_SLICES} / {@code MOD_LAYERS} / 塔身表仍在
 * {@link NanochipComplexPattern}，本类通过同包只读访问读取，<b>不复制、不留第二份</b>；
 * {@code counts} / {@code STRICT_CELLS} 两个惰性缓存是随方法一起搬来的原有缓存，口径不变。
 *
 * <p>原类保留同名 {@code public static} 一行委托 ⇒ <b>所有调用点零改动</b>。
 */
public final class NanochipStructureSections {

    private NanochipStructureSections() {
    }

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
}
