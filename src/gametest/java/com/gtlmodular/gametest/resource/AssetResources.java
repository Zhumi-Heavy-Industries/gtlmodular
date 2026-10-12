package com.gtlmodular.gametest.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.resource.ResourcePackLoader;
import net.minecraftforge.resource.PathPackResources;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;

/** 专服的数据包管理器不提供 assets，直接读取本次 Forge 选中的模组资源。 */
public final class AssetResources implements AutoCloseable {
    private final List<PackResources> packs = new ArrayList<>();

    public AssetResources() {
        ModList.get().getModFiles().forEach(mod -> packs.add(ResourcePackLoader.createPackForMod(mod)));
        packs.add(new PathPackResources("kubejs-assets", true, FMLPaths.GAMEDIR.get().resolve("kubejs")));
    }

    public InputStream open(ResourceLocation id) throws IOException {
        for (int i = packs.size() - 1; i >= 0; i--) {
            var supplier = packs.get(i).getResource(PackType.CLIENT_RESOURCES, id);
            if (supplier != null) return supplier.get();
        }
        // 原版 assets 在开发运行的 client-extra 中，不一定属于 Forge 模组资源根。
        return id.getNamespace().equals("minecraft")
                ? AssetResources.class.getResourceAsStream("/assets/minecraft/" + id.getPath()) : null;
    }

    public boolean exists(ResourceLocation id) throws IOException {
        try (var input = open(id)) { return input != null; }
    }

    public JsonObject json(ResourceLocation id) throws IOException {
        try (var input = open(id)) {
            if (input == null) throw new IOException("缺少资源 " + id);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    public JsonObject language(String locale) throws IOException {
        JsonObject combined;
        if (locale.equals("en_us")) {
            combined = json(new ResourceLocation("minecraft:lang/en_us.json"));
        } else {
            // 原版非英文语言在资源索引中，不在 Minecraft jar；缺缓存应明确报环境错误。
            Path root = Path.of(System.getProperty("gtl.gametest.assetRoot"));
            Path index = root.resolve("indexes/" + System.getProperty("gtl.gametest.assetIndex") + ".json");
            try (var reader = Files.newBufferedReader(index, StandardCharsets.UTF_8)) {
                String hash = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("objects")
                        .getAsJsonObject("minecraft/lang/" + locale + ".json").get("hash").getAsString();
                byte[] bytes = Files.readAllBytes(root.resolve("objects/" + hash.substring(0, 2) + "/" + hash));
                try {
                    if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes)).equals(hash)) {
                        throw new IOException("原版语言缓存校验失败：" + locale);
                    }
                } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
                combined = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        for (PackResources pack : packs) {
            for (String namespace : new TreeSet<>(pack.getNamespaces(PackType.CLIENT_RESOURCES))) {
                var supplier = pack.getResource(PackType.CLIENT_RESOURCES,
                        new ResourceLocation(namespace, "lang/" + locale + ".json"));
                if (supplier == null) continue;
                try (var input = supplier.get(); var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    JsonParser.parseReader(reader).getAsJsonObject().entrySet()
                            .forEach(entry -> combined.add(entry.getKey(), entry.getValue()));
                }
            }
        }
        return combined;
    }

    @Override
    public void close() { packs.forEach(PackResources::close); }
}
