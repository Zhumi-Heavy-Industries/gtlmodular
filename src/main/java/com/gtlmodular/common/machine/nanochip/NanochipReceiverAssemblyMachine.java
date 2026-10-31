package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

/**
 * <b>NAC 收件总成</b>（对应 NH 的真空输入端口 VCI）。
 *
 * <p>P1 落了骨架（频率 / 颜色 / 样板槽 / 存档 / 同步 / 界面）；P2 起它是<b>线路的投递目的地</b>：
 * 发件总成每拍把物品按数量平摊塞进本机 16 格缓冲；P3 起主机每 5 秒从这里取料
 * （真件 → CC 发往线路 / CC → 真件推给相邻容器）。
 *
 * <p><b>本机的 16 格缓冲对 GT 配方引擎不可见</b>（{@code getRecipeHandlers()} 返回空，见基类注释）：
 * 否则主机里那套旧引擎会把它当普通输入总线，把料截走当配方输入（0.3.190 实测：251 拍全是收/发 0，
 * 旧引擎在跑 nanochip_conversion 来回倒手）。需要给模块供料时由 P4 显式搬运。
 *
 * <p><b>为什么收件总成不主动"取料"</b>：同一条线可以有多个收件总成，如果每个都主动去发件缓冲里抢，
 * 就会出现重复取/漏取；投递方向统一由发件总成做（规划书 §4.4 第 7 条也是按"发件推"描述的）。
 * 本类因此只需要记「已收到」这一笔账，投递算法在 {@link NanochipSenderAssemblyMachine}。
 */
public class NanochipReceiverAssemblyMachine extends NanochipPortPartMachine {

    public NanochipReceiverAssemblyMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier, IO.IN);
    }
}
