package com.gtlmodular.common.data.nanochip;

import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;

/** 真实匹配、预检与外部规格共用塔身目录，光学族只替换角色参数。 */
public final class NanochipModuleTemplates {
    private NanochipModuleTemplates() {}

    public record Cell(String role, String material) {}
    public record Template(int offsetY, int rows, String[] slices, Map<Character, Cell> cells) {
        public Template {
            slices = slices.clone();
            cells = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(cells));
        }
        @Override public String[] slices() { return slices.clone(); }
    }

    public static Template require(String template, ResourceLocation moduleId) {
        Template found = moduleTowers().get(template);
        if (found == null) throw new IllegalArgumentException("Unknown NAC module template: " + template + " for " + moduleId);
        return found;
    }

    private static Template optical(String roles) {
        return new Template(7, 7, new String[] {
                "       ", " BBCBB ", " AACAA ", "   C   ", "   C   ", "   C   ", " AACAA ",
                " AAAAA ", "BBBCBBB", "ABD DBA", " BD DB ", " BD DB ", " BD DB ", "ABD DBA",
                " A   A ", "BBCCCBB", "AD   DA", " DCCCD ", " D   D ", " DCCCD ", "AD   DA",
                " A   A ", "CCCBCCC", "C  C  C", "C CCC C", "C     C", "C CCC C", "C  C  C",
                " A   A ", "BBCCCBB", "AD   DA", " DCCCD ", " D   D ", " DCCCD ", "AD   DA",
                " AAAAA ", "BBBCBBB", "ABD DBA", " BD DB ", " BD DB ", " BD DB ", "ABD DBA",
                "       ", " BBCBB ", " AACAA ", "   C   ", "   C   ", "   C   ", " AACAA ",
        }, cells(roles));
    }

    private static Map<Character, Cell> cells(String spec) {
        Map<Character, Cell> map = new LinkedHashMap<>();
        for (String part : spec.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq != 1) {
                throw new IllegalStateException("塔身角色表格式错：\"" + part + "\"（应形如 A=MESH）");
            }
            char ch = part.charAt(0);
            String[] roleAndMat = part.substring(eq + 1).split(":", 2);
            String role = roleAndMat[0];
            map.put(ch, new Cell(role, roleAndMat.length > 1 ? roleAndMat[1] : null));
        }
        return map;
    }

    private static Map<String, Template> buildTowers() {
        Map<String, Template> m = new LinkedHashMap<>();
        m.put("nanochip_assembly_matrix", new Template(4, 4, new String[] {
                "       ", "  BFB  ", " DDFDD ", "  BFB  ",
                "  CFC  ", " D   D ", "DB   BD", " DEEED ",
                " CFFFC ", "B     B", "D     D", "BE   EB",
                " FFDFF ", "F  G  F", "F  G  F", "FE G EF",
                " CFFFC ", "B     B", "D     D", "BE   EB",
                "  CFC  ", " D   D ", "DB   BD", " DEEED ",
                "       ", "  BFB  ", " DDFDD ", "  BFB  ",
        }, cells("B=ASSEMBLY,C=MESH,D=REINFORCEMENT,E=FRAME:NaquadahAlloy,F=GLASS,G=SHEET:NaquadahAlloy")));
        m.put("nanochip_biological_coordinator", new Template(5, 6, new String[] {
                "       ", " AAAAA ", "  C C  ", "  C C  ", "  C C  ", "   ~   ",
                "  CCC  ", "AABBBAA", " ABDBA ", " ABDBA ", " ABDBA ", "       ",
                " C   C ", "ABDDDBA", "CB   BC", "CB   BC", "CB   BC", "       ",
                " C   C ", "ABDADBA", " D   D ", " D   D ", " D   D ", "       ",
                " C   C ", "ABDDDBA", "CB   BC", "CB   BC", "CB   BC", "       ",
                "  CCC  ", "AABBBAA", " ABDBA ", " ABDBA ", " ABDBA ", "       ",
                "       ", " AAAAA ", "  C C  ", "  C C  ", "  C C  ", "       ",
        }, cells("A=MESH,B=REINFORCEMENT,C=FRAME:Tritanium,D=GLASS,~=CONTROLLER")));
        m.put("nanochip_board_processor", new Template(4, 4, new String[] {
                "       ", " A   A ", " A   A ", " B   B ",
                " A   A ", "CCC CCC", "DDD DDD", "DDD DDD",
                "  A A  ", "CBCCCBC", "D DDD D", "D DDD D",
                "   A   ", "CBCCCBC", "D DAD D", "D DAD D",
                "  A A  ", "CBCCCBC", "D DDD D", "D DDD D",
                " A   A ", "CCC CCC", "DDD DDD", "DDD DDD",
                "       ", " A   A ", " A   A ", " B   B ",
        }, cells("A=FRAME:Octiron,B=MESH,C=REINFORCEMENT,D=GLASS")));
        m.put("nanochip_cutting_chamber", new Template(5, 5, new String[] {
                "       ", "       ", "       ", " A   A ", " A   A ",
                "  CCC  ", " BBBBB ", " CEEEC ", "ACEEECA", "ACEEECA",
                " CCDCC ", "B     B", "B     B", "B     B", "B DDD B",
                " DDDDD ", "BCCCCCB", "EAAAAAE", "E     E", "E DDD E",
                " CCDCC ", "B     B", "B     B", "B     B", "B DDD B",
                "  CCC  ", " BBBBB ", " CEEEC ", "ACEEECA", "ACEEECA",
                "       ", "       ", "       ", " A   A ", " A   A ",
        }, cells("A=FRAME:Neutronium,B=MESH,C=REINFORCEMENT,D=REINFORCED_CASING,E=GLASS")));
        m.put("nanochip_encasement_wrapper", new Template(6, 6, new String[] {
                "       ", "  DBD  ", "  DBD  ", " CDBDC ", " CDBDC ", " CDBDC ",
                "  BBB  ", " A   A ", " A C A ", "CA   AC", "CA C AC", "CA   AC",
                " BAAAB ", "D     D", "D AAA D", "D     D", "D AAA D", "D     D",
                " BAAAB ", "B     B", "BCAAACB", "B     B", "BCAAACB", "B     B",
                " BAAAB ", "D     D", "D AAA D", "D     D", "D AAA D", "D     D",
                "  BBB  ", " A   A ", " A C A ", "CA   AC", "CA C AC", "CA   AC",
                "       ", "  DBD  ", "  DBD  ", " CDBDC ", " CDBDC ", " CDBDC ",
        }, cells("A=MESH,B=REINFORCEMENT,C=FRAME:Quantanium,D=GLASS")));
        m.put("nanochip_etching_array", new Template(4, 4, new String[] {
                "  EEE  ", " EBBBE ", " EBHBE ", " BBBBB ",
                "  AGA  ", " A   A ", " G F G ", "B     B",
                "  AGA  ", " A   A ", " G F G ", "B     B",
                "  AGA  ", " A   A ", " G F G ", "B     B",
                "  AGA  ", " A   A ", " G F G ", "B     B",
                "  AGA  ", " BCCCB ", " BCCCB ", "BBCCCBB",
                "  EEE  ", " EAEAE ", " EAEAE ", " BAEAB ",
        }, cells("A=MESH,B=REINFORCEMENT,C=MATRIX_CASING,E=FRAME:EnrichedHolmium,F=GLASS,G=GLASS,H=HATCH")));
        m.put("nanochip_optical_organizer", optical("A=FRAME:DegenerateRhenium,B=MESH,C=REINFORCEMENT,D=GLASS"));
        m.put("nanochip_smd_processor", new Template(3, 3, new String[] {
                " B   B ", " EA AE ", " BB BB ",
                "BBDDDBB", "ECA ACE", "BBB BBB",
                " D   D ", "AAA AAA", "BBBDBBB",
                " D   D ", "       ", "  DDD  ",
                " D   D ", "AAA AAA", "BBBDBBB",
                "BBDDDBB", "ECA ACE", "BBB BBB",
                " B   B ", " EA AE ", " BB BB ",
        }, cells("A=MESH,B=REINFORCEMENT,C=UEV_CASING,D=FRAME:Infuscolium,E=GLASS")));
        m.put("nanochip_splitter", new Template(2, 2, new String[] {
                "  A    ", " CBBBC ",
                "  AA A ", "CBBBBBC",
                "   ACA ", "BBBBBBB",
                "AA A A ", "BBBBBBB",
                " ACA AA", "BBBBBBB",
                " A A   ", "CBBBBBC",
                "   AA  ", " CBBBC ",
        }, cells("A=HATCH,B=REINFORCEMENT,C=FRAME:Kevlar")));
        m.put("nanochip_superconductor_splitter", new Template(7, 7, new String[] {
                "       ", "       ", " DEEED ", "       ", "       ", "       ", " DEEED ",
                "       ", " D   D ", "DBD DBD", " B   B ", " B   B ", " B   B ", "DDD DDD",
                "   C   ", "  CFC  ", "EDCFCDE", "  CFC  ", "  CFC  ", "  CFC  ", "EDCFCDE",
                "  CCC  ", "  FAF  ", "E FAF E", "  FAF  ", "  FAF  ", "  FAF  ", "E FAF E",
                "   C   ", "  CFC  ", "EDCFCDE", "  CFC  ", "  CFC  ", "  CFC  ", "EDCFCDE",
                "       ", " D   D ", "DBD DBD", " B   B ", " B   B ", " B   B ", "DDD DDD",
                "       ", "       ", " DEEED ", "       ", "       ", "       ", " DEEED ",
        }, cells("A=COIL_UHV,B=COIL_UEV,C=MESH,D=REINFORCEMENT,E=FRAME:Naquadria,F=GLASS")));
        m.put("nanochip_wire_tracer", new Template(5, 5, new String[] {
                "       ", "  BBB  ", "  BAB  ", "  BAB  ", " BBABB ",
                " DAAAD ", " E D E ", " E A E ", " E   E ", "BEDDDEB",
                " AEEEA ", "B     B", "B     B", "B     B", "BD   DB",
                " AEEEA ", "BD   DB", "AA C AA", "A  B  A", "AD B DA",
                " AEEEA ", "B     B", "B     B", "B     B", "BD   DB",
                " DAAAD ", " E D E ", " E A E ", " E   E ", "BEDDDEB",
                "       ", "  BBB  ", "  BAB  ", "  BAB  ", " BBABB ",
        }, cells("A=MESH,B=REINFORCEMENT,C=SHEET:SuperconductorUEVBase,D=FRAME:SuperconductorUHVBase,E=GLASS")));
        return m;
    }
    /** 结构角色表延迟到图案构建时解析，避免注册期类加载触发重型计算。 */
    private static Map<String, Template> moduleTowersCache;

    private static synchronized Map<String, Template> moduleTowers() {
        if (moduleTowersCache == null) {
            Map<String, Template> all = buildTowers();
            all.putAll(extensionTowers());
            moduleTowersCache = all;
        }
        return moduleTowersCache;
    }

    /** 组织器变体共享形状但分别指定框架，避免共用几何时丢失材料差异。 */
    private static Map<String, Template> extensionTowers() {
        Map<String, Template> m = new LinkedHashMap<>();
        m.put("nanochip_exotic_organizer", optical("A=FRAME:Orichalcum,B=MESH,C=REINFORCEMENT,D=GLASS"));
        m.put("nanochip_cosmic_organizer", optical("A=FRAME:Infuscolium,B=MESH,C=REINFORCEMENT,D=GLASS"));
        m.put("nanochip_supracausal_organizer", optical("A=FRAME:Taranium,B=MESH,C=REINFORCEMENT,D=GLASS"));
        return m;
    }

}
