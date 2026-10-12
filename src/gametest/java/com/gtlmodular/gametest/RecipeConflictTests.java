package com.gtlmodular.gametest;

import com.google.gson.GsonBuilder;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.gametest.recipe.RecipeConflictScanner;
import com.gtlmodular.gametest.recipe.RecipeConflictProbe;
import com.gtlmodular.gametest.recipe.KnownRecipeConflicts;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

public final class RecipeConflictTests {
    private static RecipeConflictScanner.Report completed;

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        String product = NanochipModContext.modId();
        List<TestFunction> tests = new ArrayList<>();
        tests.add(new TestFunction("recipe_conflicts", product + ".recipe_conflicts",
                product + ":empty", 200, 0, true, helper -> {
                    var probe = new RecipeConflictProbe(helper);
                    helper.startSequence().thenIdle(1).thenExecute(() -> scan(helper, probe)).thenSucceed();
                }));
        for (String issue : List.of("NAC_ASSEMBLER_INPUT_COLLISION", "NAC_ASSEMBLY_LINE_INPUT_COLLISION",
                "UHV_FREEZER_INPUT_COLLISION")) {
            tests.add(new KnownFailureTest("recipe_conflicts", product + ".recipe_conflicts_" + issue.toLowerCase(java.util.Locale.ROOT),
                    product + ":empty", 200, issue, helper -> helper.startSequence()
                    .thenWaitUntil(() -> helper.assertTrue(completed != null, "等待扫描和真实 handler 复核完成"))
                    .thenExecute(() -> {
                        var conflicts = completed.ownConflicts().stream()
                                .filter(f -> KnownRecipeConflicts.issue(f).equals(issue)).toList();
                        if (!conflicts.isEmpty()) throw new KnownFailureTest.Failure(issue,
                                "真实 ME handler 复核 " + conflicts.size() + " 个有向冲突；例："
                                        + conflicts.get(0).candidate() + " <- " + conflicts.get(0).target()
                                        + "；逐条证据见 " + product + "-recipe-conflicts.json");
                    }).thenSucceed()));
        }
        return tests;
    }

    private static void scan(GameTestHelper helper, RecipeConflictProbe probe) {
        String product = NanochipModContext.modId();
        for (String control : List.of("subset", "superset", "different_output", "different_type", "amount_one",
                "amount_four", "circuit_0", "circuit_1", "circuit_2", "nbt_1", "nbt_2", "tag", "tag_b", "fluid_small",
                "fluid_large", "needs_catalyst")) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(new ResourceLocation(id(control))).isPresent(),
                    "冲突扫描对照配方未加载：" + control);
        }
        var report = new RecipeConflictScanner(helper.getLevel(), product, probe).scan();
        Path output = Path.of(System.getProperty("gtl.gametest.report")).resolveSibling(product + "-recipe-conflicts.json");
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        } catch (IOException e) {
            throw new IllegalStateException("无法写配方冲突报告", e);
        }
        assertControls(helper, report);
        LogUtils.getLogger().info("Recipe conflict scan: product={} loaded={} tables={} comparisons={} findings={} "
                        + "owned={} unresolved={} report={}", product, report.loadedRecipes(), report.tables().size(),
                report.directedComparisons(), report.findings().size(), report.ownConflicts().size(),
                report.unresolved().size(), output);
        helper.assertTrue(report.unresolved().stream().noneMatch(row -> owned(row.get("candidate"))
                        || owned(row.get("target"))), "自有配方存在尚未穷尽的候选搜索，见 " + output);
        helper.assertTrue(report.findings().stream().noneMatch(f -> f.owned() && f.status().equals("CONDITIONAL")),
                "自有配方存在尚未验证的运行条件，见 " + output);
        completed = report;
        var unexpected = report.ownConflicts().stream().filter(f -> KnownRecipeConflicts.issue(f).isEmpty()).toList();
        helper.assertTrue(unexpected.isEmpty(), "自有配方存在新增 ME 供料冲突："
                + unexpected.stream().map(f -> f.candidate() + " <- " + f.target()).toList()
                + "；报告 " + output);
    }

    private static void assertControls(GameTestHelper helper, RecipeConflictScanner.Report report) {
        var subset = finding(report, "subset", "superset");
        helper.assertTrue(subset != null && subset.status().equals("CONFLICT") && subset.targetBatches() == 1,
                "未检出无电路的输入子集冲突");
        helper.assertTrue(finding(report, "superset", "subset") == null, "凭空补齐了缺少的物品");
        var amount = finding(report, "amount_four", "amount_one");
        helper.assertTrue(amount != null && amount.targetBatches() == 4, "未覆盖同槽累积多批的数量冲突");
        helper.assertTrue(finding(report, "circuit_1", "circuit_2") == null
                && finding(report, "circuit_2", "circuit_1") == null, "不同电路被误报为冲突");
        var zeroCircuit = finding(report, "circuit_0", "circuit_1");
        helper.assertTrue(zeroCircuit != null && finding(report, "circuit_1", "circuit_0") == null,
                "未按上游 ME handler 的大于零条件检查电路");
        helper.assertTrue(finding(report, "nbt_1", "nbt_2") == null
                && finding(report, "nbt_2", "nbt_1") == null, "物品 NBT 被错误忽略");
        helper.assertTrue(finding(report, "tag_b", "tag") != null
                && finding(report, "tag", "tag_b") != null, "未覆盖标签的非首个候选");
        helper.assertTrue(finding(report, "fluid_small", "fluid_large") != null
                && finding(report, "fluid_large", "fluid_small") == null, "流体子集或额外物品匹配错误");
        helper.assertTrue(finding(report, "needs_catalyst", "subset") == null, "非消耗催化剂被错误忽略");
        helper.assertTrue(finding(report, "different_type", "subset") == null, "扫描跨越了独立配方表");
        if (report.product().equals("gtlmodular")) {
            helper.assertTrue(subset.patternIdentity().equals("ALLOWS"), "同主产物冲突未调用实际身份闸");
            helper.assertTrue(finding(report, "subset", "different_output").patternIdentity().equals("BLOCKS"),
                    "不同主产物的身份保护未被识别");
            helper.assertTrue(zeroCircuit.patternIdentity().equals("BLOCKS"),
                    "身份闸未携带样板电路，误用了忽略电路的重载");
        } else {
            helper.assertTrue(subset.patternIdentity().equals("NOT_PRESENT"), "独立版混入完整版身份闸");
        }
    }

    private static RecipeConflictScanner.Finding finding(RecipeConflictScanner.Report report, String candidate, String target) {
        return report.findings().stream().filter(f -> f.candidate().equals(id(candidate)) && f.target().equals(id(target)))
                .findFirst().orElse(null);
    }

    private static String id(String name) { return "gtl_gametest:conflict_scan/" + name; }
    private static boolean owned(String id) { return id.startsWith("gtlmodular:") || id.startsWith("gtl_nac:"); }
}
