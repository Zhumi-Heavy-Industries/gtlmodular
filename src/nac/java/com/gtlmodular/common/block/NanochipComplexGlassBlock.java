package com.gtlmodular.common.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 剔除相邻同类玻璃的共享面，避免两层半透明纹理重叠；外表面的连接纹理由客户端模型处理。 */
public class NanochipComplexGlassBlock extends Block {

    public NanochipComplexGlassBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        return adjacentState.is(this) || super.skipRendering(state, adjacentState, side);
    }
}
