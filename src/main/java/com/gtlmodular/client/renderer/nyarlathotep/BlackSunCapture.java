package com.gtlmodular.client.renderer.nyarlathotep;

import com.gtlmodular.GTLModular;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 奈亚中央黑日的屏幕空间场景捕获（2026-11-02，0.3.328）。
 *
 * <p>黑日 shader 需要把「已经画好的世界」当 {@code DiffuseSampler} 采样，才能做出
 * 引力透镜/背景折光，而不是只叠一层半透明颜色。做法与恒星炎炀锻炉的
 * {@code ScreenSpaceSceneCapture} 同源（同一套「抓当前绑定的绘制帧缓冲」的 Iris/Oculus 适配），
 * 但**独立一份缓冲**——这样它不会被恒星特效管理器的 {@code release()} 顺手回收。
 *
 * <p>关键点（与 forge 的 0.3.313 修复一致）：
 * <ul>
 *   <li>抓 {@code GL_DRAW_FRAMEBUFFER_BINDING} 而不是写死主目标（光影下世界画在 Iris 的缓冲里）；</li>
 *   <li>blit 只拷颜色附件（{@code GL_COLOR_BUFFER_BIT}），失败再退回主目标重试一次；</li>
 *   <li>拷完把帧缓冲绑回原来那个，视口按窗口复原；</li>
 *   <li>分辨率变化/窗口最小化时重建或释放。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class BlackSunCapture {

    private static TextureTarget sceneTarget;
    private static boolean sceneCopiedThisFrame;
    private static boolean loggedCapture;

    private BlackSunCapture() {
    }

    /** 每帧起点调用一次。 */
    public static void beginFrame() {
        sceneCopiedThisFrame = false;
    }

    /**
     * 抓当前已渲染画面的颜色。返回 null 时调用方必须直接跳过。
     *
     * <p>0.3.329 起本效果画在 {@code AFTER_LEVEL}（Iris 合成之后）⇒ 要抓的是<b>最终合成画面</b>：
     * 优先抓「当前真正绑定的绘制帧缓冲」（Iris 下是它自己的世界缓冲；合成后是主目标），
     * 拿不到就退回主渲染目标。**不再**取 Fabulous 的 translucent target ——
     * 那个缓冲只是半透明阶段的中间产物，不含最终合成。
     */
    public static TextureTarget capture(LevelRenderer levelRenderer) {
        RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
        if (mainTarget == null) {
            return null;
        }
        return captureTarget(mainTarget);
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
        int boundFbo = boundDrawFramebuffer();
        int sourceFbo = boundFbo != 0 ? boundFbo : outputTarget.frameBufferId;
        int sourceWidth = outputTarget.width;
        int sourceHeight = outputTarget.height;
        sceneTarget.clear(Minecraft.ON_OSX);
        int error = blitColor(sourceFbo, sourceWidth, sourceHeight);
        if (error != 0 && sourceFbo != outputTarget.frameBufferId) {
            error = blitColor(outputTarget.frameBufferId, outputTarget.width, outputTarget.height);
        }
        if (boundFbo != 0) {
            GlStateManager._glBindFramebuffer(36160 /* GL_FRAMEBUFFER */, boundFbo);
        } else {
            outputTarget.bindWrite(true);
        }
        RenderSystem.viewport(0, 0, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        if (!loggedCapture) {
            loggedCapture = true;
            GTLModular.LOGGER.info("[gtlmodular] 奈亚中央黑日：场景捕获 fbo={} size={}x{} → {}x{} 结果={}",
                    sourceFbo, sourceWidth, sourceHeight, sceneTarget.width, sceneTarget.height,
                    error == 0 ? "ok" : ("GL错误" + error));
        }
        sceneCopiedThisFrame = true;
        return sceneTarget;
    }

    private static int boundDrawFramebuffer() {
        try {
            return org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int blitColor(int sourceFbo, int sourceWidth, int sourceHeight) {
        GlStateManager._glBindFramebuffer(36008 /* GL_READ_FRAMEBUFFER */, sourceFbo);
        GlStateManager._glBindFramebuffer(36009 /* GL_DRAW_FRAMEBUFFER */, sceneTarget.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
                0, 0, sourceWidth, sourceHeight,
                0, 0, sceneTarget.width, sceneTarget.height,
                16384 /* GL_COLOR_BUFFER_BIT */,
                9728 /* GL_NEAREST */);
        int error = org.lwjgl.opengl.GL11.glGetError();
        GlStateManager._glBindFramebuffer(36160 /* GL_FRAMEBUFFER */, sourceFbo);
        return error;
    }

    /** 释放离屏缓冲（世界卸载时调用）。 */
    public static void release() {
        if (sceneTarget != null) {
            sceneTarget.destroyBuffers();
            sceneTarget = null;
        }
        sceneCopiedThisFrame = false;
    }

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
