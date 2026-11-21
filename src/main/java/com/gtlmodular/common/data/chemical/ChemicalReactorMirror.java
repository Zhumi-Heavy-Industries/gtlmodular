package com.gtlmodular.common.data.chemical;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.lookup.GTRecipeLookup;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtlmodular.GTLModular;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 化学反应釜（{@code gtceu:chemical_reactor}）→ 大型化学反应釜
 * （{@code gtceu:large_chemical_reactor}）的<b>配方兼容镜像</b>。
 *
 * <h2>为什么需要它</h2>
 * <p>GTCEu 1.4.4 的大型化学反应釜跑的是<b>独立配方类型</b> {@code gtceu:large_chemical_reactor}，
 * <b>不继承</b>单方块的 {@code gtceu:chemical_reactor}。所以任何只写了单方块版本的配方，
 * 小反应釜能做、大反应釜（以及复用该表的化工厂）做不出来 —— 玩家报的
 * 「做不出超级大脑」「新加的化学配方大化反里看不到」都是这个原因。</p>
 *
 * <p>本模组此前用「手写 <code>xxx_large.json</code>」补过 6 条（0.3.390），
 * 但整合包 KubeJS 里还有两百多条 {@code chemical_reactor}，逐条手补不现实、
 * 以后新加的配方还会继续漏。这里改成 <b>运行期自动镜像</b>。</p>
 *
 * <h2>时机与数据来源</h2>
 * <p>挂在 {@link ServerStartedEvent}（Forge 事件总线）。此刻配方管理器已就绪，
 * <b>数据包 JSON、KubeJS 脚本、GT 自己生成的配方</b>全部在
 * {@code RecipeManager.byType} 里（GTCEu 的 {@code RecipeManagerMixin} 会把所有
 * {@code GTRecipeType} 的 JSON 配方反序列化成 {@link GTRecipe} 并按类型收进 byType，
 * KubeJS 的 GT 配方也在 {@code injectRuntimeRecipes} 里转成 {@code GTRecipe} 后写进同一张表）。</p>
 *
 * <h2>做什么</h2>
 * <ol>
 *   <li>取 {@code byType[CHEMICAL_RECIPES]} 的<b>全部</b>配方（GTRecipe）；</li>
 *   <li>按<b>产物指纹</b>去重：目标表 {@code byType[LARGE_CHEMICAL_RECIPES]} 里已经有
 *       相同产物集合的配方（手写的 {@code _large.json}、KubeJS 的
 *       {@code gtr.large_chemical_reactor(...)}、GT 自己的大化反配方）⇒ 跳过，不重复造路；</li>
 *   <li>尺寸检查：LCR 的 {@code setMaxIOSize(3, 3, 5, 4)} 比单方块宽，但仍有个别配方超限，
 *       超限的跳过并计数（不硬塞进表，避免 JEI 里出现跑不了的配方）；</li>
 *   <li>逐条复制：{@code new GTRecipeBuilder(src, src.getType())} +
 *       {@code target.copyFrom(builder)} —— 输入/输出/概率/条件/NBT 原样带走，
 *       只把类型换成 target，id 换成 {@code gtlmodular:large_chemical_reactor/lcr_mirror/...}；</li>
 *   <li><b>两处一起登记</b>：{@code target.getLookup().addRecipe(...)}（机器找配方）
 *       + {@code byType} 目标那一格（JEI / 样板识别 / 计数）。只写 lookup 会
 *       「机器能跑但 JEI 空」，只写 byType 则机器不认 —— 这条教训本仓库已多次取证。</li>
 * </ol>
 *
 * <h2>幂等 / 容错</h2>
 * <ul>
 *   <li>每轮 {@code ServerStartedEvent} 自己在 {@code merged} 里按 id 去重，
 *       重复执行不会叠加；</li>
 *   <li>每条 try/catch（失败跳过并计数），整段 try/catch，<b>任何情况下不影响启动</b>；</li>
 *   <li>只<b>追加</b>镜像条目，绝不清空目标表（大化反原有配方一条不动）。</li>
 * </ul>
 */
public final class ChemicalReactorMirror {

    /** 镜像配方 id 路径前缀（最终 id 再由 buildRawRecipe 前置目标表名）。 */
    private static final String MIRROR_PATH_PREFIX = "lcr_mirror/";

    /** byType 反射字段：生产 SRG 名 {@code f_44007_} / 开发 official 名 {@code recipes}。 */
    private static Field byTypeField;

    /** byType 字段解析彻底失败 ⇒ 不再重试。 */
    private static volatile boolean byTypeResolutionFailed;

    private ChemicalReactorMirror() {
    }

