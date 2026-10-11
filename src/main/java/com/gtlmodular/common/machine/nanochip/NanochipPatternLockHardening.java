package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import java.util.concurrent.locks.Lock;

/**
 * <b>结构匹配锁的异常安全包装（照抄「增强版」的做法，纯代码、不含 mixin、不含 @Overwrite）</b>
 *
 * <h3>抄的是哪一条（出处）</h3>
 * 「增强版」GTL-Enhancedcore 的
 * {@code com.gtl.enhancedcore.mixin.gtceu.MultiblockControllerLockFixMixin}
 * （本地源码 {@code _census/enhancedcore-src/src/main/java/com/gtl/enhancedcore/mixin/gtceu/MultiblockControllerLockFixMixin.java:12-52}）
 * 用 {@code @Mixin(value = MultiblockControllerMachine.class, priority = 1100)} 覆写
 * {@code checkPatternWithLock()} / {@code checkPatternWithTryLock()}，把
 * <pre>
 *   lock.lock();                 // 或 lock.tryLock()
 *   matched = checkPattern();
 *   lock.unlock();               // ← 裸调用，异常时永不执行
 * </pre>
 * 改成 {@code try { ... } finally { lock.unlock(); }}（它的字节码异常表
 * {@code from 3 to 73 target 85 type any} 已由 {@code _增强版性能优化_研究报告.md:910-917} 取证）。
 * 它自己文档里写明这条修的就是漏锁死锁：
 * {@code _census/enhancedcore-src/docs/全面审查-2.6.4-20260925.md:95-97}
 * 「原接口锁修复被 GTLCore 新增的控制器具体方法遮蔽。<b>异常注入实测确认仍会漏锁。</b>」
 *
 * <h3>为什么我们这边确实有这个洞（本机字节码逐行取证）</h3>
 * 部署 jar {@code gtlcore-1.2.3.2-fix2} 的
 * {@code org.gtlcore.gtlcore.mixin.gtm.api.machine.MultiblockControllerMachineMixin}
 * 用 {@code @Overwrite(priority = 1000)} 覆写了这两个方法，<b>但它的解锁是裸调用</b>：
 * <ul>
 *   <li>{@code checkPatternWithLock()}：{@code _p1_evidence/gtlcore_MCMMixin.txt:266} 是
 *       {@code invokeinterface Lock.lock()}，{@code :277} 是 {@code invokeinterface Lock.unlock()}，
 *       而 {@code :303-306} 的异常表只覆盖 {@code try-with-resources} 的
 *       {@code WorldPatternTiming.close()}（{@code from 7 to 66 → 77}），
 *       <b>没有任何一条把 {@code checkPattern()} 包进去</b> ⇒
 *       {@code checkPattern()} 抛异常 = 锁永久泄漏；</li>
 *   <li>{@code checkPatternWithTryLock()}：{@code :371} 是 {@code Lock.tryLock()}、
 *       {@code :385} 是 {@code unlock()}，异常表 {@code :424-428} 同样只覆盖 timing。</li>
 * </ul>
 * 泄漏的后果就是本工程实测过的形状：服务端线程在
 * {@code MultiblockState.onBlockStateChanged → checkPatternWithLock() → lock.lock()} 上
 * <b>等到天荒地老</b>（3/3 线程转储同形、watchdog {@code 40001 / 85752 / 132410 ms}，
 * 见 {@code _census/多方块检测性能优化_队员.md:97-113}；闸类注释里也记了同一次：
 * {@link NanochipStructureCheckThrottle}）。
 * 异常源不是假想：gtlcore 的 {@code BlockPatternMixin} 会把 matchContext 里的
 * {@code "parts"} 强转成 {@code ObjectOpenHashSet}，而 GTCEu 1.4.4 的
 * {@code onStructureFormed} 建的是 {@code Collections::emptySet}
 * （「增强版」被这条打中过，见 {@code _census/enhancedcore-src/docs/history/20260917-before-refactor/已知问题.md:175-176}）。
 *
 * <h3>为什么不加 mixin（本轮的硬约束）</h3>
 * 「增强版」用 mixin 是为了<b>对全包所有控制器</b>生效；我们这一轮明令「零混入」，
 * 所以改成<b>在 NAC 自己的子类里覆写</b>（虚分派选子类，{@code super.*} 落到的仍是 gtlcore 版）。
 * 覆盖面因此只有<b>我们自己的机器</b>——这正是本任务要的范围（NAC 主机 → 模块 → 地心系列）。
 *
 * <h3>零语义变更（为什么不会让结构误判成型）</h3>
 * <ol>
 *   <li><b>没有一行判定式</b>：本类不读世界、不查方块、不做任何预检，
 *       {@code matched} 永远等于 {@code machine.checkPatternWithLock()} /
 *       {@code machine.checkPatternWithTryLock()} 的<b>真实返回值</b>。没有常量 {@code true}，
 *       没有任何「跳过检测」的分支。</li>
 *   <li><b>正常路径逐字节等价</b>：不抛异常时，执行的调用序列与 gtlcore 完全相同
 *       （lock → checkPattern → unlock），只是 unlock 被搬进 {@code finally}。</li>
 *   <li><b>唯一的差别在异常路径</b>：gtlcore 原来「锁不还」，现在「锁还掉」。
 *       「锁还掉」不会让任何格子的判定结果变化——它只是把后续的调用从
 *       「永远阻塞」变成「能继续走到 gtlcore 自己的判定」。</li>
 *   <li><b>异常仍然如实上抛</b>：{@code finally} 先执行、随后原异常继续往外抛，
 *       与 {@code try-with-resources} 的语义一致；本类**不 catch 任何异常**、
 *       也不把异常改写成返回值（与增强版 {@code MultiblockControllerLockFixMixin} 逐字同形）。</li>
 * </ol>
 */
