package com.gtlmodular.common.data.stellar;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.machine.stellar.StellarForgeMachine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * 恒星炎炀锻炉「换机 + 换描述」的收尾配置（2026-10-31）。
 *
 * <p>做两件事，都发生在 <b>FMLLoadCompleteEvent</b>（所有 mod 的 common setup 之后，
 * 机器注册表已冻结但世界还没加载，改 definition 字段安全）：
 * <ol>
 *   <li>把 {@code gtceu:stellar_forge} 的机器工厂换成 {@link StellarForgeMachine}
 *       —— GTO 的「连续运行 / 配方等级按恒星容器 / 掉电爆炸」都在那个类里；</li>
 *   <li>给它的 tooltip 追加 GTO 原版机器描述（{@code GTOMachineTooltips.StellarForgeTooltips} 的
 *       「连续运行加成」一节，中文逐句对照）。</li>
 * </ol>
 *
 * <p><b>为什么用 definition setter 而不是 mixin</b>：GTCEu 1.4.4 的 {@code MachineDefinition}
 * 自带 {@code setMachineSupplier / setTooltipBuilder}（javap 实测），比注入 {@code MachineBuilder}
 * 稳，也不会和「增强版核心」的 struct 替换 mixin 打架。
 *
 * <p>渲染器（GTO 漩涡特效）不在这里挂：它是客户端类，由 {@code client} 侧在
 * {@code Dist.CLIENT} 分支里调 {@code StellarForgeFxClient.attachRenderer()}。
 */
public final class StellarForgeDefinitionSetup {

    /**
     * 机器描述（逐句取自 GTO {@code GTOMachineTooltips.StellarForgeTooltips}，按玩家 2026-11-01 口径
     * <b>删掉</b>「安装模块后」「附属模块」「超频受损」三节；保留「连续运行加成 / 激光仓 / 配方类型」）。
     *
     * <p>不再追加 gtlcore 原来的 tooltip —— 那三条里「结构来源：GCY」现在已是<b>错</b>的
     * （结构已换成 GTO 的），「可用配方类型」与下面「配方类型」重复，「完美超频」也不在 GTO 的这段描述里。
     */
    private static final String[] TOOLTIP_KEYS = {
            "gtlmodular.machine.stellar_forge.tooltip.section",
            "gtlmodular.machine.stellar_forge.tooltip.tier",
            "gtlmodular.machine.stellar_forge.tooltip.continuous",
            "gtlmodular.machine.stellar_forge.tooltip.explode",
            "gtlmodular.machine.stellar_forge.tooltip.laser",
            "gtlmodular.machine.stellar_forge.tooltip.laser_desc",
            "gtlmodular.machine.stellar_forge.tooltip.recipe",
            // 结尾一行：结构来源（玩家 2026-11-02 要求；GTO 原版这行是「结构来源：%s」，我们结构就是 GTO 的）
            "gtlmodular.machine.stellar_forge.tooltip.source",
    };

    /** 幂等闸门：早窗口（机器注册）配置成功后，FMLLoadComplete 的兜底不再重复执行。 */
    private static boolean configured;

    private StellarForgeDefinitionSetup() {
    }