    /** 在 {@code @Mod} 构造里调用一次（Forge 事件总线）。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ChemicalReactorMirror::onServerStarted);
        GTLModular.LOGGER.info("[gtlmodular] 大型化学反应釜兼容镜像已挂载：{} ← {}"
                        + "（服务端启动完成后全量复制；已有同产物 large 配方的跳过）",
                GTRecipeTypes.LARGE_CHEMICAL_RECIPES.registryName,
                GTRecipeTypes.CHEMICAL_RECIPES.registryName);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        try {
            install(event.getServer());
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 大型化学反应釜兼容镜像执行失败（已忽略，不影响启动）：{}",
                    t.toString());
        }
    }

    /** 本轮真正干活的入口（幂等：重复调用按 id/产物去重）。 */
    public static synchronized void install(MinecraftServer server) {
        if (server == null) {
            return;
        }
        GTRecipeType source = GTRecipeTypes.CHEMICAL_RECIPES;
        GTRecipeType target = GTRecipeTypes.LARGE_CHEMICAL_RECIPES;
        RecipeManager manager = server.getRecipeManager();
        if (manager == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 大型化学反应釜兼容镜像：拿不到配方管理器 ⇒ 跳过");
            return;
        }
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> byType = readByType(manager);
        if (byType == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 大型化学反应釜兼容镜像：拿不到 RecipeManager.byType ⇒ 跳过"
                    + "（配方仍在，只是大化反里看不到镜像条目）");
            return;
        }

        Map<ResourceLocation, Recipe<?>> sources = byType.get(source);
        if (sources == null || sources.isEmpty()) {
            GTLModular.LOGGER.info("[gtlmodular] 大型化学反应釜兼容镜像：源表 {} 没有配方 ⇒ 跳过",
                    source.registryName);
            return;
        }

        // 目标表现有配方：保留原样，只做追加
        Map<ResourceLocation, Recipe<?>> targetRecipes = byType.get(target);
        Map<ResourceLocation, Recipe<?>> merged = new LinkedHashMap<>();
        if (targetRecipes != null) {
            merged.putAll(targetRecipes);
        }
        Set<String> existingOutputs = new LinkedHashSet<>();
        for (Recipe<?> existing : merged.values()) {
            String key = outputKey(existing);
            if (key != null) {
                existingOutputs.add(key);
            }
        }

        GTRecipeLookup lookup = target.getLookup();
        int added = 0;
        int skipped = 0;
        int oversized = 0;
        int rejected = 0;
        int failed = 0;
        for (Map.Entry<ResourceLocation, Recipe<?>> entry : new ArrayList<>(sources.entrySet())) {
            Recipe<?> value = entry.getValue();
            if (!(value instanceof GTRecipe src)) {
                skipped++;
                continue;
            }
            String key = outputKey(src);
            if (key != null && existingOutputs.contains(key)) {
                // 已有一条大化反配方做同样产物 ⇒ 不再造第二条路
                skipped++;
                continue;
            }
            if (!fits(target, src)) {
                oversized++;
                continue;
            }
            try {
                GTRecipe mirror = copyTo(target, src, entry.getKey());
                if (mirror == null || mirror.getId() == null || merged.containsKey(mirror.getId())) {
                    skipped++;
                    continue;
                }
                if (lookup != null && !lookup.addRecipe(mirror)) {
                    rejected++;
                    continue;
                }
                merged.put(mirror.getId(), mirror);
                if (key != null) {
                    existingOutputs.add(key);
                }
                added++;
            } catch (Throwable t) {
                failed++;
                if (failed <= 5) {
                    GTLModular.LOGGER.warn("[gtlmodular] 大型化学反应釜兼容镜像失败（第 {} 条，已跳过）：{}",
                            failed, t.toString());
                }
            }
        }

        if (added == 0) {
            GTLModular.LOGGER.info("[gtlmodular] 大型化学反应釜兼容镜像结果：源 {} {} 条 → 追加 0 条"
                            + "（已有同产物 {} 条 / 超尺寸 {} 条 / 表拒绝 {} 条 / 失败 {} 条）",
                    source.registryName, sources.size(), skipped, oversized, rejected, failed);
            return;
        }

        // vanilla 的 byType 用 ImmutableMap 收口 ⇒ 复制外层表后整张换回去
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> next = new LinkedHashMap<>(byType);
        next.put(target, merged);
        if (!writeByType(manager, next)) {
            GTLModular.LOGGER.warn("[gtlmodular] 大型化学反应釜兼容镜像：byType 回写失败 ⇒ 机器能跑，但 JEI 可能不显示");
        }

