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
 * 本模组的 Jade 插件入口，注册两个 provider：
 * <ol>
 *   <li>{@link VacuumColorProvider}（客户端）：真空传送带仓在 <b>Jade 方块提示</b>里显示
 *       「颜色: 紫色」（用该颜色本身上色）。</li>
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
        registration.registerBlockComponent(NanochipThreadedJadeProvider.INSTANCE, Block.class);
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
