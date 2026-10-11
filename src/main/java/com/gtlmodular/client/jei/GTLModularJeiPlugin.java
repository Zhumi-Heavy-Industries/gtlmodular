package com.gtlmodular.client.jei;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeTypeCategory;
import com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
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

/**
 * GTL·更多实用设备 的 JEI 插件 —— <b>两件事</b>：
 * <ol>
 *   <li><b>①</b> 把「NAC 孤儿封装件」在 JEI 里隐藏（改前就有的功能，见下面各节）；</li>
 *   <li><b>②</b> ★ 2026-09-19（队长：「给主机注册本模组自己的类型（显示「纳米芯片组装复合体」…）」）：
 *       把主机真正能跑的那批配方登记进**主机自己那张表的 JEI 分类**里
 *       （{@link #registerRecipes}），否则那个分类会是一个标题正确、内容全空的空白页。</li>
 * </ol>
 *
 * <h2>① 为什么是"隐藏"而不是"删掉"（2026-09-19 队长原话）</h2>
 * 「那 42 个孤儿的话，就先隐藏吧」——那 42 件（清单见 {@code _census/残留物品清理_队员.md} §8.1）
 * 的定义是「<b>有产出配方（玩家能拿到）、但现存配方里没有任何一条消费它</b>」。
 * 删注册/注销 Entry 会毁存档（它们可能是存档里已有的物品），所以本插件：
 * <ul>
 *   <li><b>不删注册</b>：{@code NanochipCircuitComponents} 一个 Entry 都不动；</li>
 *   <li><b>不动配方</b>：切割室/换算配方原样保留，物品照旧能被合成出来；</li>
 *   <li><b>不碰 {@code retired}</b>：注销名单是另一条线的口径；</li>
 *   <li>只调用 JEI <b>自己的"隐藏物品"机制</b>（{@link IEditModeConfig#hideIngredientUsingConfigFile}），
 *       也就是玩家在 JEI 编辑模式里手动点"隐藏"用的那套 —— 语义完全一致，且写在 JEI 自己的配置文件里。</li>
 * </ul>
 * ⇒ 隐藏后这 42 件<b>仍可</b>通过配方、{@code /give}、创造模式物品栏拿到，只是不再出现在 JEI 的
 * 物品列表/搜索里。
 *
 * <h2>开关键</h2>
 * {@code config/gtlmodular-common.toml} → {@code nanochipComplex.hideOrphanItems}
 * （默认 {@code true} = 隐藏；{@link GTLModularConfig#isHideOrphanItemsEnabled()}）。
 * 也可以在游戏内 {@code Mods → GTL更多实用设备 → 配置} 里点开关：配置屏保存时会调用
 * {@link #refresh()}（配置屏走反射调用，避免在没装 JEI 的客户端上加载本类）。
 * <ul>
 *   <li>开 → 对清单里每一件调用 {@code hideIngredientUsingConfigFile(SINGLE)}；</li>
 *   <li>关 → 对清单里每一件调用 {@code showIngredientUsingConfigFile(SINGLE)}，
 *       <b>主动把之前隐藏的撤销</b>，恢复可见。</li>
 * </ul>
 *
 * <h2>清单在哪</h2>
 * {@code assets/gtlmodular/jei/hidden_orphans.txt}（classpath 资源，每行一个 {@code ns:path}，
 * {@code #} 注释）。它是<b>单一真源</b>：由 {@code _census/_gen_hidden_orphans_list.js}
 * 从 §8.1 表格现读生成，`_census/check_orphan_hidden.js` 也读同一个文件做对账。
 * <p>读不到资源 / 某个 id 已不在注册表里 → <b>静默跳过</b>，绝不因为"隐藏清单"本身报错而影响启动。
 */
@JeiPlugin
public final class GTLModularJeiPlugin implements IModPlugin {

    /** 本插件唯一 id。 */
    private static final ResourceLocation PLUGIN_UID =
            new ResourceLocation(GTLModular.MOD_ID, "jei_plugin");

    /** 隐藏清单资源路径（classpath）。 */
    private static final String LIST_RESOURCE = "/assets/gtlmodular/jei/hidden_orphans.txt";

