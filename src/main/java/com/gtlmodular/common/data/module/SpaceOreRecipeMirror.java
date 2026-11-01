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

/**
 * 天基矿石处理厂（{@code gtceu:space_ore_processor}）配方镜像 —— 「把同族矿石处理配方可靠地复制一份过来」。
 *
 * <h2>★ 2026-10-01（第二轮）：旧方案已废弃，原因见下（三行实机日志）</h2>
 * <p><b>废弃的是「配方构建回调」这条路</b>（原实现：{@code AddPackFindersEvent(HIGHEST, SERVER_DATA)}
 * 里给源类型挂 {@code GTRecipeType.onRecipeBuild(BiConsumer)}）。实机日志：
 * <pre>
 * [23:29:54] [gtlmodular] 天基矿石处理厂配方镜像已挂载：gtceu:space_ore_processor ← gtceu:integrated_ore_processor
 *            （…电压 480 EU/t…）                                    ← ① 挂载确实成功了
 * [23:29:54] [gtlmodular] …配方镜像：gtceu:integrated_ore_processor 一条配方都没经过回调 ⇒ 本轮镜像无效。
 *                                                                     ← ② 但回调一次都没触发
 * [23:30:29] [GTLCore] Slow GTCEu JEI recipe type registration: type=gtceu:steam_space_ore_processor, recipes=999
 *                                                                     ← ③ 同族的 steam 变体有 999 条，内容可用
 * </pre>
 * ⇒ <b>结论 1</b>：那 999 条同族配方<b>不是</b>经 {@code type.recipeBuilder(...)} 构建的
 * （或至少在「源类型模板上挂的 {@code onSave}」这条链上不留痕）⇒ {@code onRecipeBuild} 回调
 * <b>结构上就收不到它们</b>，「挂载成功但零回调」是必然结果，不是时序问题。
 * <b>结论 2</b>：{@code gtceu:steam_space_ore_processor} 实机 999 条 ⇒ 同一族、内容可用。
 * <b>结论 3</b>：本模组已有一个<b>每轮都可靠</b>的晚钩子 —— NAC 自检挂在
 * {@link net.minecraftforge.event.server.ServerStartedEvent}（见 {@link com.gtlmodular.common.command.GTLModularCommands}），
 * 那时配方管理器已就绪（自检能数出 413 / 228 / 999 等）。
 *
 * <h2>本方案（v2）：配方加载完成之后，从「配方管理器」里枚举 → 复制 → 双向登记</h2>
 * <ol>
 *   <li><b>时机</b>：{@code ServerStartedEvent}（Forge 事件总线，与 NAC 自检同一时刻同一写法）。
 *       此刻数据包（含 {@code gtceu:dynamic_data} 那 999 条）已经全部加载完，
 *       {@code server.getRecipeManager()} 里什么都拿得到。</li>
 *   <li><b>源类型</b>：默认 {@link #BASE_TYPE_ID} = {@code gtceu:steam_space_ore_processor}
 *       （日志实证 999 条）。若它在本环境里一条都没有（别的 gtladditions 分支/别的包版本），
 *       自动回落到 {@link #FALLBACK_TYPE_ID} = {@code gtceu:integrated_ore_processor}
 *       （历史上也是 999 条）。<b>「一条都没有」就不动手</b>，绝不清空任何表。</li>
 *   <li><b>枚举</b>：遍历 {@code RecipeManager} 的全部配方，取 {@code recipe.getType() == 源类型}
 *       的 {@code GTRecipe}（照 {@code NanochipSelfCheck.countAutoRecipes} 的既有做法）。</li>
 *   <li><b>复制</b>：用 <b>{@code GTRecipeBuilder.copyFrom(源 builder)}</b> 做——见
 *       {@link #copyOne} 的注释与下面的 API 取证表。</li>
 *   <li><b>登记</b>：<b>两处都要写</b>，这是本轮的关键教训：
 *       <ul>
 *         <li><b>① lookup</b>（机器用）：{@code target.getLookup().removeAllRecipes()} 后
 *             {@code target.getLookup().addRecipe(copy)} —— 引擎找配方走 {@code GTRecipeLookup}；</li>
 *         <li><b>② byType</b>（JEI / 计数 / 样板识别用）：往 {@code RecipeManager.byType} 的
 *             目标类型那一格 put 这些 copy —— JEI 分类与「该表共 N 条」读的是它。</li>
 *       </ul>
 *       只写 lookup 就会「机器能跑但 JEI 空、自检数 0」（本仓库
 *       {@link com.gtlmodular.common.data.nanochip.NanochipProxyTableByTypeAlias} 的类注释
 *       已把这条教训逐条取证过）；只写 byType 则机器压根不认。两处一起写才自洽。</li>
 *   <li><b>幂等</b>：目标类型在 {@code byType} 里<b>已经有配方</b> ⇒ 直接跳过（打一行 INFO），
 *       一条都不加、一条都不清。运行期重复触发（{@code /reload} 等）同样被这一条挡住。</li>
 *   <li><b>容错</b>：每条 try/catch（失败的跳过并计数）；整段 try/catch；
 *       <b>任何情况下都不影响游戏启动</b>，也<b>绝不动源类型</b>。</li>
 * </ol>
 *
 * <h2>API 取证（全部对 {@code gtceu-1.20.1-1.4.4.jar} / 本仓库既有已编译代码核实，2026-10-01）</h2>
 * <table border="1">
 *   <tr><th>API</th><th>签名</th><th>依据</th></tr>
 *   <tr><td>{@code GTRecipeType.copyFrom}</td>
 *       <td>{@code public GTRecipeBuilder copyFrom(GTRecipeBuilder)}</td>
 *       <td>{@code _nac_holmium/GTRecipeBuilder.txt:397}（javap -c 逐偏移）</td></tr>
 *   <tr><td>{@code GTRecipeBuilder.copyFrom}</td>
 *       <td>字节码 = {@code arg.copy(arg.id).onSave(null).recipeType(this.recipeType)}
 *           ⇒ 造<b>全新</b> builder（源对象一个字不改）、沿用源 id、
 *           {@code onSave} 置空（<b>不会递归</b>）、类型换成目标</td>
 *       <td>同上，偏移 0-19</td></tr>
 *   <tr><td>{@code GTRecipeBuilder.buildRawRecipe}</td>
 *       <td>{@code public GTRecipe buildRawRecipe()}；最终 id =
 *           {@code id.withPrefix(recipeType.registryName.getPath() + "/")}
 *           ⇒ {@code gtceu:space_ore_processor/<原名>}，与源配方 id 不撞</td>
 *       <td>同文件偏移 3167 起</td></tr>
 *   <tr><td>{@code GTRecipeType.recipeBuilder}</td>
 *       <td>{@code public GTRecipeBuilder recipeBuilder(ResourceLocation, Object...)}</td>
 *       <td>被本包大量调用（如 {@code _census/out/javap_MachineRecipeLoader.txt}）</td></tr>
 *   <tr><td>{@code GTRecipeType.getLookup}</td>
 *       <td>{@code public GTRecipeLookup getLookup()}</td>
 *       <td>{@code gtl-modular/…/NanochipProxyTableByTypeAlias.java:442} 已在用</td></tr>
 *   <tr><td>{@code GTRecipeLookup.addRecipe}</td>
 *       <td>{@code public boolean addRecipe(GTRecipe)}（<b>返回 boolean</b>）</td>
 *       <td>同上文件 {@code :452} 已在用（{@code if (… && lookup.addRecipe(gt))}）</td></tr>
 *   <tr><td>{@code GTRecipeLookup.removeAllRecipes}</td>
 *       <td>{@code public void removeAllRecipes()}</td>
 *       <td>GTCEu 装载端同款调用（{@code RecipeManagerMixin} 偏移 47-52，见上述类注释）</td></tr>
 *   <tr><td>{@code GTRecipe.getType}</td>
 *       <td>{@code public GTRecipeType getType()}（{@code GTRecipe implements Recipe<Container>}）</td>
 *       <td>{@code _census/_gtceu_GTRecipe_copy.javap.txt:337}；{@code NanochipSelfCheck.java:1236} 同款用法</td></tr>
 *   <tr><td>{@code RecipeManager} 的 {@code byType}</td>
 *       <td>字段：生产 {@code f_44007_} / 开发（official 映射）{@code recipes}；<b>非 final</b>
 *           ⇒ 复制外层 {@code LinkedHashMap} 后整张 set 回去</td>
 *       <td>{@code NanochipProxyTableByTypeAlias.java:180} 的字段名表 + {@code :298-300} 的写法（已在用）</td></tr>
 * </table>
 *
 * <h2>软依赖 / 容错</h2>
 * <ul>
 *   <li>源/目标类型一律走 <b>{@code GTRegistries.RECIPE_TYPES.get(ResourceLocation)}</b> 弱解析
 *       （本模组既有做法，见 {@code api/module/ModuleKind}），
 *       <b>完全不 import gtladditions 的任何类</b> ⇒ 没装 gtladditions 时不会类加载失败，
 *       拿不到就是 {@code null}，静默跳过。</li>
 *   <li>没有服务器实例 / 配方管理器拿不到 ⇒ 直接返回，什么都不做。</li>
 * </ul>
 *
 * <h2>开关</h2>
 * <p>{@code config/gtlmodular-common.toml} 的 {@code spaceOre.recipeMirror}（默认 {@code true}）。
 * 在 ServerStarted 时读一次 ⇒ <b>改它需要重启游戏</b>（或至少重新进世界）。
 *
 * @see com.gtlmodular.common.config.GTLModularConfig
 * @see com.gtlmodular.common.data.nanochip.NanochipProxyTableByTypeAlias
 */
