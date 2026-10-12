package com.gtlmodular.common.machine.nanochip.vacuum;

import com.gtlmodular.common.machine.nanochip.NanochipSplitterRules.Rule;
import static com.gtlmodular.common.machine.nanochip.NanochipSplitterRules.MAX_RULES;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class NanochipSplitterRuleBook {

    private final Function<Rule, CompoundTag> saveRule;
    private final Function<CompoundTag, Rule> loadRule;

    public NanochipSplitterRuleBook(Function<Rule, CompoundTag> saveRule,
                                   Function<CompoundTag, Rule> loadRule) {
        this.saveRule = saveRule;
        this.loadRule = loadRule;
    }

    private final List<Rule> rules = new ArrayList<>();

    public List<Rule> rules() {
        return rules;
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public boolean addRule() {
        if (rules.size() >= MAX_RULES) {
            return false;
        }
        Rule rule = new Rule();
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
            list.add(saveRule.apply(rule));
        }
        tag.put("Rules", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        rules.clear();
        ListTag list = tag.getList("Rules", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && rules.size() < MAX_RULES; i++) {
            rules.add(loadRule.apply(list.getCompound(i)));
        }
    }
}
