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

/** 在控件进出容器时拦截命名替换，避免依赖多个 createUIWidget 返回注入的执行顺序。 */
@Mixin(value = WidgetGroup.class, remap = false)
public class SuperBufferNameWidgetHideMixin {

    private static boolean HIDE_ENABLED = true;

    private static boolean DIAGNOSTIC_LOG = false;

    /** 可选上游控件通过类名识别，避免引入编译期依赖。 */
    private static final String UPSTREAM_WIDGET =
            "com.gtl.enhancedcore.common.gui.SuperBufferNameWidget";

    private static final String OUR_INPUT_WIDGET =
            "com.gregtechceu.gtceu.integration.ae2.gui.widget.AETextInputButtonWidget";

    /** 弱键避免缓存延长已关闭界面的生命周期；捕获只供一次恢复使用。 */
    private static final Map<WidgetGroup, Widget> OUR_CAPTURED_INPUT = new WeakHashMap<>();

    @Inject(method = "getWidgetsByType", at = @At("HEAD"), require = 0, remap = false)
    private void gtlmodular$captureOurOwnInput(Class<?> clazz, CallbackInfoReturnable<List<Widget>> cir) {
        try {
            if (!HIDE_ENABLED || !(((Object) this) instanceof WidgetGroup group)) {
                return;
            }
            if (OUR_CAPTURED_INPUT.containsKey(group)) {
                return;
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
        }
    }

    /** 保留输入框后仍需拦截随后的追加，避免两套命名控件同时进入容器。 */
    @Inject(method = "removeWidget", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void gtlmodular$keepOurOwnInput(Widget widget, CallbackInfo ci) {
        try {
            if (!HIDE_ENABLED || widget == null) {
                return;
            }
            // 控件增删是公共热路径，先排除无关类型以避免读取捕获表。
            if (!OUR_INPUT_WIDGET.equals(widget.getClass().getName())) {
                return;
            }
            if (!(((Object) this) instanceof WidgetGroup group)) {
                return;
            }
            if (!gtlmodular$hasOurOwnInput(group)) {
                return;
            }
            ci.cancel();
            if (DIAGNOSTIC_LOG) {
                gtlmodular$log("已拦下上游对我们输入框的摘除（保持原位）");
            }
        } catch (Throwable ignored) {
        }
    }

    /** 移除拦截未生效时恢复捕获的输入框，避免只隐藏上游控件后失去改名入口。 */
    @Inject(method = "addWidget", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void gtlmodular$hideUpstreamNameWidget(Widget widget, CallbackInfoReturnable<WidgetGroup> cir) {
        try {
            if (!HIDE_ENABLED || widget == null) {
                return;
            }
            if (!UPSTREAM_WIDGET.equals(widget.getClass().getName())) {
                return;
            }
            if (!(((Object) this) instanceof WidgetGroup group)) {
                return;
            }
            Widget restoredInput = null;
            if (!gtlmodular$hasOurOwnInput(group)) {
                restoredInput = gtlmodular$takeOurOwnInput(group);
                if (restoredInput != null) {
                    try {
                        restoredInput.setSelfPosition(widget.getSelfPositionX(), widget.getSelfPositionY());
                    } catch (Throwable ignored) {
                    }
                }
            }
            cir.setReturnValue(group);
            if (restoredInput != null) {
                try {
                    group.addWidget(restoredInput);
                } catch (Throwable ignored) {
                }
            }
            if (DIAGNOSTIC_LOG) {
                gtlmodular$log("已隐藏 Enhancedcore 命名控件（{}）；兜底放回我们自己的输入框：{}",
                        UPSTREAM_WIDGET, restoredInput != null);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean gtlmodular$hasOurOwnInput(WidgetGroup group) {
        try {
            for (Widget child : group.widgets) {
                if (child != null && OUR_INPUT_WIDGET.equals(child.getClass().getName())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 上游移除后无法再从容器中找到输入框，因此在枚举控件时提前捕获。 */
    private static Widget gtlmodular$takeOurOwnInput(WidgetGroup group) {
        try {
            Widget captured = OUR_CAPTURED_INPUT.remove(group);
            if (captured == null) {
                return null;
            }
            // 不恢复已转移到其他控件树的对象，避免破坏父子关系。
            if (captured.getParent() != group) {
                return null;
            }
            return captured;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void gtlmodular$log(String format, Object... args) {
        try {
            GTLModular.LOGGER.info("[gtlmodular] " + format, args);
        } catch (Throwable ignored) {
        }
    }
}
