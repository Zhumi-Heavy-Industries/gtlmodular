package com.gtlmodular.gametest;

import com.google.gson.GsonBuilder;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.gametest.resource.AssetResources;
import com.gtlmodular.gametest.resource.KnownResourceFailures;
import com.gtlmodular.gametest.resource.ResourceAudit;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ResourceIntegrityTests {
    private static ResourceAudit.Result result;

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        String product = NanochipModContext.modId();
        List<TestFunction> tests = new ArrayList<>();
        tests.add(new TestFunction("smoke", product + ".resource_integrity", product + ":empty",
                100, 0, true, ResourceIntegrityTests::check));
        for (String issue : List.of(KnownResourceFailures.AIR_NAME, KnownResourceFailures.PACK_TEXTURES)) {
            tests.add(new KnownFailureTest("smoke", product + ".resources_" + issue.toLowerCase(Locale.ROOT),
                    product + ":empty", 100, issue, helper -> checkKnown(helper, issue)));
        }
        return tests;
    }

    private static ResourceAudit.Result scan() {
        if (result != null) return result;
        String product = NanochipModContext.modId();
        try (var assets = new AssetResources()) {
            var scanned = new ResourceAudit(assets).scan(product);
            Path report = Path.of(System.getProperty("gtl.gametest.report")).resolveSibling(product + "-resources.json");
            Files.createDirectories(report.getParent());
            var classified = scanned.findings().stream().map(finding -> Map.of(
                    "finding", finding, "issue", KnownResourceFailures.issue(finding))).toList();
            Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                    "product", product, "complete", true, "scan", scanned, "classified", classified)) + "\n");
            result = scanned;
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("资源检查无法完成；原版语言缺缓存时先执行 downloadAssets", e);
        }
    }

    private static void check(GameTestHelper helper) {
        var scanned = scan();
        helper.assertTrue(scanned.blocks() > 0 && scanned.items() > 0, "未检查任何注册对象");
        var unexpected = scanned.findings().stream().filter(finding -> KnownResourceFailures.issue(finding).isEmpty()).toList();
        helper.assertTrue(unexpected.isEmpty(), "资源完整性失败：" + unexpected);
        helper.succeed();
    }

    private static void checkKnown(GameTestHelper helper, String issue) {
        var findings = scan().findings().stream().filter(finding -> KnownResourceFailures.issue(finding).equals(issue)).toList();
        if (!findings.isEmpty()) throw new KnownFailureTest.Failure(issue, "共 " + findings.size() + " 项：" + findings);
        helper.succeed();
    }
}
