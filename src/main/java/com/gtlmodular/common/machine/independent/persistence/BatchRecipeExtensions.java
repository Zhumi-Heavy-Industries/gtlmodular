package com.gtlmodular.common.machine.independent.persistence;

import java.math.BigInteger;
import java.util.Objects;

public record BatchRecipeExtensions(Kind kind, Power power, long realParallels, int parallels, int batchSize,
                                    int ocTier, int euTier, boolean batchProcessed, boolean subTickParallelized,
                                    boolean hasTick, BigInteger wirelessEu) {
    public enum Kind { NORMAL, WIRELESS }
    public enum Power { WIRED, WIRELESS_ACCOUNT, WIRELESS_TERMINAL }

    public BatchRecipeExtensions {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(power);
        if (realParallels < 1 || parallels < 1 || batchSize < 1 || ocTier < 0 || euTier < 0) {
            throw new IllegalArgumentException("Invalid recipe extensions");
        }
        if (kind == Kind.NORMAL && wirelessEu != null) throw new IllegalArgumentException("Wireless energy on a normal recipe");
        if (power != Power.WIRED && (kind != Kind.WIRELESS || wirelessEu == null)) {
            throw new IllegalArgumentException("Missing wireless energy");
        }
        if (power == Power.WIRED && wirelessEu != null) throw new IllegalArgumentException("Wireless recipe needs a wireless power route");
    }

    public static BigInteger parseWireless(String value) {
        // 限制损坏输入的解析成本；零是有效数值，缺失不能以零代替。
        if (value == null || value.length() > 4096 || !value.matches("-?(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException("Invalid wireless energy");
        }
        BigInteger result = new BigInteger(value);
        if (!result.toString().equals(value)) throw new IllegalArgumentException("Non-canonical wireless energy");
        return result;
    }
}
