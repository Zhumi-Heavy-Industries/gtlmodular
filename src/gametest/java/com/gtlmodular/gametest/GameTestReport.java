package com.gtlmodular.gametest;

import com.google.gson.GsonBuilder;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.event.server.ServerAboutToStartEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GameTestReport extends LogTestReporter {
    private final Map<String, Result> results = new LinkedHashMap<>();

    public static void install(ServerAboutToStartEvent event) {
        GlobalTestReporter.replaceWith(new GameTestReport());
    }

    @Override
    public void onTestFailed(GameTestInfo test) {
        super.onTestFailed(test);
        boolean expected = test.getTestFunction() instanceof KnownFailureTest known && known.matches(test.getError());
        record(test, expected ? "XFAIL" : "FAIL");
    }

    @Override
    public void onTestSuccess(GameTestInfo test) {
        super.onTestSuccess(test);
        record(test, test.getTestFunction() instanceof KnownFailureTest ? "XPASS" : "PASS");
    }

    private void record(GameTestInfo test, String status) {
        String issue = test.getTestFunction() instanceof KnownFailureTest known ? known.issue() : "";
        String error = test.getError() == null ? "" : test.getError().toString();
        results.put(test.getTestName(), new Result(status, issue, error));
        LogUtils.getLogger().info("GameTest result: test={} status={} issue={} error={}",
                test.getTestName(), status, issue, error);
    }

    @Override
    public void finish() {
        String product = NanochipModContext.modId();
        var discovered = GameTestRegistry.getAllTestFunctions().stream().map(TestFunction::getTestName)
                .filter(name -> name.startsWith(product + ".")).sorted().toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : new String[]{"PASS", "XFAIL", "XPASS", "FAIL"}) {
            counts.put(status, results.values().stream().filter(result -> result.status.equals(status)).count());
        }
        LogUtils.getLogger().info("GameTest summary: product={} counts={} knownFailures={}", product, counts,
                results.entrySet().stream().filter(entry -> !entry.getValue().issue.isEmpty()).toList());
        var report = Map.of("product", product, "complete", true, "discovered", discovered,
                "counts", counts, "results", results, "mixinApplications", MixinApplicationTests.results());
        Path path = Path.of(System.getProperty("gtl.gametest.report"));
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        } catch (IOException e) {
            throw new IllegalStateException("无法写入 GameTest 结果 " + path, e);
        }
    }

    private record Result(String status, String issue, String error) {}
}
