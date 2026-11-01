package com.gtlmodular.client.renderer.stellar;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * 屏幕空间特效基类 —— 对应 GTO 的 {@code com.gtocore.client.renderer.fx.AbstractFX}。
 *
 * <p>生命周期（由 {@link StellarFxManager} 驱动）：
 * <ol>
 *   <li>构造后每客户端 tick 调一次 {@link #tick()}（内部累加 {@code age}）；</li>
 *   <li>每个 {@code RenderLevelStageEvent} 都会调一次 {@link #render}（子类按 stage 自己过滤）；</li>
 *   <li>tick 里若 {@link #shouldDiscard()} 为 true ⇒ 从队列里摘掉并调 {@link #onDiscard()}。</li>
 * </ol>
 *
 * <p>与 GTO 的差异：仅去掉 Lombok 的 {@code @Generated} 与 {@code @MustBeInvokedByOverriders}
 * （前者只是标记，后者是 JetBrains 注解），语义完全一致。
 */
@OnlyIn(Dist.CLIENT)
public abstract class AbstractStellarFx {

    /** 是否已丢弃。丢弃后 {@link StellarFxManager} 不再调 {@link #render}。 */
    private boolean discarded;

    /** 存活 tick 数（从 0 开始，每次 {@link #tick()} +1）。 */
    protected int age;

    /**
     * 每客户端 tick 一次。子类覆写时必须调用 {@code super.tick()}，
     * 否则 {@code age} 不增长、过期判定与开场淡入全失效。
     */
    public void tick() {
        ++this.age;
    }

    /** 由子类判定「这一帧之后就不该再存在了」（例如机器停机 / 维度切换 / 长时间没被刷新）。 */
    public boolean shouldDiscard() {
        return false;
    }

    /** 被摘掉时的收尾钩子（当前仅置丢弃标记；子类需要释放 GL 资源时覆写并调 super）。 */
    protected void onDiscard() {
        this.discarded = true;
    }

    /**
     * 渲染入口。每个 level stage 都会被调用一次，实现方自行按 {@code stage} 过滤。
     *
     * <p>默认实现为空（对应 GTO 的空方法）。
     */
    public void render(RenderLevelStageEvent.Stage stage, LevelRenderer levelRenderer, PoseStack poseStack,
                       Matrix4f projectionMatrix, float partialTick, Camera camera, Frustum frustum) {
    }

    public boolean isDiscarded() {
        return this.discarded;
    }

    public int getAge() {
        return this.age;
    }
}
