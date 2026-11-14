package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtlmodular.GTLModular;

import java.util.List;

/**
 * ★ 2026-11-15：<b>样板类型识别的引擎侧策略</b> —— 「主机表」口径的唯一实现。
 *
 * <h2>为什么要有这个类（问题现象）</h2>
 * 奈亚拉托提普之触这类<b>多类型机器</b>，实际日志出现：
 * <pre>
 * 自动切模式检查: 当前=1/3 状态=IDLE 样板=75 有料槽=[] 未认配方=74 找到目标=-1
 * </pre>
 * 即：样板插着、机器成型、线程仓也装了，却 74/75 认不出配方，机器永久 IDLE；
 * 玩家必须「敲掉控制器再放回去」才会恢复。
 *
 * <h2>根因（两处机器类里的硬编码）</h2>
 * 类型识别采用「两遍扫描」：第一遍跳过主机表、第二遍只看主机表。而两台总成
 * （{@code LazyPatternAssemblyPartMachine} / {@code MoltenPatternAssemblyPartMachine}）
 * 都把「主机表」硬编码成<b>纳芯片的 {@code NanochipRecipeTypes.ASSEMBLY_COMPLEX}</b>：
 * <pre>
 * boolean isHostTable = type == NanochipRecipeTypes.ASSEMBLY_COMPLEX;
 * </pre>
 * 对纳芯片机器没问题，但对「类型表里根本没有 ASSEMBLY_COMPLEX」的机器是致命的：
 * <ul>
 *   <li>第一遍 {@code skipHostTable=true} ⇒ 一张表都不跳过（全参与）；</li>
 *   <li>第二遍 {@code skipHostTable=false} ⇒ 3 张表全都 {@code != ASSEMBLY_COMPLEX}
 *       ⇒ <b>一张都不参与</b> ⇒ 恒 {@code MATCH_NONE}。</li>
 * </ul>
 * 第一遍一旦没有唯一命中，第二遍也救不回来 ⇒ 样板永远认不出 ⇒ 没有配方缓存
 * ⇒ 槽不参与原料查找 ⇒ AE 不推料 ⇒ 永远没料（死锁，只能重成型自救）。
 *
 * <h2>口径（本类 = 唯一真相）</h2>
 * 「主机表」的定义改为：
 * <ol>
 *   <li>类型表里<b>有</b> {@code ASSEMBLY_COMPLEX} ⇒ 用它（<b>纳芯片机器行为逐字不变</b>）；</li>
 *   <li>类型表里<b>没有</b> ⇒ 退化到<b>下标 0</b>（GTO/gtlcore 多类型机器的默认表，
 *       即 {@code getRecipeTypes()[0]}；奈亚的 0 号位就是 {@code suprachronal_assembly_line}）。</li>
 * </ol>
 *
 * <h2>为什么放在引擎包里</h2>
 * 这是<b>机器无关</b>的策略：任何多类型机器都该用同一套「哪张表算主机表」规则。
 * 放在机器类里 ⇒ 每台机器各写一份、各改一份，注定漂移（本次就是两台各写了一份同样的硬编码）。
 * 放引擎后机器只提供「类型表」，规则由引擎统一裁决。
 *
 * <p>纯静态、无状态、任何异常都回落到下标 0 —— 绝不抛异常影响识别主流程。
 */
public final class HostTableRule {

    private HostTableRule() {
    }

    /**
     * 本机类型表里「主机表」的下标。
     *
     * @param types 机器可用配方类型表（可为 null / 空 / 含 null 元素）
     * @return 主机表下标；表不可用 ⇒ {@code -1}（此时两遍扫描都不跳过任何表，退化不致命）
     */
    public static int indexIn(List<GTRecipeType> types) {
        if (types == null || types.isEmpty()) {
            return -1;
        }
        try {
            GTRecipeType host = com.gtlmodular.common.data.nanochip.NanochipRecipeTypes.ASSEMBLY_COMPLEX;
            if (host != null) {
                for (int i = 0; i < types.size(); i++) {
                    if (types.get(i) == host) {
                        return i;
                    }
                }
            }
            // 没有 ASSEMBLY_COMPLEX ⇒ 退化到下标 0（若 0 号位为 null，则取第一个非 null 的）
            for (int i = 0; i < types.size(); i++) {
                if (types.get(i) != null) {
                    return i;
                }
            }
            return -1;
        } catch (Throwable t) {
            GTLModular.LOGGER.debug("[gtlmodular] 主机表下标解析失败，回落下标 0：{}", t.toString());
            return types.isEmpty() ? -1 : 0;
        }
    }

    /**
     * 该下标是不是主机表（供两遍扫描直接判定）。
     *
     * @param hostIndex {@link #indexIn(List)} 的结果
     * @param index     当前遍历到的类型下标
     */
    public static boolean isHost(int hostIndex, int index) {
        return hostIndex >= 0 && hostIndex == index;
    }

    /**
     * 两遍扫描里「本遍是否应跳过该下标」。
     *
     * <p>语义与机器类原有写法逐字一致：
     * <pre>
     * boolean isHostTable = (下标 == 主机表下标);
     * if (isHostTable == skipHostTable) continue;
     * </pre>
     *
     * @param skipHostTable {@code true} = 第一遍（跳过主机表）；{@code false} = 第二遍（只看主机表）
     */
    public static boolean shouldSkip(int hostIndex, int index, boolean skipHostTable) {
        return isHost(hostIndex, index) == skipHostTable;
    }

    /** 主机表的 id（日志用；取不到返回 {@code "?"}）。 */
    public static String nameOf(List<GTRecipeType> types, int hostIndex) {
        try {
            if (types == null || hostIndex < 0 || hostIndex >= types.size()) {
                return "?";
            }
            GTRecipeType type = types.get(hostIndex);
            return type == null || type.registryName == null ? "?" : type.registryName.toString();
        } catch (Throwable t) {
            return "?";
        }
    }

    /**
     * 诊断用：某条配方是否属于给定类型表（第二遍只看主机表时用来核对）。
     *
     * <p>仅日志使用，不参与判定。
     */
    static boolean belongsTo(GTRecipe recipe, List<GTRecipeType> types) {
        if (recipe == null || types == null) {
            return false;
        }
        for (GTRecipeType type : types) {
            if (type != null && recipe.getType() == type) {
                return true;
            }
        }
        return false;
    }
}
