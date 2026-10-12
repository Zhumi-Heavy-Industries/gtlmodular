package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

/** 用逐条环形历史决定校准，派生数量缓存只用于加速查询，避免另建一份校准事实源。 */
public final class NanochipCalibration {

    public static final int BATCH_SIZE = 1000;

    public static final int BATCH_COUNT = 100;

    public static final int HISTORY_SIZE = BATCH_SIZE * BATCH_COUNT;

    public enum Family {

        PRIMITIVE("primitives", "基础电路", 0.0D, 0.0D, 0.0D),

        CRYSTAL("crystals", "晶体电路", 0.20D, 0.40D, 0.60D),

        WETWARE("wetwares", "湿件电路", 0.20D, 0.40D, 0.60D),

        BIOWARE("bios", "生物活性电路", 0.20D, 0.40D, 0.60D),

        OPTICAL("opticals", "光学电路", 0.20D, 0.40D, 0.60D),

        EXOTIC("exotics", "奇异电路", 0.01D, 0.05D, 0.20D),

        COSMIC("cosmics", "寰宇电路", 0.01D, 0.05D, 0.20D),

        TEMPORAL("temporals", "超因果电路", 0.01D, 0.05D, 0.20D),

        /** 保留 SPECIAL 的枚举位置以兼容 ordinal 存档；显示与生效判定通过 mergedAway 排除它。 */
        SPECIAL("specials", "特殊电路", 0.01D, 0.05D, 0.20D);

        private final String key;
        private final String nameZh;
        private final double t1;
        private final double t2;
        private final double t3;

        Family(String key, String nameZh, double t1, double t2, double t3) {
            this.key = key;
            this.nameZh = nameZh;
            this.t1 = t1;
            this.t2 = t2;
            this.t3 = t3;
        }

        public String key() {
            return key;
        }

        public String nameZh() {
            return nameZh;
        }

        public boolean calibratable() {
            return t1 > 0.0D;
        }

        public double threshold(int tier) {
            return switch (tier) {
                case 1 -> t1;
                case 2 -> t2;
                case 3 -> t3;
                default -> 0.0D;
            };
        }

        public boolean mergedAway() {
            return this == SPECIAL;
        }

        public boolean specialStyleEffects() {
            return this == SPECIAL || this == EXOTIC || this == COSMIC || this == TEMPORAL;
        }

        public static Family[] barFamilies() {
            Family[] all = values();
            int n = 0;
            for (Family f : all) {
                if (!f.mergedAway()) {
                    n++;
                }
            }
            Family[] out = new Family[n];
            int i = 0;
            for (Family f : all) {
                if (!f.mergedAway()) {
                    out[i++] = f;
                }
            }
            return out;
        }

        public static Family of(ResourceLocation id) {
            if (id == null) {
                return PRIMITIVE;
            }
            String p = id.getPath().toLowerCase(Locale.ROOT);
            if (p.contains("pico") || p.contains("quantum")) {
                return TEMPORAL;
            }
            if (p.contains("wetware")) {
                return WETWARE;
            }
            if (p.contains("bioware") || p.contains("bio_processor") || p.contains("bioprocessor")) {
                return BIOWARE;
            }
            if (p.contains("optical")) {
                return OPTICAL;
            }
            if (p.contains("crystal")) {
                return CRYSTAL;
            }
            if (p.contains("exotic")) {
                return EXOTIC;
            }
            if (p.contains("cosmic")) {
                return COSMIC;
            }
            if (p.contains("temporal") || p.contains("suprachronal") || p.contains("spacetime")
                    || p.contains("supracausal")) {
                return TEMPORAL;
            }
            return PRIMITIVE;
        }

        public static Family of(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return PRIMITIVE;
            }
            return of(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        }

        public int color() {
            return switch (this) {
                case PRIMITIVE -> 0x9E9E9E;
                case CRYSTAL -> 0x7FE7FF;
                case WETWARE -> 0x8AE234;
                case BIOWARE -> 0xFF8FD8;
                case OPTICAL -> 0xFFD54F;
                case EXOTIC -> 0xB388FF;
                case COSMIC -> 0x536DFE;
                case TEMPORAL -> 0xFF6E40;
                case SPECIAL -> 0xFFFFFF;
            };
        }
    }

