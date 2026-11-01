package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtlmodular.common.data.nanochip.NanochipCircuitComponents;
import com.gtlmodular.common.data.nanochip.NanochipRecipeTypes;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 移植版 <b>W2「真件 ⇄ CC」的运行时映射表</b>。
 *
 * <h3>为什么需要它（NH 出处）</h3>
 * 原版 NAC 的控制室每 5 秒把输入总线里的真实物品「封装」成 CC 送进真空管道
 * （NH 字节码 {@code MTENanochipAssemblyComplex.processRealItemInputs}：拿真件 →
 * {@code RecipeMaps.nanochipConversionRecipes.findRecipeQuery().items(stack).find()} →
 * 把产出的假栈投进同色管道）。<b>没有这一步，模块拉到的是真件，而模块配方要的是
 * {@code gtlmodular:cc_*} 假栈 ⇒ 永远匹配不上，整条链在第三跳断掉。</b>
 *
 * <h3>单一真相：映射来源就是我们自己的 CONVERSION 配方表</h3>
 * 本类<b>不新增任何数据源</b>：它扫描 {@code server.getRecipeManager()} 里
 * {@code type == NanochipRecipeTypes.CONVERSION} 的配方（= 95 条
 * {@code 真件×1 → cc_*×1}，由 {@code _census/gen_nac_p1_recipes.js} 从 NH 的
 * {@code registerConversionRecipes()} 逐条落地的），现场建出两张表：
 * <ul>
 *   <li>{@code realToCc}：真件物品 → CC 物品（封装用，VCO）；</li>
 *   <li>{@code ccToReal}：CC 物品 → 真件物品（还原用，VCI）。</li>
 * </ul>
 * 因此「JEI 里看到的换算配方」与「机器实际做的换算」<b>必然是同一份数据</b> ——
 * 不存在第二份需要手工同步的表。
 *
 * <h3>两个已登记的风险（P3 方案 §3.4i 的 R-W2-1 / R-W2-2）</h3>
 * <ul>
 *   <li><b>R-W2-1 幂等</b>：入网前先判「已经是 CC 物品了吗」⇒ 已经 {@code cc_*} 的件原样入网，
 *       绝不做二次换算（{@link #encapsulate}）；</li>
 *   <li><b>R-W2-2 反向一对多</b>：反向表<b>以 CC 物品为键</b>（不是以真件为键）。真件侧可能有
 *       多条配方指向不同 CC（例如 4 档超导线材），若按真件反查必然串号 ——
 *       {@link #materialize} 只按手里那件 CC 自己的 id 反查。</li>
 * </ul>
 *
 * <h3>缓存失效</h3>
 * 只在「{@link RecipeManager} 实例换了」时重建（{@code /reload} 会换实例）。
 * 不注册任何事件钩子，也不依赖 tick 计数 —— 服务器线程串行调用，重入用类锁兜住。
 */
public final class NanochipConversionMap {

    private NanochipConversionMap() {}

    /** 建表依据的配方管理器；换了它就重建。 */
    private static volatile RecipeManager builtFor = null;

    /** 真件物品 → CC 物品（数量 1 的模板）。 */
    private static volatile Map<Item, ItemStack> realToCc = Map.of();

    /** CC 物品 → 真件物品（数量 1 的模板）。★ 以 CC 为键，见类注释 R-W2-2。 */
    private static volatile Map<Item, ItemStack> ccToReal = Map.of();

    /** 被采纳的配方条数（形状为「1 物品进 1 物品出」才算）。 */
    private static volatile int accepted = 0;

    /** 键冲突计数（真件侧 / CC 侧各一个）；非 0 说明配方表有歧义，自检会报。 */
    private static volatile int realSideCollisions = 0;
    private static volatile int ccSideCollisions = 0;

    /**
     * <b>撞件但已按材料名确定性裁决</b>的真件数。
     *
     * <p>现实例（2026-09-18 已登记）：{@code gtceu:yttrium_barium_cuprate_bolt} 同时是
     * {@code cc_bolt_yttrium_barium_cuprate}（真件）与 {@code cc_bolt_umv_superconductor}
     * （玩家裁决的<b>替身</b>：NH 的 {@code Materials.SuperconductorUMVBase} bolt 本包不存在）
     * 的输入。裁决规则见 {@link #preferByMaterialName}：材料名对上的那件赢 ⇒
     * YBCO bolt 稳定封装成 {@code cc_bolt_yttrium_barium_cuprate}，替身那条**放弃**
     * （它的真件本来就是假的，宁可"封装不出来"也不要"封错件"）。
     */
    private static volatile int disputedResolved = 0;

    /** 撞件且**连材料名都对不上**的真件数（真正的无解歧义；>0 时这些件一律不封装）。 */
    private static volatile int disputedUnresolved = 0;

    /** 已裁决的真件 → 胜出的 CC（供自检/排障显示，只读）。 */
    private static volatile Map<Item, ItemStack> disputedPicks = Map.of();

    /**
     * 按需重建映射表（每 tick 由真空仓调用；命中缓存时只是两次引用比较）。
     */
    public static void ensure(MinecraftServer server) {
        if (server == null) {
            return;
        }
        RecipeManager rm = server.getRecipeManager();
        if (rm == builtFor) {
            return;
        }
        synchronized (NanochipConversionMap.class) {
            if (rm == builtFor) {
                return;
            }
            Map<Item, ItemStack> fwd = new LinkedHashMap<>();
            Map<Item, ItemStack> rev = new LinkedHashMap<>();
            Map<Item, java.util.List<ItemStack>> fwdAll = new LinkedHashMap<>();
            int n = 0;
            int dupCc = 0;
            for (Recipe<?> recipe : rm.getRecipes()) {
                // GTRecipeType 自己就实现了 vanilla 的 RecipeType ⇒ 身份比较最稳
                // （与 NanochipSelfCheck.recipeChecks 同款写法）。
                if (recipe.getType() != NanochipRecipeTypes.CONVERSION) {
                    continue;
                }
                if (!(recipe instanceof GTRecipe gt)) {
                    continue;
                }
                List<ItemStack> in = RecipeHelper.getInputItems(gt);
                List<ItemStack> out = RecipeHelper.getOutputItems(gt);
                if (in.size() != 1 || out.size() != 1) {
                    continue; // 形状不是 1→1 的一律不参与映射（也不计入 accepted）
                }
                ItemStack real = in.get(0);
                ItemStack cc = out.get(0);
                if (real.isEmpty() || cc.isEmpty()) {
                    continue;
                }
                n++;
                fwdAll.computeIfAbsent(real.getItem(), k -> new java.util.ArrayList<>(1)).add(cc.copyWithCount(1));
                if (rev.put(cc.getItem(), real.copyWithCount(1)) != null) {
                    dupCc++;
                }
            }
            // 真件侧：无歧义的直接进表；有歧义的按材料名裁决（见 preferByMaterialName）。
            int disputed = 0;
            int unresolvedDisputed = 0;
            Map<Item, ItemStack> picks = new LinkedHashMap<>();
            for (Map.Entry<Item, java.util.List<ItemStack>> e : fwdAll.entrySet()) {
                java.util.List<ItemStack> cands = e.getValue();
                if (cands.size() == 1) {
                    fwd.put(e.getKey(), cands.get(0));
                    continue;
                }
                disputed++;
                String realPath = realPathOf(e.getKey());
                ItemStack pick = preferByMaterialName(realPath, cands);
                if (pick == null) {
                    unresolvedDisputed++; // 无解：这件**不封装**（宁可不动，也不要封错件）
                    continue;
                }
                fwd.put(e.getKey(), pick);
                picks.put(e.getKey(), pick);
            }
            realToCc = Map.copyOf(fwd);
            ccToReal = Map.copyOf(rev);
            accepted = n;
            realSideCollisions = disputed;
            ccSideCollisions = dupCc;
            disputedResolved = picks.size();
            disputedUnresolved = unresolvedDisputed;
            disputedPicks = Map.copyOf(picks);
            builtFor = rm;
        }
    }

    /** 真件的注册路径（如 {@code gtceu:yttrium_barium_cuprate_bolt}）；拿不到返回空串。 */
    private static String realPathOf(Item item) {
        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
        return id == null ? "" : id.toString();
    }

    /**
     * <b>撞件的确定性裁决</b>：材料名对上的那件赢。
     *
     * <p>做法：把真件路径（{@code gtceu:yttrium_barium_cuprate_bolt}）逐次去掉末尾的下划线片段，
     * 得到候选材料名（{@code yttrium_barium_cuprate} → {@code yttrium_barium} → {@code yttrium}），
     * 取**最长且唯一命中**的那个；看候选 CC 的物品路径（{@code cc_bolt_yttrium_barium_cuprate}）
     * 是否含该材料名。命中恰好一个 ⇒ 它就是答案；命中 0 个或多于 1 个 ⇒ 返回 {@code null}（无解）。
     *
     * @param realPath 真件完整路径（含命名空间）
     * @param cands    撞在这件真件上的所有 CC 候选
     */
    private static ItemStack preferByMaterialName(String realPath, List<ItemStack> cands) {
        int colon = realPath.indexOf(':');
        String path = colon < 0 ? realPath : realPath.substring(colon + 1);
        if (path.isEmpty()) {
            return null;
        }
        int underscore = path.lastIndexOf('_');
        while (underscore > 0) {
            String token = path.substring(0, underscore);
            ItemStack hit = null;
            int hits = 0;
            for (ItemStack cand : cands) {
                String ccPath = realPathOf(cand.getItem());
                int c2 = ccPath.indexOf(':');
                String ccBody = c2 < 0 ? ccPath : ccPath.substring(c2 + 1);
                if (ccBody.contains(token)) {
                    hit = cand;
                    hits++;
                }
            }
            if (hits == 1) {
                return hit;
            }
            if (hits > 1) {
                return null; // 这一层材料名同时命中多件 ⇒ 判不了，不猜
            }
            underscore = path.lastIndexOf('_', underscore - 1);
        }
        return null;
    }

    /** 手里这件是真件时，返回它对应的 CC（数量随原件）；已经是 CC 物品则原样返回；查不到返回空。 */
    public static ItemStack encapsulate(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (isCc(stack)) {
            return stack.copy(); // R-W2-1：幂等，绝不做二次换算
        }
        ItemStack cc = realToCc.get(stack.getItem());
        return cc == null ? ItemStack.EMPTY : cc.copyWithCount(stack.getCount());
    }

    /** 手里这件是 CC 时，返回它能还原出的真件（数量随原件）；非 CC / 查不到则原样返回（不吞件）。 */
    public static ItemStack materialize(ItemStack stack) {
        if (stack.isEmpty() || !isCc(stack)) {
            // 旧存档里网络直接存过真件（W2 之前的写法）⇒ 原样交回，不做任何转换。
            return stack;
        }
        ItemStack real = ccToReal.get(stack.getItem());
        return real == null ? stack : real.copyWithCount(stack.getCount());
    }

    /**
     * CC 物品入网时的「可还原」标志：<b>由件本身决定</b> —— 未处理 CC 可还原，已处理 PC 不可还原
     * （NH：{@code gt.circuitcomponent.tooltip.cc.base} / {@code .pc.base}）。
     *
     * @return {@code null} = 这不是 CC 物品（调用方按自己的规则兜底）
     */
    public static Boolean materializableFlag(ItemStack stack) {
        if (!isCc(stack)) {
            return null;
        }
        return !((NanochipCircuitComponents.CcItem) stack.getItem()).circuitComponent().processed;
    }

    /** 这件是不是本模组的 CC 物品（含已处理 PC —— PC 也是同一个物品类）。 */
    public static boolean isCc(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof NanochipCircuitComponents.CcItem;
    }

    /** 已建表的条数（应为 95：= 未处理 CC 数）。 */
    public static int size() {
        return accepted;
    }

    public static int realSideCollisions() {
        return realSideCollisions;
    }

    public static int ccSideCollisions() {
        return ccSideCollisions;
    }

    /**
     * 已按材料名确定性裁决的撞件数。
     *
     * <p>实例（2026-09-18 已登记）：{@code gtceu:yttrium_barium_cuprate_bolt} 同时是真件
     * {@code cc_bolt_yttrium_barium_cuprate} 与替身 {@code cc_bolt_umv_superconductor} 的输入
     * ⇒ 按 {@link #preferByMaterialName} 判给材料名对上的前者；替身那条放弃封装。
     */
    public static int resolvedDisputes() {
        return disputedResolved;
    }

    /** 撞件且材料名也判不了的件数（>0 ⇒ 这些件一律不封装；真的需要人来裁决）。 */
    public static int unresolvedDisputes() {
        return disputedUnresolved;
    }

    /** 已裁决的「真件 → 胜出的 CC」（只读，供自检/排障显示）。 */
    public static Map<Item, ItemStack> resolvedPicks() {
        return disputedPicks;
    }
}
