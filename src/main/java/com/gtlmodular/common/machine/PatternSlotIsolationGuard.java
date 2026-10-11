package com.gtlmodular.common.machine;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.minecraft.resources.ResourceLocation;

/**
 * B 层：严格逐槽隔离的**判定核心**（甲案）。
 *
 * <p>口径来自「星律」作者（2026-10-02）与其实现（gt_shanhai 4.0.1 反汇编）：
 * GTCEu 的配方层"只要能匹配就做"，而样板仓默认会把<b>所有槽</b>一起拿去匹配
 * ⇒ A 配方的产物/原料与 B 配方需求能拼上时就会<b>跨槽串配方</b>。
 * 严格逐槽 = 只允许"该配方**所属的那个槽**"参与匹配，其余槽不参战。
 *
 * <p>本类只做判定；注入点在 mixin 里（gtlcore 的样板仓 ME 输入/流体处理器 ✓ 它们是 public 方法 ✓）。
 * <ul>
 *   <li>归属槽由 A 层的 {@link MoltenPatternAssemblyPartMachine#slotOwning} 提供（0.3.64 起 ✓）</li>
 *   <li><b>豁免</b>：{@code gtceu:dummy} 这类机器自身的辅助 IO 配方一律放行 ✓（照星律 isAuxiliaryIORecipe ✓）</li>
 *   <li><b>总开关</b>：{@link #STRICT_SLOT_ISOLATION}（默认开 ✓ 关掉立刻回到宽松行为 ✓）</li>
 * </ul>
 */
public final class PatternSlotIsolationGuard {

    /** 总开关：true = 严格逐槽隔离（默认 ✓）；false = 完全回到旧行为（一行回退 ✓）。 */
    public static boolean STRICT_SLOT_ISOLATION = true;

    /** 辅助 IO 配方类型（豁免名单；与星律同口径 ✓）。 */
    private static final ResourceLocation DUMMY_TYPE = new ResourceLocation("gtceu", "dummy");

    /** 限流日志（每秒最多一条 ✓ 免得刷屏）。 */
    private static long lastLogMs = 0L;

    private PatternSlotIsolationGuard() {
    }

    /**
     * 这条配方是否应当被**拒绝**（因为要用的槽不是它的归属槽）。
     *
     * @param ownerMachine 该处理器所属的机器（若为本模组的样板总成 ⇒ 用 A 层的归属查表 ✓）
     * @param recipe       正在被处理的那条配方
     * @return true = 拒绝（跨槽）✗；false = 放行 ✓
     */
    public static boolean rejects(Object ownerMachine, GTRecipe recipe) {
        if (!STRICT_SLOT_ISOLATION || recipe == null) {
            return false;
        }
        try {
            if (isAuxiliaryIORecipe(recipe)) {
                return false;   // 机器自身的辅助 IO ⇒ 放行 ✓
            }
            if (!(ownerMachine instanceof MoltenPatternAssemblyPartMachine machine)) {
                return false;   // 不是我们的机器 ⇒ 不掺和 ✓
            }
            int owner = machine.slotOwning(recipe);
            if (owner < 0) {
                return false;   // 归属未知 ⇒ 保守放行（绝不误杀 ✓）
            }
            int[] active = machine.activePatternSlotsForIsolation();
            if (active == null || active.length == 0) {
                return false;
            }
            for (int slot : active) {
                if (slot == owner) {
                    return false;   // 归属槽在参与集里 ⇒ 放行 ✓
                }
            }
            long now = System.currentTimeMillis();
            if (now - lastLogMs > 1000L) {
                lastLogMs = now;
                com.gtlmodular.GTLModular.LOGGER.info(
                        "[gtlmodular] 槽隔离：拒绝跨槽配方 {}（归属槽 {} 不在参与集内）@{}",
                        recipe.getId(), owner, machine.getPos());
            }
            return true;
        } catch (Throwable ignored) {
            return false;   // 任何异常都放行（绝不因隔离把机器搞停 ✗）
        }
    }

    /** 是否为辅助 IO 配方（豁免 ✓）。 */
    public static boolean isAuxiliaryIORecipe(GTRecipe recipe) {
        try {
            ResourceLocation id = recipe.getType() == null ? null : recipe.getType().registryName;
            return id != null && id.equals(DUMMY_TYPE);
        } catch (Throwable ignored) {
            return false;
        }
    }
}