    /**
     * ★ <b>主入口</b>：由 {@code GTLModularMachines.init()}（GTM 的机器注册窗口内）调用。
     *
     * <p><b>为什么必须这么早</b>（2026-11-01 实机日志取证）：FMLLoadComplete 在本包**晚于客户端第一次资源加载**
     * （日志：21:24:55 资源重载开始 → 21:25:23 才到 FMLLoadComplete）。渲染器的覆层贴图（overlay_front…）
     * 是在资源重载的 **贴图集拼接**阶段由 {@code WorkableCasingMachineRenderer#onPrepareTextureAtlas} 注册的；
     * 若在资源重载之后才 {@code setRenderer}，新渲染器的 overlay 精灵表是**空的** ⇒ 控制器正面只剩外壳
     * （玩家看到的「主方块没脸」就是这个：日志里 attach 在 21:25:23，晚于 21:24:55 的贴图集）。
     * 机器注册窗口（21:24:52）早于资源重载 ⇒ 在这里换渲染器，贴图集与模型烘焙都会用新实例。
     *
     * @param attachRenderer 是否顺带挂客户端渲染器（只有早窗口才允许；晚兜底传 false，避免贴图集已烘焙后
     *                       换上一个精灵表为空的新渲染器，反而把正面弄没）
     */
    public static void configureNow(boolean attachRenderer) {
        if (configured) {
            return;
        }
        MachineDefinition definition = GTRegistries.MACHINES.get(GTCEu.id("stellar_forge"));
        if (!(definition instanceof MultiblockMachineDefinition stellarForge)) {
            GTLModular.LOGGER.error(
                    "[gtlmodular] 找不到 gtceu:stellar_forge（gtlcore 未加载？）⇒ 恒星炎炀锻炉换机/换描述跳过");
            return;
        }
        configured = true;

        // 1) 换机器类：结构/配方表/仓室全部沿用 gtlcore 的定义，只换 MetaMachine 实现。
        stellarForge.setMachineSupplier(StellarForgeMachine::new);

        // 1.5) ★ 2026-11-06：加第二张配方表（「魔力生成」= 魔法制造机同一张表）。
        //   走 GTCEu 标准做法：definition.setRecipeTypes(多张) ⇒
        //   IFancyUIMachine#getSubTabs() 自动挂标准「机器模式」子页（MachineModeFancyConfigurator），
        //   切换走 setActiveRecipeType(int)（@Persisted 存盘）。
        //   结构 C 位的 autoAbilities 用的是 definition.getRecipeTypes()，图案是惰性构建的
        //   ⇒ 这里先换掉，图案第一次判定时两张表的仓室都会放行。
        stellarForge.setRecipeTypes(new com.gregtechceu.gtceu.api.recipe.GTRecipeType[]{
                org.gtlcore.gtlcore.common.data.GTLRecipeTypes.STELLAR_FORGE_RECIPES,
                org.gtlcore.gtlcore.common.data.GTLRecipeTypes.MAGIC_MANUFACTURER_RECIPES,
        });

        // 2) 换掉机器描述：整套换成 GTO 的（删掉模块/超频受损三节），不再保留 gtlcore 旧 tooltip。
        stellarForge.setTooltipBuilder((stack, tooltip) -> {
            for (String key : TOOLTIP_KEYS) {
                tooltip.add(Component.translatable(key));
            }
        });

        // 3) 客户端：挂 GTO 漩涡特效的 TESR（客户端类只在 Dist.CLIENT 分支加载）。
        if (attachRenderer) {
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.gtlmodular.client.renderer.stellar.StellarForgeFxClient.attachRenderer());
        }

        GTLModular.LOGGER.info(
                "[gtlmodular] 恒星炎炀锻炉：机器类已换成 StellarForgeMachine（连续运行 -50% / 等级按 SCTier / 掉电爆炸），"
                        + "并追加 GTO 机器描述 {} 行（渲染器：{}）",
                TOOLTIP_KEYS.length, attachRenderer ? "早窗口挂载" : "未挂载(晚兜底)");
    }

    /**
     * 兜底：若机器注册窗口里没找到定义（极端加载顺序），在 FMLLoadComplete 再试一次。
     * <b>只换机器类/描述，不挂渲染器</b> —— 此时贴图集已烘焙，换渲染器会让覆层精灵表为空。
     */
    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        if (configured) {
            return;
        }
        GTLModular.LOGGER.warn(
                "[gtlmodular] 恒星炎炀锻炉：机器注册窗口没配上，改在 FMLLoadComplete 兜底（不挂漩涡渲染器，避免主方块没脸）");
        event.enqueueWork(() -> StellarForgeDefinitionSetup.configureNow(false));
    }
}
