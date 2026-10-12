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

/** ConfiguratorPanel.Tab#expand 的下边界修正可能把子页推到屏幕上方，需补齐顶部夹取。 */
@Mixin(value = ConfiguratorPanel.Tab.class, remap = false)
public abstract class ConfiguratorPanelTabMixin {

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
                int top = view.getParentPosition().y + target.y;
                if (top < 0) {
                    return animation.position(new Position(target.x, target.y - top));
                }
            }
        } catch (Throwable ignored) {
        }
        return animation.position(target);
    }
}
