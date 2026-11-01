package com.gtlmodular.client.renderer.stellar;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.client.renderer.machine.WorkableCasingMachineRenderer;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 恒星炎炀锻炉的机器渲染器（TESR）—— 移植 GTO
 * {@code com.gtocore.client.renderer.machine.StellarForgeRenderer}。
 *
 * <p>它自己<b>不画任何东西</b>：只负责（1）用 {@code WorkableCasingMachineRenderer} 画机器的
 * 外壳/覆层模型，（2）在机器成形且正在运行时，把「漩涡应该出现在哪」算出来喂给
 * {@link StellarFxManager#upsert}。真正的几何与 draw call 在 {@link StellarForgeVortexFX} 里，
 * 由 {@code RenderLevelStageEvent.AFTER_CUTOUT_BLOCKS} 统一批处理。
 *
 * <h2>与 GTO 的差异（逐条）</h2>
 * <ol>
 *   <li><b>机器判定</b>：GTO 是 {@code instanceof StellarForgeMachine}（它自己的机器类）；
 *       这里按「机器定义 id = {@code gtceu:stellar_forge}」判定，所以无论该 id 上的
 *       机器实现是 gtlcore 的 {@code TierCasingMachine} 还是本包换上的
 *       {@code com.gtlmodular.common.machine.stellar.StellarForgeMachine}，渲染器都认。</li>
 *   <li><b>{@code isActive()}</b>：GTO 的 {@code StellarForgeMachine#isActive()} 在本包没有同名方法
 *       （GTCEu 1.4.4 的 {@code WorkableElectricMultiblockMachine} / {@code MultiblockControllerMachine}
 *       都不提供 {@code isActive()}，javap 实测）。等价口径取
 *       {@code isFormed() && getRecipeLogic().isWorking()}（与增强版核心
 *       {@code StellarForgeMachineRenderer} 的判定同源）。</li>
 *   <li><b>去掉 GTOConfig</b>：GTO 的 {@code shouldRender} 会被
 *       {@code GTOConfig.INSTANCE.client.renderingConfig.enableLargeRangeShaderEffects} 一票否决。
 *       本包没有这个开关 ⇒ <b>不覆写 {@code shouldRender}</b>，直接用 LDLib
 *       {@code IRenderer} 的默认实现（{@code 相机到方块中心距离 < getViewDistance()}），
 *       即「恒定开、256 格内渲染」——和 GTO 开关打开时的行为一致。</li>
 *   <li>常量 {@code OFF_D/OFF_U/BAND_ANGLE/DECAY_RADIUS/DISAPPEAR_RADIUS} 按 GTO 原值写死
 *       （-2 / 31 / 0.3926991 → 0.39269908169872414 / 32 / 48）。</li>
 * </ol>
 *
 * <p><b>专用服务器安全</b>：本类<b>没有任何 static 字段引用 FX / shader 客户端类</b>
 * （只有 5 个 primitive 常量），FX 引用只出现在 {@link #render} 的方法体内；
 * 且它本身只在客户端被 {@code StellarForgeFxClient#attachRenderer()} 实例化。
 */
@OnlyIn(Dist.CLIENT)
public class StellarForgeRenderer extends WorkableCasingMachineRenderer {

    /** 漩涡中心相对控制器前方（front）的偏移：向里 2 格（GTO {@code offD}）。 */
    private static final int OFF_D = -2;

    /** 漩涡中心相对控制器上方的偏移：向上 31 格（GTO {@code offU}）。 */
    private static final int OFF_U = 31;

    /** 漩涡盘的半张角（GTO 常量 {@code angle = 0.39269908169872414}，即 PI/8）。 */
    private static final float BAND_ANGLE = 0.3926991F;

    /** 半径 32 格以内的漩涡强度开始按「衰减半径」递减。 */
    private static final float DECAY_RADIUS = 32.0F;

    /** 半径 48 格处完全消失（也是几何体本身的半径）。 */
    private static final float DISAPPEAR_RADIUS = 48.0F;

    public StellarForgeRenderer() {
        // 外壳贴图：与 gtlcore 原机一致（atomic casing）。
        // 覆层模型（= 控制器那块「脸」）：**用 gtlcore 原来的 electric_blast_furnace**，不用 GTO 的
        // fusion_reactor —— 后者 overlay_front 只有 56% 不透明且几乎是纯黑，机器没在跑的时候正面看
        // 就是「没有脸」；换回 EBF 覆层后正面恢复成正常机器脸（玩家 2026-11-01 反馈）。
        super(GTCEu.id("block/casings/gcym/atomic_casing"),
                GTCEu.id("block/multiblock/electric_blast_furnace"));
    }

    @Override
    public void render(BlockEntity blockEntity, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        if (!(blockEntity instanceof MetaMachineBlockEntity machineBlockEntity)) {
            return;
        }
        MetaMachine metaMachine = machineBlockEntity.getMetaMachine();
        if (!(metaMachine instanceof WorkableElectricMultiblockMachine machine)) {
            return;
        }
        if (!isStellarForge(machine)) {
            return;
        }
        Level level = blockEntity.getLevel();
        boolean active = machine.isFormed()
                && machine.getRecipeLogic() != null
                && machine.getRecipeLogic().isWorking();
        // GTO: (isActive() && isFormed()) || level instanceof TrackedDummyWorld
        if (!active && !(level instanceof TrackedDummyWorld)) {
            return;
        }
        // 漩涡中心 = 控制器方块位置 + RelativeDirection 偏移(上 31 / 前 -2)
        BlockPos center = RelativeDirection.offsetPos(BlockPos.ZERO,
                machine.getFrontFacing(), machine.getUpwardsFacing(), machine.isFlipped(),
                OFF_U, 0, OFF_D);
        // 结构预览（JEI 的 TrackedDummyWorld）里不生成真特效 —— 与 GTO 一致
        if (level != null && !(level instanceof TrackedDummyWorld)) {
            Vec3 vortexCenter = Vec3.atCenterOf(blockEntity.getBlockPos().offset(center));
            StellarForgeVortexFX.Key key = new StellarForgeVortexFX.Key(level.dimension(), blockEntity.getBlockPos());
            StellarFxManager.upsert(key,
                    () -> new StellarForgeVortexFX(key.dimension(), key.machinePos(), vortexCenter,
                            machine.getFrontFacing(), BAND_ANGLE, DECAY_RADIUS, DISAPPEAR_RADIUS),
                    fx -> fx.refresh(vortexCenter, machine.getFrontFacing(),
                            BAND_ANGLE, DECAY_RADIUS, DISAPPEAR_RADIUS));
        }
    }

    /** 是否为 {@code gtceu:stellar_forge}（GTO 此处是 {@code instanceof StellarForgeMachine}）。 */
    private static boolean isStellarForge(MetaMachine machine) {
        if (machine.getDefinition() == null) {
            return false;
        }
        ResourceLocation id = machine.getDefinition().getId();
        return id != null && "gtceu".equals(id.getNamespace()) && "stellar_forge".equals(id.getPath());
    }

    /** 有 TESR ⇒ LDLib 会为这个方块调 {@link #render}。 */
    @Override
    public boolean hasTESR(BlockEntity blockEntity) {
        return true;
    }

    /**
     * 全局渲染器 ⇒ 不受「方块是否在视锥内」的逐区块剔除限制
     * （特效球半径 48 格，跨好几个区块，必须全局算）。
     */
    @Override
    public boolean isGlobalRenderer(BlockEntity blockEntity) {
        return true;
    }

    /** 渲染距离 = 256 格（与 GTO 一致；{@code IRenderer#shouldRender} 的默认实现用的就是它）。 */
    @Override
    public int getViewDistance() {
        return 256;
    }
}
