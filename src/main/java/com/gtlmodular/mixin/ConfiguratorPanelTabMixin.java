package com.gtlmodular.mixin;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.lowdragmc.lowdraglib.gui.animation.Animation;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * ★ 0.3.35（队长批准的方案 A）：给 GTCEu 的「展开子页」补上**上越界夹取**。
 *
 * <h2>为什么只有这一条是正解（整条链都有字节码/实机依据）</h2>
 * <ol>
 *   <li>GTCEu 在主界面控件里 new 出 {@code ConfiguratorPanel}，面板锚点按**主界面控件的左下角**算
 *       ⇒ 「界面越矮 ⇒ 面板越靠上」（实机对照：惰性机 UI=180 正常、本机 UI=172 越界）。</li>
 *   <li>展开子页（含面板画的 24px 页眉：我们的标题 + 图标）的 Y = {@code dragOffsetY}，
 *       就贴在「面板顶」上。</li>
 *   <li>{@code ConfiguratorPanel$Tab.expand()}（gtl-dev/api-dump/configurator_tab.javap.txt:346-471）
 *       只有【X 左越界】与【Y 下越界】两处夹取，**没有"上越界"分支**
 *       （Y 的默认值就是 0，只有"底边超出屏幕"时才会被推成负数）。</li>
 * </ol>
 * ⇒ 面板顶一旦为负（页签一多、主界面一矮就会发生），子页与页眉必然被屏幕顶边切掉 ✗。
 * <p>本 mixin 就是补那个缺失的分支：把动画目标位置整体往下推，直到视图顶边 ≥ 0。
 *
 * <h2>实现要点</h2>
 * <ul>
 *   <li>只重定向 {@code expand()} 里那一处 {@code Animation#position(Position)}
 *       —— 页签自己的 {@code setSelfPosition}（本方法开头那处）**不碰**，
 *       gtlcore 的「页签多列排布」照旧生效 ✓。</li>
 *   <li>{@code @Shadow} 直接读私有字段 {@code view}（＝"页眉 + 我们页面"的那个容器）。</li>
 *   <li>GTCEu / LDLib 是**模组**、其成员不经 SRG 重映射 ⇒ 所有注入点一律 {@code remap = false}，
 *       因此本工程不需要生成 refmap。</li>
 *   <li>整段包在 try/catch 里：任何意外（屏高取不到、字段语义变了）都退回原版行为，
 *       绝不因为一个界面修补让游戏崩。</li>
 * </ul>
 */
@Mixin(value = ConfiguratorPanel.Tab.class, remap = false)
public abstract class ConfiguratorPanelTabMixin {

    /** 「页眉 + 本页控件」的容器（原版把动画目标位置写给它）。 */
    @Shadow
    private WidgetGroup view;

    @Redirect(method = "expand",
            at = @At(value = "INVOKE",
                    target = "Lcom/lowdragmc/lowdraglib/gui/animation/Animation;position(Lcom/lowdragmc/lowdraglib/utils/Position;)"
                            + "Lcom/lowdragmc/lowdraglib/gui/animation/Animation;"),
            remap = false)
    private Animation gtlmodular$clampExpandedPageIntoView(Animation animation, Position target) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (view != null && mc != null && mc.getWindow() != null) {
                int screenHeight = mc.getWindow().getGuiScaledHeight();
                // 按原版算出来的位置，视图顶边会落在屏幕的哪一行
                int top = view.getParentPosition().y + target.y;
                if (top < 0) {
                    // ★ 缺的那条分支：整体下移 |top|，让顶边贴住屏幕顶（页眉不再被切）
                    return animation.position(new Position(target.x, target.y - top));
                }
            }
        } catch (Throwable ignored) {
            // 退回原版位置
        }
        return animation.position(target);
    }
}
