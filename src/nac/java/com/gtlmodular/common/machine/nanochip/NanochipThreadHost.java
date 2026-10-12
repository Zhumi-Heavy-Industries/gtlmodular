package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

/**
 * ★★ 2026-10-28【独立跨配方线程】<b>「独立线程逻辑」需要机器回答的四件事</b>（纯只读契约，零 mixin、零存档）★★
 *
 * <h3>为什么要有这个接口（而不是让新逻辑直接读 {@code NanochipAssemblyComplexMachine}）</h3>
 * 本轮的纪律是「<b>按上游作者路线：先功能本体，能用覆写/继承就零混入</b>」，且新逻辑要能被单独看懂、
 * 单独验证。新逻辑只需要机器告诉它四件事：
 * <ol>
 *   <li><b>最多几条线程</b> —— 沿用本机<b>已有</b>的线程数阶梯
 *       （{@code getMultipleThreads()} = 电压基线 + Ω-天球分歧引擎追加，夹 {@code 256}）
 *       ⇒ <b>不新增方块、不改结构、不动存档</b>（这一点与本轮任务书「先只落地代码、不动结构」一致）；</li>
 *   <li><b>本轮要搜哪些配方类型</b> —— 这是「跨配方类型」的<b>唯一取数口</b>
 *       （简单模式下 = 电路组装表 + 换算表）；</li>
 *   <li><b>一条候选能不能用</b> —— 候选校验是只读试算，机器侧才能拿到能力代理；</li>
 *   <li><b>轮转游标</b> —— 公平轮转的可变位（见 {@link FairCandidateSelector}）。</li>
 * </ol>
 *
 * <p>换句话说：本接口把「机器侧的四件事」与「派活算法」切开，于是派活算法（
 * {@link NanochipThreadedRecipeLogic}）不需要 {@code instanceof} 任何具体机器类，
 * 也不需要混入任何东西。
 *
 * <h3>与 GTO 公开契约的对应关系（逐条）</h3>
 * <table border="1">
 *   <tr><th>GTO（{@code ICrossRecipeMachine}）</th><th>本接口</th></tr>
 *   <tr><td>{@code getThread(): int}</td><td>{@link #nanochipThreadCount()}</td></tr>
 *   <tr><td>{@code getThreads(): List<ICrossRecipeMachine$Thread>}</td>
 *       <td>不放在这里 —— 线程表属于<b>逻辑</b>（{@link NanochipThreadedRecipeLogic} 自己的
 *           {@code threads}），与 GTO 把 {@code threads} 放在 {@code $Logic} 上同构</td></tr>
 *   <tr><td>配方候选来源</td><td>{@link #nanochipSearchTypes()}（GTO 的同名能力由它自己的
 *       {@code getLastRecipes()} / 配方表声明决定）</td></tr>
 * </table>
 *
 * <h3>纪律</h3>
 * <ol>
 *   <li><b>全部只读</b>：除 {@link #nanochipThreadCursors()}（那是一个<b>调用方持有</b>的可变游标位）
 *       之外，任何实现<b>都不许</b>有副作用；</li>
 *   <li><b>零存档影响</b>：本接口不是字段、不进 NBT、不改任何存档格式；</li>
 *   <li><b>零 mixin</b>：调用侧全是「子类实现 + 覆写点」，不新增/不启用任何 mixin；</li>
 *   <li><b>fail-safe</b>：任意方法抛异常时，调用侧一律按「本轮不开工」处理，
 *       <b>绝不</b>把异常抛进 tick（见 {@link NanochipThreadedRecipeLogic#serverTick()} 外的兜底）。</li>
 * </ol>
 *
 * <h3>回退（一档，逐字）</h3>
 * 删掉 {@code NanochipAssemblyComplexMachine} 类声明上的 {@code implements ... NanochipThreadHost}
 * 与那四个 {@code @Override}，并把 {@code createRecipeLogic} 的开关常量置 {@code false}
 * ⇒ 逐字回到本功能之前（本文件与新逻辑类留着无害，因为没人再引用它们）。
 */
public interface NanochipThreadHost {

    /**
     * 本机这一刻<b>最多能同时挂起几条独立线程</b>（= 上限，不是保证值）。
     *
     * <p>必须 ≥ 1；返回 {@code <= 0} 时调用侧按 1 处理（不退化成「不开工」）。
     */
    int nanochipThreadCount();

    /**
     * <b>本轮要搜哪些配方类型</b> —— 「跨配方类型」的唯一取数口。
     *
     * <p>返回 {@code getRecipeTypes()} 的当前快照即可（简单模式下 = 电路组装表 + 换算表）。
     * 调用侧对 {@code null} / 空数组 / 含 {@code null} 位都做了保守处理 ⇒ 实现方不必自己防。
     */
    GTRecipeType[] nanochipSearchTypes();

    /**
     * 这条候选配方<b>这一刻能不能用</b>（输入放得下 && 产出放得下 && 条件满足）。
     *
     * <p><b>只读试算，绝不许扣料</b>：它是搜索结果的前置校验，一个 tick 里会被问很多次
     * （每个候选一次、每次搜索一次）。真正的扣料只发生在
     * {@link NanochipThreadedRecipeLogic} 的 {@code commit} 那一步。
     */
    boolean nanochipCandidateUsable(GTRecipe candidate);

    /**
     * 公平轮转的游标位（长度 ≥ 1 的<b>可变</b>数组；{@code [0]} = 上次选中的最后一条配方 id）。
     *
     * <p>为什么让机器持有而不是逻辑自己持有：同一台机器的两条配方逻辑（新旧）共享同一个轮转位置，
     * 开关切换时不会让「常备配方霸占线程」重来一次；它也<b>只是运行期状态</b>
     * （{@code transient}、不进 NBT、不改存档格式）。
     *
     * <p>不许返回 {@code null}：返回长度 0 的数组时调用侧退化为「每轮都从第一条开始」。
     */
    String[] nanochipThreadCursors();
}
