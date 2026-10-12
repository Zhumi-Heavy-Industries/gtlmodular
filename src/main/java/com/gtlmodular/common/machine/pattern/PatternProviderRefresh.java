package com.gtlmodular.common.machine.pattern;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;

import java.util.function.Supplier;

/** 刷新会同步重读样板，必须挡住重读触发的嵌套刷新。 */
public final class PatternProviderRefresh {

    private boolean refreshing;

    public void refresh(Supplier<IManagedGridNode> prepareNode) {
        if (refreshing) {
            return;
        }
        refreshing = true;
        try {
            IManagedGridNode mainNode = prepareNode.get();
            IGrid grid = mainNode.getGrid();
            if (grid != null) {
                ICraftingService crafting = grid.getCraftingService();
                IGridNode node = mainNode.getNode();
                if (crafting != null && node != null) {
                    crafting.refreshNodeCraftingProvider(node);
                }
            }
            ICraftingProvider.requestUpdate(mainNode);
        } finally {
            refreshing = false;
        }
    }
}
