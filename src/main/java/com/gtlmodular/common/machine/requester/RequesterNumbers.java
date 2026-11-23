package com.gtlmodular.common.machine.requester;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * 数字显示/解析工具：<b>超过 10 万就转科学计数法</b>，避免"6400000"这种一堆零难读。
 *
 * <h2>显示规则</h2>
 * <table border="1">
 *   <tr><th>区间</th><th>显示</th></tr>
 *   <tr><td>&lt; 100 000</td><td>原样：{@code 64000}</td></tr>
 *   <tr><td>≥ 100 000</td><td>科学计数法：{@code 1.28e5}、{@code 9.2e18}</td></tr>
 * </table>
 * <p>小写 {@code e} 与 GTCEu/JEI 的观感一致，也比 {@code E} 更好与十六进制区分。
 * 尾数最多保留 2 位小数并去掉无意义的 0（{@code 1.0e6} → {@code 1e6}）。
 *
 * <h2>解析规则</h2>
 * <p>接受三种写法，大小写不敏感：
 * <ul>
 *   <li>纯数字：{@code 64000}</li>
 *   <li>{@code 1.5e6} / {@code 1.5E6}</li>
 *   <li>{@code 1,500,000}（顺手容忍千分位逗号）</li>
 * </ul>
 * 超出 {@link RequesterSlot#MAX_AMOUNT} 一律夹到上限；负数/非法 → 返回 -1（调用方据此忽略这次输入）。
 */
public final class RequesterNumbers {

    /** 超过这个值就切科学计数法。 */
    public static final long SCIENTIFIC_THRESHOLD = 100_000L;

    private RequesterNumbers() {
    }

    /** 展示用文本。 */
    public static String display(long value) {
        if (value < 0L) return "-" + display(-value);
        if (value < SCIENTIFIC_THRESHOLD) return Long.toString(value);
        BigDecimal decimal = BigDecimal.valueOf(value);
        int exponent = decimal.precision() - decimal.scale() - 1;
        BigDecimal mantissa = decimal.movePointLeft(exponent).setScale(2, RoundingMode.DOWN).stripTrailingZeros();
        return mantissa.toPlainString().toLowerCase(Locale.ROOT) + "e" + exponent;
    }

    /**
     * 解析用户输入。
     *
     * @return 夹到 {@code [0, MAX_AMOUNT]} 的值；输入为空/非法时返回 {@code -1}
     */
    public static long parse(String raw) {
        if (raw == null) return -1L;
        String text = raw.trim().replace(",", "").replace(" ", "").toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return -1L;
        try {
            if (text.indexOf('e') >= 0) {
                // 用 BigDecimal 解析，避免 Double 在 9.2e18 附近丢精度
                BigDecimal value = new BigDecimal(text);
                if (value.signum() < 0) return -1L;
                if (value.compareTo(BigDecimal.valueOf(RequesterSlot.MAX_AMOUNT)) >= 0) {
                    return RequesterSlot.MAX_AMOUNT;
                }
                return value.setScale(0, RoundingMode.DOWN).longValueExact();
            }
            long value = Long.parseLong(text);
            if (value < 0L) return -1L;
            return Math.min(value, RequesterSlot.MAX_AMOUNT);
        } catch (NumberFormatException | ArithmeticException ignored) {
            return -1L;
        }
    }

    /** 解析结果是否是一个"用户真的想设的值"（非 -1）。 */
    public static boolean isValid(String raw) {
        return parse(raw) >= 0L;
    }

    /**
     * 流体数量显示（内部一律按 mB 存）。
     *
     * <p>与 gtlcore 的 {@code MEFluidUnits} 同观感：
     * <ul>
     *   <li>{@code < 1000 mB} → {@code 640mB}</li>
     *   <li>{@code >= 1000 mB} → 按桶显示，最多 2 位小数：{@code 1.5B}、{@code 1000B}</li>
     * </ul>
     * 超过 10 万桶同样会落到科学计数法（走 {@link #display(long)}）。
     */
    public static String displayFluid(long amountMb) {
        if (amountMb < 0L) return "-" + displayFluid(-amountMb);
        if (amountMb < RequesterSlot.BUCKET_VOLUME) {
            return display(amountMb) + "mB";
        }
        long buckets = amountMb / RequesterSlot.BUCKET_VOLUME;
        long remainder = amountMb % RequesterSlot.BUCKET_VOLUME;
        if (remainder == 0L) return display(buckets) + "B";
        BigDecimal value = BigDecimal.valueOf(buckets)
                .add(BigDecimal.valueOf(remainder).movePointLeft(3))
                .setScale(2, RoundingMode.DOWN).stripTrailingZeros();
        return value.toPlainString() + "B";
    }

    /** 按槽类型挑显示方式：流体走 mB/B，物品走原数值。 */
    public static String displayFor(long amount, boolean fluid) {
        return fluid ? displayFluid(amount) : display(amount);
    }
}
