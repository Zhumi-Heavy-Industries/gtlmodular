package com.gtlmodular.mixin;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.lowdragmc.lowdraglib.gui.animation.Animation;
import com.lowdragmc.lowdraglib.utils.Position;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** ConfiguratorPanel.Tab#expand 的下边界修正可能把子页推到屏幕上方，需补齐顶部夹取。 */
@Mixin(value = ConfiguratorPanel.Tab.class, remap = false)
public abstract class ConfiguratorPanelTabMixin {

    @Shadow
    protected int dragOffsetY;

    @Redirect(method = "expand()V",
            at = @At(value = "INVOKE",
                    target = "Lcom/lowdragmc/lowdraglib/gui/animation/Animation;position(Lcom/lowdragmc/lowdraglib/utils/Position;)"
                            + "Lcom/lowdragmc/lowdraglib/gui/animation/Animation;"),
            require = 1, remap = false)
    private Animation gtlmodular$clampExpandedPageIntoView(Animation animation, Position target) {
        ConfiguratorPanel.Tab tab = (ConfiguratorPanel.Tab) (Object) this;
        if (tab.isRemote()) {
            // 动画移动的是 Tab；view 的父坐标还带着展开前的页签偏移。
            int top = tab.getParentPosition().y + target.y;
            if (top < 0) {
                // 尺寸更新会按拖动偏移重新定位，必须保留同一修正量以免回跳。
                dragOffsetY -= top;
                return animation.position(new Position(target.x, target.y - top));
            }
        }
        return animation.position(target);
    }
}
