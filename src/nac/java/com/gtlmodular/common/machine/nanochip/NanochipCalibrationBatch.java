package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 合批会丢失原配方身份，来源须随实际运行配方保存，才能在完成或读档后按白名单记账。 */
final class NanochipCalibrationBatch {

    private static final String SOURCES = "gtlmodular:calibration_sources";
    private final List<Production> pending = new ArrayList<>();

    record Production(ResourceLocation recipeId, NanochipCalibration.Family family, int amount) {}

    void capture(GTRecipe recipe) {
        if (recipe == null || recipe.getId() == null) return;
        NanochipCalibration.Family family = NanochipCalibrationAccounting.familyForAccounting(recipe);
        for (Content output : recipe.getOutputContents(ItemRecipeCapability.CAP)) {
            ItemStack stack = NanochipCalibrationAccounting.firstItemStackOf(output.content);
            if (stack == null || stack.isEmpty()) continue;
            // 成功扣料入口传入已缩放并结算概率的产出，不能再乘 realParallels。
            capture(recipe.getId(), family, NanochipCalibrationAccounting.producedCountOf(output.content));
        }
    }

    void capture(ResourceLocation recipeId, NanochipCalibration.Family family, long producedCount) {
        if (recipeId != null && family != null && producedCount > 0L) {
            pending.add(new Production(recipeId, family, (int) Math.min(Integer.MAX_VALUE, producedCount)));
        }
    }

    void attach(GTRecipe batch) {
        if (batch != null) batch.data = attach(batch.data);
        else clear();
    }

    CompoundTag attach(CompoundTag data) {
        if (!pending.isEmpty()) {
            // 不改共享的配方数据；新增来源随上游已有 lastRecipe CODEC 一起存取。
            data = data.copy();
            data.put(SOURCES, encode(pending));
        }
        clear();
        return data;
    }

    void clear() {
        pending.clear();
    }

    static boolean hasSources(GTRecipe batch) {
        return batch.data.contains(SOURCES, Tag.TAG_LIST);
    }

    static List<Production> sources(GTRecipe batch) {
        return decode(batch.data.getList(SOURCES, Tag.TAG_COMPOUND));
    }

    static ListTag encode(List<Production> productions) {
        ListTag list = new ListTag();
        for (Production production : productions) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", production.recipeId().toString());
            entry.putString("family", production.family().name());
            entry.putInt("amount", production.amount());
            list.add(entry);
        }
        return list;
    }

    static List<Production> decode(ListTag list) {
        List<Production> productions = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            int amount = entry.getInt("amount");
            if (id == null || amount <= 0) continue;
            try {
                productions.add(new Production(id,
                        NanochipCalibration.Family.valueOf(entry.getString("family")), amount));
            } catch (IllegalArgumentException ignored) {
                // 损坏的来源不能猜族或制造校准数量。
            }
        }
        return productions;
    }
}
