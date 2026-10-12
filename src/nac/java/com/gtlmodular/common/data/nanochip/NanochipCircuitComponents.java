package com.gtlmodular.common.data.nanochip;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 每种 CC/PC 独立注册为物品，保留加工状态与真件映射，使物流和配方能按物品身份区分。 */
public final class NanochipCircuitComponents {

    private NanochipCircuitComponents() {}

    public static final class Entry {
        public final String enumName;
        public final int metaId;
        public final boolean processed;
        public final String nameKey;
        public final String itemId;
        public final String realRef;
        public final String calibration;

        private ItemEntry<CcItem> entry;

        Entry(String enumName, int metaId, boolean processed, String nameKey, String itemId, String realRef, String calibration) {
            this.enumName = enumName;
            this.metaId = metaId;
            this.processed = processed;
            this.nameKey = nameKey;
            this.itemId = itemId;
            this.realRef = realRef;
            this.calibration = calibration;
        }

        public Item item() { return entry == null ? null : entry.get(); }

        public ResourceLocation id() { return NanochipModContext.registryId(itemId); }

        public String langKey() { return "gtlmodular.cc." + itemId + ".name"; }

        public Component displayName() {
            if (processed || realRef == null) {
                return Component.translatable(langKey());
            }
            Component real = realName();
            return real == null ? Component.translatable(langKey())
                    : Component.translatable("gt.circuitcomponent.base", real);
        }

