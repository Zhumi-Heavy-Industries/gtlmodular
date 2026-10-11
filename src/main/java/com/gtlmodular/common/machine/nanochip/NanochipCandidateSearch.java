package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ★ 2026-10-28【独立跨配方线程】<b>「这一轮要搜哪些配方类型」+「候选截断到线程数」的唯一实现</b>
 * —— 旧逻辑（{@code NanochipRecipeLogic}）与新逻辑（{@link NanochipThreadedRecipeLogic}）<b>共用同一份</b>，
 * 于是「跨配方类型」这条语义在两条派活通路上<b>只有一处口径</b>（不会一边改、一边忘）。
 *
 * <h3>为什么抽出来（本轮「跨类型」的落点）</h3>
 * 上一轮的多类型搜索写在 {@code NanochipRecipeLogic.lookupRecipeIteratorInternal()} 里，
 * 它做的事用一句话说就是：<b>候选来源 = 本机声明的全部类型（不是当前激活的那一个）</b>，
 * 再截断到线程数、并按游标公平轮转。新的独立线程逻辑需要<b>完全相同</b>的候选口径
 * （否则「跨配方类型的独立线程」就退化成「只搜激活类型的线程」，正是我们要避免的
 * —— 参考实现 GTET {@code ThreadedRecipeLogic.kt:474} 就是单数 {@code machine.recipeType}，
 * 我们<b>不照抄</b>那一处）⇒ 抽成本类，两条通路都从这里取候选。
 *
 * <h3>逐字出处（旧实现的那一段，一字未变语义）</h3>
 * <ul>
 *   <li>类型遍历：{@code machine.getRecipeTypes()}（复数）→ 逐类型
 *       {@code type.getLookup().getRecipeIterator(machine, recipe -&gt; true)}；
 *       机制文档逐字：gtladditions 指南 {@code .../guide/_zh_cn/machine/multi_type.md:16}
 *       「当一台机器支持多个配方类别时，它可以在<b>同一轮工作中同时处理不同类别下的配方</b>」；</li>
 *   <li>截断 + 轮转：{@link FairCandidateSelector#select}（照 Enhancedcore
 *       {@code ThreadLimitedRecipeLogic.java:307-320} 的写法，说明见该类的注释）。</li>
 * </ul>
 *
 * <h3>「候选校验」为什么由调用方给（而不是本类自己判）</h3>
 * 旧实现的候选校验是 gtladditions 父类的 {@code checkRecipe(GTRecipe)}
 * （{@code ThreadLimitedRecipeLogic.java:245-270} 逐字展示了它的口径：输入放得下 + 产出放得下 +
 * 电压等级够 + 配方自带条件），新逻辑不能调它（它属于 gtladditions 那一族）⇒
 * 本类把校验抽成入参 {@link Eligibility}，<b>旧逻辑传它自己的 {@code checkRecipe}、
 * 新逻辑传自己那份「只读试算」</b>。两边的判据文本都写在各自的调用点，本类不做任何判定。
 *
 * <h3>异常口径（与旧实现逐字一致，不许改）</h3>
 * <ul>
 *   <li>{@link #collectFromType} <b>不</b>自吞异常：某个类型搜索时抛出的 {@code RuntimeException}
 *       会照原样冒到调用方 ⇒ 语义是「<b>放弃本次搜索</b>（本 tick 不开工）」，而<b>不是</b>
 *       「跳过该类型继续搜别的」（那会静默改变错误可见性）；</li>
 *   <li>拿不到类型数组 / 类型位是 null ⇒ 保守跳过（绝不 NPE 进 tick）。</li>
 * </ul>
 *
 * <h3>零存档影响</h3>
 * 不新增 {@code @Persisted}、不写 NBT、不改 {@code getRecipeTypes()} 的返回内容与顺序；
 * 轮转游标由调用方持有（本类无状态）。
 */
public final class NanochipCandidateSearch {

    private NanochipCandidateSearch() {
    }

    /** 候选校验：这条配方这一刻可不可以用（只读判定，<b>不许</b>有任何副作用/扣料）。 */
    @FunctionalInterface
    public interface Eligibility {

        boolean isUsable(GTRecipe candidate);
    }

    /**
     * 搜一轮候选：<b>遍历给定类型</b>取候选 → 按 {@code eligibility} 逐条校验 → 截断到 {@code threads} 条并轮转。
     *
     * @param machine    配方逻辑所属机器（提供能力代理；类型表已由调用方按开关算好）
     * @param types      本轮要搜的类型（{@code null} / 空 ⇒ 返回空集）；元素为 {@code null} 时保守跳过
     * @param single      只有一个类型时用哪张表（正常情况下就是 {@code types[0]}）；
     *                    {@code types} 为空但本参数非 null 时按「单类型」语义搜它
     *                    —— 这一条对应旧实现里「多类型开关关掉时只搜 {@code machine.getRecipeType()}」
     * @param eligibility 候选校验（见类注释）
     * @param threads    本轮最多同时挂起几条（{@code <= 0} 由 {@link FairCandidateSelector} 按 1 处理）
     * @param cursor     轮转游标（长度 ≥ 1；本方法把本次最后一条的 id 写进 {@code cursor[0]}）
     * @return 被选中的配方集合（可能为空 = 本 tick 不开工）；空集用 {@code Set.of()}
     */
    public static Set<GTRecipe> search(IRecipeLogicMachine machine,
                                       GTRecipeType[] types,
                                       GTRecipeType single,
                                       Eligibility eligibility,
                                       int threads,
                                       String[] cursor) {
        // ★ 2026-11-01【候选为空 · 修复】本方法现在只是 searchIsolating 的「异常照旧上抛」形态，
        //   语义与改动前**逐字等价**：某个类型搜索时抛出的 RuntimeException 照原样冒到调用方
        //   ⇒ 「放弃本次搜索（本 tick 不开工）」（这一条是旧逻辑既有口径，不许改）。
        //   新逻辑（NanochipThreadedRecipeLogic）走 searchIsolating：逐类型隔离 + 收集失败说明。
        return searchIsolating(machine, types, single, eligibility, threads, cursor, null, null);
    }

    /**
     * ★ 2026-11-01【独立线程候选为空 · 诊断 + 兜底】与 {@link #search} <b>共用同一份实现</b>，
     * 唯一差别是「某个类型搜索时抛异常」怎么办：
     *
     * <ul>
     *   <li>{@code failures == null}（旧逻辑那条路，即 {@link #search}）⇒ 异常<b>照原样上抛</b>
     *       = 「放弃本次搜索」。既有语义、一字未改；</li>
     *   <li>{@code failures != null}（新逻辑那条路）⇒ 把失败说明记进 {@code failures} 后
     *       <b>只跳过那一个类型</b>，其余类型照常收候选 ——
     *       新逻辑的候选池每 tick 重建，<b>一个坏类型不该让整台机器一条批次都开不出来</b>
     *       （改前的表现就是「在用 0/N」而日志里什么都看不到）。</li>
     * </ul>
     *
     * <p><b>为什么不让新逻辑自己写一份遍历</b>：候选口径（类型遍历 + eligibility + 截断轮转）
     * 必须只有一处实现，否则新旧两条派活通路迟早走岔（见类注释）。
     *
     * @param failures 非 null 时按类型收集失败说明（{@code 类型id → 异常类: 消息}）；
     *                 null = 旧语义（异常上抛）
     * @param stats    可选出参（长度 ≥ 2 时才写）：{@code stats[0]} = 各类型<b>原始枚举</b>到的条数
     *                 （还没过 {@code eligibility}），{@code stats[1]} = 通过校验的条数
     */
    public static Set<GTRecipe> searchIsolating(IRecipeLogicMachine machine,
                                                GTRecipeType[] types,
                                                GTRecipeType single,
                                                Eligibility eligibility,
                                                int threads,
                                                String[] cursor,
                                                List<String> failures,
                                                int[] stats) {
        if (machine == null || eligibility == null) {
            return Set.of();
        }
        Set<GTRecipe> candidates = new LinkedHashSet<>();
        int raw = 0;
        if (types != null && types.length > 0) {
            for (GTRecipeType type : types) {
                if (type == null) {
                    continue;
                }
                raw += collectFromTypeOrFail(machine, type, eligibility, candidates, failures);
            }
        } else if (single != null) {
            raw += collectFromTypeOrFail(machine, single, eligibility, candidates, failures);
        }
        if (stats != null && stats.length >= 2) {
            stats[0] = raw;
            stats[1] = candidates.size();
        }
        if (candidates.isEmpty()) {
            return Set.of();
        }
        String last = (cursor == null || cursor.length == 0) ? null : cursor[0];
        // ★ 排序键：`GTRecipe.getId()` 返回 ResourceLocation（**不是** CharSequence）⇒
        //   这里显式转成 String，类型才对得上（旧实现的注释同款）。
        Set<GTRecipe> selected = FairCandidateSelector.select(candidates, threads, last,
                recipe -> recipe.getId() == null ? null : recipe.getId().toString());
        if (selected.isEmpty() || cursor == null || cursor.length == 0) {
            return selected;
        }
        // 游标推进到本次最后一条（下一轮从它之后继续 ⇒ 公平轮转，常备配方不会霸占线程）
        for (GTRecipe recipe : selected) {
            if (recipe.getId() != null) {
                cursor[0] = recipe.getId().toString();
            }
        }
        return selected;
    }

    /**
     * 把<b>某一个</b>配方类型下的候选收进 {@code candidates}，并返回该类型<b>原始枚举</b>到的条数
     * （含被 {@code eligibility} 拒掉的）。
     *
     * <p>逐字保留旧实现那段「取迭代器 → 逐条校验 → 收进集合」的全部语义
     * （唯一差别是类型从「当前激活的那一条」变成「入参给的那一条」）：
     * {@code GTRecipeLookup.getRecipeIterator(holder, predicate)} 的返回值可直接落到
     * {@code Iterator<GTRecipe>}。注意 {@code IAdditionalRecipeIterator} 与
     * {@code GTRecipeLookup.RecipeIterator} 是<b>平级</b>的两个类型，所以这里<b>不</b>做
     * 「增强搜索开关」——候选集本身已由 {@code eligibility} 逐条校验，
     * 少开一个搜索增强换来的只是「候选可能略少」，属保守一侧。
     *
     * <p>★ 2026-11-01：异常口径由 {@code failures} 决定（{@code null} = 照原样上抛 = 旧语义；
     * 非 null = 记一行后只跳过这个类型）——逐字见 {@link #searchIsolating} 的注释。
     */
    private static int collectFromTypeOrFail(IRecipeLogicMachine machine, GTRecipeType type,
                                             Eligibility eligibility, Set<GTRecipe> candidates,
                                             List<String> failures) {
        int raw = 0;
        try {
            Iterator<GTRecipe> iterator = type.getLookup().getRecipeIterator(machine, recipe -> true);
            while (iterator.hasNext()) {
                GTRecipe candidate = iterator.next();
                raw++;
                if (candidate != null && eligibility.isUsable(candidate)) {
                    candidates.add(candidate);
                }
            }
        } catch (RuntimeException e) {
            if (failures == null) {
                throw e;   // ★ 旧路径语义（见类注释「异常口径」）：异常上抛 ⇒ 放弃本次搜索
            }
            // ★ 新路径：只跳过这个类型（记一行失败说明，由调用方限频打出来）
            failures.add((type.registryName == null ? "?" : type.registryName.toString())
                    + " → " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
        return raw;
    }

    /**
     * 供调用方做诊断用：把「本轮要搜的类型」摊成可读串（只读、不参与判定）。
     *
     * <p>存在的理由：跨类型这件事在游戏里唯一的可见痕迹就是「不同类型同时开线程」，
     * 而这需要玩家/维护者能一眼看出「这台机器这一轮到底搜了哪几张表」。
     */
    public static String describeTypes(GTRecipeType[] types) {
        if (types == null || types.length == 0) {
            return "（无）";
        }
        StringBuilder sb = new StringBuilder();
        for (GTRecipeType type : types) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(type == null || type.registryName == null ? "?" : type.registryName.toString());
        }
        return sb.toString();
    }
}
