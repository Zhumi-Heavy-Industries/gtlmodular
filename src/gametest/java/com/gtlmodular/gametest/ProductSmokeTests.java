package com.gtlmodular.gametest;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.util.Collection;
import java.util.List;

public final class ProductSmokeTests {

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        String product = NanochipModContext.modId();
        return List.of(new TestFunction("smoke", product + ".product_smoke",
                product + ":empty", 100, 0, true, ProductSmokeTests::smoke));
    }

    private static void smoke(GameTestHelper helper) {
        String expected = System.getProperty("gtl.gametest.product");
        String actual = NanochipModContext.modId();
        helper.assertTrue(actual.equals(expected), "产品上下文不匹配：" + actual + " / " + expected);
        String other = actual.equals("gtl_nac") ? "gtlmodular" : "gtl_nac";
        helper.assertTrue(ModList.get().isLoaded(actual) && !ModList.get().isLoaded(other), "产品没有隔离");
        helper.assertTrue(ModList.get().isLoaded("gtl_enhancedcore") == actual.equals("gtlmodular"),
                "Enhancedcore 测试依赖未按产品隔离");
        helper.assertTrue(ModList.get().isLoaded("gtl_gametest"), "缺少测试辅助模组");
        helper.assertTrue(ModList.get().isLoaded("kubejs"), "缺少选定的 KubeJS");
        helper.assertTrue(GameTestMod.startupLoaded(), "测试 startup 脚本未加载");
        helper.assertTrue(Files.isRegularFile(FMLPaths.CONFIGDIR.get().resolve(actual + "-common.toml")),
                "当前产品配置未生成");
        helper.assertTrue(!Files.exists(FMLPaths.CONFIGDIR.get().resolve(other + "-common.toml")),
                "测试目录混入另一产品配置");
        ResourceLocation controller = new ResourceLocation(actual, "nanochip_assembly_complex");
        helper.assertTrue(ForgeRegistries.BLOCKS.containsKey(controller), "主机方块未注册：" + controller);
        helper.assertTrue(ForgeRegistries.ITEMS.containsKey(controller), "主机物品未注册：" + controller);
        for (String name : List.of("product_smoke", "mixin_application", "resource_integrity", "recipe_conflicts",
                "nac_structure_lifecycle", "nac_processing_local",
                "nac_processing_local_capacity", "nac_processing_output_during_work",
                "nac_processing_grid", "nac_processing_blocked", "nac_calibration_single", "nac_calibration_unlisted",
                "nac_calibration_cross", "nac_calibration_mixed", "nac_calibration_parallel", "nac_calibration_tiers_crystal",
                "nac_calibration_tiers_wetware", "nac_calibration_tiers_bioware", "nac_calibration_tiers_optical",
                "nac_calibration_tiers_exotic", "nac_calibration_tiers_cosmic", "nac_calibration_tiers_temporal",
                "nac_persistence_host", "nac_persistence_module")) {
            helper.assertTrue(GameTestRegistry.getAllTestFunctions().stream()
                    .anyMatch(test -> test.getTestName().equals(actual + "." + name) && test.isRequired()),
                    "必跑用例未被发现：" + name);
        }
        for (String scenario : List.of("host_grid", "module_grid", "module_local", "module_small")) {
            String name = actual + ".nac_restart_" + scenario;
            helper.assertTrue(GameTestRegistry.getAllTestFunctions().stream().anyMatch(test ->
                            test.getTestName().equals(name) && test.isRequired() && !(test instanceof KnownFailureTest)),
                    "NAC 首次到料回归用例未发现或仍是已知失败：" + name);
        }
        for (String issue : List.of("NAC_ASSEMBLER_INPUT_COLLISION", "NAC_ASSEMBLY_LINE_INPUT_COLLISION",
                "UHV_FREEZER_INPUT_COLLISION")) {
            String name = actual + ".recipe_conflicts_" + issue.toLowerCase(java.util.Locale.ROOT);
            helper.assertTrue(GameTestRegistry.getAllTestFunctions().stream().anyMatch(test ->
                            test.getTestName().equals(name) && test instanceof KnownFailureTest known
                                    && known.issue().equals(issue)), "配方冲突已知失败用例未发现：" + name);
        }
        for (String issue : List.of("NAC_CC_MISSING_REFERENCE_NAME", "NAC_CC_PACK_TEXTURES")) {
            String name = actual + ".resources_" + issue.toLowerCase(java.util.Locale.ROOT);
            helper.assertTrue(GameTestRegistry.getAllTestFunctions().stream().anyMatch(test ->
                            test.getTestName().equals(name) && test instanceof KnownFailureTest known
                                    && known.issue().equals(issue)), "资源已知失败用例未发现：" + name);
        }
        for (String variant : List.of("lazy", "mv", "super")) {
            for (String scenario : List.of("identity", "circuit", "type")) {
                String name = "gtlmodular.pattern_" + variant + "_" + scenario;
                boolean found = GameTestRegistry.getAllTestFunctions().stream()
                        .anyMatch(test -> test.getTestName().equals(name) && test.isRequired());
                helper.assertTrue(found == actual.equals("gtlmodular"), "样板总成用例发现或产品隔离错误：" + name);
            }
        }
        if (actual.equals("gtl_nac")) {
            helper.assertTrue(!ForgeRegistries.ITEMS.containsKey(new ResourceLocation("kubejs", "pattern_gate_common")),
                    "独立测试服加载了完整版样板测试数据");
        }
        LogUtils.getLogger().info("GameTest smoke: product={}, discovered={}, heap={}, mods={}",
                actual, GameTestRegistry.getAllTestFunctions().size(), Runtime.getRuntime().maxMemory(),
                ModList.get().getMods().stream().map(mod -> mod.getModId() + "@" + mod.getVersion()).sorted().toList());
        helper.succeed();
    }
}
