package com.gtlmodular.common.compat;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtlmodular.GTLModular;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 宿主机器是不是 <b>GTLsupb（超级样板总成 / 卫核样板总成）的可变配方机器</b>。
 *
 * <p><b>为什么需要它</b>（2026-10-30 玩家报告「SUPB 的机器用上我们的样板总成以后完全没办法工作」）：
 * <ul>
 *   <li>SUPB 的可变配方机器靠 {@code com.gtlsupb.gtlsupb.api.machine.IPatternAssemblyHatch}
 *       找样板（{@code MutableRecipesLogic#countBoundSlots} 遍历机器部件时只认
 *       {@code instanceof IPatternAssemblyHatch}）；</li>
 *   <li>实现这个接口的只有 SUPB 自己的 {@code gtlsupb:weihe_pattern_assembly}（卫核样板总成）；</li>
 *   <li>我们的样板总成和它注册了<b>完全相同的 4 个 IO 能力</b>
 *       （IMPORT/EXPORT 物品/流体），而 SUPB 的结构没有把那个舱位钉死成自己的方块
 *       ⇒ <b>我们的仓能被顶进那个位置</b>；</li>
 *   <li>顶进去以后 SUPB 侧扫不到 {@code IPatternAssemblyHatch} ⇒ 机器成型但一条配方都绑不到，
 *       整台停摆，而且没有任何提示。</li>
 * </ul>
 *
 * <p><b>0.3.223 先止血</b>：识别到这类宿主时，我们的自动切模式 / 推料锁模式 / 自动命名全部停手，
 * 并给附近玩家一条明确提示。真适配（实现 {@code IPatternAssemblyHatch}）另排期。
 *
 * <p><b>判据只看配方逻辑的类名</b>（{@code com.gtlsupb.gtlsupb.*}）：SUPB 的普通机器用 GTCEu
 * 标准 {@code RecipeLogic} 时本来就不需要这个接口，不能一起误伤；只有
 * {@code MutableRecipesLogic} / {@code MultiTypeMutableRecipesLogic} 这类逻辑才是"必须从
 * IPatternAssemblyHatch 取配方"的宿主。SUPB 是可选依赖 ⇒ 只做字符串判断，不加载它的类。
 */
public final class SupbMutableMachineCompat {

    private static final String SUPB_PACKAGE = "com.gtlsupb.gtlsupb.";

    /** 同一条提示对同一台机器的冷却（毫秒）——避免每 tick 刷屏。 */
    private static final long NOTIFY_COOLDOWN_MS = 300_000L;

    private static final Map<Object, Long> LAST_NOTIFY = new WeakHashMap<>();

    private SupbMutableMachineCompat() {
    }

    /** 控制器的配方逻辑是否属于 SUPB 的可变配方引擎（= 必须从 IPatternAssemblyHatch 取配方）。 */
    public static boolean suppressesModeSwitch(IRecipeLogicMachine controller) {
        if (controller == null) {
            return false;
        }
        RecipeLogic logic;
        try {
            logic = controller.getRecipeLogic();
        } catch (RuntimeException failed) {
            return false;
        }
        if (logic == null) {
            return false;
        }
        try {
            return logic.getClass().getName().startsWith(SUPB_PACKAGE);
        } catch (Throwable failed) {
            return false;
        }
    }

    /**
     * 明确提示：这台机器不会从我们的样板总成拿到配方。
     * 只在服务端调用；同一台机器 {@value #NOTIFY_COOLDOWN_MS} 毫秒内最多一条，附近 16 格玩家可收到。
     */
    public static void notifyUnsupported(MetaMachine part, IRecipeLogicMachine controller) {
        if (part == null || part.getLevel() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        // 以机器实例为键：BlockPos 每次新建、WeakHashMap 里会被立刻回收，起不到限频作用。
        Object key = part;
        Long last = LAST_NOTIFY.get(key);
        if (last != null && now - last < NOTIFY_COOLDOWN_MS) {
            return;
        }
        LAST_NOTIFY.put(key, now);
        Level level = part.getLevel();
        Component message = Component.translatable("gtlmodular.pattern_assembly.supb_no_hatch");
        try {
            for (Player player : level.players()) {
                if (player.distanceToSqr(part.getPos().getX() + 0.5, part.getPos().getY() + 0.5,
                        part.getPos().getZ() + 0.5) <= 256.0) {
                    player.displayClientMessage(message, false);
                }
            }
        } catch (RuntimeException ignored) {
            // 提示失败不影响机器逻辑
        }
        GTLModular.LOGGER.warn("[gtlmodular] SUPB 可变配方机器：本样板总成不实现 IPatternAssemblyHatch，"
                        + "机器不会从本仓取配方；请改用 gtlsupb 的卫核样板总成 @{}（控制器={}）",
                part.getPos(), controller == null ? "?" : controller.self().getClass().getSimpleName());
    }
}
