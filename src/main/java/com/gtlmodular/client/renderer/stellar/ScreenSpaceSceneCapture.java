package com.gtlmodular.client.renderer.stellar;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 屏幕空间场景捕获 —— <b>原样移植</b> GTO {@code com.gtocore.client.renderer.fx.ScreenSpaceSceneCapture}。
 *
 * <p>作用：把「已经画好的世界」整块 blit 到一张离屏 {@link TextureTarget}，当作
 * {@code DiffuseSampler} 喂给漩涡 shader，让漩涡能在自己的顶点范围内再采样一次背景
 * （屏幕空间折光/扭曲），而不是单纯叠加半透明颜色。
 *
 * <p>每帧只拷贝一次（{@code sceneCopiedThisFrame}），大小跟随主渲染目标；分辨率变化时
 * 销毁重建。窗口最小化（宽高 &lt;= 0）时释放，避免 {@code glTexImage2D} 传 0。
 *
 * <h2>映射说明</h2>
 * 全部按 1.20.1 官方映射重写（反编译产物里是 {@code m_xxxxx_} / {@code f_xxxxx_}）：
 * <ul>
 *   <li>{@code m_91087_ → Minecraft.getInstance()}</li>
 *   <li>{@code m_91385_ → getMainRenderTarget()}</li>
 *   <li>{@code m_91085_ → Minecraft.useShaderTransparency()}（华丽画质下才有独立半透明缓冲）</li>
 *   <li>{@code m_109831_ → LevelRenderer.getTranslucentTarget()}</li>
 *   <li>{@code m_83954_ → RenderTarget.clear(boolean)}，{@code f_91002_ → Minecraft.ON_OSX}</li>
 *   <li>{@code m_83947_ → bindWrite(boolean)}，{@code m_83931_ → setClearColor(...)}，
 *       {@code m_83936_ → setFilterMode(int)}，{@code m_83930_ → destroyBuffers()}</li>
 *   <li>{@code f_83915_/f_83916_ → width/height}（1.20.1 是 public 字段，不是 getter）</li>
 *   <li>{@code f_83920_ → frameBufferId}，{@code m_83975_ → getColorTextureId()}</li>
 * </ul>
 *
 * <p>GL 常量按 GTO 原样写成字面量（{@code 36008}=GL_READ_FRAMEBUFFER、
 * {@code 36009}=GL_DRAW_FRAMEBUFFER、{@code 36160}=GL_FRAMEBUFFER、
 * {@code 16640}=GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT、{@code 9728}=GL_NEAREST、
 * {@code 9729}=GL_LINEAR），避免引入 LWJGL 常量类带来的符号差异。
 */
@OnlyIn(Dist.CLIENT)
public final class ScreenSpaceSceneCapture {

    /** 离屏场景缓冲（懒创建，分辨率变化时重建）。 */
    private static TextureTarget sceneTarget;

    /** 本帧是否已经拷过 —— 同帧多个漩涡只拷一次。 */
    private static boolean sceneCopiedThisFrame;

    private ScreenSpaceSceneCapture() {
    }

    /** 每帧起点调用一次（{@code AFTER_CUTOUT_BLOCKS} 与 {@code AFTER_LEVEL} 都调了，与 GTO 一致）。 */
    public static void beginFrame() {
        sceneCopiedThisFrame = false;
    }

    /**
     * 捕获当前已渲染的画面的颜色缓冲。
     *
     * @return 离屏目标；尺寸非法或创建失败时为 {@code null}（调用方必须判空）
     */
    public static TextureTarget capture(LevelRenderer levelRenderer) {
        RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
        // 华丽画质（Fabulous）下，半透明阶段画在单独的缓冲里 ⇒ 想「连半透明一起抓」就得抓它。
        RenderTarget outputTarget = Minecraft.useShaderTransparency() && levelRenderer.getTranslucentTarget() != null
                ? levelRenderer.getTranslucentTarget()
                : mainTarget;
        return captureTarget(outputTarget);
    }

    /** 只抓主渲染目标（GTO 保留的另一入口，当前没用到）。 */
    public static TextureTarget captureMainTarget() {
        return captureTarget(Minecraft.getInstance().getMainRenderTarget());
    }

    private static TextureTarget captureTarget(RenderTarget outputTarget) {
        ensureSceneTarget();
        if (sceneTarget == null) {
            return null;
        }
        if (sceneCopiedThisFrame) {
            return sceneTarget;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // 1) 清掉离屏目标（第 2 参是 onMac，官方映射 Minecraft.ON_OSX）
        sceneTarget.clear(Minecraft.ON_OSX);
        // 2) 把 outputTarget 的颜色附件 blit 到离屏目标的颜色附件
        GlStateManager._glBindFramebuffer(36008 /* GL_READ_FRAMEBUFFER */, outputTarget.frameBufferId);
        GlStateManager._glBindFramebuffer(36009 /* GL_DRAW_FRAMEBUFFER */, sceneTarget.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
                0, 0, outputTarget.width, outputTarget.height,
                0, 0, sceneTarget.width, sceneTarget.height,
                16640 /* GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT */,
                9728 /* GL_NEAREST */);
        GlStateManager._glBindFramebuffer(36160 /* GL_FRAMEBUFFER */, 0);
        // 3) 把视口复原并重新绑回主目标（blit 之后 GL 状态已被我们自己改过）
        outputTarget.bindWrite(true);
        RenderSystem.viewport(0, 0, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        sceneCopiedThisFrame = true;
        return sceneTarget;
    }

    /** 释放离屏缓冲（世界卸载 / 没有特效时调用）。 */
    public static void release() {
        if (sceneTarget != null) {
            sceneTarget.destroyBuffers();
            sceneTarget = null;
        }
        sceneCopiedThisFrame = false;
    }

    /** 按主渲染目标尺寸懒建/重建离屏缓冲。 */
    private static void ensureSceneTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (mainTarget.width <= 0 || mainTarget.height <= 0) {
            release();
            return;
        }
        if (sceneTarget == null
                || sceneTarget.width != mainTarget.width
                || sceneTarget.height != mainTarget.height) {
            release();
            sceneTarget = new TextureTarget(mainTarget.width, mainTarget.height, true, Minecraft.ON_OSX);
            sceneTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            sceneTarget.setFilterMode(9729 /* GL_LINEAR */);
        }
    }
}
