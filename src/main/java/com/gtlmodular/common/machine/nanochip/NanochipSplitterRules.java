package com.gtlmodular.common.machine.nanochip;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;

/**
 * 纳米组件分流器（Nanopart Splitter）的<b>路由规则表</b>。
 *
 * <p>原版说明（GT5U {@code GT5U.tooltip.nac.module.splitter.*}）：
 * <pre>
 * By default, splits same colored inputs evenly into their respective outputs
 * Behavior can be overwritten by creating 'Rules' to follow in the GUI
 * Rules can be set by input color(s), output color(s), supplied redstone signal, and item filters
 * （Items）will be transferred after all conditions of a Rule are met
 * </pre>
 * 也就是说：默认「同色均分」（移植版里同色自动对接，天然满足），
 * 规则的作用是<b>改道</b>——把某条颜色通道里的某些物品、在满足红石条件时，搬到另一条颜色通道。
 *
 * <p>在本移植的虚拟通道模型里，一条规则 = {输入色 → 输出色，物品过滤，红石阈值}：
 * 分流器每 20 tick 扫一遍规则表，把符合条件的虚拟件（保留 CC/PC 分级）搬过去。
 * 这让玩家能用颜色搭出多级流水线，而不是被颜色数量卡死。
 */
public final class NanochipSplitterRules {

    /** 最多规则条数（界面一页放得下）。 */
    public static final int MAX_RULES = 6;

    /** 单条规则单次搬运上限（防止一条规则把整条通道瞬间抽干）。 */
    public static final int MAX_TRANSFER_PER_RULE = 256;

    /** 一条规则：输入色 → 输出色，可选物品过滤与红石门控。 */
    public static final class Rule {
        private NanochipColor in = NanochipColor.WHITE;
        private NanochipColor out = NanochipColor.ORANGE;
        /** 物品过滤：物品注册名子串，空 = 不限。 */
        private String itemFilter = "";
        /** 红石阈值（0 = 不要求；>0 = 控制器处的红石信号必须 ≥ 该值）。 */
        private int redstoneThreshold;
        private boolean enabled = true;

        public NanochipColor in() {
            return in;
        }

        public NanochipColor out() {
            return out;
        }

        public String itemFilter() {
            return itemFilter;
        }

        public int redstoneThreshold() {
            return redstoneThreshold;
        }

        public boolean enabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void cycleIn() {
            in = in.next();
        }

        public void cycleOut() {
            out = out.next();
        }

        public void setFilter(String itemFilter) {
            this.itemFilter = itemFilter == null ? "" : itemFilter;
        }

        public void setRedstoneThreshold(int threshold) {
            this.redstoneThreshold = Math.max(0, Math.min(15, threshold));
        }

        /** 规则是否真的会搬东西（输入输出同色 = 空转，忽略）。 */
        public boolean effective() {
            return enabled && in != out;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("In", in.ordinal());
            tag.putInt("Out", out.ordinal());
            tag.putString("Filter", itemFilter);
            tag.putInt("Redstone", redstoneThreshold);
            tag.putBoolean("Enabled", enabled);
            return tag;
        }

        static Rule load(CompoundTag tag) {
            Rule rule = new Rule();
            // ★ 2026-09-17：NanochipColor.fromIndex() 对负数返回 null（= 未染色），
            //   而分流规则的两端**必须**是一个具体颜色 ⇒ 用 fromIndexOr 兜底成白色。
            rule.in = NanochipColor.fromIndexOr(tag.getInt("In"), NanochipColor.WHITE);
            rule.out = NanochipColor.fromIndexOr(tag.getInt("Out"), NanochipColor.WHITE);
            rule.itemFilter = tag.getString("Filter");
            rule.redstoneThreshold = Math.max(0, Math.min(15, tag.getInt("Redstone")));
            rule.enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
            return rule;
        }
    }

    private final List<Rule> rules = new ArrayList<>();

    public List<Rule> rules() {
        return rules;
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /** 添加一条规则（超上限返回 false）。 */
    public boolean addRule() {
        if (rules.size() >= MAX_RULES) {
            return false;
        }
        Rule rule = new Rule();
        // 默认给一条「白 → 橙」，玩家自己改；同色不搬，所以不会误动作
        rules.add(rule);
        return true;
    }

    public void removeRule(int index) {
        if (index >= 0 && index < rules.size()) {
            rules.remove(index);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Rule rule : rules) {
            list.add(rule.save());
        }
        tag.put("Rules", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        rules.clear();
        ListTag list = tag.getList("Rules", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && rules.size() < MAX_RULES; i++) {
            rules.add(Rule.load(list.getCompound(i)));
        }
    }
}
