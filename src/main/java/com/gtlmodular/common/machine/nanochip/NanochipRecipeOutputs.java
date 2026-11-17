package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;

import java.util.ArrayList;
import java.util.List;

/**
 * 配方产物只读取值口 —— 从<b>旧世代逻辑类</b>里<b>原样搬出</b>的四个静态工具
 * （历史：那个类已随 P1 删除；Jade 侧不再依赖它，只依赖本工具类）。
 *
 * <h2>这组方法的定位（为什么是"镜像"而不是"复用权威实现"）</h2>
 * 识别/计数的权威实现在
 * {@code NanochipAssemblyComplexMachine#firstItemStackOf} 与 {@code #producedCountOf}，
 * 但两者都是 <b>private static</b>（同坑的 {@code NanochipSelfCheck#itemIdOf} 也是 private），
 * 本包取不到 ⇒ 这里<b>逐字镜像</b>那两段：三路兜底（ItemStack / ItemStack[] / Ingredient）
 * ＋计数顺序（{@code LongIngredient.getActualAmount()} → {@code SizedIngredient.getAmount()}
 * → 栈自身 count → 1）。口径必须与它们一致 ——
 * 本包（gtlcore 1.2.3.2）物品产出一律是 {@code gtceu:sized} ⇒ 反序列化后 content 一次都不是
 * ItemStack，只判 {@code instanceof ItemStack} 会恒假。
 *
 * <h2>本类只有只读语义</h2>
 * 不改配方、不改批次表、不进存档、不参与任何判定；所有方法都对 {@code null} 入参与
 * 运行时异常做收敛（取不到就返回 {@code null} / 空表 / {@code 0}，<b>绝不抛</b>），
 * 因为它们跑在 Jade 的写数据路径上，任何意外都只该表现为"这一条不显示产物"。
 */
public final class NanochipRecipeOutputs {

    private NanochipRecipeOutputs() {
    }

    /**
     * 只读：一条配方的第一个<b>可识别物品产物</b>的<b>物品注册名</b>
     * （{@code namespace:path}，例 {@code gtlmodular:cc_crystal_processor} / {@code gtceu:...}）。
     *
     * <h4>为什么回传"注册名"而不是本地化键（2026-11-05 语义变更）</h4>
     * 改前回传 {@code ItemStack#getDescriptionId()}（形如 {@code item.gtlmodular.cc_crystal_processor}）
     * = <b>本地化键</b>，<b>它画不出图标</b>：这个串只能拿去做
     * {@code Component.translatable(...)} 取译名，<b>反查不回 {@code Item}</b>
     * （键→物品没有注册表映射）⇒ 客户端拿不到 {@code ItemStack} ⇒ 拿不到 {@code IElement} 图标。
     * <p>现在回传 {@code BuiltInRegistries.ITEM.getKey(stack.getItem())} 的 {@code toString()}
     * = <b>注册名</b>：客户端 {@code ResourceLocation.tryParse(id)} →
     * {@code BuiltInRegistries.ITEM.get(...)} → {@code new ItemStack(item)}
     * ⇒ 既能取译名（{@code stack.getDescriptionId()}，与改前送出的串<b>逐字相同</b>
     * ⇒ 文案一个字不变），又能建<b>物品图标元素</b>。
     *
     * <p><b>为什么回传字符串而不是名字 / ItemStack / 配方对象</b>：服务端<b>没有</b>客户端的语言，
     * 在服务端取 {@code stack.getHoverName().getString()} 在专用服务器上只会得到 lang 键本身；
     * 而回传 ItemStack 全量 NBT 会把 Jade 的载荷翻好几倍。⇒ 只送一个 ≤ 约 60 字符的 ASCII 串，
     * 客户端自己按<b>玩家自己的语言</b>渲染译名、自己重建图标。注册名与本地化键<b>长度同量级</b>
     * （例：{@code gtlmodular:cc_crystal_processor} 30 字符 /
     * {@code item.gtlmodular.cc_crystal_processor} 35 字符）⇒ 载荷量级不变。
     *
     * <p>⚠ <b>方法名保留</b>（{@code ...OutputId}）：返回的就是"产物这件物品的 id"
     * —— 只是这个 id 从<b>翻译用 id</b>换成了<b>注册用 id</b>。不改名是为了不动调用点与既有注释锚点。
     *
     * @return 注册名（{@code namespace:path}）；{@code null} = <b>取不到</b>（没有物品产物 /
     *         纯流体产物 / content 识别不出 / Ingredient 未解析 / <b>注册表里查不到这件物品</b>）——
     *         调用方据此<b>不写键</b>，客户端照实写「无物品产物或未识别」
     */
    public static String firstItemOutputId(GTRecipe recipe) {
        Content content = firstItemOutputContent(recipe);
        if (content == null) {
            return null;
        }
        ItemStack stack = itemStackOfContent(content.content);
        if (stack == null || stack.isEmpty()) {
            // 边界【空气】：itemStackOfContent 已经过滤过 isEmpty，这里是第二道闸（防御性，不改变口径）。
            return null;
        }
        // 注册表命中：返回 `namespace:path`（客户端据此重建 ItemStack 画图标）。
        // 查不到（未注册的物品 / 物品被数据包换掉）：返回 null ⇒ 调用方不写键
        //   ⇒ 客户端照实写「无物品产物或未识别」，绝不伪造一个 id，也绝不画空图标。
        // BuiltInRegistries 是静态注册表，客户端/服务端查同一份 ⇒ 客户端重建必然与这里同解。
        ResourceLocation id;
        try {
            id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        } catch (RuntimeException e) {
            // 注册表自身异常（理论上不可达）⇒ 收敛成"取不到"，绝不掀翻 Jade 的写数据路径。
            return null;
        }
        return id == null ? null : id.toString();
    }

