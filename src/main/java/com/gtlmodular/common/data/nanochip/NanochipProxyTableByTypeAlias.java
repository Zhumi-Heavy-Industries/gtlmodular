package com.gtlmodular.common.data.nanochip;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.lookup.Branch;
import com.gregtechceu.gtceu.api.recipe.lookup.GTRecipeLookup;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.machine.MoltenPatternAssemblyPartMachine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ★★★ 2026-10-14（队长：「<b>剪断 NAC 对「电路组装机」的代理关系</b>」）：<b>本类已停用（总开关 = false）</b> ★★★
 *
 * <p>为什么停用：本类整段是给「主机表在 {@code byType} 里恒为空」打的补丁（成因是那时的主机表**没有**自己的 JSON，
 * 只靠 GTCEu 的代理表机制吃电路组装机那批配方）。2026-09-22 起主机表已经有了**自己的 7 条 JSON**
 * （{@code data/gtlmodular/recipes/nanochip/assembly_complex/}，{@code "type"} = 主机表 id）
 * ⇒ vanilla {@code byType} 里主机表**天然**有一格、装那 7 条 ⇒ 本条"别名键"（把主机表指到电路组装机那一格）
 * <b>本来就已多余</b>；2026-10-14 再剪断代理后，它更会与"只让主机吃自己的 7 条"这个新口径**背道而驰**
 * （别名会把 141 条电路组装机配方重新喂回主机表）⇒ 按队长令<b>用最小改动停用它</b>：
 * 只加一个总开关 {@link #ENABLED} = {@code false}，两个事件入口各加一行早退；<b>代码一行未删</b>，
 * 把 {@link #ENABLED} 改回 {@code true} 即整段复活。
 *
 * <p>⚠️ 保留的代码仍会随事件被调用（只做一次 {@code boolean} 判断就返回），不注册任何东西、不读反射、无副作用。
 *
 * <hr>
 * <p><b>以下为停用前的原文（含 2026-09-21 的根因取证链，原样保留供追溯）</b>：
 *
 * ★★ 2026-09-21（队长选 B：「名字保留『纳米芯片组装复合体』+ 真能跑」）★★
 *
 * <h3>这个类治的是什么（0.1.36 主机「机器模式完全用不了」的根因）</h3>
 *
 * <b>一句话</b>：主机表 {@code gtlmodular:nanochip_assembly_complex} 是一张<b>代理配方表</b>——
 * GTCEu 只会把电路组装机的配方<b>克隆进它的 {@code GTRecipeLookup}</b>，
 * <b>绝不会</b>在 vanilla 的 {@code RecipeManager} 按类型索引（{@code byType}）里给它建条目；
 * 而「主机吃不吃得到样板」这条链路上，判据读的恰恰是 {@code byType}。
 *
 * <h4>证据一：装载端只写 lookup，不写 byType（javap -c 逐偏移）</h4>
 * 两处填充点形状完全一致，<b>最后一跳都是 {@code GTRecipeLookup.addRecipe(...)}</b>：
 * <ol>
 *   <li>数据包侧 {@code com/gregtechceu/gtceu/core/mixins/RecipeManagerMixin.class}
 *       （{@code @Inject(method="apply(...)", at=@At("TAIL"))}，javap -v 实证）：
 *       <ul>
 *         <li>偏移 0/3/34：遍历 {@code BuiltInRegistries.f_256990_}（= {@code RECIPE_TYPE}）；</li>
 *         <li>偏移 47-52：{@code gtType.getLookup().removeAllRecipes()}；</li>
 *         <li>偏移 122-224：对 {@code getProxyRecipes()} 每一项 {@code list.clear()} 后，
 *             用 {@code this.f_44007_}（= {@code byType}）里<b>代理表</b>那一格经
 *             {@code toGTrecipe} 填回 list；</li>
 *         <li>偏移 230-241：判 {@code this.f_44007_.containsKey(gtType)}；本表不在 byType 里 ⇒ 落到偏移 342；</li>
 *         <li>偏移 342-381：{@code proxyRecipes.values().stream().flatMap(List::stream)}
 *             → {@code lambda$gtceu$cloneVanillaRecipes$1}（偏移 0-5）
 *             = <b>{@code gtType.getLookup().addRecipe(recipe)}</b>。</li>
 *       </ul>
 *       ⇒ 配方进了<b>我们自己的 lookup</b>，{@code byType} 一个键都没加。</li>
 *   <li>KubeJS 侧 {@code com/gregtechceu/gtceu/integration/kjs/GregTechKubeJSPlugin.injectRuntimeRecipes}
 *       （同一套形状，javap -c）：偏移 57-62 {@code getLookup().removeAllRecipes()}；
 *       偏移 132-235 用传入的运行时配方表（= {@code RecipesEventJS.post} 的局部表，
 *       就是它随后整张赋给 {@code f_44007_}/{@code f_199900_} 的那张，偏移 1026-1035）按
 *       {@code lambda$injectRuntimeRecipes$43}（{@code entry.getValue().getType() == key}）重填代理 list；
 *       偏移 241-332 再 {@code forEach(lambda$injectRuntimeRecipes$46)} =
 *       <b>{@code gtType.getLookup().addRecipe(recipe)}</b>。</li>
 * </ol>
 * <p>⇒ <b>结论：装载端确实会把电路组装机的配方克隆进我们这张表 —— 但只克隆进 lookup，
 * 不会在 {@code byType} 里给我们建条目。</b>队长问的「GTCEu 到底会不会克隆到共享同一原版配方类型的第二张 GT 表」
 * 的答案是<b>会</b>；坏掉的不是 lookup，是 {@code byType}。
 *
 * <h4>证据二：我们的表在 byType 里永远是空的（可复核的事实链）</h4>
 * {@code byType} 的键只可能来自「某条被加载配方的 {@code getType()}」（vanilla {@code apply} 与
 * KubeJS {@code post} 都是 {@code computeIfAbsent(recipe.getType(), …)}）。而没有任何配方属于我们这张表：
 * <ul>
 *   <li>本模组 591 条 JSON 里 {@code "type": "gtlmodular:nanochip_assembly_complex"} 出现 <b>0 次</b>（队长已统计）；</li>
 *   <li>整合包 {@code kubejs/}（含 workspace 的脚本备份）里搜 {@code nanochip_assembly_complex} <b>0 命中</b>；</li>
 *   <li>GTCEu / gtlcore / 其它 mod 不可能引用我们的类型 id。</li>
 * </ul>
 * ⇒ {@code RecipeManager.getAllRecipesFor(ASSEMBLY_COMPLEX)} <b>恒为空</b>（不是"加载慢"，是结构上不可能非空）。
 *
 * <h4>证据三：主机「吃不到样板」这条链路读的就是 byType（"完全用不了"的直接原因）</h4>
 * 主机由「猪咪样板总成」喂料。{@code MoltenPatternAssemblyPartMachine.detectRecipeType(int, List)}
 * （{@code common/machine/MoltenPatternAssemblyPartMachine.java:584}）逐类型读
 * {@code level.getRecipeManager().getAllRecipesFor(type)} 做样板身份匹配；它扫的是控制器
 * {@code getRecipeTypes()} 数组（简单模式 = 下标 0 本表 + 下标 1 换算表）⇒ 下标 0 恒 0 条
 * ⇒ 电路组装机样板识别不出类型 ⇒ {@code recipeTypeOfSlot} 恒 -1
 * ⇒ 自动切模式（{@code findModeWithWaitingStock}：{@code type < 0} 直接 skip，该文件第 896 行）
 * 永远选不到下标 0，{@code lockRecipeTypeForSlot} 只能保持当前模式
 * ⇒ 玩家看到的就是"这个模式找不到/跑不了配方"。
 *
 * <p><b>运行时实证（队长 0.1.36 实机日志）</b>：
 * {@code …\profiles\GTL推荐新手玩版（较难）_versions_GTL\logs\latest.log}
 * <pre>
 * [17:01:35] [gtlmodular] 自动切模式检查: 当前=0/2 状态=IDLE 找到目标=-1 样板=1 类型已知=[] 有料槽=[]
 * [17:03:40] [gtlmodular] 自动切模式检查: 当前=0/2 状态=IDLE 找到目标=-1 样板=2 类型已知=[] 有料槽=[]
 * [17:22:28] [gtlmodular] 自动切模式检查: 当前=0/2 状态=IDLE 找到目标=-1 样板=3 类型已知=[] 有料槽=[3]
 * </pre>
 * （{@code 当前=0/2} = 简单模式两张表、正停在<b>下标 0 = 主机表</b>；{@code 样板=1/2/3} = 仓里有样板；
 * {@code 类型已知=[]} = 一条都没识别出类型；{@code 有料槽=[3]} = 槽 3 里料已经推进来了。
 * 同一份日志里换算样板（下标 1）却能识别出来（{@code 类型已知=[1=1 2=1 …]}），
 * 因为换算表<b>有</b>自己的 JSON ⇒ byType 有条目 ⇒ 识别正常。）
 * 旁证：本模组 JEI 插件当年就是为同一个空洞才手工把电路组装机的 141 条塞进主机分类
 * （{@code GTLModularJeiPlugin.registerAssemblyComplexRecipes}，读的也是 {@code getAllRecipesFor(父表)}）。
 *
 * <h3>修法（三段，都最小且互不干扰）</h3>
 *
 * <h4>① 服务端给 byType 补一个「只读别名键」（治根因）</h4>
 * 每个配方重载之后，往 {@code RecipeManager.byType} 里放
 * {@code byType.put(ASSEMBLY_COMPLEX, byType.get(CIRCUIT_ASSEMBLER_RECIPES))} ——
 * 值就是父表那张内层 {@code Map} 的<b>同一个实例</b>：
 * <ul>
 *   <li><b>恒等</b>：别名里的对象就是父表里那批 {@code GTRecipe}，不重建、不复制、不改数
 *       （与 {@code NanochipRecipeTypes.ProxiedRecipeType#toGTrecipe} 的恒等语义一致）；</li>
 *   <li><b>不动父表数据</b>：只往<b>外层</b>表加一个键，父表那一格（键与内层 Map）逐项不变，
 *       更不会给电路组装机加/删任何配方；内层 Map 我们只读；</li>
 *   <li><b>幂等 + 自愈</b>：已在就跳过；外层 Map 被整张换掉（原版重载 / KubeJS
 *       {@code RecipesEventJS.post} 偏移 1026-1035）之后下一个 tick 自动补回 ——
 *       所以用<b>tick 轮询</b>而不是"一次性事件"，才扛得住 KubeJS 晚于装载端的替换；</li>
 *   <li><b>挂在 {@code TickEvent.Phase.START}</b>：Forge 补丁后的 {@code MinecraftServer.m_5705_}
 *       偏移 6 = {@code ForgeEventFactory.onPreServerTick}（= START），偏移 20 = {@code m_5703_}（tickChildren，
 *       各维度 level tick / 机器 tick）⇒ <b>START 一定早于本 tick 里的机器 tick</b>
 *       （javap -c 实证）⇒ 世界加载后的第一次样板识别一定在别名到位之后；</li>
 *   <li><b>只装服务端</b>：读 byType 的那条链路（样板类型识别 / AE 上传解析）全在服务端；
 *       客户端<b>故意不装</b> —— 本模组 JEI 插件已在客户端手工登记 141 条，
 *       若客户端 byType 也有条目，GTCEu 的 {@code GTRecipeTypeCategory} 会再登记一遍
 *       （JEI {@code RecipeTypeData.addRecipes} 是 {@code List.addAll} 累加）⇒ 主机分类里每条配方出现两次。</li>
 * </ul>
 *
 * <h4>② 清掉样板总成里「已被判成识别不出」的旧缓存（否则老存档上看不出任何变化）</h4>
 * 样板总成把每个槽位的识别结果<b>存进了存档</b>：
 * {@code MoltenPatternAssemblyPartMachine.saveCustomPersistedData}（该文件 1559-1581 行）把
 * {@code slotRecipeType} 里所有 {@code != -1} 的项稀疏写进 NBT
 * {@code gtlmodular$slot_recipe_type_sparse}，{@code loadCustomPersistedData}（1589-1612 行）原样读回；
 * 而 {@code recipeTypeOfSlot}（449-473 行）在 {@code cached == -2}（{@code TYPE_UNKNOWN}）时
 * <b>直接返回 -1、不再扫配方表</b>（这是作者为扣料热路径做的正确优化）。
 * ⇒ 队长那台机器在 0.1.36 里已经把电路组装机样板槽判成 -2 并落盘了；只装别名的话，
 * 重新进世界这些槽<b>仍然</b>是"识别不出"，玩家会以为修法无效。
 * <br>所以这里在服务端每次<b>区块加载</b>时，把这台机器缓存里的 {@code -2} 改回 {@code -1}
 * （"还没试过"，见该文件 444-446 行常量），让它按新环境重扫一次：
 * <ul>
 *   <li>只改<b>派生缓存</b>，不碰样板、不碰手动指定（{@code manualSlotTypes}）、不碰配方；</li>
 *   <li>不设"只清一次"的会话闸：区块来回加载时会再清一次并重扫，这是想要的
 *       （清 -2 → 重扫 → 要么识别成功、要么重新判 -2；单槽重扫 = 扫该类型那几百条配方，代价可忽略）；</li>
 *   <li>顺序天然正确：世界加载期的区块加载发生在 tick 1 之前 → 那时缓存已清、别名还没装，
 *       但机器<b>首次</b>探测发生在 tick 1 的 level tick 里，而别名在 tick 1 的 START 就装好了
 *       ⇒ 首次探测一定能看到别名；玩家登录后加载的区块则在别名已就位之后才清缓存 ⇒ 同样正确；</li>
 *   <li>反射的是<b>本模组自己的字段名</b>（{@code slotRecipeType}，mod 字段不参与混淆），
 *       拿不到就只打一条 WARN 并停手，并提示"手动把那个样板槽重放一次"作为兜底。</li>
 * </ul>
 *
 * <h4>③ 防呆：只在自家 lookup 真的空时恒等回填（按证据一应当是 no-op）</h4>
 * {@link #refillLookupIfEmpty} 只在主机表<b>自己的 lookup 彻底为空</b>时，把别名里那批对象恒等灌进去。
 * 有闸门 ⇒ 非空绝不动（不可能重复写、不可能造出重复配方），且只写<b>我们自己的</b> lookup。
 *
 * <h4>不确定性（如实标注）</h4>
 * <ul>
 *   <li>反射字段名：生产（SRG）{@code f_44007_}、开发（official 映射，本工程
 *       {@code mappings channel: 'official'}）{@code recipes}；两者都试，再退回
 *       "扫 {@code RecipeManager} 所有 {@code Map} 字段、取键是 {@code RecipeType} 的那个"结构兜底；
 *       三条都失败只打一条 WARN 并停手（<b>绝不抛异常</b>）。</li>
 *   <li>本类只解决"byType 里没有我们的表"。若实机上主机表<b>自己的 lookup</b> 也是空的
 *       （与证据一相反），③ 会兜住；两条都失效才会仍然"用不了"，
 *       此时 {@code 自动切模式检查} 日志里的 {@code 类型已知} 仍会是 {@code []}，把那一行发回即可定案。</li>
 *   <li>仅完成编译验证（{@code compileJava}），<b>未跑游戏</b>；② 的实际效果要等队长进存档看
 *       {@code 类型已知=[…]} 是否出现下标 0。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NanochipProxyTableByTypeAlias {

    private NanochipProxyTableByTypeAlias() {
    }

    /**
     * ★ 2026-10-14（代理剪断）：<b>总开关，现为 {@code false} = 本类彻底不工作</b>。
     *
     * <p>改 {@code true} 即恢复 2026-10-14 之前的全部行为（往 {@code byType} 补别名键 + 作废陈旧的
     * "识别不出"缓存 + 空 lookup 防呆回填），代码一行未删。
     * <p>为什么停：主机表如今有自己的 7 条 JSON（{@code byType} 天然有条目），且代理已剪断 ——
     * 别名会把电路组装机那 141 条重新喂回主机表，与"主机只吃自己的 7 条"矛盾。详见类注释。
     */
    private static final boolean ENABLED = false;

    /** 轮询间隔（tick）：5 tick 一次。单次代价 = 一次反射读字段 + 一次 HashMap.containsKey。 */
    private static final int CHECK_INTERVAL_TICKS = 5;

    /**
     * {@code RecipeManager.byType} 的候选字段名：
     * 生产（SRG）{@code f_44007_}、开发（official 映射）{@code recipes}。
     * （javap -p {@code net.minecraft.world.item.crafting.RecipeManager}：
     * {@code private Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> f_44007_;}，
     * <b>非 final</b> ⇒ 反射可替换；紧邻的 {@code f_199900_} 是 byName，别认错。）
     */
    private static final String[] BY_TYPE_FIELD_NAMES = {"f_44007_", "recipes"};

    /** {@code MoltenPatternAssemblyPartMachine.slotRecipeType} 里「还没尝试识别」（= 该文件 445 行 TYPE_NOT_TRIED）。 */
    private static final int TYPE_NOT_TRIED = -1;

    /** {@code MoltenPatternAssemblyPartMachine.slotRecipeType} 里「试过但识别不出」（= 该文件 446 行 TYPE_UNKNOWN）。 */
    private static final int TYPE_UNKNOWN = -2;

    /** 已解析到的 byType 字段（字段属于类，与环境无关 ⇒ 缓存一次）。 */
    private static Field byTypeField;

    /** byType 字段解析彻底失败 ⇒ 不再重试（只打一条 WARN）。 */
    private static boolean resolutionFailed;

    /** 样板总成识别缓存字段（{@code slotRecipeType}）。 */
    private static Field slotTypeCacheField;

    /** 识别缓存字段拿不到 ⇒ 不再重试（只打一条 WARN）。 */
    private static boolean slotTypeCacheFieldFailed;

    /** 首次安装成功是否已打过日志。 */
    private static boolean installedLogged;

    /** 防呆回填是否已打过日志。 */
    private static boolean refillLogged;

    /** 清陈旧识别缓存是否已打过日志。 */
    private static boolean cacheClearLogged;

    /** 意外异常是否已打过日志（打一条就够，不刷屏）。 */
    private static boolean failureLogged;

    private static int tickCounter;

    /**
     * 服务端 tick：保证别名在位。
     *
     * <p>挂在 {@code Phase.START}：Forge 补丁后的 {@code MinecraftServer.m_5705_} 偏移 6 是
     * {@code onPreServerTick}(= START)、偏移 20 才是 {@code m_5703_}(tickChildren → 机器 tick)
     * ⇒ START 一定早于本 tick 里的任何样板识别（javap -c 实证），而世界加载后的第一次探测就在 tick 1。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED) {
            return;   // ★ 2026-10-14（代理剪断）：总开关关 ⇒ 一行早退，不做任何事
        }
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        if (tickCounter++ % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        try {
            install(server.getRecipeManager());
        } catch (Throwable t) {
            // 诊断型代码绝不允许影响游戏：只记一条日志，下一轮继续尝试
            if (!failureLogged) {
                failureLogged = true;
                GTLModular.LOGGER.warn("[gtlmodular] 主机表 byType 别名：安装过程异常（已忽略，不影响启动）：{}", t.toString());
            }
        }
    }

    /**
     * 服务端区块加载：把样板总成里「已被判成识别不出」的槽位缓存改回「还没试过」，让它按新环境重扫。
     *
     * <p>为什么必须做：那份缓存是<b>存档里的</b>（{@code MoltenPatternAssemblyPartMachine}
     * 1559-1581 写、1589-1612 读），而 {@code recipeTypeOfSlot} 命中 {@code -2} 时直接返回 -1 不再扫表
     * ⇒ 不清理的话，老存档上本修法"看不出任何变化"。
     * <p>只动派生缓存：不碰样板、不碰手动指定、不碰配方。
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!ENABLED) {
            return;   // ★ 2026-10-14（代理剪断）：总开关关 ⇒ 不再动样板总成的识别缓存
        }
        if (!(event.getLevel() instanceof ServerLevel)) {
            return;   // 只在服务端做：读 byType 的那条链路在服务端
        }
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            // ★ 注意：MoltenPatternAssemblyPartMachine 是 GT 的「多方块部件机器」（MetaMachine），
            //   本身不是 BlockEntity ⇒ 必须经 IMachineBlockEntity#getMetaMachine() 拿（javap 实证：
            //   IMachineBlockEntity.getMetaMachine() 是抽象方法，BE 实现里持有那台机器）。
            if (blockEntity instanceof IMachineBlockEntity holder
                    && holder.getMetaMachine() instanceof MoltenPatternAssemblyPartMachine machine) {
                invalidateStaleTypeCache(machine);
            }
        }
    }

    /** ① 给主机表在 {@code byType} 里补只读别名键；幂等、自愈。 */
    private static void install(RecipeManager manager) throws IllegalAccessException {
        if (manager == null || resolutionFailed) {
            return;
        }
        GTRecipeType alias = NanochipRecipeTypes.ASSEMBLY_COMPLEX;
        if (alias == null) {
            return;
        }
        Field field = resolve(manager);
        if (field == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> byType =
                (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) field.get(manager);
        if (byType == null) {
            return;
        }
        // 父表那一格：没有 / 还空着（加载中）⇒ 什么都不做，下个检查点再看
        Map<ResourceLocation, Recipe<?>> parent = byType.get(GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES);
        if (parent == null || parent.isEmpty()) {
            return;
        }
        if (!byType.containsKey(alias)) {
            // vanilla apply 用 ImmutableMap 收口 ⇒ 复制一份外层表再换字段。
            // 只加一个键；值 = 父表内层 Map 的同一个实例（只读共享，绝不写入）。
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> next = new LinkedHashMap<>(byType);
            next.put(alias, parent);
            field.set(manager, next);
            if (!installedLogged) {
                installedLogged = true;
                GTLModular.LOGGER.info("[gtlmodular] 主机表 byType 别名已挂上：{} → 电路组装机那 {} 条（恒等共享，不动父表）",
                        alias.registryName, parent.size());
            }
        }
        refillLookupIfEmpty(parent);
    }

    /** 解析 {@code byType} 字段：先按名字，再按结构兜底；失败只打一条 WARN。 */
    private static Field resolve(RecipeManager manager) {
        if (byTypeField != null) {
            return byTypeField;
        }
        if (resolutionFailed) {
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
                // 名字撞车保护：有值时必须是"键为 RecipeType"的那张表（byName 的键是 ResourceLocation）
                if (value instanceof Map<?, ?> map && !map.isEmpty() && !looksLikeByType(map)) {
                    continue;
                }
                byTypeField = candidate;
                return candidate;
            } catch (NoSuchFieldException ignored) {
                // 这个环境没有这个名字 ⇒ 试下一个
            } catch (Throwable t) {
                if (!failureLogged) {
                    failureLogged = true;
                    GTLModular.LOGGER.warn("[gtlmodular] 主机表 byType 别名：试探字段 {} 时报错（已忽略）：{}", name, t.toString());
                }
            }
        }
        // 结构兜底：扫所有 Map 字段，认"键是 RecipeType"的那一个
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
        resolutionFailed = true;
        GTLModular.LOGGER.warn("[gtlmodular] 主机表 byType 别名：在 {} 上找不到「按配方类型索引」那张 Map"
                        + "（候选名 {} / 结构扫描都失败）⇒ 本修法在当前 MC/映射环境下失效，"
                        + "主机模式会退回「完全用不了」，请把这条日志发回",
                RecipeManager.class.getName(), String.join("/", BY_TYPE_FIELD_NAMES));
        return null;
    }

    /** 结构判据：这张 Map 的键是 {@code RecipeType}（byName 那张的键是 {@code ResourceLocation}）。 */
    private static boolean looksLikeByType(Map<?, ?> map) {
        if (map.isEmpty()) {
            return false;
        }
        return map.keySet().iterator().next() instanceof RecipeType;
    }

    /** ② 把 {@code slotRecipeType} 里的 {@code -2}（试过但识别不出）改回 {@code -1}（还没试过）。 */
    private static void invalidateStaleTypeCache(MoltenPatternAssemblyPartMachine machine) {
        try {
            Field field = resolveSlotTypeCacheField();
            if (field == null) {
                return;
            }
            int[] cache = (int[]) field.get(machine);
            if (cache == null) {
                return;
            }
            int cleared = 0;
            for (int i = 0; i < cache.length; i++) {
                if (cache[i] == TYPE_UNKNOWN) {
                    cache[i] = TYPE_NOT_TRIED;
                    cleared++;
                }
            }
            if (cleared > 0 && !cacheClearLogged) {
                cacheClearLogged = true;
                GTLModular.LOGGER.info("[gtlmodular] 主机表 byType 别名：已作废样板总成 {} 个「识别不出」的旧缓存（存档里存的 -2）"
                        + "⇒ 那些样板槽会按新环境重扫一次 @{}", cleared, machine.getPos());
            }
        } catch (Throwable t) {
            if (!failureLogged) {
                failureLogged = true;
                GTLModular.LOGGER.warn("[gtlmodular] 主机表 byType 别名：清样板识别缓存时异常（已忽略）：{}", t.toString());
            }
        }
    }

    /** 解析样板总成的识别缓存字段（本模组自己的字段名，不参与混淆 ⇒ 生产/开发同名）。 */
    private static Field resolveSlotTypeCacheField() {
        if (slotTypeCacheField != null) {
            return slotTypeCacheField;
        }
        if (slotTypeCacheFieldFailed) {
            return null;
        }
        try {
            Field candidate = MoltenPatternAssemblyPartMachine.class.getDeclaredField("slotRecipeType");
            if (candidate.getType() != int[].class) {
                slotTypeCacheFieldFailed = true;
                return null;
            }
            candidate.setAccessible(true);
            slotTypeCacheField = candidate;
            return candidate;
        } catch (Throwable t) {
            slotTypeCacheFieldFailed = true;
            GTLModular.LOGGER.warn("[gtlmodular] 主机表 byType 别名：拿不到样板总成的识别缓存字段（slotRecipeType）：{}"
                    + " ⇒ 旧存档里被判过「识别不出」的样板槽不会自动重扫；"
                    + "验证时请把那个样板槽重新放一次（或 Shift+右键点一下槽）再试", t.toString());
            return null;
        }
    }

    /**
     * ③ 防呆：<b>仅当</b>主机表自己的 lookup 彻底为空时，把别名里那批 {@code GTRecipe} 恒等灌进去。
     *
     * <p>为什么这么设闸门：按证据一，装载端（{@code RecipeManagerMixin} 偏移 342-381 与
     * {@code GregTechKubeJSPlugin} 偏移 241-332）本来就会把它填满 ⇒ 常态下这里<b>直接 return</b>，
     * 既不会重复写，也不会造出重复配方。只有"装载端没填 / 被清了没填回来"这种异常情形才会动手。
     * 无论哪种情形都只写<b>我们自己的</b> lookup，父表一个字都不动。
     */
    private static void refillLookupIfEmpty(Map<ResourceLocation, Recipe<?>> aliasRecipes) {
        GTRecipeType alias = NanochipRecipeTypes.ASSEMBLY_COMPLEX;
        if (alias == null) {
            return;
        }
        GTRecipeLookup lookup = alias.getLookup();
        if (lookup == null) {
            return;
        }
        Branch branch = lookup.getLookup();
        if (branch == null || !branch.isEmptyBranch()) {
            return;   // 非空 ⇒ 装载端已经克隆进来了，绝不再写
        }
        int added = 0;
        for (Recipe<?> recipe : aliasRecipes.values()) {
            if (recipe instanceof GTRecipe gt && lookup.addRecipe(gt)) {
                added++;
            }
        }
        if (added > 0 && !refillLogged) {
            refillLogged = true;
            GTLModular.LOGGER.warn("[gtlmodular] 主机表 lookup 竟然是空的（装载端没克隆进来？）⇒ 已从 byType 别名恒等回填 {} 条；"
                    + "请把这条日志发回（说明 RecipeManagerMixin/GregTechKubeJSPlugin 那条路在本包没跑）", added);
        }
    }
}
