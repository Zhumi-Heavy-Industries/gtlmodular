package com.gtlmodular.mixin.enhancedcore;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.machine.LazyPatternAssemblyPartMachine;
import com.gtlmodular.common.machine.MoltenPatternAssemblyPartMachine;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 自适配补丁（0.3.92，0.3.105 审查修正）：让 GTL-Enhancedcore 的
 * {@code IvBuffers.isolated(仓)} <b>对我们的样板仓返回 false</b>。
 *
 * <h2>为什么要这条</h2>
 * <p>依据作者源码（{@code _census\enhancedcore-src} 逐字核对）：
 * <pre>
 * IvBuffers.isolated(MEPatternBufferPartMachineBase buffer) {   // :43
 *     IvBufferState state = state(buffer);
 *     if (state == null) return false;                 // 没有 IV 状态 ⇒ 不算隔离 ⇒ 不过滤
 *     if (buffer.isRemote() &amp;&amp; ((IvBufferAccess)buffer).iv$isDedicatedDisplay()) return true;  // :46
 *     boolean ivController = buffer.getControllers().stream().anyMatch(IvBuffers::targetController);
 *     return state.dedicated() || ivController;        // :48
 * }
 * IvPatternBufferMixin.iv$publishAllowedTypes(...) {   // 注入在 getAvailablePatterns 的 RETURN
 *     if (!IvBuffers.isolated(buffer)) return;         // 不隔离就直接放行
 *     ... 只保留带 ROUTE_KEY 且类型匹配的样板 ...
 * }
 * </pre>
 * 我们第三方仓的样板<b>没有</b> {@code ROUTE_KEY} 标记 ⇒ 一律被删 ⇒ AE 看不到可合成选项
 * （用户现象：单独放正常，装进巨型组装厂就看不到）。
 * <p>本补丁让 {@code isolated} 对我们返回 false ⇒ 上游那道过滤整体早退 ⇒ 样板原样交给 AE。
 *
 * <h2>0.3.105 审查修正（三处，其中第一、二处都会直接崩游戏）</h2>
 * <ol>
 *   <li><b>静态字段可见性</b>：{@code BYPASS_ENABLED} 原为 {@code public static} ⇒
 *       {@code MixinPreProcessorStandard.validateField} 抛
 *       {@code InvalidMixinException: Mixin ...:enhancedcore.IvBuffersIsolatedMixin contains
 *       non-private static field BYPASS_ENABLED}（<b>就是用户日志里那一行</b>）
 *       ⇒ 整包混入失败、游戏起不来。已改为 {@code private static}。</li>
 *   <li><b>注入处理器签名（会撞 "handler descriptor mismatch"）</b>：原处理器形参写的是
 *       {@code Object buffer}，而目标方法签名是
 *       {@code isolated(MEPatternBufferPartMachineBase)} ⇒ 处理器描述符
 *       {@code (Ljava/lang/Object;)V} 与目标 {@code (...MEPatternBufferPartMachineBase;)V} 不一致。
 *       Mixin 生成注入器时会报 {@code InvalidInjectionException: ... handler descriptor mismatch}，
 *       在 {@code required:true} 的配置下同样是启动失败。已改为形参类型与目标完全一致
 *       {@link MEPatternBufferPartMachineBase}（取证：{@code IvBuffers.java:43}）。</li>
 *   <li><b>判据覆盖面</b>：原判据只有 {@code instanceof MoltenPatternAssemblyPartMachine}，
 *       而 <b>{@code LazyPatternAssemblyPartMachine} 直接继承
 *       {@code MEPatternBufferPartMachineBase}</b>（{@code LazyPatternAssemblyPartMachine.java:229}），
 *       <b>不是</b> {@code MoltenPatternAssemblyPartMachine} 的子类 ⇒ 惰性机<b>漏判</b>。
 *       已补上第二个 {@code instanceof}。</li>
 * </ol>
 *
 * <h2>影响面（比补丁注释原本写的更大，务必知情）</h2>
 * <ul>
 *   <li>只对<b>我们两个部件类</b>生效，其它模组/上游机器一律走原逻辑。</li>
 *   <li>但：我们机器一旦接在<b>上游原生 IV 控制器</b>下（{@code targetController} 为真），
 *       上游此后就<b>不再</b>给它做「样板类型白名单 / 去重」这一层保护。副作用是
 *       {@code IvBuffers.sharedCatalysts(buffer)}（{@code :126} 里 {@code if (!isolated) return result;}）
 *       会退化成空 Map ⇒ 该仓在原生控制器下的<b>共享催化剂不再被计入</b>。
 *       这是"让 AE 看得见"换来的代价，属于本补丁的<b>已知取舍</b>，不是 bug；
 *       若要彻底规避，应改成"只对 dedicated 仓返回 false"（需要上游 {@code IvBufferState} API，
 *       本轮未取证，见报告第 ⑦ 节）。</li>
 * </ul>
 *
 * <h2>回退</h2>
 * <p>{@link #BYPASS_ENABLED} 置 {@code false} ⇒ 上游行为逐字恢复。异常全吞 ⇒ 最坏情况只是不生效。
 */
@Mixin(targets = "com.gtl.enhancedcore.common.recipe.iv.IvBuffers", remap = false)
public class IvBuffersIsolatedMixin {

    /**
     * 总开关（默认开）。{@code private} 是 Mixin 的硬性要求；关掉即恢复上游原始行为。
     */
    private static boolean BYPASS_ENABLED = true;

    /**
     * 目标签名核对依据：{@code IvBuffers.java:43}
     * {@code public static boolean isolated(MEPatternBufferPartMachineBase buffer)}。
     */
    @Inject(method = "isolated", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void gtlmodular$notIsolatedForOurBuffer(MEPatternBufferPartMachineBase buffer,
                                                          CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!BYPASS_ENABLED || buffer == null) {
                return;
            }
            if (buffer instanceof MoltenPatternAssemblyPartMachine
                    || buffer instanceof LazyPatternAssemblyPartMachine) {
                GTLModular.LOGGER.info(
                        "[gtlmodular] 自适配补丁生效：IvBuffers.isolated({}) ⇒ false（跳过类型白名单过滤）",
                        buffer.getClass().getSimpleName());
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {
            // 出问题就让上游照常执行
        }
    }
}
