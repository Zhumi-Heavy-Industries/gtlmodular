package com.gtlmodular;

import com.gtlmodular.client.config.GTLModularConfigScreen;
import com.gtlmodular.common.compat.PatternAssemblyRecipeCacheInvalidator;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.data.GTLModularNacContext;
import com.gtlmodular.common.data.GTLModularNacSelfCheck;
import com.gtlmodular.common.event.NanochipEventWiring;
import com.gtlmodular.common.data.nanochip.NanochipRegistration;
import com.gtlmodular.common.data.nanochip.NanochipRecipeCacheHooks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GTL·熔岩模块化机组 —— 模组主入口（骨架：M0 仅完成加载框架）。
 *
 * <p>设计文档：docs/01_设计方案_GTL熔岩模块化机组.md
 */
@Mod(GTLModular.MOD_ID)
public final class GTLModular {

    public static final String MOD_ID = "gtlmodular";
    public static final Logger LOGGER = LoggerFactory.getLogger("GTL-Modular");

    public GTLModular() {
        GTLModularNacContext.bind();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        NanochipRecipeCacheHooks.register(new PatternAssemblyRecipeCacheInvalidator());

        // COMMON 配置（M2 双流体税参数等）
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GTLModularConfig.SPEC);

        // 客户端：注册「Mods → 配置」图形界面（Cloth Config 构建）
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(GTLModularConfigScreen::create)));

        // 本模组 Registrate 的注册事件挂到 mod 总线（deferred 条目在 RegisterEvent 时落库），
        // 同时订阅 GTM 的机器注册窗口（GTCEuAPI.RegisterEvent<RL, MachineDefinition>）。
        GTLModularMachines.registerEventListeners(modBus);

        // ★ NAC 自有配方表的注册窗口：GTCEuAPI.RegisterEvent<RL, GTRecipeType>（在 GTRecipeTypes.init() 内、
        //   RECIPE_TYPES.freeze() **之前**）——与机器注册同款做法（gtlcore / gtmthings 都这么接）。
        //   必须走事件：freeze 之后 GTRegistry.register 直接抛 "registry has been frozen"。
        NanochipRegistration.registerRecipeTypes(modBus);

        // ★ 2026-10-01：**天基矿石处理厂（gtceu:space_ore_processor）配方镜像**（v2，第二轮）。
        //   起因：gtladditions 分支 3.2.8Custom_SubSpace-fix2 自带的复制逻辑
        //   （RecipesModify.initSpaceOreProcessor() → INTEGRATED_ORE_PROCESSOR.onRecipeBuild{…copyFrom…}）
        //   与我们的 v1 复制逻辑（同样挂 onRecipeBuild）实机都是「挂载成功但回调一次未触发」：
        //     [23:29:54] …配方镜像已挂载：gtceu:space_ore_processor ← gtceu:integrated_ore_processor
        //     [23:29:54] …gtceu:integrated_ore_processor 一条配方都没经过回调 ⇒ 本轮镜像无效。
        //     [23:30:29] Slow GTCEu JEI recipe type registration: type=gtceu:steam_space_ore_processor, recipes=999
        //   ⇒ 那 999 条同族配方不是经 type.recipeBuilder(...) 构建的，onRecipeBuild 结构上收不到；
        //   ⇒ v2 改成 **ServerStartedEvent**（配方管理器就绪）里「枚举 → 复制 → 双向登记」，
        //     源类型改 gtceu:steam_space_ore_processor（实机 999 条），回落 integrated_ore_processor。
        //   v1 的两个 AddPackFindersEvent 监听器已删除；详细取证写在 SpaceOreRecipeMirror 的类注释里。
        //   配置开关 spaceOre.recipeMirror（默认开）。
        com.gtlmodular.common.data.module.SpaceOreRecipeMirror.register(modBus);
        modBus.addListener(com.gtlmodular.common.compat.EnhcoreIvBufferRegistration::onCommonSetup);   // 2026-10-02 登记猪咪为 Enhancedcore 的 IV 样板仓（反射/软依赖）

        // ★ 2026-09-23：**虚拟电路覆盖板整体退役**（队长：「算了，把编程电路覆盖板相关的，弄不成器」）。
        //   虽然 0.1.74 起它已经能跑（读机器输入里的 gtlcore 虚拟原料 → 翻译成机器的编程电路），
        //   但队长改用别的方案，且明确要求删掉 ⇒ 定义注册、物品、`common/cover/**` 三个类全部移除。
        //   （历史踩坑记录保留在施工记录 O.121~O.133 里：注册窗口/活动容器、`coverHolder` 不是
        //    `IRecipeCapabilityHolder`、注入后要 `markLastRecipeDirty()` 等，日后要用得上。）

        // 结构方块必须在这里（RegisterEvent<Block> 之前）建好；机器注册则在 GTM 的机器窗口内进行。
        GTLModularMachines.initBlocks();

        // ★ GTCEu 扩展（addon）：把 NAC 结构需要、本包缺失的材料（富集钬 / 八角铁 / 超导粗胚）
        //   交给 GTCEu 注册 ⇒ GTCEu 自动生成 `<材料>_frame` 方块（见 GTLModularAddon 类注释 + 施工记录 §64）。
        //   材料注册**必须挂在 MaterialEvent 上**：GTCEu 1.4.4 的 CommonProxy.initMaterials 在打印
        //   "Registering addon Materials" 之后是 `ModLoader.postEvent(new MaterialEvent())`，
        //   根本不调用 IGTAddon.registerMaterials()（javap -c 实测；本包 gtlcore 也是这么接的）。
        NanochipRegistration.registerMaterials(modBus);

        // NAC 自检：进世界自动跑一次写日志 + /gtlmodular_nac 随时重跑
        // （用来验证「注册表里真的有 / 数据包真的加载了 / 引用的流体真的存在」这类只有跑起来才知道的事）
        GTLModularNacSelfCheck.register();
        com.gtlmodular.common.command.GTLModularCommands.register();

        // ★ 2026-09-27：GTL-Enhancedcore 兼容 mixin 的**条件注册**（见 registerEnhancedcoreMixins）。
        // [2026-10-01 停用：反射+ignoreRequired 方案不采用，见 _enhcore/实施.md] registerEnhancedcoreMixins();

        // 注意：机器注册不在此构造中执行，而是在 GTM 投递机器 RegisterEvent（MACHINES 未冻结
        // 的唯一窗口）时由 GTLModularMachines.init() 执行 —— 依据 gtlcore 同款做法。
        LOGGER.info("[gtlmodular] 加载中：机器注册委托给 GTM 机器注册事件窗口。");
        NanochipEventWiring.register(modBus);
    }

    /** 兼容 mixin 的配置文件名（放在 {@code src/main/resources/} 根下）。 */
    private static final String ENHANCEDCORE_MIXIN_CONFIG = "gtlmodular.enhancedcore.mixins.json";

    /**
     * 只有当「GTL-Enhancedcore 装在本实例里」**且**它的「跳过缺失 mixin 目标」开关处于开启状态时，
     * 才加载 {@code gtlmodular.enhancedcore.mixins.json}。
     *
     * <h2>为什么必须条件加载（而不是直接写进 gtlmodular.mixins.json）</h2>
     * 三条 mixin 的目标类都在 enhancedcore 里：
     * <ul>
     *   <li>{@code com.gtl.enhancedcore.common.recipe.iv.IvNativeHatches}</li>
     *   <li>{@code com.gtl.enhancedcore.common.registration.MachineRegistrationSupport}（2.9.1 才有）</li>
     *   <li>{@code com.gtl.enhancedcore.common.machine.TieredParallelMachine}</li>
     * </ul>
     * {@code gtlmodular.mixins.json} 是 {@code "required": true}：把这三条常驻进去，
     * <b>没装 enhancedcore 的实例（以及装了旧版 enhancedcore 的实例）会因为「找不到目标类」直接崩服</b>。
     *
     * <h2>为什么用反射而不是 {@code MixinConfigs.register(...)} 直接调用</h2>
     * 本工程编译期 classpath 上的 mixin 是 {@code org.spongepowered:mixin:0.8.5}
     * （{@code gtl-dev/gradle-home/caches/.../mixin-0.8.5.jar}，692 条目，已实测<b>不含</b>
     * {@code org/spongepowered/asm/mixin/MixinConfigs.class}）。
     * 直接静态调用会<b>编译失败</b>；反射 + 显式探测开关则无论该 API 在与不在都能编译并安全退化。
     *
     * <h2>开关</h2>
     * 仅当 JVM 参数 {@code -Dmixin.env.ignoreRequired=true} 或
     * {@code -Dmixin.checks=false} 存在时才注册（两者都是 Mixin 的官方开关；
     * 「mixin 目标类缺失」到底是否致命，本会话<b>无法实机取证</b>，故取保守口径）。
     * 未开启时本方法只写一条 warn 日志 ⇒ 与现状完全一致，绝不会因为兼容补丁让游戏起不来。
     */
    private static void registerEnhancedcoreMixins() {
        try {
            if (!net.minecraftforge.fml.ModList.get().isLoaded("gtl_enhancedcore")) {
                LOGGER.info("[gtlmodular] 未检测到 gtl_enhancedcore ⇒ 不加载 enhancedcore 兼容 mixin。");
                return;
            }
            String m = System.getProperty("mixin.env.ignoreRequired");
            String c = System.getProperty("mixin.checks");
            boolean tolerant = "true".equalsIgnoreCase(m) || "false".equalsIgnoreCase(c);
            if (!tolerant) {
                LOGGER.warn("[gtlmodular] 检测到 gtl_enhancedcore，但 {} 未启用「忽略缺失 mixin 目标」开关"
                                + "（需要 -Dmixin.env.ignoreRequired=true 或 -Dmixin.checks=false）"
                                + " ⇒ 本次不注入 enhancedcore 兼容 mixin（闸门①/②的放行不会生效）。",
                        ENHANCEDCORE_MIXIN_CONFIG);
                return;
            }
            Class<?> mixinConfigs = Class.forName("org.spongepowered.asm.mixin.MixinConfigs");
            for (java.lang.reflect.Method method : mixinConfigs.getMethods()) {
                if (!"register".equals(method.getName()) || method.getParameterCount() != 1) continue;
                if (method.getParameterTypes()[0] != String.class) continue;
                method.invoke(null, ENHANCEDCORE_MIXIN_CONFIG);
                LOGGER.info("[gtlmodular] 已注册 {}（闸门①/②放行 + 闸门③护栏）。", ENHANCEDCORE_MIXIN_CONFIG);
                return;
            }
            LOGGER.warn("[gtlmodular] 未找到 MixinConfigs.register(String) ⇒ {} 未加载。",
                    ENHANCEDCORE_MIXIN_CONFIG);
        } catch (Throwable t) {
            // 兼容补丁失败绝不影响本体加载
            LOGGER.warn("[gtlmodular] enhancedcore 兼容 mixin 注册失败（已忽略）：{}", t.toString());
        }
    }
}
