package com.gtlmodular.common.data.tentacle;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.client.renderer.machine.WorkableTieredHullMachineRenderer;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.machine.tentacle.ThreadHatchPartMachine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ★ 2026-11-01（0.3.281）<b>GTO 线程仓 7 档</b>的注册。
 *
 * <h2>照搬对象</h2>
 * <p>GTO {@code gtocore:thread_hatch_<tier>}，注册原文
 * （{@code _gto_src/src/main/java/com/gtocore/common/data/GTOMachines.java:281-288}）：
 * <pre>
 * public static final MachineDefinition[] THREAD_HATCH = registerTieredMachines("thread_hatch",
 *         tier -&gt; GTOValues.VNFR[tier] + "线程仓",
 *         ThreadPartMachine::new, (tier, builder) -&gt; builder
 *                 .allRotation()
 *                 .abilities(GTOPartAbility.THREAD_HATCH)
 *                 .workableTieredHullRenderer(GTOCore.id("block/machines/thread_hatch/thread_hatch_mk" + (tier - 7)))
 *                 .tooltips(Component.translatable("gtocore.machine.thread_hatch.tooltip.0", 1 &lt;&lt; (tier - LuV)))
 *                 .register(),
 *         UV, UHV, UEV, UIV, UXV, OpV, MAX);
 * </pre>
 * 本模组逐条对应：
 * <ul>
 *   <li><b>7 档电压</b>：UV / UHV / UEV / UIV / UXV / OpV / MAX；</li>
 *   <li><b>每档线程数上限 = {@code 1 << (tier - LuV)}</b>（0.3.282 起是<b>上限</b>，
 *       实际值由玩家在仓的界面里从 1 调到上限，默认取上限 —— 与 GTO 一致）；</li>
 *   <li><b>方块 id</b>：{@code gtlmodular:thread_hatch_uv … thread_hatch_max}；</li>
 *   <li><b>同一套自定义 {@code PartAbility}</b>（名字 {@code thread_hatch}，与 GTO 同名）——
 *       7 档都挂在<b>同一个</b> ability 下，结构里写一条
 *       {@code Predicates.abilities(THREAD_HATCH).setMaxGlobalLimited(1)} 就能收全部 7 档；</li>
 *   <li><b>贴图</b>：{@code gtlmodular:block/machines/thread_hatch/thread_hatch_mk1..7}，
 *       逐字节取自 GTOCore jar（{@code assets/gtocore/textures/block/machines/thread_hatch/}），
 *       每个目录里是 {@code overlay_front.png / overlay_front_active.png} + 各自 {@code .mcmeta}；</li>
 *   <li><b>渲染器</b>：{@link WorkableTieredHullMachineRenderer}
 *       （= GTCEu 1.4.4 的 {@code workableTieredHullRenderer}，与 GTO 同一族）——
 *       机壳用 GTCEu 对应电压的 hull，正面叠上面的动画覆盖层。
 *       <b>不需要</b>手写 blockstate / 方块 model / item model：
 *       GTCEu 的机器方块模型由 LDLib 动态提供（本工程 26 台现役机器一个 json 都没有，
 *       见 {@code assets/gtlmodular}），手写 blockstate 反而会盖掉动态模型。</li>
 * </ul>
 *
 * <h2>为什么注册要在结构之前</h2>
 * <p>{@code PartAbility.getAllBlocks()} 是 <b>memoize</b> 的，而
 * {@code Predicates.abilities(...)} 在<b>构建谓词时</b>就把它取走了（javap：
 * {@code Predicates.abilities} → {@code PartAbility.getAllBlocks().stream()} → {@code blocks(Block[])}）。
 * 所以 {@code GTLModularMachines#init()} 里必须先调
 * {@link #register()}，再调 {@link NyarlathotepsTentacleMachines#register()}，
 * 否则奈亚的结构谓词会拿到一张<b>空方块表</b>（线程仓永远放不进去）。
 */
public final class ThreadHatchMachines {

    private ThreadHatchMachines() {
    }

    /**
     * 自定义 {@code PartAbility}，ID 与 GTO 的 {@code GTOPartAbility.THREAD_HATCH} 同名
     * （{@code "thread_hatch"}）。
     *
     * <p>GTCEu 1.4.4 的 {@code PartAbility(String)} 构造器是 public（javap 现读），
     * 7 个档位的方块由 {@code MachineBuilder} 在注册时自动以
     * {@code PartAbility.register(tier, block)} 记进来，本类不用手工登记。
     */
    public static final PartAbility THREAD_HATCH = new PartAbility("thread_hatch");

    /** 7 档电压（低 → 高）。 */
    public static final int[] TIERS = {
            GTValues.UV, GTValues.UHV, GTValues.UEV, GTValues.UIV,
            GTValues.UXV, GTValues.OpV, GTValues.MAX
    };

    /** 7 档小写后缀（= GTCEu 的 {@code GTValues.VN[tier]} 小写，与物品 id 一致）。 */
    private static final String[] SUFFIXES = {"uv", "uhv", "uev", "uiv", "uxv", "opv", "max"};

    /** 7 档中文显示名（与 GTO 的 {@code GTOValues.VNFR[tier] + "线程仓"} 对齐）。 */
    private static final String[] DISPLAY_ZH = {
            "UV线程仓", "UHV线程仓", "UEV线程仓", "UIV线程仓",
            "UXV线程仓", "OpV线程仓", "MAX线程仓"
    };

    /** 已注册的 7 台（key = 电压档位），供其它代码查询（结构/测试/提示）。 */
    public static final Map<Integer, MachineDefinition> THREAD_HATCHES = new LinkedHashMap<>();

    /**
     * 注册 7 台线程仓 —— <b>必须在奈亚拉托提普之触之前调用</b>（原因见类注释）。
     */
    public static void register() {
        for (int i = 0; i < TIERS.length; i++) {
            registerOne(TIERS[i], SUFFIXES[i], i + 1, DISPLAY_ZH[i]);
        }
        GTLModular.LOGGER.info(
                "[gtlmodular] 已注册 GTO 线程仓 7 档：{}（线程数上限 UV=4 / UHV=8 / UEV=16 / UIV=32 / "
                        + "UXV=64 / OpV=128 / MAX=256，界面里可调 1~上限；共用 PartAbility[{}]）",
                THREAD_HATCHES.keySet(), THREAD_HATCH.getName());
    }

    private static void registerOne(int tier, String suffix, int mk, String displayZh) {
        String id = "thread_hatch_" + suffix;
        // 贴图目录（覆盖层）：assets/gtlmodular/textures/block/machines/thread_hatch/thread_hatch_mk<N>/overlay_front*.png
        ResourceLocation overlay = new ResourceLocation(
                GTLModular.MOD_ID, "block/machines/thread_hatch/thread_hatch_mk" + mk);
        int threads = ThreadHatchPartMachine.threadsForTier(tier);

        MachineDefinition definition = GTLModularMachines.REGISTRATE
                .machine(id, holder -> new ThreadHatchPartMachine(holder, tier))
                // GTO 的 .allRotation()：六面可旋转
                .rotationState(RotationState.ALL)
                // ★ 同一个自定义 PartAbility：结构里一条谓词即可收全部 7 档
                .abilities(THREAD_HATCH)
                .tier(tier)
                // 与 GTO 同一族渲染器：GTCEu 电压 hull + 本目录的动画覆盖层
                .renderer(() -> new WorkableTieredHullMachineRenderer(tier, overlay))
                .langValue(displayZh)
                // ★ 0.3.282：与 GTO 的 `gtocore.machine.thread_hatch.tooltip.0` 同一句话
                //   （「可为机器提供 %s 线程的并行」），%s = 该档上限。
                // ★ 0.3.285：删掉我们自己追加的「可调范围 1 ~ %s」第二行（用户反馈黑色行太多）。
                .tooltips(Component.translatable("gtlmodular.thread_hatch.tooltip", threads))
                .register();

        THREAD_HATCHES.put(tier, definition);
        GTLModular.LOGGER.debug("[gtlmodular] 线程仓 {}：tier={} 线程={} 覆盖层={}",
                definition.getId(), tier, threads, overlay);
    }
}
