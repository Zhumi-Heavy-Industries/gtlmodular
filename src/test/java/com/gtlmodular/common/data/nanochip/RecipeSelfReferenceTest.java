package com.gtlmodular.common.data.nanochip;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 查「产物又出现在自己输入里」的配方：这种配方会把自己锁死（拿不到产物就做不了它自己），
 * 新存档里整条链直接造不出来。冲突扫描只查「两条配方会不会互相认错」，管不到这件事。
 * 已知白名单里的那一条是本分支之前就有的、确认是故意的。
 */
class RecipeSelfReferenceTest {

    /** 本分支之前就存在、确认故意的自循环。加新条目必须写明为什么。 */
    private static final Set<String> KNOWN_INTENTIONAL = Set.of(
            "coolant/super_coolant.json");

    @Test
    void noRecipeConsumesItsOwnOutput() throws Exception {
        Path root = Paths.get("src", "main", "resources", "data", "gtlmodular", "recipes");
        assertTrue(Files.isDirectory(root), "找不到配方目录：" + root.toAbsolutePath());

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject recipe = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                Set<String> produced = itemIds(recipe.getAsJsonObject("outputs"));
                Set<String> consumed = itemIds(recipe.getAsJsonObject("inputs"));
                Set<String> overlap = new TreeSet<>(consumed);
                overlap.retainAll(produced);
                if (overlap.isEmpty()) {
                    continue;
                }
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (!KNOWN_INTENTIONAL.contains(relative)) {
                    offenders.add(relative + " -> " + overlap);
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "有配方把产物又当原料，会把自己锁死（新存档可能造不出整条链）：" + offenders);
    }

    /** 取 inputs/outputs 里 item[] 的物品 id；chance 为 0 的条目不算（不消耗也不产出）。 */
    private static Set<String> itemIds(JsonObject side) {
        Set<String> ids = new TreeSet<>();
        if (side == null || !side.has("item")) {
            return ids;
        }
        JsonArray entries = side.getAsJsonArray("item");
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            if (entry.has("chance") && entry.get("chance").getAsDouble() == 0.0D) {
                continue;
            }
            JsonObject content = entry.has("content") ? entry.getAsJsonObject("content") : entry;
            JsonObject ingredient = content.has("ingredient")
                    ? content.getAsJsonObject("ingredient") : content;
            if (ingredient.has("item")) {
                ids.add(ingredient.get("item").getAsString());
            }
        }
        return ids;
    }
}
