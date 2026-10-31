package com.gtlmodular.client.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.machine.nanochip.NanochipColor;
import com.gtlmodular.common.machine.nanochip.NanochipVacuumHatchMachine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * 本模组的 Jade 插件入口，注册三个 provider：
 * <ol>
 *   <li>{@link VacuumColorProvider}（客户端）：真空传送带仓在 <b>Jade 方块提示</b>里显示
 *       「颜色: 紫色」（用该颜色本身上色）。</li>
 *   <li>{@link PortChannelProvider}（客户端，★ 2026-10-29 P1）：NAC 收件/发件总成显示
 *       「频率 / 颜色 / 样板槽空」。</li>
 *   <li>{@link NanochipThreadedJadeProvider}（★ 2026-10-28 新增，服务端 + 客户端两侧）：
 *       纳米芯片组装复合体的<b>独立线程状态</b> —— 上限 / 在用条数 + 逐条线程的绿色进度条。
 *       它必须两侧都注册：服务端读线程表写 serverData，客户端只读 serverData 拼文本。</li>
 * </ol>
 *
 * <p><b>为什么不复用 GTCEu 那行</b>（针对第 1 个 provider）：GTCEu 的 {@code StainedColorProvider}
 * 只在"已喷漆"的方块上显示 {@code gtceu.top.stained}（值固定拼成 {@code #RRGGBB}），
 * 玩家反馈"只看 #FF9AC0CD 根本不知道是淡蓝色"。本模组现在不用喷漆染色（改覆写 {@code tintColor()}），
 * 所以那行不会再出现；颜色名由这里补上 —— 与原版一致（GTNH 的提示里写的也是颜色名，
 * 见 {@code GT5U.tooltip.nac.interface.structure_base_vci=Vacuum Conveyor Input: %s}）。
 *
 * <p>Jade 是<b>可选</b>依赖：没装 Jade 时 {@code @WailaPlugin} 不会被扫描，本类也不会被加载。
 */
@WailaPlugin(GTLModular.MOD_ID)
public class NanochipJadePlugin implements IWailaPlugin {

    /**
     * 服务端侧注册（★ 2026-10-28 新增）：独立线程机器的线程状态必须由<b>服务端</b>读线程表写进
     * Jade 的 serverData —— 客户端那份线程表是空的（见 {@link NanochipThreadedJadeProvider} 类注释）。
     *
     * <p>{@code registerBlockDataProvider} 的签名（{@code (IServerDataProvider, Class)}）取自
     * GTCEu 自己的 {@code GTJadePlugin} 与 gtlcore / gtladditions 的 Jade 插件（三处同形，
     * 见 {@code _census/_probe_nacmat_evidence/classstr_gtceu-1.20.1-1.4.4.txt:50623-50627}）。
     * 传 {@code Block.class} 是「所有方块都可能被问」的通用口径 —— 真正的过滤在 provider 内部
     * （不是本机就一个键都不写），这样不会漏掉任何一种机器类型。
     */
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(NanochipThreadedJadeProvider.INSTANCE, net.minecraft.world.level.block.entity.BlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(VacuumColorProvider.INSTANCE, Block.class);
        registration.registerBlockComponent(PortChannelProvider.INSTANCE, Block.class);
        registration.registerBlockComponent(BeatProvider.INSTANCE, Block.class);
        registration.registerBlockComponent(NanochipThreadedJadeProvider.INSTANCE, Block.class);
    }

    /**
     * <b>NAC 主机的 5 秒节拍</b>（P3/P4）：第几拍 + 进度 + 本拍收/发/挂起。
     *
     * <p>节拍属于主机（每 100 tick 一拍、与有没有料无关）；这里只是把 {@code @DescSynced}
     * 的字段读到 Jade 上，让玩家随时看得到"主机一直在跑"。
     */
    public enum BeatProvider implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID =
                new ResourceLocation(GTLModular.MOD_ID, "nac_beat");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            try {
                if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity)) {
                    return;
                }
                if (!(blockEntity.getMetaMachine()
                        instanceof com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine host)) {
                    return;
                }
                float progress = Math.max(0.0F, Math.min(1.0F, host.portBeatProgress()));
                // 进度条：boxStyle 必须非 null（Jade 的 ElementHelper 里 Objects.requireNonNull），
                // 之前传 null 被吞掉、连下面的文字行也一起没了。
                try {
                    var helper = snownee.jade.api.ui.IElementHelper.get();
                    var style = helper.progressStyle()
                            .color(0xFF44DD44, 0xFF2A2A2A)   // 绿色进度条（2026-10-29 用户要求）
                            .textColor(0xFFFFFFFF);
                    tooltip.add(helper.progress(progress,
                            Component.translatable("gtlmodular.nanochip.beat.progress",
                                    Math.round(progress * 100.0F)),
                            style, snownee.jade.api.ui.BoxStyle.DEFAULT, false));
                } catch (Throwable ignored) {
                    // 条画不出来也要让下面那行文字照常显示。
                }
                tooltip.add(Component.translatable("gtlmodular.nanochip.beat.line",
                        host.portBeatCount(), host.portBeatReceived(),
                        host.portBeatSent(), host.portBeatPending()));
            } catch (Throwable ignored) {
                // Jade 渲染期绝不因为本模组抛异常。
            }
        }
    }

    /**
     * <b>NAC 总成（收件 / 发件）</b>的 Jade 行（★ 2026-10-29 P1 新增）：频率 + 颜色 + 样板槽状态。
     *
     * <p>两个字段都是 {@code @DescSynced} ⇒ 客户端直接可读，不需要走 serverData；
     * 与 {@link VacuumColorProvider} 同构，只是多了频率行与「样板槽空」提示。
     */
    public enum PortChannelProvider implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID =
                new ResourceLocation(GTLModular.MOD_ID, "nac_port_channel");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            try {
                if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity)) {
                    return;
                }
                if (!(blockEntity.getMetaMachine() instanceof com.gtlmodular.common.machine.nanochip.NanochipPortPartMachine port)) {
                    return;
                }
                // 只 add，不 clear/重建列表 —— 否则会把其它 provider 的行并成一行（B21 事故）。
                tooltip.add(Component.translatable("gtlmodular.nanochip.port.frequency.line", port.frequency()));
                NanochipColor color = port.color();
                if (color == null) {
                    tooltip.add(Component.translatable("gtlmodular.nanochip.vacuum.color.unpainted"));
                } else {
                    tooltip.add(Component.translatable("gtlmodular.nanochip.vacuum.color.line",
                            Component.literal(color.nameZh())
                                    .withStyle(style -> style.withColor(color.textColor()))));
                }
                if (!port.hasPatterns()) {
                    tooltip.add(Component.translatable("gtlmodular.nanochip.port.pattern.empty"));
                }
                // P2 三笔账 + 线路冲突（都是 @DescSynced，客户端可直接读）
                tooltip.add(Component.translatable("gtlmodular.nanochip.port.account.line",
                        port.totalPushed(), port.totalReceived(), port.pendingItems()));
                if (port.lineConflict()) {
                    tooltip.add(Component.translatable("gtlmodular.nanochip.port.conflict.line",
                            port.lineSenderCount()));
                }
            } catch (Throwable ignored) {
                // Jade 渲染期绝不能因为本模组抛异常 —— 宁可少一行，也不要把准星提示打崩。
            }
        }
    }

    /** 只对真空传送带仓生效（其它方块直接跳过）。 */
    public enum VacuumColorProvider implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID =
                new ResourceLocation(GTLModular.MOD_ID, "vacuum_conveyor_color");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity)) {
                return;
            }
            if (!(blockEntity.getMetaMachine() instanceof NanochipVacuumHatchMachine hatch)) {
                return;
            }
            NanochipColor color = hatch.color();
            if (color == null) {
                // 未染色（NH：colorization == -1）—— 不谎报一个颜色名。
                tooltip.add(Component.translatable("gtlmodular.nanochip.vacuum.color.unpainted"));
                return;
            }
            tooltip.add(Component.translatable("gtlmodular.nanochip.vacuum.color.line",
                    Component.literal(color.nameZh()).withStyle(style -> style.withColor(color.textColor()))));
        }
    }
}