    /**
     * 只读：与 {@link #firstItemOutputId} <b>同一个产物</b>的数量。
     *
     * <p>★ <b>这个数已含并行 p，绝不再乘</b>：进批次表的配方是
     * 旧并行拟合器 {@code scalePrecisely(modified, parallel)} 缩放后的<b>同一个对象</b>
     * （历史：旧并行拟合器 / 旧批次表两个类已随 P1 删除；
     * 当时是开批次路径把缩放后的配方放进 {@code Batch#recipe}），而 {@code scalePrecisely} 走
     * {@code recipe.copy(ContentModifier.multiplier(p), false)}（输入输出一并放大）
     * ⇒ {@code LongIngredient.getActualAmount()} / {@code SizedIngredient.getAmount()} 读到的
     * 就是「每份 × p」= 这一批的<b>真实产出总数</b>。证据链与结论同
     * {@code NanochipAssemblyComplexMachine#recordProduction}（那里定案的缺陷正是
     * 「再乘一次并行 ⇒ 记成 每份 × p²」）⇒ 本方法<b>只读内容数量</b>。
     *
     * <p><b>纯只读</b>：与 {@link #firstItemOutputId} 同一份数据、同一顺序，两次调用各自遍历
     * 物品产物表（该表 1~3 项，成本可忽略）。
     *
     * @return 数量（≥ 1）；{@code 0} = 取不到（理由同 {@link #firstItemOutputId}）
     */
    public static long firstItemOutputAmount(GTRecipe recipe) {
        Content content = firstItemOutputContent(recipe);
        return content == null ? 0L : producedAmountOfContent(content.content);
    }

    /**
     * 见 {@link #firstItemOutputId}：第一个「能识别出物品」的产物内容；{@code null} = 取不到。
     *
     * <p>只认物品产物（{@code ItemRecipeCapability.CAP}）：纯流体产物的线程**没有**物品产物可显示
     * ⇒ 返回 {@code null}，由显示层照实写「无物品产物或未识别」，<b>不</b>拿别的键顶上、<b>不</b>编数量。
     * 与校准记账同款：{@code recordProduction} 也只读这一个 capability，且不按 {@code chance} 过滤。
     */
    private static Content firstItemOutputContent(GTRecipe recipe) {
        if (recipe == null) {
            return null;
        }
        List<Content> outs;
        try {
            outs = recipe.getOutputContents(ItemRecipeCapability.CAP);
        } catch (RuntimeException e) {
            // 配方被数据包换掉 / 未解析 ⇒ 当作"取不到"。调用方是 Jade 的写数据路径，
            // 任何意外都只该表现为"这一条不显示产物"，绝不掀翻准星提示或服务端主线程。
            return null;
        }
        if (outs == null || outs.isEmpty()) {
            return null;
        }
        for (Content c : outs) {
            if (c != null && itemStackOfContent(c.content) != null) {
                return c;
            }
        }
        return null;
    }

