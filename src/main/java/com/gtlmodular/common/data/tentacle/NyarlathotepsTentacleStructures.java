package com.gtlmodular.common.data.tentacle;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gtlmodular.GTLModular;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;

/**
 * 奈亚拉托提普之触（{@code gtlmodular:nyarlathoteps_tentacle}）的结构 —— GTO
 * {@code gtocore:nyarlathoteps_tentacle} 的 <b>1:1 照搬</b>。
 *
 * <h2>取证</h2>
 * <ul>
 *   <li>结构文件：GTO jar（{@code gtocore-forge-1.20.1-26.10.1.jar}）内
 *       {@code pattern/nyarlathoteps_tentacle.mbs}（10,916 B，gzip 后 392,843 B）。</li>
 *   <li>格式：{@code FF FF FF FF | 0F | ver=1 | charDir=2 | stringDir=0 | aisleDir=4 |
 *       LEB(aisleCount=49) | LEB(stringsPerAisle=48)}，随后每行 {@code [0x00][1 字节长度][字符]}，
 *       每片之间插 1 字节标记（值 == 48）。解析器与逐格校验见
 *       {@code _scratch/tentacle/parse_mbs.py} / {@code verify_aisles.py}（0 差异）。</li>
 *   <li>谓词绑定：{@code _gto_src/.../machines/MultiBlockD.java:420} 的 {@code NYARLATHOTEPS_TENTACLE}
 *       （与 jar 字节码 {@code lambda$static$22} 逐条一致）。</li>
 *   <li>★ 0.3.285 <b>线圈核对</b>（用户怀疑 'B' 绑错）：GTO 给 {@code 'B'} 绑的是
 *       {@code GTOBlocks.ADVANCED_FUSION_COIL}（{@code MultiBlockD.java:432}；
 *       {@code GTOBlocks.java:258} 定义为 {@code createActiveCasing("advanced_fusion_coil",
 *       "进阶聚变线圈方块", ...)} ⇒ {@code gtocore:advanced_fusion_coil}；
 *       jar 内 {@code MultiBlockD.class} 字节码 {@code bipush 66 → getstatic ADVANCED_FUSION_COIL} 同证）。
 *       <b>不是</b>通用加热线圈（那是 {@code 'Q'} = {@code heatingCoils()}），<b>也不是</b>超级热熔线圈
 *       （那是 {@code 'D'} = {@code GTOBlocks.URUIUM_COIL_BLOCK}，{@code MultiBlockD.java:434}）。
 *       本包没装 GTOCore，用 GTLcore 的镜像块（同 id、同配方）⇒ 绑定不变。</li>
 *   <li>朝向：<b>直转，不做任何轴颠倒</b> —— 第 1 片 = 最背面（z=0），第 49 片 = 正面（z=48，控制器所在）；
 *       每片第 1 行 = 最低层（y=0）；每行第 1 个字符 = 机器左手边（x 最小侧）。
 *       依据见 {@code _scratch/tentacle/struct_报告.md}（mbs 的 RelativeDirection 序号 +
 *       GTO 魔改 GTCEu 的 {@code CompiledPiece} 坐标换算 + 我方 GTCEu 1.4.4
 *       {@code FactoryBlockPattern.build()} 的 {@code [aisle][row][char]} 直填取证）。</li>
 * </ul>
 *
 * <h2>尺寸与符号</h2>
 * <p><b>165 (X) × 48 (Y) × 49 (Z) = 388,080 格</b>，其中实心 29,045 格、{@code ~} 33 格（GTO 未绑定 =
 * 不判定，这里绑成 {@code any()}）、控制器 {@code a} 1 格（x=82, y=24, z=48 = 正面正中）。
 *
 * <table border="1">
 *   <caption>符号表（GTO 方块 → 我方方块；缺的 4 件见 {@link TentacleBlocks}）</caption>
 *   <tr><th>符号</th><th>GTO</th><th>我方</th><th>格数</th></tr>
 *   <tr><td>A / O</td><td>分子机械方块 + IO/能源/激光/线程/并行/光口/维护</td><td>{@code gtlcore:molecular_casing}</td><td>62 / 4952</td></tr>
 *   <tr><td>B</td><td>进阶聚变线圈方块</td><td>{@code gtlcore:advanced_fusion_coil}（GTO 原 id {@code gtocore:advanced_fusion_coil} 的本包镜像）</td><td>336</td></tr>
 *   <tr><td>C</td><td>激光机械方块</td><td><b>{@code gtlmodular:laser_casing}（新造）</b></td><td>800</td></tr>
 *   <tr><td>D</td><td>超级热熔线圈方块</td><td>{@code kubejs:uruium_coil_block}</td><td>373</td></tr>
 *   <tr><td>E</td><td>超能核心</td><td>{@code gtlcore:hyper_core}</td><td>154</td></tr>
 *   <tr><td>F</td><td>安普洛（=中子素）框架</td><td>{@code gtceu:neutronium_frame}（安普洛=中子素）</td><td>685</td></tr>
 *   <tr><td>G</td><td>超时空装配外壳</td><td>{@code kubejs:spacetime_assembly_line_casing}</td><td>196</td></tr>
 *   <tr><td>H</td><td>极密机械方块</td><td>{@code gtladditions:extreme_density_casing}</td><td>728</td></tr>
 *   <tr><td>I</td><td>聚变玻璃</td><td>{@code gtceu:fusion_glass}</td><td>6344</td></tr>
 *   <tr><td>J</td><td>激光冷却方块</td><td>{@code kubejs:laser_cooling_casing}</td><td>144</td></tr>
 *   <tr><td>K</td><td>量子玻璃</td><td>{@code gtladditions:quantum_glass}</td><td>652</td></tr>
 *   <tr><td>L</td><td>硅岩合金机械外壳</td><td>{@code gtlcore:naquadah_alloy_casing}</td><td>2280</td></tr>
 *   <tr><td>M</td><td>超临界外壳</td><td>{@code gtlcore:sps_casing}</td><td>3072</td></tr>
 *   <tr><td>N</td><td>超时空装配单元</td><td>{@code kubejs:spacetime_assembly_line_unit}</td><td>594</td></tr>
 *   <tr><td>P</td><td>遏制场发生器</td><td>{@code kubejs:containment_field_generator}</td><td>542</td></tr>
 *   <tr><td>Q</td><td>加热线圈（任意）</td><td>{@code Predicates.heatingCoils()}</td><td>424</td></tr>
 *   <tr><td>R</td><td>无菌过滤机械方块</td><td>{@code gtceu:sterilizing_filter_casing}</td><td>256</td></tr>
 *   <tr><td>S</td><td>电子渗透安普洛涂层玻璃</td><td><b>{@code gtlmodular:electron_permeable_amprosium_coated_glass}（新造）</b></td><td>720</td></tr>
 *   <tr><td>T</td><td>中空机械方块</td><td>{@code kubejs:hollow_casing}</td><td>128</td></tr>
 *   <tr><td>U</td><td>安普洛活性机械方块</td><td><b>{@code gtlmodular:amprosium_active_casing}（新造）</b></td><td>496</td></tr>
 *   <tr><td>V</td><td>秘银框架</td><td>{@code gtceu:mithril_frame}</td><td>672</td></tr>
 *   <tr><td>W</td><td>红宝石块</td><td>{@code gtceu:ruby_block}</td><td>1088</td></tr>
 *   <tr><td>X</td><td>电解器</td><td>{@code gtceu:electrolytic_cell}</td><td>1384</td></tr>
 *   <tr><td>Y</td><td>三钛（凯金）框架</td><td>{@code gtceu:trinium_frame}</td><td>152</td></tr>
 *   <tr><td>Z</td><td>高能机器外壳</td><td>{@code gtceu:high_power_casing}</td><td>1040</td></tr>
 *   <tr><td>[</td><td>维度桥接方块</td><td>{@code kubejs:dimensional_bridge_casing}</td><td>314</td></tr>
 *   <tr><td>~</td><td>力场约束装置</td><td>{@code kubejs:restraint_device}</td><td>34</td></tr>
 *   <tr><td>+</td><td>力场约束装置（旧版符号，本结构未用）</td><td>{@code kubejs:restraint_device}</td><td>0</td></tr>
 *   <tr><td>]</td><td>夸克管道</td><td><b>{@code gtlmodular:quark_pipe}（新造）</b></td><td>36</td></tr>
 *   <tr><td>^</td><td>分子线圈</td><td>{@code kubejs:molecular_coil}</td><td>320</td></tr>
 *   <tr><td>_</td><td>简并态铼约束外壳</td><td>{@code gtlcore:degenerate_rhenium_constrained_casing}</td><td>48</td></tr>
 *   <tr><td>`</td><td>超维度机械方块</td><td>{@code gtlcore:dimensionally_transcendent_casing}</td><td>18</td></tr>
 *   <tr><td>a</td><td>控制器</td><td>{@code gtlmodular:nyarlathoteps_tentacle}</td><td>1</td></tr>
 * </table>
 *
 * <p><b>方块解析时机</b>：{@code .pattern(...)} 在注册期只登记一个 memoize 的 supplier，{@link #build}
 * 到<b>第一次结构判定 / 首次打开 JEI 预览</b>才执行 —— 那时所有模组的方块（含 KubeJS）都已就位。
 * 因此 0.3.286 起每个符号都直接用 GTCEu 原生的 {@link Predicates#blocks(Block...)}
 * （经 {@link #blocksOf(String, String)} 解析），不再走自定义谓词：预览才能把「这一格允许什么」画对。
 * 只有「按 id 取不到方块」时（注册顺序异常）才退回 {@link #lazy(String)} 兜底并记 error，
 * 不会崩服、也不会把方块表退化成屏障方块。
 */
public final class NyarlathotepsTentacleStructures {

    static final int WIDTH = 165;
    static final int HEIGHT = 48;
    static final int LENGTH = 49;

    private NyarlathotepsTentacleStructures() {
    }

    /**
     * 结构层数据本体（{@code slices[z][y]}，每格一整行 165 字符，{@code z=0} 最背面、{@code z=48} 正面）。
     *
     * <p><b>为什么公开</b>：客户端「奈亚不稳定特效」（{@code client.renderer.preview}）需要从<b>同一份</b>
     * 结构数据反推表面锚点（{@code ShanhaiInstabilityGeometry#fromSlices}），
     * 避免再往 jar 里塞一份 {@code .mb} 资源造成两份真值。
     * 直接返回内部数组、不做防御性拷贝（2352 个字符串，只读；不在热路径上调用）。
     */
    public static String[][] slices() {
        return SLICES;
    }

