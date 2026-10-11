package com.gtlmodular.mixin.enhancedcore;

import com.gtlmodular.GTLModular;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 自适配补丁（0.3.95，0.3.105 审查修正）：把 GTL-Enhancedcore 画在「超级猪咪样板总成」界面
 * 右上角的那<b>一行自动命名文字 + 旁边两个方块控件</b>整块藏掉（默认藏，一行开关可恢复）。
 *
 * <h2>① 那一行/那些方块到底是谁、在哪一步加进去的</h2>
 * <p>答案：<b>上游把我们界面里已有的控件「抽掉换成它自己的控件组」</b>。三跳链：
 * <ol>
 *   <li><b>上游注入点</b>（挂在 gtlcore 的真类上，所以我们的覆写也吃得到）：
 *       <pre>
 * com.gtl.enhancedcore.mixin.gtlcore.IvCompatibleBufferUiMixin:11-16
 * :11  &#64;Mixin(value = MEPatternBufferPartMachine.class, remap = false)
 * :13      &#64;Inject(method = "createUIWidget", at = &#64;At("RETURN"))
 * :15          IvBufferUi.installNameControls((MEPatternBufferPartMachine)(Object)this, cir.getReturnValue());
 *       </pre>
 *       我们的 {@code MoltenPatternAssemblyPartMachine extends MEPatternBufferPartMachine}
 *       且覆写了 {@code createUIWidget} ⇒ {@code @At("RETURN")} 会挂到<b>我们的</b>覆写上
 *       ⇒ 返回的正是我们那个 {@code WidgetGroup}。</li>
 *   <li><b>实际的「追加动作」</b>：
 *       <pre>
 * com.gtl.enhancedcore.common.recipe.iv.IvBufferUi:13-21
 * :14  if (!IvBuffers.compatible(buffer) || !(widget instanceof WidgetGroup group)) return;
 * :15  for (var field : group.getWidgetsByType(AETextInputButtonWidget.class)) {
 * :16      var position = field.getSelfPosition();
 * :17      group.removeWidget(field);                       // ★ 我们的输入框被摘掉
 * :18      group.addWidget(new SuperBufferNameWidget(position.x, position.y,
 * :19              field.getSizeWidth(), field.getSizeHeight(), buffer));   // ★ 就是这一句画上去
 * :20  }
 *       </pre>
 *       被摘掉的是 {@code MoltenPatternAssemblyPartMachine} 里那个
 *       {@code new AETextInputButtonWidget(...).setText(getCustomName())} —— <b>显示正确的那个</b>。</li>
 *   <li><b>画出来的那一行字 + 两个方块 = 上游 {@code SuperBufferNameWidget} 这个控件组</b>
 *       （{@code SuperBufferNameWidget.java:16-82}；:70-81 的 {@code restore} 钮用的是
 *       {@code GuiTextures.BUTTON_CLEAR_GRID} = 空白方格，看上去就是个没打勾的方框）。</li>
 * </ol>
 *
 * <h2>② 落点：拦「摘掉我们的」+「追加它的」两个动作，而不是「事后删」</h2>
 * <ol>
 *   <li>{@code removeWidget} 的 {@code HEAD} + {@code ci.cancel()} ⇒ 我们的输入框不离开容器
 *       （身份、父子关系、控件 id 全不变）；</li>
 *   <li>{@code addWidget} 的 {@code HEAD} + {@code cir.setReturnValue(group)} ⇒ 它的控件组整体
 *       不进入控件树 ⇒ 那一行文字 + 两个方块一起消失。</li>
 * </ol>
 * <p>相比「在 {@code createUIWidget} 的 RETURN 里事后删掉」：不依赖注入顺序（RETURN 注入点上
 * 上游与我们会抢同一个点），且两端在同一时刻掐掉同一个动作 ⇒ 两端控件树依然逐字一致，
 * 不会出现「服务端发 UI 更新包、客户端找不到控件」的错配。
 *
 * <h2>③ 只藏这一处</h2>
 * <p>两道判据都是「运行时类名<b>恰好等于</b>
 * {@code com.gtl.enhancedcore.common.gui.SuperBufferNameWidget}」<b>且</b>容器是 {@code WidgetGroup}
 * —— 这类控件在上游全工程只有 {@code IvBufferUi:17-18} 这一条路径会进出控件树
 * ⇒ 上传路由提示、AE 终端名、Jade 名、上游其它界面元素一律不碰；别的模组更不碰。
 *
 * <h2>④ 结果</h2>
 * <p>界面右上角只剩<b>我们自己</b>那个输入框（显示 {@code getCustomName()} = 世界里的正确名字），
 * 上游那一行兜底文字 + 两个方块控件消失。
 *
 * <h2>⑤ 0.3.105 审查修正</h2>
 * <ol>
 *   <li><b>静态字段可见性</b>：{@code HIDE_ENABLED} / {@code DIAGNOSTIC_LOG} 原为 {@code public static}
 *       ⇒ {@code InvalidMixinException: contains non-private static field} ⇒ 整包混入失败。
 *       已改 {@code private static}（全工程检索无外部引用 ⇒ 不影响编译）。</li>
 *   <li><b>{@code DIAGNOSTIC_LOG} 默认值</b>：原文件是 {@code true}（另一份留档是 {@code false}），
 *       而它会在<b>每个</b>拦截/捕获动作上打日志 ⇒ 界面上每次操作都刷日志。已统一为 {@code false}。</li>
 *   <li><b>删除静态初始化块</b>：原 {@code static { LOGGER.info(...) } }。Mixin 会把混入类的
 *       {@code <clinit>} 合并进目标类，而这里的目标是 <b>LDLib 的 {@code WidgetGroup}</b>
 *       —— 一个被所有模组 GUI 用的公共类。往它的初始化里塞我们模组的类加载属于不必要的耦合，
 *       已删除。生效判据改为：界面右上角上游那一行文字/方块消失（见第 ⑦ 节验证方法）。</li>
 *   <li><b>热路径早退</b>：{@code addWidget} / {@code removeWidget} 是 LDLib 里<b>最高频</b>的控件调用，
 *       钩子对全工程生效 ⇒ 两个处理器现在都先做 {@code HIDE_ENABLED} + {@code widget == null} +
 *       <b>类名字符串比较</b>（无分配）就返回；只有命中上游控件名才会走后面的弱表/放回逻辑。</li>
 *   <li><b>未使用 {@code @Overwrite}</b>（高危）；全部为 {@code @Inject} + {@code require = 0}
 *       + {@code try/catch(Throwable)} 全吞 ⇒ 织不上只是不生效，绝不崩在加载期/运行期。</li>
 * </ol>
 *
 * <h2>⑥ 已知更优替代（本轮不改，供后续决策）</h2>
 * <p>把注入点从 LDLib 的 {@code WidgetGroup} 换成上游自己的
 * {@code com.gtl.enhancedcore.common.recipe.iv.IvBufferUi.installNameControls(...)}
 * （{@code HEAD + cancellable + ci.cancel()}）可以把影响面从「全工程所有控件组」
 * 收窄到「上游命名控件这一条路径」，语义完全等价且更安全。本轮<b>保持原落点</b>是为了
 * 不改变已验证的行为面，仅记录为改进项。
 *
 * <h2>⑦ 开关与回退</h2>
 * <p>{@link #HIDE_ENABLED}（默认 {@code true} = 隐藏）。置 {@code false} ⇒ 本补丁立即完全失效，
 * 上游 {@code installNameControls} 原样跑完、控件与今天逐字一致（无需改配置、无需重启存档）。
 */
@Mixin(value = WidgetGroup.class, remap = false)
public class SuperBufferNameWidgetHideMixin {

    /** 总开关（默认开 = 隐藏）。{@code private} 是 Mixin 的硬性要求。 */
    private static boolean HIDE_ENABLED = true;

    /** 诊断开关（默认关）：为 true 时把每一次拦截/放回的结果打进日志（只加日志，不改判据）。 */
    private static boolean DIAGNOSTIC_LOG = false;

    /** 上游那个控件组的运行时全名（逐字取自作者源码）。用字符串比较 ⇒ 无编译期依赖。 */
    private static final String UPSTREAM_WIDGET =
            "com.gtl.enhancedcore.common.gui.SuperBufferNameWidget";

    /** 我们自己那个输入框的运行时全名（GTCEu 真实类，同样只用字符串比较）。 */
    private static final String OUR_INPUT_WIDGET =
            "com.gregtechceu.gtceu.integration.ae2.gui.widget.AETextInputButtonWidget";

    /**
     * 「控件组 → 被上游抽掉前的我们那个输入框」的一次性捕获表。
     * <p>键用弱引用 ⇒ 界面关掉后条目自动消失，不留内存。取值即移走（一次性）。
     */
    private static final Map<WidgetGroup, Widget> OUR_CAPTURED_INPUT = new WeakHashMap<>();

    /**
     * 捕获注入：上游 {@code IvBufferUi:15} 在抽掉我们输入框<b>之前</b>会先调
     * {@code group.getWidgetsByType(AETextInputButtonWidget.class)} ⇒ 在这一刻把它记下来。
     * <p>只读不写、不改上游行为；{@code require = 0} ⇒ 即便上游换了实现也只是不捕获。
     * <p>签名核对：{@code WidgetGroup.getWidgetsByType} 在 LDLib 1.0.33.b 中存在
     * （取证：{@code _census\_probe_nacmat_evidence\strings_gtlcore-1.2.3.2-fix1.txt:14748}，
     * 上游 audit 用法 {@code SuperBufferNameChecks.java:85}、{@code ClaimReplacementChecks.java:107}）。
     */
    @Inject(method = "getWidgetsByType", at = @At("HEAD"), require = 0, remap = false)
    private void gtlmodular$captureOurOwnInput(Class<?> clazz, CallbackInfoReturnable<List<Widget>> cir) {
        try {
            if (!HIDE_ENABLED || !(((Object) this) instanceof WidgetGroup group)) {
                return;
            }
            if (OUR_CAPTURED_INPUT.containsKey(group)) {
                return;   // 已经捕获过 ⇒ 不重复覆盖
            }
            for (Widget child : group.widgets) {
                if (child == null) {
                    continue;
                }
                if (OUR_INPUT_WIDGET.equals(child.getClass().getName())) {
                    OUR_CAPTURED_INPUT.put(group, child);
                    if (DIAGNOSTIC_LOG) {
                        gtlmodular$log("已捕获我们自己的输入框（等待上游替换时刻放回）：{}", child);
                    }
                    return;
                }
            }
        } catch (Throwable ignored) {
            // 捕获失败 = 之后只隐藏、不放回，绝不影响游戏
        }
    }

    /**
     * 拦截①：不让上游 {@code IvBufferUi:17} 的 {@code group.removeWidget(field)} 把
     * <b>我们的输入框</b>摘走（判据是被移除的控件恰是 {@link #OUR_INPUT_WIDGET}）。
     *
     * <p>ⓘ 上游 {@code IvBufferUi} 并不在意 {@code removeWidget} 的返回值，所以
     * {@code ci.cancel()} 之后上游紧接着的 {@code addWidget(new SuperBufferNameWidget(...))}
     * 仍然会发生 —— 那一步由 {@link #gtlmodular$hideUpstreamNameWidget} 负责拦下。
     * 两者配合的结果：我们的输入框没离开容器，上游控件组没进树。
     *
     * <p>签名核对：LDLib 的 {@code WidgetGroup.removeWidget(Widget)} 返回 {@code void}
     * （取证：{@code classstr_gtceu-1.20.1-1.4.4.txt:46895} 的 {@code /(Lcom/lowdragmc/lowdraglib/gui/widget/Widget;)V}）
     * ⇒ 处理器用 {@code CallbackInfo}。
     */
    @Inject(method = "removeWidget", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void gtlmodular$keepOurOwnInput(Widget widget, CallbackInfo ci) {
        try {
            if (!HIDE_ENABLED || widget == null) {
                return;
            }
            // 早退：绝大多数调用都不是我们的输入框（字符串比较，无分配）
            if (!OUR_INPUT_WIDGET.equals(widget.getClass().getName())) {
                return;
            }
            if (!(((Object) this) instanceof WidgetGroup group)) {
                return;
            }
            if (!gtlmodular$hasOurOwnInput(group)) {
                return;   // 组里本来就没有我们的输入框 ⇒ 这次摘除与我们无关，放行
            }
            ci.cancel();   // ★ 不摘 ⇒ 我们那个显示正确名字的输入框原地保留
            if (DIAGNOSTIC_LOG) {
                gtlmodular$log("已拦下上游对我们输入框的摘除（保持原位）");
            }
        } catch (Throwable ignored) {
            // 任何异常都不拦 ⇒ 上游照常摘除（随后由 addWidget 那一步兜底隐藏）
        }
    }

    /**
     * 拦截②：命中「上游往控件组里追加它的命名控件」时取消这次追加
     * ⇒ 那一行文字 + 两个方块控件一起消失。
     *
     * <p>兜底：万一 {@link #gtlmodular$keepOurOwnInput} 没织上（我们那个输入框已被摘成孤儿），
     * 这里再把捕获到的它放回容器；若 {@code removeWidget} 那条成功，输入框还在组里
     * ⇒ 本步什么都不做（不会出现两个输入框，也不会二次 add）。
     *
     * <p>签名核对：LDLib 的 {@code WidgetGroup.addWidget(Widget)} 返回 {@code WidgetGroup}
     * ⇒ 处理器用 {@code CallbackInfoReturnable<WidgetGroup>} 并 {@code cir.setReturnValue(group)}
     * （取证：{@code strings_gtceu-1.20.1-1.4.4.txt:39905}
     * {@code _(Lcom/lowdragmc/lowdraglib/gui/widget/Widget;)Lcom/lowdragmc/lowdraglib/gui/widget/WidgetGroup;}）。
     */
    @Inject(method = "addWidget", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void gtlmodular$hideUpstreamNameWidget(Widget widget, CallbackInfoReturnable<WidgetGroup> cir) {
        try {
            if (!HIDE_ENABLED || widget == null) {
                return;
            }
            // 早退：绝大多数调用都不是上游命名控件（字符串比较，无分配）
            if (!UPSTREAM_WIDGET.equals(widget.getClass().getName())) {
                return;
            }
            if (!(((Object) this) instanceof WidgetGroup group)) {
                return;
            }
            Widget restoredInput = null;
            if (!gtlmodular$hasOurOwnInput(group)) {
                restoredInput = gtlmodular$takeOurOwnInput(group);   // 兜底：孤儿输入框放回
                if (restoredInput != null) {
                    try {
                        // 坐标沿用我们原来的位置（上游新控件的坐标就是照抄我们输入框的）
                        restoredInput.setSelfPosition(widget.getSelfPositionX(), widget.getSelfPositionY());
                    } catch (Throwable ignored) {
                        // 坐标拿不到就用对象自带的
                    }
                }
            }
            cir.setReturnValue(group);   // ★ 这一次 addWidget 整体不发生
            if (restoredInput != null) {
                try {
                    group.addWidget(restoredInput);
                } catch (Throwable ignored) {
                    // 放回失败 = 只隐藏，不影响游戏
                }
            }
            if (DIAGNOSTIC_LOG) {
                gtlmodular$log("已隐藏 Enhancedcore 命名控件（{}）；兜底放回我们自己的输入框：{}",
                        UPSTREAM_WIDGET, restoredInput != null);
            }
        } catch (Throwable ignored) {
            // 任何异常都不拦 ⇒ 上游照常追加（最坏情况 = 今天的样子）
        }
    }

    /** 控件组里此刻是否已经有我们自己的输入框（只看直接子控件）。 */
    private static boolean gtlmodular$hasOurOwnInput(WidgetGroup group) {
        try {
            for (Widget child : group.widgets) {
                if (child != null && OUR_INPUT_WIDGET.equals(child.getClass().getName())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // 读不到就当没有 ⇒ 走兜底路径
        }
        return false;
    }

    /**
     * 找回流离在控件组里的我们自己的输入框。返回 {@code null} = 不放回（只隐藏）。
     *
     * <p>为什么要「捕获」而不是「当场找」：上游 {@code IvBufferUi:17} 的 {@code removeWidget}
     * 已经把输入框从控件组里摘掉了，等我们被调用时（{@code :18}）它已经不在 {@code group.widgets} 里；
     * 而 {@code :18} 的 {@code addWidget} 之前，同一个方法体里 {@code :15} 还会调
     * {@code getWidgetsByType(...)} —— 那时输入框还在组里。于是本补丁在 {@code getWidgetsByType}
     * 的 {@code HEAD} 上先把它捕获，供 {@code addWidget} 那一刻取用。
     *
     * <p>{@code WidgetGroup.widgets} 是可访问的列表字段
     * （取证：作者源码 audit 直接读 {@code WidgetGroup.widgets}，见
     * {@code FullscreenPreviewClientChecks.java:113/313}、{@code PreviewHighlightClientChecks.java:66/68}；
     * 本仓既有用法见 {@code MoltenPatternAssemblyPartMachine.java}）。这里只读、不删。
     */
    private static Widget gtlmodular$takeOurOwnInput(WidgetGroup group) {
        try {
            Widget captured = OUR_CAPTURED_INPUT.remove(group);
            if (captured == null) {
                return null;
            }
            // 校验：必须仍属于这棵控件树（否则就是别的界面/别的模组的对象，宁可不放回）
            if (captured.getParent() != group) {
                return null;
            }
            return captured;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 有界日志（异常全吞，绝不影响主流程）。 */
    private static void gtlmodular$log(String format, Object... args) {
        try {
            GTLModular.LOGGER.info("[gtlmodular] " + format, args);
        } catch (Throwable ignored) {
        }
    }
}
