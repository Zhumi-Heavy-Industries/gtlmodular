package com.gtlmodular.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtlmodular.common.machine.fuel.AccelerateHatchLogic;
import com.gtlmodular.common.machine.fuel.OverclockHatchLogic;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * 兜底：每 tick 把 {@code duration} 校正到「按原始时长换算后」的值。
 *
 * <h2>为什么 setupRecipe 还不够</h2>
 * <p>有些配方逻辑（gtlcore 的 {@code MultipleRecipesLogic}、增强版的原生引擎、
 * gtladditions 的 Mutable 系）在 {@code setupRecipe} <b>之后</b>仍会改写
 * {@link RecipeLogic#duration}：它们在 {@code tickTasks} / 自家引擎里按
 * 「总能耗 ÷ 输入功率」重新算一遍时长，把我们在 {@code setupRecipe} 写下的值覆盖掉。
 * 只在 {@code setupRecipe} 上动手，这些机器就会出现「装得上、进度条不变」。
 *
 * <h2>为什么要记原始时长</h2>
 * <p>覆盖发生之后，{@code recipe.duration} 已经不是原始值了；若每 tick 都按当前值乘一次，
 * 时长会一路塌到 1，进度条瞬间跑完（那是彻底的错误行为）。
 * 所以第一次看到这条配方时把<b>原始时长</b>记进 {@code recipe.data}，
 * 之后每次都用「原始值 × 仓的换算」重算，而不是在当前值上继续乘。
 *
 * <p>键用 {@code gtlmodular_tick_original_duration}，与
 * {@link AccelerateHatchLogic} 内部的结果去重键、以及超频仓的键都分开，
 * 三者串联时才不会互相覆盖。
 *
 * <h2>为什么不在客户端跑</h2>
 * <p>客户端也会 tick 配方逻辑（做进度显示），但 {@code level} 为 {@code null}
 * 或 {@code isClientSide}。在那里改时长只会让界面和服务器不一致，没有任何收益，
 * 所以直接跳过。
 *
 * <h2>为什么用反射而不是 @Shadow</h2>
 * <p>运行期 {@link RecipeLogic} 的父类链会被别的模组的 mixin 改动（增强版就把自己的逻辑
 * 混了进来）。跨 jar 的 {@code @Shadow} 一旦解析不到，会在<b>类加载期</b>抛
 * {@code Shadow field ... was not located} 直接崩游戏；反射失败只是「这次不生效」。
 *
 * <h2>优先级 800</h2>
 * <p>要排在 {@code tickTasks} 一类「每 tick 重算时长」的改写<b>之后</b>，
 * 才能覆盖它们的结果。比 setup 侧的 1300 低，两者不会互相打架。
 */
@Mixin(value = RecipeLogic.class, remap = false, priority = 800)
public abstract class RecipeLogicGainHatchTickMixin {

    private static final String ORIGINAL = "gtlmodular_tick_original_duration";

    /**
     * {@code RecipeLogic#duration} 的反射句柄。
     *
     * <p>不用 {@code @Shadow}：本类的父类链在运行期会被别的模组的 mixin 改动
     * （增强版把 {@code IvRecipeLogic} 混到 {@code RecipeLogic} 上），
     * 跨 jar 的 {@code @Shadow} 一旦解析不到就会在类加载期抛
     * {@code Shadow field ... was not located}，直接崩游戏。
     * 反射失败只是「这次不生效」，可控。
     */
    private static final Field GTLMODULAR$DURATION = gtlmodular$findDuration();

    private static Field gtlmodular$findDuration() {
        Class<?> type = RecipeLogic.class;
        while (type != null) {
            try {
                Field field = type.getDeclaredField("duration");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    @Inject(method = "serverTick", at = @At("RETURN"), remap = false)
    private void gtlmodular$applyGainHatches(CallbackInfo ci) {
        Field field = GTLMODULAR$DURATION;
        if (field == null) {
            return;
        }
        RecipeLogic self = (RecipeLogic) (Object) this;
        MetaMachine machine = self.getMachine();
        if (machine == null || machine.getDefinition() == null) {
            return;
        }
        // 客户端不跑：改了只会让界面和服务端不一致。
        if (machine.getLevel() == null || machine.getLevel().isClientSide()) {
            return;
        }
        GTRecipe recipe = self.getLastRecipe();
        if (recipe == null) {
            return;
        }
        try {
            int current = field.getInt(self);
            if (current <= 0) {
                return;
            }
            // 原始时长只在第一次看到这条配方时记一次。
            // 记「原始值」而不是「已完成」标记：原始值对同一配方恒定，复制出去也无害；
            // 标记一旦共享就会让后续副本全部跳过换算。
            CompoundTag data = recipe.data;
            int original;
            if (data.contains(ORIGINAL)) {
                original = data.getInt(ORIGINAL);
            } else {
                original = recipe.duration;
                data.putInt(ORIGINAL, original);
            }
            if (original <= 0) {
                return;
            }
            int adjusted = AccelerateHatchLogic.applyToDuration(
                    machine, original, RecipeHelper.getRecipeEUtTier(recipe));
            long maxEUt = machine instanceof IOverclockMachine oc ? oc.getOverclockVoltage() : 0L;
            adjusted = OverclockHatchLogic.applyToDuration(
                    machine, adjusted, maxEUt, RecipeHelper.getInputEUt(recipe), 0.25D);
            if (adjusted != current) {
                field.setInt(self, adjusted);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // 字段被改名 / 无权访问 ⇒ 本次不生效，不影响游戏其它部分。
        }
    }
}
