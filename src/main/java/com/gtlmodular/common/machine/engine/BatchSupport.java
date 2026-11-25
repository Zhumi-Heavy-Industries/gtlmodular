package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.google.common.collect.Table;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.api.capability.IMERecipeHandler;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferProxyPartMachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通用独立线程引擎的<b>只读探测／文本</b>小工具。
 *
 * <p>抽出来的目的与参考实现（gtl_extend 的 {@code CommonUtils}）一致：这些方法只读世界、
 * 不参与调度、不写任何字段，集中在一处后 {@link BatchFinder}／{@link BatchDiagnostics}／
 * {@link BatchIo} 都能用，又不必互相依赖。
 */
final class BatchSupport {

    private BatchSupport() {}

    /** 机器结构里是否挂了 ME 样板仓（即使它的库存不在通用 handler 里）。 */
    static boolean hasMePatternPart(IRecipeLogicMachine machine) {
        try {
            if (machine.self() instanceof IMultiController controller && controller.isFormed()) {
                for (IMultiPart part : controller.getParts()) {
                    if (part instanceof IMEPatternPartMachine) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 拿不到部件表就不拦（宁可多扫）
        }
        return false;
    }

    /**
     * ★ 2026-11-25：结构里的 ME 样板仓<b>是否真有样板</b>（不分哪家的样板总成）。
     *
     * <h3>为什么需要它（这就是「只能挂猪咪样板总成才能工作」的根因补丁）</h3>
     * <p>gtlcore 的 ME 样板仓把 AE 推来的料放在<b>自己的槽</b>里，它的 handler
     * （{@code IMERecipeHandler}）继承的是 {@code IFilteredHandler}，
     * <b>不是</b> GTCEu 的 {@code IRecipeHandler} ⇒ 不进 {@code capabilitiesProxy}、
     * 也没有 {@code getContents()}。所以：
     * <ul>
     *   <li>{@link #hasMePatternPart}（ME 口径）= 真；</li>
     *   <li>{@link com.gtlmodular.common.machine.engine.ThreadedRecipeLogic#hasRealItemOrFluidInput}
     *       （通用 handler 口径）= <b>假</b>，哪怕料已经推进槽里。</li>
     * </ul>
     * <p>{@link BatchFinder} 用的是后者 ⇒ ME 缓存为空（冷槽）时直接跳过整表扫描
     * ⇒ 样板插着、料也到了，机器却永远 IDLE。而猪咪两台总成有「直供配方」
     * （{@code setSlotCacheRecipe}）先把冷槽热起来，正好绕过这道闸
     * ⇒ 表象就是「只有猪咪的样板总成能用」。
     *
     * <h3>口径与纪律</h3>
     * <ul>
     *   <li>问的是 AE 的 {@link ICraftingProvider#getAvailablePatterns()}（gtlcore 样板仓
     *       与该接口的 javap 取证：{@code MEPatternBufferPartMachineBase implements PatternContainer,
     *       ICraftingProvider}）；<b>不看配方类型</b> —— 原生/超级样板总成本身就不按类型过滤，
     *       统合样板总成的类型过滤由它自己 + {@link IdleTableFollow} 负责；</li>
     *   <li><b>只在冷路径调用</b>（{@code ME 缓存为空} + {@code 通用 handler 看不到料}），
     *       且被 {@link BatchFinder} 的退避冷却（10→80 tick）限流 ⇒ 不会进每 tick 热路径；</li>
     *   <li>任何异常 ⇒ {@code false}（退回旧行为，绝不影响别的判据）。</li>
     * </ul>
     */
    static boolean hasWaitingMePattern(IRecipeLogicMachine machine) {
        try {
            if (machine.self() instanceof IMultiController controller && controller.isFormed()) {
                for (IMultiPart part : controller.getParts()) {
                    // ① 直连总成：问 AE 口径的样板表（原生 / 统合 / gtladditions 超级 / 本模组猪咪都实现该接口）。
                    if (part instanceof ICraftingProvider provider && hasAvailablePattern(provider)) {
                        return true;
                    }
                    // ② 代理 / 镜像总成：本体（真正的样板仓）挂在别的结构里，getParts() 里看不到它，
                    //    顺着 gtlcore 的公开口 getBuffer() 再问一次（未连线返回 null ⇒ 安全跳过）。
                    //    gtladditions 的 MESuperPatternBufferProxyPartMachine 也继承这个代理类 ⇒ 一并覆盖。
                    if (part instanceof MEPatternBufferProxyPartMachine proxy
                            && hasAvailablePattern(proxy.getBuffer())) {
                        return true;
                    }
                    // ③ 最后一道保险：任何 ME 样板部件只要有「活跃槽」（槽里有样板 **且** 槽里有料），
                    //    这台机器就该扫整表。依据 gtlcore MEPatternBufferPartMachineBase#getActiveSlots()
                    //    → InternalSlot#isActive()（hasPatternInSlot && 槽库存非空）；代理 handler 会委托真实仓。
                    //    这条与「AE 样板表」解耦：即使 getAvailablePatterns() 因上游改动/未刷新而为空，
                    //    「样板 + 料都在」这一手读数也能把冷槽救活。
                    if (part instanceof IMEPatternPartMachine me && hasActiveMeSlot(me)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 拿不到部件表就不拦（宁可多扫，绝不因此把该跑的单挡住）
        }
        return false;
    }

    /** 这个样板仓（可为 {@code null}）当前向 AE 报出的样板表是否非空。 */
    private static boolean hasAvailablePattern(ICraftingProvider provider) {
        if (provider == null) {
            return false;
        }
        try {
            List<IPatternDetails> patterns = provider.getAvailablePatterns();
            return patterns != null && !patterns.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 该 ME 样板部件是否至少有一个「活跃槽」（有样板且槽里有料）。 */
    private static boolean hasActiveMeSlot(IMEPatternPartMachine me) {
        try {
            var pair = me.getMERecipeHandlerTraits();
            return pair != null && (hasActiveSlot(pair.left()) || hasActiveSlot(pair.right()));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** ME 口径 handler 的活跃槽数是否 &gt; 0（不是 ME handler ⇒ {@code false}）。 */
    private static boolean hasActiveSlot(Object handler) {
        if (!(handler instanceof IMERecipeHandler<?, ?> meHandler)) {
            return false;
        }
        try {
            int[] slots = meHandler.getActiveSlots();
            return slots != null && slots.length > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 「无物品/流体输入」时的定位信息 —— 列出这台机器到底有没有输入仓、仓里有没有东西
     * （区分「没装输入仓」与「装了但是空的」）。
     */
    static String describeInputCaps(IRecipeLogicMachine machine) {
        try {
            Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> proxy = machine.getCapabilitiesProxy();
            if (proxy == null) {
                return "能力表=null";
            }
            Map<RecipeCapability<?>, List<IRecipeHandler<?>>> in = proxy.row(IO.IN);
            if (in == null) {
                return "IN能力表=null";
            }
            int itemHandlers = 0;
            int fluidHandlers = 0;
            int itemContent = 0;
            int fluidContent = 0;
            for (Map.Entry<RecipeCapability<?>, List<IRecipeHandler<?>>> entry : in.entrySet()) {
                RecipeCapability<?> cap = entry.getKey();
                if (cap != ItemRecipeCapability.CAP && cap != FluidRecipeCapability.CAP) {
                    continue;
                }
                for (IRecipeHandler<?> handler : entry.getValue()) {
                    if (cap == ItemRecipeCapability.CAP) {
                        itemHandlers++;
                    } else {
                        fluidHandlers++;
                    }
                    for (Object content : handler.getContents()) {
                        if (content instanceof ItemStack stack && !stack.isEmpty()) {
                            itemContent++;
                        } else if (content instanceof FluidStack fluid && fluid.getAmount() > 0) {
                            fluidContent++;
                        }
                    }
                }
            }
            return "IN能力=" + in.size() + " 物品仓=" + itemHandlers + " 流体仓=" + fluidHandlers
                    + " 物品条目=" + itemContent + " 流体条目=" + fluidContent
                    + " 样板仓=" + (hasMePatternPart(machine) ? "有" : "无");
        } catch (Throwable t) {
            return "输入仓探测异常:" + t.getClass().getSimpleName();
        }
    }

    /** 产物摘要（前 4 项），给「线程批次完成」那行用。 */
    static String describeOutputs(GTRecipe recipe) {
        if (recipe == null) {
            return "?";
        }
        try {
            List<String> out = new ArrayList<>();
            for (Content c : recipe.getOutputContents(ItemRecipeCapability.CAP)) {
                Object inner = ItemRecipeCapability.CAP.of(c.getContent());
                if (inner instanceof net.minecraft.world.item.crafting.Ingredient ingredient
                        && ingredient.getItems().length > 0) {
                    ItemStack stack = ingredient.getItems()[0];
                    out.add(net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(stack.getItem()).toString() + "×" + stack.getCount());
                }
            }
            for (Content c : recipe.getOutputContents(FluidRecipeCapability.CAP)) {
                Object inner = FluidRecipeCapability.CAP.of(c.getContent());
                if (inner instanceof com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient fluid
                        && fluid.getStacks().length > 0) {
                    out.add(net.minecraftforge.registries.ForgeRegistries.FLUIDS
                            .getKey(fluid.getStacks()[0].getFluid()).toString()
                            + "×" + fluid.getAmount() + "mB");
                }
            }
            if (out.isEmpty()) {
                return "无";
            }
            if (out.size() <= 4) {
                return String.join(" ", out);
            }
            return String.join(" ", out.subList(0, 4)) + " …(共 " + out.size() + " 项)";
        } catch (Throwable t) {
            return "?";
        }
    }

    /** {@code ActionResult.reason()} → 可读文本（取不到就给 {@code null}）。 */
    static String reasonText(GTRecipe.ActionResult result) {
        try {
            if (result != null && result.reason() != null) {
                Component reason = result.reason().get();
                if (reason != null) {
                    // 服务端没有语言表：Component#getString() 直接回翻译键（例如
                    // "gtceu.recipe_logic.insufficient_in: item"）⇒ 这里换成中文再进日志，
                    // 免得玩家在日志里看到一坨键名。
                    return reason.getString()
                            .replace("gtceu.recipe_logic.insufficient_in", "输入不足")
                            .replace("gtceu.recipe_logic.insufficient_out", "输出不足")
                            .replace("gtceu.recipe_logic.insufficient", "不足")
                            .replace(": item", ": 物品")
                            .replace(": fluid", ": 流体");
                }
            }
        } catch (Throwable ignored) {
            // 原因本身取不到不影响诊断
        }
        return null;
    }
}
