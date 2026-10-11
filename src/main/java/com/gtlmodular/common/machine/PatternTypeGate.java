package com.gtlmodular.common.machine;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

/**
 * ★ 0.3.89：<b>代理配方表（ProxiedRecipeType）例外判定</b>。
 *
 * <p>取证与来龙去脉见 {@code _census\样板编码类型错配_定位.md} ✓。摘要：
 * GTCEu 的"代理表"（把 NAC 这类表挂到宿主表下使用）里，配方的 {@code recipe.recipeType}
 * 恒为<b>父表</b>（例如 {@code gtceu:circuit_assembler}）✗，而槽位的期望类型是<b>代理表</b>（NAC）✓
 * ⇒ 原来的"引用判等"**恒不成立** ⇒ 一律拒绝 ✗
 * ⇒ 用户现象: 样板放进总成后「AE 里看不到可合成选项」✗ / 材料够却不消费 ✓。
 *
 * <p>策略:
 * <ul>
 *   <li>相等 ⇒ 放行 ✓</li>
 *   <li>否则反射问期望类型"你代理了哪些配方"✓ ⇒ 其中有属于该实际类型的 ⇒ 放行 ✓</li>
 *   <li>取不到/形状不符/异常 ⇒ <b>放行</b>（fail-open ✓ 我们信息不足时不该拦 ✗）</li>
 *   <li>非代理表场景 ⇒ 与原行为一致（相等才放行 ✓ 零漂移 ✓）</li>
 * </ul>
 */
public final class PatternTypeGate {

    private PatternTypeGate() {
    }

    /** 期望类型下是否允许出现 {@code actual} 类型的配方（代理感知 ✓）。 */
    public static boolean allows(GTRecipeType expected, GTRecipeType actual) {
        if (expected == null || actual == null) {
            return true;                 // 信息不足 ⇒ 放行 ✓
        }
        if (actual == expected) {
            return true;                 // 常规: 相等 ✓
        }
        try {
            Object proxies = null;
            for (java.lang.reflect.Method m : expected.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && "getProxyRecipes".equals(m.getName())) {
                    m.setAccessible(true);
                    proxies = m.invoke(expected);
                    break;
                }
            }
            if (proxies instanceof java.util.Collection<?> col) {
                for (Object o : col) {
                    if (o instanceof GTRecipe r && r.recipeType == actual) {
                        return true;     // 代理表里确有该类型的配方 ⇒ 放行 ✓
                    }
                }
                // 代理表存在且里面没有该类型 ⇒ 交给上层原判等（拒绝 ✗）
                return false;
            }
        } catch (Throwable ignored) {
            // 反射失败 ⇒ 放行 ✓
        }
        return true;                     // ★ 保守放行（真正的拦截由"身份闸"那一层负责 ✓）
    }
}