    /**
     * 见 {@link #firstItemOutputId}：三路兜底取物品栈
     * （<b>逐字镜像</b> {@code NanochipAssemblyComplexMachine#firstItemStackOf}）。
     */
    private static ItemStack itemStackOfContent(Object content) {
        if (content instanceof ItemStack stack) {
            return stack.isEmpty() ? null : stack;
        }
        if (content instanceof ItemStack[] stacks) {
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) {
                    return stack;
                }
            }
            return null;
        }
        if (content instanceof Ingredient ingredient) {
            try {
                ItemStack[] stacks = ingredient.getItems();
                if (stacks != null) {
                    for (ItemStack stack : stacks) {
                        if (stack != null && !stack.isEmpty()) {
                            return stack;
                        }
                    }
                }
            } catch (RuntimeException e) {
                // 未解析的 tag / 空 Ingredient ⇒ 取不到（同 firstItemStackOf 的处理：
                // 只把"取不到"收敛成 null，不吞掉别的逻辑错误）
                return null;
            }
        }
        return null;
    }

    /**
     * 见 {@link #firstItemOutputId}：一份产出内容标称产出多少个
     * （<b>逐字镜像</b> {@code NanochipAssemblyComplexMachine#producedCountOf}）。
     *
     * <p>必须优先读 Ingredient <b>自带的份数</b>：{@code SizedIngredient} / {@code LongIngredient}
     * 的 {@code getItems()} 给的是<b>单份</b>样品（count 1），真实份数存在包装里（例：晶体处理器 8 个）。
     */
    private static long producedAmountOfContent(Object content) {
        if (content instanceof LongIngredient longIngredient) {
            return longIngredient.getActualAmount();
        }
        if (content instanceof SizedIngredient sized) {
            return sized.getAmount();
        }
        ItemStack stack = itemStackOfContent(content);
        return stack == null ? 1L : Math.max(1L, (long) stack.getCount());
    }

    /**
     * 一条配方的<b>全部物品产物</b>（注册名 + 数量，已含并行）。
     *
     * <p>与 {@link #firstItemOutputId} 同一套取数方式（{@link #itemStackOfContent} /
     * {@link #producedAmountOfContent}），只是不取「第一个」而是全部；
     * 供 Jade 逐线程列多个产物（通用线程引擎分支）。
     */
    public static List<OutputView> itemOutputs(GTRecipe recipe) {
        List<OutputView> list = new ArrayList<>();
        if (recipe == null) {
            return list;
        }
        try {
            List<Content> outs = recipe.getOutputContents(ItemRecipeCapability.CAP);
            if (outs != null) {
                for (Content c : outs) {
                    if (c == null) {
                        continue;
                    }
                    ItemStack stack = itemStackOfContent(c.content);
                    if (stack == null || stack.isEmpty()) {
                        continue;
                    }
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    long amount = producedAmountOfContent(c.content);
                    if (id != null && amount > 0L) {
                        // ★ 2026-11-08：把**真实 ItemStack**也带上（含 NBT）——
                        //   只送注册名会让客户端 new ItemStack(item) 丢掉材质/NBT，
                        //   图标与译名都可能变成「通用物品」那一版。
                        list.add(new OutputView(id.toString(), amount, false, stack));
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // 配方被数据包换掉 / 未解析 ⇒ 当作取不到，绝不影响 Jade 写数据。
        }
        return list;
    }

    /** 一条配方的<b>全部流体产物</b>（注册名 + mB，已含并行）。 */
    public static List<OutputView> fluidOutputs(GTRecipe recipe) {
        List<OutputView> list = new ArrayList<>();
        if (recipe == null) {
            return list;
        }
        try {
            List<Content> outs = recipe.getOutputContents(FluidRecipeCapability.CAP);
            if (outs != null) {
                for (Content c : outs) {
                    if (c == null) {
                        continue;
                    }
                    FluidIngredient ing = FluidRecipeCapability.CAP.of(c.content);
                    if (ing == null) {
                        continue;
                    }
                    long amount = ing.getAmount();
                    FluidStack[] stacks = ing.getStacks();
                    if (amount <= 0L || stacks == null || stacks.length == 0) {
                        continue;
                    }
                    ResourceLocation id = BuiltInRegistries.FLUID.getKey(stacks[0].getFluid());
                    if (id != null) {
                        // ★ 2026-11-08：带上**真实 FluidStack**（含 tag）——
                        //   只送注册名会丢掉流体的 NBT 变体/材质信息，贴图与译名都会走样。
                        list.add(new OutputView(id.toString(), amount, true, stacks[0]));
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // 同上：取不到就不显示，绝不影响主路径。
        }
        return list;
    }

    /**
     * 一条产物：{@code id} = 物品/流体注册名；{@code fluid} = 是否流体；{@code amount} = 数量（已含并行）。
     *
     * <p>★ 2026-11-08：新增 {@link #content}——这一条产物对应的**真实对象**
     * （物品是 {@link ItemStack}，流体是 {@link FluidStack}，都含 NBT）。
     * 老的三参构造保留（{@code content == null}），调用方按需取用：
     * 只做求和/计数的路径可以忽略它，Jade 显示路径用它来「直接引用」原贴图与原译名。
     */
    public record OutputView(String id, long amount, boolean fluid,
                             @org.jetbrains.annotations.Nullable Object content) {

        /** 兼容旧用法：不带真实对象。 */
        public OutputView(String id, long amount, boolean fluid) {
            this(id, amount, fluid, null);
        }
    }
}
