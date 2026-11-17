package com.gtlmodular.mixin.gtladditions;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.machine.fuel.AccelerateHatchLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * 让加速仓在 gtladditions 的 Mutable 系机器上生效。
 *
 * <h2>为什么必须单独注</h2>
 * <p>{@code MutableRecipesLogic} <b>直接继承 {@code RecipeLogic}</b>，
 * 不是 {@code GTLAddMultipleRecipesLogic} 的子类；它的 {@code findAndHandleRecipe}
 * 按 {@code useMultipleRecipes} 分支，并把算好的时长写进 {@code lastRecipe}。
 * 给多配方族打针不会命中它，{@code AddMutableRecipesLogic} 继承本类则一并覆盖。
 *
 * <h2>为什么注在 findAndHandleRecipe 尾部</h2>
 * <p>{@code checkRecipe} 是查找过滤器、每条候选配方会被回调多次，
 * 在那里改时长会把同一条配方压缩 N 次；{@code findAndHandleRecipe} 每次尝试只跑一次，
 * 结束时 {@code lastRecipe} 已经定下来、发货拿的正是它 ⇒ 只缩一次，且作用到实际发的那份。
 *
 * <h2>为什么还要反射写 duration</h2>
 * <p>改了 {@code lastRecipe.duration} 只影响「配方对象本身」。
 * 若该逻辑随后用 {@code lastRecipe} 重算 {@code this.duration}，就还需要把计时字段一并对齐。
 * 用反射而不是 {@code @Shadow}：跨 jar 的 {@code @Shadow} 一旦解析不到会在类加载期崩游戏，
 * 反射失败只是「这次不生效」。
 *
 * <p>没装加速仓时 {@link AccelerateHatchLogic#applyIfNeeded} 原样返回，零副作用。
 */
@Mixin(targets = "com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic", remap = false)
public abstract class MutableRecipesLogicGainHatchMixin {

    @Inject(method = "findAndHandleRecipe", at = @At("RETURN"), remap = false)
    private void gtlmodular$applyAccelerate(CallbackInfo ci) {
        RecipeLogic self = (RecipeLogic) (Object) this;
        GTRecipe recipe = self.getLastRecipe();
        if (recipe == null) {
            return;
        }
        int before = recipe.duration;
        GTRecipe adjusted = AccelerateHatchLogic.applyIfNeeded(self.getMachine(), recipe);
        if (adjusted == null || adjusted.duration == before) {
            return;
        }
        gtlmodular$setDuration(self, Math.max(1, adjusted.duration));
    }

    /** 沿父类链反射写 {@code duration}，兼容被别的模组改写过的类。 */
    private static void gtlmodular$setDuration(RecipeLogic logic, int value) {
        Class<?> type = logic.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("duration");
                field.setAccessible(true);
                field.setInt(logic, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return;
            }
        }
    }
}
