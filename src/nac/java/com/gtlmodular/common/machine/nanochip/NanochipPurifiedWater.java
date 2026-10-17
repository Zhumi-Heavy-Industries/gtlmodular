package com.gtlmodular.common.machine.nanochip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * NH 净化水档位表：1~16 级原样保留，每 3 级折成一档，档位同时决定效果类别与基础消耗。
 * 配方加成与耗材扣除必须读同一份选择结果，否则会出现「加成认这份水、扣料认另一份」。
 */
public final class NanochipPurifiedWater {

    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 16;

    /** 必须同时供两种水，且两者属于不同效果类别。 */
    public static final int REQUIRED_WATERS = 2;

    private NanochipPurifiedWater() {
    }

    /** 效果类别：水耗类只改扣水量，耗时类只改配方时长，EU 类只改 EU/t。 */
    public enum Category {
        CONSUMPTION,
        DURATION,
        EU
    }

    /** 档位效果用百分数保存，扣料换算全程整数，避免 0.8 这类二进制小数把向上取整算歪。 */
    public enum Grade {
        THREE(3, 1, 3, Category.CONSUMPTION, 80, 1000),
        FOUR(4, 4, 6, Category.CONSUMPTION, 60, 800),
        FIVE(5, 7, 9, Category.DURATION, 90, 800),
        SIX(6, 10, 12, Category.DURATION, 70, 600),
        SEVEN(7, 13, 15, Category.EU, 90, 600),
        EIGHT(8, 16, 16, Category.EU, 70, 400);

        private final int level;
        private final int minTier;
        private final int maxTier;
        private final Category category;
        private final int effectPercent;
        private final int baseMbPerSecond;

        Grade(int level, int minTier, int maxTier, Category category, int effectPercent, int baseMbPerSecond) {
            this.level = level;
            this.minTier = minTier;
            this.maxTier = maxTier;
            this.category = category;
            this.effectPercent = effectPercent;
            this.baseMbPerSecond = baseMbPerSecond;
        }

        public int level() {
            return level;
        }

        public int minTier() {
            return minTier;
        }

        public int maxTier() {
            return maxTier;
        }

        public Category category() {
            return category;
        }

        public int effectPercent() {
            return effectPercent;
        }

        /** 表中「基础消耗」一列：每 20 tick（= 每秒）的基准扣水量。 */
        public int baseMbPerSecond() {
            return baseMbPerSecond;
        }

        public boolean coversTier(int tier) {
            return tier >= minTier && tier <= maxTier;
        }
    }

    /** 等级到档位的映射函数；表外等级没有档位。 */
    public static Grade gradeOfTier(int tier) {
        for (Grade grade : Grade.values()) {
            if (grade.coversTier(tier)) {
                return grade;
            }
        }
        return null;
    }

    /** 全表最小基础消耗：某一类水一点都没有时，至少要让玩家知道这个量级。 */
    public static int minimumBaseMbPerSecond() {
        int min = Integer.MAX_VALUE;
        for (Grade grade : Grade.values()) {
            min = Math.min(min, grade.baseMbPerSecond());
        }
        return min;
    }

    /** 某一类别手里的水：最高一级决定档位，同类别所有等级的量合起来算可用量。 */
    public record Stock(Grade grade, int highestTier, long availableMb) {
        public Category category() {
            return grade.category();
        }

        public boolean covers() {
            return availableMb >= grade.baseMbPerSecond();
        }
    }

    /** 每 20 tick 实际要扣的（等级，数量）。 */
    public record TierAmount(int tier, long amountMb) {}

    /** 选择结果只由各类别最高一级决定，与流体扫描顺序无关。 */
    public record Selection(List<Stock> stocks) {
        public Selection {
            stocks = List.copyOf(stocks);
        }

        public Stock stockOf(Category category) {
            for (Stock stock : stocks) {
                if (stock.category() == category) {
                    return stock;
                }
            }
            return null;
        }

        public boolean complete() {
            if (stocks.size() != REQUIRED_WATERS) {
                return false;
            }
            for (Stock stock : stocks) {
                if (!stock.covers()) {
                    return false;
                }
            }
            return true;
        }

        /** 只有水耗类的水贡献水耗倍率；没有水耗类水时该倍率 = 1。 */
        public double drainMultiplier(boolean fullCalibration) {
            long[] factor = factor(Category.CONSUMPTION, fullCalibration);
            return (double) factor[0] / (double) factor[1];
        }

