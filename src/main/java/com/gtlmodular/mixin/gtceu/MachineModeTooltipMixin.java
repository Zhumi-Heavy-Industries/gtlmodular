package com.gtlmodular.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.fancyconfigurator.MachineModeFancyConfigurator;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 「机器模式」页的工具提示本地化。
 *
 * <p>GTCEu 的 {@link MachineModeFancyConfigurator#getTabTooltips()} 把 tooltip 写死成
 * {@code Component.literal("Change active Machine Mode")}（不是语言键，改 lang 文件没用），
 * 玩家反馈「这个机器模式这里也是英文」⇒ 这里在 RETURN 处把返回值换成我们的语言键。
 *
 * <p>该方法只被客户端 GUI 调用 ⇒ 放在 {@code gtlmodular.mixins.json} 的 {@code client} 列表里，
 * 专用服务器不会加载。
 */
@Mixin(value = MachineModeFancyConfigurator.class, remap = false)
public abstract class MachineModeTooltipMixin {

    @Inject(method = "getTabTooltips", at = @At("RETURN"), cancellable = true, remap = false)
    private void gtlmodular$translateMachineModeTooltip(CallbackInfoReturnable<List<Component>> callback) {
        callback.setReturnValue(List.of(Component.translatable("gtlmodular.gui.machine_mode.tooltip")));
    }
}
