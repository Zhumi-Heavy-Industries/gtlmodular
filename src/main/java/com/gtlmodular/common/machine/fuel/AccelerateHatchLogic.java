package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.config.GTLModularConfig;


/**
 * 加速仓的<b>生效侧</b>：把结构里那台加速仓的百分比套到配方耗时上。
 *
 * <h2>为什么需要这一层（结构放行不等于真的加速）</h2>
 * <p>加速仓原本唯一的入口是 {@link com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart#modifyRecipe}，
 * 它只从 {@code WorkableMultiblockMachine#doModifyRecipe} 被调用，而 {@code doModifyRecipe} 的上游是
 * {@code RecipeLogic#fullModifyRecipe}。按字节码核实：
 * <ul>
 *   <li>gtceu 原生 {@code RecipeLogic} —— 常量池里有 {@code fullModifyRecipe} ⇒ <b>会</b>走到加速仓；</li>
 *   <li>gtlcore {@code MultipleRecipesLogic}（GTL 核心的多数巨构、终端、本模组 NAC 主机等）——
 *       常量池里<b>没有</b> {@code fullModifyRecipe} / {@code doModifyRecipe} / {@code modifyRecipe}，
 *       它自己在 {@code setupRecipe} 里读写 {@code GTRecipe.duration}。</li>
 * </ul>
 * ⇒ 只放开结构的话，这些机器会「装得上、界面正常、速度一点没变」，比装不上更糟。
 * 因此凡是不走 {@code doModifyRecipe} 的配方逻辑，都改由本类在它拿到配方时补一次同样的换算。
 *
 * <h2>不重复计算</h2>
 * <p>走原生链路的机器已经由 {@code AccelerateHatchPartMachine#modifyRecipe} 改过一次，
 * 本类<b>只</b>被 gtlcore 那条链路调用（见 {@code MultipleRecipesLogic}'s mixin），
 * 两条链路互斥，同一台机器不会被加速两次。
 *
 * <h2>数值</h2>
 * <p>换算公式只有一份，就是 {@link AccelerateHatchPartMachine#applyTo}；本类只负责「找到仓」。
 * 没有任何加速仓时原样返回，不产生任何副作用。
 */
public final class AccelerateHatchLogic {

    private AccelerateHatchLogic() {
    }

    /**
     * 在多方块里找加速仓（没有 / 不成形 / 不是多方块 ⇒ {@code null}）。
     *
     * <p>结构谓词已经把加速仓限制为最多一台；万一旧存档里有多台，取第一台，不叠加
     * —— 与超频仓同一条口径（加速仓不是并行仓，叠加没有意义）。
     */
    public static AccelerateHatchPartMachine find(MetaMachine machine) {
        if (!(machine instanceof IMultiController controller) || !controller.isFormed()) {
            return null;
        }
        for (IMultiPart part : controller.getParts()) {
            if (part instanceof AccelerateHatchPartMachine hatch) {
                return hatch;
            }
        }
        return null;
    }