    /** 按完整配方 ID 或末尾星号前缀匹配，避免仅凭产物族误计入校准。 */
    public static boolean whitelistMatches(ResourceLocation id, Collection<String> entries) {
        if (id == null || entries == null || entries.isEmpty()) {
            return false;
        }
        String full = id.toString();
        for (String entry : entries) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }
            if (entry.endsWith("*")) {
                if (full.startsWith(entry.substring(0, entry.length() - 1))) {
                    return true;
                }
            } else if (full.equals(entry)) {
                return true;
            }
        }
        return false;
    }

    private final byte[] ring = new byte[HISTORY_SIZE];

    private int ringPos;

    private int totalAll;

    private int recorded;

    private final int[] familyTotals = new int[Family.values().length];

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private void recountFamilyTotals() {
        Arrays.fill(familyTotals, 0);
        int valid = clamp(totalAll, 0, HISTORY_SIZE);
        for (int i = 0; i < valid; i++) {
            familyTotals[ring[i]]++;
        }
        totalAll = valid;
    }

    private static final int RLE_SHIFT = 4;

    private static final int RLE_ORD_MASK = (1 << RLE_SHIFT) - 1;

    private int runEnd(int from) {
        byte v = ring[from];
        int j = from + 1;
        while (j < HISTORY_SIZE && ring[j] == v) {
            j++;
        }
        return j;
    }

    /** 同族连续记录较多，游程编码可缩小 NBT，同时保留逐条历史顺序。 */
    private int[] encodeRingRle() {
        int runs = 0;
        for (int i = 0; i < HISTORY_SIZE; i = runEnd(i)) {
            runs++;
        }
        int[] out = new int[runs];
        int n = 0;
        for (int i = 0; i < HISTORY_SIZE; i = runEnd(i)) {
            out[n++] = ((runEnd(i) - i) << RLE_SHIFT) | (ring[i] & RLE_ORD_MASK);
        }
        return out;
    }

    private boolean decodeRingRle(int[] rle) {
        if (rle == null || rle.length == 0) {
            return false;
        }
        int pos = 0;
        for (int packed : rle) {
            int len = packed >>> RLE_SHIFT;
            int ord = packed & RLE_ORD_MASK;
            // NBT 是外部输入，长度与族序号必须检查，避免坏档造成数组越界。
            if (len <= 0 || ord >= Family.values().length || pos + len > HISTORY_SIZE) {
                return false;
            }
            Arrays.fill(ring, pos, pos + len, (byte) ord);
            pos += len;
        }
        return pos == HISTORY_SIZE;
    }

    /** 旧批次档只保留族计数，无法还原批内先后顺序；迁移按保存的批次顺序展开，批内顺序是近似值。 */
    private void migrateLegacyBatches(CompoundTag tag) {
        int written = 0;
        for (int b = 0; b < BATCH_COUNT && written < HISTORY_SIZE; b++) {
            String key = "B" + b;
            if (!tag.contains(key)) {
                continue;
            }
            int[] arr = tag.getIntArray(key);
            int n = Math.min(arr.length, Family.values().length);
            for (int ord = 0; ord < n && written < HISTORY_SIZE; ord++) {
                int c = clamp(arr[ord], 0, HISTORY_SIZE - written);
                if (c <= 0) {
                    continue;
                }
                Arrays.fill(ring, written, written + c, (byte) ord);
                written += c;
            }
        }
        totalAll = written;
        ringPos = written % HISTORY_SIZE;
        recountFamilyTotals();
    }

    public void record(Family family) {
        record(family, 1);
    }

    public void record(Family family, int amount) {
        if (family == null || amount <= 0) {
            return;
        }
        // 先转 long 再累加，避免超大产出把已记录数溢出成负值。
        recorded = (int) Math.min((long) HISTORY_SIZE, (long) recorded + (long) amount);
        final int ord = family.ordinal();
        // 单次产出覆盖整窗时直接填满，避免循环次数随巨量并行增长。
        if (amount >= HISTORY_SIZE) {
            Arrays.fill(ring, (byte) ord);
            Arrays.fill(familyTotals, 0);
            familyTotals[ord] = HISTORY_SIZE;
            totalAll = HISTORY_SIZE;
            ringPos = 0;
            return;
        }
        int remaining = amount;
        int space = HISTORY_SIZE - totalAll;
        if (space > 0) {
            int put = Math.min(space, remaining);
            int span = Math.min(put, HISTORY_SIZE - ringPos);
            Arrays.fill(ring, ringPos, ringPos + span, (byte) ord);
            if (put > span) {
                Arrays.fill(ring, 0, put - span, (byte) ord);
            }
            familyTotals[ord] += put;
            totalAll += put;
            ringPos = (ringPos + put) % HISTORY_SIZE;
            remaining -= put;
        }
        while (remaining > 0) {
            int span = Math.min(remaining, HISTORY_SIZE - ringPos);
            for (int i = 0; i < span; i++) {
                familyTotals[ring[ringPos + i]]--;
            }
            Arrays.fill(ring, ringPos, ringPos + span, (byte) ord);
            familyTotals[ord] += span;
            ringPos = (ringPos + span) % HISTORY_SIZE;
            remaining -= span;
        }
    }

    public int countOf(Family family) {
        return familyTotals[family.ordinal()];
    }

    public int total() {
        return totalAll;
    }

    public static boolean useFullWindowDenominator() {
        return NanochipModContext.config().isNanochipCalibrationUseFullWindow();
    }

    /** 显示端和服务端共用分母计算，避免百分比与档位判定不一致。 */
    public static double denominatorFor(long historyTotal, boolean fullWindow) {
        return fullWindow ? (double) HISTORY_SIZE : (double) Math.max(0L, historyTotal);
    }

    public double denominator() {
        return denominatorFor(total(), useFullWindowDenominator());
    }

    public static String denominatorModeKey(boolean fullWindow) {
        return fullWindow
                ? "gtlmodular.nanochip.calibration.denominator.vanilla"
                : "gtlmodular.nanochip.calibration.denominator.recorded";
    }

    public double percentage(Family family) {
        double den = denominator();
        return den <= 0.0D ? 0.0D : countOf(family) / den;
    }

    public int tierOf(Family family) {
        if (!family.calibratable()) {
            return 0;
        }
        double pct = percentage(family);
        int tier = 0;
        for (int t = 1; t <= 3; t++) {
            if (family.threshold(t) > 0.0D && pct >= family.threshold(t)) {
                tier = t;
            }
        }
        return tier;
    }

    public Family activeFamily() {
        Family best = null;
        int bestTier = 0;
        int bestCount = -1;
        for (Family f : Family.values()) {
            if (f.mergedAway()) {
                continue;
            }
            int t = tierOf(f);
            if (t <= 0) {
                continue;
            }
            int c = countOf(f);
            if (t > bestTier || (t == bestTier && c > bestCount)) {
                best = f;
                bestTier = t;
                bestCount = c;
            }
        }
        return best;
    }

    public int activeTier() {
        Family f = activeFamily();
        return f == null ? 0 : tierOf(f);
    }

    public double euMultiplier() {
        Family active = activeFamily();
        if (active == null || active == Family.SPECIAL || active.specialStyleEffects()) {
            return 1.0D;
        }
        return tierOf(active) >= 1 ? 0.8D : 1.0D;
    }

    public double durationMultiplier(Family recipeFamily) {
        Family active = activeFamily();
        if (active == null || recipeFamily == null || active != recipeFamily) {
            return 1.0D;
        }
        int tier = tierOf(active);
        if (active == Family.SPECIAL || active.specialStyleEffects()) {
            return switch (tier) {
                case 1 -> 0.80D;
                case 2 -> 0.60D;
                case 3 -> 0.50D;
                default -> 1.0D;
            };
        }
        return tier >= 2 ? 0.8D : 1.0D;
    }

    public boolean specialActive(Family family) {
        return activeFamily() == family && tierOf(family) >= 3;
    }

    public String summary() {
        Family active = activeFamily();
        if (active == null) {
            return "未校准 · 历史 " + total() + " / " + HISTORY_SIZE + " 个电路";
        }
        return active.nameZh() + " 第 " + tierOf(active) + " 档 · 占比 "
                + String.format(Locale.ROOT, "%.2f", percentage(active) * 100.0D) + "%";
    }

    public String breakdown() {
        StringBuilder sb = new StringBuilder();
        for (Family f : Family.values()) {
            if (f.mergedAway()) {
                continue;
            }
            int c = countOf(f);
            if (c <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("｜");
            }
            sb.append(f.nameZh()).append(' ')
                    .append(String.format(Locale.ROOT, "%.2f", percentage(f) * 100.0D)).append('%');
        }
        return sb.toString();
    }

    public double batchProgress() {
        return (ringPos % BATCH_SIZE) / (double) BATCH_SIZE;
    }

    public int clear() {
        int before = totalAll;
        // ring 是事实源，重置不能只清派生计数，否则读档重算会恢复旧历史。
        Arrays.fill(ring, (byte) 0);
        Arrays.fill(familyTotals, 0);
        totalAll = 0;
        recorded = 0;
        ringPos = 0;
        return before;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Cursor", ringPos);
        tag.putInt("Fill", ringPos % BATCH_SIZE);
        tag.putInt("Recorded", recorded);
        tag.putInt("Total", totalAll);
        tag.putIntArray("RingRLE", encodeRingRle());
        return tag;
    }

    public void load(CompoundTag tag) {
        Arrays.fill(ring, (byte) 0);
        Arrays.fill(familyTotals, 0);
        totalAll = 0;
        ringPos = 0;
        recorded = 0;

        recorded = clamp(tag.getInt("Recorded"), 0, HISTORY_SIZE);
        if (tag.contains("RingRLE")) {
            if (decodeRingRle(tag.getIntArray("RingRLE"))) {
                totalAll = clamp(tag.getInt("Total"), 0, HISTORY_SIZE);
                ringPos = clamp(tag.getInt("Cursor"), 0, HISTORY_SIZE - 1);
                // 未满窗口的有效区从零连续排列，写入点必须跟随有效记录数。
                if (totalAll < HISTORY_SIZE) {
                    ringPos = totalAll;
                }
                recountFamilyTotals();
            }
            // 解码失败不信任 Total 和 Cursor，保留空窗口以免传播损坏状态。
            return;
        }
        migrateLegacyBatches(tag);
    }
}
