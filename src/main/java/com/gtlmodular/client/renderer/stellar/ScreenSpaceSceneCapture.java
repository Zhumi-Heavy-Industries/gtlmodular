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

    /** 首次捕获成功/失败只打一条日志（诊断光影下的帧缓冲来源）。 */
    private static boolean loggedCapture;

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

    /**
     * ★ 2026-11-02：抓「当前真正绑定着的绘制帧缓冲」，而不是写死主渲染目标。
     *
     * <p>原因：玩家带 <b>Oculus/Iris + 光影包</b> 时，世界不是画在主渲染目标上，而是画进 Iris 自己的
     * 世界缓冲（在主目标上只有天空/清屏内容），最后才 composite 到主目标。若照 GTO 原样只抓
     * {@code getMainRenderTarget()}，{@code AFTER_CUTOUT_BLOCKS} 时抓到的是空画面 ⇒ 漩涡 shader
     * 采样到黑，视觉上「有几何但看不见扭曲」。
     * 这里改为读 {@code GL_DRAW_FRAMEBUFFER_BINDING}：谁在画就抓谁（原版下正好是主目标）。
     */
    private static int boundDrawFramebuffer() {
        try {
            return org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        } catch (Throwable ignored) {
            return 0;
        }
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
        // ★ 当前真正在画的帧缓冲（Iris 光影下是它自己的世界缓冲；原版下是主目标）。
        int boundFbo = boundDrawFramebuffer();
        int sourceFbo = boundFbo != 0 ? boundFbo : outputTarget.frameBufferId;
        int sourceWidth = outputTarget.width;
        int sourceHeight = outputTarget.height;
        // 1) 清掉离屏目标（第 2 参是 onMac，官方映射 Minecraft.ON_OSX）
        sceneTarget.clear(Minecraft.ON_OSX);
        // 2) 只 blit 颜色附件。
        //    ★ 2026-11-02：GTO 原来带 GL_DEPTH_BUFFER_BIT（16640）；在光影/某些驱动下
        //    源与目标的深度格式不一致时，整条 blit 会以 GL_INVALID_OPERATION 失败（连颜色也拷不过去）
        //    ⇒ 场景缓冲全黑 ⇒ 漩涡采样到黑，看起来就是「一个大黑圈 + 彩色螺旋」。
        //    这里只拷颜色（16384），失败再退回主渲染目标重试一次。
        int error = blitColor(sourceFbo, sourceWidth, sourceHeight);
        if (error != 0 && sourceFbo != outputTarget.frameBufferId) {
            error = blitColor(outputTarget.frameBufferId, outputTarget.width, outputTarget.height);
        }
        // 3) 把 GL 状态还回去：绑定回原来那个帧缓冲（不是盲目绑 0），视口按窗口复原
        if (boundFbo != 0) {
            GlStateManager._glBindFramebuffer(36160 /* GL_FRAMEBUFFER */, boundFbo);
        } else {
            outputTarget.bindWrite(true);
        }
        RenderSystem.viewport(0, 0, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        if (!loggedCapture) {
            loggedCapture = true;
            com.gtlmodular.GTLModular.LOGGER.info(
                    "[gtlmodular] 恒星炎炀锻炉特效：场景捕获 fbo={} size={}x{} → {}x{} 结果={}",
                    sourceFbo, sourceWidth, sourceHeight, sceneTarget.width, sceneTarget.height,
                    error == 0 ? "ok" : ("GL错误" + error));
        }
        sceneCopiedThisFrame = true;
        return sceneTarget;
    }

    /** 只拷颜色附件；返回 {@code glGetError()}（0 = 成功）。 */
    private static int blitColor(int sourceFbo, int sourceWidth, int sourceHeight) {
        GlStateManager._glBindFramebuffer(36008 /* GL_READ_FRAMEBUFFER */, sourceFbo);
        GlStateManager._glBindFramebuffer(36009 /* GL_DRAW_FRAMEBUFFER */, sceneTarget.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
                0, 0, sourceWidth, sourceHeight,
                0, 0, sceneTarget.width, sceneTarget.height,
                16384 /* GL_COLOR_BUFFER_BIT */,
                9728 /* GL_NEAREST */);
        int error = org.lwjgl.opengl.GL11.glGetError();
        // 把读/写绑定还原成「统一帧缓冲」，避免后续 GL 调用看到奇怪的读写分离状态。
        GlStateManager._glBindFramebuffer(36160 /* GL_FRAMEBUFFER */, sourceFbo);
        return error;
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