    /**
     * 给一条配方套上加速仓的耗时百分比 —— 供 {@code RecipeLogic#setupRecipe} 钩子调用。
     *
     * <h2>为什么需要这个入口</h2>
     * <p>原生 {@code doModifyRecipe} 会遍历部件调 {@code IMultiPart#modifyRecipe}，
     * 但<b>不是所有机器都走那条路</b>：gtlcore 用 {@code @Overwrite} 换掉了
     * {@code doModifyRecipe}（不再遍历部件），还有一些机器（如 {@code isa_mill}）
     * 根本不继承 {@code WorkableMultiblockMachine}。{@code setupRecipe} 是所有配方逻辑
     * 的必经一步，把换算放这里覆盖面最广。
     *
     * <h2>★ 为什么必须先把原始时长存下来</h2>
     * <p>{@code setupRecipe} 会被<b>同一个配方对象</b>反复调用
     * （{@code onRecipeFinish} 里 {@code setupRecipe(this.lastRecipe)}）。
     * 若每次都乘一次百分比，时长会递进塌缩（实测 99 → 66 → 1），
     * 表现成「越跑越快」。
     *
     * <p>所以第一次进来时把<b>上一次结果</b>按「机器 + 配方 id」记下来，之后每次都从
     * 原始值重算。<b>不再写 {@code recipe.data}</b>：{@code GTRecipe.copy()} 按引用共享 data，
     * 同一配方被多台机器共用时，一台机器的结果会串到另一台（2026-11-25 实机事故）。
     * 记录的是「结果值」而不是「已完成」布尔标记 —— 结果值只在当前时长确实等于它时才命中，
     * 换配方或换档位就不匹配，不会让后续副本全部跳过换算。
     *
     * @param machine 正在处理配方的机器
     * @param recipe  它的配方（就地修改）
     * @return 同一个配方对象
     */
    public static GTRecipe applyIfNeeded(MetaMachine machine, GTRecipe recipe) {
        if (recipe == null || machine == null || !AccelerateHatchPredicate.runtimeEnabled()) {
            return recipe;
        }
        AccelerateHatchPartMachine hatch = find(machine);
        if (hatch == null) {
            return recipe;
        }
        // 基准 = 本轮处理前的时长。超频仓可能已经缩过一次，两者串联相乘，
        // 而不是各自从「原始值」重算后互相覆盖。
        //
        // 幂等：记住上一次本方法写出的结果；当前时长正好等于它就说明是
        // setupRecipe 对同一配方的重复调用，不重复乘。
        int base = recipe.duration;
        if (base <= 0) {
            return recipe;
        }
        // 幂等：记住上一次本方法写出的结果；当前时长正好等于它 就说明是
        // setupRecipe 对同一配方的重复调用，不重复乘。
        // ★ 2026-11-25：判重状态**不再写进 recipe.data**（GTRecipe.copy() 按引用共享 data，
        //   同一配方被多台机器共用时，一台机器的结果会串到另一台）。改为按「机器 + 配方 id」记。
        Integer last = lastResult(machine, recipe);
        if (last != null && last == base) {
            return recipe;
        }
        GTRecipe result = AccelerateHatchPartMachine.applyTo(
                recipe, hatch.getPercentage(), hatch.getTier());
        if (result == recipe) {
            rememberResult(machine, recipe, recipe.duration);
        }
        return result;
    }

    /** 机器 →（配方 id / 配方对象 → 上一次换算结果）。机器弱引用，不阻止回收。 */
    private static final java.util.Map<MetaMachine, java.util.Map<Object, Integer>> LAST_RESULTS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static Integer lastResult(MetaMachine machine, GTRecipe recipe) {
        synchronized (LAST_RESULTS) {
            java.util.Map<Object, Integer> byRecipe = LAST_RESULTS.get(machine);
            return byRecipe == null ? null : byRecipe.get(resultKey(recipe));
        }
    }

    private static void rememberResult(MetaMachine machine, GTRecipe recipe, int duration) {
        synchronized (LAST_RESULTS) {
            java.util.Map<Object, Integer> byRecipe =
                    LAST_RESULTS.computeIfAbsent(machine, m -> new java.util.HashMap<>());
            if (byRecipe.size() > 512) {
                byRecipe.clear();
            }
            byRecipe.put(resultKey(recipe), duration);
        }
    }

    /** 同一条配方的稳定身份：优先 recipe id（副本之间相同），没有 id 时退回对象身份。 */
    private static Object resultKey(GTRecipe recipe) {
        return recipe.id != null ? recipe.id : recipe;
    }

    /**
     * 纯数值版：按本仓的百分比把一段时长缩短。
     *
     * <p>供不持有 {@code GTRecipe} 的链路使用（如增强版的 IV 原生引擎，
     * 它自己维护 {@code IvJob.duration}，不经过 {@code RecipeLogic}）。
     *
     * @param machine  机器（用来找仓）
     * @param duration 原始时长（tick）
     * @param recipeTier 配方电压档（用于「仓低于配方时每级 -20%」的惩罚）
     * @return 缩短后的时长；没有仓时原样返回
     */
    public static int applyToDuration(MetaMachine machine, int duration, int recipeTier) {
        if (duration <= 0 || machine == null || !AccelerateHatchPredicate.runtimeEnabled()) {
            return duration;
        }
        AccelerateHatchPartMachine hatch = find(machine);
        if (hatch == null) {
            return duration;
        }
        int reduction = hatch.getPercentage();
        int t = recipeTier - hatch.getTier();
        if (t > 0) {
            reduction = Math.min(100, reduction + 20 * t);
        }
        return Math.max(1, duration * reduction / 100);
    }

    /** 界面/Jade 用：当前生效的加速百分比；没有加速仓返回 {@code -1}。 */
    public static int currentPercentage(MetaMachine machine) {
        AccelerateHatchPartMachine hatch = find(machine);
        return hatch == null ? -1 : hatch.getPercentage();
    }

    /** 开关现读一次，供 mixin 快速短路（避免每条配方都去遍历部件表）。 */
    public static boolean enabled() {
        return GTLModularConfig.isAccelerateHatchAnyMultiblock();
    }
}
