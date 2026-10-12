package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.machine.nanochip.vacuum.NanochipSplitterRuleBook;

import net.minecraft.nbt.CompoundTag;

import java.util.List;

public final class NanochipSplitterRules {

    /** 规则数量受单页界面容量限制。 */
    public static final int MAX_RULES = 6;

    /** 限制每条规则的搬运量，避免单条规则立即抽干整个通道。 */
    public static final int MAX_TRANSFER_PER_RULE = 256;

    public static final class Rule {
        private NanochipColor in = NanochipColor.WHITE;
        private NanochipColor out = NanochipColor.ORANGE;
        private String itemFilter = "";
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
            // 规则两端必须有实际颜色，存档中的负序号不能沿用未染色的 null 语义。
            rule.in = NanochipColor.fromIndexOr(tag.getInt("In"), NanochipColor.WHITE);
            rule.out = NanochipColor.fromIndexOr(tag.getInt("Out"), NanochipColor.WHITE);
            rule.itemFilter = tag.getString("Filter");
            rule.redstoneThreshold = Math.max(0, Math.min(15, tag.getInt("Redstone")));
            rule.enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
            return rule;
        }
    }

    private final NanochipSplitterRuleBook ruleBook = new NanochipSplitterRuleBook(Rule::save, Rule::load);

    public List<Rule> rules() {
        return ruleBook.rules();
    }

    public boolean isEmpty() {
        return ruleBook.isEmpty();
    }

    public boolean addRule() {
        return ruleBook.addRule();
    }

    public void removeRule(int index) {
        ruleBook.removeRule(index);
    }

    public CompoundTag save() {
        return ruleBook.save();
    }

    public void load(CompoundTag tag) {
        ruleBook.load(tag);
    }
}