    /**
     * ★ 2026-10-14（代理剪断）：<b>「把电路组装机那批配方手工灌进主机 JEI 分类」这个钩子的开关，现为 {@code false} = 停用</b>。
     *
     * <p>为什么停：这个钩子当年是因为主机表**没有自己的 JSON**（JEI 分类会是一页空白）才手工补的。
     * 现在主机表有了自己的 7 条 JSON（{@code data/gtlmodular/recipes/nanochip/assembly_complex/}，
     * {@code "type"} = 主机表 id）⇒ 客户端 {@code byType} 里天然有这些条目 ⇒ GTCEu 的
     * {@code GTJEIPlugin.registerRecipes} 会自动把它们登记进「纳米芯片组装复合体」分类
     * （该插件读的就是 {@code RecipeManager.getAllRecipesFor(type)}），本钩子<b>不再必要</b>；
     * 而且 2026-10-14 剪断代理后，主机<b>已经跑不了</b>那批电路组装机配方，把它们登记进主机分类就是误导玩家。
     * <p>改 {@code true} 即恢复原行为（手工把电路组装机表的配方再灌一份进主机分类）；代码一行未删。
     * <p>另：那 42 件孤儿物品的隐藏功能（本插件的 ① 号职责）与本开关<b>无关</b>，不受影响。
     */
    private static final boolean REGISTER_ASSEMBLY_COMPLEX_RECIPES = false;

    /** 清单（惰性加载一次；不可变）。 */
    private static volatile List<String> hiddenIds;

