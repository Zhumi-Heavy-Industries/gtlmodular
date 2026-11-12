package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * ★ <b>加速仓</b>（{@code gtlmodular:accelerate_hatch_*}）—— 无条件加快所在多方块的运行速度。
 *
 * <p>对应 GTO {@code gtocore:accelerate_hatch_*}：贴在多方块外壳上，
 * 把每条配方的耗时按「百分比」缩短。与超频仓不同，它<b>不看超频档数、不受电压墙限制</b>，
 * 只要仓等级不低于配方等级就一定生效。
 *
 * <h2>数值口径（对齐 GTO）</h2>
 * <pre>
 *   reduction = 玩家设定百分比（范围 [最低%, 100%]，越低越快）
 *   t = 配方电压档 - 本仓电压档 *   if (t &gt; 0) reduction = min(100, reduction + 20 × t)   // 仓每低一级，加速效果 -20%
 *   duration = max(1, duration × reduction / 100)
 * </pre>
 * 最低百分比 = {@code 52 - 2×tier}（LV=50% → MAX=24%）。默认取最低（最快）。
 *
 * <h2>与批处理叠加</h2>
 * <p>批处理（{@code accurateParallel(modifyDuration=false)}）只放大每批数量、不改时长；
 * 本仓压缩批次绝对时长 ⇒ 吞吐 = 批处理份数 / (时长 × reduction)。两者正交，纯增益。
 *
 * <h2>能耗</h2>
 * <p>本仓不额外耗电（GTO 同口径：加速是"无条件"的，不额外计费）。
 *
 * @see IMultiPart#modifyRecipe(GTRecipe)
 * @author 小爱与大肥鱼
 */
public class AccelerateHatchPartMachine extends TieredPartMachine implements IMultiPart {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(AccelerateHatchPartMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /** 存档键（追加键，老存档没有就回落默认值）。 */
    private static final String TAG_PERCENTAGE = "GTLModularAcceleratePercentage";

    /** 当前设定的时长百分比（[min, 100]，越低越快）。服务端权威，客户端只读。 */
    @DescSynced
    private int percentage = -1;

    public AccelerateHatchPartMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier);
        this.percentage = minPercentage(tier);
    }

    // ------------------------------------------------------------------
    // 档位数值
    // ------------------------------------------------------------------

    /** 本档允许的最快百分比（GTO 口径 {@code 52 - 2×tier}），夹到 ≥1。 */
    public static int minPercentage(int tier) {
        return Math.max(1, 52 - 2 * tier);
    }

    /** 本档的最快百分比。 */
    public int getMinPercentage() {
        return minPercentage(getTier());
    }

    /** 当前设定百分比；未设定 / 越界时夹回 {@code [min, 100]}。 */
    public int getPercentage() {
        int p = percentage < 0 ? getMinPercentage() : percentage;
        return Math.max(getMinPercentage(), Math.min(100, p));
    }

    public void setPercentage(int value) {
        this.percentage = Math.max(getMinPercentage(), Math.min(100, value));
        markDirty();
    }

    // ------------------------------------------------------------------
    // 核心：改配方时长
    // ------------------------------------------------------------------

    /**
     * 按百分比缩短配方耗时。这是本仓接入多方块的唯一入口
     * （{@code WorkableMultiblockMachine#doModifyRecipe} 会遍历所有部件调它）。
     *
     * <p>只有能工作于该配方的机器才改（{@link TieredPartMachine} 不区分机器类型，
     * 这里通过 {@code recipe.tier} 与自身 tier 比对来做低等级惩罚）。
     */
    @Override
    public GTRecipe modifyRecipe(@NotNull GTRecipe recipe) {
        int reduction = getPercentage();
        int t = com.gregtechceu.gtceu.api.recipe.RecipeHelper.getRecipeEUtTier(recipe) - getTier();
        if (t > 0) {
            reduction = Math.min(100, reduction + 20 * t);
        }
        recipe.duration = Math.max(1, recipe.duration * reduction / 100);
        return recipe;
    }

    // ------------------------------------------------------------------
    // 界面：主页一个百分比输入框
    // ------------------------------------------------------------------

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 130, 40);
        group.addWidget(new LabelWidget(6, 4,
                Component.translatable("gtlmodular.accelerate_hatch.config.title").getString()));
        group.addWidget(new IntInputWidget(6, 18, 118, 18,
                this::getPercentage, this::setPercentage)
                .setMin(getMinPercentage()).setMax(100));
        return group;
    }

/** 与 GTO 对齐：加速仓<b>不可被多台多方块共享</b>（GTO tooltip 明写「可共享：✗」）。 */
    @Override
    public boolean canShared() {
        return false;
    }

    @Override
    public void addMultiText(List<Component> textList) {
        textList.add(Component.translatable("gtlmodular.accelerate_hatch.tooltip", getPercentage())
                .withStyle(ChatFormatting.AQUA));
    }
}