public final class SpaceOreRecipeMirror {

    private SpaceOreRecipeMirror() {
    }

    /**
     * 镜像的<b>源</b>配方表（首选）：{@code gtceu:steam_space_ore_processor}。
     *
     * <p>2026-10-01 实机日志实证：{@code Slow GTCEu JEI recipe type registration:
     * type=gtceu:steam_space_ore_processor, recipes=999} —— 同族、内容可用。
     * （旧的源 {@code gtceu:integrated_ore_processor} 那 999 条<b>走不通回调</b>，见类注释。）
     */
    public static final ResourceLocation BASE_TYPE_ID = new ResourceLocation("gtceu", "steam_space_ore_processor");

    /**
     * 镜像的<b>源</b>配方表（回落）：{@code gtceu:integrated_ore_processor}。
     *
     * <p>只有首选源在本环境里<b>一条配方都没有</b>时才用它（历史日志里它也是 999 条）。
     * 两个源的内容同族；本方案最后会把电压钳到 {@link #MIRROR_EUT} 并清空流体输入，
     * 所以用哪一个都不会把「蒸汽口径」带进目标表（见 {@link #copyOne}）。
     */
    public static final ResourceLocation FALLBACK_TYPE_ID = new ResourceLocation("gtceu", "integrated_ore_processor");

