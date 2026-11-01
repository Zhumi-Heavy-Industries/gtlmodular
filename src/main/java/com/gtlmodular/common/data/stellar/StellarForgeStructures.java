package com.gtlmodular.common.data.stellar;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.common.data.GCyMBlocks;
import com.gtlmodular.GTLModular;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.api.pattern.GTLPredicates;
import org.gtlcore.gtlcore.common.block.BlockMap;

/**
 * 恒星炎炀锻炉（{@code gtceu:stellar_forge}）的新结构 —— GTO {@code gtocore:stellar_forge} 的
 * <b>1:1 照搬</b>（2026-10-31）。
 *
 * <h2>取证</h2>
 * <ul>
 *   <li>结构文件：GTO jar（{@code gtocore-0.5.6-pre2.jar} / 与 26.10.1 同 sha256）内
 *       {@code pattern/stellar_forge.mbs}（2,876 B）。解析器 {@code _scratch/stellar/parse_mbs.py}，
 *       与 {@code gtolib.MultiBlockFileReader} 同口径：
 *       {@code FF FF FF FF | 0F | ver=1 | charDir=2(LEFT) | stringDir=0(UP) | aisleDir=4(FRONT) |
 *       LEB(15) | LEB(65)}，每行 {@code [0x00][1 字节长度][字符]}，片间 1 字节标记。
 *       三个方向与 {@code FactoryBlockPattern.start()}（LEFT / UP / FRONT）逐一相同 ⇒ 直转、不颠倒。</li>
 *   <li>谓词绑定：GTO {@code MultiBlockA.STELLAR_FORGE}（{@code _gto_src/.../MultiBlockA.java:1879}）。</li>
 *   <li>尺寸 <b>65 (X) × 65 (Y) × 15 (Z) = 63,375 格</b>，实心 10,503 格，控制器 1 格（z=9 / y=1 / x=32，
 *       底部正面横条正中；横条只存在于 z=5..9 五片，控制器朝外无遮挡）。</li>
 * </ul>
 *
 * <h2>符号表（GTO → 本包）</h2>
 * <table border="1">
 *   <tr><th>符号</th><th>GTO</th><th>本包</th><th>格数</th></tr>
 *   <tr><td>A</td><td>高能机器外壳</td><td>gtceu:high_power_casing</td><td>336</td></tr>
 *   <tr><td>B</td><td>原子机械方块</td><td>gtceu:atomic_casing</td><td>2070</td></tr>
 *   <tr><td>C</td><td>原子机械方块 + 激光/维护仓位</td><td>gtceu:atomic_casing + autoAbilities + 维护仓×1</td><td>231</td></tr>
 *   <tr><td>D</td><td>三钛框架</td><td>gtceu:trinium_frame</td><td>250</td></tr>
 *   <tr><td>E</td><td>进阶聚变线圈</td><td>gtlcore:advanced_fusion_coil</td><td>147</td></tr>
 *   <tr><td>F</td><td>辐射吸收机械方块</td><td>gtlmodular:radiation_absorbent_casing（新造）</td><td>492</td></tr>
 *   <tr><td>G</td><td>超能机械方块</td><td>gtlcore:hyper_mechanical_casing</td><td>160</td></tr>
 *   <tr><td>H</td><td>碳化硼陶瓷耐辐射机械方块</td><td>gtlmodular:boron_carbide_ceramic_radiation_resistant_mechanical_cube（新造）</td><td>80</td></tr>
 *   <tr><td>I</td><td>强化超能机械方块</td><td>gtlcore:enhance_hyper_mechanical_casing</td><td>376</td></tr>
 *   <tr><td>J</td><td>氧化镁陶瓷高温绝缘机械方块</td><td>gtlmodular:magnesium_oxide_ceramic_high_temperature_insulation_mechanical_block（新造）</td><td>97</td></tr>
 *   <tr><td>K</td><td>磁力机械方块</td><td>gtlmodular:magtech_casing（新造）</td><td>60</td></tr>
 *   <tr><td>L</td><td>等离子加热机械方块</td><td>gtlmodular:plasma_heater_casing（新造）</td><td>424</td></tr>
 *   <tr><td>M</td><td>恒星热力容器（SCTier 分级方块）</td><td>gtlcore 三档 stellar_containment_casing（SCTier 判定）</td><td>4346</td></tr>
 *   <tr><td>N</td><td>量子玻璃</td><td>gtladditions:quantum_glass</td><td>1154</td></tr>
 *   <tr><td>O</td><td>中子稳定机械方块</td><td>gtlmodular:neutronium_stable_casing（新造）</td><td>236</td></tr>
 *   <tr><td>P</td><td>极密机械方块</td><td>gtladditions:extreme_density_casing</td><td>32</td></tr>
 *   <tr><td>Q</td><td>控制器</td><td>gtceu:stellar_forge（原机，换壳不换机）</td><td>1</td></tr>
 *   <tr><td>R</td><td>遏制场发生器</td><td>kubejs:containment_field_generator</td><td>2</td></tr>
 *   <tr><td>S</td><td>中空机械方块</td><td>kubejs:hollow_casing</td><td>9</td></tr>
 * </table>
 *
 * <p><b>为什么换结构而不是新注册机器</b>：本包已有 {@code gtceu:stellar_forge}（gtlcore 的 TierCasingMachine，
 * 配方表 {@code gtceu:stellar_forge} + SCTier），玩家要的是「同一台机器换成 GTO 的壳」⇒ 由
 * {@code mixin/gtceu/StellarForgePatternMixin} 在 {@code MultiblockMachineDefinition#setPatternFactory}
 * 处替换结构工厂（与「增强版核心」替换第三方结构的做法同一点位）。
 *
 * <p>GTO 的 {@code stellar_forge_magic} 子结构（魔法/模块形态）<b>不移植</b> —— 玩家明确「不用管它的模块」。
 */
public final class StellarForgeStructures {

    static final int WIDTH = 65;
    static final int HEIGHT = 65;
    static final int LENGTH = 15;

    private StellarForgeStructures() {
    }

