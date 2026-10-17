package com.gtlmodular.common.machine.pattern.ui;

import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;

/**
 * 催化剂页与宿主机器之间的窄接口：页面只认"第几号样板槽"，不关心宿主那边是定长数组表还是惰性表。
 *
 * <p>这样超级猪咪与惰性猪咪共用同一套页面同步，切槽时只换代理指向，不再重建槽控件。</p>
 */
public interface CatalystSlotAccess {

    boolean isRemote();

    /** 页收起时槽控件仍留在菜单里，必须拒绝放入，避免物品被快捷键塞进看不见的槽。 */
    boolean isCatalystPageOpen();

    /** 服务端取该槽（0 基）的催化剂物品仓；惰性机在这里按需建仓。 */
    IItemTransfer catalystItemTransfer(int slot);

    /** 服务端取该槽（0 基）的催化剂流体仓；惰性机在这里按需建仓。 */
    FluidTransferList catalystFluidTransfer(int slot);

    /** 真仓的 setStackInSlot 不触发内容回调，代理写入后必须补一次重算。 */
    void onCatalystItemChanged(int slot);

    void onCatalystFluidChanged(int slot);

    int catalystCacheCount(int slot);

    /** 页内"每槽缓存阈值"输入框写的是页缓冲，改动要回填正规表并作废配方缓存。 */
    void onCatalystCacheCountChanged(int slot, int value);
}
