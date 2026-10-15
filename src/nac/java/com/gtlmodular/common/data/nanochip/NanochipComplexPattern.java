package com.gtlmodular.common.data.nanochip;

import com.gtlmodular.common.diag.SwallowedErrors;
import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.common.data.GTMaterials;
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

/** 主机、模块、预检和诊断共用结构数据，避免另写几何规则后与实际匹配脱节。 */
public final class NanochipComplexPattern {

    private NanochipComplexPattern() {
    }

    public static final int NAC_W = 63;
    public static final int NAC_H = 49;
    public static final int NAC_D = 63;

    public static final int MOD_W = 7;
    public static final int MOD_H = 3;
    public static final int MOD_D = 7;

    public static final int NAC_CTRL_X = 31;
    public static final int NAC_CTRL_ROW = 8;
    /** 控制器锚点必须与 NAC_SLICES 中的控制器字符一致，否则全部世界坐标都会偏移。 */
    public static final int NAC_CTRL_Z = 32;

    public static final int MOD_CTRL_X = 3;
    public static final int MOD_CTRL_ROW = 2;

    public static final int MOD_CTRL_Z = 6;

    /** 匹配时登记 F 位世界坐标，供主机按真实结构位置连接模块。 */
    public static final String MODULE_SLOT_NAME = "gtlmodular_nanochip_module_slot";

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

    private static final char MOD_HATCH = 'V';
    private static final char MOD_MESH = 'P';
    private static final char MOD_CORE = 'Z';
    private static final char MOD_RING_ANY = 'X';

    // 不同塔身的同一个字母可代表不同材料，必须按各自的角色表解释。

    private enum TowerRole {
        ASSEMBLY,
        MESH,
        REINFORCEMENT,
        GLASS,
        FRAME,
        SHEET,
        HATCH,
        UEV_CASING,
        MATRIX_CASING,
        REINFORCED_CASING,
        COIL_UHV,
        COIL_UEV,
        CONTROLLER,
    }

    private record TowerCell(TowerRole role, String material) {
    }

    /** 部分塔身数据含底座行，不能把 rows 直接当作控制器以上的高度。 */
    private record TowerDef(int offsetY, int rows, String[] slices, Map<Character, TowerCell> cells) {
        int height() {
            return MOD_H + offsetY;
        }
    }

    /** 与空格同为 any，但保留独立字符以支持让位区域的完整性检查。 */
    private static final char TOWER_SPACE = 'K';

    private static final int TOWER_SPACE_H = 10;

    private static final String[] VACUUM_HATCH_IDS = {
            NanochipModContext.registryId("nanochip_vacuum_input_hatch").toString(),
            NanochipModContext.registryId("nanochip_vacuum_output_hatch").toString(),
    };

    private static final String TWR_FRAME_BLOCK = "gtceu:naquadah_alloy_frame";
    private static final String TWR_FRAME_FALLBACK = "gtceu:naquadah_alloy_block";
    private static final String TWR_SHEET_BLOCK = "gtceu:naquadah_alloy_block";
    private static final String TWR_ASSEMBLY_TIER_PREFIX = "gtlcore:component_assembly_line_casing_";
    private static final String[] TWR_ASSEMBLY_TIERS = {
            "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv", "uev", "uiv", "uxv", "opv", "max"};
    private static final String TWR_ASSEMBLY_BLOCK = "gtceu:assembly_line_casing";
    private static final String TWR_UEV_CASING_BLOCK = "gtceu:uev_machine_casing";
    private static final String TWR_COIL_BLOCK = "gtceu:naquadah_coil_block";

