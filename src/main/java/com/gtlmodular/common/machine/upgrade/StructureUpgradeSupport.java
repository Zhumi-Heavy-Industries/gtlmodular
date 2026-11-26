package com.gtlmodular.common.machine.upgrade;

import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gtladd.gtladditions.api.machine.feature.IStructureTierMachine;
import com.gtladd.gtladditions.api.machine.feature.TierCost;
import com.gtladd.gtladditions.api.machine.feature.TierRequirement;
import com.gtladd.gtladditions.api.machine.gui.StructureTierPage;
import com.gtladd.gtladditions.api.pattern.StructureTierLayout;
import com.gtladd.gtladditions.common.machine.trait.StructureTierTrait;
import kotlin.Unit;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * 把 GTO 移植机接入新版 GTL Additions（3.6.2+）「结构升级」的公共零件。
 *
 * <p>升级分两级：1 级是原结构，2 级在原基础上要求若干指定格换成高一级材料，
 * 交齐材料并解锁后即可在侧栏「结构升级」页激活。1 级谓词在基础结构里被替换成
 * {@code any()}（Add 的 {@code StructureTierLayout.base} 行为），所以老存档里
 * 已经成形的结构不会因为这次改动而失形。
 */
public final class StructureUpgradeSupport {

    private StructureUpgradeSupport() {
    }

    /** 按结构切片给「第 2 级专属格」分类；其余格子归 1 级。 */
    public static StructureTierLayout layout(Supplier<BlockPattern> pattern,
                                             Supplier<String[][]> slices,
                                             char tierSymbol) {
        return new StructureTierLayout(1, 2,
                pattern::get,
                (aisle, row, column) -> {
                    String[][] data = slices.get();
                    if (aisle < 0 || aisle >= data.length || row < 0 || row >= data[aisle].length) {
                        return 1;
                    }
                    String line = data[aisle][row];
                    if (column < 0 || column >= line.length()) {
                        return 1;
                    }
                    return line.charAt(column) == tierSymbol ? 2 : 1;
                });
    }

    /** 惰性物品成本：注册表在机器运行期才解析，避免注册窗口取到 null。 */
    public static TierCost cost(String id, long amount) {
        return new TierCost(() -> {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            return item == null ? Ingredient.EMPTY : Ingredient.of(item);
        }, amount);
    }

    public static TierRequirement requirement(TierCost... costs) {
        return new TierRequirement(Arrays.asList(costs), List.of());
    }

    /** Add 的升级页需要这个回调签名；我们不需要额外的结构改动通知。 */
    public static kotlin.jvm.functions.Function1<kotlin.ranges.IntRange, Unit> noOpSync() {
        return range -> Unit.INSTANCE;
    }

    public static void attachTab(TabsWidget tabs, IStructureTierMachine machine) {
        tabs.attachSubTab(new StructureTierPage(machine));
    }

    /** 结构等级提示（与 Add 自家机器的显示口径一致）。 */
    public static Component display(StructureTierTrait trait) {
        return Component.translatable("gtladditions.structure_tier.display",
                trait.getStructureTier(), trait.getLayout().getMaxTier());
    }
}