    /**
     * 目标配方表：gtladditions 的「天基矿石处理厂」类型 —— <b>也就是本模组模块C
     * {@code gtlmodular:molten_space_ore_module}（猪咪撕咬炼矿机）真正订阅的那张表</b>。
     *
     * <p>取证（本仓库）：
     * <ul>
     *   <li>{@code api/module/ModuleKind.java:28-32} —— {@code SPACE_ORE_PROCESSOR} 的
     *       {@code recipeTypeId = new ResourceLocation("gtceu", "space_ore_processor")}；</li>
     *   <li>{@code common/data/GTLModularMachines.java:462} ——
     *       {@code GTRecipeType recipeType = GTRegistries.RECIPE_TYPES.get(kind.recipeTypeId())}；</li>
     *   <li>{@code common/data/GTLModularMachines.java:468-470} ——
     *       {@code REGISTRATE.multiblock("molten_space_ore_module", MoltenSpaceOreModuleMachine::new)…recipeType(recipeType)}
     *       ⇒ 定义里那张表就是 {@code gtceu:space_ore_processor}（机器读配方走
     *       {@code machine.getRecipeType().getLookup()}，见 {@code MoltenStripperModuleMachine.java:503} 同款）。
     *   </li>
     * </ul>
     * ⇒ <b>镜像目标 = {@code gtceu:space_ore_processor} 正确，不需要改目标。</b>
     * （gtladditions 用 {@code GTRecipeTypes.register("space_ore_processor", …)} 注册，
     * 落在 {@code gtceu:} 命名空间；实机日志有 {@code [gtlmodular] M4 已注册机器:
     * gtlmodular:molten_space_ore_module} ⇒ 该解析确实拿得到。）
     */
    public static final ResourceLocation TARGET_TYPE_ID = new ResourceLocation("gtceu", "space_ore_processor");