    /** 15 片 Z 层（z=0 = 最背，z=14 = 最前），每层 65 行（Y=0 在最低），行宽 65。数据来自 GTO .mbs，直转无颠倒。 */
    private static final String[][] SLICES = {
            { // Z=0
                    "                                                                 ", // Y=0
                    "                                                                 ", // Y=1
                    "                                                                 ", // Y=2
                    "                                                                 ", // Y=3
                    "                                                                 ", // Y=4
                    "                                                                 ", // Y=5
                    "                                                                 ", // Y=6
                    "                                                                 ", // Y=7
                    "                                                                 ", // Y=8
                    "                                                                 ", // Y=9
                    "                                                                 ", // Y=10
                    "                                                                 ", // Y=11
                    "                                                                 ", // Y=12
                    "                                                                 ", // Y=13
                    "                                                                 ", // Y=14
                    "                                                                 ", // Y=15
                    "                                                                 ", // Y=16
                    "                                                                 ", // Y=17
                    "                                                                 ", // Y=18
                    "                             NNNNNNN                             ", // Y=19
                    "                          NNNNNNNNNNNNN                          ", // Y=20
                    "                         NNNNNNNNNNNNNNN                         ", // Y=21
                    "                       NNNNNNNNNNNNNNNNNNN                       ", // Y=22
                    "                      NNNNNNNNNNNNNNNNNNNNN                      ", // Y=23
                    "                      NNNNNNNNNNNNNNNNNNNNN                      ", // Y=24
                    "                     NNNNNNNNNNNNNNNNNNNNNNN                     ", // Y=25
                    "                    NNNNNNNNNNNNNNNNNNNNNNNNN                    ", // Y=26
                    "                    NNNNNNNNNNNNNNNNNNNNNNNNN                    ", // Y=27
                    "                    NNNNNNNNNNNNNNNNNNNNNNNNN                    ", // Y=28
                    "                   NNNNNNNNNNNNNNNNNNNNNNNNNNN                   ", // Y=29
                    "                   NNNNNNNNNNNNNNNNNNNNNNNNNNN                   ", // Y=30
                    "                   NNNNNNNNNNNNNNNNNNNNNNNNNNN                   ", // Y=31
                    "                   NNNNNNNNNNNNNNNNNNNNNNNNNNN                   ", // Y=32
                    "                   NNNNNNNNNNNNNNNNNNNNNNNNNNN                   ", // Y=33
                    "                   NNNNNNNNNNNNNNNNNNNNNNNNNNN                   ", // Y=34
                    "                   NNNNNNNNNNNNNNNNNNNNNNNNNNN                   ", // Y=35
                    "                    NNNNNNNNNNNNNNNNNNNNNNNNN                    ", // Y=36
                    "                    NNNNNNNNNNNNNNNNNNNNNNNNN                    ", // Y=37
                    "                    NNNNNNNNNNNNNNNNNNNNNNNNN                    ", // Y=38
                    "                     NNNNNNNNNNNNNNNNNNNNNNN                     ", // Y=39
                    "                      NNNNNNNNNNNNNNNNNNNNN                      ", // Y=40
                    "                      NNNNNNNNNNNNNNNNNNNNN                      ", // Y=41
                    "                       NNNNNNNNNNNNNNNNNNN                       ", // Y=42
                    "                         NNNNNNNNNNNNNNN                         ", // Y=43
                    "                          NNNNNNNNNNNNN                          ", // Y=44
                    "                             NNNNNNN                             ", // Y=45
                    "                                                                 ", // Y=46
                    "                                                                 ", // Y=47
                    "                                                                 ", // Y=48
                    "                                                                 ", // Y=49
                    "                                                                 ", // Y=50
                    "                                                                 ", // Y=51
                    "                                                                 ", // Y=52
                    "                                                                 ", // Y=53
                    "                                                                 ", // Y=54
                    "                                                                 ", // Y=55
                    "                                                                 ", // Y=56
                    "                                                                 ", // Y=57
                    "                                                                 ", // Y=58
                    "                                                                 ", // Y=59
                    "                                                                 ", // Y=60
                    "                                                                 ", // Y=61
                    "                                                                 ", // Y=62
                    "                                                                 ", // Y=63
                    "                                                                 ", // Y=64
            },
            { // Z=1
                    "                                                                 ", // Y=0
                    "                                                                 ", // Y=1
                    "                                                                 ", // Y=2
                    "                                                                 ", // Y=3
                    "                                                                 ", // Y=4
                    "                                                                 ", // Y=5
                    "                                                                 ", // Y=6
                    "                                                                 ", // Y=7
                    "                                                                 ", // Y=8
                    "                                                                 ", // Y=9
                    "                                                                 ", // Y=10
                    "                                                                 ", // Y=11
                    "                                                                 ", // Y=12
                    "                                                                 ", // Y=13
                    "                                                                 ", // Y=14
                    "                                                                 ", // Y=15
                    "                                                                 ", // Y=16
                    "                                                                 ", // Y=17
                    "                            BBBBBBBBB                            ", // Y=18
                    "                         BBBB       BBBB                         ", // Y=19
                    "                        BB             BB                        ", // Y=20
                    "                      BBB      FF       BBB                      ", // Y=21
                    "                     BB     FFFF          BB                     ", // Y=22
                    "                     B     FF              B                     ", // Y=23
                    "                    BB    FF      FF       BB                    ", // Y=24
                    "                   BB     F    FFFFFFFF     BB                   ", // Y=25
                    "                   B     FF   FF      FFF    B                   ", // Y=26
                    "                   B     F   FF         FF   B                   ", // Y=27
                    "                  BB     F  FF FFFFFF    FF  BB                  ", // Y=28
                    "                  B     FF  F FFFFF FF    F   B                  ", // Y=29
                    "                  B     FF  FFFPPPFF FF   F   B                  ", // Y=30
                    "                  B      F  FFPPOPPFF FF  FF  B                  ", // Y=31
                    "                  B  F   F  FFPOOOPFF  F   F  B                  ", // Y=32
                    "                  B  FF  FF FFPPOPPFF  F      B                  ", // Y=33
                    "                  B   F   FF FFPPPFFF  FF     B                  ", // Y=34
                    "                  B   F    FF FFFFF F  FF     B                  ", // Y=35
                    "                  BB  FF    FFFFFF FF  F     BB                  ", // Y=36
                    "                   B   FF         FF   F     B                   ", // Y=37
                    "                   B    FFF      FF   FF     B                   ", // Y=38
                    "                   BB     FFFFFFFF    F     BB                   ", // Y=39
                    "                    BB       FF      FF    BB                    ", // Y=40
                    "                     B              FF     B                     ", // Y=41
                    "                     BB          FFFF     BB                     ", // Y=42
                    "                      BBB       FF      BBB                      ", // Y=43
                    "                        BB             BB                        ", // Y=44
                    "                         BBBB       BBBB                         ", // Y=45
                    "                            BBBBBBBBB                            ", // Y=46
                    "                                                                 ", // Y=47
                    "                                                                 ", // Y=48
                    "                                                                 ", // Y=49
                    "                                                                 ", // Y=50
                    "                                                                 ", // Y=51
                    "                                                                 ", // Y=52
                    "                                                                 ", // Y=53
                    "                                                                 ", // Y=54
                    "                                                                 ", // Y=55
                    "                                                                 ", // Y=56
                    "                                                                 ", // Y=57
                    "                                                                 ", // Y=58
                    "                                                                 ", // Y=59
                    "                                                                 ", // Y=60
                    "                                                                 ", // Y=61
                    "                                                                 ", // Y=62
                    "                                                                 ", // Y=63
                    "                                                                 ", // Y=64
            },
            { // Z=2
                    "                                                                 ", // Y=0
                    "                                                                 ", // Y=1
                    "                                                                 ", // Y=2
                    "                                                                 ", // Y=3
                    "                                                                 ", // Y=4
                    "                                                                 ", // Y=5
                    "                                                                 ", // Y=6
                    "                            BBBBBBBBB                            ", // Y=7
                    "                        BBBBB       BBBBB                        ", // Y=8
                    "                     BBBB               BBBB                     ", // Y=9
                    "                    BB                     BB                    ", // Y=10
                    "                    B                       B                    ", // Y=11
                    "                                                                 ", // Y=12
                    "                                                                 ", // Y=13
                    "                                                                 ", // Y=14
                    "                                                                 ", // Y=15
                    "                                                                 ", // Y=16
                    "                                                                 ", // Y=17
                    "                                                                 ", // Y=18
                    "                                                                 ", // Y=19
                    "          BB                                         BB          ", // Y=20
                    "         BB                                           BB         ", // Y=21
                    "         B                                             B         ", // Y=22
                    "         B                                             B         ", // Y=23
                    "        BB                                             BB        ", // Y=24
                    "        B                                               B        ", // Y=25
                    "        B                                               B        ", // Y=26
                    "        B                                               B        ", // Y=27
                    "       BB                                               BB       ", // Y=28
                    "       B                                                 B       ", // Y=29
                    "       B                       LLL                       B       ", // Y=30
                    "       B                      LLLLL                      B       ", // Y=31
                    "       B                      LLRLL                      B       ", // Y=32
                    "       B                      LLLLL                      B       ", // Y=33
                    "       B                       LLL                       B       ", // Y=34
                    "       B                                                 B       ", // Y=35
                    "       BB                                               BB       ", // Y=36
                    "        B                                               B        ", // Y=37
                    "        B                                               B        ", // Y=38
                    "        B                                               B        ", // Y=39
                    "        BB                                             BB        ", // Y=40
                    "         B                                             B         ", // Y=41
                    "         B                                             B         ", // Y=42
                    "         BB                                           BB         ", // Y=43
                    "          BB                                         BB          ", // Y=44
                    "                                                                 ", // Y=45
                    "                                                                 ", // Y=46
                    "                                                                 ", // Y=47
                    "                                                                 ", // Y=48
                    "                                                                 ", // Y=49
                    "                                                                 ", // Y=50
                    "                                                                 ", // Y=51
                    "                                                                 ", // Y=52
                    "                    B                       B                    ", // Y=53
                    "                    BB                     BB                    ", // Y=54
                    "                     BBBB               BBBB                     ", // Y=55
                    "                        BBBBB       BBBBB                        ", // Y=56
                    "                            BBBBBBBBB                            ", // Y=57
                    "                                                                 ", // Y=58
                    "                                                                 ", // Y=59
                    "                                                                 ", // Y=60
                    "                                                                 ", // Y=61
                    "                                                                 ", // Y=62
                    "                                                                 ", // Y=63
                    "                                                                 ", // Y=64
            },
            { // Z=3
                    "                                                                 ", // Y=0
                    "                                                                 ", // Y=1
                    "                                                                 ", // Y=2
                    "                                                                 ", // Y=3
                    "                                                                 ", // Y=4
                    "                                                                 ", // Y=5
                    "                                                                 ", // Y=6
                    "                                                                 ", // Y=7
                    "                            FLLLLLLLF                            ", // Y=8
                    "                        FLLLLL     LLLLLF                        ", // Y=9
                    "                     FLLLL             LLLLF                     ", // Y=10
                    "                     LL                   LL                     ", // Y=11
                    "                                                                 ", // Y=12
                    "                                                                 ", // Y=13
                    "                                                                 ", // Y=14
                    "                                                                 ", // Y=15
                    "                                                                 ", // Y=16
                    "                                                                 ", // Y=17
                    "                                                                 ", // Y=18
                    "                                                                 ", // Y=19
                    "                                                                 ", // Y=20
                    "          FL                                         LF          ", // Y=21
                    "          LL                                         LL          ", // Y=22
                    "          L                                           L          ", // Y=23
                    "         FL                                           LF         ", // Y=24
                    "         LL                                           LL         ", // Y=25
                    "         L                                             L         ", // Y=26
                    "         L                                             L         ", // Y=27
                    "        FL                                             LF        ", // Y=28
                    "        LL                                             LL        ", // Y=29
                    "        L                                               L        ", // Y=30
                    "        L                      LLL                      L        ", // Y=31
                    "        L                      LSL                      L        ", // Y=32
                    "        L                      LLL                      L        ", // Y=33
                    "        L                                               L        ", // Y=34
                    "        LL                                             LL        ", // Y=35
                    "        FL                                             LF        ", // Y=36
                    "         L                                             L         ", // Y=37
                    "         L                                             L         ", // Y=38
                    "         LL                                           LL         ", // Y=39
                    "         FL                                           LF         ", // Y=40
                    "          L                                           L          ", // Y=41
                    "          LL                                         LL          ", // Y=42
                    "          FL                                         LF          ", // Y=43
                    "                                                                 ", // Y=44
                    "                                                                 ", // Y=45
                    "                                                                 ", // Y=46
                    "                                                                 ", // Y=47
                    "                                                                 ", // Y=48
                    "                                                                 ", // Y=49
                    "                                                                 ", // Y=50
                    "                                                                 ", // Y=51
                    "                                                                 ", // Y=52
                    "                     LL                   LL                     ", // Y=53
                    "                     FLLLL             LLLLF                     ", // Y=54
                    "                        FLLLLL     LLLLLF                        ", // Y=55
                    "                            FLLLLLLLF                            ", // Y=56
                    "                                                                 ", // Y=57
                    "                                                                 ", // Y=58
                    "                                                                 ", // Y=59
                    "                                                                 ", // Y=60
                    "                                                                 ", // Y=61
                    "                                                                 ", // Y=62
                    "                                                                 ", // Y=63
                    "                                                                 ", // Y=64
            },
            { // Z=4
                    "                                                                 ", // Y=0
                    "                                                                 ", // Y=1
                    "                                                                 ", // Y=2
                    "                                                                 ", // Y=3
                    "                                                                 ", // Y=4
                    "                                                                 ", // Y=5
                    "                                                                 ", // Y=6
                    "              B                                   B              ", // Y=7
                    "             BB                                   BB             ", // Y=8
                    "            BBD                                   DBB            ", // Y=9
                    "           BBD                                     DBB           ", // Y=10
                    "          BBD                                       DBB          ", // Y=11
                    "         BBD                                         DBB         ", // Y=12
                    "        BBD                                           DBB        ", // Y=13
                    "       BBD                                             DBB       ", // Y=14
                    "                                                                 ", // Y=15
                    "                                                                 ", // Y=16
                    "                                                                 ", // Y=17
                    "                                                                 ", // Y=18
                    "                                                                 ", // Y=19
                    "                                                                 ", // Y=20
                    "                                                                 ", // Y=21
                    "                                 OO                              ", // Y=22
                    "                               OOO                               ", // Y=23
                    "                             OOO                                 ", // Y=24
                    "                            OO                                   ", // Y=25
                    "                           OO     OO                             ", // Y=26
                    "                           O   OOOOOOOO                          ", // Y=27
                    "                           O  OO      OO                         ", // Y=28
                    "                          OO OO OOOO   O                         ", // Y=29
                    "                      O   OO O O   OO  OO                        ", // Y=30
                    "                      OO   O O LLL  OO  O                        ", // Y=31
                    "                       O   O O LSLO  O  OO                       ", // Y=32
                    "                       OO  OO OLLL O O   O                       ", // Y=33
                    "                        O   OO   O O OO                          ", // Y=34
                    "                        OO   OOOO OO OO                          ", // Y=35
                    "                         OO      OO  O                           ", // Y=36
                    "                          OOOOOOOO   O                           ", // Y=37
                    "                             OO     OO                           ", // Y=38
                    "                                   OO                            ", // Y=39
                    "                                 OOO                             ", // Y=40
                    "                               OOO                               ", // Y=41
                    "                              OO                                 ", // Y=42
                    "                                                                 ", // Y=43
                    "                                                                 ", // Y=44
                    "                                                                 ", // Y=45
                    "                                                                 ", // Y=46
                    "                                                                 ", // Y=47
                    "                                                                 ", // Y=48
                    "                                                                 ", // Y=49
                    "       BBD                                             DBB       ", // Y=50
                    "        BBD                                           DBB        ", // Y=51
                    "         BBD                                         DBB         ", // Y=52
                    "          BBD                                       DBB          ", // Y=53
                    "           BBD                                     DBB           ", // Y=54
                    "            BBD                                   DBB            ", // Y=55
                    "             BB                                   BB             ", // Y=56
                    "              B                                   B              ", // Y=57
                    "                                                                 ", // Y=58
                    "                                                                 ", // Y=59
                    "                                                                 ", // Y=60
                    "                                                                 ", // Y=61
                    "                                                                 ", // Y=62
                    "                                                                 ", // Y=63
                    "                                                                 ", // Y=64
            },
            { // Z=5
                    "                             C C C C                             ", // Y=0
                    "                           BBCCCCCCCBB                           ", // Y=1
                    "                       BBBBB C C C C BBBBB                       ", // Y=2
                    "                    BBBB                 BBBB                    ", // Y=3
                    "                  BBB                       BBB                  ", // Y=4
                    "                BBB                           BBB                ", // Y=5
                    "             H BB                               BB H             ", // Y=6
                    "          GGGIBB                                 BBIGGG          ", // Y=7
                    "        GG  IBHB                                 BHBI  GG        ", // Y=8
                    "        G  IBIID                                 DIIBI  G        ", // Y=9
                    "       G  IBII                                     IIBI  G       ", // Y=10
                    "       G IBIII                                     IIIBI G       ", // Y=11
                    "       GIBIII                                       IIIBIG       ", // Y=12
                    "      HIBIII                                         IIIBIH      ", // Y=13
                    "       BHI                                             IHB       ", // Y=14
                    "      BBBD                                             DBBB      ", // Y=15
                    "     BB                                                   BB     ", // Y=16
                    "     B                        MMMM                         B     ", // Y=17
                    "    BB                      MMM                            BB    ", // Y=18
                    "    B                     MMM  MMMMM                        B    ", // Y=19
                    "   BB                   MMMMMMMMMMMMMMM                     BB   ", // Y=20
                    "   B                   MMMMMMMMMMMMMMMMMM                    B   ", // Y=21
                    "   B                  MMMMMMMMMMMMMMMMMMMMM                  B   ", // Y=22
                    "  BB                  MMMMMMMMMMMMMMMMMMMMMM                 BB  ", // Y=23
                    "  B                  MMMMMMMMMMMMMMMMMMMMMMMM                 B  ", // Y=24
                    "  B                  MMMMMMMMMMMMMMMMMMMMMMMM                 B  ", // Y=25
                    "  B                 MMMMMMMMMMMMMMMMMMMMMMMMMM                B  ", // Y=26
                    " BB                 MMMMMMMMMMMMMMMMMMMMMMMMMM                BB ", // Y=27
                    " B                  MMMMMMMMMMMMMMMMMMMMMMMMMMM                B ", // Y=28
                    "CCC                MMMMMMMMMMMMMMMMMMMMMMMMMM M               CCC", // Y=29
                    " C                 MMMMMMMMMMMMMMMMMMMMMMMMMM MM               C ", // Y=30
                    "CCC              M MMMMMMMMMMMMMMMMMMMMMMMMMMM M              CCC", // Y=31
                    " C               M MMMMMMMMMMMMMSMMMMMMMMMMMMM M               C ", // Y=32
                    "CCC              M MMMMMMMMMMMMMMMMMMMMMMMMMMM M              CCC", // Y=33
                    " C               MM MMMMMMMMMMMMMMMMMMMMMMMMMM                 C ", // Y=34
                    "CCC               M MMMMMMMMMMMMMMMMMMMMMMMMMM                CCC", // Y=35
                    " B                MMMMMMMMMMMMMMMMMMMMMMMMMMM                  B ", // Y=36
                    " BB                MMMMMMMMMMMMMMMMMMMMMMMMMM                 BB ", // Y=37
                    "  B                MMMMMMMMMMMMMMMMMMMMMMMMMM                 B  ", // Y=38
                    "  B                 MMMMMMMMMMMMMMMMMMMMMMMM                  B  ", // Y=39
                    "  B                 MMMMMMMMMMMMMMMMMMMMMMMM                  B  ", // Y=40
                    "  BB                 MMMMMMMMMMMMMMMMMMMMMM                  BB  ", // Y=41
                    "   B                  MMMMMMMMMMMMMMMMMMMMM                  B   ", // Y=42
                    "   B                    MMMMMMMMMMMMMMMMMM                   B   ", // Y=43
                    "   BB                     MMMMMMMMMMMMMMM                   BB   ", // Y=44
                    "    B                        MMMMM  MMM                     B    ", // Y=45
                    "    BB                            MMM                      BB    ", // Y=46
                    "     B                         MMMM                        B     ", // Y=47
                    "     BB                                                   BB     ", // Y=48
                    "      BBBD                                             DBBB      ", // Y=49
                    "       BHI                                             IHB       ", // Y=50
                    "      HIBIII                                         IIIBIH      ", // Y=51
                    "       GIBIII                                       IIIBIG       ", // Y=52
                    "       G IBIII                                     IIIBI G       ", // Y=53
                    "       G  IBII                                     IIBI  G       ", // Y=54
                    "        G  IBIID                                 DIIBI  G        ", // Y=55
                    "        GG  IBHB                                 BHBI  GG        ", // Y=56
                    "          GGGIBB                                 BBIGGG          ", // Y=57
                    "             H BB                               BB H             ", // Y=58
                    "                BBB                           BBB                ", // Y=59
                    "                  BBB                       BBB                  ", // Y=60
                    "                    BBBB                 BBBB                    ", // Y=61
                    "                       BBBBB C C C C BBBBB                       ", // Y=62
                    "                           BBCCCCCCCBB                           ", // Y=63
                    "                             C C C C                             ", // Y=64
            },
            { // Z=6
                    "                           BBCCCCCCCBB                           ", // Y=0
                    "                       BBBBBBBBBBBBBBBBBBB                       ", // Y=1
                    "                    BBBBBBBBBCCCCCCCBBBBBBBBB                    ", // Y=2
                    "                  BBBBBBBBBB         BBBBBBBBBB                  ", // Y=3
                    "                BBBBBBBB                 BBBBBBBB                ", // Y=4
                    "             H BBBBBB                       BBBBBB H             ", // Y=5
                    "      I   GGHBBBBBB                           BBBBBBHGG   I      ", // Y=6
                    "       IGGJIIBBBBB                             BBBBBIIJGGI       ", // Y=7
                    "       GJJIIBBBBB                               BBBBBIIJJG       ", // Y=8
                    "       GJIIBBKIID                               DIIKBBIIJG       ", // Y=9
                    "      GJIIBBKJI                                   IJKBBIIJG      ", // Y=10
                    "      GIIBBKJI                                     IJKBBIIG      ", // Y=11
                    "      HIBBKJLL                                     LLJKBBIH      ", // Y=12
                    "     HBBBKJILL                 MMMMM               LLIJKBBBH     ", // Y=13
                    "      BBBII   L            MMMMM                  L   IIBBB      ", // Y=14
                    "     BBBBI     L         MMM                     L     IBBBB     ", // Y=15
                    "    BBBBBD             MMM  MMMMMMMMMMMM               DBBBBB    ", // Y=16
                    "    BBBB              MM  MMMMMMMMMM   MMM               BBBB    ", // Y=17
                    "   BBBB              MM MMMMMMMMMMMMMMM  MMM              BBBB   ", // Y=18
                    "   BBB              MM MMMMMMMMMMMMMMMMMM  MM              BBB   ", // Y=19
                    "  BBBB             MM MMMMMMMMMMMMMMMMMMMMM MM             BBBB  ", // Y=20
                    "  BBB             MM MMMMMMMMMMMMMMMMMMMMMMM MM             BBB  ", // Y=21
                    "  BBB             M MMMMMMMMMMMMMMMMMMMMMMMMM MM            BBB  ", // Y=22
                    " BBBB            MM MMMMMMMMMMMMMMMMMMMMMMMMMM MM           BBBB ", // Y=23
                    " BBB             M MMMMMMMMMMMMMMMMMMMMMMMMMMMM M            BBB ", // Y=24
                    " BBB            MM MMMMMMMMMMMMMMMMMMMMMMMMMMMM MM           BBB ", // Y=25
                    " BBB            M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M           BBB ", // Y=26
                    "BBBB            M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMM MM          BBBB", // Y=27
                    "BBB             M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M           BBB", // Y=28
                    "CBC          M  MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M           CBC", // Y=29
                    "CBC          M  MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M           CBC", // Y=30
                    "CBC          M  MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM MM          CBC", // Y=31
                    "CBC          M  MMMMMMMMMMMMMMMMSMMMMMMMMMMMMMMMM  M          CBC", // Y=32
                    "CBC          MM MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM  M          CBC", // Y=33
                    "CBC           M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM  M          CBC", // Y=34
                    "CBC           M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM  M          CBC", // Y=35
                    "BBB           M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M  M          BBB", // Y=36
                    "BBBB          MM MMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M            BBBB", // Y=37
                    " BBB           M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M            BBB ", // Y=38
                    " BBB           MM MMMMMMMMMMMMMMMMMMMMMMMMMMMM MM            BBB ", // Y=39
                    " BBB            M MMMMMMMMMMMMMMMMMMMMMMMMMMMM M             BBB ", // Y=40
                    " BBBB           MM MMMMMMMMMMMMMMMMMMMMMMMMMM MM            BBBB ", // Y=41
                    "  BBB            MM MMMMMMMMMMMMMMMMMMMMMMMMM M             BBB  ", // Y=42
                    "  BBB             MM MMMMMMMMMMMMMMMMMMMMMMM MM             BBB  ", // Y=43
                    "  BBBB             MM MMMMMMMMMMMMMMMMMMMMM MM             BBBB  ", // Y=44
                    "   BBB              MM  MMMMMMMMMMMMMMMMMM MM              BBB   ", // Y=45
                    "   BBBB              MMM  MMMMMMMMMMMMMMM MM              BBBB   ", // Y=46
                    "    BBBB               MMM   MMMMMMMMMM  MM              BBBB    ", // Y=47
                    "    BBBBBD               MMMMMMMMMMMM  MMM             DBBBBB    ", // Y=48
                    "     BBBBI     L                     MMM         L     IBBBB     ", // Y=49
                    "      BBBII   L                  MMMMM            L   IIBBB      ", // Y=50
                    "     HBBBKJILL               MMMMM                 LLIJKBBBH     ", // Y=51
                    "      HIBBKJLL                                     LLJKBBIH      ", // Y=52
                    "      GIIBBKJI                                     IJKBBIIG      ", // Y=53
                    "      GJIIBBKJI                                   IJKBBIIJG      ", // Y=54
                    "       GJIIBBKIID                               DIIKBBIIJG       ", // Y=55
                    "       GJJIIBBBBB                               BBBBBIIJJG       ", // Y=56
                    "       IGGJIIBBBBB                             BBBBBIIJGGI       ", // Y=57
                    "      I   GGHBBBBBB                           BBBBBBHGG   I      ", // Y=58
                    "             H BBBBBB                       BBBBBB H             ", // Y=59
                    "                BBBBBBBB                 BBBBBBBB                ", // Y=60
                    "                  BBBBBBBBBB         BBBBBBBBBB                  ", // Y=61
                    "                    BBBBBBBBBCCCCCCCBBBBBBBBB                    ", // Y=62
                    "                       BBBBBBBBBBBBBBBBBBB                       ", // Y=63
                    "                           BBCCCCCCCBB                           ", // Y=64
            },
            { // Z=7
                    "                          AAAAAAAAAAAAA                          ", // Y=0
                    "                      AAAAADDDDBBBDDDDAAAAA                      ", // Y=1
                    "                   AAAADDDDJEFFFFFFFEEDDDDAAAA                   ", // Y=2
                    "                 AAADDDEEFFFF       FFFFEEDDDAAA                 ", // Y=3
                    "    A   A      AAADDEEFFF               FFFEEDDAAA      A   A    ", // Y=4
                    "     AA  AAGGGAADDEEFF                     FFEEDDAAGGGAA  AA     ", // Y=5
                    "     AAA AAIHAADEEFF                         FFEEDAAHIAA AAA     ", // Y=6
                    "      AAAAKAADDEEFB                           BFEEDDAAKAAAA      ", // Y=7
                    "    A  AKIAADEEFHBB                           BBHFEEDAAIKA  A    ", // Y=8
                    "     AAAIAADEEKI B                             B IKEEDAAIAAA     ", // Y=9
                    "     AAKAADEEJJI                                 IJJEEDAAKAA     ", // Y=10
                    "     GIAADEEJLI               MMM                 ILJEEDAAIG     ", // Y=11
                    "     GHADEEJLLL           MMMMM                   LLLJEEDAHG     ", // Y=12
                    "     GADEEJLLLL         MMM                       LLLLJEEDAG     ", // Y=13
                    "     AADEKJILLLL      MMM   MMMMMMMMMMMM         LLLLIJKEDAA     ", // Y=14
                    "    AADEFII   LLL    MM   MMMMMMMMMMM  MMMM     LLL   IIFEDAA    ", // Y=15
                    "    ADEEH      LL   MM  MMMMMMMMMMMMMMMM  MMM   LL      HEEDA    ", // Y=16
                    "   AADEFBB       L MM  MMMMMMMMMMMMMMMMMMM  MM L       BBFEDAA   ", // Y=17
                    "   ADEFBB         MM MMMMMMMMMMMMMMMMMMMMMM  MM         BBFEDA   ", // Y=18
                    "  AADEF          MM MMMMMMMMMMMMMMMMMMMMMMMM  MM          FEDAA  ", // Y=19
                    "  ADEF          MM  MMMMMMMMMMMMMMMMMMMMMMMMMM MM          FEDA  ", // Y=20
                    "  ADEF          M  MMMMMMMMMMMMMMMMMMMMMMMMMMMM MM         FEDA  ", // Y=21
                    " AADF          MM MMMMMMMMMMMMMMMMMMMMMMMMMMMMM  MM         FDAA ", // Y=22
                    " ADEF          M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM  M         FEDA ", // Y=23
                    " ADEF          M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM MM        FEDA ", // Y=24
                    " ADF          MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM  M         FDA ", // Y=25
                    "AADF          M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM MM        FDAA", // Y=26
                    "ADEF          M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM  M        FEDA", // Y=27
                    "ADEF          MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M        FEDA", // Y=28
                    "ADF           MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M         FDA", // Y=29
                    "ADF           MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM MM        FDA", // Y=30
                    "ADF           MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM  M        FDA", // Y=31
                    "ADF        M  MMMMMMMMMMMMMMMMMMSMMMMMMMMMMMMMMMMMM  M        FDA", // Y=32
                    "ADF        M  MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM           FDA", // Y=33
                    "ADF        MM MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM           FDA", // Y=34
                    "ADF         M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM           FDA", // Y=35
                    "ADEF        M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM          FEDA", // Y=36
                    "ADEF        M  MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M          FEDA", // Y=37
                    "AADF        MM MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M          FDAA", // Y=38
                    " ADF         M  MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM          FDA ", // Y=39
                    " ADEF        MM MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M          FEDA ", // Y=40
                    " ADEF         M  MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M          FEDA ", // Y=41
                    " AADF         MM  MMMMMMMMMMMMMMMMMMMMMMMMMMMMM MM          FDAA ", // Y=42
                    "  ADEF         MM MMMMMMMMMMMMMMMMMMMMMMMMMMMM  M          FEDA  ", // Y=43
                    "  ADEF          MM MMMMMMMMMMMMMMMMMMMMMMMMMM  MM          FEDA  ", // Y=44
                    "  AADEF          MM  MMMMMMMMMMMMMMMMMMMMMMMM MM          FEDAA  ", // Y=45
                    "   ADEFBB         MM  MMMMMMMMMMMMMMMMMMMMMM MM         BBFEDA   ", // Y=46
                    "   AADEFBB       L MM  MMMMMMMMMMMMMMMMMMM  MM L       BBFEDAA   ", // Y=47
                    "    ADEEH      LL   MMM  MMMMMMMMMMMMMMMM  MM   LL      HEEDA    ", // Y=48
                    "    AADEFII   LLL     MMMM  MMMMMMMMMMM   MM    LLL   IIFEDAA    ", // Y=49
                    "     AADEKJILLLL         MMMMMMMMMMMM   MMM      LLLLIJKEDAA     ", // Y=50
                    "     GADEEJLLLL                       MMM         LLLLJEEDAG     ", // Y=51
                    "     GHADEEJLLL                   MMMMM           LLLJEEDAHG     ", // Y=52
                    "     GIAADEEJLI                 MMM               ILJEEDAAIG     ", // Y=53
                    "     AAKAADEEJJI                                 IJJEEDAAKAA     ", // Y=54
                    "     AAAIAADEEKI B                             B IKEEDAAIAAA     ", // Y=55
                    "    A  AKIAADEEFHBB                           BBHFEEDAAIKA  A    ", // Y=56
                    "      AAAAKAADDEEFB                           BFEEDDAAKAAAA      ", // Y=57
                    "     AAA AAIHAADEEFF                         FFEEDAAHIAA AAA     ", // Y=58
                    "     AA  AAGGGAADDEEFF                     FFEEDDAAGGGAA  AA     ", // Y=59
                    "    A   A      AAADDEEFFF               FFFEEDDAAA      A   A    ", // Y=60
                    "                 AAADDDEEFFFF       FFFFEEDDDAAA                 ", // Y=61
                    "                   AAAADDDDEEFFFFFFFEEDDDDAAAA                   ", // Y=62
                    "                      AAAAADDDDBBBDDDDAAAAA                      ", // Y=63
                    "                          AAAAAAAAAAAAA                          ", // Y=64
            },
            { // Z=8
                    "                           BBCCCCCCCBB                           ", // Y=0
                    "                       BBBBBBBBBBBBBBBBBBB                       ", // Y=1
                    "                    BBBBBBBBBCCCCCCCBBBBBBBBB                    ", // Y=2
                    "                  BBBBBBBBBB         BBBBBBBBBB                  ", // Y=3
                    "                BBBBBBBB                 BBBBBBBB                ", // Y=4
                    "             H BBBBBB                       BBBBBB H             ", // Y=5
                    "      I   GGHBBBBBB                           BBBBBBHGG   I      ", // Y=6
                    "       IGGJIIBBBBB                             BBBBBIIJGGI       ", // Y=7
                    "       GJJIIBBBBB                               BBBBBIIJJG       ", // Y=8
                    "       GJIIBBKIID                               DIIKBBIIJG       ", // Y=9
                    "      GJIIBBKJI                                   IJKBBIIJG      ", // Y=10
                    "      GIIBBKJI                                     IJKBBIIG      ", // Y=11
                    "      HIBBKJLL                                     LLJKBBIH      ", // Y=12
                    "     HBBBKJILL                 MMMMM               LLIJKBBBH     ", // Y=13
                    "      BBBII   L            MMMMM                  L   IIBBB      ", // Y=14
                    "     BBBBI     L         MMM                     L     IBBBB     ", // Y=15
                    "    BBBBBD             MMM  MMMMMMMMMMMM               DBBBBB    ", // Y=16
                    "    BBBB              MM  MMMMMMMMMM   MMM               BBBB    ", // Y=17
                    "   BBBB              MM MMMMMMMMMMMMMMM  MMM              BBBB   ", // Y=18
                    "   BBB              MM MMMMMMMMMMMMMMMMMM  MM              BBB   ", // Y=19
                    "  BBBB             MM MMMMMMMMMMMMMMMMMMMMM MM             BBBB  ", // Y=20
                    "  BBB             MM MMMMMMMMMMMMMMMMMMMMMMM MM             BBB  ", // Y=21
                    "  BBB             M MMMMMMMMMMMMMMMMMMMMMMMMM MM            BBB  ", // Y=22
                    " BBBB            MM MMMMMMMMMMMMMMMMMMMMMMMMMM MM           BBBB ", // Y=23
                    " BBB             M MMMMMMMMMMMMMMMMMMMMMMMMMMMM M            BBB ", // Y=24
                    " BBB            MM MMMMMMMMMMMMMMMMMMMMMMMMMMMM MM           BBB ", // Y=25
                    " BBB            M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M           BBB ", // Y=26
                    "BBBB            M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMM MM          BBBB", // Y=27
                    "BBB             M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M           BBB", // Y=28
                    "CBC          M  MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M           CBC", // Y=29
                    "CBC          M  MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M           CBC", // Y=30
                    "CBC          M  MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM MM          CBC", // Y=31
                    "CBC          M  MMMMMMMMMMMMMMMMSMMMMMMMMMMMMMMMM  M          CBC", // Y=32
                    "CBC          MM MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM  M          CBC", // Y=33
                    "CBC           M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM  M          CBC", // Y=34
                    "CBC           M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM  M          CBC", // Y=35
                    "BBB           M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M  M          BBB", // Y=36
                    "BBBB          MM MMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M            BBBB", // Y=37
                    " BBB           M MMMMMMMMMMMMMMMMMMMMMMMMMMMMMM M            BBB ", // Y=38
                    " BBB           MM MMMMMMMMMMMMMMMMMMMMMMMMMMMM MM            BBB ", // Y=39
                    " BBB            M MMMMMMMMMMMMMMMMMMMMMMMMMMMM M             BBB ", // Y=40
                    " BBBB           MM MMMMMMMMMMMMMMMMMMMMMMMMMM MM            BBBB ", // Y=41
                    "  BBB            MM MMMMMMMMMMMMMMMMMMMMMMMMM M             BBB  ", // Y=42
                    "  BBB             MM MMMMMMMMMMMMMMMMMMMMMMM MM             BBB  ", // Y=43
                    "  BBBB             MM MMMMMMMMMMMMMMMMMMMMM MM             BBBB  ", // Y=44
                    "   BBB              MM  MMMMMMMMMMMMMMMMMM MM              BBB   ", // Y=45
                    "   BBBB              MMM  MMMMMMMMMMMMMMM MM              BBBB   ", // Y=46
                    "    BBBB               MMM   MMMMMMMMMM  MM              BBBB    ", // Y=47
                    "    BBBBBD               MMMMMMMMMMMM  MMM             DBBBBB    ", // Y=48
                    "     BBBBI     L                     MMM         L     IBBBB     ", // Y=49
                    "      BBBII   L                  MMMMM            L   IIBBB      ", // Y=50
                    "     HBBBKJILL               MMMMM                 LLIJKBBBH     ", // Y=51
                    "      HIBBKJLL                                     LLJKBBIH      ", // Y=52
                    "      GIIBBKJI                                     IJKBBIIG      ", // Y=53
                    "      GJIIBBKJI                                   IJKBBIIJG      ", // Y=54
                    "       GJIIBBKIID                               DIIKBBIIJG       ", // Y=55
                    "       GJJIIBBBBB                               BBBBBIIJJG       ", // Y=56
                    "       IGGJIIBBBBB                             BBBBBIIJGGI       ", // Y=57
                    "      I   GGHBBBBBB                           BBBBBBHGG   I      ", // Y=58
                    "             H BBBBBB                       BBBBBB H             ", // Y=59
                    "                BBBBBBBB                 BBBBBBBB                ", // Y=60
                    "                  BBBBBBBBBB         BBBBBBBBBB                  ", // Y=61
                    "                    BBBBBBBBBCCCCCCCBBBBBBBBB                    ", // Y=62
                    "                       BBBBBBBBBBBBBBBBBBB                       ", // Y=63
                    "                           BBCCCCCCCBB                           ", // Y=64
            },
            { // Z=9
                    "                             C C C C                             ", // Y=0
                    "                           BBCCCQCCCBB                           ", // Y=1
                    "                       BBBBB C C C C BBBBB                       ", // Y=2
                    "                    BBBB                 BBBB                    ", // Y=3
                    "                  BBB                       BBB                  ", // Y=4
                    "                BBB                           BBB                ", // Y=5
                    "             H BB                               BB H             ", // Y=6
                    "          GGGIBB                                 BBIGGG          ", // Y=7
                    "        GG  IBHB                                 BHBI  GG        ", // Y=8
                    "        G  IBIID                                 DIIBI  G        ", // Y=9
                    "       G  IBII                                     IIBI  G       ", // Y=10
                    "       G IBIII                                     IIIBI G       ", // Y=11
                    "       GIBIII                                       IIIBIG       ", // Y=12
                    "      HIBIII                                         IIIBIH      ", // Y=13
                    "       BHI                                             IHB       ", // Y=14
                    "      BBBD                                             DBBB      ", // Y=15
                    "     BB                                                   BB     ", // Y=16
                    "     B                        MMMM                         B     ", // Y=17
                    "    BB                      MMM                            BB    ", // Y=18
                    "    B                     MMM  MMMMM                        B    ", // Y=19
                    "   BB                   MMMMMMMMMMMMMMM                     BB   ", // Y=20
                    "   B                   MMMMMMMMMMMMMMMMMM                    B   ", // Y=21
                    "   B                  MMMMMMMMMMMMMMMMMMMMM                  B   ", // Y=22
                    "  BB                  MMMMMMMMMMMMMMMMMMMMMM                 BB  ", // Y=23
                    "  B                  MMMMMMMMMMMMMMMMMMMMMMMM                 B  ", // Y=24
                    "  B                  MMMMMMMMMMMMMMMMMMMMMMMM                 B  ", // Y=25
                    "  B                 MMMMMMMMMMMMMMMMMMMMMMMMMM                B  ", // Y=26
                    " BB                 MMMMMMMMMMMMMMMMMMMMMMMMMM                BB ", // Y=27
                    " B                  MMMMMMMMMMMMMMMMMMMMMMMMMMM                B ", // Y=28
                    "CCC                MMMMMMMMMMMMMMMMMMMMMMMMMM M               CCC", // Y=29
                    " C                 MMMMMMMMMMMMMMMMMMMMMMMMMM MM               C ", // Y=30
                    "CCC              M MMMMMMMMMMMMMMMMMMMMMMMMMMM M              CCC", // Y=31
                    " C               M MMMMMMMMMMMMMSMMMMMMMMMMMMM M               C ", // Y=32
                    "CCC              M MMMMMMMMMMMMMMMMMMMMMMMMMMM M              CCC", // Y=33
                    " C               MM MMMMMMMMMMMMMMMMMMMMMMMMMM                 C ", // Y=34
                    "CCC               M MMMMMMMMMMMMMMMMMMMMMMMMMM                CCC", // Y=35
                    " B                MMMMMMMMMMMMMMMMMMMMMMMMMMM                  B ", // Y=36
                    " BB                MMMMMMMMMMMMMMMMMMMMMMMMMM                 BB ", // Y=37
                    "  B                MMMMMMMMMMMMMMMMMMMMMMMMMM                 B  ", // Y=38
                    "  B                 MMMMMMMMMMMMMMMMMMMMMMMM                  B  ", // Y=39
                    "  B                 MMMMMMMMMMMMMMMMMMMMMMMM                  B  ", // Y=40
                    "  BB                 MMMMMMMMMMMMMMMMMMMMMM                  BB  ", // Y=41
                    "   B                  MMMMMMMMMMMMMMMMMMMMM                  B   ", // Y=42
                    "   B                    MMMMMMMMMMMMMMMMMM                   B   ", // Y=43
                    "   BB                     MMMMMMMMMMMMMMM                   BB   ", // Y=44
                    "    B                        MMMMM  MMM                     B    ", // Y=45
                    "    BB                            MMM                      BB    ", // Y=46
                    "     B                         MMMM                        B     ", // Y=47
                    "     BB                                                   BB     ", // Y=48
                    "      BBBD                                             DBBB      ", // Y=49
                    "       BHI                                             IHB       ", // Y=50
                    "      HIBIII                                         IIIBIH      ", // Y=51
                    "       GIBIII                                       IIIBIG       ", // Y=52
                    "       G IBIII                                     IIIBI G       ", // Y=53
                    "       G  IBII                                     IIBI  G       ", // Y=54
                    "        G  IBIID                                 DIIBI  G        ", // Y=55
                    "        GG  IBHB                                 BHBI  GG        ", // Y=56
                    "          GGGIBB                                 BBIGGG          ", // Y=57
                    "             H BB                               BB H             ", // Y=58
                    "                BBB                           BBB                ", // Y=59
                    "                  BBB                       BBB                  ", // Y=60
                    "                    BBBB                 BBBB                    ", // Y=61
                    "                       BBBBB C C C C BBBBB                       ", // Y=62
                    "                           BBCCCCCCCBB                           ", // Y=63
                    "                             C C C C                             ", // Y=64
            },
            { // Z=10
                    "                                                                 ", // Y=0
                    "                                                                 ", // Y=1
                    "                                                                 ", // Y=2
                    "                                                                 ", // Y=3
                    "                                                                 ", // Y=4
                    "                                                                 ", // Y=5
                    "                                                                 ", // Y=6
                    "              B                                   B              ", // Y=7
                    "             BB                                   BB             ", // Y=8
                    "            BBD                                   DBB            ", // Y=9
                    "           BBD                                     DBB           ", // Y=10
                    "          BBD                                       DBB          ", // Y=11
                    "         BBD                                         DBB         ", // Y=12
                    "        BBD                                           DBB        ", // Y=13
                    "       BBD                                             DBB       ", // Y=14
                    "                                                                 ", // Y=15
                    "                                                                 ", // Y=16
                    "                                                                 ", // Y=17
                    "                                                                 ", // Y=18
                    "                                                                 ", // Y=19
                    "                                                                 ", // Y=20
                    "                                                                 ", // Y=21
                    "                                 OO                              ", // Y=22
                    "                               OOO                               ", // Y=23
                    "                             OOO                                 ", // Y=24
                    "                            OO                                   ", // Y=25
                    "                           OO     OO                             ", // Y=26
                    "                           O   OOOOOOOO                          ", // Y=27
                    "                           O  OO      OO                         ", // Y=28
                    "                          OO OO OOOO   O                         ", // Y=29
                    "                      O   OO O O   OO  OO                        ", // Y=30
                    "                      OO   O O LLL  OO  O                        ", // Y=31
                    "                       O   O O LSLO  O  OO                       ", // Y=32
                    "                       OO  OO OLLL O O   O                       ", // Y=33
                    "                        O   OO   O O OO                          ", // Y=34
                    "                        OO   OOOO OO OO                          ", // Y=35
                    "                         OO      OO  O                           ", // Y=36
                    "                          OOOOOOOO   O                           ", // Y=37
                    "                             OO     OO                           ", // Y=38
                    "                                   OO                            ", // Y=39
                    "                                 OOO                             ", // Y=40
                    "                               OOO                               ", // Y=41
                    "                              OO                                 ", // Y=42
                    "                                                                 ", // Y=43
                    "                                                                 ", // Y=44
                    "                                                                 ", // Y=45
                    "                                                                 ", // Y=46
                    "                                                                 ", // Y=47
                    "                                                                 ", // Y=48
                    "                                                                 ", // Y=49
                    "       BBD                                             DBB       ", // Y=50
                    "        BBD                                           DBB        ", // Y=51
                    "         BBD                                         DBB         ", // Y=52
                    "          BBD                                       DBB          ", // Y=53
                    "           BBD                                     DBB           ", // Y=54
                    "            BBD                                   DBB            ", // Y=55
                    "             BB                                   BB             ", // Y=56
                    "              B                                   B              ", // Y=57
                    "                                                                 ", // Y=58
                    "                                                                 ", // Y=59
                    "                                                                 ", // Y=60
                    "                                                                 ", // Y=61
                    "                                                                 ", // Y=62
                    "                                                                 ", // Y=63
                    "                                                                 ", // Y=64
            },
            { // Z=11
                    "                                                                 ", // Y=0
                    "                                                                 ", // Y=1
                    "                                                                 ", // Y=2
                    "                                                                 ", // Y=3
                    "                                                                 ", // Y=4
                    "                                                                 ", // Y=5
                    "                                                                 ", // Y=6
                    "                                                                 ", // Y=7
                    "                            FLLLLLLLF                            ", // Y=8
                    "                        FLLLLL     LLLLLF                        ", // Y=9
                    "                     FLLLL             LLLLF                     ", // Y=10
                    "                     LL                   LL                     ", // Y=11
                    "                                                                 ", // Y=12
                    "                                                                 ", // Y=13
                    "                                                                 ", // Y=14
                    "                                                                 ", // Y=15
                    "                                                                 ", // Y=16
                    "                                                                 ", // Y=17
                    "                                                                 ", // Y=18
                    "                                                                 ", // Y=19
                    "                                                                 ", // Y=20
                    "          FL                                         LF          ", // Y=21
                    "          LL                                         LL          ", // Y=22
                    "          L                                           L          ", // Y=23
                    "         FL                                           LF         ", // Y=24
                    "         LL                                           LL         ", // Y=25
                    "         L                                             L         ", // Y=26
                    "         L                                             L         ", // Y=27
                    "        FL                                             LF        ", // Y=28
                    "        LL                                             LL        ", // Y=29
                    "        L                                               L        ", // Y=30
                    "        L                      LLL                      L        ", // Y=31
                    "        L                      LSL                      L        ", // Y=32
                    "        L                      LLL                      L        ", // Y=33
                    "        L                                               L        ", // Y=34
                    "        LL                                             LL        ", // Y=35
                    "        FL                                             LF        ", // Y=36
                    "         L                                             L         ", // Y=37
                    "         L                                             L         ", // Y=38
                    "         LL                                           LL         ", // Y=39
                    "         FL                                           LF         ", // Y=40
                    "          L                                           L          ", // Y=41
                    "          LL                                         LL          ", // Y=42
                    "          FL                                         LF          ", // Y=43
                    "                                                                 ", // Y=44
                    "                                                                 ", // Y=45
                    "                                                                 ", // Y=46
                    "                                                                 ", // Y=47
                    "                                                                 ", // Y=48
                    "                                                                 ", // Y=49
                    "                                                                 ", // Y=50
                    "                                                                 ", // Y=51
                    "                                                                 ", // Y=52
                    "                     LL                   LL                     ", // Y=53
                    "                     FLLLL             LLLLF                     ", // Y=54
                    "                        FLLLLL     LLLLLF                        ", // Y=55
                    "                            FLLLLLLLF                            ", // Y=56
                    "                                                                 ", // Y=57
                    "                                                                 ", // Y=58
                    "                                                                 ", // Y=59
                    "                                                                 ", // Y=60
                    "                                                                 ", // Y=61
                    "                                                                 ", // Y=62
                    "                                                                 ", // Y=63
                    "                                                                 ", // Y=64
            },
            { // Z=12
                    "                                                                 ", // Y=0
                    "                                                                 ", // Y=1
                    "                                                                 ", // Y=2
                    "                                                                 ", // Y=3
                    "                                                                 ", // Y=4
                    "                                                                 ", // Y=5
                    "                                                                 ", // Y=6
                    "                            BBBBBBBBB                            ", // Y=7
                    "                        BBBBB       BBBBB                        ", // Y=8
                    "                     BBBB               BBBB                     ", // Y=9
                    "                    BB                     BB                    ", // Y=10
                    "                    B                       B                    ", // Y=11
                    "                                                                 ", // Y=12
                    "                                                                 ", // Y=13
                    "                                                                 ", // Y=14
                    "                                                                 ", // Y=15
                    "                                                                 ", // Y=16
                    "                                                                 ", // Y=17
                    "                                                                 ", // Y=18
                    "                                                                 ", // Y=19
                    "          BB                                         BB          ", // Y=20
                    "         BB                                           BB         ", // Y=21
                    "         B                                             B         ", // Y=22
                    "         B                                             B         ", // Y=23
                    "        BB                                             BB        ", // Y=24
                    "        B                                               B        ", // Y=25
                    "        B                                               B        ", // Y=26
                    "        B                                               B        ", // Y=27
                    "       BB                                               BB       ", // Y=28
                    "       B                                                 B       ", // Y=29
                    "       B                       LLL                       B       ", // Y=30
                    "       B                      LLLLL                      B       ", // Y=31
                    "       B                      LLRLL                      B       ", // Y=32
                    "       B                      LLLLL                      B       ", // Y=33
                    "       B                       LLL                       B       ", // Y=34
                    "       B                                                 B       ", // Y=35
                    "       BB                                               BB       ", // Y=36
                    "        B                                               B        ", // Y=37
                    "        B                                               B        ", // Y=38
                    "        B                                               B        ", // Y=39
                    "        BB                                             BB        ", // Y=40
                    "         B                                             B         ", // Y=41
                    "         B                                             B         ", // Y=42
                    "         BB                                           BB         ", // Y=43
                    "          BB                                         BB          ", // Y=44
                    "                                                                 ", // Y=45
                    "                                                                 ", // Y=46
                    "                                                                 ", // Y=47
                    "                                                                 ", // Y=48
                    "                                                                 ", // Y=49
                    "                                                                 ", // Y=50
                    "                                                                 ", // Y=51
                    "                                                                 ", // Y=52
                    "                    B                       B                    ", // Y=53
                    "                    BB                     BB                    ", // Y=54
                    "                     BBBB               BBBB                     ", // Y=55
                    "                        BBBBB       BBBBB                        ", // Y=56
                    "                            BBBBBBBBB                            ", // Y=57
                    "                                                                 ", // Y=58
                    "                                                                 ", // Y=59
                    "                                                                 ", // Y=60
                    "                                                                 ", // Y=61
                    "                                                                 ", // Y=62
                    "                                                                 ", // Y=63
                    "                                                                 ", // Y=64
            },
            { // Z=13
                    "                                                                 ", // Y=0
                    "                                                                 ", // Y=1
                    "                                                                 ", // Y=2
                    "                                                                 ", // Y=3
                    "                                                                 ", // Y=4
                    "                                                                 ", // Y=5
                    "                                                                 ", // Y=6
                    "                                                                 ", // Y=7
                    "                                                                 ", // Y=8
                    "                                                                 ", // Y=9
                    "                                                                 ", // Y=10
                    "                                                                 ", // Y=11
                    "                                                                 ", // Y=12
                    "                                                                 ", // Y=13
                    "                                                                 ", // Y=14
                    "                                                                 ", // Y=15
                    "                                                                 ", // Y=16
                    "                                                                 ", // Y=17
                    "                            BBBBBBBBB                            ", // Y=18
                    "                         BBBB       BBBB                         ", // Y=19
                    "                        BB             BB                        ", // Y=20
                    "                      BBB      FF       BBB                      ", // Y=21
                    "                     BB     FFFF          BB                     ", // Y=22
                    "                     B     FF              B                     ", // Y=23
                    "                    BB    FF      FF       BB                    ", // Y=24
                    "                   BB     F    FFFFFFFF     BB                   ", // Y=25
                    "                   B     FF   FF      FFF    B                   ", // Y=26
                    "                   B     F   FF         FF   B                   ", // Y=27
                    "                  BB     F  FF FFFFFF    FF  BB                  ", // Y=28
                    "                  B     FF  F FFFFF FF    F   B                  ", // Y=29
                    "                  B     FF  FFFPPPFF FF   F   B                  ", // Y=30
                    "                  B      F  FFPPOPPFF FF  FF  B                  ", // Y=31
                    "                  B  F   F  FFPOOOPFF  F   F  B                  ", // Y=32
                    "                  B  FF  FF FFPPOPPFF  F      B                  ", // Y=33
                    "                  B   F   FF FFPPPFFF  FF     B                  ", // Y=34
                    "                  B   F    FF FFFFF F  FF     B                  ", // Y=35
                    "                  BB  FF    FFFFFF FF  F     BB                  ", // Y=36
                    "                   B   FF         FF   F     B                   ", // Y=37
                    "                   B    FFF      FF   FF     B                   ", // Y=38
                    "                   BB     FFFFFFFF    F     BB                   ", // Y=39
                    "                    BB       FF      FF    BB                    ", // Y=40
                    "                     B              FF     B                     ", // Y=41
                    "                     BB          FFFF     BB                     ", // Y=42
                    "                      BBB       FF      BBB                      ", // Y=43
                    "                        BB             BB                        ", // Y=44
                    "                         BBBB       BBBB                         ", // Y=45
                    "                            BBBBBBBBB                            ", // Y=46
                    "                                                                 ", // Y=47
                    "                                                                 ", // Y=48
                    "                                                                 ", // Y=49
                    "                                                                 ", // Y=50
                    "                                                                 ", // Y=51
                    "                                                                 ", // Y=52
                    "                                                                 ", // Y=53
                    "                                                                 ", // Y=54
                    "                                                                 ", // Y=55
                    "                                                                 ", // Y=56
                    "                                                                 ", // Y=57
                    "                                                                 ", // Y=58
                    "                                                                 ", // Y=59
                    "                                                                 ", // Y=60
                    "                                                                 ", // Y=61
                    "                                                                 ", // Y=62
                    "                                                                 ", // Y=63
                    "                                                                 ", // Y=64
            },
            { // Z=14
                    "                                                                 ", // Y=0
                    "                                                                 ", // Y=1
                    "                                                                 ", // Y=2
                    "                                                                 ", // Y=3
                    "                                                                 ", // Y=4
                    "                                                                 ", // Y=5
                    "                                                                 ", // Y=6
                    "                                                                 ", // Y=7
                    "                                                                 ", // Y=8
                    "                                                                 ", // Y=9
                    "                                                                 ", // Y=10
                    "                                                                 ", // Y=11
                    "                                                                 ", // Y=12
                    "                                                                 ", // Y=13
                    "                                                                 ", // Y=14
                    "                                                                 ", // Y=15
                    "                                                                 ", // Y=16
                    "                                                                 ", // Y=17
                    "                                                                 ", // Y=18
                    "                             NNNNNNN                             ", // Y=19
                    "                          NNNNNNNNNNNNN                          ", // Y=20
                    "                         NNNNNNNNNNNNNNN                         ", // Y=21
                    "                       NNNNNNNNNNNNNNNNNNN                       ", // Y=22
                    "                      NNNNNNNNNNNNNNNNNNNNN                      ", // Y=23
                    "                      NNNNNNNNNNNNNNNNNNNNN                      ", // Y=24
                    "                     NNNNNNNNNNNNNNNNNNNNNNN                     ", // Y=25
                    "                    NNNNNNNNNNNNNNNNNNNNNNNNN                    ", // Y=26
                    "                    NNNNNNNNNNNNNNNNNNNNNNNNN                    ", // Y=27
                    "                    NNNNNNNNNNNNNNNNNNNNNNNNN                    ", // Y=28
                    "                   NNNNNNNNNNNNNNNNNNNNNNNNNNN                   ", // Y=29
                    "                   NNNNNNNNNNNNNNNNNNNNNNNNNNN                   ", // Y=30
                    "                   NNNNNNNNNNNNNNNNNNNNNNNNNNN                   ", // Y=31
                    "                   NNNNNNNNNNNNNNNNNNNNNNNNNNN                   ", // Y=32
                    "                   NNNNNNNNNNNNNNNNNNNNNNNNNNN                   ", // Y=33
                    "                   NNNNNNNNNNNNNNNNNNNNNNNNNNN                   ", // Y=34
                    "                   NNNNNNNNNNNNNNNNNNNNNNNNNNN                   ", // Y=35
                    "                    NNNNNNNNNNNNNNNNNNNNNNNNN                    ", // Y=36
                    "                    NNNNNNNNNNNNNNNNNNNNNNNNN                    ", // Y=37
                    "                    NNNNNNNNNNNNNNNNNNNNNNNNN                    ", // Y=38
                    "                     NNNNNNNNNNNNNNNNNNNNNNN                     ", // Y=39
                    "                      NNNNNNNNNNNNNNNNNNNNN                      ", // Y=40
                    "                      NNNNNNNNNNNNNNNNNNNNN                      ", // Y=41
                    "                       NNNNNNNNNNNNNNNNNNN                       ", // Y=42
                    "                         NNNNNNNNNNNNNNN                         ", // Y=43
                    "                          NNNNNNNNNNNNN                          ", // Y=44
                    "                             NNNNNNN                             ", // Y=45
                    "                                                                 ", // Y=46
                    "                                                                 ", // Y=47
                    "                                                                 ", // Y=48
                    "                                                                 ", // Y=49
                    "                                                                 ", // Y=50
                    "                                                                 ", // Y=51
                    "                                                                 ", // Y=52
                    "                                                                 ", // Y=53
                    "                                                                 ", // Y=54
                    "                                                                 ", // Y=55
                    "                                                                 ", // Y=56
                    "                                                                 ", // Y=57
                    "                                                                 ", // Y=58
                    "                                                                 ", // Y=59
                    "                                                                 ", // Y=60
                    "                                                                 ", // Y=61
                    "                                                                 ", // Y=62
                    "                                                                 ", // Y=63
                    "                                                                 ", // Y=64
            },
    };