        GTLModular.LOGGER.info("[gtlmodular] 大型化学反应釜兼容镜像结果：源 {} {} 条 → 追加 {} 条"
                        + "（已有同产物 {} 条 / 超尺寸 {} 条 / 表拒绝 {} 条 / 失败 {} 条）",
                source.registryName, sources.size(), added, skipped, oversized, rejected, failed);
    }

    /**
     * 把源表的一条配方复制成目标表的一条<b>新</b>配方（源对象一个字不改）。
     *
     * <p>{@code new GTRecipeBuilder(src, src.getType())} 把源配方的全部内容装进 builder；
     * {@code target.copyFrom(builder)} 沿用源 id、{@code onSave} 置空（不递归）、类型换 target；
     * 再显式改 id 后 {@code buildRawRecipe()} 会把目标表名前置一次，得到唯一 id。</p>
     */
    private static GTRecipe copyTo(GTRecipeType target, GTRecipe src, ResourceLocation sourceId) {
        GTRecipeBuilder builder = new GTRecipeBuilder(src, src.getType());
        GTRecipeBuilder copy = target.copyFrom(builder);
        String path = sourceId == null ? "unknown" : sourceId.toString().replace(':', '/');
        copy.id = new ResourceLocation(GTLModular.MOD_ID, MIRROR_PATH_PREFIX + path);
        return copy.buildRawRecipe();
    }

    /** 尺寸检查：超过目标表的 maxIOSize 就不镜像（否则 JEI 里会出现跑不了的配方）。 */
    private static boolean fits(GTRecipeType target, GTRecipe recipe) {
        return size(recipe.getInputContents(ItemRecipeCapability.CAP))
                <= target.getMaxInputs(ItemRecipeCapability.CAP)
                && size(recipe.getInputContents(FluidRecipeCapability.CAP))
                <= target.getMaxInputs(FluidRecipeCapability.CAP)
                && size(recipe.getOutputContents(ItemRecipeCapability.CAP))
                <= target.getMaxOutputs(ItemRecipeCapability.CAP)
                && size(recipe.getOutputContents(FluidRecipeCapability.CAP))
                <= target.getMaxOutputs(FluidRecipeCapability.CAP);
    }

    private static int size(List<Content> list) {
        return list == null ? 0 : list.size();
    }

    /**
     * 产物指纹：排序后的「物品 id + 流体 id」集合。
     *
     * <p>只比较<b>产物种类</b>（不比数量），够用来判断「大化反里是不是已经有一条做同样东西的配方」。
     * 这样手写的 {@code _large.json}、KubeJS 的 {@code gtr.large_chemical_reactor(...)}
     * 和 GT 自己的大化反配方都能挡住，不会重复造路。</p>
     */
    private static String outputKey(Recipe<?> recipe) {
        if (!(recipe instanceof GTRecipe gt)) {
            return null;
        }
        TreeSet<String> parts = new TreeSet<>();
        List<Content> items = gt.getOutputContents(ItemRecipeCapability.CAP);
        if (items != null) {
            for (Content content : items) {
                Object value = content == null ? null : content.getContent();
                if (value instanceof ItemStack stack && !stack.isEmpty()) {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (id != null) {
                        parts.add("i:" + id);
                    }
                }
            }
        }
        List<Content> fluids = gt.getOutputContents(FluidRecipeCapability.CAP);
        if (fluids != null) {
            for (Content content : fluids) {
                Object value = content == null ? null : content.getContent();
                if (value instanceof FluidStack stack) {
                    ResourceLocation id = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
                    if (id != null) {
                        parts.add("f:" + id);
                    }
                }
            }
        }
        return parts.isEmpty() ? null : String.join("|", parts);
    }

    @SuppressWarnings("unchecked")
    private static Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> readByType(RecipeManager manager) {
        if (manager == null || byTypeResolutionFailed) {
            return null;
        }
        try {
            if (byTypeField == null) {
                for (String name : new String[]{"f_44007_", "recipes"}) {
                    try {
                        Field field = RecipeManager.class.getDeclaredField(name);
                        field.setAccessible(true);
                        byTypeField = field;
                        break;
                    } catch (NoSuchFieldException ignored) {
                        // 换下一个候选名
                    }
                }
                if (byTypeField == null) {
                    byTypeResolutionFailed = true;
                    return null;
                }
            }
            return (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) byTypeField.get(manager);
        } catch (Throwable t) {
            byTypeResolutionFailed = true;
            return null;
        }
    }

    private static boolean writeByType(RecipeManager manager,
                                       Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> map) {
        try {
            if (byTypeField == null) {
                return false;
            }
            byTypeField.set(manager, map);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
