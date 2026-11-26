package com.gtlmodular.common.data;

import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * 与新版 GTL Additions（3.6.2+）共用的结构方块桥接。
 *
 * <p>新版 Add 自带 {@code gtladditions:nanochip_complex_glass}（异星化工复合体用），
 * 本模组不再注册同名玻璃，避免两个 mod 同时提供同一种材质。这里用<b>注册表按 id 取</b>
 * 而不是编译期依赖 Add 的类：既兼容普通版/亚空间版，也避免加载顺序导致的
 * “注册表还没填满 ⇒ 取到 null”问题（谓词在实际结构检查时才解析）。
 */
public final class AddCompatBlocks {

    public static final ResourceLocation NANOCHIP_COMPLEX_GLASS =
            new ResourceLocation("gtladditions", "nanochip_complex_glass");

    private AddCompatBlocks() {
    }

    @Nullable
    public static Block glassBlock() {
        return ForgeRegistries.BLOCKS.getValue(NANOCHIP_COMPLEX_GLASS);
    }

    public static ItemStack glassStack() {
        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(NANOCHIP_COMPLEX_GLASS);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** 结构判定用的延迟谓词：解析发生在结构检查时，而不是机器注册窗口。 */
    public static TraceabilityPredicate glassPredicate() {
        return Predicates.custom(state -> {
            Block block = glassBlock();
            return block != null && state.getBlockState().getBlock() == block;
        }, () -> {
            Block block = glassBlock();
            return block == null ? new BlockInfo[0] : new BlockInfo[]{new BlockInfo(block.defaultBlockState())};
        });
    }
}