    /**
     * 镜像配方的电压：{@code GTValues.VA[GTValues.HV]} = 512 EU/t。
     *
     * <p>与 gtladditions 上游 {@code RecipesModify.initSpaceOreProcessor()} 里的
     * {@code .EUt(VA[GTValues.HV].toLong())} 保持一致 —— 这不是我们的口味，是那个配方表本来的规格。
     */
    public static final long MIRROR_EUT = GTValues.VA[GTValues.HV];

    /** {@code RecipeManager.byType} 的候选字段名：生产（SRG）/ 开发（official 映射）。 */
    private static final String[] BY_TYPE_FIELD_NAMES = {"f_44007_", "recipes"};

    private static final Object LOCK = new Object();

    /** 本轮是否已经处理过（幂等：同一 JVM 只处理第一次 ServerStarted）。 */
    private static volatile boolean handled;

    /** 处理失败过（不再重试，避免每次进世界都炸一次）。 */
    private static volatile boolean handleFailed;

    /** 跳过原因只喊一次。 */
    private static volatile boolean skipLogged;

    /** 结果日志只喊一次。 */
    private static volatile boolean resultLogged;

    /** 已解析到的 {@code byType} 字段。 */
    private static Field byTypeField;

    /** {@code byType} 字段解析彻底失败 ⇒ 不再重试。 */
    private static volatile boolean byTypeResolutionFailed;

    /**
     * 在 {@code @Mod} 构造里调用一次（见 {@code GTLModular}）。
     *
     * <p><b>2026-10-01 废弃</b>：这里原本挂两个 {@code AddPackFindersEvent}（HIGHEST 挂回调 / LOWEST 打结果）。
     * 实机证明「挂载成功但回调一次未触发」（见类注释的三行日志）⇒ 整条路已删除，改为
     * {@code ServerStartedEvent}（Forge 事件总线）里做「加载完之后枚举 + 复制 + 双向登记」。
     *
     * <pre>
     * // ❌ 旧方案（2026-10-01 废弃）：modBus.addListener(EventPriority.HIGHEST,
     * //        (AddPackFindersEvent e) -&gt; onPackFindersEarly(e));
     * //    modBus.addListener(EventPriority.LOWEST,
     * //        (AddPackFindersEvent e) -&gt; onPackFindersLate(e));
     * //    ⇒ 实机「已挂载」但「一条配方都没经过回调」⇒ onRecipeBuild 收不到那 999 条
     * </pre>
     *
     * @param modBus {@code FMLJavaModLoadingContext.get().getModEventBus()}（本方案不再使用，
     *               仅保留签名以兼容 {@code GTLModular} 里的既有调用点）
     */
    public static void register(IEventBus modBus) {
        // ServerStartedEvent 是 **Forge 事件总线**上的事件（不是 mod 总线）；
        // 与 NAC 自检（GTLModularCommands.onServerStarted）同一时刻、同一写法。
        MinecraftForge.EVENT_BUS.addListener(SpaceOreRecipeMirror::onServerStarted);
        GTLModular.LOGGER.info("[gtlmodular] 天基矿石处理厂配方镜像已挂载：{} ← {}（回落到 {}）"
                        + "，在服务器启动完成后（配方管理器就绪）执行「枚举 + 复制 + 双向登记」。",
                TARGET_TYPE_ID, BASE_TYPE_ID, FALLBACK_TYPE_ID);
    }