public final class NanochipPatternLockHardening {

    private NanochipPatternLockHardening() {
    }

    /**
     * {@code checkPatternWithLock()} 的异常安全包装（阻塞锁）。
     *
     * @param machine 调用方（NAC 主机 / 模块）；本方法直接调它的 {@code getPatternLock()} 与
     *                {@code checkPattern()}，<b>不</b>调 {@code checkPatternWithLock()}
     *                ⇒ 不存在重入、也不会与调用方自己的覆写形成环
     * @param lockFixEnabled 该机器的独立开关；{@code false} ⇒ 逐字回退到原 {@code super}
     * @return 与 {@code machine.checkPatternWithLock()} 语义完全一致（见类注释「零语义变更」）
     */
    public static boolean withLock(MultiblockControllerMachine machine, boolean lockFixEnabled) {
        if (!lockFixEnabled) {
            return machine.checkPatternWithLock();          // ★ 一行回退：原样走 gtlcore 的做法
        }
        final Lock lock = machine.getPatternLock();
        lock.lock();
        try {
            return machine.checkPattern();                  // 与 gtlcore 同序：唯一的一处判定
        } finally {
            // ★★ 本类存在的全部理由就是这一句：**锁一定要还**。
            //    gtlcore 在这一支上把锁永久留给了下一个调用者 —— 那是"永久停摆"，
            //    而不是"这一轮失败"（异常本身照原样往外抛，判定结果一字未改）。
            lock.unlock();
        }
    }

    /**
     * {@code checkPatternWithTryLock()} 的异常安全包装（非阻塞锁）。
     *
     * <p>与 {@link #withLock} 的两点差别，<b>逐字对齐 gtlcore 自己的取舍</b>
     * （{@code _p1_evidence/gtlcore_MCMMixin.txt:352-411}）：
     * <ol>
     *   <li>用 {@code tryLock()}：拿不到锁（异步检查线程正在跑匹配）⇒ 直接 {@code false}，
     *       <b>绝不阻塞调用线程</b>；</li>
     *   <li>拿不到锁时<b>不抛</b>、也不跑判定 —— 「这次没拿到」与「这次不成形」在 GTCEu 眼里
     *       是同一个返回值 {@code false}，下一轮会自然重试。</li>
     * </ol>
     */
    public static boolean withTryLock(MultiblockControllerMachine machine, boolean lockFixEnabled) {
        if (!lockFixEnabled) {
            return machine.checkPatternWithTryLock();       // ★ 一行回退
        }
        final Lock lock = machine.getPatternLock();
        if (!lock.tryLock()) {
            return false;                                   // 与 gtlcore 同：非阻塞、不抛、不判定
        }
        try {
            return machine.checkPattern();
        } finally {
            lock.unlock();                                  // ★★ 同上：异常路径也必须还锁
        }
    }
}
