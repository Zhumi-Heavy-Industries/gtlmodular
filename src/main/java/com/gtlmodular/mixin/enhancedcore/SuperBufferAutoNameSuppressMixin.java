package com.gtlmodular.mixin.enhancedcore;

import com.gtlmodular.common.machine.LazyPatternAssemblyPartMachine;
import com.gtlmodular.common.machine.MoltenPatternAssemblyPartMachine;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自适配补丁（0.3.94，0.3.105 审查修正）：<b>拦写入动作</b> —— 让 GTL-Enhancedcore 的自动命名
 * <b>不要作用在我们自己的样板总成上</b>（不再把我们的机器名写成兜底串「未命名加工模式」）。
 *
 * <h2>上游真实类:方法（逐字，作者源码 {@code _census\enhancedcore-src}）</h2>
 * <ol>
 *   <li><b>写入动作本身</b>（本补丁拦的就是这一条）：
 *       {@code MEPatternBufferPartMachineBase.setCustomName(java.lang.String)} ——
 *       字节码实证（{@code _census\_javap_simple_gtlcore_bufferbase.txt:838-843}）：
 *       <pre>
 *   public void setCustomName(java.lang.String);
 *     Code:
 *        0: aload_0
 *        1: aload_1
 *        2: putfield      #79  // Field customName:Ljava/lang/String;
 *        5: return
 *       </pre>
 *       这是全链路上<b>唯一真正落地名字</b>的一句（{@code putfield customName}）；
 *       {@code MEPatternBufferPartMachine}（我们直接继承的具体类）没有覆写它，
 *       我们两个部件类也都没覆写 ⇒ 一定走这里。
 *       <br>⚠ <b>注意：{@code public void} ⇒ 非 static</b> ⇒ 注入处理器<b>必须也是实例方法</b>。</li>
 *   <li><b>上游是谁调它、调成什么</b>（读，不改）：
 *       {@code com.gtl.enhancedcore.mixin.gtlcore.SuperBufferNamePersistenceMixin:37-52}
 *       <pre>
 * :37  public void enhanced$setAutomaticName(String name) {
 * :39      if (self.isRemote() || enhanced$manualName || !IvBuffers.compatible(self)) return;
 * :41      String chinese = SuperBufferChineseNames.resolve(name);
 * :42      enhanced$writingAutomaticName = true;
 * :44          self.setCustomName(chinese);          // ★ 就是这一句把我们写成兜底串
 * :45          enhanced$automaticName = chinese;
 * :49      self.markDirty();
 *       </pre>
 *       入口 {@code SuperBufferNaming.applyNames(...)}（{@code ...\iv\SuperBufferNaming.java:14-27}，
 *       {@code :22} 判 {@code shouldWrite}、{@code :24} 调 {@code names.enhanced$setAutomaticName(targetKey)}）。
 *       兜底串出处：{@code SuperBufferChineseNames.java:13}
 *       {@code private static final String FALLBACK = "\u672a\u547d\u540d\u52a0\u5de5\u6a21\u5f0f";}
 *       （源码里就是 Unicode 转义，所以用中文字面量 grep 搜不到），
 *       {@code :40} 的加载白名单只扫 5 个命名空间（不含 gtlmodular）⇒ 我们的键永远查不到 ⇒ 必然落到兜底串。</li>
 * </ol>
 *
 * <h2>0.3.105 审查修正（三处）</h2>
 * <ol>
 *   <li><b>静态字段可见性</b>：{@code ENABLED} / {@code DIAGNOSTIC_LOG} 原为 {@code public static}
 *       ⇒ 触发 {@code InvalidMixinException: contains non-private static field} ⇒ 整包混入失败。
 *       已全部改 {@code private static}（全工程检索无外部引用 ⇒ 不影响编译）。</li>
 *   <li><b>调用栈判据原先是错的（即使织入成功也永不生效）</b>：原判据要求在栈里找到
 *       {@code com.gtl.enhancedcore.common.recipe.iv.SuperBufferNaming.applyNames}。
 *       但按上游源码，{@code setCustomName} 的调用者是
 *       {@code SuperBufferNamePersistenceMixin.enhanced$setAutomaticName:44}，
 *       {@code applyNames} <b>不在调用栈上</b> ⇒ 判据恒为 false ⇒ 补丁空转。
 *       已改为「栈里出现<b>任意</b> {@code com.gtl.enhancedcore.} 帧」，覆盖
 *       {@code applyNames} 与 {@code enhanced$setAutomaticName} 两条路径
 *       （自动命名只可能从 enhancedcore 发起 ⇒ 精确且无漏判；玩家在界面改名
 *       走我们自己的 {@code setOnConfirm}，栈里没有 enhancedcore 帧 ⇒ 放行）。</li>
 *   <li><b>判据覆盖面</b>：原判据
 *       {@code instanceof MoltenPatternAssemblyPartMachine || instanceof LazyPatternAssemblyPartMachine}
 *       是对的 —— <b>必须两个都写</b>，因为
 *       {@code LazyPatternAssemblyPartMachine extends MEPatternBufferPartMachineBase}
 *       （{@code LazyPatternAssemblyPartMachine.java:229}）<b>不是</b>
 *       {@code MoltenPatternAssemblyPartMachine} 的子类。本次保留并加注释说明理由。</li>
 *   <li><b>性能</b>：取栈（{@code new Throwable()}）是热路径成本，而 {@code setCustomName}
 *       在玩家改名时也会被调到 ⇒ 现在先做「同值早退」：上游 {@code shouldWrite} 只要会在
 *       {@code current == targetName} 时返回 false，因此「新旧值相同」的调用一定不是自动命名，
 *       直接返回、不取栈。</li>
 * </ol>
 *
 * <h2>影响面</h2>
 * <p>两道判据同时成立才拦：① 接收写入的对象是 {@link MoltenPatternAssemblyPartMachine} 或
 * {@link LazyPatternAssemblyPartMachine}；② 调用栈来自 {@code com.gtl.enhancedcore.}。
 * 其它模组/上游自身的机器一律不碰。
 *
 * <h2>开关与回退</h2>
 * <p>{@link #ENABLED}（默认 {@code true}）置 {@code false} ⇒ 本补丁立即完全失效，
 * 上游行为逐字恢复（无需重启存档，下一次写入即生效）。
 * {@link #DIAGNOSTIC_LOG}（默认 {@code false}）只加日志、不改判据。
 */
@Mixin(targets = "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase", remap = false)
public class SuperBufferAutoNameSuppressMixin {

    /** 总开关（默认开）。{@code private} 是 Mixin 的硬性要求。 */
    private static boolean ENABLED = true;

    /** 诊断开关（默认关）：为 true 时把每一次判读结果打进日志（只加日志，不改判据）。 */
    private static boolean DIAGNOSTIC_LOG = false;

    /** 诊断日志条数上限（防止自动命名刷屏）。 */
    private static int gtlmodular$diagBudget = 40;

    /** 极简重入守卫：避免日志/异常处理本身再次触发本注入。 */
    private static final ThreadLocal<Boolean> GTLMODULAR_SUPPRESS_GUARD =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 注入 {@code MEPatternBufferPartMachineBase.setCustomName(String)} 的 <b>HEAD</b>：
     * 命中「上游自动命名写进我们自己的机器」时 {@code ci.cancel()} ⇒ 这次赋值整体不发生。
     *
     * <p>处理器为<b>实例方法</b>，与目标 {@code public void setCustomName(String)} 一致
     * （Mixin 会比对 {@code ACC_STATIC}；写成 static 会抛
     * {@code STATIC modifier of @Inject method ... does not match the target}）。
     */
    @Inject(method = "setCustomName", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void gtlmodular$suppressUpstreamAutoName(String name, CallbackInfo ci) {
        try {
            if (!ENABLED || GTLMODULAR_SUPPRESS_GUARD.get()) {
                return;
            }
            // 判据①：接收写入的对象必须是我们自己的部件类
            // （两个都判：Lazy 直接继承 MEPatternBufferPartMachineBase，不是 Molten 的子类）
            if (!(((Object) this) instanceof MoltenPatternAssemblyPartMachine)
                    && !(((Object) this) instanceof LazyPatternAssemblyPartMachine)) {
                return;
            }
            // 同值早退：上游 SuperBufferAutoName.shouldWrite 在 current == targetName 时返回 false，
            // 所以"新旧值一样"的这一次写入一定不是自动命名（多数玩家改名会命中这里）⇒ 不必取栈。
            String current = ((MEPatternBufferPartMachineBase) (Object) this).getCustomName();
            if (current != null && current.equals(name)) {
                return;
            }
            // 判据②：调用栈里必须真有 enhancedcore 的命名帧（玩家改名没有 ⇒ 放行）
            boolean fromUpstreamAutoNaming = gtlmodular$isUpstreamAutoNaming();
            if (DIAGNOSTIC_LOG) {
                gtlmodular$log("setCustomName 判读：类型={} 值={} 来自上游自动命名={}",
                        ((Object) this).getClass().getSimpleName(), name, fromUpstreamAutoNaming);
            }
            if (!fromUpstreamAutoNaming) {
                return;
            }
            gtlmodular$log("已拦下 Enhancedcore 自动命名对我们的写入：{} 的名字不会变成「{}」",
                    ((Object) this).getClass().getSimpleName(), name);
            ci.cancel();
        } catch (Throwable ignored) {
            // 任何异常都不拦 ⇒ 上游照常执行
        }
    }

    /**
     * 调用栈里是否存在 enhancedcore 的命名帧。
     *
     * <p>判据用「包前缀 {@code com.gtl.enhancedcore.}」而不是某个具体方法名：
     * 上游的入口是 {@code SuperBufferNaming.applyNames}，但真正调 {@code setCustomName} 的是
     * {@code SuperBufferNamePersistenceMixin.enhanced$setAutomaticName:44} —— 只匹配前者会恒为 false
     * （这正是本次修正的 bug）。自动命名只可能由 enhancedcore 发起，因此包前缀判据既够精也不会漏。
     *
     * <p>只在被拦的那一刻（每台机最多几次）取一次栈，不是热路径；取栈失败就返回 false（不拦）。
     */
    private static boolean gtlmodular$isUpstreamAutoNaming() {
        GTLMODULAR_SUPPRESS_GUARD.set(Boolean.TRUE);
        try {
            StackTraceElement[] frames = new Throwable().getStackTrace();
            for (StackTraceElement frame : frames) {
                String cls = frame.getClassName();
                if (cls != null && cls.startsWith("com.gtl.enhancedcore.")) {
                    return true;
                }
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        } finally {
            GTLMODULAR_SUPPRESS_GUARD.set(Boolean.FALSE);
        }
    }

    /** 有界日志（异常全吞，绝不影响主流程）。 */
    private static void gtlmodular$log(String format, Object... args) {
        try {
            if (gtlmodular$diagBudget <= 0) {
                return;
            }
            gtlmodular$diagBudget--;
            com.gtlmodular.GTLModular.LOGGER.info("[gtlmodular] " + format, args);
        } catch (Throwable ignored) {
        }
    }
}
