package com.gtlmodular.common.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <b>复合体玻璃（Nanochip Complex Glass）</b> —— 只比普通方块多做一件事：
 * <b>与相邻的同种玻璃共享面时不画那一面</b>。
 *
 * <h2>为什么必须补这一手（玩家实测现象：玻璃的花纹"连不起来"）</h2>
 * 取证结论（★ 2026-09-16 更正，见 {@code _census/connected_texture_audit.md}；原 §24.4 的结论已被推翻）：
 * <ul>
 *   <li>贴图<b>没有问题</b>：我们的 {@code block/nanochip_complex_glass.png} 与 GTNH 原版
 *       {@code NANOCHIP_GLASS.png} <b>逐像素相同</b>（原版那张被重编码过，故字节不同）。</li>
 *   <li>★ <b>{@code "GTNH 那边也只有这一张图、没有 CTM / 连接纹理变体" —— 这句是错的 ✗</b>：
 *       GTNH 对<b>复合体玻璃</b>挂了 <b>CTM（{@code method=compact}，5 格变体）</b>、
 *       对<b>网格接口机壳</b>挂了 <b>CTM（{@code method=ctm}，47 格变体）</b>，
 *       消费端是 MCPatcher 系的「连通纹理」（GTNH 侧由 Angelica 内置的 {@code com/prupe/mcpatcher} 实现，
 *       配置 {@code connected_textures.enabled=true}）；GT5U 自己**不读邻居**
 *       （其 {@code IIconContainer.getIcon()} 连坐标都没有 ⇒ 只能由渲染层拦截）。
 *       ⇒ 我们一直画的是 CTM 的「<b>孤立变体</b>」（0.png 与原贴图逐字节相同，这就是铁证），
 *         所以每块各画各的 —— 缺的是"<b>变体图集 + 选择器</b>"，不是贴图画错了。</li>
 *   <li>模型也<b>没有问题</b>：{@code cube_all} + {@code render_type: translucent}，
 *       UV 是标准 [0,0,16,16]、无旋转无镜像。</li>
 *   <li>真正的原因是<b>面剔除</b>：1.20 的 {@link Block#skipRendering} 默认返回 {@code false}，
 *       于是两块相邻的玻璃会<b>各画一遍它们的共享面</b>（两层半透明贴图叠在一起，
 *       花纹的接缝就会出现重影/断线）；而<b>不透明</b>的网格接口机壳由 MC 的常规剔除规则
 *       直接省掉内面 —— 这正是玩家说"机壳能咬合、玻璃连不上"的那一半原因。</li>
 *   <li>⚠️ 面剔除只解决"半透明面重影"，<b>不会让花纹连起来</b>；连接纹理要另靠 CTM
 *       （变体资源已随本模组发布在 {@code assets/gtlmodular/mcpatcher/ctm/**}，
 *        但**必须客户端有 MCPatcher 系消费端才会生效** —— 本整合包的 99 个 jar 里目前没有，
 *        详见施工记录 §58 与 {@code _census/connected_texture_audit.md}）。</li>
 * </ul>
 *
 * <p>做法与原生 {@code HalfTransparentBlock}（{@code AbstractGlassBlock} 的父类）<b>完全同款</b>：
 * 相邻方块也是自己 → 跳过这一面。**不改贴图、不改 UV、不影响结构判定、不需要重出数据包**。
 */
public class NanochipComplexGlassBlock extends Block {

    public NanochipComplexGlassBlock(Properties properties) {
        super(properties);
    }

    /**
     * @param state         本方块的状态
     * @param adjacentState 与该面相邻的方块状态
     * @param side          哪一面
     * @return 相邻同类 → {@code true}（不画这一面）
     *
     * <p>注意可见性：1.20.1 的 {@code BlockBehaviour.skipRendering} 是 {@code public}
     * （不是 {@code protected}）—— 收窄会编译失败："attempting to assign weaker access privileges"。
     */
    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        return adjacentState.is(this) || super.skipRendering(state, adjacentState, side);
    }
}