    public static BlockPattern build(MultiblockMachineDefinition definition) {
        FactoryBlockPattern pattern = FactoryBlockPattern.start();
        for (String[] slice : SLICES) {
            pattern.aisle(slice);
        }

        // B / C：原子机械方块。C 额外放开本机配方表的自动仓 + 1 个维护仓（沿用 gtlcore 原结构 b 位的口径，
        // 不引入 GTO 的激光仓 —— 本机不是激光机）。
        TraceabilityPredicate atomicCasing = Predicates.blocks(GCyMBlocks.CASING_ATOMIC.get())
                .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                // ★ 2026-11-01：GTO 的 C 位是「原子机壳 + 自动仓 + 激光仓」，描述里也写了「激光仓：✔」。
                //   GTCEu 1.4.4 的 LaserHatchPartMachine 的缓冲区是 NotifiableLaserContainer
                //   （extends NotifiableEnergyContainer ⇒ IEnergyContainer）⇒ 放进结构就能当 EU 仓用。
                .or(Predicates.abilities(PartAbility.INPUT_LASER).setMaxGlobalLimited(2));

        return pattern
                .where('A', lazy("gtceu", "high_power_casing"))
                .where('B', Predicates.blocks(GCyMBlocks.CASING_ATOMIC.get()).setMinGlobalLimited(150))
                .where('C', atomicCasing)
                .where('D', lazy("gtceu", "trinium_frame"))
                .where('E', lazy("gtlcore", "advanced_fusion_coil"))
                .where('F', lazy("gtlmodular", "radiation_absorbent_casing"))
                .where('G', lazy("gtlcore", "hyper_mechanical_casing"))
                .where('H', lazy("gtlmodular", "boron_carbide_ceramic_radiation_resistant_mechanical_cube"))
                .where('I', lazy("gtlcore", "enhance_hyper_mechanical_casing"))
                .where('J', lazy("gtlmodular", "magnesium_oxide_ceramic_high_temperature_insulation_mechanical_block"))
                .where('K', lazy("gtlmodular", "magtech_casing"))
                .where('L', lazy("gtlmodular", "plasma_heater_casing"))
                // M：恒星热力容器 —— gtlcore 的 SCTier 三档判定（与本机 TierCasingMachine("SCTier") 同源，
                // 直接把 tier 写进 match context，机器才能读到等级）。
                .where('M', GTLPredicates.tierCasings(BlockMap.scMap, "SCTier"))
                .where('N', lazy("gtladditions", "quantum_glass"))
                .where('O', lazy("gtlmodular", "neutronium_stable_casing"))
                .where('P', lazy("gtladditions", "extreme_density_casing"))
                .where('Q', Predicates.controller(Predicates.blocks(definition.get())))
                .where('R', lazy("kubejs", "containment_field_generator"))
                .where('S', lazy("kubejs", "hollow_casing"))
                .where(' ', Predicates.any())
                .build();
    }

    /**
     * 按 id <b>惰性</b>解析方块谓词：结构在第一次判定/预览时才构建，那时 KubeJS 方块也已注册。
     * 取不到时记 error 并退回「不判定」，绝不在注册期抛异常（与奈亚拉托提普之触同款）。
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
                                "[gtlmodular] 恒星炎炀锻炉结构：方块 {} 不存在（该符号退化为不判定）", id);
                        return new BlockInfo[0];
                    }
                    return new BlockInfo[]{BlockInfo.fromBlockState(block.defaultBlockState())};
                });
    }
}
