package com.gtlmodular.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

/**
 * 样板"槽作用域"标记工具（A 层）。
 *
 * <p><b>为什么要它</b>：GTCEu 的配方层"只要能匹配就做"，而样板仓默认会把<b>所有槽</b>一起拿去匹配
 * ⇒ 当 A 配方的产物/原料与 B 配方的需求能拼上，就会<b>跨槽串配方</b>。
 * 「星律」（gt_shanhai 4.0.1，反汇编实证）的做法是给每条样板配方打上
 * 「来源维度 + 来源仓 pos + 槽号」的标记（{@code PatternSlotScopedRecipe}，后缀
 * {@code "/__gt_shanhai_pattern_slot/"}），之后任何跨槽匹配都能被识破并拒绝。
 *
 * <p>本类就是那套标记的**最小实现**：把作用域信息编码进 recipe id 后缀。
 * <ul>
 *   <li>不改 {@link GTRecipe} 的结构 ✗（只改 id 字符串 ✓）</li>
 *   <li>A 层**只打标记、不做任何拦截** ✓ ⇒ 对外行为零变化 ✓（拦截留给 B 层的槽级守卫）</li>
 * </ul>
 *
 * <p>格式：{@code <原id> + SCOPE_PATH + <dim> + "|" + <pos.asLong()> + "|" + <slot>}
 */
public final class PatternSlotScope {

    /** 作用域后缀（与星律同思路，但用我们自己的命名空间，避免与他的 id 相互误判 ✓）。 */
    public static final String SCOPE_PATH = "/__gtlmodular_pattern_slot/";

    private PatternSlotScope() {
    }

    /** 给配方打作用域标记；已带标记则原样返回（幂等 ✓）。 */
    public static GTRecipe scope(GTRecipe recipe, ResourceLocation dim, BlockPos pos, int slot) {
        if (recipe == null || dim == null || pos == null) {
            return recipe;
        }
        ResourceLocation id = recipe.getId();
        if (id == null || isScoped(recipe)) {
            return recipe;
        }
        ResourceLocation scoped = ResourceLocation.tryParse(
                id + SCOPE_PATH + dim + "|" + pos.asLong() + "|" + slot);
        if (scoped == null) {
            return recipe;
        }
        recipe.setId(scoped);
        return recipe;
    }

    /** 该配方是否带作用域标记。 */
    public static boolean isScoped(GTRecipe recipe) {
        ResourceLocation id = recipe == null ? null : recipe.getId();
        return id != null && id.toString().contains(SCOPE_PATH);
    }

    /** 该配方的作用域是否就是（dim, pos, slot）。 */
    public static boolean matchesSource(GTRecipe recipe, ResourceLocation dim, BlockPos pos, int slot) {
        int[] decoded = decodeScope(recipe);
        if (decoded == null || dim == null || pos == null) {
            return false;
        }
        return decoded[0] == dim.hashCode() && decoded[1] == pos.asLong() && decoded[2] == slot;
    }

    /**
     * 解出 {@code [dimHash, posLong, slot]}；不是带标记的配方返回 {@code null}。
     * <p>dim 用 {@link ResourceLocation#hashCode()} 编码（可逆性不重要 ✓ 只用于同一次运行内的比对 ✓）。
     */
    public static int[] decodeScope(GTRecipe recipe) {
        ResourceLocation id = recipe == null ? null : recipe.getId();
        if (id == null) {
            return null;
        }
        String s = id.toString();
        int at = s.indexOf(SCOPE_PATH);
        if (at < 0) {
            return null;
        }
        String tail = s.substring(at + SCOPE_PATH.length());
        String[] parts = tail.split("\\|");
        if (parts.length < 3) {
            return null;
        }
        try {
            return new int[]{parts[0].hashCode(), (int) Long.parseLong(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException bad) {
            return null;
        }
    }
}