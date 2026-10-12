package com.gtlmodular.client;

import com.google.gson.JsonObject;
import com.gregtechceu.gtceu.data.pack.GTPackSource;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModules;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.event.AddPackFindersEvent;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 运行时外部规格没有 datagen 输出，用低优先级资源包提供名称和 LDLib 机器模型。 */
public final class NanochipModuleResources implements PackResources {
    private final String name;
    private final Map<ResourceLocation, byte[]> resources = new LinkedHashMap<>();

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES
                && NanochipModules.all().stream().anyMatch(spec -> spec.legacyKind() == null)) {
            event.addRepositorySource(new GTPackSource("nac:module_defaults", PackType.CLIENT_RESOURCES,
                    Pack.Position.BOTTOM, NanochipModuleResources::new));
        }
    }

    public NanochipModuleResources(String name) {
        this.name = name;
        Map<String, JsonObject> chinese = new LinkedHashMap<>();
        Map<String, JsonObject> english = new LinkedHashMap<>();
        for (NanochipModuleSpec spec : NanochipModules.all()) {
            if (spec.legacyKind() != null) continue;
            ResourceLocation id = spec.id();
            String namespace = id.getNamespace();
            JsonObject blockstate = new JsonObject();
            JsonObject variants = new JsonObject();
            JsonObject model = new JsonObject();
            model.addProperty("model", "ldlib:block/renderer_model");
            variants.add("", model);
            blockstate.add("variants", variants);
            put(namespace, "blockstates/" + id.getPath() + ".json", blockstate);
            JsonObject item = new JsonObject();
            item.addProperty("parent", "ldlib:block/renderer_model");
            put(namespace, "models/item/" + id.getPath() + ".json", item);
            translations(chinese.computeIfAbsent(namespace, key -> new JsonObject()), spec, spec.nameZh());
            translations(english.computeIfAbsent(namespace, key -> new JsonObject()), spec, spec.nameEn());
        }
        chinese.forEach((namespace, values) -> put(namespace, "lang/zh_cn.json", values));
        english.forEach((namespace, values) -> put(namespace, "lang/en_us.json", values));
    }

    private static void translations(JsonObject values, NanochipModuleSpec spec, String displayName) {
        values.addProperty(net.minecraft.Util.makeDescriptionId("block", spec.id()), displayName);
        values.addProperty(spec.langKey("role"), displayName);
        values.addProperty(spec.langKey("desc"), spec.mechanicZh());
    }

    private void put(String namespace, String path, JsonObject value) {
        resources.put(new ResourceLocation(namespace, path), value.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) { return null; }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        byte[] data = type == PackType.CLIENT_RESOURCES ? resources.get(id) : null;
        return data == null ? null : () -> new ByteArrayInputStream(data);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) return;
        String prefix = path.isEmpty() || path.endsWith("/") ? path : path + "/";
        resources.forEach((id, data) -> {
            if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix)) {
                output.accept(id, () -> new ByteArrayInputStream(data));
            }
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES
                ? resources.keySet().stream().map(ResourceLocation::getNamespace).collect(Collectors.toUnmodifiableSet())
                : Set.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        return serializer == PackMetadataSection.TYPE ? (T) new PackMetadataSection(Component.literal(name),
                SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES)) : null;
    }

    @Override public String packId() { return name; }
    @Override public void close() {}
}