    /** 49 片 Z 层，每层 48 行（Y=0 在前），行宽 165。数据来自 GTO 的 .mbs，直转无颠倒。 */
    private static final String[][] SLICES = {
            { // Z=0
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                                                                                                                                                     ", // Y=9
                    "                                                                                                                                                                     ", // Y=10
                    "                                                                                                                                                                     ", // Y=11
                    "                                                                                                                                                                     ", // Y=12
                    "                                                                                                                                                                     ", // Y=13
                    "                                                                                                                                                                     ", // Y=14
                    "                                                                                                                                                                     ", // Y=15
                    "                                                                                                                                                                     ", // Y=16
                    "                                                                                                                                                                     ", // Y=17
                    "                                                                                                                                                                     ", // Y=18
                    "                                                                                                                                                                     ", // Y=19
                    "                                                                                                                                                                     ", // Y=20
                    "                                                                                                                                                                     ", // Y=21
                    "                                                                                 AAA                                                                                 ", // Y=22
                    "                                                                                AAAAA                                                                                ", // Y=23
                    "                                                                                AAAAA                                                                                ", // Y=24
                    "                                                                                AAAAA                                                                                ", // Y=25
                    "                                                                                 AAA                                                                                 ", // Y=26
                    "                                                                                                                                                                     ", // Y=27
                    "                                                                                                                                                                     ", // Y=28
                    "                                                                                                                                                                     ", // Y=29
                    "                                                                                                                                                                     ", // Y=30
                    "                                                                                                                                                                     ", // Y=31
                    "                                                                                                                                                                     ", // Y=32
                    "                                                                                                                                                                     ", // Y=33
                    "                                                                                                                                                                     ", // Y=34
                    "                                                                                                                                                                     ", // Y=35
                    "                                                                                                                                                                     ", // Y=36
                    "                                                                                                                                                                     ", // Y=37
                    "                                                                                                                                                                     ", // Y=38
                    "                                                                                                                                                                     ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=1
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                                                                                                                                                     ", // Y=9
                    "                                                                                                                                                                     ", // Y=10
                    "                                                                                                                                                                     ", // Y=11
                    "                                                                                                                                                                     ", // Y=12
                    "                                                                                                                                                                     ", // Y=13
                    "                                                                                                                                                                     ", // Y=14
                    "                                                                                                                                                                     ", // Y=15
                    "                                                                                                                                                                     ", // Y=16
                    "                                                                                                                                                                     ", // Y=17
                    "                                                                                                                                                                     ", // Y=18
                    "                                                                                                                                                                     ", // Y=19
                    "                                B                                                CCC                                                B                                ", // Y=20
                    "                                B                                               CDDDC                                               B                                ", // Y=21
                    "                                B                                              CDEEEDC                                              B                                ", // Y=22
                    "                                F                                             CDEGGGEDC                                             F                                ", // Y=23
                    "                               EEE                                            CDEGGGEDC                                            EEE                               ", // Y=24
                    "                                F                                             CDEGGGEDC                                             F                                ", // Y=25
                    "                                B                                              CDEEEDC                                              B                                ", // Y=26
                    "                                B                                               CDDDC                                               B                                ", // Y=27
                    "                                B                                                CCC                                                B                                ", // Y=28
                    "                                                                                                                                                                     ", // Y=29
                    "                                                                                                                                                                     ", // Y=30
                    "                                                                                                                                                                     ", // Y=31
                    "                                                                                                                                                                     ", // Y=32
                    "                                                                                                                                                                     ", // Y=33
                    "                                                                                                                                                                     ", // Y=34
                    "                                                                                                                                                                     ", // Y=35
                    "                                                                                                                                                                     ", // Y=36
                    "                                                                                                                                                                     ", // Y=37
                    "                                                                                                                                                                     ", // Y=38
                    "                                                                                                                                                                     ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=2
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                                                                                                                                                     ", // Y=9
                    "                                                                                                                                                                     ", // Y=10
                    "                                                                                                                                                                     ", // Y=11
                    "                                                                                                                                                                     ", // Y=12
                    "                                                                                                                                                                     ", // Y=13
                    "                                                                                                                                                                     ", // Y=14
                    "                                                                                                                                                                     ", // Y=15
                    "                                B                                                                                                   B                                ", // Y=16
                    "                                B                                                                                                   B                                ", // Y=17
                    "                                B                                                                                                   B                                ", // Y=18
                    "                                B                                                HHH                                                B                                ", // Y=19
                    "                                B                                               HCCCH                                               B                                ", // Y=20
                    "                                                                               HCIJICH                                                                               ", // Y=21
                    "                                                                              HCIIJIICH                                                                              ", // Y=22
                    "                               FFF                                           HCIIIKIIICH                                           FFF                               ", // Y=23
                    "                              LLFLL                                          HCJJKKKJJCH                                          LLFLL                              ", // Y=24
                    "                               FFF                                           HCIIIKIIICH                                           FFF                               ", // Y=25
                    "                                                                              HCIIJIICH                                                                              ", // Y=26
                    "                                                                               HCIJICH                                                                               ", // Y=27
                    "                                B                                               HCCCH                                               B                                ", // Y=28
                    "                                B                                                HHH                                                B                                ", // Y=29
                    "                                B                                                                                                   B                                ", // Y=30
                    "                                B                                                                                                   B                                ", // Y=31
                    "                                B                                                                                                   B                                ", // Y=32
                    "                                                                                                                                                                     ", // Y=33
                    "                                                                                                                                                                     ", // Y=34
                    "                                                                                                                                                                     ", // Y=35
                    "                                                                                                                                                                     ", // Y=36
                    "                                                                                                                                                                     ", // Y=37
                    "                                                                                                                                                                     ", // Y=38
                    "                                                                                                                                                                     ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=3
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                                                                                                                                                     ", // Y=9
                    "                                                                                                                                                                     ", // Y=10
                    "                                                                                                                                                                     ", // Y=11
                    "                                                                                                                                                                     ", // Y=12
                    "                                                                                                                                                                     ", // Y=13
                    "                                B                                                                                                   B                                ", // Y=14
                    "                                B                                                                                                   B                                ", // Y=15
                    "                                B                                                                                                   B                                ", // Y=16
                    "                                                                                                                                                                     ", // Y=17
                    "                                                                                                                                                                     ", // Y=18
                    "                                                                                                                                                                     ", // Y=19
                    "                                                                                 CCC                                                                                 ", // Y=20
                    "                                                                                C   C                                                                                ", // Y=21
                    "                                                                               C     C                                                                               ", // Y=22
                    "                                           FMMMMMMMMMMM                       C       C                       MMMMMMMMMMMF                                           ", // Y=23
                    "                              L   L        FMMNNNNNNNMM                       C       C                       MMNNNNNNNMMF        L   L                              ", // Y=24
                    "                                           FMMMMMMMMMMM                       C       C                       MMMMMMMMMMMF                                           ", // Y=25
                    "                                                                               C     C                                                                               ", // Y=26
                    "                                                                                C   C                                                                                ", // Y=27
                    "                                                                                 CCC                                                                                 ", // Y=28
                    "                                                                                                                                                                     ", // Y=29
                    "                                                                                                                                                                     ", // Y=30
                    "                                                                                                                                                                     ", // Y=31
                    "                                B                                                                                                   B                                ", // Y=32
                    "                                B                                                                                                   B                                ", // Y=33
                    "                                B                                                                                                   B                                ", // Y=34
                    "                                                                                                                                                                     ", // Y=35
                    "                                                                                                                                                                     ", // Y=36
                    "                                                                                                                                                                     ", // Y=37
                    "                                                                                                                                                                     ", // Y=38
                    "                                                                                                                                                                     ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=4
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                                                                                                                                                     ", // Y=9
                    "                                                                                                                                                                     ", // Y=10
                    "                                                                                                                                                                     ", // Y=11
                    "                                B                                                                                                   B                                ", // Y=12
                    "                                B                                                                                                   B                                ", // Y=13
                    "                                B                                                                                                   B                                ", // Y=14
                    "                                                                                                                                                                     ", // Y=15
                    "                                                                                                                                                                     ", // Y=16
                    "                                                                                                                                                                     ", // Y=17
                    "                                                                                                                                                                     ", // Y=18
                    "                                                                                                                                                                     ", // Y=19
                    "                                                                               OOCCC                                                                                 ", // Y=20
                    "                                                                               PC   C                                                                                ", // Y=21
                    "                                           FMMMMMMMMMMM                        C     C                        MMMMMMMMMMMF                                           ", // Y=22
                    "                                           MM         MF                      C       C                     PFM         MM                                           ", // Y=23
                    "                             LL   LL     OOMM         MFO                     C       C                     OFM         MMOO     LL   LL                             ", // Y=24
                    "                                           MM         MFP                     C       C                      FM         MM                                           ", // Y=25
                    "                                           FMMMMMMMMMMM                        C     C                        MMMMMMMMMMMF                                           ", // Y=26
                    "                                                                                C   CP                                                                               ", // Y=27
                    "                                                                                 CCCOO                                                                               ", // Y=28
                    "                                                                                                                                                                     ", // Y=29
                    "                                                                                                                                                                     ", // Y=30
                    "                                                                                                                                                                     ", // Y=31
                    "                                                                                                                                                                     ", // Y=32
                    "                                                                                                                                                                     ", // Y=33
                    "                                B                                                                                                   B                                ", // Y=34
                    "                                B                                                                                                   B                                ", // Y=35
                    "                                B                                                                                                   B                                ", // Y=36
                    "                                                                                                                                                                     ", // Y=37
                    "                                                                                                                                                                     ", // Y=38
                    "                                                                                                                                                                     ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=5
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                                                                                                                                                     ", // Y=9
                    "                                                                                                                                                                     ", // Y=10
                    "                                B                                                                                                   B                                ", // Y=11
                    "                                B                                                                                                   B                                ", // Y=12
                    "                                                                                                                                                                     ", // Y=13
                    "                                                                                                                                                                     ", // Y=14
                    "                                                                                                                                                                     ", // Y=15
                    "                                                                                                                                                                     ", // Y=16
                    "                                                                                                                                                                     ", // Y=17
                    "                                                                                                                                                                     ", // Y=18
                    "                                                                                                                                                                     ", // Y=19
                    "                                                                            QQ POCCCPO                   Q                                                           ", // Y=20
                    "                                           FMMMMMMMMMMM                       QQCIJICO                    Q   MMMMMMMMMMMF                                           ", // Y=21
                    "                                           MM         MF                       CIIJIIC                     QPFM         MM                                           ", // Y=22
                    "                                          M            R                      CIIISIIIC                     QR            M                                          ", // Y=23
                    "                             L     L    OOM            ROO                    CJJSSSJJC                    OOR            MOO    L     L                             ", // Y=24
                    "                                          M            RQ                     CIIISIIIC                      R            M                                          ", // Y=25
                    "                                           MM         MFPQ                     CIIJIIC                       FM         MM                                           ", // Y=26
                    "                                           FMMMMMMMMMMM   Q                    OCIJICQQ                       MMMMMMMMMMMF                                           ", // Y=27
                    "                                                           Q                   OPCCCOP QQ                                                                            ", // Y=28
                    "                                                                                                                                                                     ", // Y=29
                    "                                                                                                                                                                     ", // Y=30
                    "                                                                                                                                                                     ", // Y=31
                    "                                                                                                                                                                     ", // Y=32
                    "                                                                                                                                                                     ", // Y=33
                    "                                                                                                                                                                     ", // Y=34
                    "                                                                                                                                                                     ", // Y=35
                    "                                B                                                                                                   B                                ", // Y=36
                    "                                B                                                                                                   B                                ", // Y=37
                    "                                                                                                                                                                     ", // Y=38
                    "                                                                                                                                                                     ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=6
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                                                                                                                                                     ", // Y=9
                    "                                B                                                                                                   B                                ", // Y=10
                    "                                B                                                                                                   B                                ", // Y=11
                    "                                                                                                                                                                     ", // Y=12
                    "                                                                                                                                                                     ", // Y=13
                    "                                                                                                                                                                     ", // Y=14
                    "                                                                                                                                                                     ", // Y=15
                    "                                                                                                                                                                     ", // Y=16
                    "                                                                      QQ         Q                    Q                                                              ", // Y=17
                    "                                                                        QQ        Q                    Q                                                             ", // Y=18
                    "                                                                          QQ       Q                    Q                                                            ", // Y=19
                    "                                      OTTO              OTTO                   OOCCCQP                   OTTO              OTTO                                      ", // Y=20
                    "                                      OSSO FMMNNNNNNNMM OSSO                   PC   CO                   OSSO MMNNNNNNNMMF OSSO                                      ", // Y=21
                    "                                      OSSOFMM         MFOSSO                   C     C                   OSSOFM         MMFOSSO                                      ", // Y=22
                    "                                      OSSOM            ROSSO                  C       C                  OSSOR            MOSSO                                      ", // Y=23
                    "                            LL     LL OOOONUUUUUUUUUUUUUOOOO                  C       C                  OOOOUUUUUUUUUUUUUNOOOO LL     LL                            ", // Y=24
                    "                                      OSSOM            ROSSO                  C       C                  OSSOR            MOSSO                                      ", // Y=25
                    "                                      OSSOFMM         MFOSSO                   C     C                   OSSOFM         MMFOSSO                                      ", // Y=26
                    "                                      OSSO FMMNNNNNNNMM OSSO                   OC   CP                   OSSO MMNNNNNNNMMF OSSO                                      ", // Y=27
                    "                                      OTTO              OTTO                   PQCCCOO                   OTTO              OTTO                                      ", // Y=28
                    "                                                            Q                    Q       QQ                                                                          ", // Y=29
                    "                                                             Q                    Q        QQ                                                                        ", // Y=30
                    "                                                              Q                    Q         QQ                                                                      ", // Y=31
                    "                                                                                                                                                                     ", // Y=32
                    "                                                                                                                                                                     ", // Y=33
                    "                                                                                                                                                                     ", // Y=34
                    "                                                                                                                                                                     ", // Y=35
                    "                                                                                                                                                                     ", // Y=36
                    "                                B                                                                                                   B                                ", // Y=37
                    "                                B                                                                                                   B                                ", // Y=38
                    "                                                                                                                                                                     ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=7
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                B                                                                                                   B                                ", // Y=8
                    "                                B                                                                                                   B                                ", // Y=9
                    "                                B                                                                                                   B                                ", // Y=10
                    "                                                                                                                                                                     ", // Y=11
                    "                                                                                                                                                                     ", // Y=12
                    "                                                                                                                                                                     ", // Y=13
                    "                                                                                                                                                                     ", // Y=14
                    "                                                                  QQ           Q                    Q                                                                ", // Y=15
                    "                                                                    QQ          Q                    Q                                                               ", // Y=16
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=17
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=18
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=19
                    "                                      VWWVOOOOOOOOOOOOOOVWWV                     CCCPO                   VWWVOOOOOOOOOOOOOOVWWV                                      ", // Y=20
                    "                                      V  VOFMMMMMMMMMMMOV  V                    C   CO                   V  VOMMMMMMMMMMMFOV  V                                      ", // Y=21
                    "                                      V  VOMM         MFV  V                   C     C                   V  VFM         MMOV  V                                      ", // Y=22
                    "                                      V  VM            RV  V                  C       C                  V  VR            MV  V                                      ", // Y=23
                    "                            L       L O  OM            RO  O                  C       C                  O  OR            MO  O L       L                            ", // Y=24
                    "                                      V  VM            RV  V                  C       C                  V  VR            MV  V                                      ", // Y=25
                    "                                      V  VOMM         MFV  V                   C     C                   V  VFM         MMOV  V                                      ", // Y=26
                    "                                      V  VOFMMMMMMMMMMMOV  V                   OC   C                    V  VOMMMMMMMMMMMFOV  V                                      ", // Y=27
                    "                                      VWWVOOOOOOOOOOOOOOVWWV                   OPCCC                     VWWVOOOOOOOOOOOOOOVWWV                                      ", // Y=28
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=29
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=30
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=31
                    "                                                               Q                    Q          QQ                                                                    ", // Y=32
                    "                                                                Q                    Q           QQ                                                                  ", // Y=33
                    "                                                                                                                                                                     ", // Y=34
                    "                                                                                                                                                                     ", // Y=35
                    "                                                                                                                                                                     ", // Y=36
                    "                                                                                                                                                                     ", // Y=37
                    "                                B                                                                                                   B                                ", // Y=38
                    "                                B                                                                                                   B                                ", // Y=39
                    "                                B                                                                                                   B                                ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=8
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                B                                                                                                   B                                ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                                                                                                                                                     ", // Y=9
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=10
                    "                                           FMMNNNNNNNMM                                                       MMNNNNNNNMMF                                           ", // Y=11
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=12
                    "                                                                                                                                                                     ", // Y=13
                    "                                                                QQ            Q                    Q                                                                 ", // Y=14
                    "                                      OOOO              OOOO                                             OOOO              OOOO                                      ", // Y=15
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=16
                    "                                      O  OOOOOOOOOOOOOOOO  O                                             O  OOOOOOOOOOOOOOOO  O                                      ", // Y=17
                    "                                      X   OOOOIIIIIIIOOO   X                                             X   OOOIIIIIIIOOOO   X                                      ", // Y=18
                    "                                      X   OOOOOOOOOOOOOO   X                                             X   OOOOOOOOOOOOOO   X                                      ", // Y=19
                    "                                      VWW                WWV                     CCC                     VWW                WWV                                      ", // Y=20
                    "                                      X                    X                    CIJIC                    X                    X                                      ", // Y=21
                    "                                      X    FMMMMMMMMMMM    X                   CIIJIIC                   X    MMMMMMMMMMMF    X                                      ", // Y=22
                    "                           F         FX    MM         MF   X                  CIIIKIIIC                  X   FM         MM    XF         F                           ", // Y=23
                    "                           LL       LLO  OFMM         MFO  O                  CJJKKKJJC                  O  OFM         MMFO  OLL       LL                           ", // Y=24
                    "                           F         FX    MM         MF   X                  CIIIKIIIC                  X   FM         MM    XF         F                           ", // Y=25
                    "                                      X    FMMMMMMMMMMM    X                   CIIJIIC                   X    MMMMMMMMMMMF    X                                      ", // Y=26
                    "                                      X                    X                    CIJIC                    X                    X                                      ", // Y=27
                    "                                      VWW                WWV                     CCC                     VWW                WWV                                      ", // Y=28
                    "                                      X   OOOOOOOOOOOOOO   X                                             X   OOOOOOOOOOOOOO   X                                      ", // Y=29
                    "                                      X   OOOOIIIIIIIOOO   X                                             X   OOOIIIIIIIOOOO   X                                      ", // Y=30
                    "                                      O  OOOOOOOOOOOOOOOO  O                                             O  OOOOOOOOOOOOOOOO  O                                      ", // Y=31
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=32
                    "                                      OOOO              OOOO                                             OOOO              OOOO                                      ", // Y=33
                    "                                                                 Q                    Q            QQ                                                                ", // Y=34
                    "                                                                                                                                                                     ", // Y=35
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=36
                    "                                           FMMNNNNNNNMM                                                       MMNNNNNNNMMF                                           ", // Y=37
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=38
                    "                                                                                                                                                                     ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                B                                                                                                   B                                ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=9
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                B                                                                                                   B                                ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=9
                    "                                           MM         MFP                                                   PFM         MM                                           ", // Y=10
                    "                                          FMM         MF  QQ                                                 FM         MMF                                          ", // Y=11
                    "                                           MM         MF    QQ            QQ                   QQ            FM         MM                                           ", // Y=12
                    "                                           FMMMMMMMMMMM       QQ            QQ                   QQ           MMMMMMMMMMMF                                           ", // Y=13
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=14
                    "                                      O  OOOOOOOOOOOOOOOO  O                                             O  OOOOOOOOOOOOOOOO  O                                      ", // Y=15
                    "                                      O  OOOOOIIIIIIIOOOO  O                                             O  OOOOIIIIIIIOOOOO  O                                      ", // Y=16
                    "                                      X  O              O  X                                             X  O              O  X                                      ", // Y=17
                    "                                      X  O              O  X                                             X  O              O  X                                      ", // Y=18
                    "                                      X  O              O  X                     H H                     X  O              O  X                                      ", // Y=19
                    "                                      VWWO              OWWV                     CCC                     VWWO              OWWV                                      ", // Y=20
                    "                                      X  O              O  X                    C   C                    X  O              O  X                                      ", // Y=21
                    "                                      X  O              O  X                   C     C                   X  O              O  X                                      ", // Y=22
                    "                           F         FX  O FMMMMMMMMMMM O  X                 HC       CH                 X  O MMMMMMMMMMMF O  XF         F                           ", // Y=23
                    "                           L         LO  O FMMNNNNNNNMM O  O                  C       C                  O  O MMNNNNNNNMMF O  OL         L                           ", // Y=24
                    "                           F         FX  O FMMMMMMMMMMM O  X                 HC       CH                 X  O MMMMMMMMMMMF O  XF         F                           ", // Y=25
                    "                                      X  O              O  X                   C     C                   X  O              O  X                                      ", // Y=26
                    "                                      X  O              O  X                    C   C                    X  O              O  X                                      ", // Y=27
                    "                                      VWWO              OWWV                     CCC                     VWWO              OWWV                                      ", // Y=28
                    "                                      X  O              O  X                     H H                     X  O              O  X                                      ", // Y=29
                    "                                      X  O              O  X                                             X  O              O  X                                      ", // Y=30
                    "                                      X  O              O  X                                             X  O              O  X                                      ", // Y=31
                    "                                      O  OOOOOIIIIIIIOOOO  O                                             O  OOOOIIIIIIIOOOOO  O                                      ", // Y=32
                    "                                      O  OOOOOOOOOOOOOOOO  O                                             O  OOOOOOOOOOOOOOOO  O                                      ", // Y=33
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=34
                    "                                           FMMMMMMMMMMM           QQ                   QQ            QQ       MMMMMMMMMMMF                                           ", // Y=35
                    "                                           MM         MF            QQ                   QQ            QQ    FM         MM                                           ", // Y=36
                    "                                          FMM         MF                                                 QQ  FM         MMF                                          ", // Y=37
                    "                                           MM         MFP                                                   PFM         MM                                           ", // Y=38
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                B                                                                                                   B                                ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=10
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                B                                                                                                   B                                ", // Y=6
                    "                                B                                                                                                   B                                ", // Y=7
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=8
                    "                                           MM         MFP                                                   PFM         MM                                           ", // Y=9
                    "                                          M            RQ                                                   QR            M                                          ", // Y=10
                    "                                         OM            ROQ              QQ                   QQ             OR            MO                                         ", // Y=11
                    "                                      OSSOM            ROSSO                                             OSSOR            MOSSO                                      ", // Y=12
                    "                                      OSSO MM         MFOSSO                                             OSSOFM         MM OSSO                                      ", // Y=13
                    "                                      O  OOFMMMMMMMMMMMOO  O                                             O  OOMMMMMMMMMMMFOO  O                                      ", // Y=14
                    "                                      O                    O                                             O                    O                                      ", // Y=15
                    "                                      O  O              O  O                                             O  O              O  O                                      ", // Y=16
                    "                                      X                    X                                             X                    X                                      ", // Y=17
                    "                                      X                    X                                             X                    X                                      ", // Y=18
                    "                                      X                    X                     H H                     X                    X                                      ", // Y=19
                    "                                      VWW                WWV                     CCC                     VWW                WWV                                      ", // Y=20
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                    C   C                    X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=21
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                   C     C                   X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=22
                    "L     LLIIIIIIIIIIIIIIIIIILF         FX                    X                 HC       CH                 X                    XF         FLIIIIIIIIIIIIIIIIIILL     L", // Y=23
                    "LYYYYYLLLLLLLLLLLLLLLLLLLLLL         LO  O     U   U    O  O                  C       C                  O  O    U   U     O  OL         LLLLLLLLLLLLLLLLLLLLLLYYYYYL", // Y=24
                    "L     LLIIIIIIIIIIIIIIIIIILF         FX                    X                 HC       CH                 X                    XF         FLIIIIIIIIIIIIIIIIIILL     L", // Y=25
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                   C     C                   X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=26
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                    C   C                    X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=27
                    "                                      VWW                WWV                     CCC                     VWW                WWV                                      ", // Y=28
                    "                                      X                    X                     H H                     X                    X                                      ", // Y=29
                    "                                      X                    X                                             X                    X                                      ", // Y=30
                    "                                      X                    X                                             X                    X                                      ", // Y=31
                    "                                      O  O              O  O                                             O  O              O  O                                      ", // Y=32
                    "                                      O                    O                                             O                    O                                      ", // Y=33
                    "                                      O  OOFMMMMMMMMMMMOO  O                                             O  OOMMMMMMMMMMMFOO  O                                      ", // Y=34
                    "                                      OSSO MM         MFOSSO                                             OSSOFM         MM OSSO                                      ", // Y=35
                    "                                      OSSOM            ROSSO                                             OSSOR            MOSSO                                      ", // Y=36
                    "                                         OM            RO             QQ                   QQ              QOR            MO                                         ", // Y=37
                    "                                          M            RQ                                                   QR            M                                          ", // Y=38
                    "                                           MM         MFP                                                   PFM         MM                                           ", // Y=39
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=40
                    "                                B                                                                                                   B                                ", // Y=41
                    "                                B                                                                                                   B                                ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=11
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                B                                                                                                   B                                ", // Y=5
                    "                                B                                                                                                   B                                ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                           FMMNNNNNNNMM                                                       MMNNNNNNNMMF                                           ", // Y=8
                    "                                          FMM         MF                                                 QQ  FM         MMF                                          ", // Y=9
                    "                                         OM            RO             QQ                   QQ              QOR            MO                                         ", // Y=10
                    "                                      OTTONUUUUUUUUUUUUUOTTO                                             OTTOUUUUUUUUUUUUUNOTTO                                      ", // Y=11
                    "                                      OWWOM            ROWWO                                             OWWOR            MOWWO                                      ", // Y=12
                    "                                      OWW FMM         MF WWO                                             OWW FM         MMF WWO                                      ", // Y=13
                    "                                      OWW  FMMNNNNNNNMM  WWO                                             OWW  MMNNNNNNNMMF  WWO                                      ", // Y=14
                    "                                      OWW      U   U     WWO                                             OWW     U   U      WWO                                      ", // Y=15
                    "                                      OWWO     U   U    OWWO                                             OWWO    U   U     OWWO                                      ", // Y=16
                    "                                      VWW      U   U     WWV                                             VWW     U   U      WWV                                      ", // Y=17
                    "L     LLIIIIIIIIIIIIIIIIIIL           VWW      U   U     WWV                                             VWW     U   U      WWV           LIIIIIIIIIIIIIIIIIILL     L", // Y=18
                    "L     LLIIIIIIIIIIIIIIIIIIL           VWW      U   U     WWV                   HHHHHHH                   VWW     U   U      WWV           LIIIIIIIIIIIIIIIIIILL     L", // Y=19
                    "L     LLIIIIIIIIIIIIIIIIIIL           VIIIIIIIIIIIIIIIIIIIIV                  HIIIJIIIH                  VIIIIIIIIIIIIIIIIIIIIV           LIIIIIIIIIIIIIIIIIILL     L", // Y=20
                    "      I      O            K           V        U   U       V                 HIIIIJIIIIH                 V       U   U        V           K           O       I      ", // Y=21
                    "      I      O            K           V        U   U       V                 HIIIIJIIIIH                 V       U   U        V           K           O       I      ", // Y=22
                    "      I      P            K           V        U   U       V                 HIIIISIIIIH                 V       U   U        V           K           P       I      ", // Y=23
                    "L    LLLPPP  P            L           O  O     U   U    O  O                 HJJJSSSJJJH                 O  O    U   U     O  O           L           P   PPPLLL    L", // Y=24
                    "      I      P            K           V        U   U       V                 HIIIISIIIIH                 V       U   U        V           K           P       I      ", // Y=25
                    "      I      O            K           V        U   U       V                 HIIIIJIIIIH                 V       U   U        V           K           O       I      ", // Y=26
                    "      I      O            K           V        U   U       V                 HIIIIJIIIIH                 V       U   U        V           K           O       I      ", // Y=27
                    "L     LLIIIIIIIIIIIIIIIIIIL           VIIIIIIIIIIIIIIIIIIIIV                  HIIIJIIIH                  VIIIIIIIIIIIIIIIIIIIIV           LIIIIIIIIIIIIIIIIIILL     L", // Y=28
                    "L     LLIIIIIIIIIIIIIIIIIIL           VWW      U   U     WWV                   HHHHHHH                   VWW     U   U      WWV           LIIIIIIIIIIIIIIIIIILL     L", // Y=29
                    "L     LLIIIIIIIIIIIIIIIIIIL           VWW      U   U     WWV                                             VWW     U   U      WWV           LIIIIIIIIIIIIIIIIIILL     L", // Y=30
                    "                                      VWW      U   U     WWV                                             VWW     U   U      WWV                                      ", // Y=31
                    "                                      OWWO     U   U    OWWO                                             OWWO    U   U     OWWO                                      ", // Y=32
                    "                                      OWW      U   U     WWO                                             OWW     U   U      WWO                                      ", // Y=33
                    "                                      OWW  FMMNNNNNNNMM  WWO                                             OWW  MMNNNNNNNMMF  WWO                                      ", // Y=34
                    "                                      OWW FMM         MF WWO                                             OWW FM         MMF WWO                                      ", // Y=35
                    "                                      OWWOM            ROWWO                                             OWWOR            MOWWO                                      ", // Y=36
                    "                                      OTTONUUUUUUUUUUUUUOTTO                                             OTTOUUUUUUUUUUUUUNOTTO                                      ", // Y=37
                    "                                         OM            ROQ              QQ                   QQ             OR            MO                                         ", // Y=38
                    "                                          FMM         MF  QQ                                                 FM         MMF                                          ", // Y=39
                    "                                           FMMNNNNNNNMM                                                       MMNNNNNNNMMF                                           ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                B                                                                                                   B                                ", // Y=42
                    "                                B                                                                                                   B                                ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=12
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                B                                                                                                   B                                ", // Y=4
                    "                                B                                                                                                   B                                ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=8
                    "                                           MM         MF            QQ                   QQ            QQ    FM         MM                                           ", // Y=9
                    "                                      OSSOM            ROSSO                                             OSSOR            MOSSO                                      ", // Y=10
                    "                                      OWWOM            ROWWO                                             OWWOR            MOWWO                                      ", // Y=11
                    "                                      X   M            R   X                                             X   R            M   X                                      ", // Y=12
                    "                                      X    MM         MF   X                                             X   FM         MM    X                                      ", // Y=13
                    "                                      X    FMMMMMMMMMMM    X                                             X    MMMMMMMMMMMF    X                                      ", // Y=14
                    "                                      O                    O                                             O                    O                                      ", // Y=15
                    "L     LLLLLLLLLLLLLLLLLLLLL           O  O              O  O                                             O  O              O  O           LLLLLLLLLLLLLLLLLLLLL     L", // Y=16
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                                             X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=17
                    "     I       O            K           X                    X                   HIIIIIH                   X                    X           K           O        I     ", // Y=18
                    "     I       O            K           X                    X                  H       H                  X                    X           K           O        I     ", // Y=19
                    "     I       O            K           VWW                WWV                 H         H                 VWW                WWV           K           O        I     ", // Y=20
                    "     I                    K           X                    X                H           H                X                    X           K                    I     ", // Y=21
                    "     I                    K           X                    X                I           I                X                    X           K                    I     ", // Y=22
                    "     I                    K           X                    X                I           I                X                    X           K                    I     ", // Y=23
                    "L   LLE                   L           O  O              O  O                I           I                O  O              O  O           L                   ELL   L", // Y=24
                    "     I                    K           X                    X                I           I                X                    X           K                    I     ", // Y=25
                    "     I                    K           X                    X                I           I                X                    X           K                    I     ", // Y=26
                    "     I                    K           X                    X                H           H                X                    X           K                    I     ", // Y=27
                    "     I       O            K           VWW                WWV                 H         H                 VWW                WWV           K           O        I     ", // Y=28
                    "     I       O            K           X                    X                  H       H                  X                    X           K           O        I     ", // Y=29
                    "     I       O            K           X                    X                   HIIIIIH                   X                    X           K           O        I     ", // Y=30
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                                             X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=31
                    "L     LLLLLLLLLLLLLLLLLLLLL           O  O              O  O                                             O  O              O  O           LLLLLLLLLLLLLLLLLLLLL     L", // Y=32
                    "                                      O                    O                                             O                    O                                      ", // Y=33
                    "                                      X    FMMMMMMMMMMM    X                                             X    MMMMMMMMMMMF    X                                      ", // Y=34
                    "                                      X    MM         MF   X                                             X   FM         MM    X                                      ", // Y=35
                    "                                      X   M            R   X                                             X   R            M   X                                      ", // Y=36
                    "                                      OWWOM            ROWWO                                             OWWOR            MOWWO                                      ", // Y=37
                    "                                      OSSOM            ROSSO                                             OSSOR            MOSSO                                      ", // Y=38
                    "                                           MM         MF    QQ            QQ                   QQ            FM         MM                                           ", // Y=39
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                B                                                                                                   B                                ", // Y=43
                    "                                B                                                                                                   B                                ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=13
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                B                                                                                                   B                                ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                           FMMMMMMMMMMM           QQ                   QQ            QQ       MMMMMMMMMMMF                                           ", // Y=9
                    "                                      OSSO MM         MFOSSO                                             OSSOFM         MM OSSO                                      ", // Y=10
                    "                                      OWW FMM         MF WWO                                             OWW FM         MMF WWO                                      ", // Y=11
                    "                                      X    MM         MF   X                                             X   FM         MM    X                                      ", // Y=12
                    "                                      X    FMMMMMMMMMMM    X                                             X    MMMMMMMMMMMF    X                                      ", // Y=13
                    "                                      X                    X                                             X                    X                                      ", // Y=14
                    "L     LLIIIIIIIIIIIIIIIIIIL           O                    O                                             O                    O           LIIIIIIIIIIIIIIIIIILL     L", // Y=15
                    "     I       P            L          DO  O              O  O                                             O  O              O  O          DL           P        I     ", // Y=16
                    "     I       O            K          DX                    X                                             X                    X          DK           O        I     ", // Y=17
                    "     I                    K         D X                    X                   HHHHHHH                   X                    X         D K                    I     ", // Y=18
                    "    I                     K         D X                    X                  HZZZZZZZH                  X                    X         D K                     I    ", // Y=19
                    "    I                     K        D  VWW                WWV                 HZZ     ZZH                 VWW                WWV        D  K                     I    ", // Y=20
                    "    I              O      K        D  X                    X                HZZ       ZZH                X                    X        D  K            O        I    ", // Y=21
                    "    I              O      K       D   X                    X                HZ         ZH                X                    X       D   K            O        I    ", // Y=22
                    "    I               T     K    D[ D   X                    X     [          HZ         ZH          [     X                    X    D[ D   K           T         I    ", // Y=23
                    "L  LL               T     L    D[D    O  O              O  O     [          HZ         ZH          [     O  O              O  O    D[D    L           T         LL  L", // Y=24
                    "    I               T     K   D [D    X                    X     [          HZ         ZH          [     X                    X   D [D    K           T         I    ", // Y=25
                    "    I              O      K   D       X                    X                HZ         ZH                X                    X   D       K          O          I    ", // Y=26
                    "    I              O      K  D        X                    X                HZZ       ZZH                X                    X  D        K          O          I    ", // Y=27
                    "    I                     K  D        VWW                WWV                 HZZ     ZZH                 VWW                WWV  D        K                     I    ", // Y=28
                    "    I                     K D         X                    X                  HZZZZZZZH                  X                    X D         K                     I    ", // Y=29
                    "     I                    K D         X                    X                   HHHHHHH                   X                    X D         K                    I     ", // Y=30
                    "     I       O            KD          X                    X                                             X                    XD          K           O        I     ", // Y=31
                    "     I       P            LD          O  O              O  O                                             O  O              O  OD          L           P        I     ", // Y=32
                    "L     LLIIIIIIIIIIIIIIIIIIL           O                    O                                             O                    O           LIIIIIIIIIIIIIIIIIILL     L", // Y=33
                    "                                      X                    X                                             X                    X                                      ", // Y=34
                    "                                      X    FMMMMMMMMMMM    X                                             X    MMMMMMMMMMMF    X                                      ", // Y=35
                    "                                      X    MM         MF   X                                             X   FM         MM    X                                      ", // Y=36
                    "                                      OWW FMM         MF WWO                                             OWW FM         MMF WWO                                      ", // Y=37
                    "                                      OSSO MM         MFOSSO                                             OSSOFM         MM OSSO                                      ", // Y=38
                    "                                           FMMMMMMMMMMM       QQ            QQ                   QQ           MMMMMMMMMMMF                                           ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                B                                                                                                   B                                ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=14
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                B                                                                                                   B                                ", // Y=3
                    "                                B                                                                                                   B                                ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                 Q                    Q            QQ                                                                ", // Y=8
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=9
                    "                                      O  OOFMMMMMMMMMMMOO  O                                             O  OOMMMMMMMMMMMFOO  O                                      ", // Y=10
                    "                                      OWW  FMMNNNNNNNMM  WWO                                             OWW  MMNNNNNNNMMF  WWO                                      ", // Y=11
                    "                                      X    FMMMMMMMMMMM    X                                             X    MMMMMMMMMMMF    X                                      ", // Y=12
                    "                                      X                    X                                             X                    X                                      ", // Y=13
                    "LYYYYLLLIIIIIIIIIIIIIIIIIIL           X                    X                                             X                    X           LIIIIIIIIIIIIIIIIIILLLYYYYL", // Y=14
                    "     I       O            X           O                    O                                             O                    O           X           O        I     ", // Y=15
                    "     I       O            L           O  O              O  O                                             O  O              O  O           L           O        I     ", // Y=16
                    "    I                     K           X                    X                                             X                    X           K                     I    ", // Y=17
                    "    I                     K           X                    X                                             X                    X           K                     I    ", // Y=18
                    "    I              O      K           X                    X                                             X                    X           K            O        I    ", // Y=19
                    "   I               O      K           VWW                WWV                                             VWW                WWV           K            O         I   ", // Y=20
                    "   I                      K           X                    X                                             X                    X           K                      I   ", // Y=21
                    "   I                      K    OIO    X                    X    OIO    HSH                 HSH    OIO    X                    X    OIO    K                      I   ", // Y=22
                    "   I                      K    PIP    X                    X    PIP    HSH                 HSH    PIP    X                    X    PIP    K                      I   ", // Y=23
                    "L LL                      L    PIP    O  O              O  O    PIP    HSH                 HSH    PIP    O  O              O  O    PIP    L                      LL L", // Y=24
                    "   I                      K    PIP    X                    X    PIP    HSH                 HSH    PIP    X                    X    PIP    K                      I   ", // Y=25
                    "   I                      K    OIO    X                    X    OIO    HSH                 HSH    OIO    X                    X    OIO    K                      I   ", // Y=26
                    "   I                      K           X                    X                                             X                    X           K                      I   ", // Y=27
                    "   I               O      K           VWW                WWV                                             VWW                WWV           K         O            I   ", // Y=28
                    "    I              O      K           X                    X                                             X                    X           K         O           I    ", // Y=29
                    "    I                     K           X                    X                                             X                    X           K                     I    ", // Y=30
                    "    I                     K           X                    X                                             X                    X           K                     I    ", // Y=31
                    "     I       O            L           O  O              O  O                                             O  O              O  O           L           O        I     ", // Y=32
                    "     I       O            X           O                    O                                             O                    O           X           O        I     ", // Y=33
                    "LYYYYLLLIIIIIIIIIIIIIIIIIIL           X                    X                                             X                    X           LIIIIIIIIIIIIIIIIIILLLYYYYL", // Y=34
                    "                                      X                    X                                             X                    X                                      ", // Y=35
                    "                                      X    FMMMMMMMMMMM    X                                             X    MMMMMMMMMMMF    X                                      ", // Y=36
                    "                                      OWW  FMMNNNNNNNMM  WWO                                             OWW  MMNNNNNNNMMF  WWO                                      ", // Y=37
                    "                                      O  OOFMMMMMMMMMMMOO  O                                             O  OOMMMMMMMMMMMFOO  O                                      ", // Y=38
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=39
                    "                                                                QQ            Q                    Q                                                                 ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                B                                                                                                   B                                ", // Y=44
                    "                                B                                                                                                   B                                ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=15
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                B                                                                                                   B                                ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                Q                    Q           QQ                                                                  ", // Y=7
                    "                                      OOOO              OOOO                                             OOOO              OOOO                                      ", // Y=8
                    "                                      O  OOOOOOOOOOOOOOOO  O                                             O  OOOOOOOOOOOOOOOO  O                                      ", // Y=9
                    "                                      O                    O                                             O                    O                                      ", // Y=10
                    "                                      OWW      U   U     WWO                                             OWW     U   U      WWO                                      ", // Y=11
                    "                                      O                    O                                             O                    O                                      ", // Y=12
                    "L     LLIIIIIIIIIIIIIIIIIIL           O                    O                                             O                    O           LIIIIIIIIIIIIIIIIIILL     L", // Y=13
                    "     I        O           X           O                    O                                             O                    O           X          O         I     ", // Y=14
                    "L   LLE                   X           O  O              O  O                                             O  O              O  O           X                   ELL   L", // Y=15
                    "    I                     L           O  O              O  O                                             O  O              O  O           L                     I    ", // Y=16
                    "    I                     K           P  P              P  P                                             P  P              P  P           K                     I    ", // Y=17
                    "   I              O       K           P  P              P  P                                             P  P              P  P           K             O        I   ", // Y=18
                    "   I                      K           P  P              P  P                                             P  P              P  P           K                      I   ", // Y=19
                    "   I                      K    OIO    PWWP              PWWP    OIO    HDH                 HDH    OIO    PWWP              PWWP    OIO    K                      I   ", // Y=20
                    "  I                       K    OIO    O  O              O  O    OIO    HDH                 HDH    OIO    O  O              O  O    OIO    K                       I  ", // Y=21
                    "Y I                       K   L   L   O  O              O  O   L   L    G                   G    L   L   O  O              O  O   L   L   K                       I Y", // Y=22
                    "Y I [                     K   L   L   O  O              O  O   L   L    G                   G    L   L   O  O              O  O   L   L   K          [            I Y", // Y=23
                    "~LL [                     L   EGGGE   O  O              O  O   EGGGE   [G[                 [G[   EGGGE   O  O              O  O   EGGGE   L           [           LL~", // Y=24
                    "Y I [                     K   L   L   O  O              O  O   L   L    G                   G    L   L   O  O              O  O   L   L   K            [          I Y", // Y=25
                    "Y I                       K   L   L   O  O              O  O   L   L    G                   G    L   L   O  O              O  O   L   L   K                       I Y", // Y=26
                    "  I                       K    OIO    O  O              O  O    OIO    HDH                 HDH    OIO    O  O              O  O    OIO    K                       I  ", // Y=27
                    "   I                      K    OIO    PWWP              PWWP    OIO    HDH                 HDH    OIO    PWWP              PWWP    OIO    K                      I   ", // Y=28
                    "   I                      K           P  P              P  P                                             P  P              P  P           K                      I   ", // Y=29
                    "   I              O       K           P  P              P  P                                             P  P              P  P           K        O             I   ", // Y=30
                    "    I                     K           P  P              P  P                                             P  P              P  P           K                     I    ", // Y=31
                    "    I                     L           O  O              O  O                                             O  O              O  O           L                     I    ", // Y=32
                    "L   LLE                   X           O  O              O  O                                             O  O              O  O           X                   ELL   L", // Y=33
                    "     I        O           X           O                    O                                             O                    O           X            O       I     ", // Y=34
                    "L     LLIIIIIIIIIIIIIIIIIIL           O                    O                                             O                    O           LIIIIIIIIIIIIIIIIIILL     L", // Y=35
                    "                                      O                    O                                             O                    O                                      ", // Y=36
                    "                                      OWW      U   U     WWO                                             OWW     U   U      WWO                                      ", // Y=37
                    "                                      O                    O                                             O                    O                                      ", // Y=38
                    "                                      O  OOOOOOOOOOOOOOOO  O                                             O  OOOOOOOOOOOOOOOO  O                                      ", // Y=39
                    "                                      OOOO              OOOO                                             OOOO              OOOO                                      ", // Y=40
                    "                                                                  QQ           Q                    Q                                                                ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                B                                                                                                   B                                ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=16
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                B                                                                                                   B                                ", // Y=2
                    "                                B                                                                                                   B                                ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                               Q                    Q          QQ                                                                    ", // Y=7
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=8
                    "                                      O  OOOOOIIIIIIIOOOO  O                                             O  OOOOIIIIIIIOOOOO  O                                      ", // Y=9
                    "                                      O  O              O  O                                             O  O              O  O                                      ", // Y=10
                    "                                      OWWO     U   U    OWWO                                             OWWO    U   U     OWWO                                      ", // Y=11
                    "L     LLLLLLLLLLLLLLLLLLLLL           O  O              O  O                                             O  O              O  O           LLLLLLLLLLLLLLLLLLLLL     L", // Y=12
                    "     I        P           LD          O  O              O  O                                             O  O              O  OD          L          P         I     ", // Y=13
                    "     I        O           L           O  O              O  O                                             O  O              O  O           L          O         I     ", // Y=14
                    "    I                     L           O  O              O  O                                             O  O              O  O           L                     I    ", // Y=15
                    "L  LL            T        L           O  O              O  O                                             O  O              O  O           L              T      LL  L", // Y=16
                    "   I              O       L           O  O              O  O                                             O  O              O  O           L             O        I   ", // Y=17
                    "   I                      L           O  O              O  O           HDH                 HDH           O  O              O  O           L                      I   ", // Y=18
                    "  I                       L    OIO    O  O              O  O    OIO    HDH                 HDH    OIO    O  O              O  O    OIO    L                       I  ", // Y=19
                    "Y I                       L   L   L   OWWO              OWWO   L   L                             L   L   OWWO              OWWO   L   L   L                       I Y", // Y=20
                    "Y I                       L   L   L   O  O              O  O   L   L                             L   L   O  O              O  O   L   L   L                       I Y", // Y=21
                    "XL   O                    Z           ZRRZ              ZRRZ                                             ZRRZ              ZRRZ           Z         O              LX", // Y=22
                    "XL                        Z           ZRRZ              ZRRZ                                             ZRRZ              ZRRZ           Z                        LX", // Y=23
                    "LL~                       Z           ZZZZ              ZZZZ                                             ZZZZ              ZZZZ           Z                       ~LL", // Y=24
                    "XL                        Z           ZRRZ              ZRRZ                                             ZRRZ              ZRRZ           Z                        LX", // Y=25
                    "XL   O                    Z           ZRRZ              ZRRZ                                             ZRRZ              ZRRZ           Z             O          LX", // Y=26
                    "Y I                       L   L   L   O  O              O  O   L   L                             L   L   O  O              O  O   L   L   L                       I Y", // Y=27
                    "Y I                       L   L   L   OWWO              OWWO   L   L                             L   L   OWWO              OWWO   L   L   L                       I Y", // Y=28
                    "  I                       L    OIO    O  O              O  O    OIO    HDH                 HDH    OIO    O  O              O  O    OIO    L                       I  ", // Y=29
                    "   I                      L           O  O              O  O           HDH                 HDH           O  O              O  O           L                      I   ", // Y=30
                    "   I              O       L           O  O              O  O                                             O  O              O  O           L        O             I   ", // Y=31
                    "L  LL            T        L           O  O              O  O                                             O  O              O  O           L        T            LL  L", // Y=32
                    "    I                     L           O  O              O  O                                             O  O              O  O           L                     I    ", // Y=33
                    "     I        O           L           O  O              O  O                                             O  O              O  O           L            O       I     ", // Y=34
                    "     I        P           L          DO  O              O  O                                             O  O              O  O          DL            P       I     ", // Y=35
                    "L     LLLLLLLLLLLLLLLLLLLLL           O  O              O  O                                             O  O              O  O           LLLLLLLLLLLLLLLLLLLLL     L", // Y=36
                    "                                      OWWO     U   U    OWWO                                             OWWO    U   U     OWWO                                      ", // Y=37
                    "                                      O  O              O  O                                             O  O              O  O                                      ", // Y=38
                    "                                      O  OOOOOIIIIIIIOOOO  O                                             O  OOOOIIIIIIIOOOOO  O                                      ", // Y=39
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=40
                    "                                                                    QQ          Q                    Q                                                               ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                B                                                                                                   B                                ", // Y=45
                    "                                B                                                                                                   B                                ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=17
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                B                                                                                                   B                                ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                              Q                    Q         QQ                                                                      ", // Y=6
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=7
                    "                                      O  OOOOOOOOOOOOOOOO  O                                             O  OOOOOOOOOOOOOOOO  O                                      ", // Y=8
                    "                                      X  O              O  X                                             X  O              O  X                                      ", // Y=9
                    "                                      X                    X                                             X                    X                                      ", // Y=10
                    "                                      VWW      U   U     WWV                                             VWW     U   U      WWV                                      ", // Y=11
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                                             X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=12
                    "     I        O           K           X                    X                                             X                    XD          K          O         I     ", // Y=13
                    "    I                     K           X                    X                                             X                    X           K                     I    ", // Y=14
                    "    I                     K           P  P              P  P                                             P  P              P  P           K                     I    ", // Y=15
                    "   I             O        L           O  O              O  O                                             O  O              O  O           L              O       I   ", // Y=16
                    "L LL[                     L           O  O              O  O           HDH                 HDH           O  O              O  O           L                     [LL L", // Y=17
                    "  I                       Z    OIO    Z  Z              Z  Z    OIO                               OIO    Z  Z              Z  Z    OIO    Z                       I  ", // Y=18
                    "Y I                       Z   L   L   Z  Z              Z  Z   L   L                             L   L   Z  Z              Z  Z   L   L   Z                       I Y", // Y=19
                    "XL     O                  Z           Z[[Z              Z[[Z                                             Z[[Z              Z[[Z           Z                        LX", // Y=20
                    "XL    O                   Z           ZZZZ              ZZZZ                                             ZZZZ              ZZZZ           Z        O               LX", // Y=21
                    "I                                                                                                                                                                   I", // Y=22
                    "I                                                                                                                                                                   I", // Y=23
                    "L                         L                                                                                                               L                         L", // Y=24
                    "I                                                                                                                                                                   I", // Y=25
                    "I                                                                                                                                                                   I", // Y=26
                    "XL    O                   Z           ZZZZ              ZZZZ                                             ZZZZ              ZZZZ           Z              O         LX", // Y=27
                    "XL                        Z           Z[[Z              Z[[Z                                             Z[[Z              Z[[Z           Z               O        LX", // Y=28
                    "Y I                       Z   L   L   Z  Z              Z  Z   L   L                             L   L   Z  Z              Z  Z   L   L   Z                       I Y", // Y=29
                    "  I                       Z    OIO    Z  Z              Z  Z    OIO                               OIO    Z  Z              Z  Z    OIO    Z                       I  ", // Y=30
                    "L LL[                     L           O  O              O  O           HDH                 HDH           O  O              O  O           L                     [LL L", // Y=31
                    "   I             O        L           O  O              O  O                                             O  O              O  O           L       O              I   ", // Y=32
                    "    I                     K           P  P              P  P                                             P  P              P  P           K                     I    ", // Y=33
                    "    I                     K           X                    X                                             X                    X           K                     I    ", // Y=34
                    "     I        O           K          DX                    X                                             X                    X          DK            O       I     ", // Y=35
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                                             X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=36
                    "                                      VWW      U   U     WWV                                             VWW     U   U      WWV                                      ", // Y=37
                    "                                      X                    X                                             X                    X                                      ", // Y=38
                    "                                      X  O              O  X                                             X  O              O  X                                      ", // Y=39
                    "                                      O  OOOOOOOOOOOOOOOO  O                                             O  OOOOOOOOOOOOOOOO  O                                      ", // Y=40
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=41
                    "                                                                      QQ         Q                    Q                                                              ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                B                                                                                                   B                                ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=18
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                B                                                                                                   B                                ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                             Q                    Q        QQ                                                                        ", // Y=6
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=7
                    "                                      X   OOOOIIIIIIIOOO   X                                             X   OOOIIIIIIIOOOO   X                                      ", // Y=8
                    "                                      X  O              O  X                                             X  O              O  X                                      ", // Y=9
                    "                                      X                    X                                             X                    X                                      ", // Y=10
                    "L     LLIIIIIIIIIIIIIIIIIIL           VWW      U   U     WWV                                             VWW     U   U      WWV           LIIIIIIIIIIIIIIIIIILL     L", // Y=11
                    "     I        O           K           X                    X                   HIIIIIH                   X                    X           K          O         I     ", // Y=12
                    "     I                    K D         X                    X                   HHHHHHH                   X                    X D         K                    I     ", // Y=13
                    "    I                     K           X                    X                                             X                    X           K                     I    ", // Y=14
                    "   I             O        K           P  P              P  P                                             P  P              P  P           K              O       I   ", // Y=15
                    "   I                      L           O  O              O  O           HDH                 HDH           O  O              O  O           L                      I   ", // Y=16
                    "  I                       Z    OIO    Z  Z              Z  Z    OIO                               OIO    Z  Z              Z  Z    OIO    Z                       I  ", // Y=17
                    "~LL[                      Z   LGGGL   ZZZZZZZZZZZZZZZZZZZZZZ   LGGGL                             LGGGL   ZZZZZZZZZZZZZZZZZZZZZZ   LGGGL   Z                      [LL~", // Y=18
                    "XL      O                 Z           Z[[Z              Z[[Z                                             Z[[Z              Z[[Z           Z      O                 LX", // Y=19
                    "I                                                                                                                                                 O                 I", // Y=20
                    "I                                                                                                                                                                   I", // Y=21
                    "I                                                                                                                                                                   I", // Y=22
                    "I                                                                                                                                                                   I", // Y=23
                    "L                         L                                                                                                               L                         L", // Y=24
                    "I                                                                                                                                                                   I", // Y=25
                    "I                                                                                                                                                                   I", // Y=26
                    "I                                                                                                                                                                   I", // Y=27
                    "I      O                                                                                                                                                            I", // Y=28
                    "XL      O                 Z           Z[[Z              Z[[Z                                             Z[[Z              Z[[Z           Z                O       LX", // Y=29
                    "~LL[                      Z   LGGGL   ZZZZZZZZZZZZZZZZZZZZZZ   LGGGL                             LGGGL   ZZZZZZZZZZZZZZZZZZZZZZ   LGGGL   Z                      [LL~", // Y=30
                    "  I                       Z    OIO    Z  Z              Z  Z    OIO                               OIO    Z  Z              Z  Z    OIO    Z                       I  ", // Y=31
                    "   I                      L           O  O              O  O           HDH                 HDH           O  O              O  O           L                      I   ", // Y=32
                    "   I             O        K           P  P              P  P                                             P  P              P  P           K       O              I   ", // Y=33
                    "    I                     K           X                    X                                             X                    X           K                     I    ", // Y=34
                    "     I                    K         D X                    X                   HHHHHHH                   X                    X         D K                    I     ", // Y=35
                    "     I        O           K           X                    X                   HIIIIIH                   X                    X           K            O       I     ", // Y=36
                    "L     LLIIIIIIIIIIIIIIIIIIL           VWW      U   U     WWV                                             VWW     U   U      WWV           LIIIIIIIIIIIIIIIIIILL     L", // Y=37
                    "                                      X                    X                                             X                    X                                      ", // Y=38
                    "                                      X  O              O  X                                             X  O              O  X                                      ", // Y=39
                    "                                      X   OOOOIIIIIIIOOO   X                                             X   OOOIIIIIIIOOOO   X                                      ", // Y=40
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=41
                    "                                                                        QQ        Q                    Q                                                             ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                B                                                                                                   B                                ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=19
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                B                                                HHH                                                B                                ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                            Q                    Q       QQ                                                                          ", // Y=6
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=7
                    "                                      X   OOOOOOOOOOOOOO   X                                             X   OOOOOOOOOOOOOO   X                                      ", // Y=8
                    "                                      X  O              O  X                     H H                     X  O              O  X                                      ", // Y=9
                    "                                      X                    X                     H H                     X                    X                                      ", // Y=10
                    "L     LLIIIIIIIIIIIIIIIIIIL           VWW      U   U     WWV                   HHHHHHH                   VWW     U   U      WWV           LIIIIIIIIIIIIIIIIIILL     L", // Y=11
                    "     I        O           K           X                    X                  H       H                  X                    X           K          O         I     ", // Y=12
                    "    I                     K           X                    X                  HZZZZZZZH                  X                    X D         K                     I    ", // Y=13
                    "    I           O         K           X                    X                                             X                    X           K               O     I    ", // Y=14
                    "   I                      K           P  P              P  P                                             P  P              P  P           K                      I   ", // Y=15
                    "  I                       L    OIO    O  O              O  O    OIO    HDH                 HDH    OIO    O  O              O  O    OIO    L                       I  ", // Y=16
                    "Y I                       Z   L   L   Z  Z              Z  Z   L   L                             L   L   Z  Z              Z  Z   L   L   Z                       I Y", // Y=17
                    "XL       O                Z           Z[[Z              Z[[Z                                             Z[[Z              Z[[Z           Z     O                  LX", // Y=18
                    "LL                                                                                                                                                                 LL", // Y=19
                    "I                                                                                                                                                                   I", // Y=20
                    "I                                                                                                                                                                   I", // Y=21
                    "I                                                                                                                                                                   I", // Y=22
                    "I                                                                                                                                                                   I", // Y=23
                    "L                         L                                                                                                               L                         L", // Y=24
                    "I                                                                                                                                                                   I", // Y=25
                    "I                                                                                                                                                                   I", // Y=26
                    "I                                                                                                                                                                   I", // Y=27
                    "I                                                                                                                                                                   I", // Y=28
                    "LL                                                                                                                                                                 LL", // Y=29
                    "XL       O                Z           Z[[Z              Z[[Z                                             Z[[Z              Z[[Z           Z                 O      LX", // Y=30
                    "Y I                       Z   L   L   Z  Z              Z  Z   L   L                             L   L   Z  Z              Z  Z   L   L   Z                       I Y", // Y=31
                    "  I                       L    OIO    O  O              O  O    OIO    HDH                 HDH    OIO    O  O              O  O    OIO    L                       I  ", // Y=32
                    "   I                      K           P  P              P  P                                             P  P              P  P           K                      I   ", // Y=33
                    "    I           O         K           X                    X                                             X                    X           K      O              I    ", // Y=34
                    "    I                     K         D X                    X                  HZZZZZZZH                  X                    X         D K                     I    ", // Y=35
                    "     I        O           K           X                    X                  H       H                  X                    X           K            O       I     ", // Y=36
                    "L     LLIIIIIIIIIIIIIIIIIIL           VWW      U   U     WWV                   HHHHHHH                   VWW     U   U      WWV           LIIIIIIIIIIIIIIIIIILL     L", // Y=37
                    "                                      X                    X                     H H                     X                    X                                      ", // Y=38
                    "                                      X  O              O  X                     H H                     X  O              O  X                                      ", // Y=39
                    "                                      X   OOOOOOOOOOOOOO   X                                             X   OOOOOOOOOOOOOO   X                                      ", // Y=40
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=41
                    "                                                                          QQ       Q                    Q                                                            ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                B                                                HHH                                                B                                ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=20
                    "                                                                                                                                                                     ", // Y=0
                    "                                B                                                CCC                                                B                                ", // Y=1
                    "                                B                                               HCCCH                                               B                                ", // Y=2
                    "                                                                                 CCC                                                                                 ", // Y=3
                    "                                                                                 CCCOO                                                                               ", // Y=4
                    "                                                           Q                   OPCCCOP QQ                                                                            ", // Y=5
                    "                                      OTTO              OTTO                   PQCCCOO                   OTTO              OTTO                                      ", // Y=6
                    "                                      VWWVOOOOOOOOOOOOOOVWWV                   OPCCC                     VWWVOOOOOOOOOOOOOOVWWV                                      ", // Y=7
                    "                                      VWW                WWV                     CCC                     VWW                WWV                                      ", // Y=8
                    "                                      VWWO              OWWV                     CCC                     VWWO              OWWV                                      ", // Y=9
                    "                                      VWW                WWV                     CCC                     VWW                WWV                                      ", // Y=10
                    "L     LLIIIIIIIIIIIIIIIIIIL           VIIIIIIIIIIIIIIIIIIIIV                  HIIIJIIIH                  VIIIIIIIIIIIIIIIIIIIIV           LIIIIIIIIIIIIIIIIIILL     L", // Y=11
                    "     I        O           K           VWW                WWV                 H         H                 VWW                WWV           K          O         I     ", // Y=12
                    "    I                     K  D        VWW                WWV                 HZZ     ZZH                 VWW                WWV  D        K                     I    ", // Y=13
                    "   I            O         K           VWW                WWV                                             VWW                WWV           K               O      I   ", // Y=14
                    "   I                      K    OIO    PWWP              PWWP    OIO    HDH                 HDH    OIO    PWWP              PWWP    OIO    K                      I   ", // Y=15
                    "Y I                       L   L   L   OWWO              OWWO   L   L                             L   L   OWWO              OWWO   L   L   L                       I Y", // Y=16
                    "XL        O               Z           Z[[Z              Z[[Z                                             Z[[Z              Z[[Z           Z    O                   LX", // Y=17
                    "I                                                                                                                                                                   I", // Y=18
                    "I                                                                                                                                                                   I", // Y=19
                    "L                                                                                                                                                                   L", // Y=20
                    "I                                                                                                                                                                   I", // Y=21
                    "I                                                                                                                                                                   I", // Y=22
                    "I                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII                               IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         I", // Y=23
                    "L                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII               P               IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         L", // Y=24
                    "I                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII                               IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         I", // Y=25
                    "I                                                                                                                                                                   I", // Y=26
                    "I                                                                                                                                                                   I", // Y=27
                    "L                                                                                                                                                                   L", // Y=28
                    "I                                                                                                                                                                   I", // Y=29
                    "I                                                                                                                                                            O      I", // Y=30
                    "XL        O               Z           Z[[Z              Z[[Z                                             Z[[Z              Z[[Z           Z                        LX", // Y=31
                    "Y I                       L   L   L   OWWO              OWWO   L   L                             L   L   OWWO              OWWO   L   L   L                       I Y", // Y=32
                    "   I                      K    OIO    PWWP              PWWP    OIO    HDH                 HDH    OIO    PWWP              PWWP    OIO    K                      I   ", // Y=33
                    "   I            O         K           VWW                WWV                                             VWW                WWV           K      O               I   ", // Y=34
                    "    I                     K        D  VWW                WWV                 HZZ     ZZH                 VWW                WWV        D  K                     I    ", // Y=35
                    "     I        O           K           VWW                WWV                 H         H                 VWW                WWV           K            O       I     ", // Y=36
                    "L     LLIIIIIIIIIIIIIIIIIIL           VIIIIIIIIIIIIIIIIIIIIV                  HIIIJIIIH                  VIIIIIIIIIIIIIIIIIIIIV           LIIIIIIIIIIIIIIIIIILL     L", // Y=37
                    "                                      VWW                WWV                     CCC                     VWW                WWV                                      ", // Y=38
                    "                                      VWWO              OWWV                     CCC                     VWWO              OWWV                                      ", // Y=39
                    "                                      VWW                WWV                     CCC                     VWW                WWV                                      ", // Y=40
                    "                                      VWWVOOOOOOOOOOOOOOVWWV                     CCCPO                   VWWVOOOOOOOOOOOOOOVWWV                                      ", // Y=41
                    "                                      OTTO              OTTO                   OOCCCQP                   OTTO              OTTO                                      ", // Y=42
                    "                                                                            QQ POCCCPO                   Q                                                           ", // Y=43
                    "                                                                               OOCCC                                                                                 ", // Y=44
                    "                                                                                 CCC                                                                                 ", // Y=45
                    "                                B                                               HCCCH                                               B                                ", // Y=46
                    "                                B                                                CCC                                                B                                ", // Y=47
            },
            { // Z=21
                    "                                                                                                                                                                     ", // Y=0
                    "                                B                                               CDDDC                                               B                                ", // Y=1
                    "                                                                               HCIJICH                                                                               ", // Y=2
                    "                                                                                C   C                                                                                ", // Y=3
                    "                                                                                C   CP                                                                               ", // Y=4
                    "                                           FMMMMMMMMMMM   Q                    OCIJICQQ                       MMMMMMMMMMMF                                           ", // Y=5
                    "                                      OSSO FMMNNNNNNNMM OSSO                   OC   CP                   OSSO MMNNNNNNNMMF OSSO                                      ", // Y=6
                    "                                      V  VOFMMMMMMMMMMMOV  V                   OC   C                    V  VOMMMMMMMMMMMFOV  V                                      ", // Y=7
                    "                                      X                    X                    CIJIC                    X                    X                                      ", // Y=8
                    "                                      X  O              O  X                    C   C                    X  O              O  X                                      ", // Y=9
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                    C   C                    X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=10
                    "      I       O           K           V        U   U       V                 HIIIIJIIIIH                 V       U   U        V           K          O        I      ", // Y=11
                    "     I                    K           X                    X                H           H                X                    X           K                    I     ", // Y=12
                    "    I          O          K           X                    X                HZZ       ZZH                X                    X  D        K                O    I    ", // Y=13
                    "   I                      K           X                    X                                             X                    X           K                      I   ", // Y=14
                    "  I                       K    OIO    O  O              O  O    OIO    HDH                 HDH    OIO    O  O              O  O    OIO    K                       I  ", // Y=15
                    "Y I                       L   L   L   O  O              O  O   L   L                             L   L   O  O              O  O   L   L   L                       I Y", // Y=16
                    "XL         O              Z           ZZZZ              ZZZZ                                             ZZZZ              ZZZZ           Z   O                    LX", // Y=17
                    "I                                                                                                                                                                   I", // Y=18
                    "I                                                                                                                                                                   I", // Y=19
                    "I                                                                                                                                                                   I", // Y=20
                    "L                                                                                                                                                                   L", // Y=21
                    "I                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIF                             FIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         I", // Y=22
                    "I           KKK           N                                        P              G              P                                        N           KKK           I", // Y=23
                    "L           K]K           N^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^P             G[G             P^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^N           K]K           L", // Y=24
                    "I           KKK           N                                        P              G              P                                        N           KKK           I", // Y=25
                    "I                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIF                             FIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         I", // Y=26
                    "L                                                                                                                                                                   L", // Y=27
                    "I                                                                                                                                                                   I", // Y=28
                    "I                                                                                                                                                                   I", // Y=29
                    "I                                                                                                                                                                   I", // Y=30
                    "XL         O              Z           ZZZZ              ZZZZ                                             ZZZZ              ZZZZ           Z                   O    LX", // Y=31
                    "Y I                       L   L   L   O  O              O  O   L   L                             L   L   O  O              O  O   L   L   L                       I Y", // Y=32
                    "  I                       K    OIO    O  O              O  O    OIO    HDH                 HDH    OIO    O  O              O  O    OIO    K                       I  ", // Y=33
                    "   I                      K           X                    X                                             X                    X           K                      I   ", // Y=34
                    "    I          O          K        D  X                    X                HZZ       ZZH                X                    X        D  K      O              I    ", // Y=35
                    "     I                    K           X                    X                H           H                X                    X           K                    I     ", // Y=36
                    "      I       O           K           V        U   U       V                 HIIIIJIIIIH                 V       U   U        V           K            O      I      ", // Y=37
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                    C   C                    X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=38
                    "                                      X  O              O  X                    C   C                    X  O              O  X                                      ", // Y=39
                    "                                      X                    X                    CIJIC                    X                    X                                      ", // Y=40
                    "                                      V  VOFMMMMMMMMMMMOV  V                    C   CO                   V  VOMMMMMMMMMMMFOV  V                                      ", // Y=41
                    "                                      OSSO FMMNNNNNNNMM OSSO                   PC   CO                   OSSO MMNNNNNNNMMF OSSO                                      ", // Y=42
                    "                                           FMMMMMMMMMMM                       QQCIJICO                    Q   MMMMMMMMMMMF                                           ", // Y=43
                    "                                                                               PC   C                                                                                ", // Y=44
                    "                                                                                C   C                                                                                ", // Y=45
                    "                                                                               HCIJICH                                                                               ", // Y=46
                    "                                B                                               CDDDC                                               B                                ", // Y=47
            },
            { // Z=22
                    "                                                                                 AAA                                                                                 ", // Y=0
                    "                                B                                              CDEEEDC                                              B                                ", // Y=1
                    "                                                                              HCIIJIICH                                                                              ", // Y=2
                    "                                                                               C     C                                                                               ", // Y=3
                    "                                           FMMMMMMMMMMM                        C     C                        MMMMMMMMMMMF                                           ", // Y=4
                    "                                           MM         MFPQ                     CIIJIIC                       FM         MM                                           ", // Y=5
                    "                                      OSSOFMM         MFOSSO                   C     C                   OSSOFM         MMFOSSO                                      ", // Y=6
                    "                                      V  VOMM         MFV  V                   C     C                   V  VFM         MMOV  V                                      ", // Y=7
                    "                                      X    FMMMMMMMMMMM    X                   CIIJIIC                   X    MMMMMMMMMMMF    X                                      ", // Y=8
                    "                                      X  O              O  X                   C     C                   X  O              O  X                                      ", // Y=9
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                   C     C                   X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=10
                    "      I       O           K           V        U   U       V                 HIIIIJIIIIH                 V       U   U        V           K          O        I      ", // Y=11
                    "     I                    K           X                    X                I           I                X                    X           K                    I     ", // Y=12
                    "    I          O          K   D       X                    X                HZ         ZH                X                    X   D       K                O    I    ", // Y=13
                    "   I                      K    OIO    X                    X    OIO    HSH                 HSH    OIO    X                    X    OIO    K                      I   ", // Y=14
                    "Y I                       K   L   L   O  O              O  O   L   L    G                   G    L   L   O  O              O  O   L   L   K                       I Y", // Y=15
                    "XL          O             Z           ZRRZ              ZRRZ                                             ZRRZ              ZRRZ           Z  O                     LX", // Y=16
                    "I                                                                                                                                                                   I", // Y=17
                    "I                                                                                                                                                                   I", // Y=18
                    "I                                                                                                                                                                   I", // Y=19
                    "I                                                                                                                                                                   I", // Y=20
                    "I                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIF                             FIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         I", // Y=21
                    "L          N_E_N          P                                        [[[[P                     P[[[[                                        P          N_E_N          L", // Y=22
                    "I          _   _          [                                                      IKI                                                      [          _   _          I", // Y=23
                    "L          E ] E          [                                                      K[K                                                      [          E ] E          L", // Y=24
                    "I          _   _          [                                                      IKI                                                      [          _   _          I", // Y=25
                    "L          N_E_N          P                                        [[[[P                     P[[[[                                        P          N_E_N          L", // Y=26
                    "I                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIF                             FIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         I", // Y=27
                    "I                                                                                                                                                                   I", // Y=28
                    "I                                                                                                                                                                   I", // Y=29
                    "I                                                                                                                                                                   I", // Y=30
                    "I                                                                                                                                                                   I", // Y=31
                    "XL          O             Z           ZRRZ              ZRRZ                                             ZRRZ              ZRRZ           Z                    O   LX", // Y=32
                    "Y I                       K   L   L   O  O              O  O   L   L    G                   G    L   L   O  O              O  O   L   L   K                       I Y", // Y=33
                    "   I                      K    OIO    X                    X    OIO    HSH                 HSH    OIO    X                    X    OIO    K                      I   ", // Y=34
                    "    I          O          K       D   X                    X                HZ         ZH                X                    X       D   K      O              I    ", // Y=35
                    "     I                    K           X                    X                I           I                X                    X           K                    I     ", // Y=36
                    "      I       O           K           V        U   U       V                 HIIIIJIIIIH                 V       U   U        V           K            O      I      ", // Y=37
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                   C     C                   X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=38
                    "                                      X  O              O  X                   C     C                   X  O              O  X                                      ", // Y=39
                    "                                      X    FMMMMMMMMMMM    X                   CIIJIIC                   X    MMMMMMMMMMMF    X                                      ", // Y=40
                    "                                      V  VOMM         MFV  V                   C     C                   V  VFM         MMOV  V                                      ", // Y=41
                    "                                      OSSOFMM         MFOSSO                   C     C                   OSSOFM         MMFOSSO                                      ", // Y=42
                    "                                           MM         MF                       CIIJIIC                     QPFM         MM                                           ", // Y=43
                    "                                           FMMMMMMMMMMM                        C     C                        MMMMMMMMMMMF                                           ", // Y=44
                    "                                                                               C     C                                                                               ", // Y=45
                    "                                                                              HCIIJIICH                                                                              ", // Y=46
                    "                                B                                              CDEEEDC                                              B                                ", // Y=47
            },
            { // Z=23
                    "                                                                                AAAAA                                                                                ", // Y=0
                    "                                F                                             CDEGGGEDC                                             F                                ", // Y=1
                    "                               FFF                                           HCIIIKIIICH                                           FFF                               ", // Y=2
                    "                                           FMMMMMMMMMMM                       C       C                       MMMMMMMMMMM                                            ", // Y=3
                    "                                           MM         MFP                     C       C                      FM         MM                                           ", // Y=4
                    "                                          M            RQ                     CIIISIIIC                      R            M                                          ", // Y=5
                    "                                      OSSOM            ROSSO                  C       C                  OSSOR            MOSSO                                      ", // Y=6
                    "                                      V  VM            RV  V                  C       C                  V  VR            MV  V                                      ", // Y=7
                    "                           F         FX    MM         MF   X                  CIIIKIIIC                  X   FM         MM    XF         F                           ", // Y=8
                    "                           F         FX  O FMMMMMMMMMMM O  X                 HC       CH                 X  O MMMMMMMMMMMF O  XF         F                           ", // Y=9
                    "L     LLIIIIIIIIIIIIIIIIIILF         FX                    X                 HC       CH                 X                    XF         FLIIIIIIIIIIIIIIIIIILL     L", // Y=10
                    "      I       P           K           V        U   U       V                 HIIIISIIIIH                 V       U   U        V           K          P        I      ", // Y=11
                    "     I                    K           X                    X                I           I                X                    X           K                    I     ", // Y=12
                    "    I         T           K   D [D    X                    X     [          HZ         ZH          [     X                    X   D [D    K                 T   I    ", // Y=13
                    "   I                      K    PIP    X                    X    PIP    HSH                 HSH    PIP    X                    X    PIP    K                      I   ", // Y=14
                    "Y I                       K   L   L   O  O              O  O   L   L    G                   G    L   L   O  O              O  O   L   L   K [                     I Y", // Y=15
                    "XL           [            Z           ZRRZ              ZRRZ                                             ZRRZ              ZRRZ           Z                        LX", // Y=16
                    "I                                                                                                                                                                   I", // Y=17
                    "I                                                                                                                                                                   I", // Y=18
                    "I                                                                                                                                                                   I", // Y=19
                    "I                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII                               IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         I", // Y=20
                    "I           KKK           N                                        P              G              P                                        N           KKK           I", // Y=21
                    "I          _   _          [                                                      IKI                                                      [          _   _          I", // Y=22
                    "HXX       K     K         `                                                     INKNI                                                     `         K     K       XXH", // Y=23
                    "~         K  ]  K         `  D   D   D   D   D   D   D   D   D   D [           GKK[KKG           [  D   D   D   D   D   D   D   D   D   D `         K  ]  K         ~", // Y=24
                    "HXX       K     K         `                                                     INKNI                                                     `         K     K       XXH", // Y=25
                    "I          _   _          [                                                      IKI                                                      [          _   _          I", // Y=26
                    "I           KKK           N                                        P              G              P                                        N           KKK           I", // Y=27
                    "I                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII                               IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         I", // Y=28
                    "I                                                                                                                                                                   I", // Y=29
                    "I                                                                                                                                                                   I", // Y=30
                    "I                                                                                                                                                                   I", // Y=31
                    "XL                        Z           ZRRZ              ZRRZ                                             ZRRZ              ZRRZ           Z                        LX", // Y=32
                    "Y I          [            K   L   L   O  O              O  O   L   L    G                   G    L   L   O  O              O  O   L   L   K                     [ I Y", // Y=33
                    "   I                      K    PIP    X                    X    PIP    HSH                 HSH    PIP    X                    X    PIP    K                      I   ", // Y=34
                    "    I         T           K    D[ D   X                    X     [          HZ         ZH          [     X                    X    D[ D   K     T               I    ", // Y=35
                    "     I                    K           X                    X                I           I                X                    X           K                    I     ", // Y=36
                    "      I       P           K           V        U   U       V                 HIIIISIIIIH                 V       U   U        V           K            P      I      ", // Y=37
                    "L     LLIIIIIIIIIIIIIIIIIILF         FX                    X                 HC       CH                 X                    XF         FLIIIIIIIIIIIIIIIIIILL     L", // Y=38
                    "                           F         FX  O FMMMMMMMMMMM O  X                 HC       CH                 X  O MMMMMMMMMMMF O  XF         F                           ", // Y=39
                    "                           F         FX    MM         MF   X                  CIIIKIIIC                  X   FM         MM    XF         F                           ", // Y=40
                    "                                      V  VM            RV  V                  C       C                  V  VR            MV  V                                      ", // Y=41
                    "                                      OSSOM            ROSSO                  C       C                  OSSOR            MOSSO                                      ", // Y=42
                    "                                          M            R                      CIIISIIIC                     QR            M                                          ", // Y=43
                    "                                           MM         MF                      C       C                     PFM         MM                                           ", // Y=44
                    "                                           FMMMMMMMMMMM                       C       C                       MMMMMMMMMMMF                                           ", // Y=45
                    "                               FFF                                           HCIIIKIIICH                                           FFF                               ", // Y=46
                    "                                F                                             CDEGGGEDC                                             F                                ", // Y=47
            },
            { // Z=24
                    "                                                                                AAAAA                                                                                ", // Y=0
                    "                               EEE                                            CDEGGGEDC                                            EEE                               ", // Y=1
                    "                              LLFLL                                          HCJJKKKJJCH                                          LLFLL                              ", // Y=2
                    "                              L   L        FMMNNNNNNNMM                       C       C                       MMNNNNNNNMM         L   L                              ", // Y=3
                    "                             LL   LL     OOMM         MFO                     C       C                     OFM         MMOO     LL   LL                             ", // Y=4
                    "                             L     L    OOM            ROO                    CJJSSSJJC                    OOR            MOO    L     L                             ", // Y=5
                    "                            LL     LL OOOONUUUUUUUUUUUUUOOOO                  C       C                  OOOOUUUUUUUUUUUUUNOOOO LL     LL                            ", // Y=6
                    "                            L       L O  OM            RO  O                  C       C                  O  OR            MO  O L       L                            ", // Y=7
                    "                           LL       LLO  OFMM         MFO  O                  CJJKKKJJC                  O  OFM         MMFO  OLL       LL                           ", // Y=8
                    "                           L         LO  O FMMNNNNNNNMM O  O                  C       C                  O  O MMNNNNNNNMMF O  OL         L                           ", // Y=9
                    "LYYYYYLLLLLLLLLLLLLLLLLLLLLL         LO  O     U   U    O  O                  C       C                  O  O    U   U     O  OL         LLLLLLLLLLLLLLLLLLLLLLYYYYYL", // Y=10
                    "L    LLLPPP   P           L           O  O     U   U    O  O                 HJJJSSSJJJH                 O  O    U   U     O  O           L          P    PPPLLL    L", // Y=11
                    "L   LLE                   L           O  O              O  O                I           I                O  O              O  O           L                   ELL   L", // Y=12
                    "L  LL         T           L    D[D    O  O              O  O     [          HZ         ZH          [     O  O              O  O    D[D    L                 T   LL  L", // Y=13
                    "L LL                      L    PIP    O  O              O  O    PIP    HSH                 HSH    PIP    O  O              O  O    PIP    L                      LL L", // Y=14
                    "~LL                       L   EGGGE   O  O              O  O   EGGGE   [G[                 [G[   EGGGE   O  O              O  O   EGGGE   L [                     LL~", // Y=15
                    "LL~           [           Z           ZZZZ              ZZZZ                                             ZZZZ              ZZZZ           Z                       ~LL", // Y=16
                    "L                         L                                                                                                               L                         L", // Y=17
                    "L                         L                                                                                                               L                         L", // Y=18
                    "L                         L                                                                                                               L                         L", // Y=19
                    "L                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII               P               IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         L", // Y=20
                    "L           K]K           N^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^P             G[G             P^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^N           K]K           L", // Y=21
                    "L          E ] E          [                                                      K[K                                                      [          E ] E          L", // Y=22
                    "~         K  ]  K         ` D   D   D   D   D   D   D   D   D   D  [           GKK[KKG           [ D   D   D   D   D   D   D   D   D   D  `         K  ]  K         ~", // Y=23
                    "~EEEE     ]]]E]]]         `NNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNN          P[[[ [[[P          NNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNN`         ]]]E]]]     EEEE~", // Y=24
                    "~         K  ]  K         `   D   D   D   D   D   D   D   D   D   D[           GKK[KKG           [   D   D   D   D   D   D   D   D   D   D`         K  ]  K         ~", // Y=25
                    "L          E ] E          [                                                      K[K                                                      [          E ] E          L", // Y=26
                    "L           K]K           N^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^P             G[G             P^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^N           K]K           L", // Y=27
                    "L                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII               P               IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         L", // Y=28
                    "L                         L                                                                                                               L                         L", // Y=29
                    "L                         L                                                                                                               L                         L", // Y=30
                    "L                         L                                                                                                               L                         L", // Y=31
                    "LL~                       Z           ZZZZ              ZZZZ                                             ZZZZ              ZZZZ           Z                       ~LL", // Y=32
                    "~LL           [           L   EGGGE   O  O              O  O   EGGGE   [G[                 [G[   EGGGE   O  O              O  O   EGGGE   L                     [ LL~", // Y=33
                    "L LL                      L    PIP    O  O              O  O    PIP    HSH                 HSH    PIP    O  O              O  O    PIP    L                      LL L", // Y=34
                    "L  LL         T           L    D[D    O  O              O  O     [          HZ         ZH          [     O  O              O  O    D[D    L     T               LL  L", // Y=35
                    "L   LLE                   L           O  O              O  O                I           I                O  O              O  O           L                   ELL   L", // Y=36
                    "L    LLLPPP   P           L           O  O     U   U    O  O                 HJJJSSSJJJH                 O  O    U   U     O  O           L            P  PPPLLL    L", // Y=37
                    "LYYYYYLLLLLLLLLLLLLLLLLLLLLL         LO  O     U   U    O  O                  C       C                  O  O    U   U     O  OL         LLLLLLLLLLLLLLLLLLLLLLYYYYYL", // Y=38
                    "                           L         LO  O FMMNNNNNNNMM O  O                  C       C                  O  O MMNNNNNNNMMF O  OL         L                           ", // Y=39
                    "                           LL       LLO  OFMM         MFO  O                  CJJKKKJJC                  O  OFM         MMFO  OLL       LL                           ", // Y=40
                    "                            L       L O  OM            RO  O                  C       C                  O  OR            MO  O L       L                            ", // Y=41
                    "                            LL     LL OOOONUUUUUUUUUUUUUOOOO                  C       C                  OOOOUUUUUUUUUUUUUNOOOO LL     LL                            ", // Y=42
                    "                             L     L    OOM            ROO                    CJJSSSJJC                    OOR            MOO    L     L                             ", // Y=43
                    "                             LL   LL     OOMM         MFO                     C       C                     OFM         MMOO     LL   LL                             ", // Y=44
                    "                              L   L        FMMNNNNNNNMM                       C       C                       MMNNNNNNNMMF        L   L                              ", // Y=45
                    "                              LLFLL                                          HCJJKKKJJCH                                          LLFLL                              ", // Y=46
                    "                               EEE                                            CDEGGGEDC                                            EEE                               ", // Y=47
            },
            { // Z=25
                    "                                                                                AAAAA                                                                                ", // Y=0
                    "                                F                                             CDEGGGEDC                                             F                                ", // Y=1
                    "                               FFF                                           HCIIIKIIICH                                           FFF                               ", // Y=2
                    "                                           FMMMMMMMMMMM                       C       C                       MMMMMMMMMMM                                            ", // Y=3
                    "                                           MM         MF                      C       C                     PFM         MM                                           ", // Y=4
                    "                                          M            R                      CIIISIIIC                     QR            M                                          ", // Y=5
                    "                                      OSSOM            ROSSO                  C       C                  OSSOR            MOSSO                                      ", // Y=6
                    "                                      V  VM            RV  V                  C       C                  V  VR            MV  V                                      ", // Y=7
                    "                           F         FX    MM         MF   X                  CIIIKIIIC                  X   FM         MM    XF         F                           ", // Y=8
                    "                           F         FX  O FMMMMMMMMMMM O  X                 HC       CH                 X  O MMMMMMMMMMMF O  XF         F                           ", // Y=9
                    "L     LLIIIIIIIIIIIIIIIIIILF         FX                    X                 HC       CH                 X                    XF         FLIIIIIIIIIIIIIIIIIILL     L", // Y=10
                    "      I       P           K           V        U   U       V                 HIIIISIIIIH                 V       U   U        V           K          P        I      ", // Y=11
                    "     I                    K           X                    X                I           I                X                    X           K                    I     ", // Y=12
                    "    I         T           K    D[ D   X                    X     [          HZ         ZH          [     X                    X    D[ D   K                 T   I    ", // Y=13
                    "   I                      K    PIP    X                    X    PIP    HSH                 HSH    PIP    X                    X    PIP    K                      I   ", // Y=14
                    "Y I                       K   L   L   O  O              O  O   L   L    G                   G    L   L   O  O              O  O   L   L   K [                     I Y", // Y=15
                    "XL             [          Z           ZRRZ              ZRRZ                                             ZRRZ              ZRRZ           Z                        LX", // Y=16
                    "I                                                                                                                                                                   I", // Y=17
                    "I                                                                                                                                                                   I", // Y=18
                    "I                                                                                                                                                                   I", // Y=19
                    "I                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII                               IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         I", // Y=20
                    "I           KKK           N                                        P              G              P                                        N           KKK           I", // Y=21
                    "I          _   _          [                                                      IKI                                                      [          _   _          I", // Y=22
                    "HXX       K     K         `                                                     INKNI                                                     `         K     K       XXH", // Y=23
                    "~         K  ]  K         `D   D   D   D   D   D   D   D   D   D   [           GKK[KKG           [D   D   D   D   D   D   D   D   D   D   `         K  ]  K         ~", // Y=24
                    "HXX       K     K         `                                                     INKNI                                                     `         K     K       XXH", // Y=25
                    "I          _   _          [                                                      IKI                                                      [          _   _          I", // Y=26
                    "I           KKK           N                                        P              G              P                                        N           KKK           I", // Y=27
                    "I                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII                               IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         I", // Y=28
                    "I                                                                                                                                                                   I", // Y=29
                    "I                                                                                                                                                                   I", // Y=30
                    "I                                                                                                                                                                   I", // Y=31
                    "XL                        Z           ZRRZ              ZRRZ                                             ZRRZ              ZRRZ           Z                        LX", // Y=32
                    "Y I            [          K   L   L   O  O              O  O   L   L    G                   G    L   L   O  O              O  O   L   L   K                     [ I Y", // Y=33
                    "   I                      K    PIP    X                    X    PIP    HSH                 HSH    PIP    X                    X    PIP    K                      I   ", // Y=34
                    "    I         T           K   D [D    X                    X     [          HZ         ZH          [     X                    X   D [D    K     T               I    ", // Y=35
                    "     I                    K           X                    X                I           I                X                    X           K                    I     ", // Y=36
                    "      I       P           K           V        U   U       V                 HIIIISIIIIH                 V       U   U        V           K            P      I      ", // Y=37
                    "L     LLIIIIIIIIIIIIIIIIIILF         FX                    X                 HC       CH                 X                    XF         FLIIIIIIIIIIIIIIIIIILL     L", // Y=38
                    "                           F         FX  O FMMMMMMMMMMM O  X                 HC       CH                 X  O MMMMMMMMMMMF O  XF         F                           ", // Y=39
                    "                           F         FX    MM         MF   X                  CIIIKIIIC                  X   FM         MM    XF         F                           ", // Y=40
                    "                                      V  VM            RV  V                  C       C                  V  VR            MV  V                                      ", // Y=41
                    "                                      OSSOM            ROSSO                  C       C                  OSSOR            MOSSO                                      ", // Y=42
                    "                                          M            RQ                     CIIISIIIC                      R            M                                          ", // Y=43
                    "                                           MM         MFP                     C       C                      FM         MM                                           ", // Y=44
                    "                                           FMMMMMMMMMMM                       C       C                       MMMMMMMMMMMF                                           ", // Y=45
                    "                               FFF                                           HCIIIKIIICH                                           FFF                               ", // Y=46
                    "                                F                                             CDEGGGEDC                                             F                                ", // Y=47
            },
            { // Z=26
                    "                                                                                 AAA                                                                                 ", // Y=0
                    "                                B                                              CDEEEDC                                              B                                ", // Y=1
                    "                                                                              HCIIJIICH                                                                              ", // Y=2
                    "                                                                               C     C                                                                               ", // Y=3
                    "                                           FMMMMMMMMMMM                        C     C                        MMMMMMMMMMMF                                           ", // Y=4
                    "                                           MM         MF                       CIIJIIC                     QPFM         MM                                           ", // Y=5
                    "                                      OSSOFMM         MFOSSO                   C     C                   OSSOFM         MMFOSSO                                      ", // Y=6
                    "                                      V  VOMM         MFV  V                   C     C                   V  VFM         MMOV  V                                      ", // Y=7
                    "                                      X    FMMMMMMMMMMM    X                   CIIJIIC                   X    MMMMMMMMMMMF    X                                      ", // Y=8
                    "                                      X  O              O  X                   C     C                   X  O              O  X                                      ", // Y=9
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                   C     C                   X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=10
                    "      I       O           K           V        U   U       V                 HIIIIJIIIIH                 V       U   U        V           K          O        I      ", // Y=11
                    "     I                    K           X                    X                I           I                X                    X           K                    I     ", // Y=12
                    "    I        O            K       D   X                    X                HZ         ZH                X                    X       D   K                O    I    ", // Y=13
                    "   I                      K    OIO    X                    X    OIO    HSH                 HSH    OIO    X                    X    OIO    K                      I   ", // Y=14
                    "Y I                       K   L   L   O  O              O  O   L   L    G                   G    L   L   O  O              O  O   L   L   K                       I Y", // Y=15
                    "XL              O         Z           ZRRZ              ZRRZ                                             ZRRZ              ZRRZ           Z  O                     LX", // Y=16
                    "I                                                                                                                                                                   I", // Y=17
                    "I                                                                                                                                                                   I", // Y=18
                    "I                                                                                                                                                                   I", // Y=19
                    "I                                                                                                                                                                   I", // Y=20
                    "I                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIF                             FIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         I", // Y=21
                    "L          N_E_N          P                                        [[[[P                     P[[[[                                        P          N_E_N          L", // Y=22
                    "I          _   _          [                                                      IKI                                                      [          _   _          I", // Y=23
                    "L          E ] E          [                                                      K[K                                                      [          E ] E          L", // Y=24
                    "I          _   _          [                                                      IKI                                                      [          _   _          I", // Y=25
                    "L          N_E_N          P                                        [[[[P                     P[[[[                                        P          N_E_N          L", // Y=26
                    "I                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIF                             FIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         I", // Y=27
                    "I                                                                                                                                                                   I", // Y=28
                    "I                                                                                                                                                                   I", // Y=29
                    "I                                                                                                                                                                   I", // Y=30
                    "I                                                                                                                                                                   I", // Y=31
                    "XL              O         Z           ZRRZ              ZRRZ                                             ZRRZ              ZRRZ           Z                    O   LX", // Y=32
                    "Y I                       K   L   L   O  O              O  O   L   L    G                   G    L   L   O  O              O  O   L   L   K                       I Y", // Y=33
                    "   I                      K    OIO    X                    X    OIO    HSH                 HSH    OIO    X                    X    OIO    K                      I   ", // Y=34
                    "    I        O            K   D       X                    X                HZ         ZH                X                    X   D       K      O              I    ", // Y=35
                    "     I                    K           X                    X                I           I                X                    X           K                    I     ", // Y=36
                    "      I       O           K           V        U   U       V                 HIIIIJIIIIH                 V       U   U        V           K            O      I      ", // Y=37
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                   C     C                   X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=38
                    "                                      X  O              O  X                   C     C                   X  O              O  X                                      ", // Y=39
                    "                                      X    FMMMMMMMMMMM    X                   CIIJIIC                   X    MMMMMMMMMMMF    X                                      ", // Y=40
                    "                                      V  VOMM         MFV  V                   C     C                   V  VFM         MMOV  V                                      ", // Y=41
                    "                                      OSSOFMM         MFOSSO                   C     C                   OSSOFM         MMFOSSO                                      ", // Y=42
                    "                                           MM         MFPQ                     CIIJIIC                       FM         MM                                           ", // Y=43
                    "                                           FMMMMMMMMMMM                        C     C                        MMMMMMMMMMMF                                           ", // Y=44
                    "                                                                               C     C                                                                               ", // Y=45
                    "                                                                              HCIIJIICH                                                                              ", // Y=46
                    "                                B                                              CDEEEDC                                              B                                ", // Y=47
            },
            { // Z=27
                    "                                                                                                                                                                     ", // Y=0
                    "                                B                                               CDDDC                                               B                                ", // Y=1
                    "                                                                               HCIJICH                                                                               ", // Y=2
                    "                                                                                C   C                                                                                ", // Y=3
                    "                                                                               PC   C                                                                                ", // Y=4
                    "                                           FMMMMMMMMMMM                       QQCIJICO                    Q   MMMMMMMMMMMF                                           ", // Y=5
                    "                                      OSSO FMMNNNNNNNMM OSSO                   PC   CO                   OSSO MMNNNNNNNMMF OSSO                                      ", // Y=6
                    "                                      V  VOFMMMMMMMMMMMOV  V                    C   CO                   V  VOMMMMMMMMMMMFOV  V                                      ", // Y=7
                    "                                      X                    X                    CIJIC                    X                    X                                      ", // Y=8
                    "                                      X  O              O  X                    C   C                    X  O              O  X                                      ", // Y=9
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                    C   C                    X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=10
                    "      I       O           K           V        U   U       V                 HIIIIJIIIIH                 V       U   U        V           K          O        I      ", // Y=11
                    "     I                    K           X                    X                H           H                X                    X           K                    I     ", // Y=12
                    "    I        O            K        D  X                    X                HZZ       ZZH                X                    X        D  K                O    I    ", // Y=13
                    "   I                      K           X                    X                                             X                    X           K                      I   ", // Y=14
                    "  I                       K    OIO    O  O              O  O    OIO    HDH                 HDH    OIO    O  O              O  O    OIO    K                       I  ", // Y=15
                    "Y I                       L   L   L   O  O              O  O   L   L                             L   L   O  O              O  O   L   L   L                       I Y", // Y=16
                    "XL               O        Z           ZZZZ              ZZZZ                                             ZZZZ              ZZZZ           Z   O                    LX", // Y=17
                    "I                                                                                                                                                                   I", // Y=18
                    "I                                                                                                                                                                   I", // Y=19
                    "I                                                                                                                                                                   I", // Y=20
                    "L                                                                                                                                                                   L", // Y=21
                    "I                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIF                             FIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         I", // Y=22
                    "I           KKK           N                                        P              G              P                                        N           KKK           I", // Y=23
                    "L           K]K           N^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^P             G[G             P^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^N           K]K           L", // Y=24
                    "I           KKK           N                                        P              G              P                                        N           KKK           I", // Y=25
                    "I                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIF                             FIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         I", // Y=26
                    "L                                                                                                                                                                   L", // Y=27
                    "I                                                                                                                                                                   I", // Y=28
                    "I                                                                                                                                                                   I", // Y=29
                    "I                                                                                                                                                                   I", // Y=30
                    "XL               O        Z           ZZZZ              ZZZZ                                             ZZZZ              ZZZZ           Z                   O    LX", // Y=31
                    "Y I                       L   L   L   O  O              O  O   L   L                             L   L   O  O              O  O   L   L   L                       I Y", // Y=32
                    "  I                       K    OIO    O  O              O  O    OIO    HDH                 HDH    OIO    O  O              O  O    OIO    K                       I  ", // Y=33
                    "   I                      K           X                    X                                             X                    X           K                      I   ", // Y=34
                    "    I        O            K  D        X                    X                HZZ       ZZH                X                    X  D        K      O              I    ", // Y=35
                    "     I                    K           X                    X                H           H                X                    X           K                    I     ", // Y=36
                    "      I       O           K           V        U   U       V                 HIIIIJIIIIH                 V       U   U        V           K            O      I      ", // Y=37
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                    C   C                    X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=38
                    "                                      X  O              O  X                    C   C                    X  O              O  X                                      ", // Y=39
                    "                                      X                    X                    CIJIC                    X                    X                                      ", // Y=40
                    "                                      V  VOFMMMMMMMMMMMOV  V                   OC   C                    V  VOMMMMMMMMMMMFOV  V                                      ", // Y=41
                    "                                      OSSO FMMNNNNNNNMM OSSO                   OC   CP                   OSSO MMNNNNNNNMMF OSSO                                      ", // Y=42
                    "                                           FMMMMMMMMMMM   Q                    OCIJICQQ                       MMMMMMMMMMMF                                           ", // Y=43
                    "                                                                                C   CP                                                                               ", // Y=44
                    "                                                                                C   C                                                                                ", // Y=45
                    "                                                                               HCIJICH                                                                               ", // Y=46
                    "                                B                                               CDDDC                                               B                                ", // Y=47
            },
            { // Z=28
                    "                                                                                                                                                                     ", // Y=0
                    "                                B                                                CCC                                                B                                ", // Y=1
                    "                                B                                               HCCCH                                               B                                ", // Y=2
                    "                                                                                 CCC                                                                                 ", // Y=3
                    "                                                                               OOCCC                                                                                 ", // Y=4
                    "                                                                            QQ POCCCPO                   Q                                                           ", // Y=5
                    "                                      OTTO              OTTO                   OOCCCQP                   OTTO              OTTO                                      ", // Y=6
                    "                                      VWWVOOOOOOOOOOOOOOVWWV                     CCCPO                   VWWVOOOOOOOOOOOOOOVWWV                                      ", // Y=7
                    "                                      VWW                WWV                     CCC                     VWW                WWV                                      ", // Y=8
                    "                                      VWWO              OWWV                     CCC                     VWWO              OWWV                                      ", // Y=9
                    "                                      VWW                WWV                     CCC                     VWW                WWV                                      ", // Y=10
                    "L     LLIIIIIIIIIIIIIIIIIIL           VIIIIIIIIIIIIIIIIIIIIV                  HIIIJIIIH                  VIIIIIIIIIIIIIIIIIIIIV           LIIIIIIIIIIIIIIIIIILL     L", // Y=11
                    "     I        O           K           VWW                WWV                 H         H                 VWW                WWV           K          O         I     ", // Y=12
                    "    I                     K        D  VWW                WWV                 HZZ     ZZH                 VWW                WWV        D  K                     I    ", // Y=13
                    "   I         O            K           VWW                WWV                                             VWW                WWV           K               O      I   ", // Y=14
                    "   I                      K    OIO    PWWP              PWWP    OIO    HDH                 HDH    OIO    PWWP              PWWP    OIO    K                      I   ", // Y=15
                    "Y I                       L   L   L   OWWO              OWWO   L   L                             L   L   OWWO              OWWO   L   L   L                       I Y", // Y=16
                    "XL                O       Z           Z[[Z              Z[[Z                                             Z[[Z              Z[[Z           Z    O                   LX", // Y=17
                    "I                                                                                                                                                                   I", // Y=18
                    "I                                                                                                                                                                   I", // Y=19
                    "L                                                                                                                                                                   L", // Y=20
                    "I                                                                                                                                                                   I", // Y=21
                    "I                                                                                                                                                                   I", // Y=22
                    "I                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII                               IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         I", // Y=23
                    "L                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII               P               IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         L", // Y=24
                    "I                         LIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII                               IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIL                         I", // Y=25
                    "I                                                                                                                                                                   I", // Y=26
                    "I                                                                                                                                                                   I", // Y=27
                    "L                                                                                                                                                                   L", // Y=28
                    "I                                                                                                                                                                   I", // Y=29
                    "I                 O                                                                                                                                                 I", // Y=30
                    "XL                        Z           Z[[Z              Z[[Z                                             Z[[Z              Z[[Z           Z                  O     LX", // Y=31
                    "Y I                       L   L   L   OWWO              OWWO   L   L                             L   L   OWWO              OWWO   L   L   L                       I Y", // Y=32
                    "   I                      K    OIO    PWWP              PWWP    OIO    HDH                 HDH    OIO    PWWP              PWWP    OIO    K                      I   ", // Y=33
                    "   I         O            K           VWW                WWV                                             VWW                WWV           K      O               I   ", // Y=34
                    "    I                     K  D        VWW                WWV                 HZZ     ZZH                 VWW                WWV  D        K                     I    ", // Y=35
                    "     I        O           K           VWW                WWV                 H         H                 VWW                WWV           K            O       I     ", // Y=36
                    "L     LLIIIIIIIIIIIIIIIIIIL           VIIIIIIIIIIIIIIIIIIIIV                  HIIIJIIIH                  VIIIIIIIIIIIIIIIIIIIIV           LIIIIIIIIIIIIIIIIIILL     L", // Y=37
                    "                                      VWW                WWV                     CCC                     VWW                WWV                                      ", // Y=38
                    "                                      VWWO              OWWV                     CCC                     VWWO              OWWV                                      ", // Y=39
                    "                                      VWW                WWV                     CCC                     VWW                WWV                                      ", // Y=40
                    "                                      VWWVOOOOOOOOOOOOOOVWWV                   OPCCC                     VWWVOOOOOOOOOOOOOOVWWV                                      ", // Y=41
                    "                                      OTTO              OTTO                   PQCCCOO                   OTTO              OTTO                                      ", // Y=42
                    "                                                           Q                   OPCCCOP QQ                                                                            ", // Y=43
                    "                                                                                 CCCOO                                                                               ", // Y=44
                    "                                                                                 CCC                                                                                 ", // Y=45
                    "                                B                                               HCCCH                                               B                                ", // Y=46
                    "                                B                                                CCC                                                B                                ", // Y=47
            },
            { // Z=29
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                B                                                HHH                                                B                                ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                          QQ       Q                    Q                                                            ", // Y=6
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=7
                    "                                      X   OOOOOOOOOOOOOO   X                                             X   OOOOOOOOOOOOOO   X                                      ", // Y=8
                    "                                      X  O              O  X                     H H                     X  O              O  X                                      ", // Y=9
                    "                                      X                    X                     H H                     X                    X                                      ", // Y=10
                    "L     LLIIIIIIIIIIIIIIIIIIL           VWW      U   U     WWV                   HHHHHHH                   VWW     U   U      WWV           LIIIIIIIIIIIIIIIIIILL     L", // Y=11
                    "     I        O           K           X                    X                  H       H                  X                    X           K          O         I     ", // Y=12
                    "    I                     K         D X                    X                  HZZZZZZZH                  X                    X         D K                     I    ", // Y=13
                    "    I        O            K           X                    X                                             X                    X           K               O     I    ", // Y=14
                    "   I                      K           P  P              P  P                                             P  P              P  P           K                      I   ", // Y=15
                    "  I                       L    OIO    O  O              O  O    OIO    HDH                 HDH    OIO    O  O              O  O    OIO    L                       I  ", // Y=16
                    "Y I                       Z   L   L   Z  Z              Z  Z   L   L                             L   L   Z  Z              Z  Z   L   L   Z                       I Y", // Y=17
                    "XL                 O      Z           Z[[Z              Z[[Z                                             Z[[Z              Z[[Z           Z     O                  LX", // Y=18
                    "LL                                                                                                                                                                 LL", // Y=19
                    "I                                                                                                                                                                   I", // Y=20
                    "I                                                                                                                                                                   I", // Y=21
                    "I                                                                                                                                                                   I", // Y=22
                    "I                                                                                                                                                                   I", // Y=23
                    "L                         L                                                                                                               L                         L", // Y=24
                    "I                                                                                                                                                                   I", // Y=25
                    "I                                                                                                                                                                   I", // Y=26
                    "I                                                                                                                                                                   I", // Y=27
                    "I                                                                                                                                                                   I", // Y=28
                    "LL                                                                                                                                                                 LL", // Y=29
                    "XL                 O      Z           Z[[Z              Z[[Z                                             Z[[Z              Z[[Z           Z                 O      LX", // Y=30
                    "Y I                       Z   L   L   Z  Z              Z  Z   L   L                             L   L   Z  Z              Z  Z   L   L   Z                       I Y", // Y=31
                    "  I                       L    OIO    O  O              O  O    OIO    HDH                 HDH    OIO    O  O              O  O    OIO    L                       I  ", // Y=32
                    "   I                      K           P  P              P  P                                             P  P              P  P           K                      I   ", // Y=33
                    "    I        O            K           X                    X                                             X                    X           K      O              I    ", // Y=34
                    "    I                     K D         X                    X                  HZZZZZZZH                  X                    X D         K                     I    ", // Y=35
                    "     I        O           K           X                    X                  H       H                  X                    X           K            O       I     ", // Y=36
                    "L     LLIIIIIIIIIIIIIIIIIIL           VWW      U   U     WWV                   HHHHHHH                   VWW     U   U      WWV           LIIIIIIIIIIIIIIIIIILL     L", // Y=37
                    "                                      X                    X                     H H                     X                    X                                      ", // Y=38
                    "                                      X  O              O  X                     H H                     X  O              O  X                                      ", // Y=39
                    "                                      X   OOOOOOOOOOOOOO   X                                             X   OOOOOOOOOOOOOO   X                                      ", // Y=40
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=41
                    "                                                            Q                    Q       QQ                                                                          ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                B                                                HHH                                                B                                ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=30
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                B                                                                                                   B                                ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                        QQ        Q                    Q                                                             ", // Y=6
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=7
                    "                                      X   OOOOIIIIIIIOOO   X                                             X   OOOIIIIIIIOOOO   X                                      ", // Y=8
                    "                                      X  O              O  X                                             X  O              O  X                                      ", // Y=9
                    "                                      X                    X                                             X                    X                                      ", // Y=10
                    "L     LLIIIIIIIIIIIIIIIIIIL           VWW      U   U     WWV                                             VWW     U   U      WWV           LIIIIIIIIIIIIIIIIIILL     L", // Y=11
                    "     I        O           K           X                    X                   HIIIIIH                   X                    X           K          O         I     ", // Y=12
                    "     I                    K         D X                    X                   HHHHHHH                   X                    X         D K                    I     ", // Y=13
                    "    I                     K           X                    X                                             X                    X           K                     I    ", // Y=14
                    "   I        O             K           P  P              P  P                                             P  P              P  P           K              O       I   ", // Y=15
                    "   I                      L           O  O              O  O           HDH                 HDH           O  O              O  O           L                      I   ", // Y=16
                    "  I                       Z    OIO    Z  Z              Z  Z    OIO                               OIO    Z  Z              Z  Z    OIO    Z                       I  ", // Y=17
                    "~LL[                      Z   LGGGL   ZZZZZZZZZZZZZZZZZZZZZZ   LGGGL                             LGGGL   ZZZZZZZZZZZZZZZZZZZZZZ   LGGGL   Z                      [LL~", // Y=18
                    "XL                  O     Z           Z[[Z              Z[[Z                                             Z[[Z              Z[[Z           Z      O                 LX", // Y=19
                    "I                                                                                                                                                                   I", // Y=20
                    "I                                                                                                                                                                   I", // Y=21
                    "I                                                                                                                                                                   I", // Y=22
                    "I                                                                                                                                                                   I", // Y=23
                    "L                         L                                                                                                               L                         L", // Y=24
                    "I                                                                                                                                                                   I", // Y=25
                    "I                                                                                                                                                                   I", // Y=26
                    "I                                                                                                                                                                   I", // Y=27
                    "I                                                                                                                                                                   I", // Y=28
                    "XL                  O     Z           Z[[Z              Z[[Z                                             Z[[Z              Z[[Z           Z                O       LX", // Y=29
                    "~LL[                      Z   LGGGL   ZZZZZZZZZZZZZZZZZZZZZZ   LGGGL                             LGGGL   ZZZZZZZZZZZZZZZZZZZZZZ   LGGGL   Z                      [LL~", // Y=30
                    "  I                       Z    OIO    Z  Z              Z  Z    OIO                               OIO    Z  Z              Z  Z    OIO    Z                       I  ", // Y=31
                    "   I                      L           O  O              O  O           HDH                 HDH           O  O              O  O           L                      I   ", // Y=32
                    "   I        O             K           P  P              P  P                                             P  P              P  P           K       O              I   ", // Y=33
                    "    I                     K           X                    X                                             X                    X           K                     I    ", // Y=34
                    "     I                    K D         X                    X                   HHHHHHH                   X                    X D         K                    I     ", // Y=35
                    "     I        O           K           X                    X                   HIIIIIH                   X                    X           K            O       I     ", // Y=36
                    "L     LLIIIIIIIIIIIIIIIIIIL           VWW      U   U     WWV                                             VWW     U   U      WWV           LIIIIIIIIIIIIIIIIIILL     L", // Y=37
                    "                                      X                    X                                             X                    X                                      ", // Y=38
                    "                                      X  O              O  X                                             X  O              O  X                                      ", // Y=39
                    "                                      X   OOOOIIIIIIIOOO   X                                             X   OOOIIIIIIIOOOO   X                                      ", // Y=40
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=41
                    "                                                             Q                    Q        QQ                                                                        ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                B                                                                                                   B                                ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=31
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                B                                                                                                   B                                ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                      QQ         Q                    Q                                                              ", // Y=6
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=7
                    "                                      O  OOOOOOOOOOOOOOOO  O                                             O  OOOOOOOOOOOOOOOO  O                                      ", // Y=8
                    "                                      X  O              O  X                                             X  O              O  X                                      ", // Y=9
                    "                                      X                    X                                             X                    X                                      ", // Y=10
                    "                                      VWW      U   U     WWV                                             VWW     U   U      WWV                                      ", // Y=11
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                                             X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=12
                    "     I        O           K          DX                    X                                             X                    X          DK          O         I     ", // Y=13
                    "    I                     K           X                    X                                             X                    X           K                     I    ", // Y=14
                    "    I                     K           P  P              P  P                                             P  P              P  P           K                     I    ", // Y=15
                    "   I        O             L           O  O              O  O                                             O  O              O  O           L              O       I   ", // Y=16
                    "L LL[                     L           O  O              O  O           HDH                 HDH           O  O              O  O           L                     [LL L", // Y=17
                    "  I                       Z    OIO    Z  Z              Z  Z    OIO                               OIO    Z  Z              Z  Z    OIO    Z                       I  ", // Y=18
                    "Y I                       Z   L   L   Z  Z              Z  Z   L   L                             L   L   Z  Z              Z  Z   L   L   Z                       I Y", // Y=19
                    "XL                   O    Z           Z[[Z              Z[[Z                                             Z[[Z              Z[[Z           Z       O                LX", // Y=20
                    "XL                    O   Z           ZZZZ              ZZZZ                                             ZZZZ              ZZZZ           Z        O               LX", // Y=21
                    "I                                                                                                                                                                   I", // Y=22
                    "I                                                                                                                                                                   I", // Y=23
                    "L                         L                                                                                                               L                         L", // Y=24
                    "I                                                                                                                                                                   I", // Y=25
                    "I                                                                                                                                                                   I", // Y=26
                    "XL                    O   Z           ZZZZ              ZZZZ                                             ZZZZ              ZZZZ           Z              O         LX", // Y=27
                    "XL                   O    Z           Z[[Z              Z[[Z                                             Z[[Z              Z[[Z           Z               O        LX", // Y=28
                    "Y I                       Z   L   L   Z  Z              Z  Z   L   L                             L   L   Z  Z              Z  Z   L   L   Z                       I Y", // Y=29
                    "  I                       Z    OIO    Z  Z              Z  Z    OIO                               OIO    Z  Z              Z  Z    OIO    Z                       I  ", // Y=30
                    "L LL[                     L           O  O              O  O           HDH                 HDH           O  O              O  O           L                     [LL L", // Y=31
                    "   I        O             L           O  O              O  O                                             O  O              O  O           L       O              I   ", // Y=32
                    "    I                     K           P  P              P  P                                             P  P              P  P           K                     I    ", // Y=33
                    "    I                     K           X                    X                                             X                    X           K                     I    ", // Y=34
                    "     I        O           KD          X                    X                                             X                    XD          K            O       I     ", // Y=35
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                                             X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=36
                    "                                      VWW      U   U     WWV                                             VWW     U   U      WWV                                      ", // Y=37
                    "                                      X                    X                                             X                    X                                      ", // Y=38
                    "                                      X  O              O  X                                             X  O              O  X                                      ", // Y=39
                    "                                      O  OOOOOOOOOOOOOOOO  O                                             O  OOOOOOOOOOOOOOOO  O                                      ", // Y=40
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=41
                    "                                                              Q                    Q         QQ                                                                      ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                B                                                                                                   B                                ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=32
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                B                                                                                                   B                                ", // Y=2
                    "                                B                                                                                                   B                                ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                    QQ          Q                    Q                                                               ", // Y=7
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=8
                    "                                      O  OOOOOIIIIIIIOOOO  O                                             O  OOOOIIIIIIIOOOOO  O                                      ", // Y=9
                    "                                      O  O              O  O                                             O  O              O  O                                      ", // Y=10
                    "                                      OWWO     U   U    OWWO                                             OWWO    U   U     OWWO                                      ", // Y=11
                    "L     LLLLLLLLLLLLLLLLLLLLL           O  O              O  O                                             O  O              O  O           LLLLLLLLLLLLLLLLLLLLL     L", // Y=12
                    "     I        P           L          DO  O              O  O                                             O  O              O  O          DL          P         I     ", // Y=13
                    "     I        O           L           O  O              O  O                                             O  O              O  O           L          O         I     ", // Y=14
                    "    I                     L           O  O              O  O                                             O  O              O  O           L                     I    ", // Y=15
                    "L  LL      T              L           O  O              O  O                                             O  O              O  O           L              T      LL  L", // Y=16
                    "   I       O              L           O  O              O  O                                             O  O              O  O           L             O        I   ", // Y=17
                    "   I                      L           O  O              O  O           HDH                 HDH           O  O              O  O           L                      I   ", // Y=18
                    "  I                       L    OIO    O  O              O  O    OIO    HDH                 HDH    OIO    O  O              O  O    OIO    L                       I  ", // Y=19
                    "Y I                       L   L   L   OWWO              OWWO   L   L                             L   L   OWWO              OWWO   L   L   L                       I Y", // Y=20
                    "Y I                       L   L   L   O  O              O  O   L   L                             L   L   O  O              O  O   L   L   L                       I Y", // Y=21
                    "XL                     O  Z           ZRRZ              ZRRZ                                             ZRRZ              ZRRZ           Z         O              LX", // Y=22
                    "XL                        Z           ZRRZ              ZRRZ                                             ZRRZ              ZRRZ           Z          [             LX", // Y=23
                    "LL~                       Z           ZZZZ              ZZZZ                                             ZZZZ              ZZZZ           Z           [           ~LL", // Y=24
                    "XL                        Z           ZRRZ              ZRRZ                                             ZRRZ              ZRRZ           Z            [           LX", // Y=25
                    "XL                     O  Z           ZRRZ              ZRRZ                                             ZRRZ              ZRRZ           Z             O          LX", // Y=26
                    "Y I                       L   L   L   O  O              O  O   L   L                             L   L   O  O              O  O   L   L   L                       I Y", // Y=27
                    "Y I                       L   L   L   OWWO              OWWO   L   L                             L   L   OWWO              OWWO   L   L   L                       I Y", // Y=28
                    "  I                       L    OIO    O  O              O  O    OIO    HDH                 HDH    OIO    O  O              O  O    OIO    L                       I  ", // Y=29
                    "   I                      L           O  O              O  O           HDH                 HDH           O  O              O  O           L                      I   ", // Y=30
                    "   I       O              L           O  O              O  O                                             O  O              O  O           L        O             I   ", // Y=31
                    "L  LL      T              L           O  O              O  O                                             O  O              O  O           L        T            LL  L", // Y=32
                    "    I                     L           O  O              O  O                                             O  O              O  O           L                     I    ", // Y=33
                    "     I        O           L           O  O              O  O                                             O  O              O  O           L            O       I     ", // Y=34
                    "     I        P           LD          O  O              O  O                                             O  O              O  OD          L            P       I     ", // Y=35
                    "L     LLLLLLLLLLLLLLLLLLLLL           O  O              O  O                                             O  O              O  O           LLLLLLLLLLLLLLLLLLLLL     L", // Y=36
                    "                                      OWWO     U   U    OWWO                                             OWWO    U   U     OWWO                                      ", // Y=37
                    "                                      O  O              O  O                                             O  O              O  O                                      ", // Y=38
                    "                                      O  OOOOOIIIIIIIOOOO  O                                             O  OOOOIIIIIIIOOOOO  O                                      ", // Y=39
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=40
                    "                                                               Q                    Q          QQ                                                                    ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                B                                                                                                   B                                ", // Y=45
                    "                                B                                                                                                   B                                ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=33
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                B                                                                                                   B                                ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                  QQ           Q                    Q                                                                ", // Y=7
                    "                                      OOOO              OOOO                                             OOOO              OOOO                                      ", // Y=8
                    "                                      O  OOOOOOOOOOOOOOOO  O                                             O  OOOOOOOOOOOOOOOO  O                                      ", // Y=9
                    "                                      O                    O                                             O                    O                                      ", // Y=10
                    "                                      OWW      U   U     WWO                                             OWW     U   U      WWO                                      ", // Y=11
                    "                                      O                    O                                             O                    O                                      ", // Y=12
                    "L     LLIIIIIIIIIIIIIIIIIIL           O                    O                                             O                    O           LIIIIIIIIIIIIIIIIIILL     L", // Y=13
                    "     I        O           X           O                    O                                             O                    O           X          O         I     ", // Y=14
                    "L   LLE                   X           O  O              O  O                                             O  O              O  O           X                   ELL   L", // Y=15
                    "    I                     L           O  O              O  O                                             O  O              O  O           L                     I    ", // Y=16
                    "    I                     K           P  P              P  P                                             P  P              P  P           K                     I    ", // Y=17
                    "   I       O              K           P  P              P  P                                             P  P              P  P           K             O        I   ", // Y=18
                    "   I                      K           P  P              P  P                                             P  P              P  P           K                      I   ", // Y=19
                    "   I                      K    OIO    PWWP              PWWP    OIO    HDH                 HDH    OIO    PWWP              PWWP    OIO    K                      I   ", // Y=20
                    "  I                       K    OIO    O  O              O  O    OIO    HDH                 HDH    OIO    O  O              O  O    OIO    K                       I  ", // Y=21
                    "Y I                       K   L   L   O  O              O  O   L   L    G                   G    L   L   O  O              O  O   L   L   K                       I Y", // Y=22
                    "Y I                     [ K   L   L   O  O              O  O   L   L    G                   G    L   L   O  O              O  O   L   L   K                       I Y", // Y=23
                    "~LL                     [ L   EGGGE   O  O              O  O   EGGGE   [G[                 [G[   EGGGE   O  O              O  O   EGGGE   L                       LL~", // Y=24
                    "Y I                     [ K   L   L   O  O              O  O   L   L    G                   G    L   L   O  O              O  O   L   L   K                       I Y", // Y=25
                    "Y I                       K   L   L   O  O              O  O   L   L    G                   G    L   L   O  O              O  O   L   L   K                       I Y", // Y=26
                    "  I                       K    OIO    O  O              O  O    OIO    HDH                 HDH    OIO    O  O              O  O    OIO    K                       I  ", // Y=27
                    "   I                      K    OIO    PWWP              PWWP    OIO    HDH                 HDH    OIO    PWWP              PWWP    OIO    K                      I   ", // Y=28
                    "   I                      K           P  P              P  P                                             P  P              P  P           K                      I   ", // Y=29
                    "   I       O              K           P  P              P  P                                             P  P              P  P           K        O             I   ", // Y=30
                    "    I                     K           P  P              P  P                                             P  P              P  P           K                     I    ", // Y=31
                    "    I                     L           O  O              O  O                                             O  O              O  O           L                     I    ", // Y=32
                    "L   LLE                   X           O  O              O  O                                             O  O              O  O           X                   ELL   L", // Y=33
                    "     I        O           X           O                    O                                             O                    O           X            O       I     ", // Y=34
                    "L     LLIIIIIIIIIIIIIIIIIIL           O                    O                                             O                    O           LIIIIIIIIIIIIIIIIIILL     L", // Y=35
                    "                                      O                    O                                             O                    O                                      ", // Y=36
                    "                                      OWW      U   U     WWO                                             OWW     U   U      WWO                                      ", // Y=37
                    "                                      O                    O                                             O                    O                                      ", // Y=38
                    "                                      O  OOOOOOOOOOOOOOOO  O                                             O  OOOOOOOOOOOOOOOO  O                                      ", // Y=39
                    "                                      OOOO              OOOO                                             OOOO              OOOO                                      ", // Y=40
                    "                                                                Q                    Q           QQ                                                                  ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                B                                                                                                   B                                ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=34
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                B                                                                                                   B                                ", // Y=3
                    "                                B                                                                                                   B                                ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                QQ            Q                    Q                                                                 ", // Y=8
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=9
                    "                                      O  OOFMMMMMMMMMMMOO  O                                             O  OOMMMMMMMMMMMFOO  O                                      ", // Y=10
                    "                                      OWW  FMMNNNNNNNMM  WWO                                             OWW  MMNNNNNNNMMF  WWO                                      ", // Y=11
                    "                                      X    FMMMMMMMMMMM    X                                             X    MMMMMMMMMMMF    X                                      ", // Y=12
                    "                                      X                    X                                             X                    X                                      ", // Y=13
                    "LYYYYLLLIIIIIIIIIIIIIIIIIIL           X                    X                                             X                    X           LIIIIIIIIIIIIIIIIIILLLYYYYL", // Y=14
                    "     I         O          X           O                    O                                             O                    O           X           O        I     ", // Y=15
                    "     I         O          L           O  O              O  O                                             O  O              O  O           L           O        I     ", // Y=16
                    "    I                     K           X                    X                                             X                    X           K                     I    ", // Y=17
                    "    I                     K           X                    X                                             X                    X           K                     I    ", // Y=18
                    "    I     O               K           X                    X                                             X                    X           K            O        I    ", // Y=19
                    "   I      O               K           VWW                WWV                                             VWW                WWV           K            O         I   ", // Y=20
                    "   I                      K           X                    X                                             X                    X           K                      I   ", // Y=21
                    "   I                      K    OIO    X                    X    OIO    HSH                 HSH    OIO    X                    X    OIO    K                      I   ", // Y=22
                    "   I                      K    PIP    X                    X    PIP    HSH                 HSH    PIP    X                    X    PIP    K                      I   ", // Y=23
                    "L LL                      L    PIP    O  O              O  O    PIP    HSH                 HSH    PIP    O  O              O  O    PIP    L                      LL L", // Y=24
                    "   I                      K    PIP    X                    X    PIP    HSH                 HSH    PIP    X                    X    PIP    K                      I   ", // Y=25
                    "   I                      K    OIO    X                    X    OIO    HSH                 HSH    OIO    X                    X    OIO    K                      I   ", // Y=26
                    "   I                      K           X                    X                                             X                    X           K                      I   ", // Y=27
                    "   I      O               K           VWW                WWV                                             VWW                WWV           K         O            I   ", // Y=28
                    "    I     O               K           X                    X                                             X                    X           K         O           I    ", // Y=29
                    "    I                     K           X                    X                                             X                    X           K                     I    ", // Y=30
                    "    I                     K           X                    X                                             X                    X           K                     I    ", // Y=31
                    "     I         O          L           O  O              O  O                                             O  O              O  O           L           O        I     ", // Y=32
                    "     I         O          X           O                    O                                             O                    O           X           O        I     ", // Y=33
                    "LYYYYLLLIIIIIIIIIIIIIIIIIIL           X                    X                                             X                    X           LIIIIIIIIIIIIIIIIIILLLYYYYL", // Y=34
                    "                                      X                    X                                             X                    X                                      ", // Y=35
                    "                                      X    FMMMMMMMMMMM    X                                             X    MMMMMMMMMMMF    X                                      ", // Y=36
                    "                                      OWW  FMMNNNNNNNMM  WWO                                             OWW  MMNNNNNNNMMF  WWO                                      ", // Y=37
                    "                                      O  OOFMMMMMMMMMMMOO  O                                             O  OOMMMMMMMMMMMFOO  O                                      ", // Y=38
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=39
                    "                                                                 Q                    Q            QQ                                                                ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                B                                                                                                   B                                ", // Y=44
                    "                                B                                                                                                   B                                ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=35
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                B                                                                                                   B                                ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                           FMMMMMMMMMMM       QQ            QQ                   QQ           MMMMMMMMMMMF                                           ", // Y=9
                    "                                      OSSO MM         MFOSSO                                             OSSOFM         MM OSSO                                      ", // Y=10
                    "                                      OWW FMM         MF WWO                                             OWW FM         MMF WWO                                      ", // Y=11
                    "                                      X    MM         MF   X                                             X   FM         MM    X                                      ", // Y=12
                    "                                      X    FMMMMMMMMMMM    X                                             X    MMMMMMMMMMMF    X                                      ", // Y=13
                    "                                      X                    X                                             X                    X                                      ", // Y=14
                    "L     LLIIIIIIIIIIIIIIIIIIL           O                    O                                             O                    O           LIIIIIIIIIIIIIIIIIILL     L", // Y=15
                    "     I         P          LD          O  O              O  O                                             O  O              O  OD          L           P        I     ", // Y=16
                    "     I         O          KD          X                    X                                             X                    XD          K           O        I     ", // Y=17
                    "     I                    K D         X                    X                   HHHHHHH                   X                    X D         K                    I     ", // Y=18
                    "    I                     K D         X                    X                  HZZZZZZZH                  X                    X D         K                     I    ", // Y=19
                    "    I                     K  D        VWW                WWV                 HZZ     ZZH                 VWW                WWV  D        K                     I    ", // Y=20
                    "    I    O                K  D        X                    X                HZZ       ZZH                X                    X  D        K            O        I    ", // Y=21
                    "    I    O                K   D       X                    X                HZ         ZH                X                    X   D       K            O        I    ", // Y=22
                    "    I   T                 K   D [D    X                    X     [          HZ         ZH          [     X                    X   D [D    K           T         I    ", // Y=23
                    "L  LL   T                 L    D[D    O  O              O  O     [          HZ         ZH          [     O  O              O  O    D[D    L           T         LL  L", // Y=24
                    "    I   T                 K    D[ D   X                    X     [          HZ         ZH          [     X                    X    D[ D   K           T         I    ", // Y=25
                    "    I    O                K       D   X                    X                HZ         ZH                X                    X       D   K          O          I    ", // Y=26
                    "    I    O                K        D  X                    X                HZZ       ZZH                X                    X        D  K          O          I    ", // Y=27
                    "    I                     K        D  VWW                WWV                 HZZ     ZZH                 VWW                WWV        D  K                     I    ", // Y=28
                    "    I                     K         D X                    X                  HZZZZZZZH                  X                    X         D K                     I    ", // Y=29
                    "     I                    K         D X                    X                   HHHHHHH                   X                    X         D K                    I     ", // Y=30
                    "     I         O          K          DX                    X                                             X                    X          DK           O        I     ", // Y=31
                    "     I         P          L          DO  O              O  O                                             O  O              O  O          DL           P        I     ", // Y=32
                    "L     LLIIIIIIIIIIIIIIIIIIL           O                    O                                             O                    O           LIIIIIIIIIIIIIIIIIILL     L", // Y=33
                    "                                      X                    X                                             X                    X                                      ", // Y=34
                    "                                      X    FMMMMMMMMMMM    X                                             X    MMMMMMMMMMMF    X                                      ", // Y=35
                    "                                      X    MM         MF   X                                             X   FM         MM    X                                      ", // Y=36
                    "                                      OWW FMM         MF WWO                                             OWW FM         MMF WWO                                      ", // Y=37
                    "                                      OSSO MM         MFOSSO                                             OSSOFM         MM OSSO                                      ", // Y=38
                    "                                           FMMMMMMMMMMM           QQ                   QQ            QQ       MMMMMMMMMMMF                                           ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                B                                                                                                   B                                ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=36
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                B                                                                                                   B                                ", // Y=4
                    "                                B                                                                                                   B                                ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=8
                    "                                           MM         MF    QQ            QQ                   QQ            FM         MM                                           ", // Y=9
                    "                                      OSSOM            ROSSO                                             OSSOR            MOSSO                                      ", // Y=10
                    "                                      OWWOM            ROWWO                                             OWWOR            MOWWO                                      ", // Y=11
                    "                                      X   M            R   X                                             X   R            M   X                                      ", // Y=12
                    "                                      X    MM         MF   X                                             X   FM         MM    X                                      ", // Y=13
                    "                                      X    FMMMMMMMMMMM    X                                             X    MMMMMMMMMMMF    X                                      ", // Y=14
                    "                                      O                    O                                             O                    O                                      ", // Y=15
                    "L     LLLLLLLLLLLLLLLLLLLLL           O  O              O  O                                             O  O              O  O           LLLLLLLLLLLLLLLLLLLLL     L", // Y=16
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                                             X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=17
                    "     I         O          K           X                    X                   HIIIIIH                   X                    X           K           O        I     ", // Y=18
                    "     I         O          K           X                    X                  H       H                  X                    X           K           O        I     ", // Y=19
                    "     I         O          K           VWW                WWV                 H         H                 VWW                WWV           K           O        I     ", // Y=20
                    "     I                    K           X                    X                H           H                X                    X           K                    I     ", // Y=21
                    "     I                    K           X                    X                I           I                X                    X           K                    I     ", // Y=22
                    "     I                    K           X                    X                I           I                X                    X           K                    I     ", // Y=23
                    "L   LLE                   L           O  O              O  O                I           I                O  O              O  O           L                   ELL   L", // Y=24
                    "     I                    K           X                    X                I           I                X                    X           K                    I     ", // Y=25
                    "     I                    K           X                    X                I           I                X                    X           K                    I     ", // Y=26
                    "     I                    K           X                    X                H           H                X                    X           K                    I     ", // Y=27
                    "     I         O          K           VWW                WWV                 H         H                 VWW                WWV           K           O        I     ", // Y=28
                    "     I         O          K           X                    X                  H       H                  X                    X           K           O        I     ", // Y=29
                    "     I         O          K           X                    X                   HIIIIIH                   X                    X           K           O        I     ", // Y=30
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                                             X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=31
                    "L     LLLLLLLLLLLLLLLLLLLLL           O  O              O  O                                             O  O              O  O           LLLLLLLLLLLLLLLLLLLLL     L", // Y=32
                    "                                      O                    O                                             O                    O                                      ", // Y=33
                    "                                      X    FMMMMMMMMMMM    X                                             X    MMMMMMMMMMMF    X                                      ", // Y=34
                    "                                      X    MM         MF   X                                             X   FM         MM    X                                      ", // Y=35
                    "                                      X   M            R   X                                             X   R            M   X                                      ", // Y=36
                    "                                      OWWOM            ROWWO                                             OWWOR            MOWWO                                      ", // Y=37
                    "                                      OSSOM            ROSSO                                             OSSOR            MOSSO                                      ", // Y=38
                    "                                           MM         MF            QQ                   QQ            QQ    FM         MM                                           ", // Y=39
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                B                                                                                                   B                                ", // Y=43
                    "                                B                                                                                                   B                                ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=37
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                B                                                                                                   B                                ", // Y=5
                    "                                B                                                                                                   B                                ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                           FMMNNNNNNNMM                                                       MMNNNNNNNMMF                                           ", // Y=8
                    "                                          FMM         MF  QQ                                                 FM         MMF                                          ", // Y=9
                    "                                         OM            ROQ              QQ                   QQ             OR            MO                                         ", // Y=10
                    "                                      OTTONUUUUUUUUUUUUUOTTO                                             OTTOUUUUUUUUUUUUUNOTTO                                      ", // Y=11
                    "                                      OWWOM            ROWWO                                             OWWOR            MOWWO                                      ", // Y=12
                    "                                      OWW FMM         MF WWO                                             OWW FM         MMF WWO                                      ", // Y=13
                    "                                      OWW  FMMNNNNNNNMM  WWO                                             OWW  MMNNNNNNNMMF  WWO                                      ", // Y=14
                    "                                      OWW      U   U     WWO                                             OWW     U   U      WWO                                      ", // Y=15
                    "                                      OWWO     U   U    OWWO                                             OWWO    U   U     OWWO                                      ", // Y=16
                    "                                      VWW      U   U     WWV                                             VWW     U   U      WWV                                      ", // Y=17
                    "L     LLIIIIIIIIIIIIIIIIIIL           VWW      U   U     WWV                                             VWW     U   U      WWV           LIIIIIIIIIIIIIIIIIILL     L", // Y=18
                    "L     LLIIIIIIIIIIIIIIIIIIL           VWW      U   U     WWV                   HHHHHHH                   VWW     U   U      WWV           LIIIIIIIIIIIIIIIIIILL     L", // Y=19
                    "L     LLIIIIIIIIIIIIIIIIIIL           VIIIIIIIIIIIIIIIIIIIIV                  HIIIJIIIH                  VIIIIIIIIIIIIIIIIIIIIV           LIIIIIIIIIIIIIIIIIILL     L", // Y=20
                    "      I        O          K           V        U   U       V                 HIIIIJIIIIH                 V       U   U        V           K           O       I      ", // Y=21
                    "      I        O          K           V        U   U       V                 HIIIIJIIIIH                 V       U   U        V           K           O       I      ", // Y=22
                    "      I        P          K           V        U   U       V                 HIIIISIIIIH                 V       U   U        V           K           P       I      ", // Y=23
                    "L    LLLPPP    P          L           O  O     U   U    O  O                 HJJJSSSJJJH                 O  O    U   U     O  O           L           P   PPPLLL    L", // Y=24
                    "      I        P          K           V        U   U       V                 HIIIISIIIIH                 V       U   U        V           K           P       I      ", // Y=25
                    "      I        O          K           V        U   U       V                 HIIIIJIIIIH                 V       U   U        V           K           O       I      ", // Y=26
                    "      I        O          K           V        U   U       V                 HIIIIJIIIIH                 V       U   U        V           K           O       I      ", // Y=27
                    "L     LLIIIIIIIIIIIIIIIIIIL           VIIIIIIIIIIIIIIIIIIIIV                  HIIIJIIIH                  VIIIIIIIIIIIIIIIIIIIIV           LIIIIIIIIIIIIIIIIIILL     L", // Y=28
                    "L     LLIIIIIIIIIIIIIIIIIIL           VWW      U   U     WWV                   HHHHHHH                   VWW     U   U      WWV           LIIIIIIIIIIIIIIIIIILL     L", // Y=29
                    "L     LLIIIIIIIIIIIIIIIIIIL           VWW      U   U     WWV                                             VWW     U   U      WWV           LIIIIIIIIIIIIIIIIIILL     L", // Y=30
                    "                                      VWW      U   U     WWV                                             VWW     U   U      WWV                                      ", // Y=31
                    "                                      OWWO     U   U    OWWO                                             OWWO    U   U     OWWO                                      ", // Y=32
                    "                                      OWW      U   U     WWO                                             OWW     U   U      WWO                                      ", // Y=33
                    "                                      OWW  FMMNNNNNNNMM  WWO                                             OWW  MMNNNNNNNMMF  WWO                                      ", // Y=34
                    "                                      OWW FMM         MF WWO                                             OWW FM         MMF WWO                                      ", // Y=35
                    "                                      OWWOM            ROWWO                                             OWWOR            MOWWO                                      ", // Y=36
                    "                                      OTTONUUUUUUUUUUUUUOTTO                                             OTTOUUUUUUUUUUUUUNOTTO                                      ", // Y=37
                    "                                         OM            RO             QQ                   QQ              QOR            MO                                         ", // Y=38
                    "                                          FMM         MF                                                 QQ  FM         MMF                                          ", // Y=39
                    "                                           FMMNNNNNNNMM                                                       MMNNNNNNNMMF                                           ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                B                                                                                                   B                                ", // Y=42
                    "                                B                                                                                                   B                                ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=38
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                B                                                                                                   B                                ", // Y=6
                    "                                B                                                                                                   B                                ", // Y=7
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=8
                    "                                           MM         MFP                                                   PFM         MM                                           ", // Y=9
                    "                                          M            RQ                                                   QR            M                                          ", // Y=10
                    "                                         OM            RO             QQ                   QQ              QOR            MO                                         ", // Y=11
                    "                                      OSSOM            ROSSO                                             OSSOR            MOSSO                                      ", // Y=12
                    "                                      OSSO MM         MFOSSO                                             OSSOFM         MM OSSO                                      ", // Y=13
                    "                                      O  OOFMMMMMMMMMMMOO  O                                             O  OOMMMMMMMMMMMFOO  O                                      ", // Y=14
                    "                                      O                    O                                             O                    O                                      ", // Y=15
                    "                                      O  O              O  O                                             O  O              O  O                                      ", // Y=16
                    "                                      X                    X                                             X                    X                                      ", // Y=17
                    "                                      X                    X                                             X                    X                                      ", // Y=18
                    "                                      X                    X                     H H                     X                    X                                      ", // Y=19
                    "                                      VWW                WWV                     CCC                     VWW                WWV                                      ", // Y=20
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                    C   C                    X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=21
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                   C     C                   X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=22
                    "L     LLIIIIIIIIIIIIIIIIIILF         FX                    X                 HC       CH                 X                    XF         FLIIIIIIIIIIIIIIIIIILL     L", // Y=23
                    "LYYYYYLLLLLLLLLLLLLLLLLLLLLL         LO  O     U   U    O  O                  C       C                  O  O    U   U     O  OL         LLLLLLLLLLLLLLLLLLLLLLYYYYYL", // Y=24
                    "L     LLIIIIIIIIIIIIIIIIIILF         FX                    X                 HC       CH                 X                    XF         FLIIIIIIIIIIIIIIIIIILL     L", // Y=25
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                   C     C                   X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=26
                    "L     LLIIIIIIIIIIIIIIIIIIL           X                    X                    C   C                    X                    X           LIIIIIIIIIIIIIIIIIILL     L", // Y=27
                    "                                      VWW                WWV                     CCC                     VWW                WWV                                      ", // Y=28
                    "                                      X                    X                     H H                     X                    X                                      ", // Y=29
                    "                                      X                    X                                             X                    X                                      ", // Y=30
                    "                                      X                    X                                             X                    X                                      ", // Y=31
                    "                                      O  O              O  O                                             O  O              O  O                                      ", // Y=32
                    "                                      O                    O                                             O                    O                                      ", // Y=33
                    "                                      O  OOFMMMMMMMMMMMOO  O                                             O  OOMMMMMMMMMMMFOO  O                                      ", // Y=34
                    "                                      OSSO MM         MFOSSO                                             OSSOFM         MM OSSO                                      ", // Y=35
                    "                                      OSSOM            ROSSO                                             OSSOR            MOSSO                                      ", // Y=36
                    "                                         OM            ROQ              QQ                   QQ             OR            MO                                         ", // Y=37
                    "                                          M            RQ                                                   QR            M                                          ", // Y=38
                    "                                           MM         MFP                                                   PFM         MM                                           ", // Y=39
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=40
                    "                                B                                                                                                   B                                ", // Y=41
                    "                                B                                                                                                   B                                ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=39
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                B                                                                                                   B                                ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=9
                    "                                           MM         MFP                                                   PFM         MM                                           ", // Y=10
                    "                                          FMM         MF                                                 QQ  FM         MMF                                          ", // Y=11
                    "                                           MM         MF            QQ                   QQ            QQ    FM         MM                                           ", // Y=12
                    "                                           FMMMMMMMMMMM           QQ                   QQ            QQ       MMMMMMMMMMMF                                           ", // Y=13
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=14
                    "                                      O  OOOOOOOOOOOOOOOO  O                                             O  OOOOOOOOOOOOOOOO  O                                      ", // Y=15
                    "                                      O  OOOOOIIIIIIIOOOO  O                                             O  OOOOIIIIIIIOOOOO  O                                      ", // Y=16
                    "                                      X  O              O  X                                             X  O              O  X                                      ", // Y=17
                    "                                      X  O              O  X                                             X  O              O  X                                      ", // Y=18
                    "                                      X  O              O  X                     H H                     X  O              O  X                                      ", // Y=19
                    "                                      VWWO              OWWV                     CCC                     VWWO              OWWV                                      ", // Y=20
                    "                                      X  O              O  X                    C   C                    X  O              O  X                                      ", // Y=21
                    "                                      X  O              O  X                   C     C                   X  O              O  X                                      ", // Y=22
                    "                           F         FX  O FMMMMMMMMMMM O  X                 HC       CH                 X  O MMMMMMMMMMMF O  XF         F                           ", // Y=23
                    "                           L         LO  O FMMNNNNNNNMM O  O                  C       C                  O  O MMNNNNNNNMMF O  OL         L                           ", // Y=24
                    "                           F         FX  O FMMMMMMMMMMM O  X                 HC       CH                 X  O MMMMMMMMMMMF O  XF         F                           ", // Y=25
                    "                                      X  O              O  X                   C     C                   X  O              O  X                                      ", // Y=26
                    "                                      X  O              O  X                    C   C                    X  O              O  X                                      ", // Y=27
                    "                                      VWWO              OWWV                     CCC                     VWWO              OWWV                                      ", // Y=28
                    "                                      X  O              O  X                     H H                     X  O              O  X                                      ", // Y=29
                    "                                      X  O              O  X                                             X  O              O  X                                      ", // Y=30
                    "                                      X  O              O  X                                             X  O              O  X                                      ", // Y=31
                    "                                      O  OOOOOIIIIIIIOOOO  O                                             O  OOOOIIIIIIIOOOOO  O                                      ", // Y=32
                    "                                      O  OOOOOOOOOOOOOOOO  O                                             O  OOOOOOOOOOOOOOOO  O                                      ", // Y=33
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=34
                    "                                           FMMMMMMMMMMM       QQ            QQ                   QQ           MMMMMMMMMMMF                                           ", // Y=35
                    "                                           MM         MF    QQ            QQ                   QQ            FM         MM                                           ", // Y=36
                    "                                          FMM         MF  QQ                                                 FM         MMF                                          ", // Y=37
                    "                                           MM         MFP                                                   PFM         MM                                           ", // Y=38
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                B                                                                                                   B                                ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=40
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                B                                                                                                   B                                ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                                                                                                                                                     ", // Y=9
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=10
                    "                                           FMMNNNNNNNMM                                                       MMNNNNNNNMMF                                           ", // Y=11
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=12
                    "                                                                                                                                                                     ", // Y=13
                    "                                                                 Q                    Q            QQ                                                                ", // Y=14
                    "                                      OOOO              OOOO                                             OOOO              OOOO                                      ", // Y=15
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=16
                    "                                      O  OOOOOOOOOOOOOOOO  O                                             O  OOOOOOOOOOOOOOOO  O                                      ", // Y=17
                    "                                      X   OOOOIIIIIIIOOO   X                                             X   OOOIIIIIIIOOOO   X                                      ", // Y=18
                    "                                      X   OOOOOOOOOOOOOO   X                                             X   OOOOOOOOOOOOOO   X                                      ", // Y=19
                    "                                      VWW                WWV                     CCC                     VWW                WWV                                      ", // Y=20
                    "                                      X                    X                    CIJIC                    X                    X                                      ", // Y=21
                    "                                      X    FMMMMMMMMMMM    X                   CIIJIIC                   X    MMMMMMMMMMMF    X                                      ", // Y=22
                    "                           F         FX    MM         MF   X                  CIIIKIIIC                  X   FM         MM    XF         F                           ", // Y=23
                    "                           LL       LLO  OFMM         MFO  O                  CJJKKKJJC                  O  OFM         MMFO  OLL       LL                           ", // Y=24
                    "                           F         FX    MM         MF   X                  CIIIKIIIC                  X   FM         MM    XF         F                           ", // Y=25
                    "                                      X    FMMMMMMMMMMM    X                   CIIJIIC                   X    MMMMMMMMMMMF    X                                      ", // Y=26
                    "                                      X                    X                    CIJIC                    X                    X                                      ", // Y=27
                    "                                      VWW                WWV                     CCC                     VWW                WWV                                      ", // Y=28
                    "                                      X   OOOOOOOOOOOOOO   X                                             X   OOOOOOOOOOOOOO   X                                      ", // Y=29
                    "                                      X   OOOOIIIIIIIOOO   X                                             X   OOOIIIIIIIOOOO   X                                      ", // Y=30
                    "                                      O  OOOOOOOOOOOOOOOO  O                                             O  OOOOOOOOOOOOOOOO  O                                      ", // Y=31
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=32
                    "                                      OOOO              OOOO                                             OOOO              OOOO                                      ", // Y=33
                    "                                                                QQ            Q                    Q                                                                 ", // Y=34
                    "                                                                                                                                                                     ", // Y=35
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=36
                    "                                           FMMNNNNNNNMM                                                       MMNNNNNNNMMF                                           ", // Y=37
                    "                                           FMMMMMMMMMMM                                                       MMMMMMMMMMMF                                           ", // Y=38
                    "                                                                                                                                                                     ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                B                                                                                                   B                                ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=41
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                B                                                                                                   B                                ", // Y=8
                    "                                B                                                                                                   B                                ", // Y=9
                    "                                B                                                                                                   B                                ", // Y=10
                    "                                                                                                                                                                     ", // Y=11
                    "                                                                                                                                                                     ", // Y=12
                    "                                                                                                                                                                     ", // Y=13
                    "                                                                                                                                                                     ", // Y=14
                    "                                                                Q                    Q           QQ                                                                  ", // Y=15
                    "                                                               Q                    Q          QQ                                                                    ", // Y=16
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=17
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=18
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=19
                    "                                      VWWVOOOOOOOOOOOOOOVWWV                   OPCCC                     VWWVOOOOOOOOOOOOOOVWWV                                      ", // Y=20
                    "                                      V  VOFMMMMMMMMMMMOV  V                   OC   C                    V  VOMMMMMMMMMMMFOV  V                                      ", // Y=21
                    "                                      V  VOMM         MFV  V                   C     C                   V  VFM         MMOV  V                                      ", // Y=22
                    "                                      V  VM            RV  V                  C       C                  V  VR            MV  V                                      ", // Y=23
                    "                            L       L O  OM            RO  O                  C       C                  O  OR            MO  O L       L                            ", // Y=24
                    "                                      V  VM            RV  V                  C       C                  V  VR            MV  V                                      ", // Y=25
                    "                                      V  VOMM         MFV  V                   C     C                   V  VFM         MMOV  V                                      ", // Y=26
                    "                                      V  VOFMMMMMMMMMMMOV  V                    C   CO                   V  VOMMMMMMMMMMMFOV  V                                      ", // Y=27
                    "                                      VWWVOOOOOOOOOOOOOOVWWV                     CCCPO                   VWWVOOOOOOOOOOOOOOVWWV                                      ", // Y=28
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=29
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=30
                    "                                      OSSO              OSSO                                             OSSO              OSSO                                      ", // Y=31
                    "                                                                    QQ          Q                    Q                                                               ", // Y=32
                    "                                                                  QQ           Q                    Q                                                                ", // Y=33
                    "                                                                                                                                                                     ", // Y=34
                    "                                                                                                                                                                     ", // Y=35
                    "                                                                                                                                                                     ", // Y=36
                    "                                                                                                                                                                     ", // Y=37
                    "                                B                                                                                                   B                                ", // Y=38
                    "                                B                                                                                                   B                                ", // Y=39
                    "                                B                                                                                                   B                                ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=42
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                                                                                                                                                     ", // Y=9
                    "                                B                                                                                                   B                                ", // Y=10
                    "                                B                                                                                                   B                                ", // Y=11
                    "                                                                                                                                                                     ", // Y=12
                    "                                                                                                                                                                     ", // Y=13
                    "                                                                                                                                                                     ", // Y=14
                    "                                                                                                                                                                     ", // Y=15
                    "                                                                                                                                                                     ", // Y=16
                    "                                                              Q                    Q         QQ                                                                      ", // Y=17
                    "                                                             Q                    Q        QQ                                                                        ", // Y=18
                    "                                                            Q                    Q       QQ                                                                          ", // Y=19
                    "                                      OTTO              OTTO                   PQCCCOO                   OTTO              OTTO                                      ", // Y=20
                    "                                      OSSO FMMNNNNNNNMM OSSO                   OC   CP                   OSSO MMNNNNNNNMMF OSSO                                      ", // Y=21
                    "                                      OSSOFMM         MFOSSO                   C     C                   OSSOFM         MMFOSSO                                      ", // Y=22
                    "                                      OSSOM            ROSSO                  C       C                  OSSOR            MOSSO                                      ", // Y=23
                    "                            LL     LL OOOONUUUUUUUUUUUUUOOOO                  C       C                  OOOOUUUUUUUUUUUUUNOOOO LL     LL                            ", // Y=24
                    "                                      OSSOM            ROSSO                  C       C                  OSSOR            MOSSO                                      ", // Y=25
                    "                                      OSSOFMM         MFOSSO                   C     C                   OSSOFM         MMFOSSO                                      ", // Y=26
                    "                                      OSSO FMMNNNNNNNMM OSSO                   PC   CO                   OSSO MMNNNNNNNMMF OSSO                                      ", // Y=27
                    "                                      OTTO              OTTO                   OOCCCQP                   OTTO              OTTO                                      ", // Y=28
                    "                                                                          QQ       Q                    Q                                                            ", // Y=29
                    "                                                                        QQ        Q                    Q                                                             ", // Y=30
                    "                                                                      QQ         Q                    Q                                                              ", // Y=31
                    "                                                                                                                                                                     ", // Y=32
                    "                                                                                                                                                                     ", // Y=33
                    "                                                                                                                                                                     ", // Y=34
                    "                                                                                                                                                                     ", // Y=35
                    "                                                                                                                                                                     ", // Y=36
                    "                                B                                                                                                   B                                ", // Y=37
                    "                                B                                                                                                   B                                ", // Y=38
                    "                                                                                                                                                                     ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=43
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                                                                                                                                                     ", // Y=9
                    "                                                                                                                                                                     ", // Y=10
                    "                                B                                                                                                   B                                ", // Y=11
                    "                                B                                                                                                   B                                ", // Y=12
                    "                                                                                                                                                                     ", // Y=13
                    "                                                                                                                                                                     ", // Y=14
                    "                                                                                                                                                                     ", // Y=15
                    "                                                                                                                                                                     ", // Y=16
                    "                                                                                                                                                                     ", // Y=17
                    "                                                                                                                                                                     ", // Y=18
                    "                                                                                                                                                                     ", // Y=19
                    "                                                           Q                   OPCCCOP QQ                                                                            ", // Y=20
                    "                                           FMMMMMMMMMMM   Q                    OCIJICQQ                       MMMMMMMMMMMF                                           ", // Y=21
                    "                                           MM         MFPQ                     CIIJIIC                       FM         MM                                           ", // Y=22
                    "                                          M            RQ                     CIIISIIIC                      R            M                                          ", // Y=23
                    "                             L     L    OOM            ROO                    CJJSSSJJC                    OOR            MOO    L     L                             ", // Y=24
                    "                                          M            R                      CIIISIIIC                     QR            M                                          ", // Y=25
                    "                                           MM         MF                       CIIJIIC                     QPFM         MM                                           ", // Y=26
                    "                                           FMMMMMMMMMMM                       QQCIJICO                    Q   MMMMMMMMMMMF                                           ", // Y=27
                    "                                                                            QQ POCCCPO                   Q                                                           ", // Y=28
                    "                                                                                                                                                                     ", // Y=29
                    "                                                                                                                                                                     ", // Y=30
                    "                                                                                                                                                                     ", // Y=31
                    "                                                                                                                                                                     ", // Y=32
                    "                                                                                                                                                                     ", // Y=33
                    "                                                                                                                                                                     ", // Y=34
                    "                                                                                                                                                                     ", // Y=35
                    "                                B                                                                                                   B                                ", // Y=36
                    "                                B                                                                                                   B                                ", // Y=37
                    "                                                                                                                                                                     ", // Y=38
                    "                                                                                                                                                                     ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=44
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                                                                                                                                                     ", // Y=9
                    "                                                                                                                                                                     ", // Y=10
                    "                                                                                                                                                                     ", // Y=11
                    "                                B                                                                                                   B                                ", // Y=12
                    "                                B                                                                                                   B                                ", // Y=13
                    "                                B                                                                                                   B                                ", // Y=14
                    "                                                                                                                                                                     ", // Y=15
                    "                                                                                                                                                                     ", // Y=16
                    "                                                                                                                                                                     ", // Y=17
                    "                                                                                                                                                                     ", // Y=18
                    "                                                                                                                                                                     ", // Y=19
                    "                                                                                 CCCOO                                                                               ", // Y=20
                    "                                                                                C   CP                                                                               ", // Y=21
                    "                                           FMMMMMMMMMMM                        C     C                        MMMMMMMMMMMF                                           ", // Y=22
                    "                                           MM         MFP                     C       C                      FM         MM                                           ", // Y=23
                    "                             LL   LL     OOMM         MFO                     C       C                     OFM         MMOO     LL   LL                             ", // Y=24
                    "                                           MM         MF                      C       C                     PFM         MM                                           ", // Y=25
                    "                                           FMMMMMMMMMMM                        C     C                        MMMMMMMMMMMF                                           ", // Y=26
                    "                                                                               PC   C                                                                                ", // Y=27
                    "                                                                               OOCCC                                                                                 ", // Y=28
                    "                                                                                                                                                                     ", // Y=29
                    "                                                                                                                                                                     ", // Y=30
                    "                                                                                                                                                                     ", // Y=31
                    "                                                                                                                                                                     ", // Y=32
                    "                                                                                                                                                                     ", // Y=33
                    "                                B                                                                                                   B                                ", // Y=34
                    "                                B                                                                                                   B                                ", // Y=35
                    "                                B                                                                                                   B                                ", // Y=36
                    "                                                                                                                                                                     ", // Y=37
                    "                                                                                                                                                                     ", // Y=38
                    "                                                                                                                                                                     ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=45
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                                                                                                                                                     ", // Y=9
                    "                                                                                                                                                                     ", // Y=10
                    "                                                                                                                                                                     ", // Y=11
                    "                                                                                                                                                                     ", // Y=12
                    "                                                                                                                                                                     ", // Y=13
                    "                                B                                                                                                   B                                ", // Y=14
                    "                                B                                                                                                   B                                ", // Y=15
                    "                                B                                                                                                   B                                ", // Y=16
                    "                                                                                                                                                                     ", // Y=17
                    "                                                                                                                                                                     ", // Y=18
                    "                                                                                                                                                                     ", // Y=19
                    "                                                                                 CCC                                                                                 ", // Y=20
                    "                                                                                C   C                                                                                ", // Y=21
                    "                                                                               C     C                                                                               ", // Y=22
                    "                                           FMMMMMMMMMMM                       C       C                       MMMMMMMMMMMF                                           ", // Y=23
                    "                              L   L        FMMNNNNNNNMM                       C       C                       MMNNNNNNNMMF        L   L                              ", // Y=24
                    "                                           FMMMMMMMMMMM                       C       C                       MMMMMMMMMMMF                                           ", // Y=25
                    "                                                                               C     C                                                                               ", // Y=26
                    "                                                                                C   C                                                                                ", // Y=27
                    "                                                                                 CCC                                                                                 ", // Y=28
                    "                                                                                                                                                                     ", // Y=29
                    "                                                                                                                                                                     ", // Y=30
                    "                                                                                                                                                                     ", // Y=31
                    "                                B                                                                                                   B                                ", // Y=32
                    "                                B                                                                                                   B                                ", // Y=33
                    "                                B                                                                                                   B                                ", // Y=34
                    "                                                                                                                                                                     ", // Y=35
                    "                                                                                                                                                                     ", // Y=36
                    "                                                                                                                                                                     ", // Y=37
                    "                                                                                                                                                                     ", // Y=38
                    "                                                                                                                                                                     ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=46
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                                                                                                                                                     ", // Y=9
                    "                                                                                                                                                                     ", // Y=10
                    "                                                                                                                                                                     ", // Y=11
                    "                                                                                                                                                                     ", // Y=12
                    "                                                                                                                                                                     ", // Y=13
                    "                                                                                                                                                                     ", // Y=14
                    "                                                                                                                                                                     ", // Y=15
                    "                                B                                                                                                   B                                ", // Y=16
                    "                                B                                                                                                   B                                ", // Y=17
                    "                                B                                                                                                   B                                ", // Y=18
                    "                                B                                                HHH                                                B                                ", // Y=19
                    "                                B                                               HCCCH                                               B                                ", // Y=20
                    "                                                                               HCIJICH                                                                               ", // Y=21
                    "                                                                              HCIIJIICH                                                                              ", // Y=22
                    "                               FFF                                           HCIIIKIIICH                                           FFF                               ", // Y=23
                    "                              LLFLL                                          HCJJKKKJJCH                                          LLFLL                              ", // Y=24
                    "                               FFF                                           HCIIIKIIICH                                           FFF                               ", // Y=25
                    "                                                                              HCIIJIICH                                                                              ", // Y=26
                    "                                                                               HCIJICH                                                                               ", // Y=27
                    "                                B                                               HCCCH                                               B                                ", // Y=28
                    "                                B                                                HHH                                                B                                ", // Y=29
                    "                                B                                                                                                   B                                ", // Y=30
                    "                                B                                                                                                   B                                ", // Y=31
                    "                                B                                                                                                   B                                ", // Y=32
                    "                                                                                                                                                                     ", // Y=33
                    "                                                                                                                                                                     ", // Y=34
                    "                                                                                                                                                                     ", // Y=35
                    "                                                                                                                                                                     ", // Y=36
                    "                                                                                                                                                                     ", // Y=37
                    "                                                                                                                                                                     ", // Y=38
                    "                                                                                                                                                                     ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=47
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                                                                                                                                                     ", // Y=9
                    "                                                                                                                                                                     ", // Y=10
                    "                                                                                                                                                                     ", // Y=11
                    "                                                                                                                                                                     ", // Y=12
                    "                                                                                                                                                                     ", // Y=13
                    "                                                                                                                                                                     ", // Y=14
                    "                                                                                                                                                                     ", // Y=15
                    "                                                                                                                                                                     ", // Y=16
                    "                                                                                                                                                                     ", // Y=17
                    "                                                                                                                                                                     ", // Y=18
                    "                                                                                                                                                                     ", // Y=19
                    "                                B                                                CCC                                                B                                ", // Y=20
                    "                                B                                               CDDDC                                               B                                ", // Y=21
                    "                                B                                              CDEEEDC                                              B                                ", // Y=22
                    "                                F                                             CDEGGGEDC                                             F                                ", // Y=23
                    "                               EEE                                            CDEGGGEDC                                            EEE                               ", // Y=24
                    "                                F                                             CDEGGGEDC                                             F                                ", // Y=25
                    "                                B                                              CDEEEDC                                              B                                ", // Y=26
                    "                                B                                               CDDDC                                               B                                ", // Y=27
                    "                                B                                                CCC                                                B                                ", // Y=28
                    "                                                                                                                                                                     ", // Y=29
                    "                                                                                                                                                                     ", // Y=30
                    "                                                                                                                                                                     ", // Y=31
                    "                                                                                                                                                                     ", // Y=32
                    "                                                                                                                                                                     ", // Y=33
                    "                                                                                                                                                                     ", // Y=34
                    "                                                                                                                                                                     ", // Y=35
                    "                                                                                                                                                                     ", // Y=36
                    "                                                                                                                                                                     ", // Y=37
                    "                                                                                                                                                                     ", // Y=38
                    "                                                                                                                                                                     ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
            { // Z=48
                    "                                                                                                                                                                     ", // Y=0
                    "                                                                                                                                                                     ", // Y=1
                    "                                                                                                                                                                     ", // Y=2
                    "                                                                                                                                                                     ", // Y=3
                    "                                                                                                                                                                     ", // Y=4
                    "                                                                                                                                                                     ", // Y=5
                    "                                                                                                                                                                     ", // Y=6
                    "                                                                                                                                                                     ", // Y=7
                    "                                                                                                                                                                     ", // Y=8
                    "                                                                                                                                                                     ", // Y=9
                    "                                                                                                                                                                     ", // Y=10
                    "                                                                                                                                                                     ", // Y=11
                    "                                                                                                                                                                     ", // Y=12
                    "                                                                                                                                                                     ", // Y=13
                    "                                                                                                                                                                     ", // Y=14
                    "                                                                                                                                                                     ", // Y=15
                    "                                                                                                                                                                     ", // Y=16
                    "                                                                                                                                                                     ", // Y=17
                    "                                                                                                                                                                     ", // Y=18
                    "                                                                                                                                                                     ", // Y=19
                    "                                                                                                                                                                     ", // Y=20
                    "                                                                                                                                                                     ", // Y=21
                    "                                                                                 AAA                                                                                 ", // Y=22
                    "                                                                                AAAAA                                                                                ", // Y=23
                    "                                                                                AAaAA                                                                                ", // Y=24
                    "                                                                                AAAAA                                                                                ", // Y=25
                    "                                                                                 AAA                                                                                 ", // Y=26
                    "                                                                                                                                                                     ", // Y=27
                    "                                                                                                                                                                     ", // Y=28
                    "                                                                                                                                                                     ", // Y=29
                    "                                                                                                                                                                     ", // Y=30
                    "                                                                                                                                                                     ", // Y=31
                    "                                                                                                                                                                     ", // Y=32
                    "                                                                                                                                                                     ", // Y=33
                    "                                                                                                                                                                     ", // Y=34
                    "                                                                                                                                                                     ", // Y=35
                    "                                                                                                                                                                     ", // Y=36
                    "                                                                                                                                                                     ", // Y=37
                    "                                                                                                                                                                     ", // Y=38
                    "                                                                                                                                                                     ", // Y=39
                    "                                                                                                                                                                     ", // Y=40
                    "                                                                                                                                                                     ", // Y=41
                    "                                                                                                                                                                     ", // Y=42
                    "                                                                                                                                                                     ", // Y=43
                    "                                                                                                                                                                     ", // Y=44
                    "                                                                                                                                                                     ", // Y=45
                    "                                                                                                                                                                     ", // Y=46
                    "                                                                                                                                                                     ", // Y=47
            },
    };

    static BlockPattern build(MultiblockMachineDefinition definition) {
        FactoryBlockPattern pattern = FactoryBlockPattern.start();
        for (String[] slice : SLICES) {
            pattern.aisle(slice);
        }

        // A/O：分子机械方块（外壳）—— 同时承担 IO 仓、能源输入、激光仓、线程仓、并行仓、
        // 1 个光口、1 个维护仓。
        //
        // ★ 0.3.286（用户：「可他多方块预览里面显示的却是全部限权的」）：
        //   上一版这里把**每一种能力**都 `Predicates.abilities(...)` 一条条 OR 进 common，而且
        //   除了能源/激光都没给 `previewCount` ⇒ GTCEu 1.4.4 的 `BlockPattern.getPreview` 里
        //   （逐指令见 `_scratch/gtceu_dec/BlockPattern.java:414-521`、本工程 `HatchPredicates` 的类注释）：
        //     · 第 3 趟只画 `common` 里 `previewCount > 0` 的谓词 —— 我们的 IO 能力全是 -1 ⇒ 一格都不画；
        //     · 第 4 趟退回 `common` 里**第一条** `previewCount == -1` 的谓词 ⇒ 命中的是外壳机壳本身；
        //   于是预览里每个 A/O 位都只画「分子机械方块」，点开候选又是一整排「该能力下的全部机器」，
        //   看上去就是「全部（都能放）」—— 这正是用户说的现象。
        //
        //   GTO 原文（`_gto_src/src/main/java/com/gtocore/common/data/machines/MultiBlockD.java:429-431`）：
        //     .where('A', blocks(GTOBlocks.MOLECULAR_CASING.get())
        //             .or(GTOPredicates.autoThreadLaserAbilities(definition.getRecipeTypes()))
        //             .or(abilities(OPTICAL_DATA_RECEPTION).setExactLimit(1)))
        //   `autoThreadLaserAbilities` = `autoLaserAbilities` + THREAD_HATCH(限1) + OVERCLOCK_HATCH(限1)
        //   + ACCELERATE_HATCH(限1)；`autoLaserAbilities` = `autoAbilities(recipeTypes,false,false,
        //   true,true,true,true)`（四种常规 IO 仓，**每条都 setPreviewCount(1)**）
        //   + INPUT_ENERGY(最多 2、previewCount 0) + INPUT_LASER(最多 2、previewCount 1)。
        //   ⇒ GTO 的预览里 A/O 位会画出「分子机械方块 + 每类仓各一格」。
        //
        //   本包**没有**的两条能力（逐处取证后跳过）：
        //     · GTOPartAbility.OVERCLOCK_HATCH（超频仓）/ ACCELERATE_HATCH（加速仓）来自 GTOCore；
        //       本包没装 GTOCore（实例 mods 只有它的替代品 GTLcore / GTLAdditions）。
        //       GTCEu 1.4.4 的 `PartAbility` 常量表里没有这两个（javap 全表见
        //       `_scratch/gtceu_dec/PartAbility.txt`）；GTLcore 的 `GTLPartAbility` 只有
        //       NEUTRON_ACCELERATOR / ITEMS_OUTPUT / MOLECULAR_ASSEMBLER_MATRIX / ME_CRAFTING_CPU_INTERFACE，
        //       GTLAdditions 只有 THREAD_MODIFIER ⇒ 没有对应方块可放，照抄不了。
        TraceabilityPredicate molecularCasing = blocksOf("gtlcore", "molecular_casing")
                // —— 常规 IO：对齐 GTO 的 autoAbilities(...)，每条 setPreviewCount(1)（预览各占一格）
                .or(ability(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                .or(ability(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                .or(ability(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                .or(ability(PartAbility.EXPORT_FLUIDS).setPreviewCount(1))
                // —— 能源 / 激光：对齐 GTO 的 autoLaserAbilities —— 能源 previewCount 0
                //    （GTO 的预览用激光仓代表供电，不画能源仓），激光 previewCount 1。
                //    数值上限沿用本机既有口径（能源 8 / 激光 4），本次只对齐「允许的能力集合」与预览格。
                .or(ability(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(8, 0))
                .or(ability(PartAbility.INPUT_LASER).setMaxGlobalLimited(4, 1))
                // —— GTO 线程仓（0.3.285 起全局 1 个；GTO 同样是 setMaxGlobalLimited(1)）。
                //    GTO 这条没有 previewCount ⇒ 预览格不占位，只在点开候选时出现。
                .or(ability(ThreadHatchMachines.THREAD_HATCH).setMaxGlobalLimited(1))
                // 超频仓：凡可插线程仓的机器都同样可插
                .or(com.gtlmodular.common.data.fuel.OverclockHatches.predicate())
                .or(com.gtlmodular.common.data.fuel.AccelerateHatches.predicate())
                // —— 并行仓：队长要求保留（GTO 奈亚的谓词里没有它）。
                //    GTCEu 自己的 `autoAbilities(checkParallel)` 是 max 1 + previewCount(1)；
                //    本机一直是「不限个数」，本次不改上限，只让它像 GTCEu 那样占一格预览。
                .or(ability(PartAbility.PARALLEL_HATCH).setPreviewCount(1))
                // —— 光口 / 维护仓：各恰好 1（GTO 的光口同样是 setExactLimit(1)）。
                .or(ability(PartAbility.OPTICAL_DATA_RECEPTION).setExactLimit(1))
                .or(ability(PartAbility.MAINTENANCE).setExactLimit(1));

        return pattern
                .where('A', molecularCasing)
                .where('O', molecularCasing)
                // ★ 0.3.285 线圈核对（用户怀疑我们绑错）——结论：**没绑错，保持 gtlcore:advanced_fusion_coil**。
                //   GTO 给 'B' 绑的是 `GTOBlocks.ADVANCED_FUSION_COIL`（进阶聚变线圈方块，gtocore 命名空间），
                //   不是通用线圈，也不是「超级热熔线圈」（后者是 'D' = URUIUM_COIL_BLOCK）。取证：
                //     - 源码 `_gto_src/.../data/machines/MultiBlockD.java:432`
                //       `.where('B', blocks(GTOBlocks.ADVANCED_FUSION_COIL.get()))`
                //     - 字节码 `gtocore-forge-1.20.1-26.10.1.jar` 的 `MultiBlockD.class`（71,382 B，非 native 壳）：
                //       `bipush 66`（'B'）→ `getstatic GTOBlocks.ADVANCED_FUSION_COIL` → `Predicates.blocks(Block[])`
                //       → `Symbols.where(C, ...)`；随后 `bipush 67` = 'C' → LASER_CASING、
                //       `bipush 68` = 'D' → `GTOBlocks.URUIUM_COIL_BLOCK`（超级热熔线圈方块）。
                //     - `GTOBlocks.java:258`：`createActiveCasing("advanced_fusion_coil", "进阶聚变线圈方块", ...)`
                //     - 结构文件 `pattern/nyarlathoteps_tentacle.mbs` 解码后与我们的 SLICES **2352 行逐字相同**
                //       （0 差异）：'B' = 336 格，位置就是 GTO 的 B 位置。
                //   为什么仍然写 gtlcore：本整合包（实例 mods 94 个 jar）**没有装 GTOCore**，只有 GTLcore；
                //   GTLcore 的 `GTLBlocks` 用同一个 id 镜像了这个方块（`ldc "advanced_fusion_coil"` →
                //   `createActiveCasing` ⇒ `gtlcore:advanced_fusion_coil`），且本包 KubeJS
                //   `server_scripts/ore_processing.js:258` 给它挂了与 GTO 逐项相同的装配机配方
                //   （改良型超导线圈 + 2x LuV 力场发生器 + IV 泵 + 4x 中子反射板 + hui_circuit_2 +
                //   4x#gtceu:circuits/luv + 硅岩普通流体管 + 4x 镝板，576 YBCO，100t）
                //   ⇒ 这就是「GTO 谓词等价物」。若改成 gtocore:advanced_fusion_coil，本包根本没有该方块，
                //   blocksOf 取不到（退回惰性谓词）⇒ 该符号永远不匹配、机器反而**不能成形**。
                .where('B', blocksOf("gtlcore", "advanced_fusion_coil"))   // ★ 2026-11-02 队长口径：改回「进阶聚变线圈方块」，不放开了
                .where('C', Predicates.blocks(TentacleBlocks.LASER_CASING.get()))
                .where('D', com.gtlmodular.common.data.host.MoltenModuleHostPattern.singleTypeHeatingCoils())   // ★ 2026-11-04 队长口径：只能一种线圈，混搭不成形   // ★ 2026-11-02 队长口径：
                //   超级热熔线圈位允许换成任意可替换线圈（与终端可替换线圈一致，按 ICoilType 判定，取最热值作 CoilType）
                .where('E', blocksOf("gtlcore", "hyper_core"))
                .where('F', blocksOf("gtceu", "neutronium_frame"))
                .where('G', blocksOf("kubejs", "spacetime_assembly_line_casing"))
                .where('H', blocksOf("gtladditions", "extreme_density_casing"))
                .where('I', blocksOf("gtceu", "fusion_glass"))
                .where('J', blocksOf("kubejs", "laser_cooling_casing"))
                .where('K', blocksOf("gtladditions", "quantum_glass"))
                .where('L', blocksOf("gtlcore", "naquadah_alloy_casing"))
                .where('M', blocksOf("gtlcore", "sps_casing"))
                .where('N', blocksOf("kubejs", "spacetime_assembly_line_unit"))
                .where('P', blocksOf("kubejs", "containment_field_generator"))
                .where('Q', Predicates.heatingCoils())
                .where('R', blocksOf("gtceu", "sterilizing_filter_casing"))
                .where('S', Predicates.blocks(TentacleBlocks.ELECTRON_PERMEABLE_AMPROSIUM_COATED_GLASS.get()))
                .where('T', blocksOf("kubejs", "hollow_casing"))
                .where('U', Predicates.blocks(TentacleBlocks.AMPROSIUM_ACTIVE_CASING.get()))
                .where('V', blocksOf("gtceu", "mithril_frame"))
                .where('W', blocksOf("gtceu", "ruby_block"))
                .where('X', blocksOf("gtceu", "electrolytic_cell"))
                .where('Y', blocksOf("gtceu", "trinium_frame"))
                .where('Z', blocksOf("gtceu", "high_power_casing"))
                .where('[', blocksOf("kubejs", "dimensional_bridge_casing"))
                .where(']', Predicates.blocks(TentacleBlocks.QUARK_PIPE.get()))
                .where('^', blocksOf("kubejs", "molecular_coil"))
                .where('_', blocksOf("gtlcore", "degenerate_rhenium_constrained_casing"))
                .where('`', blocksOf("gtlcore", "dimensionally_transcendent_casing"))
                .where('+', blocksOf("kubejs", "restraint_device"))
                .where('~', blocksOf("kubejs", "restraint_device")) // ★ 26.10.1 的 jar 把 '~' 绑到力场约束装置
                .where('a', Predicates.controller(Predicates.blocks(definition.get())))
                .where(' ', Predicates.any())
                .build();
    }

    // ------------------------------------------------------------------
    // ★ 0.3.286：让结构预览「看得懂」的 方块 / 能力 谓词
    // ------------------------------------------------------------------

    /** 档位前缀（低 → 高），与 GTCEu {@code GTValues.VN} 的小写形式一致。 */
    private static final String[] TIER_PREFIXES = {
            "ulv", "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv", "uev", "uiv", "uxv", "opv", "max"
    };

    /**
     * 方块 id → GTCEu 原生 {@link Predicates#blocks(Block...)} 谓词（内部就是 {@code PredicateBlocks}）。
     *
     * <p><b>为什么不再用 {@link #lazy(String)}</b>：{@code lazy(...)} 是自定义
     * {@code Predicate<MultiblockState>}，GTCEu 的预览虽然也读它的 {@code candidates}，但它不是
     * GTCEu 认得的 {@code PredicateBlocks} —— 类型、候选表、以及「这一格到底是什么方块」都没法被上游
     * 统一处理。换成原生 {@code blocks(...)} 后，预览（{@code BlockPattern.getPreview} 的四趟挑选）
     * 与方块格式检查走的就是 GTCEu 给普通方块用的那条路。
     *
     * <p><b>为什么不会重蹈「注册期取不到 KubeJS 方块」</b>：{@code MultiblockMachineBuilder.pattern(...)}
     * 在注册时只塞一个 {@code SupplierMemoizer.memoize(...)}（见
     * {@code _scratch/gtceu_dec/MultiblockMachineBuilder.java:366}），{@link #build} 要到
     * <b>首次结构判定 / 首次打开预览</b>才跑 —— 那时所有模组（含 KubeJS）的方块都已进注册表。
     * 万一真的提前跑到（取不到方块），退回 {@link #lazy(String)}（行为与 0.3.285 完全一致），
     * 绝不把「空方块数组」交给 {@code PredicateBlocks}（它会把空数组换成<b>屏障方块</b>，
     * 那是 GTCEu 用来表示「任意/占位」的方块）。
     */
    private static TraceabilityPredicate blocksOf(String namespace, String path) {
        Block block = blockById(new ResourceLocation(namespace, path));
        if (block == null) {
            GTLModular.LOGGER.error(
                    "[gtlmodular] 奈亚拉托提普之触结构：方块 {}/{} 取不到，退回惰性谓词（预览里该符号不显示具体方块）",
                    namespace, path);
            return lazy(namespace, path);
        }
        TraceabilityPredicate predicate = Predicates.blocks(block);
        // 预览候选：缓存一份 BlockInfo，避免每格回调都新建数组（旧 lazy 实现有同样的开销）。
        BlockInfo[] candidates = {BlockInfo.fromBlockState(block.defaultBlockState())};
        for (SimplePredicate simple : predicate.common) {
            simple.candidates = () -> candidates;
        }
        return predicate;
    }

    /**
     * <b>能力谓词</b>：GTCEu 原生 {@code Predicates.abilities(ability)}（= 该能力下的所有方块），
     * 但把<b>预览候选</b>换成「该能力的一台代表仓」。
     *
     * <p>为什么必须换候选（本工程已有的 40 秒卡死教训，见 {@code HatchPredicates} 的类注释）：
     * {@code PredicateBlocks.candidates} 是 {@code BlockInfo.fromBlock(block)}，而
     * {@code BlockInfo.fromBlock} 会真的 {@code newBlockEntity(...)}。{@code IMPORT_ITEMS} 这一条
     * 能力在本包里挂着 gtlcore / gtladditions 的 ME 样板总成（构造是 O(槽位²)），
     * 预览一构建就会把它们一台台 new 出来。换成「一台代表仓」后，预览每类能力只实例化 1 台，
     * 且选的是 GTCEu 自带的标准档位仓（确定性，不随 HashSet 迭代顺序变）。
     *
     * <p><b>匹配语义一个字没改</b>：判定仍是 {@code PredicateBlocks}
     * （{@code ArrayUtils.contains(blocks, state.getBlock())}），候选表只影响
     * 「JEI / 结构预览画哪台仓、点开候选看到哪台仓」。
     */
    private static TraceabilityPredicate ability(PartAbility ability) {
        if (ability.getAllBlocks().isEmpty()) {
            GTLModular.LOGGER.error(
                    "[gtlmodular] 奈亚拉托提普之触结构：PartAbility[{}] 下没有任何方块（该能力退化为永不匹配）",
                    ability.getName());
            return new TraceabilityPredicate(state -> false, () -> new BlockInfo[0]);
        }
        TraceabilityPredicate predicate = Predicates.abilities(ability);
        Block representative = representative(ability);
        BlockInfo candidate = representative == null ? null : blockInfo(representative);
        if (candidate != null) {
            BlockInfo[] candidates = {candidate};
            for (SimplePredicate simple : predicate.common) {
                simple.candidates = () -> candidates;
            }
            for (SimplePredicate simple : predicate.limited) {
                simple.candidates = () -> candidates;
            }
        }
        return predicate;
    }

    /**
     * 方块 → 预览用的 {@link BlockInfo}：优先 {@code fromBlock}（会真的 new 一次方块实体，
     * 机器渲染才完整），失败则退回 {@code fromBlockState}（只按方块模型画）。
     *
     * <p>为什么必须兜底：{@code BlockInfo.fromBlock} 在 LDLib 里对 {@code EntityBlock} 会调
     * {@code newBlockEntity(...)}，机器构造里若有依赖世界/等级的分支就可能抛异常；
     * 预览构建绝不能因为「某一台代表仓」而整体炸掉。
     */
    private static BlockInfo blockInfo(Block block) {
        try {
            return BlockInfo.fromBlock(block);
        } catch (Exception t) {
            GTLModular.LOGGER.warn(
                    "[gtlmodular] 奈亚拉托提普之触结构：预览候选 {} 实例化失败，退回方块状态渲染（{}）",
                    BuiltInRegistries.BLOCK.getKey(block), t.toString());
            return BlockInfo.fromBlockState(block.defaultBlockState());
        }
    }

    /**
     * 该能力在预览里的<b>代表仓</b>（确定性选择，不依赖 {@code HashSet} 的迭代顺序）：
     * <ol>
     *   <li>先按档位从低到高找 GTCEu 自带的 {@code gtceu:<档位>_<后缀>}（如 {@code gtceu:ulv_input_bus}）；</li>
     *   <li>再按档位从低到高找 id 以 {@code _<档位>} 结尾的（本模组线程仓是
     *       {@code gtlmodular:thread_hatch_uv} 这种「前缀_档位」写法）；</li>
     *   <li>都没有 ⇒ 取「{@code gtceu} 命名空间优先 + id 字典序最小」的那台。</li>
     * </ol>
     * 空能力（理论上不会发生，注册顺序错时才会）返回 {@code null}，由调用方保留 GTCEu 原候选。
     */
    private static Block representative(PartAbility ability) {
        Collection<Block> blocks = ability.getAllBlocks();
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        for (String tier : TIER_PREFIXES) {
            for (Block block : blocks) {
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                if (id != null && "gtceu".equals(id.getNamespace()) && id.getPath().startsWith(tier + "_")) {
                    return block;
                }
            }
        }
        for (String tier : TIER_PREFIXES) {
            for (Block block : blocks) {
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                if (id != null && id.getPath().endsWith("_" + tier)) {
                    return block;
                }
            }
        }
        Block best = null;
        String bestKey = null;
        for (Block block : blocks) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) {
                continue;
            }
            // "0" 前缀 = gtceu 自带（优先），"1" = 其它 addon；同前缀内按 id 字典序
            String key = ("gtceu".equals(id.getNamespace()) ? "0" : "1") + id;
            if (bestKey == null || key.compareTo(bestKey) < 0) {
                bestKey = key;
                best = block;
            }
        }
        return best;
    }

    /** 按 id 查方块；查不到或查到空气都返回 {@code null}。 */
    private static Block blockById(ResourceLocation id) {
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        return block == null || block == Blocks.AIR ? null : block;
    }

    /**
     * 按 id <b>惰性</b>解析方块谓词（解析发生在第一次结构判定/预览时，此时 KubeJS 方块也已注册）。
     *
     * <p>★ 0.3.286：正常路径已全部改走 {@link #blocksOf(String, String)}（GTCEu 原生
     * {@code blocks(...)}）—— 本方法只剩两个用途：① {@code blocksOf} 取不到方块时的兜底；
     * ② 记录「该符号退化为不判定」的 error 日志。
     *
     * <p>取不到时记 error 并退回「不判定」（{@link Predicates#any()} 的等价行为），绝不在注册期抛异常。
     */
    private static TraceabilityPredicate lazy(String namespace, String path) {
        ResourceLocation id = new ResourceLocation(namespace, path);
        return new TraceabilityPredicate(
                (MultiblockState state) -> {
                    Block block = ForgeRegistries.BLOCKS.getValue(id);
                    return block != null && block != Blocks.AIR
                            && state.getBlockState().getBlock() == block;
                },
                () -> {
                    Block block = ForgeRegistries.BLOCKS.getValue(id);
                    if (block == null || block == Blocks.AIR) {
                        GTLModular.LOGGER.error(
                                "[gtlmodular] 奈亚拉托提普之触结构：方块 {} 不存在（该符号退化为不判定）", id);
                        return new BlockInfo[0];
                    }
                    return new BlockInfo[]{BlockInfo.fromBlockState(block.defaultBlockState())};
                });
    }
}