    /** 服务器（含单人存档的集成服务器）启动完成 —— 此刻配方管理器已就绪（NAC 自检同一时机）。 */
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

    /**
     * 本轮真正干活的入口：解析类型 → 幂等闸 → 枚举源配方 → 逐条复制 → 双向登记。
     *
     * @param server 服务器实例（{@code server.getRecipeManager()} 是唯一的配方来源）
     */
    private static void mirrorNow(MinecraftServer server) {
        if (server == null) {
            logSkipOnce("拿不到服务器实例");
            return;
        }
        GTRecipeType target = GTRegistries.RECIPE_TYPES.get(TARGET_TYPE_ID);
        if (target == null) {
            // 静默跳过：没装 gtladditions 时就是这个分支（目标类型不存在）。
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

        // ---- 幂等闸（★ 任务要求：目标类型已有配方 ⇒ 直接跳过）----
        Map<ResourceLocation, Recipe<?>> existing = byType.get(target);
        int existingCount = existing == null ? 0 : existing.size();
        if (existingCount > 0) {
            logSkipOnce("目标配方表 " + TARGET_TYPE_ID + " 已有 " + existingCount
                    + " 条配方 ⇒ 本轮不镜像（幂等，一条都不加、一条都不清）");
            return;
        }

        // ---- 选源：首选 steam 变体（实机 999 条），一条都没有才回落 ----
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

        // ---- 逐条复制（每条 try/catch；失败的跳过并计数）----
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

        // ---- 登记 ①：lookup（机器真正取配方的地方）----
        GTRecipeLookup lookup = target.getLookup();
        int addedToLookup = 0;
        if (lookup != null) {
            // 目标表本轮按幂等闸是空的；先清一次是为了扛住「byType 空但 lookup 非空」的异常残留，
            // 保证「lookup 里的目标表配方」与「byType 里那一格」逐条一致。
            lookup.removeAllRecipes();
            for (GTRecipe copy : copies) {
                if (lookup.addRecipe(copy)) {
                    addedToLookup++;
                }
            }
        }

        // ---- 登记 ②：byType（JEI 分类 / 「该表共 N 条」/ 样板识别读的地方）----
        // 键用配方自己的 id（buildRawRecipe 已把它前缀成 gtceu:space_ore_processor/<原名>，天然唯一）；
        // 万一拿到 null 就自己造一个不撞的序号 id 兜底（绝不让 put 覆盖掉前面那条）。
        Map<ResourceLocation, Recipe<?>> fresh = new LinkedHashMap<>();
        for (GTRecipe copy : copies) {
            ResourceLocation id = copy.getId();
            if (id == null || fresh.containsKey(id)) {
                id = new ResourceLocation(TARGET_TYPE_ID.getNamespace(),
                        TARGET_TYPE_ID.getPath() + "/mirrored_" + fresh.size());
            }
            fresh.put(id, copy);
        }
        // vanilla 的 apply 用 ImmutableMap 收口 ⇒ 复制一份外层表再整张换回去（只加/换一个键）。
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> next = new LinkedHashMap<>(byType);
        next.put(target, fresh);
        writeByType(manager, next);

        GTLModular.LOGGER.info("[gtlmodular] 天基矿石处理厂配方镜像结果：源 {} {} 条 → 目标 {} {} 条，失败 {} 条"
                        + "（lookup 登记 {} 条，byType 登记 {} 条）。",
                source.registryName, sources.size(), TARGET_TYPE_ID, copies.size(), failed,
                addedToLookup, fresh.size());
        resultLogged = true;
    }

    /**
     * 把源表的一条配方复制成目标表的一条<b>新</b>配方（源对象一个字不改）。
     *
     * <p>四步（每一步的 API 都已在类注释的取证表里核实）：
     * <ol>
     *   <li>{@code new GTRecipeBuilder(src, src.getType())} —— 把源配方的<b>全部内容</b>装进一个 builder
     *       （这个构造器 javap 实证存在；它会连 {@code ingredientActions} / 概率逻辑 / 条件一起搬）；</li>
     *   <li>{@code target.copyFrom(srcBuilder)} —— 它就是
     *       {@code srcBuilder.copy(srcBuilder.id).onSave(null).recipeType(target)}
     *       ⇒ <b>沿用源 id、不递归、类型换目标</b>，且造的是<b>全新</b> builder（源对象一个字不改）。
     *       最终 id 由 {@code buildRawRecipe} 加前缀成 {@code gtceu:space_ore_processor/<原名>}，不会与源撞；</li>
     *   <li>把 EU 输入摘干净后钳到 {@link #MIRROR_EUT}（见 {@link #clearEuInput}）；</li>
     *   <li>清空流体输入（见 {@link #clearFluidInputs}）—— {@code space_ore_processor} 的流体输入格数为 0，
     *       带流体输入的配方在那台机器上永远匹配不上（gtladditions 上游同款处理）。</li>
     * </ol>
     *
     * <p><b>为什么不用 {@code new GTRecipeBuilder(src, target)}</b>：那个构造器（javap 实证存在）会把
     * {@code ingredientActions} 写成 {@code List.of()}（见 {@code GTRecipe.buildRawRecipe} 的字节码），
     * 而 {@code copyFrom} 会原样保留源的 {@code ingredientActions} / 概率逻辑 / 条件 ⇒ 复制更「全」。
     *
     * @param target 目标配方表（{@code gtceu:space_ore_processor}）
     * @param src    源配方（{@code gtceu:steam_space_ore_processor} 的一条，来自配方管理器）
     * @return 复制出来的目标类型配方
     */
    private static GTRecipe copyOne(GTRecipeType target, GTRecipe src) {
        GTRecipeBuilder srcBuilder = new GTRecipeBuilder(src, src.getType());
        GTRecipeBuilder copy = target.copyFrom(srcBuilder);
        clearEuInput(copy);
        copy.EUt(MIRROR_EUT);
        clearFluidInputs(copy);
        return copy.buildRawRecipe();
    }

    /**
     * 枚举 {@code RecipeManager} 里某一配方类型的<b>全部</b> {@code GTRecipe}。
     *
     * <p>照 {@code NanochipSelfCheck.countAutoRecipes}（该文件 1235-1249 行）的既有做法：
     * 遍历 {@code manager.getRecipes()}，按 {@code recipe.getType()} 身份比较过滤。
     * 同一 id 只取一次（防重）。
     */
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

    /**
     * 先把复制件里可能存在的 EU 输入摘掉，再设 {@link #MIRROR_EUT}（512 EU/t）。
     *
     * <p>实测 {@code GTRecipeBuilder.EUt(long)} 的完整字节码：它<b>只</b>
     * {@code tickInput.remove(EURecipeCapability.CAP)} 然后 {@code inputEU(eut)}（因为临时把 {@code perTick}
     * 置 true，{@code inputEU} 又写回 {@code tickInput}），最后<b>还原原来的 {@code perTick}</b>
     * ⇒ 对「EU 在 {@code tickInput} 里」这种标准形态它是一次干净的<b>替换</b>。
     * 若基础配方是用 {@code inputEU(...)}（{@code perTick=false}）造的，EU 会落在 {@code input} 里，
     * 那时 {@code EUt(512)} 只会往 {@code tickInput} 再塞一份 ⇒ 复制件变成两份 EU。
     * 这里先按目标语义把两处都摘掉，保证结果里<b>有且只有一份</b> EU 输入。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void clearEuInput(GTRecipeBuilder copy) {
        Map inputs = copy.input;
        Map tickInputs = copy.tickInput;
        inputs.remove(EURecipeCapability.CAP);
        tickInputs.remove(EURecipeCapability.CAP);
    }

    /**
     * 清空复制件的流体输入。
     *
     * <p>{@code space_ore_processor} 注册时是 {@code setMaxIOSize(2, 9, 0, 0)} —— 流体输入格数为
     * <b>0</b>，所以带流体输入的配方到这台机器上永远匹配不上；gtladditions 上游同样是
     * {@code spaceBuilder.input[FluidRecipeCapability.CAP]?.clear()}。
     * 同理，蒸汽源（{@code steam_space_ore_processor}）的蒸汽口径也在这里被抹掉，
     * ⇒ <b>用 steam 变体当源，不会把「蒸汽压力」带进目标表。</b>
     *
     * <p>这里用 {@code put} 换一个新列表，而<b>不是</b>对取出来的列表 {@code clear()}：
     * {@code copyFrom} 内部造的是新 builder、{@code input} 是每个 builder 自己的
     * {@code public final Map}，但列表元素是否与源共享没有取证 ⇒ 宁可换列表，
     * 保证<b>绝不碰源配方那一份</b>。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void clearFluidInputs(GTRecipeBuilder copy) {
        Map inputs = copy.input;
        if (inputs.get(FluidRecipeCapability.CAP) != null) {
            inputs.put(FluidRecipeCapability.CAP, new ObjectArrayList<>());
        }
    }

    // ------------------------------------------------------------------ byType 读写

    /** 读 {@code RecipeManager.byType}（外层表）。拿不到返回 {@code null}（调用方静默跳过）。 */
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

    /** 写回 {@code RecipeManager.byType}（外层表整张换；只加/换目标类型一个键）。 */
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

    /**
     * 解析 {@code byType} 字段：先按名字（SRG / official），再按结构兜底（认「键是 {@code RecipeType}」的那张 Map）。
     *
     * <p>写法与 {@code NanochipProxyTableByTypeAlias.resolve} 同款（那边已实机在用）；
     * 失败只打一条 WARN 并停手，<b>绝不抛异常</b>。
     */
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
                // 名字撞车保护：有值时必须是「键为 RecipeType」的那张表（byName 的键是 ResourceLocation）
                if (value instanceof Map<?, ?> map && !map.isEmpty() && !looksLikeByType(map)) {
                    continue;
                }
                byTypeField = candidate;
                return candidate;
            } catch (NoSuchFieldException ignored) {
                // 这个环境没有这个名字 ⇒ 试下一个
            } catch (Throwable ignored) {
                // 单个字段读不到就跳过
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
                // 单个字段读不到就跳过
            }
        }
        byTypeResolutionFailed = true;
        GTLModular.LOGGER.warn("[gtlmodular] 天基矿石处理厂配方镜像：在 {} 上找不到「按配方类型索引」那张 Map"
                        + "（候选名 {} / 结构扫描都失败）⇒ 镜像仍会写 lookup（机器能跑），"
                        + "但 JEI 分类与「该表共 N 条」看不到；请把这条日志发回",
                RecipeManager.class.getName(), String.join("/", BY_TYPE_FIELD_NAMES));
        return null;
    }

    /** 结构判据：这张 Map 的键是 {@code RecipeType}（byName 那张的键是 {@code ResourceLocation}）。 */
    private static boolean looksLikeByType(Map<?, ?> map) {
        return !map.isEmpty() && map.keySet().iterator().next() instanceof RecipeType;
    }

    // ------------------------------------------------------------------ 日志

    /** 跳过原因只喊一次（可能多轮进世界）。 */
    private static void logSkipOnce(String why) {
        if (skipLogged) {
            return;
        }
        skipLogged = true;
        GTLModular.LOGGER.info("[gtlmodular] 天基矿石处理厂配方镜像未执行（{}）。", why);
    }

    /** 供外部/自检查询：本轮是否已经成功处理过（只读）。 */
    public static boolean isHandled() {
        return handled;
    }

    /** 供外部/自检查询：结果日志是否已经打过（只读）。 */
    public static boolean isResultLogged() {
        return resultLogged;
    }
}
