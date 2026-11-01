package com.gtlmodular.common.event;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.machine.nanochip.NanochipColor;
import com.gtlmodular.common.machine.nanochip.NanochipPipeMachine;
import com.gtlmodular.common.machine.nanochip.NanochipPortPartMachine;
import com.gtlmodular.common.machine.nanochip.NanochipVacuumHatchMachine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 让 <b>GT 喷漆罐</b>能直接给「真空传送端口」（VCI/VCO）与「真空传送管道」染色。
 *
 * <h3>为什么需要这个钩子（2026-09-15，按 GTCEu 1.4.4 字节码定的链路）</h3>
 * 我们的颜色存在自己的字段 {@code colorIndex} 里，渲染只走 {@code tintColor(...)}；
 * 而"GT 的喷漆"在 GTCEu 1.4.4 里<b>只有两条实现路径，都不碰 {@code IPaintable}</b>：
 * <ol>
 *   <li>{@code com.gregtechceu.gtceu.common.item.ColorSprayBehaviour#useOn}
 *       → {@code tryPaintBlock} → {@code recolorBlockState(...)}
 *       —— 它遍历目标方块状态的 {@code Property} 集合，找 {@code getValueClass() == DyeColor.class}
 *       的那个属性改写，也就是<b>只对"带 DyeColor 属性的方块"有效</b>；</li>
 *   <li>同一方法里的 {@code tryStripBlockColor(...)} / {@code tryPaintSpecialBlock(...)}
 *       —— 只管原版染色玻璃、玻璃板那几类。</li>
 * </ol>
 * 反汇编 {@code ColorSprayBehaviour} 全类：<b>没有任何一处引用 {@code IPaintable}
 * 或 {@code setPaintingColor}</b>（全库搜 {@code setPaintingColor} 也只有
 * {@code MetaMachine} / {@code IPaintable} / {@code PipeBlockEntity} 三个出现名字的地方，
 * 没有任何 GTCEu 物品去调它）。⇒ 想靠覆写 {@code setPaintingColor} 来"被喷漆罐染上"是行不通的，
 * 那条路是<b>给 addon 用的 API</b>，不是喷漆罐的链路。
 *
 * <p>所以最短可行路径是：在 {@link PlayerInteractEvent.RightClickBlock} 里自己接住这次右键
 * （该事件在方块 {@code use} / 物品 {@code useOn} <b>之前</b>触发，潜行也触发，与
 * {@code StripperEmbedEventHandler} 用的是同一个钩子与同一个理由），
 * 认出"手里拿的是 GTCEu 的喷漆罐"，再把颜色写进我们自己的 {@code colorIndex}。
 *
 * <h3>喷漆罐的物品 id（来自 {@code GTItems} 的字节码/常量）</h3>
 * <ul>
 *   <li>染料罐：{@code gtceu:<染料名>_dye_spray_can}（{@code "%s_dye_spray_can"}），共 16 个；</li>
 *   <li><b>复原溶剂</b>：{@code gtceu:solvent_spray_can} ——
 *       {@code ColorSprayBehaviour} 构造器里 {@code color = (序号越界) ? null : DyeColor.values()[序号]}，
 *       溶剂罐传的就是<b>越界值 ⇒ {@code color == null}</b>（不是白色、不是 {@code 0xFFFFFF}、也不是 {@code -1}；
 *       它的耐久条颜色常量是 {@code 9868950 = 0x969696}）。所以"清除颜色"要按"复原成默认色"处理；</li>
 *   <li>{@code gtceu:empty_spray_can}：空罐，不处理。</li>
 * </ul>
 *
 * <h3>语义</h3>
 * <ul>
 *   <li>染料罐 → {@link NanochipColor#of(DyeColor)} 直接映射（按染料名，精确、不靠容差）；</li>
 *   <li><b>溶剂罐 → 变成"未染色"</b>（{@code colorIndex = -1}，即 NH 的 {@code colorization == -1}）。
 *       ★ 2026-09-17 第六轮改动：以前是"复原成白色"，那是错的 —— NH 里 {@code -1} 与白（{@code 0}）
 *       是两种不同状态，且未染色元素不建任何连接（见
 *       {@code MTEVacuumConveyorPipe.checkConnections} 的 {@code iconst_m1} 守卫）。</li>
 *   <li><b>没有"自动取色"了</b>（2026-09-17 第六轮删除 {@code autoColorFromNeighbour}）——
 *       颜色只能由玩家亲手给：喷漆罐（本类）、螺丝刀循环、或仓的 GUI 色板；</li>
 *   <li>仍然<b>不写 GT 的喷漆状态</b> ⇒ 那行 {@code 颜色: #xxxxxx} 不会出现；</li>
 *   <li>螺丝刀循环换色与 GUI 色板完全不受影响（它们走各自的 {@code setColor}）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NanochipSprayCanHandler {

    private NanochipSprayCanHandler() {
    }

    private static final String GTCEU_NS = "gtceu";
    private static final String DYE_SUFFIX = "_dye_spray_can";
    private static final String SOLVENT_CAN = "solvent_spray_can";

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        ItemStack stack = event.getItemStack();
        // ★★ 2026-10-29（NAC 重做 P1）：除 GT 喷漆罐外，**原版染料**也接进来 ——
        //    规划书 §4.1 的口径是「用原版染料机制染色」。原版染料右键机器方块本来是空操作，
        //    所以这里新增一支不会抢走任何既有交互；`minecraft:<染料>` 只对 NAC 的可染色机器生效
        //    （下面 instanceof 不匹配就直接 return，原版方块/其它模组机器完全不受影响）。
        boolean vanillaDye = stack.getItem() instanceof net.minecraft.world.item.DyeItem;
        NanochipColor picked = null;
        boolean solvent = false;
        if (vanillaDye) {
            picked = NanochipColor.of(((net.minecraft.world.item.DyeItem) stack.getItem()).getDyeColor());
            if (picked == null) {
                return;
            }
        } else {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id == null || !GTCEU_NS.equals(id.getNamespace())) {
                return;
            }
            String path = id.getPath();
            solvent = SOLVENT_CAN.equals(path);
            if (path.endsWith(DYE_SUFFIX)) {
                picked = byDyeName(path.substring(0, path.length() - DYE_SUFFIX.length()));
                if (picked == null) {
                    return;                   // 名字认不出来（模组改版）⇒ 不插手
                }
            } else if (!solvent) {
                return;                       // empty_spray_can 等
            }
        }
        if (!(level.getBlockEntity(event.getPos()) instanceof MetaMachineBlockEntity be)) {
            return;
        }
        MetaMachine machine = be.getMetaMachine();
        // ★【2026-09-17 第六轮 · NH 对齐】复原溶剂罐 = **去掉颜色**（colorization → -1），
        //   而不是"染成白色"：NH 的 MTEVacuumConveyorPipe.checkConnections() 在
        //   colorization == -1 时直接不建连接，所以"溶剂"与"白漆"是两种不同的状态。
        //   ColorSprayBehaviour 里溶剂罐传的就是越界序号 ⇒ color == null（见类注释），
        //   语义上本来就是"清除"而不是"上白色"。
        //   `picked` 在 if 里赋值 ⇒ 不是 effectively final，所以先落进 final 局部变量再进 lambda。
        final NanochipColor chosen = picked;
        NanochipColor target = solvent ? null : chosen;
        String what;
        if (machine instanceof NanochipPortPartMachine port) {
            port.setColor(target);
            what = "总成";
        } else if (machine instanceof NanochipVacuumHatchMachine hatch) {
            hatch.setColor(target);
            what = "端口";
        } else if (machine instanceof NanochipPipeMachine pipe) {
            pipe.setColor(target, true);
            what = "管道";
        } else {
            return;                           // 不是我们这几类机器 ⇒ 交回 GTCEu 自己的逻辑
        }
        // 与 StripperEmbedEventHandler 同款：消费掉这次交互（不打开机器界面、不让喷漆罐再跑它自己的方块逻辑）
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        Player player = event.getEntity();
        if (player != null) {
            player.displayClientMessage(Component.literal("[NAC " + what + "] 颜色 → ")
                    .append(solvent
                            ? Component.literal(NanochipColor.UNPAINTED_NAME_ZH)
                            .withStyle(style -> style.withColor(0xFFFFFF))
                            : Component.literal(chosen.nameZh())
                            .withStyle(style -> style.withColor(chosen.textColor()))),
                    true);
        }
    }

    /** {@code "<染料名>_dye_spray_can"} 的染料名 → 我们的颜色。 */
    private static NanochipColor byDyeName(String dyeName) {
        for (DyeColor dye : DyeColor.values()) {
            if (dye.getName().equals(dyeName)) {
                return NanochipColor.of(dye);
            }
        }
        return null;
    }
}
