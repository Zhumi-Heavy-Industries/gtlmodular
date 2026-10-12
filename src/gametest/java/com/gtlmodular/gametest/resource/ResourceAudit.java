package com.gtlmodular.gametest.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gtlmodular.common.data.nanochip.NanochipCircuitComponents.CcItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ResourceAudit {
    public record Finding(String owner, String kind, String resource, String detail) {}
    public record Result(String product, int blocks, int items, int models, int textures, List<Finding> findings) {}

    private final AssetResources assets;
    private final List<Finding> findings = new ArrayList<>();
    private final Set<String> models = new HashSet<>();
    private final Set<String> textures = new HashSet<>();
    private final Set<ResourceLocation> activeModels = new HashSet<>();
    private String owner;

    public ResourceAudit(AssetResources assets) { this.assets = assets; }

    public Result scan(String product) throws IOException {
        Map<String, JsonObject> languages = new LinkedHashMap<>();
        for (String language : List.of("zh_cn", "en_us")) {
            languages.put(language, assets.language(language));
        }
        int blocks = 0;
        for (var id : new TreeSet<>(ForgeRegistries.BLOCKS.getKeys())) {
            if (!id.getNamespace().equals(product)) continue;
            blocks++;
            owner = "block:" + id;
            var block = ForgeRegistries.BLOCKS.getValue(id);
            name(block.getName(), languages);
            if (block instanceof IMachineBlock machine) {
                attempt("dynamic_model", id, () -> MachineAssets.check(this, id, machine.getDefinition()));
            } else {
                attempt("blockstate", id, () -> {
                    JsonObject state = assets.json(path(id, "blockstates/", ".json"));
                    Set<String> references = new TreeSet<>();
                    collect(state, "model", references);
                    if (references.isEmpty()) throw new IOException("方块状态没有模型引用");
                    for (String model : references) model(model, Map.of());
                });
            }
        }
        int items = 0;
        for (var id : new TreeSet<>(ForgeRegistries.ITEMS.getKeys())) {
            if (!id.getNamespace().equals(product)) continue;
            items++;
            owner = "item:" + id;
            var item = ForgeRegistries.ITEMS.getValue(id);
            Component displayedName = item.getName(new ItemStack(item));
            name(displayedName, languages);
            if (item instanceof CcItem cc && containsAirName(displayedName)) {
                findings.add(new Finding(owner, "fallback_name", cc.circuitComponent().realRef,
                        "真实名称含 block.minecraft.air；预期缺失原物品时使用 " + cc.circuitComponent().langKey()));
            }
            if (item instanceof MetaMachineItem machine) {
                attempt("dynamic_model", id, () -> MachineAssets.check(this, id, machine.getDefinition()));
            } else {
                model(new ResourceLocation(product, "item/" + id.getPath()).toString(), Map.of());
            }
        }
        return new Result(product, blocks, items, models.size(), textures.size(), List.copyOf(findings));
    }

    private static boolean containsAirName(Component component) {
        if (component.getContents() instanceof TranslatableContents text) {
            if (text.getKey().equals("block.minecraft.air")) return true;
            for (Object argument : text.getArgs()) {
                if (argument instanceof Component child && containsAirName(child)) return true;
            }
        }
        return component.getSiblings().stream().anyMatch(ResourceAudit::containsAirName);
    }

    private void name(Component component, Map<String, JsonObject> languages) {
        if (component.getContents() instanceof TranslatableContents text) {
            nameKey(text.getKey(), languages);
            for (Object argument : text.getArgs()) {
                if (argument instanceof Component child) name(child, languages);
            }
        } else if (component.getContents() instanceof LiteralContents text) {
            if (text.text().replaceAll("§.", "").isBlank() && component.getSiblings().isEmpty()) {
                findings.add(new Finding(owner, "translation", "literal", "名称为空"));
            }
        } else {
            findings.add(new Finding(owner, "translation", "component", "未覆盖的名称组件 " + component.getContents()));
        }
        component.getSiblings().forEach(child -> name(child, languages));
    }

    private void nameKey(String key, Map<String, JsonObject> languages) {
        languages.forEach((language, values) -> {
            var value = values.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                    || value.getAsString().replaceAll("§.", "").isBlank() || value.getAsString().equals(key)) {
                findings.add(new Finding(owner, "translation", language + ":" + key, "名称缺失、为空或原样显示语言键"));
            }
        });
    }

    public void model(String location, Map<String, String> overrides) {
        ResourceLocation id = new ResourceLocation(location);
        if (!activeModels.add(id)) {
            findings.add(new Finding(owner, "model", id.toString(), "循环模型 override"));
            return;
        }
        try {
            attempt("model", id, () -> {
                Map<String, String> slots = new HashMap<>();
                List<JsonObject> chain = new ArrayList<>();
                parents(id, chain, new HashSet<>());
                for (JsonObject node : chain) {
                    if (node.has("textures")) node.getAsJsonObject("textures").entrySet()
                            .forEach(entry -> slots.put(entry.getKey(), entry.getValue().getAsString()));
                    if (node.has("loader") && !node.get("loader").getAsString().equals("ldlib:renderer")) {
                        throw new IOException("尚未覆盖的模型 loader: " + node.get("loader"));
                    }
                    if (node.has("overrides")) {
                        Set<String> references = new TreeSet<>();
                        collect(node.get("overrides"), "model", references);
                        for (String reference : references) {
                            if (reference.equals(location)) throw new IOException("模型 override 自引用 " + reference);
                            model(reference, Map.of());
                        }
                    }
                }
                slots.putAll(overrides);
                boolean generated = chain.stream().anyMatch(node -> node.has("parent")
                        && new ResourceLocation(node.get("parent").getAsString()).equals(new ResourceLocation("minecraft:builtin/generated")));
                boolean rendered = chain.stream().anyMatch(node -> node.has("loader"));
                boolean geometry = false;
                for (JsonObject node : chain) {
                    if (node.has("elements")) geometry = !node.getAsJsonArray("elements").isEmpty();
                }
                if (!rendered && !geometry && !(generated && slots.containsKey("layer0"))) {
                    throw new IOException("模型没有可用几何或 generated 图层");
                }
                Set<String> used = new TreeSet<>(slots.values());
                // 子模型可以替换整个 elements 数组，父模型的占位符只在继承时才需要解析。
                for (int i = chain.size() - 1; i >= 0; i--) {
                    if (chain.get(i).has("elements")) {
                        collect(chain.get(i).get("elements"), "texture", used);
                        break;
                    }
                }
                for (String reference : used) {
                    Set<String> visited = new HashSet<>();
                    String value = reference;
                    while (value.startsWith("#")) {
                        if (!visited.add(value) || !slots.containsKey(value.substring(1))) {
                            throw new IOException("未解析或循环的贴图变量 " + reference);
                        }
                        value = slots.get(value.substring(1));
                    }
                    texture(new ResourceLocation(value));
                }
            });
        } finally {
            activeModels.remove(id);
        }
    }

    private void parents(ResourceLocation id, List<JsonObject> chain, Set<ResourceLocation> seen) throws IOException {
        if (!seen.add(id)) throw new IOException("循环模型 parent: " + id);
        if (id.getNamespace().equals("minecraft") && Set.of("builtin/generated", "builtin/entity").contains(id.getPath())) return;
        models.add(id.toString());
        JsonObject model = assets.json(path(id, "models/", ".json"));
        if (model.has("parent")) parents(new ResourceLocation(model.get("parent").getAsString()), chain, seen);
        chain.add(model);
    }

    public void texture(ResourceLocation id) {
        textures.add(id.toString());
        attempt("texture", id, () -> {
            try (var input = assets.open(path(id, "textures/", ".png"))) {
                if (input == null) throw new IOException("缺少 PNG");
                var image = ImageIO.read(input);
                if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) throw new IOException("PNG 无法解码");
            }
        });
    }

    public void workable(ResourceLocation casing, ResourceLocation overlay) throws IOException {
        model("gtceu:block/cube/tinted/all", Map.of("all", casing.toString()));
        int faces = 0;
        for (String face : List.of("front", "back", "top", "bottom", "side")) {
            ResourceLocation normal = new ResourceLocation(overlay.getNamespace(), overlay.getPath() + "/overlay_" + face);
            if (!assets.exists(path(normal, "textures/", ".png"))) continue;
            faces++;
            texture(normal);
            for (String suffix : List.of("_active", "_paused", "_emissive", "_active_emissive", "_paused_emissive")) {
                var variant = new ResourceLocation(normal.getNamespace(), normal.getPath() + suffix);
                if (assets.exists(path(variant, "textures/", ".png"))) texture(variant);
            }
        }
        if (faces == 0) throw new IOException("动态面板没有任何可用 overlay: " + overlay);
    }

    private void attempt(String kind, ResourceLocation id, Checked action) {
        try { action.run(); }
        catch (IOException | RuntimeException error) {
            findings.add(new Finding(owner, kind, id.toString(), error.toString()));
        }
    }

    private static void collect(JsonElement value, String key, Set<String> found) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> collect(child, key, found));
        if (value.isJsonObject()) value.getAsJsonObject().entrySet().forEach(entry -> {
            if (entry.getKey().equals(key) && entry.getValue().isJsonPrimitive()) found.add(entry.getValue().getAsString());
            else collect(entry.getValue(), key, found);
        });
    }

    private static ResourceLocation path(ResourceLocation id, String prefix, String suffix) {
        return new ResourceLocation(id.getNamespace(), prefix + id.getPath() + suffix);
    }

    @FunctionalInterface private interface Checked { void run() throws IOException; }
}