    private static final String[] NAC_SLICES = {
            "0,26,CCCC   CCCC;1,26,CCCCEEECCCC;2,26,CCCC   CCCC",
            "0,22,CCCC           CCCC;1,22,CCCCEEECEEECEEECCCC;2,22,CCCC           CCCC",
            "0,19,CCC                   CCC;1,19,CCCEEEEEEECEEECEEEEEEECCC;2,19,CCC                   CCC",
            "0,17,CC                         CC;1,17,CCEEEEEEEEEECEEECEEEEEEEEEECC;2,17,CC                         CC",
            "0,15,CC                             CC;1,15,CCEEEEEEEEEEEECEEECEEEEEEEEEEEECC;"
            + "2,15,CC                             CC",
            "0,14,C      IIIII           IIIII      C;1,14,CEEEEEEEEEEEEEECEEECEEEEEEEEEEEEEEC;"
            + "2,14,C      IIFII           IIFII      C",
            "0,12,CC      IBBBBBI         IBBBBBI      CC;1,12,CCEEEEEEECCCCCEEECEEECEEECCCCCEEEEEEECC;"
            + "2,12,CC      IBBBBBI         IBBBBBI      CC;3,12,CC                                   CC",
            "0,11,C        IBBBBBI         IBBBBBI        C;1,11,CEEEEEEEEECCCCCECCCCECCCCECCCCCEEEEEEEEEC;"
            + "2,11,C        IBBBBBI         IBBBBBI        C;3,11,C                                       C;"
            + "4,11,C                                       C",
            "0,10,C         IBBBBBICCCC CCCCIBBBBBI         C;1,10,CEEEEEEEEEECCCCCECCCCECCCCECCCCCEEEEEEEEEEC;"
            + "2,10,C         FBBBBBFCCCC CCCCFBBBBBF         C;3,10,C                                         C;"
            + "4,10,C                                         C;5,10,C                                         C",
            "0,9,A          IBBBBBI   C C   IBBBBBI          A;1,9,AEEEEEEEEEEECCCCCECCCCECCCCECCCCCEEEEEEEEEEEA;"
            + "2,9,A          IBBBBBI   C C   IBBBBBI          A;3,9,A                                           A;"
            + "4,9,A                                           A;5,9,A                                           A;"
            + "6,9,A                                           A;7,9,A                                           A",
            "0,8,C           IBBBBBI   C C   IBBBBBI           C;1,8,CEEEEEEEEEEEECCCCCEEECCECCEEECCCCCEEEEEEEEEEEEC;"
            + "2,8,C           IBBBBBI   C C   IBBBBBI           C;3,8,C                                             C;"
            + "4,8,C                                             C;5,8,C                                             C",
            "0,7,C    IIIII    IIIII    C C    IIIII    IIIII    C;1,7,CEEEEEEEEEEEEEEEEEEEEECCECCEEEEEEEEEEEEEEEEEEEEEC;"
            + "2,7,C    IIFII    IIFII    C C    IIFII    IIFII    C;3,7,C                                               C;"
            + "4,7,C                                               C",
            "0,6,C    IBBBBBI            C C            IBBBBBI    C;"
            + "1,6,CEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEC;"
            + "2,6,C    IBBBBBI            C C            IBBBBBI    C;"
            + "3,6,C                                                 C",
            "0,6,C    IBBBBBI            C C            IBBBBBI    C;"
            + "1,6,CEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEC;"
            + "2,6,C    IBBBBBI            C C            IBBBBBI    C;"
            + "3,6,C                                                 C",
            "0,5,C     IBBBBBI            C C            IBBBBBI     C;"
            + "1,5,CEEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEEC;"
            + "2,5,C     FBBBBBF            C C            FBBBBBF     C",
            "0,4,C      IBBBBBI            C C            IBBBBBI      C;"
            + "1,4,CEEEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEEEC;"
            + "2,4,C      IBBBBBI            C C            IBBBBBI      C",
            "0,4,C      IBBBBBI            C C            IBBBBBI      C;"
            + "1,4,CEEEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEEEC;"
            + "2,4,C      IBBBBBI            C C            IBBBBBI      C",
            "0,3,C        IIIIIC            C C            CIIIII        C;"
            + "1,3,CEEEEEEEEEEEEECCEEEEEEEEEECCECCEEEEEEEEEECCEEEEEEEEEEEEEC;"
            + "2,3,C        IIFIIC            C C            CIIFII        C",
            "0,3,C              C           C C           C              C;"
            + "1,3,CEEEEEEEEEEEEECCCEEEEEEEEECCECCEEEEEEEEECCCEEEEEEEEEEEEEC;"
            + "2,3,C              C           C C           C              C",
            "0,2,C                C          C C          C                C;"
            + "1,2,CEEEEEEEEEEEEEEECCCEEEEEEEECCECCEEEEEEEECCCEEEEEEEEEEEEEEEC;"
            + "2,2,C                C          C C          C                C",
            "0,2,C   IIIII         C         C C         C         IIIII   C;"
            + "1,2,CEEEEEEEEEEEEEEEECCCEEEEEECCCCCCCEEEEEECCCEEEEEEEEEEEEEEEEC;"
            + "2,2,C   IIFII         C       BBC CBB       C         IIFII   C;3,29,B   B;4,29,B   B",
            "0,2,C  IBBBBBI         C        C C        C         IBBBBBI  C;"
            + "1,2,CEEECCCCCEEEEEEEEECCCEEECCCCCCCCCCCEEECCCEEEEEEEEECCCCCEEEC;"
            + "2,2,C  IBBBBBI         C    BBCCCCCCCBB    C         IBBBBBI  C;3,28,CCCCCCC;4,28,CCCCCCC;5,29,B   B;"
            + "6,29,B   B;7,29,B   B;8,29,B   B;9,29,B   B",
            "0,1,C   IBBBBBI          C       C C       C          IBBBBBI   C;"
            + "1,1,CEEEECCCCCEEEEEEEEEECCCECCC  C C  CCCECCCEEEEEEEEEECCCCCEEEEC;"
            + "2,1,C   IBBBBBI          C  BCCEEEEEEECCB  C          IBBBBBI   C;3,26,CC       CC;4,26,CCC     CCC;"
            + "5,28,CCCCCCC;6,28,CCECECC;7,28,CCECECC;8,28,CCECECC;9,28,CCCCCCC;10,29,B   B;11,29,B   B;12,29,B   B;"
            + "13,29,B   B;14,29,B   B;15,29,B   B",
            "0,1,C   IBBBBBI           C      C C      C           IBBBBBI   C;"
            + "1,1,CEEEECCCCCEEEEEEEEEEECCCC    C C    CCCCEEEEEEEEEEECCCCCEEEEC;"
            + "2,1,C   FBBBBBF           CBCEEEEEEEEEEECBC           FBBBBBF   C;3,25,C           C;4,25,CC C     C CC;"
            + "5,26,CC       CC;6,26,EC       CE;7,26,EC       CE;8,26,EC       CE;9,26,CC       CC;10,28,CCCCCCC;"
            + "11,28,CCECECC;12,28,CCECECC;13,28,CCECECC;14,28,CCECECC;15,28,CCCCCCC;16,29,B   B;17,29,B   B;18,29,B   B",
            "0,1,C   IBBBBBI            C     C C     C            IBBBBBI   C;"
            + "1,1,CEEEECCCCCEEEEEEEEEEEECC     C C     CCEEEEEEEEEEEECCCCCEEEEC;"
            + "2,1,C   IBBBBBI           BCEEEEEEEEEEEEECB           IBBBBBI   C;3,24,C             C;4,24,C C C     C C C;"
            + "5,25,C           C;6,25,C           C;7,25,C           C;8,25,C           C;9,25,C           C;"
            + "10,26,CC       CC;11,26,EC       CE;12,26,EC       CE;13,26,EC       CE;14,26,EC       CE;15,26,CC       CC;"
            + "16,28,CCCCCCC;17,28,CC   CC;18,28,CC   CC;19,29,B   B;20,33,B B;21,33,B B;22,33,BBBBB;23,34,B B;24,34,B B;"
            + "25,34,B B;26,34,B B;27,36,B;28,36,B;29,36,B;30,36,B;31,36,B",
            "0,1,C   IBBBBBI                  C C                  IBBBBBI   C;"
            + "1,1,CEEEECCCCCEEEEEEEEEEECC      C C      CCEEEEEEEEEEECCCCCEEEEC;"
            + "2,1,C   IBBBBBI          BCEEEEEEEEEEEEEEECB          IBBBBBI   C;3,23,C               C;"
            + "4,23,C  C C     C C  C;5,24,C             C;6,24,C             C;7,24,C             C;8,24,C             C;"
            + "9,24,C             C;10,25,C           C;11,25,C           C;12,25,C           C;13,25,C           C;"
            + "14,25,C           C;15,25,C           C;16,26,CC   C   CCC;17,26,CC       CCC;18,26,CC     CCCCC;"
            + "19,28,CBCCCCCCCC;20,32,CCCCCC;21,32,CCCCCC;22,32,CCCCCCB;23,32,CCCCCC;24,32,CCCCCC;25,32,CCCCCC;26,33,CCCCC;"
            + "27,33,CCCCC;28,33,CCCCC;29,33,CCCCC;30,33,CCCCC;31,33,CCCCC;32,33,CCCCC;33,33,CCCCC;34,32,CCCCCC;35,32,CCCCCC;"
            + "36,32,CCCCCC;37,32,CCCCCC;38,32,CCCCC;39,32,CCCCC;40,32,CCCC;41,32,CCCC;42,32,CCC;43,32,CCC;44,32,CC;45,32,CC;"
            + "46,32,C;47,32,C",
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
            + "43,32,CCCB;44,32,CCB;45,32,CCB;46,32,CB;47,32,CB;48,32,B",
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
            + "38,28,CC  C   C;39,29,C  C  CC;40,29,C  C  C;41,32,C CC;42,32,C C;43,32,CCC;44,32,CC;45,32,CC;46,32,C;47,32,C",
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
            + "44,32,CC;45,32,CC;46,32,C;47,32,C",
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
            + "41,29,B  C CCB;42,32,C CB;43,32,CCCB;44,32,CCB;45,32,CCB;46,32,CB;47,32,CB;48,32,B",
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
            + "38,28,CC  CCCCC;39,29,C  CCCCC;40,29,C  CCCC;41,32,CCCC;42,32,CCC;43,32,CCC;44,32,CC;45,32,CC;46,32,C;47,32,C",
            "0,28,HHHHHHH;1,0,EEEEEEEEEEEEEEEEEEEECC      HBJJJBH      CCEEEEEEEEEEEEEEEEEEEE;2,21,CEEEEEEHBJJJBHEEEEEEC;"
            + "3,21,C      HBJJJBH      C;4,21,C      H     H      C;5,22,C                 C;6,22,C                 C;"
            + "7,22,C                 C;8,22,C        B        C;9,22,C       BBB       C;10,23,C      BBB      C;"
            + "11,23,C      BCB      C;12,23,C      CCC      C;13,23,C     CCCCC     C;14,23,C    CC   CC    C;"
            + "15,23,C   CC     CC   C;16,24,C CCDD   DDCC C;17,24,CCC         CCC;18,25,C           C;19,25,CCJJJ   C  C;"
            + "20,25,CCJJJ;21,25,CCJJJ;22,25,CCJJJ;23,25,CCJJJ;24,25,CCJJJ   D;25,25,CCJJJ;26,25,CCJJJ;27,25,CCJJJ;"
            + "28,25,CCJJJ;29,25,CCJJJ;30,25,CCJJJ;31,25,CCJJJ;32,25,CCJJJ  D;33,26,CCCC;34,26,CCCC;35,27,CCC;36,27,CCC;"
            + "37,28,CC;38,28,CC;39,29,C;40,29,C",
            "0,8,CCCCCCCCCCCCCCCCCCCCHHHHHHHCCCCCCCCCCCCCCCCCCCC;"
            + "1,0,EEEEEEECCCCCCCCCCCCCCCCCCCCCHBJJJBHCCCCCCCCCCCCCCCCCCCCCEEEEEEE;"
            + "2,8,CCCCCCCCCCCCCCEEEEEEHBJJJBHEEEEEECCCCCCCCCCCCCC;3,21,C      HBJJJBH      C;4,21,C      H     H      C;"
            + "5,22,C                 C;6,22,E                 E;7,22,E                 E;8,22,E        ~        E;"
            + "9,22,C        B        C;10,23,C       D       C;11,23,E      DBD      E;12,23,E      CCC      E;"
            + "13,23,E      DCD      E;14,23,E               E;15,23,C               C;16,24,C    D   D    C;"
            + "17,24,C             C;18,25,C           C;19,25,CCJJJ   C  C;20,25,CCJJJ;21,25,CCJJJ;22,25,CCJJJ;23,25,CCJJJ;"
            + "24,25,CCJJJ   D;25,25,CCJJJ;26,25,CCJJJ;27,25,CCJJJ;28,25,CCJJJ;29,25,CCJJJ;30,25,CCJJJ;31,25,CCJJJ;"
            + "32,25,CCJJJ  D;33,26,CCCC;34,26,CCCC;35,27,CCC;36,27,CCC;37,28,CC;38,28,CC;39,29,C;40,29,C",
            "0,0,C       C                   HHHHHHH                   C       C;"
            + "1,0,CCCCCCCCCCCCCCCCCCCCCC      HBBBBBH      CCCCCCCCCCCCCCCCCCCCCC;"
            + "2,0,C       C           BCEEEEEEHBBBBBHEEEEEECB           C       C;3,20,BC      HBBBBBH      CB;"
            + "4,20,BC      H     H      CB;5,21,BC                 CB;6,21,BC                 CB;7,21,BC                 CB;"
            + "8,21,BC                 CB;9,21,BC                 CB;10,22,BC               CB;11,22,BC               CB;"
            + "12,22,BC               CB;13,22,BC       C       CB;14,22,BC       C       CB;15,22,BC               CB;"
            + "16,23,BC    DDDDD    CB;17,23,BC             CB;18,24,BC           CB;19,24,BCCJJJCCCCCCCB;20,24,BCCJJJ CCCCBB;"
            + "21,24,BCCJJJ CCCC;22,24,BCCJJJ CCCC;23,24,BCCJJJ CCCC;24,24,BCCJJJ CCCC;25,24,BCCJJJ CCCC;26,24,BCCJJJ CCCC;"
            + "27,24,BCCJJJ CCC;28,24,BCCJJJ CCC;29,24,BCCJJJ CCC;30,24,BCCJJJ CCC;31,24,BCCJJJ CC;32,24,BCCJJJ CC;"
            + "33,25,BCCCC CC;34,25,BCCCC CC;35,26,BCCC C;36,26,BCCC C;37,27,BCC C;38,27,BCC C;39,28,BC;40,28,BC;41,29,B",
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
            + "40,29,C",
            "0,0,C       C                     C C                     C       C;"
            + "1,0,CEEEEEECCCEEEEEEEEEEECC       C C       CCEEEEEEEEEEECCCEEEEEEC;"
            + "2,0,C       C            BCEEEEEEEEEEEEEEEEECB            C       C;3,22,C                 C;"
            + "4,22,C                 C;5,23,C               C;6,23,C               C;7,23,C               C;"
            + "8,23,C               C;9,23,C               C;10,24,C             C;11,24,C             C;"
            + "12,24,C             C;13,24,C             C;14,24,C             C;15,24,C      C      C;16,25,C     C     CC;"
            + "17,25,C           CCB;18,25,C           CCB;19,24,BCCCCC JJJCCCCCB;20,25,CCCCC JJJCB CCB;21,25,CCCCC JJJC  CCB;"
            + "22,25,CCCCC JJJC  CCB;23,25,CCCCC JJJCDDCCB;24,25,CCCCC JJJC  CCB;25,25,CCCCC JJJC  CCB;26,25,CCCCC JJJC  CCB;"
            + "27,25,CCCCC CCC   CB;28,25,CCCCC CCCDDDCB;29,25,CCCCC CCC   B;30,25,CCCCC CCC;31,25,CCCCC CC;32,25,CCCCC CC;"
            + "33,26,CCCC CC;34,26,CCCC CC;35,27,CCC C;36,27,CCC C;37,28,CC C;38,28,CC C;39,29,C;40,29,C",
            "0,0,C     IIIII                   C C                   IIIII     C;"
            + "1,0,CEEEEEEEEEEEEEEEEEEEECC       C C       CCEEEEEEEEEEEEEEEEEEEEC;"
            + "2,0,C     IIFII          BCEEEEEEEEEEEEEEEEECB          IIFII     C;3,22,C                 C;"
            + "4,22,CCCCC   CCC   CCCCC;5,23,C               C;6,23,E               E;7,23,E               E;"
            + "8,23,E               E;9,23,C               C;10,24,C             C;11,24,E             E;"
            + "12,24,E             E;13,24,E             E;14,24,E             E;15,24,C             C;16,25,C     C     CC;"
            + "17,25,C     C     CC;18,25,C           CC;19,25,BCCCC JJJCCCCC;20,31,JJJC  CC;21,31,JJJCB CC;22,31,JJJC  CC;"
            + "23,29,DDJJJC  CC;24,31,JJJC  CC;25,31,JJJC  CC;26,28,D  JJJC  CC;27,31,CCC   C;28,31,CCC   C;29,31,CCC;"
            + "30,31,CCC;31,31,CC;32,31,CC;33,31,CC;34,31,CC;35,31,C;36,31,C;37,31,C;38,31,C",
            "0,1,C   IBBBBBI                  C C                  IBBBBBI   C;"
            + "1,1,CEEEECCCCCEEEEEEEEEEECC      C C      CCEEEEEEEEEEECCCCCEEEEC;"
            + "2,1,C   IBBBBBI          BCEEEEEEEEEEEEEEECB          IBBBBBI   C;3,23,C               C;"
            + "4,23,C  C C CEC C C  C;5,24,C             C;6,24,C             C;7,24,C             C;8,24,C             C;"
            + "9,24,C             C;10,25,C           C;11,25,C           C;12,25,C           C;13,25,C           C;"
            + "14,25,C           C;15,25,C           C;16,26,CC       CC;17,26,CC   C   CC;18,26,CC   C   CC;19,26,BCCC JJJC;"
            + "20,28,CC JJJC;21,28,CC JJJC;22,28,CC JJJCB;23,28,CC JJJCB;24,28,CC JJJCB;25,28,CC JJJCB;26,28,CC JJJCB;"
            + "27,28,CC CCCB;28,29,B CCCB;29,29,B CCCB;30,29,B CCCB;31,31,CCB;32,31,CCB;33,31,CCB;34,31,CCB;35,31,CB;36,31,CB;"
            + "37,31,CB;38,31,CB;39,31,B",
            "0,1,C   IBBBBBI            C     C C     C            IBBBBBI   C;"
            + "1,1,CEEEECCCCCEEEEEEEEEEEECC     C C     CCEEEEEEEEEEEECCCCCEEEEC;"
            + "2,1,C   IBBBBBI           BCEEEEEEEEEEEEECB           IBBBBBI   C;3,24,C             C;4,24,C C C CEC C C C;"
            + "5,25,C           C;6,25,C           C;7,25,C           C;8,25,C           C;9,25,C           C;"
            + "10,26,CC       CC;11,26,EC       CE;12,26,EC       CE;13,26,EC       CE;14,26,EC       CE;15,26,CC       CC;"
            + "16,28,CCCCCCC;17,28,CCCCCCC;18,28,CCCCCCC;19,27,BCCCCCCC;20,28,CC CCCC;21,28,CC CCCC;22,28,CC CCCC;"
            + "23,28,CC CCCC;24,28,CC CCCC;25,29,B CCCCB;26,29,B CCCC;27,29,B CCC;28,31,CCC;29,31,CCC;30,31,CCC;31,31,CC;"
            + "32,31,CC;33,31,CC;34,31,CC;35,31,C;36,31,C;37,31,C;38,31,C",
            "0,1,C   IBBBBBI           C      C C      C           IBBBBBI   C;"
            + "1,1,CEEEECCCCCEEEEEEEEEEECCCC    C C    CCCCEEEEEEEEEEECCCCCEEEEC;"
            + "2,1,C   FBBBBBF           CBCEEEEEEEEEEECBC           FBBBBBF   C;3,25,C           C;4,25,CC C CEC C CC;"
            + "5,26,CC       CC;6,26,EC       CE;7,26,EC       CE;8,26,EC       CE;9,26,CC       CC;10,28,CCCCCCC;"
            + "11,28,CCECECC;12,28,CCECECC;13,28,CCECECC;14,28,CCECECC;15,28,CCCCCCC;16,29,B   B;17,29,B BBB;18,29,B   B;"
            + "19,28,BB   B;20,29,B   B;21,29,B   B;22,29,B   B;23,29,B  BB;24,29,B   B;25,33,BB",
            "0,1,C   IBBBBBI          C       C C       C          IBBBBBI   C;"
            + "1,1,CEEEECCCCCEEEEEEEEEECCCECCC  C C  CCCECCCEEEEEEEEEECCCCCEEEEC;"
            + "2,1,C   IBBBBBI          C  BCCEEEEEEECCB  C          IBBBBBI   C;3,26,CC       CC;4,26,CCC CEC CCC;"
            + "5,28,C     C;6,28,C     C;7,28,C     C;8,28,C     C;9,28,CCCCCCC;10,29,B   B;11,29,B   B;12,29,B   B;"
            + "13,29,B   B;14,29,B   B;15,29,B   B",
            "0,2,C  IBBBBBI         C        C C        C         IBBBBBI  C;"
            + "1,2,CEEECCCCCEEEEEEEEECCCEEECCCCCCCCCCCEEECCCEEEEEEEEECCCCCEEEC;"
            + "2,2,C  IBBBBBI         C    BBCCCCCCCBB    C         IBBBBBI  C;3,28,CCCCCCC;4,28,CCCCCCC;5,29,B   B;"
            + "6,29,B   B;7,29,B   B;8,29,B   B;9,29,B   B",
            "0,2,C   IIIII         C         C C         C         IIIII   C;"
            + "1,2,CEEEEEEEEEEEEEEEECCCEEEEEECCCCCCCEEEEEECCCEEEEEEEEEEEEEEEEC;"
            + "2,2,C   IIFII         C       BBCCCBB       C         IIFII   C;3,29,BCCCB;4,29,B   B",
            "0,2,C                C          C C          C                C;"
            + "1,2,CEEEEEEEEEEEEEEECCCEEEEEEEECCECCEEEEEEEECCCEEEEEEEEEEEEEEEC;"
            + "2,2,C                C          CCC          C                C;4,29,B   B",
            "0,3,C              C           C C           C              C;"
            + "1,3,CEEEEEEEEEEEEECCCEEEEEEEEECCECCEEEEEEEEECCCEEEEEEEEEEEEEC;"
            + "2,3,C              C           C C           C              C;4,29,B   B",
            "0,3,C        IIIIIC            C C            CIIIII        C;"
            + "1,3,CEEEEEEEEEEEEECCEEEEEEEEEECCECCEEEEEEEEEECCEEEEEEEEEEEEEC;"
            + "2,3,C        IIFIIC            C C            CIIFII        C;4,29,B   B",
            "0,4,C      IBBBBBI            C C            IBBBBBI      C;"
            + "1,4,CEEEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEEEC;"
            + "2,4,C      IBBBBBI            C C            IBBBBBI      C;4,29,B   B",
            "0,4,C      IBBBBBI            C C            IBBBBBI      C;"
            + "1,4,CEEEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEEEC;"
            + "2,4,C      IBBBBBI            C C            IBBBBBI      C;4,29,B   B",
            "0,5,C     IBBBBBI            C C            IBBBBBI     C;"
            + "1,5,CEEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEEC;"
            + "2,5,C     FBBBBBF            C C            FBBBBBF     C;4,29,B   B",
            "0,6,C    IBBBBBI            C C            IBBBBBI    C;"
            + "1,6,CEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEC;"
            + "2,6,C    IBBBBBI            C C            IBBBBBI    C;"
            + "3,6,C                                                 C;4,29,B   B",
            "0,6,C    IBBBBBI            C C            IBBBBBI    C;"
            + "1,6,CEEEEECCCCCEEEEEEEEEEEECCECCEEEEEEEEEEEECCCCCEEEEEC;"
            + "2,6,C    IBBBBBI            C C            IBBBBBI    C;"
            + "3,6,C                                                 C;4,29,B   B",
            "0,7,C    IIIII    IIIII    C C    IIIII    IIIII    C;1,7,CEEEEEEEEEEEEEEEEEEEEECCECCEEEEEEEEEEEEEEEEEEEEEC;"
            + "2,7,C    IIFII    IIFII    C C    IIFII    IIFII    C;3,7,C                                               C;"
            + "4,7,C                     B   B                     C",
            "0,8,C           IBBBBBI   C C   IBBBBBI           C;1,8,CEEEEEEEEEEEECCCCCEEECCECCEEECCCCCEEEEEEEEEEEEC;"
            + "2,8,C           IBBBBBI   C C   IBBBBBI           C;3,8,C                    C   C                    C;"
            + "4,8,C                    B   B                    C;5,8,C                                             C",
            "0,9,A          IBBBBBI   C C   IBBBBBI          A;1,9,AEEEEEEEEEEECCCCCECCCCECCCCECCCCCEEEEEEEEEEEA;"
            + "2,9,A          IBBBBBI  CC CC  IBBBBBI          A;3,9,A                   C   C                   A;"
            + "4,9,A                   B   B                   A;5,9,A                                           A;"
            + "6,9,A                                           A;7,9,A                                           A",
            "0,10,C         IBBBBBICCCC CCCCIBBBBBI         C;1,10,CEEEEEEEEEECCCCCECCCCECCCCECCCCCEEEEEEEEEEC;"
            + "2,10,C         FBBBBBFCCCC CCCCFBBBBBF         C;3,10,C                  B   B                  C;"
            + "4,10,C                                         C;5,10,C                                         C",
            "0,11,C        IBBBBBI         IBBBBBI        C;1,11,CEEEEEEEEECCCCCECCCCECCCCECCCCCEEEEEEEEEC;"
            + "2,11,C        IBBBBBI  B   B  IBBBBBI        C;3,11,C                                       C;"
            + "4,11,C                                       C",
            "0,12,CC      IBBBBBI         IBBBBBI      CC;1,12,CCEEEEEEECCCCCEEECEEECEEECCCCCEEEEEEECC;"
            + "2,12,CC      IBBBBBI         IBBBBBI      CC;3,12,CC                                   CC",
            "0,14,C      IIIII           IIIII      C;1,14,CEEEEEEEEEEEEEECEEECEEEEEEEEEEEEEEC;"
            + "2,14,C      IIFII           IIFII      C",
            "0,15,CC                             CC;1,15,CCEEEEEEEEEEEECEEECEEEEEEEEEEEECC;"
            + "2,15,CC                             CC",
            "0,17,CC                         CC;1,17,CCEEEEEEEEEECEEECEEEEEEEEEECC;2,17,CC                         CC",
            "0,19,CCC                   CCC;1,19,CCCEEEEEEECEEECEEEEEEECCC;2,19,CCC                   CCC",
            "0,22,CCCC           CCCC;1,22,CCCCEEECEEECEEECCCC;2,22,CCCC           CCCC",
            "0,26,CCCC   CCCC;1,26,CCCCEEECCCC;2,26,CCCC   CCCC",
    };

