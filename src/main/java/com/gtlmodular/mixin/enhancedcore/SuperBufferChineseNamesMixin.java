package com.gtlmodular.mixin.enhancedcore;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * 自适配补丁（0.3.93，0.3.105 审查修正）：修「未命名加工模式」。
 *
 * <p>依据作者源码（{@code _census\enhancedcore-src} ✓ 逐字核对）：
 * {@code SuperBufferChineseNames} 在 {@code :40} 只扫 5 个固定命名空间
 * （gtceu / gtmthings / gtlcore / gtladditions / gtl_enhancedcore）⇒ 我们
 * {@code gtlmodular} 的 lang 键永远查不到 ⇒ 命中 {@code :13} 的硬编码兜底
 * {@code \u672a\u547d\u540d\u52a0\u5de5\u6a21\u5f0f}（"未命名加工模式"）。
 *
 * <p>本补丁在 {@code resolve(...)} 最前面按类型 <b>path</b> 命中则直接返回中文
 * —— 按上游自己的注释要求，这里<b>用硬编码中文表</b>，不走 Minecraft 当前语言。
 *
 * <p>只影响<b>我们的</b>类型名；其它命名空间一律原样交回上游。
 *
 * <h2>0.3.105 审查修正（三处）</h2>
 * <ol>
 *   <li><b>静态字段可见性</b>：{@code ENABLED} 原为 {@code public static} ⇒ Mixin
 *       {@code MixinPreProcessorStandard.validateField} 直接抛
 *       {@code InvalidMixinException: contains non-private static field} ⇒ 整包混入失败、游戏起不来。
 *       已改为 {@code private static}。经全工程检索（{@code gtl-modular\src} 内无任何
 *       非混入类引用本开关）⇒ 改 private <b>不影响编译</b>，因此<b>不需要</b>额外开关宿主类。</li>
 *   <li><b>默认值</b>：原为 {@code false}（补丁自带关闭）= 即使织入成功也什么都不做 ⇒
 *       与"修未命名"的目标相反。已改为 {@code true}。</li>
 *   <li><b>静态初始化块</b>：删除。原 {@code static { LOGGER.info(...) } } 在织入后是与
 *       目标类 {@code <clinit>} 合并的（Mixin 支持），但会往上游类的初始化里塞我们模组的类加载，
 *       且日志时机早于配置就绪。日志改由命中时打一条（见下）。</li>
 * </ol>
 *
 * <h2>风险与回退</h2>
 * <p>{@link #ENABLED} 置 {@code false} ⇒ 立刻完全恢复上游行为（连兜底串都与今天逐字一致）。
 * 整段包在 {@code try/catch(Throwable)} 里：任何异常都交回上游，绝不因为本条补丁让游戏崩。
 */
@Mixin(targets = "com.gtl.enhancedcore.common.recipe.iv.SuperBufferChineseNames", remap = false)
public class SuperBufferChineseNamesMixin {

    /**
     * 总开关（默认开）。{@code private} 是 Mixin 的硬性要求
     * （混入类里所有非 {@code private} 的 static 字段都会让整包混入失败）。
     */
    private static boolean ENABLED = true;

    /** 诊断日志条数上限（有界，避免刷屏）。 */
    private static int gtlmodular$diagBudget = 24;

    /** 我们自己的配方类型 path → 中文名（硬编码 ⇒ 与客户端语言无关）。 */
    private static final Map<String, String> GTLMODULAR_NAMES = Map.ofEntries(
            Map.entry("nanochip_assembly_complex", "纳米芯片组装复合体"),
            Map.entry("nanochip_conversion", "纳米芯片换算"),
            Map.entry("nanochip_assembly_matrix", "纳米芯片组装矩阵"),
            Map.entry("nanochip_cutting_chamber", "纳米精度切割仓"),
            Map.entry("nanochip_wire_tracer", "纳米精度导线追踪器"),
            Map.entry("nanochip_encasement_wrapper", "纳米封装卷绕器"),
            Map.entry("nanochip_splitter", "纳米组件分流器"),
            Map.entry("nanochip_smd_processor", "元件预处理装置"),
            Map.entry("disposable_tool_printing", "3D打印：一次性工具")
    );

    /**
     * 目标签名核对依据：{@code SuperBufferChineseNames.java:21}
     * {@code public static String resolve(String keyOrName)} ⇒ 处理器必须 {@code static}、
     * 参数 {@code (String)}、{@code CallbackInfoReturnable<String>}。
     */
    @Inject(method = "resolve", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void gtlmodular$resolveOwnTypeNames(String keyOrName, CallbackInfoReturnable<String> cir) {
        try {
            if (!ENABLED || keyOrName == null || keyOrName.isEmpty()) {
                return;
            }
            String k = keyOrName.toLowerCase(java.util.Locale.ROOT);
            for (Map.Entry<String, String> e : GTLMODULAR_NAMES.entrySet()) {
                if (k.contains(e.getKey())) {              // 键里含类型 path ⇒ 命中
                    if (gtlmodular$diagBudget > 0) {
                        gtlmodular$diagBudget--;
                        try {
                            com.gtlmodular.GTLModular.LOGGER.info(
                                    "[gtlmodular] 命名适配命中：{} ⇒ {}", keyOrName, e.getValue());
                        } catch (Throwable ignored) {
                            // 日志失败不影响返回值
                        }
                    }
                    cir.setReturnValue(e.getValue());
                    return;
                }
            }
        } catch (Throwable ignored) {
            // 任何异常都交回上游
        }
    }
}
