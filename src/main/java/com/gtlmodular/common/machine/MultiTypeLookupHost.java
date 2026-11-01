package com.gtlmodular.common.machine;

/**
 * ★ 2026-10-27【「查找侧收窄」×「复合型机器（多类型搜索）」的语义闸 —— 纯标记接口，零 mixin、零存档】
 *
 * <h3>它存在的唯一理由</h3>
 * 查找侧收窄（{@link MoltenPatternAssemblyPartMachine#LOOKUP_SIDE_SLOT_NARROWING}、
 * {@link LazyPatternAssemblyPartMachine#LOOKUP_SIDE_SLOT_NARROWING}）把「参与本轮配方查找/匹配的槽」
 * 收成<b>一个</b>模式 —— 它的判据是控制器的 {@code getActiveRecipeType()}（<b>单值</b>）。
 *
 * <p>而复合型主机（{@code NanochipAssemblyComplexMachine}）在<b>同一轮</b>里会遍历
 * {@code machine.getRecipeTypes()} 声明的<b>全部</b>类型搜候选（{@code NanochipRecipeLogic.lookupRecipeIteratorInternal()}）
 * ⇒ 当它去搜「非激活类型」时，那个类型的<b>冷</b>样板槽（从未配上过配方、还没有
 * {@code recipes2SlotsMap} / {@code cacheRecipe} 快路径）已经被收窄摘出了查找面
 * ⇒ 冷槽只能走 {@code MEPatternRecipeHandlePart.handleRecipe} 的「遍历 {@code getActiveSlots()}」回退分支
 * ⇒ <b>永远拿不到第一次匹配 ⇒ 永远热不起来 ⇒ 该类冷样板永久饥饿</b> ✗
 * （热样板仍走快路径 / {@code getCachedGTRecipe} 追加，所以表象是「看似能跑两类、实际只吃一类」）。
 *
 * <p>⇒ <b>收窄的合法前提 = 「控制器这一轮只搜一个类型」</b>。本接口就是把这个前提<b>问出来</b>：
 * 控制器实现本接口并返回 {@code true} ⇒ 前提不成立 ⇒ 样板总成侧本轮<b>不收窄</b>（放行全量）。
 *
 * <h3>语义口径（必须按这一句理解，别理解成「按类型收窄」）</h3>
 * <blockquote><b>收窄面 = 本轮会被搜的类型集合</b>，而不是「单一激活模式」。</blockquote>
 * 单类型控制器上这两者是同一个集合（收窄面 == 全量面，收窄无害）；
 * 多类型控制器上「本轮会被搜的集合」就是全部类型 ⇒ 收窄面 == 全量面 ⇒ <b>本轮不该收窄</b>。
 * 所以本闸不是补丁，而是把收窄的语义修回它本来的口径 ✓。
 *
 * <h3>为什么用「我们自己的接口」而不是上游类型</h3>
 * 刻意<b>不</b>用 {@code instanceof GTLAddMultipleTypeRecipesLogic} /
 * {@code MutableRecipesLogic.isMultipleRecipeMode()}：
 * <ul>
 *   <li>后者的语义未取证（「多配方线程」还是「多类型」分不清）；</li>
 *   <li>会把我们绑死在 gtladditions 的内部类名上。</li>
 * </ul>
 * 自己模组的标记接口与既有先例同款（gtlcore 的 {@code IModularMachineHost}、
 * 本包的 {@code NanochipStructureRefresh.Refreshable}），且<b>只有一处实现、只有两处判据</b>。
 *
 * <h3>纪律</h3>
 * <ol>
 *   <li><b>纯只读</b>：实现方（控制器）<b>绝不许</b>有任何副作用、不许改状态、不许落盘；</li>
 *   <li><b>零存档影响</b>：本接口不是字段、不进 NBT、不改任何存档格式 ⇒ 加它/删它都不影响存档；</li>
 *   <li><b>零 mixin</b>：调用侧全是「子类 {@code instanceof} + 覆写点」，
 *       不新增/不启用任何 mixin，不动 {@code gtlmodular.mixins.json}；</li>
 *   <li><b>fail-open</b>：调用侧在拿不到控制器 / 判据抛异常时一律按「不是多类型」处理
 *       ⇒ 最多少放行一次，<b>绝不</b>因此把该跑的类型挡住 ✗。</li>
 * </ol>
 *
 * <h3>回退（纯代码层，逐档更狠）</h3>
 * <ol>
 *   <li><b>一档</b>：删掉 {@code NanochipAssemblyComplexMachine} 的 {@code implements MultiTypeLookupHost}
 *       与那个 {@code @Override} 方法 ⇒ 调用侧 {@code instanceof} 恒假 ⇒ 逐字回到加本闸之前（本文件留着无害）；</li>
 *   <li><b>二档</b>：两处 {@code LOOKUP_SIDE_SLOT_NARROWING} 同时置 {@code false} ⇒ 全局不收窄；</li>
 *   <li><b>三档</b>：删掉两个总成的 {@code getActiveSlots()} / {@code getActiveAndUnCachedSlots()} 覆写。</li>
 * </ol>
 */
public interface MultiTypeLookupHost {

    /**
     * 本控制器这一轮是否<b>不依赖</b> {@code getActiveRecipeType()}、会把<b>多个配方类型一起搜</b>？
     *
     * <p>{@code true} = 本轮会遍历全部（或至少不止激活那一个）类型搜候选
     * ⇒ 样板总成侧的「查找侧收窄」<b>本轮必须放行</b>（因为收窄面 = 本轮会被搜的类型集合 = 全部类型）。
     *
     * <p>{@code false} = 只搜激活的那一个类型 ⇒ 收窄的前提成立，收窄照常生效。
     *
     * <p><b>实现要求</b>：只读、幂等、无副作用、不许抛异常（调用侧虽有 fail-open 兜底，
     * 但异常会被当成 {@code false} 从而<b>恢复收窄</b> ⇒ 对该控制器就是故障回归 ✗）。
     *
     * @return true = 多类型搜索（收窄侧必须放行）；false = 单类型（收窄可照常生效）
     */
    boolean searchesAllRecipeTypes();
}
