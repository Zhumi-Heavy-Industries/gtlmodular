package com.gtlmodular.common.machine.pattern.naming;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ★ 2026-11-25：<b>服务端语言兜底</b>（自动命名专用）。
 *
 * <h2>为什么需要它（这就是「样板总成显示成 dimensionally_transcendent_plasma_forge」的根因）</h2>
 * <p>{@link BufferAutoNaming} 在<b>服务端</b>跑，名字是<b>字符串</b>（{@code customName} 同步到客户端），
 * 所以必须在服务端把 lang 键解析成文字。可 Forge 的
 * {@code Component.translatable(key).getString()} 在服务端不一定取得到翻译：
 * 整合包的配方表大多由 <b>gtlcore</b> 注册在 {@code gtceu:} 命名空间下，
 * 它们的 lang 在 gtlcore 的 {@code assets/gtceu/lang/*.json}，而不是「本模组自己的 assets」，
 * 服务端语言加载链有没有覆盖到这类<b>跨命名空间</b>条目取决于 Forge/加载顺序
 * ⇒ 取不到时 {@code getString()} 原样返回键，旧版再退回<b>注册路径名</b>并落盘
 * ⇒ 玩家看到 {@code dimensionally_transcendent_plasma_forge} 这种裸 id，且按「延续」永久保留。
 *
 * <h2>做法</h2>
 * <p>翻译取不到时，直接去 <b>mod 文件里的 {@code assets/<namespace>/lang/zh_cn.json}</b>
 * （没有就 {@code en_us.json}）查一次：
 * <ul>
 *   <li>优先中文 —— 与整合包/上游 Enhancedcore 的命名口径一致（它也是硬读 zh_cn）；</li>
 *   <li>用 {@link ModList} + {@code IModFile#findResource} 定位文件，<b>不扫目录、不读整个世界</b>；</li>
 *   <li>解析结果按「命名空间|键」缓存（含未命中），每个键最多扫一次 ⇒ 不进热路径；</li>
 *   <li>任何异常 ⇒ {@code null}（调用方保持原名，绝不因它影响机器）。</li>
 * </ul>
 *
 * <p><b>零存档影响</b>：只读文件、只返回字符串；写名字仍由 {@link BufferAutoNaming} 按既有规则做。
 */
final class ModLangIndex {

    private ModLangIndex() {}

    /** 「命名空间|键」→ 译文（{@link #MISSING} 表示查过但没有）。 */
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    /** 未命中的哨兵值（{@link ConcurrentHashMap} 不允许 null 值）。 */
    private static final String MISSING = "\u0000";

    /**
     * 在 mod 文件的 zh_cn / en_us 里查一个 lang 键。
     *
     * @param namespace 语言命名空间（= 配方表 id 的命名空间，如 {@code gtceu}）
     * @param key       完整 lang 键（如 {@code gtceu.dimensionally_transcendent_plasma_forge}）
     * @return 译文；查不到 ⇒ {@code null}
     */
    static String lookup(String namespace, String key) {
        if (namespace == null || namespace.isEmpty() || key == null || key.isEmpty()) {
            return null;
        }
        String cacheKey = namespace + "|" + key;
        String cached = CACHE.get(cacheKey);
        if (cached != null) {
            return MISSING.equals(cached) ? null : cached;
        }
        String value = read(namespace, key);
        CACHE.putIfAbsent(cacheKey, value == null ? MISSING : value);
        return value;
    }

    private static String read(String namespace, String key) {
        ModList mods;
        try {
            mods = ModList.get();
        } catch (Throwable ignored) {
            return null;
        }
        if (mods == null) {
            return null;
        }
        for (String lang : new String[]{"zh_cn", "en_us"}) {
            String value = readFromMods(mods, namespace, lang, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String readFromMods(ModList mods, String namespace, String lang, String key) {
        try {
            for (IModFileInfo info : mods.getModFiles()) {
                try {
                    Path file = info.getFile().findResource("assets", namespace, "lang", lang + ".json");
                    if (file == null || !Files.isRegularFile(file)) {
                        continue;
                    }
                    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        JsonElement root = JsonParser.parseReader(reader);
                        if (root == null || !root.isJsonObject()) {
                            continue;
                        }
                        JsonObject object = root.getAsJsonObject();
                        JsonElement value = object.get(key);
                        if (value != null && value.isJsonPrimitive()) {
                            String text = value.getAsString();
                            if (text != null && !text.isEmpty()) {
                                return text;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    // 单个 mod 文件读失败不影响其它文件
                }
            }
        } catch (Throwable ignored) {
            // ModList 或文件系统异常 ⇒ 当作查不到
        }
        return null;
    }
}
