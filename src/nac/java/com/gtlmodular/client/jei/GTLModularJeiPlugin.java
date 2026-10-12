package com.gtlmodular.client.jei;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.client.config.NanochipConfigRefresh;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeTypeCategory;
import com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper;
import com.gtlmodular.common.data.nanochip.NanochipRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IEditModeConfig;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 只隐藏孤儿物品，保留注册和配方，以免破坏存档中已有物品。 */
@JeiPlugin
public final class GTLModularJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID =
            NanochipModContext.earlyRegistryId("jei_plugin");

    private static final String LIST_RESOURCE = "/assets/gtlmodular/jei/hidden_orphans.txt";

    /** 主机有独立配方表，保持关闭以免把无法加工的电路组装机配方显示到主机分类。 */
    private static final boolean REGISTER_ASSEMBLY_COMPLEX_RECIPES = false;

    private static volatile List<String> hiddenIds;

    private static volatile IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registerAssemblyComplexRecipes(registration);
    }

    private static void registerAssemblyComplexRecipes(IRecipeRegistration registration) {
        if (!REGISTER_ASSEMBLY_COMPLEX_RECIPES) {
            return;
        }
        try {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection == null) {
                return;
            }
            RecipeManager recipeManager = connection.getRecipeManager();
            List<GTRecipe> recipes = new ArrayList<>();
            for (Recipe<?> recipe : recipeManager.getAllRecipesFor(GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES)) {
                if (recipe instanceof GTRecipe gtRecipe) {
                    recipes.add(gtRecipe);
                }
            }
            if (recipes.isEmpty()) {
                NanochipModContext.logger().info("[gtlmodular] JEI：电路组装机表里没有配方 ⇒ 主机分类（{}）保持为空",
                        NanochipRecipeTypes.ASSEMBLY_COMPLEX.registryName);
                return;
            }
            registration.addRecipes(
                    GTRecipeTypeCategory.TYPES.apply(NanochipRecipeTypes.ASSEMBLY_COMPLEX),
                    recipes.stream().map(GTRecipeWrapper::new).toList());
            NanochipModContext.logger().info("[gtlmodular] JEI：已把 {} 条配方登记进主机分类（{}）",
                    recipes.size(), NanochipRecipeTypes.ASSEMBLY_COMPLEX.registryName);
        } catch (Throwable t) {
            // 单个兼容插件的失败不能中断其他 JEI 分类注册。
            NanochipModContext.logger().warn("[gtlmodular] JEI：登记主机分类配方失败（忽略）：{}", t.toString());
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        NanochipConfigRefresh.register(GTLModularJeiPlugin::refresh);
        refresh();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** 经可选回调接入配置屏，避免未安装 JEI 时加载插件类型。 */
    public static void refresh() {
        IJeiRuntime rt = runtime;
        if (rt == null) {
            return;
        }
        apply(rt, NanochipModContext.config().isHideOrphanItemsEnabled());
    }

    public static List<String> hiddenIds() {
        return loadIds();
    }

    private static void apply(IJeiRuntime rt, boolean hide) {
        List<String> ids = loadIds();
        if (ids.isEmpty()) {
            return;
        }
        int changed = 0;
        int skipped = 0;
        for (String id : ids) {
            try {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl == null) {
                    skipped++;
                    continue;
                }
                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item == null) {
                    skipped++;
                    continue;
                }
                Optional<ITypedIngredient<ItemStack>> typed =
                        rt.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, new ItemStack(item));
                if (typed.isEmpty()) {
                    skipped++;
                    continue;
                }
                IEditModeConfig cfg = rt.getEditModeConfig();
                if (hide) {
                    cfg.hideIngredientUsingConfigFile(typed.get(), IEditModeConfig.HideMode.SINGLE);
                } else {
                    cfg.showIngredientUsingConfigFile(typed.get(), IEditModeConfig.HideMode.SINGLE);
                }
                changed++;
            } catch (Throwable t) {
                NanochipModContext.logger().warn("[gtlmodular] JEI 隐藏清单处理失败：{}（{}）", id, t.toString());
            }
        }
        NanochipModContext.logger().info("[gtlmodular] JEI 孤儿件隐藏：{} {} 件（清单 {} 件，跳过 {} 件）",
                hide ? "隐藏" : "恢复可见", changed, ids.size(), skipped);
    }

    private static List<String> loadIds() {
        List<String> cached = hiddenIds;
        if (cached != null) {
            return cached;
        }
        synchronized (GTLModularJeiPlugin.class) {
            if (hiddenIds != null) {
                return hiddenIds;
            }
            hiddenIds = Collections.unmodifiableList(readList());
            return hiddenIds;
        }
    }

    private static List<String> readList() {
        Set<String> out = new LinkedHashSet<>();
        try (InputStream in = GTLModularJeiPlugin.class.getResourceAsStream(LIST_RESOURCE)) {
            if (in == null) {
                NanochipModContext.logger().warn("[gtlmodular] 找不到 JEI 隐藏清单资源 {}（跳过隐藏）", LIST_RESOURCE);
                return new ArrayList<>();
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String s = line.trim();
                    if (s.isEmpty() || s.startsWith("#")) {
                        continue;
                    }
                    out.add(s);
                }
            }
        } catch (Throwable t) {
            NanochipModContext.logger().warn("[gtlmodular] 读取 JEI 隐藏清单失败（跳过隐藏）：{}", t.toString());
            return new ArrayList<>();
        }
        return new ArrayList<>(out);
    }
}
