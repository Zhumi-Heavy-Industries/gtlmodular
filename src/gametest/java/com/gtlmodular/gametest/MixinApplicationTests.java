package com.gtlmodular.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;
import org.spongepowered.asm.util.Annotations;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MixinApplicationTests {
    private static final Map<String, Application> results = new LinkedHashMap<>();

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        String product = NanochipModContext.modId();
        return List.of(new TestFunction("smoke", product + ".mixin_application",
                product + ":empty", 100, 0, true, MixinApplicationTests::verify));
    }

    public static Map<String, Application> results() {
        return new LinkedHashMap<>(results);
    }

    private static void verify(GameTestHelper helper) {
        results.clear();
        String product = NanochipModContext.modId();
        helper.assertTrue(FMLEnvironment.dist == Dist.DEDICATED_SERVER, "当前检查要求专用服务器");
        var file = ModList.get().getModContainerById(product).orElseThrow().getModInfo().getOwningFile();
        var configs = file.getConfig().getConfigList("mixins").stream()
                .map(entry -> entry.<String>getConfigElement("config").orElseThrow()).toList();
        helper.assertTrue(configs.contains(product + ".mixins.json"), "产品主 Mixin 配置未登记");
        List<String> failures = new ArrayList<>();
        for (String config : configs) {
            try (var stream = MixinApplicationTests.class.getClassLoader().getResourceAsStream(config)) {
                if (stream == null) throw new IllegalStateException("配置资源不存在：" + config);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                String prefix = json.get("package").getAsString() + ".";
                for (String group : List.of("mixins", "server", "client")) {
                    for (var entry : entries(json, group)) {
                        String mixin = prefix + entry.getAsString();
                        String key = config + ":" + entry.getAsString();
                        if (group.equals("client")) {
                            record(key, new Application("CLIENT_ONLY", Map.of(), "专服不加载；需客户端验收"));
                            continue;
                        }
                        verifyMixin(key, mixin, failures);
                    }
                }
            } catch (Exception | LinkageError failure) {
                failures.add(config + ": " + failure);
            }
        }
        helper.assertTrue(results.values().stream().anyMatch(result -> result.status.equals("APPLIED")),
                "没有任何已应用的 Mixin：" + failures);
        helper.assertTrue(failures.isEmpty(), "Mixin 未完整应用：" + failures);
        LogUtils.getLogger().info("GameTest mixin summary: product={} applied={} clientOnly={}", product,
                results.values().stream().filter(result -> result.status.equals("APPLIED")).count(),
                results.values().stream().filter(result -> result.status.equals("CLIENT_ONLY")).count());
        helper.succeed();
    }

    private static JsonArray entries(JsonObject config, String group) {
        return config.has(group) ? config.getAsJsonArray(group) : new JsonArray();
    }

    private static void verifyMixin(String key, String mixin, List<String> failures) {
        Map<String, String> targets = new LinkedHashMap<>();
        try {
            // 直接读取注解，避免把插件过滤掉或准备失败的 Mixin 从预期集合中漏掉。
            var node = new ClassNode();
            try (var stream = MixinApplicationTests.class.getClassLoader()
                    .getResourceAsStream(mixin.replace('.', '/') + ".class")) {
                if (stream == null) throw new IllegalStateException("Mixin 类资源不存在：" + mixin);
                new ClassReader(stream).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
            var annotation = Annotations.getInvisible(node, Mixin.class);
            if (annotation == null) throw new IllegalStateException("缺少 @Mixin：" + mixin);
            List<String> names = new ArrayList<>();
            for (Type type : Annotations.<Type>getValue(annotation, "value", true)) names.add(type.getClassName());
            names.addAll(Annotations.<String>getValue(annotation, "targets", true));
            if (names.isEmpty()) throw new IllegalStateException("没有目标类：" + mixin);
            for (String target : names) {
                try {
                    // 不执行静态初始化；强制加载可覆盖启动时尚未用到的兼容目标。
                    Class<?> transformed = Class.forName(target, false, MixinApplicationTests.class.getClassLoader());
                    boolean merged = Arrays.stream(transformed.getDeclaredMethods())
                            .map(method -> method.getAnnotation(MixinMerged.class))
                            .anyMatch(mark -> mark != null && mark.mixin().equals(mixin));
                    if (!merged) throw new IllegalStateException("目标中没有此 Mixin 的合并方法");
                    targets.put(target, "APPLIED");
                } catch (Exception | LinkageError failure) {
                    targets.put(target, "FAILED: " + failure);
                    failures.add(key + " -> " + target + ": " + failure);
                }
            }
            boolean applied = targets.values().stream().allMatch("APPLIED"::equals);
            record(key, new Application(applied ? "APPLIED" : "FAILED", targets, ""));
        } catch (Exception | LinkageError failure) {
            failures.add(key + ": " + failure);
            record(key, new Application("FAILED", targets, failure.toString()));
        }
    }

    private static void record(String key, Application result) {
        results.put(key, result);
        LogUtils.getLogger().info("GameTest mixin: mixin={} status={} targets={} reason={}",
                key, result.status, result.targets, result.reason);
    }

    public record Application(String status, Map<String, String> targets, String reason) {}
}
