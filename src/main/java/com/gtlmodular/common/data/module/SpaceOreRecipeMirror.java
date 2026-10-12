package com.gtlmodular.common.data.module;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.lookup.GTRecipeLookup;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 从已加载的配方管理器取源配方，避免依赖只有 builder 保存时才执行的构建回调。 */
public final class SpaceOreRecipeMirror {

    private SpaceOreRecipeMirror() {
    }

    public static final ResourceLocation BASE_TYPE_ID = new ResourceLocation("gtceu", "steam_space_ore_processor");

    public static final ResourceLocation FALLBACK_TYPE_ID = new ResourceLocation("gtceu", "integrated_ore_processor");

    public static final ResourceLocation TARGET_TYPE_ID = new ResourceLocation("gtceu", "space_ore_processor");

    public static final long MIRROR_EUT = GTValues.VA[GTValues.HV];

    private static final String[] BY_TYPE_FIELD_NAMES = {"f_44007_", "recipes"};

    private static final Object LOCK = new Object();

    private static volatile boolean handled;

    /** 失败后停止重试，避免每次进入世界重复触发同一异常。 */
    private static volatile boolean handleFailed;

    private static volatile boolean skipLogged;

    private static volatile boolean resultLogged;

    private static Field byTypeField;

    private static volatile boolean byTypeResolutionFailed;

    public static void register(IEventBus modBus) {
        // 服务器启动事件属于 Forge 总线，且此时配方管理器已完成加载。
        MinecraftForge.EVENT_BUS.addListener(SpaceOreRecipeMirror::onServerStarted);
        GTLModular.LOGGER.info("[gtlmodular] 天基矿石处理厂配方镜像已挂载：{} ← {}（回落到 {}）"
                        + "，在服务器启动完成后（配方管理器就绪）执行「枚举 + 复制 + 双向登记」。",
                TARGET_TYPE_ID, BASE_TYPE_ID, FALLBACK_TYPE_ID);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        if (handled || handleFailed) {
            return;
        }
        if (!GTLModularConfig.isSpaceOreRecipeMirrorEnabled()) {
            logSkipOnce("配置 spaceOre.recipeMirror=false");
            return;
        }
        synchronized (LOCK) {
            if (handled || handleFailed) {
                return;
            }
            try {
                mirrorNow(event.getServer());
                handled = true;
            } catch (Throwable t) {
                handleFailed = true;
                GTLModular.LOGGER.warn("[gtlmodular] 天基矿石处理厂配方镜像执行失败（已忽略，不影响游戏）：{}", t.toString());
            }
        }
    }