    /** JEI 运行时引用（JEI 未就绪时为 null）。 */
    private static volatile IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    /**
     * ★ 2026-09-22 新增：给主机自己的配方表补齐 JEI 分类内容。
     *
     * <h4>为什么必须在这里补（GTCEu 自己不补）</h4>
     * JEI 分类里显示的配方<b>不是</b>取 {@code GTRecipeType.getLookup()}，而是取
     * {@code RecipeManager.getAllRecipesFor(type)}（= 数据包 {@code byType} 里 {@code "type"} 等于该类型 id 的那些）——
     * {@code GTJEIPlugin.registerRecipes} 的字节码里就是
     * {@code Minecraft.getInstance().getConnection().getRecipeManager().m_44013_(recipeType)}。
     * 而主机表 {@link NanochipRecipeTypes#ASSEMBLY_COMPLEX}（id {@code gtlmodular:nanochip_assembly_complex}）
     * <b>故意</b>没有任何数据包 JSON（它靠 GTCEu 的代理机制索引电路组装机那批**同一批对象**，见该字段注释）
     * ⇒ GTCEu 为它建的分类会拿到一个**空列表**（一个空白页）。这里把"主机真能跑的那批"补进去。
     *
     * <h4>为什么这样补是安全的</h4>
     * <ul>
     *   <li><b>只读</b>：读的正是 GTCEu 自己读的那个源（客户端 RecipeManager 的 {@code byType}），
     *       所以"JEI 里列出来的"与"主机真能跑的"逐条同源；</li>
     *   <li><b>不改任何配方/数据</b>：只是把已有 {@code GTRecipe} 对象包成 JEI 的 wrapper；</li>
     *   <li><b>顺序无关</b>：JEI 的 {@code RecipeRegistration.addRecipes} 走
     *       {@code RecipeManagerInternal.addRecipes} → {@code RecipeTypeData.addRecipes}
     *       那一支是 {@code List.addAll}（javap 实证）——是<b>累加</b>而不是覆盖，
     *       所以 GTCEu 先跑（登记空表）还是我们先跑都正确；</li>
     *   <li><b>分类已注册</b>：JEI 的加载顺序是「所有插件的 registerCategories ⇒ 所有插件的 registerRecipes」，
     *       而 GTCEu 的 {@code registerCategories} 会为每个注册进 {@code BuiltInRegistries.RECIPE_TYPE}
     *       的 GTRecipeType（含本表）建分类 ⇒ 这里 {@code addRecipes} 时分类一定已存在；</li>
     *   <li><b>失败不影响 JEI</b>：整段 try/catch，任何异常只记一条日志（JEI 插件抛异常会连累整个 JEI）。</li>
     * </ul>
     * <p>副作用（如实标注）：同一批配方在 JEI 里会出现在<b>两个</b>分类下 ——
     * 电路组装机（GTCEu 自己的机器用那张表）与「纳米芯片组装复合体」（主机）——这是**对的**：
     * 两台机器确实都能跑这批配方。
     *
     * <p>★ 2026-10-14（代理剪断）：<b>以上整段描述的是改前状态</b>（含上面那句"主机表故意没有任何数据包 JSON"）。
     * 主机表剪断代理后不再索引电路组装机配方，而且它已经有**自己的 7 条 JSON**
     * ⇒ 客户端 {@code byType} 里天然有条目、GTCEu 会自动把它们登记进主机分类
     * ⇒ 本钩子已停用（{@link #REGISTER_ASSEMBLY_COMPLEX_RECIPES} = {@code false}），
     * 主机分类只显示主机真能跑的那 7 条。历史取证原样保留供追溯。
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registerAssemblyComplexRecipes(registration);
    }

    /**
     * ★ 2026-10-14（代理剪断）：<b>已停用</b>（见 {@link #REGISTER_ASSEMBLY_COMPLEX_RECIPES}）——
     * 第一行早退，其余代码<b>原样保留</b>。停用后主机分类的内容由 GTCEu 自己按
     * {@code getAllRecipesFor(ASSEMBLY_COMPLEX)} 自动登记（= 主机自己那 7 条真配方）。
     */
    private static void registerAssemblyComplexRecipes(IRecipeRegistration registration) {
        if (!REGISTER_ASSEMBLY_COMPLEX_RECIPES) {
            return;   // ★ 2026-10-14：钩子停用 ⇒ 一行早退，不再往主机分类里灌电路组装机那批配方
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
                GTLModular.LOGGER.info("[gtlmodular] JEI：电路组装机表里没有配方 ⇒ 主机分类（{}）保持为空",
                        NanochipRecipeTypes.ASSEMBLY_COMPLEX.registryName);
                return;
            }
            registration.addRecipes(
                    GTRecipeTypeCategory.TYPES.apply(NanochipRecipeTypes.ASSEMBLY_COMPLEX),
                    recipes.stream().map(GTRecipeWrapper::new).toList());
            GTLModular.LOGGER.info("[gtlmodular] JEI：已把 {} 条配方登记进主机分类（{}）",
                    recipes.size(), NanochipRecipeTypes.ASSEMBLY_COMPLEX.registryName);
        } catch (Throwable t) {
            // JEI 插件抛异常会连累整个 JEI ⇒ 一律吞掉并记日志（分类最多是空的，不影响启动与其它分类）
            GTLModular.LOGGER.warn("[gtlmodular] JEI：登记主机分类配方失败（忽略）：{}", t.toString());
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        refresh();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    // ---------------------------------------------------------------- 对外

    /**
     * 按当前配置重新应用一次隐藏/显示（幂等）。JEI 还没就绪时是空操作。
     *
     * <p>配置屏保存时会反射调用它；JEI 运行时可用时也会调一次。
     */
    public static void refresh() {
        IJeiRuntime rt = runtime;
        if (rt == null) {
            return;
        }
        apply(rt, GTLModularConfig.isHideOrphanItemsEnabled());
    }

    /** 隐藏清单（只读；供体检/日志用）。 */
    public static List<String> hiddenIds() {
        return loadIds();
    }

    // ---------------------------------------------------------------- 内部

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
                    // 注册表里没有这一件（比如被别的线注销了）⇒ 不报错，跳过。
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
                // JEI 内部状态异常（配置还没落盘 / 运行时切换中）→ 记一条，不中断其它件。
                GTLModular.LOGGER.warn("[gtlmodular] JEI 隐藏清单处理失败：{}（{}）", id, t.toString());
            }
        }
        GTLModular.LOGGER.info("[gtlmodular] JEI 孤儿件隐藏：{} {} 件（清单 {} 件，跳过 {} 件）",
                hide ? "隐藏" : "恢复可见", changed, ids.size(), skipped);
    }

    /** 读清单（缓存；异常一律退化成空表，绝不让隐藏逻辑影响游戏启动）。 */
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
                GTLModular.LOGGER.warn("[gtlmodular] 找不到 JEI 隐藏清单资源 {}（跳过隐藏）", LIST_RESOURCE);
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
            GTLModular.LOGGER.warn("[gtlmodular] 读取 JEI 隐藏清单失败（跳过隐藏）：{}", t.toString());
            return new ArrayList<>();
        }
        return new ArrayList<>(out);
    }
}
