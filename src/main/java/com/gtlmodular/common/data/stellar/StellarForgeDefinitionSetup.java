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
    };

    private StellarForgeDefinitionSetup() {
    }

    /** 由 {@link GTLModular} 在 mod 总线上注册。 */
    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(StellarForgeDefinitionSetup::configure);
    }

    private static void configure() {
        MachineDefinition definition = GTRegistries.MACHINES.get(GTCEu.id("stellar_forge"));
        if (!(definition instanceof MultiblockMachineDefinition stellarForge)) {
            GTLModular.LOGGER.error(
                    "[gtlmodular] 找不到 gtceu:stellar_forge（gtlcore 未加载？）⇒ 恒星炎炀锻炉换机/换描述跳过");
            return;
        }

        // 1) 换机器类：结构/配方表/仓室全部沿用 gtlcore 的定义，只换 MetaMachine 实现。
        stellarForge.setMachineSupplier(StellarForgeMachine::new);

        // 2) 换掉机器描述：整套换成 GTO 的（删掉模块/超频受损三节），不再保留 gtlcore 旧 tooltip。
        stellarForge.setTooltipBuilder((stack, tooltip) -> {
            for (String key : TOOLTIP_KEYS) {
                tooltip.add(Component.translatable(key));
            }
        });

        // 3) 客户端：挂 GTO 漩涡特效的 TESR（客户端类只在 Dist.CLIENT 分支加载）。
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () ->
                com.gtlmodular.client.renderer.stellar.StellarForgeFxClient.attachRenderer());

        GTLModular.LOGGER.info(
                "[gtlmodular] 恒星炎炀锻炉：机器类已换成 StellarForgeMachine（连续运行 -50% / 等级按 SCTier / 掉电爆炸），"
                        + "并追加 GTO 机器描述 {} 行",
                TOOLTIP_KEYS.length);
    }
}