    private static void mirrorNow(MinecraftServer server) {
        if (server == null) {
            logSkipOnce("拿不到服务器实例");
            return;
        }
        GTRecipeType target = GTRegistries.RECIPE_TYPES.get(TARGET_TYPE_ID);
        if (target == null) {
            logSkipOnce("目标配方表未注册（target=" + TARGET_TYPE_ID + "）");
            return;
        }
        RecipeManager manager = server.getRecipeManager();
        if (manager == null) {
            logSkipOnce("拿不到配方管理器");
            return;
        }
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> byType = readByType(manager);
        if (byType == null) {
            logSkipOnce("拿不到 RecipeManager 的 byType 索引");
            return;
        }

        // 目标已有配方时跳过，避免覆盖整合包提供的内容。
        Map<ResourceLocation, Recipe<?>> existing = byType.get(target);
        int existingCount = existing == null ? 0 : existing.size();
        if (existingCount > 0) {
            logSkipOnce("目标配方表 " + TARGET_TYPE_ID + " 已有 " + existingCount
                    + " 条配方 ⇒ 本轮不镜像（幂等，一条都不加、一条都不清）");
            return;
        }

        GTRecipeType source = GTRegistries.RECIPE_TYPES.get(BASE_TYPE_ID);
        List<GTRecipe> sources = source == null ? List.of() : collect(manager, source);
        if (sources.isEmpty()) {
            GTRecipeType fallback = GTRegistries.RECIPE_TYPES.get(FALLBACK_TYPE_ID);
            if (fallback != null) {
                List<GTRecipe> alt = collect(manager, fallback);
                if (!alt.isEmpty()) {
                    source = fallback;
                    sources = alt;
                    GTLModular.LOGGER.info("[gtlmodular] 天基矿石处理厂配方镜像：首选源 {} 一条都没有 ⇒ 回落到 {}（{} 条）",
                            BASE_TYPE_ID, FALLBACK_TYPE_ID, alt.size());
                }
            }
        }
        if (source == null || sources.isEmpty()) {
            logSkipOnce("源配方表一条配方都没有（" + BASE_TYPE_ID + " / " + FALLBACK_TYPE_ID + "）");
            return;
        }

        int failed = 0;
        List<GTRecipe> copies = new ObjectArrayList<>();
        for (GTRecipe src : sources) {
            try {
                copies.add(copyOne(target, src));
            } catch (Throwable t) {
                failed++;
                if (failed <= 5) {
                    GTLModular.LOGGER.warn("[gtlmodular] 天基矿石处理厂配方镜像失败（第 {} 条，已跳过该条）：{}",
                            failed, t.toString());
                }
            }
        }
        if (copies.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] 天基矿石处理厂配方镜像结果：源 {} {} 条 → 目标 {} 0 条，失败 {} 条。",
                    source.registryName, sources.size(), TARGET_TYPE_ID, failed);
            return;
        }

        // 机器检索与配方管理器分别维护索引，必须同时登记。
        GTRecipeLookup lookup = target.getLookup();
        int addedToLookup = 0;
        if (lookup != null) {
            // 清除 lookup 可能残留的配方，避免 byType 已空而机器仍匹配旧内容。
            lookup.removeAllRecipes();
            for (GTRecipe copy : copies) {
                if (lookup.addRecipe(copy)) {
                    addedToLookup++;
                }
            }
        }

        Map<ResourceLocation, Recipe<?>> fresh = new LinkedHashMap<>();
        for (GTRecipe copy : copies) {
            ResourceLocation id = copy.getId();
            if (id == null || fresh.containsKey(id)) {
                id = new ResourceLocation(TARGET_TYPE_ID.getNamespace(),
                        TARGET_TYPE_ID.getPath() + "/mirrored_" + fresh.size());
            }
            fresh.put(id, copy);
        }
        // 外层表可能不可变，复制后替换目标类型，避免原地写入失败。
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> next = new LinkedHashMap<>(byType);
        next.put(target, fresh);
        writeByType(manager, next);

        GTLModular.LOGGER.info("[gtlmodular] 天基矿石处理厂配方镜像结果：源 {} {} 条 → 目标 {} {} 条，失败 {} 条"
                        + "（lookup 登记 {} 条，byType 登记 {} 条）。",
                source.registryName, sources.size(), TARGET_TYPE_ID, copies.size(), failed,
                addedToLookup, fresh.size());
        resultLogged = true;
    }

    private static GTRecipe copyOne(GTRecipeType target, GTRecipe src) {
        GTRecipeBuilder srcBuilder = new GTRecipeBuilder(src, src.getType());
        GTRecipeBuilder copy = target.copyFrom(srcBuilder);
        clearEuInput(copy);
        copy.EUt(MIRROR_EUT);
        clearFluidInputs(copy);
        return copy.buildRawRecipe();
    }

    private static List<GTRecipe> collect(RecipeManager manager, GTRecipeType type) {
        List<GTRecipe> out = new ObjectArrayList<>();
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        for (Recipe<?> recipe : manager.getRecipes()) {
            if (recipe.getType() != type || !(recipe instanceof GTRecipe gt)) {
                continue;
            }
            ResourceLocation id = gt.getId();
            if (id != null && !seen.add(id)) {
                continue;
            }
            out.add(gt);
        }
        return out;
    }

    /** EUt 只替换 tickInput 中的电量；先清掉普通输入中的 EU，避免复制后重复收费。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void clearEuInput(GTRecipeBuilder copy) {
        Map inputs = copy.input;
        Map tickInputs = copy.tickInput;
        inputs.remove(EURecipeCapability.CAP);
        tickInputs.remove(EURecipeCapability.CAP);
    }

    /** 用新列表替换流体输入，避免清空可能与源配方共享的列表。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void clearFluidInputs(GTRecipeBuilder copy) {
        Map inputs = copy.input;
        if (inputs.get(FluidRecipeCapability.CAP) != null) {
            inputs.put(FluidRecipeCapability.CAP, new ObjectArrayList<>());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> readByType(RecipeManager manager) {
        Field field = resolveByTypeField(manager);
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(manager);
            return value instanceof Map<?, ?> map
                    ? (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) map : null;
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 天基矿石处理厂配方镜像：读 byType 失败（已忽略）：{}", t.toString());
            return null;
        }
    }

    private static void writeByType(RecipeManager manager,
                                    Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> next) {
        Field field = resolveByTypeField(manager);
        if (field == null) {
            return;
        }
        try {
            field.set(manager, next);
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 天基矿石处理厂配方镜像：写 byType 失败（lookup 已登记，机器仍可跑；"
                    + "只是 JEI/计数看不到）：{}", t.toString());
        }
    }

    private static Field resolveByTypeField(RecipeManager manager) {
        if (byTypeField != null) {
            return byTypeField;
        }
        if (byTypeResolutionFailed) {
            return null;
        }
        for (String name : BY_TYPE_FIELD_NAMES) {
            try {
                Field candidate = RecipeManager.class.getDeclaredField(name);
                if (!Map.class.isAssignableFrom(candidate.getType())) {
                    continue;
                }
                candidate.setAccessible(true);
                Object value = candidate.get(manager);
                // 同名字段仍需核对键类型，避免把按 ID 索引的表误认成按配方类型索引的表。
                if (value instanceof Map<?, ?> map && !map.isEmpty() && !looksLikeByType(map)) {
                    continue;
                }
                byTypeField = candidate;
                return candidate;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable ignored) {
            }
        }
        for (Field candidate : RecipeManager.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(candidate.getType())) {
                continue;
            }
            try {
                candidate.setAccessible(true);
                if (candidate.get(manager) instanceof Map<?, ?> map && looksLikeByType(map)) {
                    byTypeField = candidate;
                    return candidate;
                }
            } catch (Throwable ignored) {
            }
        }
        byTypeResolutionFailed = true;
        GTLModular.LOGGER.warn("[gtlmodular] 天基矿石处理厂配方镜像：在 {} 上找不到「按配方类型索引」那张 Map"
                        + "（候选名 {} / 结构扫描都失败）⇒ 镜像仍会写 lookup（机器能跑），"
                        + "但 JEI 分类与「该表共 N 条」看不到；请把这条日志发回",
                RecipeManager.class.getName(), String.join("/", BY_TYPE_FIELD_NAMES));
        return null;
    }

    private static boolean looksLikeByType(Map<?, ?> map) {
        return !map.isEmpty() && map.keySet().iterator().next() instanceof RecipeType;
    }

    private static void logSkipOnce(String why) {
        if (skipLogged) {
            return;
        }
        skipLogged = true;
        GTLModular.LOGGER.info("[gtlmodular] 天基矿石处理厂配方镜像未执行（{}）。", why);
    }

    public static boolean isHandled() {
        return handled;
    }

    public static boolean isResultLogged() {
        return resultLogged;
    }
}