    private static final String[] MOD_LAYERS = {
            "XVVVVVX",
            "XXXXXXX",
            "XVV~VVX",
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
            "XVVVVVX",
            "XXXXXXX",
            "XVVVVVX",
    };

    public static TraceabilityPredicate nanochipHatches() {
        // 预览仅实例化代表仓，避免为列举全部候选而构造大量复杂机器。
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
                        .setMinGlobalLimited(0).setMaxGlobalLimited(2))
                .or(com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.INPUT_LASER)
                        .setMinGlobalLimited(0).setMaxGlobalLimited(1))
                .or(com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.MAINTENANCE)
                        .setMinGlobalLimited(0).setMaxGlobalLimited(1));
    }

    /** 模块能源仓可选，以便无本地仓时从主机取电；计数谓词与主机独立；激光仓只由蚀刻阵列的 H 位单独开放。 */
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
                .or(com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.INPUT_ENERGY)
                        .setMinGlobalLimited(0).setMaxGlobalLimited(2))
                .or(com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                                com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.MAINTENANCE)
                        .setMinGlobalLimited(0).setMaxGlobalLimited(1));
    }

    private static Block block(com.tterrag.registrate.util.entry.BlockEntry<? extends Block> entry) {
        return entry.get();
    }

    private static TraceabilityPredicate meshOnly() {
        return blocks(block(NanochipBlocks.MESH_INTERFACE_CASING));
    }

    /** 真空仓限制只作用于主机 H 位，需用独立谓词，避免共享全局计数误约束其他格。 */
    private static TraceabilityPredicate vacuumHatches() {
        List<Block> list = new ArrayList<>(2);
        addVacuumBlock(list, NanochipMachines.VACUUM_INPUT_HATCH);
        addVacuumBlock(list, NanochipMachines.VACUUM_OUTPUT_HATCH);
        if (list.isEmpty()) {
            for (String id : VACUUM_HATCH_IDS) {
                Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
                if (b != net.minecraft.world.level.block.Blocks.AIR) {
                    list.add(b);
                }
            }
        }
        if (list.isEmpty()) {
            throw new IllegalStateException("真空传送带仓不存在（" + Arrays.toString(VACUUM_HATCH_IDS)
                    + "）⇒ H 位的「≥1 个真空传送带仓」无从判定");
        }
        if (NanochipModContext.config().isNanochipSimpleLogistics()) {
            return blocks(list.toArray(new Block[0])).setMinGlobalLimited(0);
        }
        return vacuumHatchesVanilla(list);
    }

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

    private static TraceabilityPredicate naquadahFrame() {
        return frames(GTMaterials.Naquadah);
    }

    /** 模块仓位与主机仓位展示不同候选，构建期间的标记用于选择预览列表。 */
    private static boolean buildingModulePattern;

    private static TraceabilityPredicate hatchOrCasing(TraceabilityPredicate hatches) {
        return hatches.or(meshOnly())
                .or(buildingModulePattern
                        // 模块自己的仓位应预览 I/O 部件，不能使用主机的模块控制器候选表。
                        ? com.gtlmodular.common.data.HatchPredicates.previewAbilitiesForModule(
                                com.gtlmodular.common.data.HatchPredicates.NAC_HATCH_ABILITIES)
                        : com.gtlmodular.common.data.HatchPredicates.previewAbilities(
                                com.gtlmodular.common.data.HatchPredicates.NAC_HATCH_ABILITIES));
    }

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

    private static TraceabilityPredicate moduleSlots(TraceabilityPredicate hatches) {
        List<IMachineBlock> accepted = new ArrayList<>();
        for (MultiblockMachineDefinition def : NanochipMachines.moduleDefinitions()) {
            if (def != null && def.get() != null) {
                accepted.add(def.get());
            }
        }
        TraceabilityPredicate blockPart;
        if (accepted.isEmpty()) {
            blockPart = meshOnly();
        } else {
            // 自动搭建按候选顺序选方块，机壳须排在模块控制器前，避免填满全部 F 位。
            blockPart = meshOnly().setMaxGlobalLimited(Math.max(1, countOf(MODULE_SLOT)))
                    .or(blocks(accepted.toArray(new IMachineBlock[0])).setMaxGlobalLimited(15))
                    // F 位允许空气，使未安装模块的位置可以保持空置。
                    .or(air());
        }
        return blockPart.setSlotName(MODULE_SLOT_NAME).or(hatches);
    }

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

    public record ModuleControllerSlot(String moduleId, BlockPos offset, Direction front) {
    }

    @Deprecated
    private static BlockPos patternToHostOffset(int x, int rowFromBottom, int z) {
        return new BlockPos(NAC_CTRL_X - x, rowFromBottom - NAC_CTRL_ROW, NAC_CTRL_Z - z);
    }

    /** 位点表是常量，缓存不可变列表避免每次预览重复构造。 */
    private static volatile List<ModuleControllerSlot> moduleControllerCache;

    /** 默认使用各基座负 Z 边中点、朝北的固定槽位；关闭后使用外侧边中点表。 */
    public static final boolean NAC_MODULE_SLOTS_CANONICAL_MINUS_Z = true;

    private static final ModuleControllerSlot[] NAC_MODULE_PREVIEW_SLOTS = {
            new ModuleControllerSlot(null, new BlockPos(-23, -6, -10), Direction.NORTH),
            new ModuleControllerSlot(null, new BlockPos(-23, -6, 6), Direction.NORTH),
            new ModuleControllerSlot(null, new BlockPos(-17, -6, -19), Direction.NORTH),
            new ModuleControllerSlot(null, new BlockPos(-17, -6, 15), Direction.NORTH),
            new ModuleControllerSlot(null, new BlockPos(-8, -6, -25), Direction.NORTH),
            new ModuleControllerSlot(null, new BlockPos(-8, -6, 21), Direction.NORTH),
            new ModuleControllerSlot(null, new BlockPos(8, -6, -25), Direction.NORTH),
            new ModuleControllerSlot(null, new BlockPos(8, -6, 21), Direction.NORTH),
            new ModuleControllerSlot(null, new BlockPos(17, -6, -19), Direction.NORTH),
            new ModuleControllerSlot(null, new BlockPos(17, -6, 15), Direction.NORTH),
            new ModuleControllerSlot(null, new BlockPos(23, -6, -10), Direction.NORTH),
            new ModuleControllerSlot(null, new BlockPos(23, -6, 6), Direction.NORTH),
    };

    private static final ModuleControllerSlot[] NAC_MODULE_PREVIEW_SLOTS_OUTWARD_EDGES = {
            new ModuleControllerSlot(null, new BlockPos(-26, -6, -7), Direction.WEST),
            new ModuleControllerSlot(null, new BlockPos(-26, -6, 9), Direction.WEST),
            new ModuleControllerSlot(null, new BlockPos(-20, -6, -16), Direction.WEST),
            new ModuleControllerSlot(null, new BlockPos(-20, -6, 18), Direction.WEST),
            new ModuleControllerSlot(null, new BlockPos(-8, -6, -25), Direction.NORTH),
            new ModuleControllerSlot(null, new BlockPos(-8, -6, 27), Direction.SOUTH),
            new ModuleControllerSlot(null, new BlockPos(8, -6, -25), Direction.NORTH),
            new ModuleControllerSlot(null, new BlockPos(8, -6, 27), Direction.SOUTH),
            new ModuleControllerSlot(null, new BlockPos(20, -6, -16), Direction.EAST),
            new ModuleControllerSlot(null, new BlockPos(20, -6, 18), Direction.EAST),
            new ModuleControllerSlot(null, new BlockPos(26, -6, -7), Direction.EAST),
            new ModuleControllerSlot(null, new BlockPos(26, -6, 9), Direction.EAST),
    };

    private static String previewId(com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec spec) {
        return spec.legacyKind() == null ? spec.id().toString() : spec.machineId();
    }

    public static List<ModuleControllerSlot> moduleControllerOffsets() {
        List<ModuleControllerSlot> cached = moduleControllerCache;
        if (cached != null) {
            return cached;
        }
        ModuleControllerSlot[] table = NAC_MODULE_SLOTS_CANONICAL_MINUS_Z
                ? NAC_MODULE_PREVIEW_SLOTS
                : NAC_MODULE_PREVIEW_SLOTS_OUTWARD_EDGES;
        var kinds = com.gtlmodular.common.machine.nanochip.registration.NanochipModules.all();
        List<ModuleControllerSlot> built = new ArrayList<>(table.length);
        for (int i = 0; i < table.length; i++) {
            ModuleControllerSlot slot = table[i];
            // 按槽轮转种类，使固定预览能展示多种模块塔身。
            String moduleId = kinds.isEmpty() ? null : previewId(kinds.get(i % kinds.size()));
            built.add(new ModuleControllerSlot(moduleId, slot.offset(), slot.front()));
        }
        moduleControllerCache = List.copyOf(built);
        return moduleControllerCache;
    }

    private static final int NAC_TABLE_SIZE = 12;

    @Deprecated
    private static final int MAX_AXIAL_DISTANCE = 3;
    @Deprecated
    private static final int EXPECTED_BASE_COUNT = 12;

    @Deprecated
    private static List<ModuleControllerSlot> deriveModuleControllerOffsets() {
        String[][] aisles = decode(NAC_SLICES, NAC_H, NAC_W);
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
        double centreX = (double) sumX / centres.size();
        double centreZ = (double) sumZ / centres.size();
        var kinds = com.gtlmodular.common.machine.nanochip.registration.NanochipModules.all();
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
            int fx = base.getX() - best.getX();
            int fz = base.getZ() - best.getZ();
            Direction front = patternDirToHostFront(Integer.signum(fx), Integer.signum(fz));
            String moduleId = previewId(kinds.get(i % kinds.size()));
            out.add(new ModuleControllerSlot(moduleId,
                    patternToHostOffset(best.getX(), best.getY(), best.getZ()), front));
        }
        out.sort(Comparator.comparingInt((ModuleControllerSlot s) -> s.offset().getX())
                .thenComparingInt(s -> s.offset().getY())
                .thenComparingInt(s -> s.offset().getZ()));
        return List.copyOf(out);
    }

    @Deprecated
    private static boolean isBaseCentre(Set<Long> fCells, BlockPos c) {
        return fCells.contains(c.offset(MAX_AXIAL_DISTANCE, 0, 0).asLong())
                && fCells.contains(c.offset(-MAX_AXIAL_DISTANCE, 0, 0).asLong())
                && fCells.contains(c.offset(0, 0, MAX_AXIAL_DISTANCE).asLong())
                && fCells.contains(c.offset(0, 0, -MAX_AXIAL_DISTANCE).asLong());
    }

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

    private static String[][] buildModuleAisles(MultiblockMachineDefinition def) {
        TowerDef tower = towerOf(def);
        int height = tower == null ? MOD_H : tower.height();
        String[] layers = rotate180(MOD_LAYERS, MOD_H, MOD_D);
        String[] towerSlices = tower == null ? null : rotate180(tower.slices(), tower.rows(), MOD_D);
        String[] flat = new String[MOD_D * height];
        for (int z = 0; z < MOD_D; z++) {
            for (int r = 0; r < MOD_H; r++) {
                flat[z * height + r] = layers[z * MOD_H + r];
            }
            if (tower != null) {
                // 塔身数据覆盖到底座的行不再写入，避免覆盖共享底座的既定角色。
                for (int y = 0; y < tower.rows(); y++) {
                    int row = 2 + tower.offsetY() - y;
                    if (row < 0 || row >= height) {
                        throw new IllegalStateException("塔身行号越界：patternRow=" + row
                                + "（模块 " + tower.offsetY() + " 层 / height=" + height + "）");
                    }
                    if (row < MOD_H) {
                        continue;
                    }
                    flat[z * height + row] = towerSlices[z * tower.rows() + y];
                }
            }
        }
        // 模块数据是逐行形状串，必须使用形状串重载，不能按主机的紧凑编码解析。
        return decode(flat, height, MOD_W, MOD_D);
    }

    public static BlockPattern build(MultiblockMachineDefinition def) {
        TraceabilityPredicate hatches = nanochipHatches();
        String[][] aisles = openTowerSpace(decode(NAC_SLICES, NAC_H, NAC_W));
        FactoryBlockPattern factory = FactoryBlockPattern.start();
        for (String[] aisle : aisles) {
            factory = factory.aisle(aisle);
        }
        // 主机空白区不参与匹配，让模块塔身和其他方块可共存于包围盒内，并减少逐格检查。
        factory = factory.where(AIR, any());
        // 让位字符仍需登记为 any，避免字符未登记导致匹配时空引用。
        factory = factory.where(TOWER_SPACE, any());
        factory = factory.where(FIREWALL, firewall());
        factory = factory.where(MESH, meshOnly());
        factory = factory.where(REINFORCEMENT, reinforcement());
        factory = factory.where(FRAME, naquadahFrame());
        factory = factory.where(GLASS, glass());
        factory = factory.where(MATRIX, matrix());
        factory = factory.where(MODULE_SLOT, moduleSlots(hatches));
        factory = factory.where(HATCH, hatchOrCasing(hatches).or(vacuumHatches()));
        factory = factory.where(IGNORED, any());
        factory = factory.where(CONTROLLER, controller(blocks(def.get())));
        return factory.build();
    }

    private static String[][] openTowerSpace(String[][] aisles) {
        int height = aisles[0].length;
        int width = aisles[0][0].length();
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
        NanochipModContext.logger().info(
                "[gtlmodular] NAC 塔身空间已让位：12 个基座 × 7×7×{} = {} 格改为 any()", TOWER_SPACE_H, opened);
        return out;
    }

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

    public static BlockPattern buildModule(MultiblockMachineDefinition def) {
        // 模块使用独立的仓室谓词，避免与主机共享全局仓数限制。
        TraceabilityPredicate hatches = moduleHatches();
        TowerDef tower = towerOf(def);
        String[][] aisles = buildModuleAisles(def);
        // 预检身份格从实际 aisles 登记，避免与结构使用不同几何数据。
        registerModuleIdentityCells(def, aisles, tower);
        FactoryBlockPattern factory = FactoryBlockPattern.start();
        for (String[] aisle : aisles) {
            factory = factory.aisle(aisle);
        }
        factory = factory.where(AIR, air());
        // 模块底座仓位允许空气，避免未装仓的格子必须堆满机壳。
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

    private static TowerDef towerOf(MultiblockMachineDefinition def) {
        if (def == null || def.getId() == null) {
            return null;
        }
        var spec = com.gtlmodular.common.machine.nanochip.registration.NanochipModules.byId(def.getId());
        if (spec == null) return null;
        var template = NanochipModuleTemplates.require(spec.template(), spec.id());
        Map<Character, TowerCell> mapped = new LinkedHashMap<>();
        template.cells().forEach((ch, cell) -> mapped.put(ch,
                new TowerCell(TowerRole.valueOf(cell.role()),
                        "FRAME".equals(cell.role()) && spec.frame() != null ? spec.frame().toString() : cell.material())));
        return new TowerDef(template.offsetY(), template.rows(), template.slices(), mapped);
    }

    /** 激光仓只属于自身声明了激光能力的模块（内置只有蚀刻阵列），其余模块的 H 位一律不收激光仓。 */
    private static boolean acceptsLaserHatch(MultiblockMachineDefinition def) {
        if (def == null || def.getId() == null) {
            return false;
        }
        var spec = com.gtlmodular.common.machine.nanochip.registration.NanochipModules.byId(def.getId());
        return spec != null && spec.features().laser();
    }

    /** 独立谓词：限量只落在本模块自己的 H 格上，不写进多格共用的 hatches 谓词对象。 */
    private static TraceabilityPredicate laserHatch() {
        return com.gtlmodular.common.data.HatchPredicates.lowCostAbilities(
                        com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.INPUT_LASER)
                .setMinGlobalLimited(0).setMaxGlobalLimited(1);
    }

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
            case HATCH -> acceptsLaserHatch(def)
                    ? hatchOrCasing(hatches).or(laserHatch())
                    : hatchOrCasing(hatches);
            case UEV_CASING -> blocks(blockByCandidates(TWR_UEV_CASING_BLOCK, TWR_ASSEMBLY_BLOCK,
                    NanochipModContext.registryId("nanochip_reinforcement_casing").toString()));
            case MATRIX_CASING -> blocks(blockByCandidates(NanochipModContext.registryId("nanochip_computational_matrix_casing").toString(),
                    TWR_ASSEMBLY_BLOCK));
            case COIL_UHV -> blocks(block(NanochipBlocks.UHV_SOLENOID_SUPERCONDUCTOR_COIL));
            case COIL_UEV -> blocks(block(NanochipBlocks.UEV_SOLENOID_SUPERCONDUCTOR_COIL));
            case CONTROLLER -> controller(blocks(def.get()));
        };
    }

    private static final Block[] NO_EXTRA_CANDIDATES = new Block[0];

    /** 基础候选与附加候选分开组合，保留替代材料仍能成形的兼容范围。 */
    private static TraceabilityPredicate withExtraCandidates(TraceabilityPredicate base, TowerCell cell) {
        Block[] extra = extraCandidatesFor(cell);
        return extra.length == 0 ? base : base.or(blocks(extra));
    }

    private static Block[] extraCandidatesFor(TowerCell cell) {
        if (cell == null) {
            return NO_EXTRA_CANDIDATES;
        }
        return switch (cell.role()) {
            case REINFORCED_CASING -> new Block[]{block(NanochipBlocks.NAQUADRIA_REINFORCED_WATER_PLANT_CASING)};
            case MATRIX_CASING -> new Block[]{block(NanochipBlocks.SHIELDED_ACCELERATOR_CASING)};
            case GLASS -> new Block[]{block(NanochipBlocks.NON_PHOTONIC_MATTER_EXCLUSION_GLASS)};
            case SHEET -> sheetmetalExtraOf(cell.material());
            default -> NO_EXTRA_CANDIDATES;
        };
    }

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

    private static String frameIdOf(String material) {
        String s = snake(material);
        return s == null ? null : "gtceu:" + s + "_frame";
    }

    private static String frameSubstituteOf(String material) {
        return null;
    }

    private static Block frameBlockOf(String material) {
        if (material != null && material.indexOf(':') >= 0) {
            return net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new ResourceLocation(material));
        }
        String sub = frameSubstituteOf(material);
        String[] ids = sub == null
                ? new String[]{frameIdOf(material), blockIdOf(material), TWR_FRAME_BLOCK, TWR_FRAME_FALLBACK}
                : new String[]{frameIdOf(material), blockIdOf(material), sub, TWR_FRAME_BLOCK, TWR_FRAME_FALLBACK};
        Block chosen = blockByCandidates(ids);
        if (sub != null) {
            Block subBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                    new ResourceLocation(sub));
            if (chosen == subBlock) {
                NanochipModContext.logger().warn(
                        "[gtlmodular] 塔身框架：原版材料 {} 在本包没有任何方块形态 ⇒ 使用**指定替代** {}"
                                + "（原版为「富集钬框架」，本包无钬/富集钬方块）",
                        material, sub);
            }
        }
        return chosen;
    }

    private static String blockIdOf(String material) {
        String s = snake(material);
        return s == null ? null : "gtceu:" + s + "_block";
    }

    private static String sheetIdOf(String material) {
        String s = snake(material);
        return s == null ? null : "gtceu:" + s + "_sheetmetal";
    }

    /** 所有候选都缺失时立即报错，避免静默生成永远无法匹配的结构。 */
    private static Block blockByCandidates(String... ids) {
        for (String id : ids) {
            if (id == null) {
                continue;
            }
            Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
            if (b != net.minecraft.world.level.block.Blocks.AIR) {
                if (!id.equals(ids[0])) {
                    NanochipModContext.logger().warn(
                            "[gtlmodular] 塔身方块 {} 不存在，回落到 {}", ids[0], id);
                }
                return b;
            }
        }
        throw new IllegalStateException("塔身方块候选链全部不存在：" + Arrays.toString(ids));
    }

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
            NanochipModContext.logger().warn(
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
                        NanochipModContext.logger().warn(
                                "[gtlmodular] decode: 第 {} 片出现非紧凑字面量 \"{}\"，按整行(row=0, pad=0)处理",
                                z, entry);
                    }
                    if (row < 0 || row >= height) {
                        NanochipModContext.logger().error(
                                "[gtlmodular] decode: 行号 {} 越界（height={}），已忽略该条 \"{}\"", row, height, entry);
                        continue;
                    }
                    int room = width - Math.max(0, pad);
                    if (pad < 0 || room <= 0) {
                        NanochipModContext.logger().error(
                                "[gtlmodular] decode: 左填充 {} 越界（width={}），已忽略该条 \"{}\"", pad, width, entry);
                        continue;
                    }
                    int len = Math.min(content.length(), room);
                    if (len < content.length()) {
                        NanochipModContext.logger().error(
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

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException e) {
            NanochipModContext.logger().error("[gtlmodular] decode: 行号/填充 \"{}\" 不是整数，按 {} 处理", s, fallback);
            return fallback;
        }
    }

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

    public static int countOf(char ch) {
        int[] c = counts();
        return ch < c.length ? c[ch] : 0;
    }

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

    /** 备料数从结构数据派生，方块名延迟翻译，避免注册期读取尚未就绪的方块。 */
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

    /** 只用条目的注册 ID 构造翻译键，避免在注册完成前调用 BlockEntry#get。 */
    private static Component blockName(BlockEntry<? extends Block> entry) {
        ResourceLocation id = entry.getId();
        return Component.translatable("block." + id.getNamespace() + "." + id.getPath());
    }

    private static void appendEntry(MutableComponent out, char ch, BlockEntry<? extends Block> entry, boolean first) {
        if (!first) {
            out.append(Component.literal(" §7· "));
        }
        out.append(blockName(entry));
        out.append(Component.literal(" §f" + countOf(ch)));
    }

    private static volatile int[][] STRICT_CELLS;

    /** 只审计唯一方块格，多解格缺少上下文时容易误报缺件。 */
    private static Block strictBlockFor(char ch) {
        return switch (ch) {
            case FIREWALL -> block(NanochipBlocks.FIREWALL_PROJECTION_CASING);
            case MESH -> block(NanochipBlocks.MESH_INTERFACE_CASING);
            case REINFORCEMENT -> block(NanochipBlocks.REINFORCEMENT_CASING);
            case GLASS -> block(NanochipBlocks.COMPLEX_GLASS);
            case MATRIX -> block(NanochipBlocks.COMPUTATIONAL_MATRIX_CASING);
            default -> null;
        };
    }

    /** 严格格与能力格共用同一解析入口，避免预检和实际图案脱节。 */
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
            SwallowedErrors.report("NanochipComplexPattern#auditStructure", t);
            return lines;
        }
    }

    public static final int IDENTITY_ROLE_MESH = 0;
    public static final int IDENTITY_ROLE_REINFORCEMENT = 1;
    public static final int IDENTITY_ROLE_FIREWALL = 2;
    public static final int IDENTITY_ROLE_GLASS = 3;
    public static final int IDENTITY_ROLE_MATRIX = 4;
    public static final int IDENTITY_ROLE_AIR = 5;

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

    /** 能力格允许多种方块，仅用指纹判断变化，不能作为直接判定成形的证据。 */
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

    /** 拆块索引只需记录会被评估的格，any 区域的变更不应触发这条索引路径。 */
    public static int[] hostCheckCells() {
        List<int[]> cells = new ArrayList<>();
        forEachNacCell((x, row, z, ch) -> {
            if (ch == AIR || ch == TOWER_SPACE || ch == IGNORED) {
                return;
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

    private static final Map<String, int[]> MODULE_IDENTITY_CELLS = new java.util.concurrent.ConcurrentHashMap<>();

    /** 缺少模块预检表时交上游完整匹配，不能将未登记解释为结构错误。 */
    public static int[] moduleIdentityCells(String moduleId) {
        if (moduleId == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(moduleId.indexOf(':') < 0
                ? NanochipModContext.modId() + ":" + moduleId : moduleId);
        return id == null ? null : MODULE_IDENTITY_CELLS.get(id.toString());
    }

    /** 部装外壳格另存一份：档位由方块身份决定，不能混进只认唯一方块的身份表。 */
    private static final Map<String, int[]> MODULE_ASSEMBLY_CELLS = new java.util.concurrent.ConcurrentHashMap<>();

    public static int[] moduleAssemblyCells(String moduleId) {
        if (moduleId == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(moduleId.indexOf(':') < 0
                ? NanochipModContext.modId() + ":" + moduleId : moduleId);
        return id == null ? null : MODULE_ASSEMBLY_CELLS.get(id.toString());
    }

    /** 塔身字符含义优先于共享底座默认值，避免同一字母在不同模块被误解释。 */
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

    /** 从实际交给 factory 的 aisles 生成身份格，避免独立维护一份可能过期的几何表。 */
    private static void registerModuleIdentityCells(MultiblockMachineDefinition def, String[][] aisles, TowerDef tower) {
        if (def == null || def.getId() == null || aisles == null) {
            return;
        }
        List<int[]> cells = new ArrayList<>();
        List<int[]> assembly = new ArrayList<>();
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
                    char ch = line.charAt(x);
                    int role = moduleRoleOf(ch, tower);
                    if (role >= 0) {
                        cells.add(new int[]{x, row, z, role});
                    }
                    TowerCell towerCell = tower == null ? null : tower.cells().get(ch);
                    if (towerCell != null && towerCell.role() == TowerRole.ASSEMBLY) {
                        assembly.add(new int[]{x, row, z});
                    }
                }
            }
        }
        int[] flat = new int[cells.size() * 4];
        for (int i = 0; i < cells.size(); i++) {
            System.arraycopy(cells.get(i), 0, flat, i * 4, 4);
        }
        MODULE_IDENTITY_CELLS.put(def.getId().toString(), flat);
        if (!assembly.isEmpty()) {
            int[] assemblyFlat = new int[assembly.size() * 3];
            for (int i = 0; i < assembly.size(); i++) {
                System.arraycopy(assembly.get(i), 0, assemblyFlat, i * 3, 3);
            }
            MODULE_ASSEMBLY_CELLS.put(def.getId().toString(), assemblyFlat);
        }
        NanochipModContext.logger().debug(
                "[gtlmodular] A+ 精确预检：登记模块 {} 的身份格 {} 个（唯一方块 + 严格空气；"
                        + "多解/带追加候选的角色一律不列）", def.getId().getPath(), cells.size());
    }
}
