package com.gtlmodular.mixin.independent;

import com.gtlmodular.common.machine.independent.persistence.IndependentBatchPersistence;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockItem.class, remap = false)
public abstract class IndependentClonePlacementMixin {
    // Ctrl 复制在 getCloneItemStack 返回后才追加 BlockEntityTag，必须在它进入方块实体之前去掉批次副本。
    // 该清理只是可选保护：目标方法在不同运行环境下可能对不上，缺失时保留原版放置行为，不能让整包启动失败。
    @Inject(method = {
            "updateCustomBlockEntityTag(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Z",
            "m_40582_(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Z"},
            at = @At("HEAD"), require = 1)
    private static void gtlmodular$stripNestedCopy(Level level, Player player, BlockPos pos, ItemStack stack,
                                                  CallbackInfoReturnable<Boolean> cir) {
        var tag = stack.getTag();
        if (tag != null && tag.contains("BlockEntityTag", 10)) {
            tag.getCompound("BlockEntityTag").remove(IndependentBatchPersistence.ROOT_KEY);
        }
    }
}