        private Component realName() {
            if (realRef.startsWith("item:")) {
                Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("gtceu", realRef.substring(5)));
                return it == null ? null : new ItemStack(it).getHoverName();
            }
            if (realRef.startsWith("itemns:")) {
                String[] p2 = realRef.substring(7).split(":", 2);
                Item it2 = ForgeRegistries.ITEMS.getValue(new ResourceLocation(p2[0], p2[1]));
                return it2 == null ? null : new ItemStack(it2).getHoverName();
            }
            String[] parts = realRef.substring(4).split(":", 2);
            return Component.translatable("tagprefix." + parts[1], Component.translatable("material.gtceu." + parts[0]));
        }
    }

    public static final class CcItem extends Item {
        private final Entry entry;

        public CcItem(Properties properties, Entry entry) {
            super(properties);
            this.entry = entry;
        }

        public Entry circuitComponent() { return entry; }

        @Override
        public Component getName(ItemStack stack) { return entry.displayName(); }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("gtlmodular.cc.tooltip.base"));
            tooltip.add(Component.translatable(entry.processed
                    ? "gtlmodular.cc.tooltip.pc.base" : "gtlmodular.cc.tooltip.cc.base"));
            tooltip.add(Component.translatable(entry.processed
                    ? "gtlmodular.cc.tooltip.pc.howto" : "gtlmodular.cc.tooltip.cc.howto"));
            if (flag.isAdvanced()) {
                tooltip.add(Component.literal("\u00a78" + entry.itemId + " / meta " + entry.metaId
                        + (entry.calibration == null ? "" : " / " + entry.calibration)));
            }
        }
    }

    private static final List<Entry> ALL = new ArrayList<>(366);
    private static final Map<Integer, Entry> BY_META = new LinkedHashMap<>();
    private static final Map<String, Entry> BY_ENUM = new LinkedHashMap<>();
    private static final Map<String, ItemEntry<CcItem>> REGISTERED = new LinkedHashMap<>();

    private static void add(String enumName, int metaId, boolean processed, String nameKey, String realRef, String calibration) {
        Entry e = new Entry(enumName, metaId, processed, nameKey, "cc_" + snake(enumName), realRef, calibration);
        ALL.add(e);
        BY_META.put(metaId, e);
        BY_ENUM.put(enumName, e);
    }

    /** 缩写边界需要单独处理，避免连续大写字母生成不兼容的物品路径。 */
    private static String snake(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean upper = c >= 'A' && c <= 'Z';
            if (upper && i > 0) {
                char prev = s.charAt(i - 1);
                boolean prevLowerOrDigit = (prev >= 'a' && prev <= 'z') || (prev >= '0' && prev <= '9');
                boolean prevUpper = prev >= 'A' && prev <= 'Z';
                boolean nextLower = i + 1 < s.length() && s.charAt(i + 1) >= 'a' && s.charAt(i + 1) <= 'z';
                if (prevLowerOrDigit || (prevUpper && nextLower)) {
                    sb.append('_');
                }
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    static {
        addRows();
        for (Entry e : ALL) {
            e.entry = NanochipModContext.machineRegistrate().item(e.itemId, p -> new CcItem(p, e)).register();
            REGISTERED.put(e.itemId, e.entry);
        }
    }

    public static void init() {
        if (ALL.size() != 366) {
            throw new IllegalStateException("NAC CC 物品层表异常：应为 366 条（NH 166 + 自造 200），实为 " + ALL.size());
        }
    }

    public static List<Entry> all() { return Collections.unmodifiableList(ALL); }

    public static Item itemFor(int metaId) {
        Entry e = BY_META.get(metaId);
        return e == null ? null : e.item();
    }

    public static ItemStack stack(int metaId, int count) {
        Item it = itemFor(metaId);
        return it == null ? ItemStack.EMPTY : new ItemStack(it, count);
    }

    public static ItemStack stack(String enumName, int count) {
        Entry e = BY_ENUM.get(enumName);
        return e == null || e.item() == null ? ItemStack.EMPTY : new ItemStack(e.item(), count);
    }

    public static String idFor(String enumName) {
        Entry e = BY_ENUM.get(enumName);
        return e == null ? null : e.itemId;
    }

    public static Map<Integer, String> metaToName() {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (Entry e : ALL) m.put(e.metaId, e.enumName);
        return Collections.unmodifiableMap(m);
    }

    public static Entry byEnumName(String enumName) { return BY_ENUM.get(enumName); }

    public static Entry byMetaId(int metaId) { return BY_META.get(metaId); }

    private static void addRows() {

        add("WireNiobiumTitanium", 0, false, "gt.circuitcomponent.wirenbti", "mat:niobium_titanium:fine_wire", null);
        add("WireYttriumBariumCuprate", 1, false, "gt.circuitcomponent.wireybc", "mat:yttrium_barium_cuprate:fine_wire", null);
        add("WireLumiium", 2, false, "gt.circuitcomponent.wirelumiium", null, null);
        add("WireProtoHalkonite", 3, false, "gt.circuitcomponent.wireprotohalkonite", null, null);
        add("CableOpticalFiber", 5, false, "gt.circuitcomponent.cable.opticalfiber", null, null);
        add("WireInfinity", 6, false, "gt.circuitcomponent.wireinfinity", null, null);
        add("WireHypogen", 7, false, "gt.circuitcomponent.wirehypogen", null, null);
        add("WireMagMatter", 8, false, "gt.circuitcomponent.wiremagmatter", null, null);
        add("ProcessedWireNiobiumTitanium", 100, true, "gt.circuitcomponent.processed.wirenbti", null, null);
        add("ProcessedWireYttriumBariumCuprate", 101, true, "gt.circuitcomponent.processed.wireybc", null, null);
        add("ProcessedWireLumiium", 102, true, "gt.circuitcomponent.processed.wirelumiium", null, null);
        add("ProcessedWireProtoHalkonite", 103, true, "gt.circuitcomponent.processed.wireprotohalkonite", null, null);
        add("ProcessedCableOpticalFiber", 105, true, "gt.circuitcomponent.cable.processed.opticalfiber", null, null);
        add("ProcessedWireInfinity", 106, true, "gt.circuitcomponent.processed.wireinfinity", null, null);
        add("ProcessedWireHypogen", 107, true, "gt.circuitcomponent.processed.wirehypogen", null, null);
        add("ProcessedWireMagMatter", 108, true, "gt.circuitcomponent.processed.wiremagmatter", null, null);
        add("AdvSMDResistor", 210, false, "gt.circuitcomponent.asmd.resistor", "item:advanced_smd_resistor", null);
        add("AdvSMDTransistor", 211, false, "gt.circuitcomponent.asmd.transistor", "item:advanced_smd_transistor", null);
        add("AdvSMDInductor", 212, false, "gt.circuitcomponent.asmd.inductor", "item:advanced_smd_inductor", null);
        add("AdvSMDCapacitor", 213, false, "gt.circuitcomponent.asmd.capacitor", "item:advanced_smd_capacitor", null);
        add("AdvSMDDiode", 214, false, "gt.circuitcomponent.asmd.diode", "item:advanced_smd_diode", null);
        add("OpticalSMDResistor", 220, false, "gt.circuitcomponent.xsmd.resistor", null, null);
        add("OpticalSMDTransistor", 221, false, "gt.circuitcomponent.xsmd.transistor", null, null);
        add("OpticalSMDInductor", 222, false, "gt.circuitcomponent.xsmd.inductor", null, null);
        add("OpticalSMDCapacitor", 223, false, "gt.circuitcomponent.xsmd.capacitor", null, null);
        add("OpticalSMDDiode", 224, false, "gt.circuitcomponent.xsmd.diode", null, null);
        add("ProcessedAdvSMDResistor", 310, true, "gt.circuitcomponent.processed.asmd.resistor", null, null);
        add("ProcessedAdvSMDTransistor", 311, true, "gt.circuitcomponent.processed.asmd.transistor", null, null);
        add("ProcessedAdvSMDInductor", 312, true, "gt.circuitcomponent.processed.asmd.inductor", null, null);
        add("ProcessedAdvSMDCapacitor", 313, true, "gt.circuitcomponent.processed.asmd.capacitor", null, null);
        add("ProcessedAdvSMDDiode", 314, true, "gt.circuitcomponent.processed.asmd.diode", null, null);
        add("ProcessedOpticalSMDResistor", 320, true, "gt.circuitcomponent.processed.xsmd.resistor", null, null);
        add("ProcessedOpticalSMDTransistor", 321, true, "gt.circuitcomponent.processed.xsmd.transistor", null, null);
        add("ProcessedOpticalSMDInductor", 322, true, "gt.circuitcomponent.processed.xsmd.inductor", null, null);
        add("ProcessedOpticalSMDCapacitor", 323, true, "gt.circuitcomponent.processed.xsmd.capacitor", null, null);
        add("ProcessedOpticalSMDDiode", 324, true, "gt.circuitcomponent.processed.xsmd.diode", null, null);
        add("BoardMultifiberglassElite", 400, false, "gt.circuitcomponent.board.multifiberelite", "item:multilayer_fiber_reinforced_circuit_board", null);
        add("BoardWetwareLifesupport", 401, false, "gt.circuitcomponent.board.wetwarelifesupport", "item:wetware_circuit_board", null);
        add("BoardBioMutated", 402, false, "gt.circuitcomponent.board.biomutated", null, null);
        add("BoardOptical", 403, false, "gt.circuitcomponent.board.optical", null, null);
        add("NeuroProcessingUnit", 404, false, "gt.circuitcomponent.neuroprocessingunit", "item:neuro_processing_unit", null);
        add("BioProcessingUnit", 405, false, "gt.circuitcomponent.bioprocessingunit", null, null);
        add("LivingBioChip", 406, false, "gt.circuitcomponent.livingbiochip", null, null);
        add("ProcessedBoardMultifiberglassElite", 500, true, "gt.circuitcomponent.processed.board.multifiberelite", null, null);
        add("ProcessedBoardWetwareLifesupport", 501, true, "gt.circuitcomponent.processed.board.wetwarelifesupport", null, null);
        add("ProcessedBoardBioMutated", 502, true, "gt.circuitcomponent.processed.board.biomutated", null, null);
        add("ProcessedBoardOptical", 503, true, "gt.circuitcomponent.processed.board.optical", null, null);
        add("ProcessedNeuroProcessingUnit", 504, true, "gt.circuitcomponent.processed.neuroprocessingunit", null, null);
        add("ProcessedBioProcessingUnit", 505, true, "gt.circuitcomponent.processed.board.bioprocessingunit", null, null);
        add("ProcessedLivingBioChip", 506, true, "gt.circuitcomponent.processed.board.livingbiochip", null, null);
        add("ChipNanoCPU", 600, false, "gt.circuitcomponent.chipNanoCPU", "item:nano_cpu_chip", null);
        add("ChipRAM", 601, false, "gt.circuitcomponent.chipram", "item:ram_chip", null);
        add("ChipNOR", 602, false, "gt.circuitcomponent.chipnor", "item:nor_memory_chip", null);
        add("ChipNAND", 603, false, "gt.circuitcomponent.chipnand", "item:nand_memory_chip", null);
        add("ChipASOC", 604, false, "gt.circuitcomponent.chipasoc", "item:advanced_soc", null);
        add("ChipPikoPIC", 605, false, "gt.circuitcomponent.chippikopic", null, null);
        add("ChipQuantumPIC", 606, false, "gt.circuitcomponent.chipquantumpic", null, null);
        add("ChipAttoPIC", 607, false, "gt.circuitcomponent.chipattopic", null, null);
        add("ChipZeptoPIC", 608, false, "gt.circuitcomponent.chipzeptopic", null, null);
        add("ChipYoctoPIC", 609, false, "gt.circuitcomponent.chipyoctopic", null, null);
        add("ChipPlanckPIC", 610, false, "gt.circuitcomponent.chipplanckpic", null, null);
        add("ProcessedChipNanoCPU", 700, true, "gt.circuitcomponent.processed.chipnanocpu", null, null);
        add("ProcessedChipRAM", 701, true, "gt.circuitcomponent.processed.chipram", null, null);
        add("ProcessedChipNOR", 702, true, "gt.circuitcomponent.processed.chipnor", null, null);
        add("ProcessedChipNAND", 703, true, "gt.circuitcomponent.processed.chipnand", null, null);
        add("ProcessedChipASOC", 704, true, "gt.circuitcomponent.processed.chipasoc", null, null);
        add("ProcessedChipPikoPIC", 705, true, "gt.circuitcomponent.processed.chippikopic", null, null);
        add("ProcessedChipQuantumPIC", 706, true, "gt.circuitcomponent.processed.chipquantumpic", null, null);
        add("ProcessedChipAttoPIC", 707, true, "gt.circuitcomponent.processed.chipattopic", null, null);
        add("ProcessedChipZeptoPIC", 708, true, "gt.circuitcomponent.processed.chipzeptopic", null, null);
        add("ProcessedChipYoctoPIC", 709, true, "gt.circuitcomponent.processed.chipyoctopic", null, null);
        add("ChipCrystalCPU", 800, false, "gt.circuitcomponent.chipcrystalcpu", "item:crystal_cpu", null);
        add("ChipCrystalSoC", 801, false, "gt.circuitcomponent.chipcrystalsoc", "item:crystal_soc", null);
        add("ChipLivingCrystal", 802, false, "gt.circuitcomponent.chiplivingcrystal", null, null);
        add("ChipRawAdvancedCrystal", 803, false, "gt.circuitcomponent.chiprawadvancedcrystal", "item:raw_crystal_chip", null);
        add("ProcessedChipCrystalCPU", 900, true, "gt.circuitcomponent.processed.chipcrystalcpu", null, null);
        add("ProcessedChipCrystalSoC", 901, true, "gt.circuitcomponent.processed.chipcrystalsoc", null, null);
        add("ProcessedChipLivingCrystal", 902, true, "gt.circuitcomponent.processed.chiplivingcrystal", null, null);
        add("ProcessedChipRawAdvancedCrystal", 903, true, "gt.circuitcomponent.processed.chiprawadvancedcrystal", null, null);
        add("ChipOpticalCPU", 1000, false, "gt.circuitcomponent.opticalcpu", null, null);
        add("OpticalRAM", 1001, false, "gt.circuitcomponent.opticalram", null, null);
        add("ProcessedChipOpticalCPU", 1100, true, "gt.circuitcomponent.processed.opticalcpu", null, null);
        add("ProcessedOpticalRAM", 1101, true, "gt.circuitcomponent.processed.opticalram", null, null);
        add("SuperconductorLuV", 1200, false, "gt.circuitcomponent.superconductorluv", null, null);
        add("SuperconductorZPM", 1201, false, "gt.circuitcomponent.superconductorzpm", null, null);
        add("SuperconductorUV", 1202, false, "gt.circuitcomponent.superconductoruv", null, null);
        add("SuperconductorUHV", 1203, false, "gt.circuitcomponent.superconductoruhv", null, null);
        add("SuperconductorUEV", 1204, false, "gt.circuitcomponent.superconductoruev", null, null);
        add("SuperconductorUIV", 1205, false, "gt.circuitcomponent.superconductoruiv", null, null);
        add("SuperconductorUMV", 1206, false, "gt.circuitcomponent.superconductorumv", null, null);
        add("ProcessedSuperconductorLuV", 1300, true, "gt.circuitcomponent.processed.superconductorluv", null, null);
        add("ProcessedSuperconductorZPM", 1301, true, "gt.circuitcomponent.processed.superconductorzpm", null, null);
        add("ProcessedSuperconductorUV", 1302, true, "gt.circuitcomponent.processed.superconductoruv", null, null);
        add("ProcessedSuperconductorUHV", 1303, true, "gt.circuitcomponent.processed.superconductoruhv", null, null);
        add("ProcessedSuperconductorUEV", 1304, true, "gt.circuitcomponent.processed.superconductoruev", null, null);
        add("ProcessedSuperconductorUIV", 1305, true, "gt.circuitcomponent.processed.superconductoruiv", null, null);
        add("ProcessedSuperconductorUMV", 1306, true, "gt.circuitcomponent.processed.superconductorumv", null, null);
        add("FrameboxAluminium", 1400, false, "gt.circuitcomponent.frame.aluminium", "mat:aluminium:frame", null);
        add("FrameboxTritanium", 1401, false, "gt.circuitcomponent.frame.tritanium", "mat:tritanium:frame", null);
        add("FrameboxCelestialTungsten", 1402, false, "gt.circuitcomponent.frame.celestialtungsten", null, null);
        add("FrameboxHypogen", 1403, false, "gt.circuitcomponent.frame.hypogen", null, null);
        add("FrameboxMagMatter", 1404, false, "gt.circuitcomponent.frame.magmatter", null, null);
        add("ProcessedFrameboxAluminium", 1500, true, "gt.circuitcomponent.processed.frame.aluminium", null, null);
        add("ProcessedFrameboxTritanium", 1501, true, "gt.circuitcomponent.processed.frame.tritanium", null, null);
        add("ProcessedFrameboxCelestialTungsten", 1502, true, "gt.circuitcomponent.processed.frame.celestialtungsten", null, null);
        add("ProcessedFrameboxHypogen", 1503, true, "gt.circuitcomponent.processed.frame.hypogen", null, null);
        add("ProcessedFrameboxMagMatter", 1504, true, "gt.circuitcomponent.processed.frame.magmatter", null, null);
        add("FoilSiliconeRubber", 1600, false, "gt.circuitcomponent.sheet.siliconerubber", "mat:silicone_rubber:foil", null);
        add("FoilPolybenzimidazole", 1601, false, "gt.circuitcomponent.sheet.polybenzimidazole", "mat:polybenzimidazole:foil", null);
        add("FoilRadoxPolymer", 1602, false, "gt.circuitcomponent.sheet.radoxpolymer", null, null);
        add("FoilShirabon", 1603, false, "gt.circuitcomponent.sheet.shirabon", null, null);
        add("FoilStyreneRubber", 1604, false, "gt.circuitcomponent.sheet.styrenebutadienerubber", "mat:styrene_butadiene_rubber:foil", null);
        add("FoilEternity", 1605, false, "gt.circuitcomponent.sheet.eternity", null, null);
        add("ProcessedFoilSiliconeRubber", 1700, true, "gt.circuitcomponent.sheet.processed.siliconerubber", null, null);
        add("ProcessedFoilPolybenzimidazole", 1701, true, "gt.circuitcomponent.sheet.processed.polybenzimidazole", null, null);
        add("ProcessedFoilRadoxPolymer", 1702, true, "gt.circuitcomponent.sheet.processed.radoxpolymer", null, null);
        add("ProcessedFoilShirabon", 1703, true, "gt.circuitcomponent.sheet.processed.shirabon", null, null);
        add("ProcessedFoilStyreneRubber", 1704, true, "gt.circuitcomponent.sheet.processed.styrenerubber", null, null);
        add("ProcessedFoilEternity", 1705, true, "gt.circuitcomponent.sheet.processed.eternity", null, null);
        add("ProcessedBasicMainframeCasing", 1800, true, "gt.circuitcomponent.casing.processed.basicmainframe", null, null);
        add("ProcessedAdvancedMainframeCasing", 1801, true, "gt.circuitcomponent.casing.processed.advancedmainframe", null, null);
        add("ProcessedCoiledThermalSuperconductor", 1805, true, "gt.circuitcomponent.other.processed.coiledthermalsuperconductor", null, null);
        add("BoltEnrichedHolmium", 1900, false, "gt.circuitcomponent.bolt.enrichedholmium", null, null);
        add("BoltTranscendentMetal", 1901, false, "gt.circuitcomponent.bolt.transcendentmetal", null, null);
        add("BoltYttriumBariumCuprate", 1902, false, "gt.circuitcomponent.bolt.yttriumbariumcuprate", "mat:yttrium_barium_cuprate:bolt", null);
        add("BoltCosmicNeutronium", 1903, false, "gt.circuitcomponent.bolt.cosmicneutronium", null, null);
        add("BoltChromaticGlass", 1904, false, "gt.circuitcomponent.bolt.chromaticglass", null, null);
        add("BoltUMVSuperconductor", 1905, false, "gt.circuitcomponent.bolt.umvsuperconductor", null, null);
        add("PlateMetastableOganesson", 1906, false, "gt.circuitcomponent.plate.metastableoganesson", null, null);
        add("ScrewAstralTitanium", 1907, false, "gt.circuitcomponent.screw.astraltitanium", null, null);
        add("CasingUEVSuperconductor", 1908, false, "gt.circuitcomponent.casing.uevsuperconductor", null, null);
        add("ScrewSixPhasedCopper", 1909, false, "gt.circuitcomponent.screw.sixphasedcopper", null, null);
        add("CasingCreon", 1910, false, "gt.circuitcomponent.casing.creon", null, null);
        add("PlateRhugnor", 1911, false, "gt.circuitcomponent.plate.rhugnor", null, null);
        add("BoltWhiteDwarfMatter", 1912, false, "gt.circuitcomponent.bolt.whitedwarfmatter", null, null);
        add("PlateHexanite", 1913, false, "gt.circuitcomponent.plate.hexanite", null, null);
        add("CasingBlackDwarfMatter", 1914, false, "gt.circuitcomponent.casing.blackdwarfmatter", null, null);
        add("CasingEternity", 1915, false, "gt.circuitcomponent.casing.eternity", null, null);
        add("ScrewUniversium", 1916, false, "gt.circuitcomponent.casing.universium", null, null);
        add("ThermalSuperconductor", 1917, false, "gt.circuitcomponent.thermalsuperconductor", null, null);
        add("ProcessedBoltEnrichedHolmium", 2000, true, "gt.circuitcomponent.bolt.processed.enrichedholmium", null, null);
        add("ProcessedBoltTranscendentMetal", 2001, true, "gt.circuitcomponent.bolt.processed.transcendentmetal", null, null);
        add("ProcessedBoltYttriumBariumCuprate", 2002, true, "gt.circuitcomponent.bolt.processed.yttriumbariumcuprate", null, null);
        add("ProcessedBoltCosmicNeutronium", 2003, true, "gt.circuitcomponent.bolt.processed.cosmicneutronium", null, null);
        add("ProcessedBoltChromaticGlass", 2004, true, "gt.circuitcomponent.bolt.processed.chromaticglass", null, null);
        add("ProcessedBoltUMVSuperconductor", 2005, true, "gt.circuitcomponent.bolt.processed.umvsuperconductor", null, null);
        add("ProcessedPlateMetastableOganesson", 2006, true, "gt.circuitcomponent.plate.processed.metastableoganesson", null, null);
        add("ProcessedPlateRhugnor", 2007, true, "gt.circuitcomponent.plate.processed.rhugnor", null, null);
        add("ProcessedBoltWhiteDwarfMatter", 2008, true, "gt.circuitcomponent.bolt.processed.whitedwarfmatter", null, null);
        add("ProcessedPlateHexanite", 2009, true, "gt.circuitcomponent.plate.processed.hexanite", null, null);
        add("CrystalProcessor", 2100, false, "gt.circuitcomponent.crystalprocessor", "item:crystal_processor", "CRYSTAL");
        add("CrystalAssembly", 2101, false, "gt.circuitcomponent.crystalasssembly", "item:crystal_processor_assembly", "CRYSTAL");
        add("CrystalComputer", 2102, false, "gt.circuitcomponent.crystalcomputer", "item:crystal_processor_computer", "CRYSTAL");
        add("CrystalMainframe", 2103, false, "gt.circuitcomponent.crystalmainframe", "item:crystal_processor_mainframe", "CRYSTAL");
        add("WetwareProcessor", 2104, false, "gt.circuitcomponent.wetwareprocessor", "item:wetware_processor", "WETWARE");
        add("WetwareAssembly", 2105, false, "gt.circuitcomponent.wetwareassembly", "item:wetware_processor_assembly", "WETWARE");
        add("WetwareComputer", 2106, false, "gt.circuitcomponent.wetwarecomputer", "item:wetware_processor_computer", "WETWARE");
        add("WetwareMainframe", 2107, false, "gt.circuitcomponent.wetwaremainframe", "item:wetware_processor_mainframe", "WETWARE");
        add("BiowareProcessor", 2108, false, "gt.circuitcomponent.biowareprocessor", null, "BIO");
        add("BiowareAssembly", 2109, false, "gt.circuitcomponent.biowareassembly", null, "BIO");
        add("BiowareComputer", 2110, false, "gt.circuitcomponent.biowarecomputer", null, "BIO");
        add("BiowareMainframe", 2111, false, "gt.circuitcomponent.biowaremainframe", null, "BIO");
        add("OpticalProcessor", 2112, false, "gt.circuitcomponent.opticalprocessor", null, "OPTICAL");
        add("OpticalAssembly", 2113, false, "gt.circuitcomponent.opticalassembly", null, "OPTICAL");
        add("OpticalComputer", 2114, false, "gt.circuitcomponent.opticalcomputer", null, "OPTICAL");
        add("OpticalMainframe", 2115, false, "gt.circuitcomponent.opticalmainframe", null, "OPTICAL");
        add("PolyetheretherketoneFoil", 2200, false, "gtlmodular.cc.cc_polyetheretherketone_foil.name", "mat:polyetheretherketone:foil", null);
        add("ProcessedPolyetheretherketoneFoil", 2201, true, "gtlmodular.cc.cc_processed_polyetheretherketone_foil.name", "mat:polyetheretherketone:foil", null);
        add("ExoticRamChip", 2202, false, "gtlmodular.cc.cc_exotic_ram_chip.name", "itemns:kubejs:exotic_ram_chip", "EXOTIC");
        add("ProcessedExoticRamChip", 2203, true, "gtlmodular.cc.cc_processed_exotic_ram_chip.name", "itemns:kubejs:exotic_ram_chip", "EXOTIC");
        add("HighlyAdvancedSoc", 2204, false, "gtlmodular.cc.cc_highly_advanced_soc.name", "item:highly_advanced_soc", null);
        add("ProcessedHighlyAdvancedSoc", 2205, true, "gtlmodular.cc.cc_processed_highly_advanced_soc.name", "item:highly_advanced_soc", null);
        add("SmdCapacitorBioware", 2206, false, "gtlmodular.cc.cc_smd_capacitor_bioware.name", "itemns:kubejs:smd_capacitor_bioware", "BIOWARE");
        add("ProcessedSmdCapacitorBioware", 2207, true, "gtlmodular.cc.cc_processed_smd_capacitor_bioware.name", "itemns:kubejs:smd_capacitor_bioware", "BIOWARE");
        add("SmdTransistorBioware", 2208, false, "gtlmodular.cc.cc_smd_transistor_bioware.name", "itemns:kubejs:smd_transistor_bioware", "BIOWARE");
        add("ProcessedSmdTransistorBioware", 2209, true, "gtlmodular.cc.cc_processed_smd_transistor_bioware.name", "itemns:kubejs:smd_transistor_bioware", "BIOWARE");
        add("SmdCapacitorExotic", 2210, false, "gtlmodular.cc.cc_smd_capacitor_exotic.name", "itemns:kubejs:smd_capacitor_exotic", "EXOTIC");
        add("ProcessedSmdCapacitorExotic", 2211, true, "gtlmodular.cc.cc_processed_smd_capacitor_exotic.name", "itemns:kubejs:smd_capacitor_exotic", "EXOTIC");
        add("SmdTransistorExotic", 2212, false, "gtlmodular.cc.cc_smd_transistor_exotic.name", "itemns:kubejs:smd_transistor_exotic", "EXOTIC");
        add("ProcessedSmdTransistorExotic", 2213, true, "gtlmodular.cc.cc_processed_smd_transistor_exotic.name", "itemns:kubejs:smd_transistor_exotic", "EXOTIC");
        add("FineCinobiteWire", 2214, false, "gtlmodular.cc.cc_fine_cinobite_wire.name", "mat:cinobite:fine_wire", null);
        add("ProcessedFineCinobiteWire", 2215, true, "gtlmodular.cc.cc_processed_fine_cinobite_wire.name", "mat:cinobite:fine_wire", null);
        add("UnstableStar", 2216, false, "gtlmodular.cc.cc_unstable_star.name", "itemns:kubejs:unstable_star", null);
        add("ProcessedUnstableStar", 2217, true, "gtlmodular.cc.cc_processed_unstable_star.name", "itemns:kubejs:unstable_star", null);
        add("CosmicRamChip", 2218, false, "gtlmodular.cc.cc_cosmic_ram_chip.name", "itemns:kubejs:cosmic_ram_chip", "COSMIC");
        add("ProcessedCosmicRamChip", 2219, true, "gtlmodular.cc.cc_processed_cosmic_ram_chip.name", "itemns:kubejs:cosmic_ram_chip", "COSMIC");
        add("SmdCapacitorCosmic", 2220, false, "gtlmodular.cc.cc_smd_capacitor_cosmic.name", "itemns:kubejs:smd_capacitor_cosmic", "COSMIC");
        add("ProcessedSmdCapacitorCosmic", 2221, true, "gtlmodular.cc.cc_processed_smd_capacitor_cosmic.name", "itemns:kubejs:smd_capacitor_cosmic", "COSMIC");
        add("SmdTransistorCosmic", 2222, false, "gtlmodular.cc.cc_smd_transistor_cosmic.name", "itemns:kubejs:smd_transistor_cosmic", "COSMIC");
        add("ProcessedSmdTransistorCosmic", 2223, true, "gtlmodular.cc.cc_processed_smd_transistor_cosmic.name", "itemns:kubejs:smd_transistor_cosmic", "COSMIC");
        add("FineNaquadahWire", 2224, false, "gtlmodular.cc.cc_fine_naquadah_wire.name", "mat:naquadah:fine_wire", null);
        add("ProcessedFineNaquadahWire", 2225, true, "gtlmodular.cc.cc_processed_fine_naquadah_wire.name", "mat:naquadah:fine_wire", null);
        add("SmdDiodeBioware", 2226, false, "gtlmodular.cc.cc_smd_diode_bioware.name", "itemns:kubejs:smd_diode_bioware", "BIOWARE");
        add("ProcessedSmdDiodeBioware", 2227, true, "gtlmodular.cc.cc_processed_smd_diode_bioware.name", "itemns:kubejs:smd_diode_bioware", "BIOWARE");
        add("SmdResistorBioware", 2228, false, "gtlmodular.cc.cc_smd_resistor_bioware.name", "itemns:kubejs:smd_resistor_bioware", "BIOWARE");
        add("ProcessedSmdResistorBioware", 2229, true, "gtlmodular.cc.cc_processed_smd_resistor_bioware.name", "itemns:kubejs:smd_resistor_bioware", "BIOWARE");
        add("SmdInductorBioware", 2230, false, "gtlmodular.cc.cc_smd_inductor_bioware.name", "itemns:kubejs:smd_inductor_bioware", "BIOWARE");
        add("ProcessedSmdInductorBioware", 2231, true, "gtlmodular.cc.cc_processed_smd_inductor_bioware.name", "itemns:kubejs:smd_inductor_bioware", "BIOWARE");
        add("GraviStar", 2232, false, "gtlmodular.cc.cc_gravi_star.name", "item:gravi_star", null);
        add("ProcessedGraviStar", 2233, true, "gtlmodular.cc.cc_processed_gravi_star.name", "item:gravi_star", null);
        add("FineDubniumWire", 2234, false, "gtlmodular.cc.cc_fine_dubnium_wire.name", "mat:dubnium:fine_wire", null);
        add("ProcessedFineDubniumWire", 2235, true, "gtlmodular.cc.cc_processed_fine_dubnium_wire.name", "mat:dubnium:fine_wire", null);
        add("SmdDiodeExotic", 2236, false, "gtlmodular.cc.cc_smd_diode_exotic.name", "itemns:kubejs:smd_diode_exotic", "EXOTIC");
        add("ProcessedSmdDiodeExotic", 2237, true, "gtlmodular.cc.cc_processed_smd_diode_exotic.name", "itemns:kubejs:smd_diode_exotic", "EXOTIC");
        add("SmdResistorExotic", 2238, false, "gtlmodular.cc.cc_smd_resistor_exotic.name", "itemns:kubejs:smd_resistor_exotic", "EXOTIC");
        add("ProcessedSmdResistorExotic", 2239, true, "gtlmodular.cc.cc_processed_smd_resistor_exotic.name", "itemns:kubejs:smd_resistor_exotic", "EXOTIC");
        add("SmdInductorExotic", 2240, false, "gtlmodular.cc.cc_smd_inductor_exotic.name", "itemns:kubejs:smd_inductor_exotic", "EXOTIC");
        add("ProcessedSmdInductorExotic", 2241, true, "gtlmodular.cc.cc_processed_smd_inductor_exotic.name", "itemns:kubejs:smd_inductor_exotic", "EXOTIC");
        add("SmdDiodeCosmic", 2242, false, "gtlmodular.cc.cc_smd_diode_cosmic.name", "itemns:kubejs:smd_diode_cosmic", "COSMIC");
        add("ProcessedSmdDiodeCosmic", 2243, true, "gtlmodular.cc.cc_processed_smd_diode_cosmic.name", "itemns:kubejs:smd_diode_cosmic", "COSMIC");
        add("SmdResistorCosmic", 2244, false, "gtlmodular.cc.cc_smd_resistor_cosmic.name", "itemns:kubejs:smd_resistor_cosmic", "COSMIC");
        add("ProcessedSmdResistorCosmic", 2245, true, "gtlmodular.cc.cc_processed_smd_resistor_cosmic.name", "itemns:kubejs:smd_resistor_cosmic", "COSMIC");
        add("SmdInductorCosmic", 2246, false, "gtlmodular.cc.cc_smd_inductor_cosmic.name", "itemns:kubejs:smd_inductor_cosmic", "COSMIC");
        add("ProcessedSmdInductorCosmic", 2247, true, "gtlmodular.cc.cc_processed_smd_inductor_cosmic.name", "itemns:kubejs:smd_inductor_cosmic", "COSMIC");
        add("SmdCapacitorSupracausal", 2248, false, "gtlmodular.cc.cc_smd_capacitor_supracausal.name", "itemns:kubejs:smd_capacitor_supracausal", "SUPRACAUSAL");
        add("ProcessedSmdCapacitorSupracausal", 2249, true, "gtlmodular.cc.cc_processed_smd_capacitor_supracausal.name", "itemns:kubejs:smd_capacitor_supracausal", "SUPRACAUSAL");
        add("SmdDiodeSupracausal", 2250, false, "gtlmodular.cc.cc_smd_diode_supracausal.name", "itemns:kubejs:smd_diode_supracausal", "SUPRACAUSAL");
        add("ProcessedSmdDiodeSupracausal", 2251, true, "gtlmodular.cc.cc_processed_smd_diode_supracausal.name", "itemns:kubejs:smd_diode_supracausal", "SUPRACAUSAL");
        add("SmdResistorSupracausal", 2252, false, "gtlmodular.cc.cc_smd_resistor_supracausal.name", "itemns:kubejs:smd_resistor_supracausal", "SUPRACAUSAL");
        add("ProcessedSmdResistorSupracausal", 2253, true, "gtlmodular.cc.cc_processed_smd_resistor_supracausal.name", "itemns:kubejs:smd_resistor_supracausal", "SUPRACAUSAL");
        add("SmdTransistorSupracausal", 2254, false, "gtlmodular.cc.cc_smd_transistor_supracausal.name", "itemns:kubejs:smd_transistor_supracausal", "SUPRACAUSAL");
        add("ProcessedSmdTransistorSupracausal", 2255, true, "gtlmodular.cc.cc_processed_smd_transistor_supracausal.name", "itemns:kubejs:smd_transistor_supracausal", "SUPRACAUSAL");
        add("SmdInductorSupracausal", 2256, false, "gtlmodular.cc.cc_smd_inductor_supracausal.name", "itemns:kubejs:smd_inductor_supracausal", "SUPRACAUSAL");
        add("ProcessedSmdInductorSupracausal", 2257, true, "gtlmodular.cc.cc_processed_smd_inductor_supracausal.name", "itemns:kubejs:smd_inductor_supracausal", "SUPRACAUSAL");
        add("UhpicChip", 2258, false, "gtlmodular.cc.cc_uhpic_chip.name", "item:uhpic_chip", null);
        add("ProcessedUhpicChip", 2259, true, "gtlmodular.cc.cc_processed_uhpic_chip.name", "item:uhpic_chip", null);
        add("NmChip", 2260, false, "gtlmodular.cc.cc_nm_chip.name", "itemns:kubejs:nm_chip", null);
        add("ProcessedNmChip", 2261, true, "gtlmodular.cc.cc_processed_nm_chip.name", "itemns:kubejs:nm_chip", null);
        add("ExoticPrintedCircuitBoard", 2262, false, "gtlmodular.cc.cc_exotic_printed_circuit_board.name", "itemns:kubejs:exotic_printed_circuit_board", "EXOTIC");
        add("ProcessedExoticPrintedCircuitBoard", 2263, true, "gtlmodular.cc.cc_processed_exotic_printed_circuit_board.name", "itemns:kubejs:exotic_printed_circuit_board", "EXOTIC");
        add("CosmicPrintedCircuitBoard", 2264, false, "gtlmodular.cc.cc_cosmic_printed_circuit_board.name", "itemns:kubejs:cosmic_printed_circuit_board", "COSMIC");
        add("ProcessedCosmicPrintedCircuitBoard", 2265, true, "gtlmodular.cc.cc_processed_cosmic_printed_circuit_board.name", "itemns:kubejs:cosmic_printed_circuit_board", "COSMIC");
        add("FmChip", 2266, false, "gtlmodular.cc.cc_fm_chip.name", "itemns:kubejs:fm_chip", null);
        add("ProcessedFmChip", 2267, true, "gtlmodular.cc.cc_processed_fm_chip.name", "itemns:kubejs:fm_chip", null);
        add("FullerenePolymerMatrixPulpFoil", 2268, false, "gtlmodular.cc.cc_fullerene_polymer_matrix_pulp_foil.name", "mat:fullerene_polymer_matrix_pulp:foil", null);
        add("ProcessedFullerenePolymerMatrixPulpFoil", 2269, true, "gtlmodular.cc.cc_processed_fullerene_polymer_matrix_pulp_foil.name", "mat:fullerene_polymer_matrix_pulp:foil", null);
        add("SupracausalRamChip", 2270, false, "gtlmodular.cc.cc_supracausal_ram_chip.name", "itemns:kubejs:supracausal_ram_chip", "SUPRACAUSAL");
        add("ProcessedSupracausalRamChip", 2271, true, "gtlmodular.cc.cc_processed_supracausal_ram_chip.name", "itemns:kubejs:supracausal_ram_chip", "SUPRACAUSAL");
        add("CosmicProcessingUnitCore", 2272, false, "gtlmodular.cc.cc_cosmic_processing_unit_core.name", "itemns:kubejs:cosmic_processing_unit_core", "COSMIC");
        add("ProcessedCosmicProcessingUnitCore", 2273, true, "gtlmodular.cc.cc_processed_cosmic_processing_unit_core.name", "itemns:kubejs:cosmic_processing_unit_core", "COSMIC");
        add("SupracausalPrintedCircuitBoard", 2274, false, "gtlmodular.cc.cc_supracausal_printed_circuit_board.name", "itemns:kubejs:supracausal_printed_circuit_board", "SUPRACAUSAL");
        add("ProcessedSupracausalPrintedCircuitBoard", 2275, true, "gtlmodular.cc.cc_processed_supracausal_printed_circuit_board.name", "itemns:kubejs:supracausal_printed_circuit_board", "SUPRACAUSAL");
        add("TriniumTitaniumPlate", 2276, false, "gtlmodular.cc.cc_trinium_titanium_plate.name", "mat:trinium_titanium:plate", null);
        add("ProcessedTriniumTitaniumPlate", 2277, true, "gtlmodular.cc.cc_processed_trinium_titanium_plate.name", "mat:trinium_titanium:plate", null);
        add("NuclearStar", 2278, false, "gtlmodular.cc.cc_nuclear_star.name", "itemns:kubejs:nuclear_star", null);
        add("ProcessedNuclearStar", 2279, true, "gtlmodular.cc.cc_processed_nuclear_star.name", "itemns:kubejs:nuclear_star", null);
        add("QbitCpuChip", 2280, false, "gtlmodular.cc.cc_qbit_cpu_chip.name", "item:qbit_cpu_chip", null);
        add("ProcessedQbitCpuChip", 2281, true, "gtlmodular.cc.cc_processed_qbit_cpu_chip.name", "item:qbit_cpu_chip", null);
        add("DuraniumPlate", 2282, false, "gtlmodular.cc.cc_duranium_plate.name", "mat:duranium:plate", null);
        add("ProcessedDuraniumPlate", 2283, true, "gtlmodular.cc.cc_processed_duranium_plate.name", "mat:duranium:plate", null);
        add("NeutroniumPlate", 2284, false, "gtlmodular.cc.cc_neutronium_plate.name", "mat:neutronium:plate", null);
        add("ProcessedNeutroniumPlate", 2285, true, "gtlmodular.cc.cc_processed_neutronium_plate.name", "mat:neutronium:plate", null);
        add("RutheniumTriniumAmericiumNeutronateDoubleWire", 2286, false, "gtlmodular.cc.cc_ruthenium_trinium_americium_neutronate_double_wire.name", "mat:ruthenium_trinium_americium_neutronate:wire_gt_double", null);
        add("ProcessedRutheniumTriniumAmericiumNeutronateDoubleWire", 2287, true, "gtlmodular.cc.cc_processed_ruthenium_trinium_americium_neutronate_double_wire.name", "mat:ruthenium_trinium_americium_neutronate:wire_gt_double", null);
        add("MithrilPlate", 2288, false, "gtlmodular.cc.cc_mithril_plate.name", "mat:mithril:plate", null);
        add("ProcessedMithrilPlate", 2289, true, "gtlmodular.cc.cc_processed_mithril_plate.name", "mat:mithril:plate", null);
        add("HighurabilityompoundteelPlate", 2290, false, "gtlmodular.cc.cc_highurabilityompoundteel_plate.name", "mat:highurabilityompoundteel:plate", null);
        add("ProcessedHighurabilityompoundteelPlate", 2291, true, "gtlmodular.cc.cc_processed_highurabilityompoundteel_plate.name", "mat:highurabilityompoundteel:plate", null);
        add("SeaborgiumPlate", 2292, false, "gtlmodular.cc.cc_seaborgium_plate.name", "mat:seaborgium:plate", null);
        add("ProcessedSeaborgiumPlate", 2293, true, "gtlmodular.cc.cc_processed_seaborgium_plate.name", "mat:seaborgium:plate", null);
        add("QuantaniumFrame", 2294, false, "gtlmodular.cc.cc_quantanium_frame.name", "mat:quantanium:frame", null);
        add("ProcessedQuantaniumFrame", 2295, true, "gtlmodular.cc.cc_processed_quantanium_frame.name", "mat:quantanium:frame", null);
        add("EnderiteDoubleWire", 2296, false, "gtlmodular.cc.cc_enderite_double_wire.name", "mat:enderite:wire_gt_double", null);
        add("ProcessedEnderiteDoubleWire", 2297, true, "gtlmodular.cc.cc_processed_enderite_double_wire.name", "mat:enderite:wire_gt_double", null);
        add("TitansteelPlate", 2298, false, "gtlmodular.cc.cc_titansteel_plate.name", "mat:titansteel:plate", null);
        add("ProcessedTitansteelPlate", 2299, true, "gtlmodular.cc.cc_processed_titansteel_plate.name", "mat:titansteel:plate", null);
        add("ExoticProcessingCore", 2300, false, "gtlmodular.cc.cc_exotic_processing_core.name", "itemns:kubejs:exotic_processing_core", "EXOTIC");
        add("ProcessedExoticProcessingCore", 2301, true, "gtlmodular.cc.cc_processed_exotic_processing_core.name", "itemns:kubejs:exotic_processing_core", "EXOTIC");
        add("ExoticProcessor", 2302, false, "gtlmodular.cc.cc_exotic_processor.name", "itemns:kubejs:exotic_processor", "EXOTIC");
        add("DraconicCore", 2303, false, "gtlmodular.cc.cc_draconic_core.name", "itemns:kubejs:draconic_core", null);
        add("ProcessedDraconicCore", 2304, true, "gtlmodular.cc.cc_processed_draconic_core.name", "itemns:kubejs:draconic_core", null);
        add("InfuscoliumPlate", 2305, false, "gtlmodular.cc.cc_infuscolium_plate.name", "mat:infuscolium:plate", null);
        add("ProcessedInfuscoliumPlate", 2306, true, "gtlmodular.cc.cc_processed_infuscolium_plate.name", "mat:infuscolium:plate", null);
        add("ExoticAssembly", 2307, false, "gtlmodular.cc.cc_exotic_assembly.name", "itemns:kubejs:exotic_assembly", "EXOTIC");
        add("AdamantinePlate", 2308, false, "gtlmodular.cc.cc_adamantine_plate.name", "mat:adamantine:plate", null);
        add("ProcessedAdamantinePlate", 2309, true, "gtlmodular.cc.cc_processed_adamantine_plate.name", "mat:adamantine:plate", null);
        add("AdamantiumFrame", 2310, false, "gtlmodular.cc.cc_adamantium_frame.name", "mat:adamantium:frame", null);
        add("ProcessedAdamantiumFrame", 2311, true, "gtlmodular.cc.cc_processed_adamantium_frame.name", "mat:adamantium:frame", null);
        add("ExoticComputer", 2312, false, "gtlmodular.cc.cc_exotic_computer.name", "itemns:kubejs:exotic_computer", "EXOTIC");
        add("DataStick", 2313, false, "gtlmodular.cc.cc_data_stick.name", "item:data_stick", null);
        add("ProcessedDataStick", 2314, true, "gtlmodular.cc.cc_processed_data_stick.name", "item:data_stick", null);
        add("EchoiteDoubleWire", 2315, false, "gtlmodular.cc.cc_echoite_double_wire.name", "mat:echoite:wire_gt_double", null);
        add("ProcessedEchoiteDoubleWire", 2316, true, "gtlmodular.cc.cc_processed_echoite_double_wire.name", "mat:echoite:wire_gt_double", null);
        add("BlackTitaniumPlate", 2317, false, "gtlmodular.cc.cc_black_titanium_plate.name", "mat:black_titanium:plate", null);
        add("ProcessedBlackTitaniumPlate", 2318, true, "gtlmodular.cc.cc_processed_black_titanium_plate.name", "mat:black_titanium:plate", null);
        add("DoubleTechnetiumPlate", 2319, false, "gtlmodular.cc.cc_double_technetium_plate.name", "mat:technetium:double_plate", null);
        add("ProcessedDoubleTechnetiumPlate", 2320, true, "gtlmodular.cc.cc_processed_double_technetium_plate.name", "mat:technetium:double_plate", null);
        add("CosmicProcessingCore", 2321, false, "gtlmodular.cc.cc_cosmic_processing_core.name", "itemns:kubejs:cosmic_processing_core", "COSMIC");
        add("ProcessedCosmicProcessingCore", 2322, true, "gtlmodular.cc.cc_processed_cosmic_processing_core.name", "itemns:kubejs:cosmic_processing_core", "COSMIC");
        add("CosmicProcessor", 2323, false, "gtlmodular.cc.cc_cosmic_processor.name", "itemns:kubejs:cosmic_processor", "COSMIC");
        add("WyvernCore", 2324, false, "gtlmodular.cc.cc_wyvern_core.name", "itemns:kubejs:wyvern_core", null);
        add("ProcessedWyvernCore", 2325, true, "gtlmodular.cc.cc_processed_wyvern_core.name", "itemns:kubejs:wyvern_core", null);
        add("ZylonFoil", 2326, false, "gtlmodular.cc.cc_zylon_foil.name", "mat:zylon:foil", null);
        add("ProcessedZylonFoil", 2327, true, "gtlmodular.cc.cc_processed_zylon_foil.name", "mat:zylon:foil", null);
        add("CinobitePlate", 2328, false, "gtlmodular.cc.cc_cinobite_plate.name", "mat:cinobite:plate", null);
        add("ProcessedCinobitePlate", 2329, true, "gtlmodular.cc.cc_processed_cinobite_plate.name", "mat:cinobite:plate", null);
        add("CosmicAssembly", 2330, false, "gtlmodular.cc.cc_cosmic_assembly.name", "itemns:kubejs:cosmic_assembly", "COSMIC");
        add("DoubleTriniumTitaniumPlate", 2331, false, "gtlmodular.cc.cc_double_trinium_titanium_plate.name", "mat:trinium_titanium:double_plate", null);
        add("ProcessedDoubleTriniumTitaniumPlate", 2332, true, "gtlmodular.cc.cc_processed_double_trinium_titanium_plate.name", "mat:trinium_titanium:double_plate", null);
        add("VibraniumFrame", 2333, false, "gtlmodular.cc.cc_vibranium_frame.name", "mat:vibranium:frame", null);
        add("ProcessedVibraniumFrame", 2334, true, "gtlmodular.cc.cc_processed_vibranium_frame.name", "mat:vibranium:frame", null);
        add("CosmicComputer", 2335, false, "gtlmodular.cc.cc_cosmic_computer.name", "itemns:kubejs:cosmic_computer", "COSMIC");
        add("DataOrb", 2336, false, "gtlmodular.cc.cc_data_orb.name", "item:data_orb", null);
        add("ProcessedDataOrb", 2337, true, "gtlmodular.cc.cc_processed_data_orb.name", "item:data_orb", null);
        add("LegendariumDoubleWire", 2338, false, "gtlmodular.cc.cc_legendarium_double_wire.name", "mat:legendarium:wire_gt_double", null);
        add("ProcessedLegendariumDoubleWire", 2339, true, "gtlmodular.cc.cc_processed_legendarium_double_wire.name", "mat:legendarium:wire_gt_double", null);
        add("ProtoMatter", 2340, false, "gtlmodular.cc.cc_proto_matter.name", "itemns:kubejs:proto_matter", null);
        add("ProcessedProtoMatter", 2341, true, "gtlmodular.cc.cc_processed_proto_matter.name", "itemns:kubejs:proto_matter", null);
        add("DoubleNaquadriatictaraniumPlate", 2342, false, "gtlmodular.cc.cc_double_naquadriatictaranium_plate.name", "mat:naquadriatictaranium:double_plate", null);
        add("ProcessedDoubleNaquadriatictaraniumPlate", 2343, true, "gtlmodular.cc.cc_processed_double_naquadriatictaranium_plate.name", "mat:naquadriatictaranium:double_plate", null);
        add("SupracausalProcessingCore", 2344, false, "gtlmodular.cc.cc_supracausal_processing_core.name", "itemns:kubejs:supracausal_processing_core", "SUPRACAUSAL");
        add("ProcessedSupracausalProcessingCore", 2345, true, "gtlmodular.cc.cc_processed_supracausal_processing_core.name", "itemns:kubejs:supracausal_processing_core", "SUPRACAUSAL");
        add("MicrowormholeGenerator", 2346, false, "gtlmodular.cc.cc_microwormhole_generator.name", "itemns:kubejs:microwormhole_generator", null);
        add("ProcessedMicrowormholeGenerator", 2347, true, "gtlmodular.cc.cc_processed_microwormhole_generator.name", "itemns:kubejs:microwormhole_generator", null);
        add("ManifoldOscillatoryPowerCell", 2348, false, "gtlmodular.cc.cc_manifold_oscillatory_power_cell.name", "itemns:kubejs:manifold_oscillatory_power_cell", null);
        add("ProcessedManifoldOscillatoryPowerCell", 2349, true, "gtlmodular.cc.cc_processed_manifold_oscillatory_power_cell.name", "itemns:kubejs:manifold_oscillatory_power_cell", null);
        add("CrystalmatrixPlate", 2350, false, "gtlmodular.cc.cc_crystalmatrix_plate.name", "mat:crystalmatrix:plate", null);
        add("ProcessedCrystalmatrixPlate", 2351, true, "gtlmodular.cc.cc_processed_crystalmatrix_plate.name", "mat:crystalmatrix:plate", null);
        add("SupracausalProcessor", 2352, false, "gtlmodular.cc.cc_supracausal_processor.name", "itemns:kubejs:supracausal_processor", "SUPRACAUSAL");
        add("AwakenedCore", 2353, false, "gtlmodular.cc.cc_awakened_core.name", "itemns:kubejs:awakened_core", null);
        add("ProcessedAwakenedCore", 2354, true, "gtlmodular.cc.cc_processed_awakened_core.name", "itemns:kubejs:awakened_core", null);
        add("FineHeavyQuarkDegenerateMatterWire", 2355, false, "gtlmodular.cc.cc_fine_heavy_quark_degenerate_matter_wire.name", "mat:heavy_quark_degenerate_matter:fine_wire", null);
        add("ProcessedFineHeavyQuarkDegenerateMatterWire", 2356, true, "gtlmodular.cc.cc_processed_fine_heavy_quark_degenerate_matter_wire.name", "mat:heavy_quark_degenerate_matter:fine_wire", null);
        add("OpticalSoc", 2357, false, "gtlmodular.cc.cc_optical_soc.name", "itemns:kubejs:optical_soc", "OPTICAL");
        add("ProcessedOpticalSoc", 2358, true, "gtlmodular.cc.cc_processed_optical_soc.name", "itemns:kubejs:optical_soc", "OPTICAL");
        add("RecursivelyFoldedNegativeSpace", 2359, false, "gtlmodular.cc.cc_recursively_folded_negative_space.name", "itemns:kubejs:recursively_folded_negative_space", null);
        add("ProcessedRecursivelyFoldedNegativeSpace", 2360, true, "gtlmodular.cc.cc_processed_recursively_folded_negative_space.name", "itemns:kubejs:recursively_folded_negative_space", null);
        add("ContainedExoticMatter", 2361, false, "gtlmodular.cc.cc_contained_exotic_matter.name", "itemns:kubejs:contained_exotic_matter", "EXOTIC");
        add("ProcessedContainedExoticMatter", 2362, true, "gtlmodular.cc.cc_processed_contained_exotic_matter.name", "itemns:kubejs:contained_exotic_matter", "EXOTIC");
        add("SupracausalAssembly", 2363, false, "gtlmodular.cc.cc_supracausal_assembly.name", "itemns:kubejs:supracausal_assembly", "SUPRACAUSAL");
        add("DataModule", 2364, false, "gtlmodular.cc.cc_data_module.name", "item:data_module", null);
        add("ProcessedDataModule", 2365, true, "gtlmodular.cc.cc_processed_data_module.name", "item:data_module", null);
        add("StarmetalPlate", 2366, false, "gtlmodular.cc.cc_starmetal_plate.name", "mat:starmetal:plate", null);
        add("ProcessedStarmetalPlate", 2367, true, "gtlmodular.cc.cc_processed_starmetal_plate.name", "mat:starmetal:plate", null);
        add("EigenfoldedKerrManifold", 2368, false, "gtlmodular.cc.cc_eigenfolded_kerr_manifold.name", "itemns:kubejs:eigenfolded_kerr_manifold", null);
        add("ProcessedEigenfoldedKerrManifold", 2369, true, "gtlmodular.cc.cc_processed_eigenfolded_kerr_manifold.name", "itemns:kubejs:eigenfolded_kerr_manifold", null);
        add("CosmicneutroniumPlate", 2370, false, "gtlmodular.cc.cc_cosmicneutronium_plate.name", "mat:cosmicneutronium:plate", "COSMIC");
        add("ProcessedCosmicneutroniumPlate", 2371, true, "gtlmodular.cc.cc_processed_cosmicneutronium_plate.name", "mat:cosmicneutronium:plate", "COSMIC");
        add("SupracausalComputer", 2372, false, "gtlmodular.cc.cc_supracausal_computer.name", "itemns:kubejs:supracausal_computer", "SUPRACAUSAL");
        add("RadoxPlate", 2373, false, "gtlmodular.cc.cc_radox_plate.name", "mat:radox:plate", null);
        add("ProcessedRadoxPlate", 2374, true, "gtlmodular.cc.cc_processed_radox_plate.name", "mat:radox:plate", null);
        add("DraconiumawakenedDoubleWire", 2375, false, "gtlmodular.cc.cc_draconiumawakened_double_wire.name", "mat:draconiumawakened:wire_gt_double", null);
        add("ProcessedDraconiumawakenedDoubleWire", 2376, true, "gtlmodular.cc.cc_processed_draconiumawakened_double_wire.name", "mat:draconiumawakened:wire_gt_double", null);
        add("CosmicFabric", 2377, false, "gtlmodular.cc.cc_cosmic_fabric.name", "itemns:kubejs:cosmic_fabric", "COSMIC");
        add("ProcessedCosmicFabric", 2378, true, "gtlmodular.cc.cc_processed_cosmic_fabric.name", "itemns:kubejs:cosmic_fabric", "COSMIC");
        add("UltrashortPulseLaser", 2379, false, "gtlmodular.cc.cc_ultrashort_pulse_laser.name", "itemns:kubejs:ultrashort_pulse_laser", null);
        add("ProcessedUltrashortPulseLaser", 2380, true, "gtlmodular.cc.cc_processed_ultrashort_pulse_laser.name", "itemns:kubejs:ultrashort_pulse_laser", null);
        add("CtcComputationalUnit", 2381, false, "gtlmodular.cc.cc_ctc_computational_unit.name", "itemns:kubejs:ctc_computational_unit", null);
        add("ProcessedCtcComputationalUnit", 2382, true, "gtlmodular.cc.cc_processed_ctc_computational_unit.name", "itemns:kubejs:ctc_computational_unit", null);
        add("QuantumchromodynamicallyConfinedMatterPlate", 2383, false, "gtlmodular.cc.cc_quantumchromodynamically_confined_matter_plate.name", "mat:quantumchromodynamically_confined_matter:plate", null);
        add("ProcessedQuantumchromodynamicallyConfinedMatterPlate", 2384, true, "gtlmodular.cc.cc_processed_quantumchromodynamically_confined_matter_plate.name", "mat:quantumchromodynamically_confined_matter:plate", null);
        add("HsseFrame", 2385, false, "gtlmodular.cc.cc_hsse_frame.name", "mat:hsse:frame", null);
        add("ProcessedHsseFrame", 2386, true, "gtlmodular.cc.cc_processed_hsse_frame.name", "mat:hsse:frame", null);
        add("HpicChip", 2387, false, "gtlmodular.cc.cc_hpic_chip.name", "item:hpic_chip", null);
        add("ProcessedHpicChip", 2388, true, "gtlmodular.cc.cc_processed_hpic_chip.name", "item:hpic_chip", null);
        add("NiobiumTitaniumSingleWire", 2389, false, "gtlmodular.cc.cc_niobium_titanium_single_wire.name", "mat:niobium_titanium:wire_gt_single", null);
        add("ProcessedNiobiumTitaniumSingleWire", 2390, true, "gtlmodular.cc.cc_processed_niobium_titanium_single_wire.name", "mat:niobium_titanium:wire_gt_single", null);
        add("NaquadahBolt", 2391, false, "gtlmodular.cc.cc_naquadah_bolt.name", "mat:naquadah:bolt", null);
        add("ProcessedNaquadahBolt", 2392, true, "gtlmodular.cc.cc_processed_naquadah_bolt.name", "mat:naquadah:bolt", null);
        add("EuropiumPlate", 2393, false, "gtlmodular.cc.cc_europium_plate.name", "mat:europium:plate", null);
        add("ProcessedEuropiumPlate", 2394, true, "gtlmodular.cc.cc_processed_europium_plate.name", "mat:europium:plate", null);
        add("EnrichedNaquadahTriniumEuropiumDuranideDoubleWire", 2395, false, "gtlmodular.cc.cc_enriched_naquadah_trinium_europium_duranide_double_wire.name", "mat:enriched_naquadah_trinium_europium_duranide:wire_gt_double", null);
        add("ProcessedEnrichedNaquadahTriniumEuropiumDuranideDoubleWire", 2396, true, "gtlmodular.cc.cc_processed_enriched_naquadah_trinium_europium_duranide_double_wire.name", "mat:enriched_naquadah_trinium_europium_duranide:wire_gt_double", null);
        add("ExoticMainframe", 2397, false, "gtlmodular.cc.cc_exotic_mainframe.name", "itemns:kubejs:exotic_mainframe", "EXOTIC");
        add("CosmicMainframe", 2398, false, "gtlmodular.cc.cc_cosmic_mainframe.name", "itemns:kubejs:cosmic_mainframe", "COSMIC");
        add("SupracausalMainframe", 2399, false, "gtlmodular.cc.cc_supracausal_mainframe.name", "itemns:kubejs:supracausal_mainframe", "SUPRACAUSAL");
    }
}
