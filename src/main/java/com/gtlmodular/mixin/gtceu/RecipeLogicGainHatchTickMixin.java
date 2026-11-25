package com.gtlmodular.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtlmodular.common.machine.fuel.AccelerateHatchLogic;
import com.gtlmodular.common.machine.fuel.AccelerateHatchPredicate;
import com.gtlmodular.common.machine.fuel.OverclockHatchLogic;
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
        // ★ 2026-11-25：诊断 —— 时长离谱（> 1,000,000 tick ≈ 13.9 游戏小时）时每台机器 5 分钟打一条 WARN。
        //   这次事故（11/72000000）的写入点不在本模组，日志是为了把它钉在「哪台机器、哪条配方、哪套逻辑」上。
        //   ★ 放在「有没有仓」的早退之前：出事的通用组装厂-plus 就没有加速/超频仓。
        gtlmodular$warnSuspiciousDuration(self, machine, recipe);
        // ★★ 2026-11-25（队长：「组装机制作部件组装机，配方耗时异常（11/72000000）」）：
        //   **没有任何影响时长的仓 ⇒ 一个字都不写。**
        //   旧版不管装没装仓都会走到下面：original 从 recipe.data 取、adjusted 与 current 不等就写回
        //   ⇒ 无仓机器也会被「校正」成 recipe.data 里记的原始时长。而 GTRecipe.copy() 把 data
        //   **按引用**传给副本、同一条配方又会被多台机器共用 ⇒ 任何一台机器（走能量换算链的
        //   IvRecipeLogic / MultipleRecipesLogic）在某条配方上记下的巨大时长，会被另一台
        //   （通用组装厂-plus 这类）**每 tick 重放**，进度条分母就永远是 72,000,000。
        //   没有仓的机器本来就轮不到本 mixin 管，直接早退。
        if (!AccelerateHatchPredicate.runtimeEnabled() && !OverclockHatchLogic.enabled()) {
            return;
        }
        if (AccelerateHatchLogic.find(machine) == null && OverclockHatchLogic.find(machine) == null) {
            return;
        }
        try {
            int current = field.getInt(self);
            if (current <= 0) {
                return;
            }
            int original = gtlmodular$originalFor(machine, recipe);
            if (original <= 0) {
                return;
            }
            int adjusted = AccelerateHatchLogic.applyToDuration(
                    machine, original, RecipeHelper.getRecipeEUtTier(recipe));
            long maxEUt = machine instanceof IOverclockMachine oc ? oc.getOverclockVoltage() : 0L;
            adjusted = OverclockHatchLogic.applyToDuration(
                    machine, adjusted, maxEUt, RecipeHelper.getInputEUt(recipe), 0.25D);
            if (adjusted > 0 && adjusted != current) {
                field.setInt(self, adjusted);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // 字段被改名 / 无权访问 ⇒ 本次不生效，不影响游戏其它部分。
        }
    }

    /**
     * 「这条配方在这台机器上的原始时长」——按 <b>机器 + 配方 id</b> 记，<b>不写进 {@code recipe.data}</b>。
     *
     * <p>为什么不能写进 {@code recipe.data}：{@code GTRecipe.copy()} 把 {@code data} 按引用传给副本，
     * 同一配方（同一 id）会被多台机器共用 ⇒ 一台机器记下的值会被别的机器读到并每 tick 重放
     * （2026-11-25「72,000,000 时长」实机事故的传播链）。按机器隔离就只影响它自己。
     *
     * <p>为什么按 {@code recipe.id} 而不是对象身份：gtlcore / 增强版会在每次 setup 时复制配方，
     * 对象身份每个副本都不同；id 才是「同一条配方」的稳定身份。id 为 {@code null} 时退回对象身份。
     */
    private static int gtlmodular$originalFor(MetaMachine machine, GTRecipe recipe) {
        synchronized (ORIGINALS) {
            java.util.Map<Object, Integer> byRecipe =
                    ORIGINALS.computeIfAbsent(machine, m -> new java.util.HashMap<>());
            if (byRecipe.size() > 512) {
                byRecipe.clear();
            }
            Object key = recipe.id != null ? recipe.id : recipe;
            Integer cached = byRecipe.get(key);
            if (cached != null) {
                return cached;
            }
            int original = recipe.duration;
            if (original > 0) {
                byRecipe.put(key, original);
            }
            return original;
        }
    }

    /** 机器 →（配方 id / 配方对象 → 原始时长）。机器弱引用，不阻止回收；条目按机器上限清空。 */
    private static final java.util.Map<MetaMachine, java.util.Map<Object, Integer>> ORIGINALS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** 时长超过这个值就当成异常（1,000,000 tick ≈ 13.9 游戏小时），打一条限频 WARN。 */
    private static final int SUSPICIOUS_DURATION = 1_000_000;

    /** 每台机器上一次打异常时长日志的 gameTime（5 分钟限频）。 */
    private static final java.util.Map<MetaMachine, Long> LAST_WARN =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static void gtlmodular$warnSuspiciousDuration(RecipeLogic self, MetaMachine machine, GTRecipe recipe) {
        try {
            int duration = GTLMODULAR$DURATION == null ? 0 : GTLMODULAR$DURATION.getInt(self);
            if (duration <= SUSPICIOUS_DURATION) {
                return;
            }
            var level = machine.getLevel();
            long now = level == null ? 0L : level.getGameTime();
            Long last = LAST_WARN.get(machine);
            if (last != null && now - last < 6000L) {
                return;
            }
            LAST_WARN.put(machine, now);
            com.gtlmodular.GTLModular.LOGGER.warn(
                    "[gtlmodular] 配方时长异常：{} tick（≈{} 秒）@{} logic={} recipe={}"
                            + "（0.3.491 起已停止把「原始时长」写进 recipe.data；若仍出现，请把这行发给开发）",
                    duration, duration / 20, machine.self().getPos(),
                    self.getClass().getSimpleName(), recipe.id);
        } catch (Throwable ignored) {
            // 诊断失败绝不影响配方逻辑
        }
    }
}
