package com.gtlmodular.client.renderer;

import com.gregtechceu.gtceu.client.renderer.machine.WorkableCasingMachineRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * ★ 2026-11-10：<b>让控制器外壳吃 LDLib 连接纹理</b>（队长「纹理连接有问题，他们没有成型为一体」）。
 *
 * <h2>为什么需要这个子类</h2>
 * <p>GTCEu 的 {@link WorkableCasingMachineRenderer} 只画外壳 + 正面覆层，<b>默认不重烘焙 CTM</b>：
 * LDLib 的 {@code RendererBakedModel#getQuads} 里有一句
 * <pre>if (renderer.reBakeCustomQuads() &amp;&amp; state != null &amp;&amp; world != null &amp;&amp; pos != null)
 *     return CustomBakedModel.reBakeCustomQuads(quads, world, pos, state, side, renderer.reBakeCustomQuadsOffset());</pre>
 * 而 {@code IRenderer.reBakeCustomQuads()} 默认 {@code false} ⇒ 机器外壳的 quad 永远不经过
 * {@code CustomBakedModel}，也就不会按贴图 {@code .mcmeta} 里的 {@code ldlib.connection} 换成连接变体。
 *
 * <h2>打开之后</h2>
 * <ul>
 *   <li>控制器外壳贴图 {@code gtlcore:block/cold_ice_casing} 在资源包
 *       {@code GToTextureBackportGTLv3.0coldfix} 里带 {@code cold_ice_casing.png.mcmeta}
 *       （{@code "ldlib": {"connection": "gtlcore:block/cold_ice_casing_ctm"}}）⇒ 重烘焙后会取
 *       {@code cold_ice_casing_ctm} 的 47 格变体；</li>
 *   <li>邻接判定走 {@code MachineRenderer#isConnected}（外观方块 = 寒冰机械方块）⇒ 与塔身相邻的
 *       四个侧面判定为「连通」，图案连续，控制器与塔身连成一片；</li>
 *   <li>正面覆层（{@code multiblock/vacuum_freezer}）没有连接数据，重烘焙时原样保留（只带
 *       {@code reBakeCustomQuadsOffset()} 的微量偏移，避免 z-fighting）。</li>
 * </ul>
 *
 * <p>只用于本模组这一台机器；不动 GTCEu 的全局渲染器。
 */
@OnlyIn(Dist.CLIENT)
public class CoolingTowerCasingRenderer extends WorkableCasingMachineRenderer {

    public CoolingTowerCasingRenderer(ResourceLocation baseCasing, ResourceLocation overlayModel) {
        super(baseCasing, overlayModel);
    }

    /** 打开 LDLib 的「按贴图 mcmeta 重烘焙连接纹理」通路（默认 false）。 */
    @Override
    public boolean reBakeCustomQuads() {
        return true;
    }
}