        /** 每 20 tick 这份水要扣的整数 mB：自己的基础值 × 两种水的水耗倍率累乘，向上取整。 */
        public long drainMb(Stock stock, boolean fullCalibration) {
            long[] factor = factor(Category.CONSUMPTION, fullCalibration);
            return ceilMb(stock.grade().baseMbPerSecond(), factor[0], factor[1]);
        }

        /** 配方侧加成：只读对应类别的水，满校准时每种水的增益再应用一次（对应倍率平方）。 */
        public double effect(Category category, boolean fullCalibration) {
            long[] factor = factor(category, fullCalibration);
            return (double) factor[0] / (double) factor[1];
        }

        /** 倍率用分数累乘：整数分子分母既没有浮点误差，也让平方与向上取整都有唯一答案。 */
        private long[] factor(Category category, boolean fullCalibration) {
            long numerator = 1L;
            long denominator = 1L;
            for (Stock stock : stocks) {
                if (stock.category() != category) {
                    continue;
                }
                numerator *= stock.grade().effectPercent();
                denominator *= 100L;
            }
            if (fullCalibration) {
                numerator *= numerator;
                denominator *= denominator;
            }
            return new long[] { numerator, denominator };
        }
    }

    /** 每类先取自己最高一级，再在不同类别里取等级最高的两类；同等级按类别序，结果与扫描顺序无关。 */
    public static Selection select(List<Stock> perCategory) {
        List<Stock> candidates = new ArrayList<>(perCategory);
        candidates.sort(Comparator.comparingInt((Stock stock) -> -stock.highestTier())
                .thenComparingInt(stock -> stock.category().ordinal()));
        return new Selection(candidates.subList(0, Math.min(REQUIRED_WATERS, candidates.size())));
    }

    /**
     * perTier 下标即等级（1~16），值为该等级的库存 mB。
     * 按**效果类别**聚成一条：档位取该类里最高一级的档，量按该类所有等级合计。
     * 按档位聚合会让「只喂 1~6 级」的玩家选出两份水耗类水，违反「两种水必须不同类别」。
     */
    public static List<Stock> stocksOf(long[] perTier) {
        List<Stock> stocks = new ArrayList<>();
        for (Category category : Category.values()) {
            long total = 0L;
            Grade highestGrade = null;
            int highestTier = 0;
            for (Grade grade : Grade.values()) {
                if (grade.category() != category) {
                    continue;
                }
                for (int tier = grade.minTier(); tier <= grade.maxTier(); tier++) {
                    long amount = tier < perTier.length ? perTier[tier] : 0L;
                    if (amount <= 0L) {
                        continue;
                    }
                    total = saturatingAdd(total, amount);
                    if (tier > highestTier) {
                        highestTier = tier;
                        highestGrade = grade;
                    }
                }
            }
            if (highestGrade != null) {
                stocks.add(new Stock(highestGrade, highestTier, total));
            }
        }
        return List.copyOf(stocks);
    }

    /** 先从高等级取，取完为止；调用前必须通过 complete()，否则可能拿不满。 */
    public static List<TierAmount> drainPlan(Selection selection, Stock stock, long[] perTier,
                                             boolean fullCalibration) {
        long remaining = selection.drainMb(stock, fullCalibration);
        List<TierAmount> plan = new ArrayList<>();
        // 同一类别可能跨两个档（例如水耗类的三级+四级），扣水要能用到整类的水，不能只看最高档的区间。
        for (int tier = MAX_TIER; tier >= MIN_TIER && remaining > 0L; tier--) {
            Grade grade = gradeOfTier(tier);
            if (grade == null || grade.category() != stock.category()) {
                continue;
            }
            long have = tier < perTier.length ? perTier[tier] : 0L;
            if (have <= 0L) {
                continue;
            }
            long take = Math.min(have, remaining);
            plan.add(new TierAmount(tier, take));
            remaining -= take;
        }
        return List.copyOf(plan);
    }

    /** 基础值 × 分子 / 分母，向上取整为整数 mB。 */
    public static long ceilMb(long baseMb, long numerator, long denominator) {
        return (baseMb * numerator + denominator - 1L) / denominator;
    }

    /** 多个网络库存来源相加可能溢出，饱和到 long 上限以免误判无料。 */
    public static long saturatingAdd(long a, long b) {
        if (b <= 0L) {
            return a;
        }
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }
}
