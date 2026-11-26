package com.gtlmodular.mixin.gtladditions;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复 GTL Additions 3.6.2 自带的异星化工复合体（exotic_chemical_complex）结构升级 bug。
 *
 * <h2>现象</h2>
 * <p>打开该机器的结构预览、或它在世界里被多方块异步线程检查时，每秒抛
 * {@code IllegalArgumentException: Tier cells cannot contain controllers or counted parts}，
 * 预览显示"结构预览准备失败"，日志刷屏。
 *
 * <h2>根因</h2>
 * <p>Add 的调色板 {@code exotic_chemical_complex.palette} 把加热线圈符号
 * {@code ~ / * / +} 分别分到 tier 1/2/3，但 {@code heatingCoils()} 谓词带计数限制
 * （{@code limited} 非空）。Add 自己的 {@code StructureTierLayout.collect()} 第 123 行硬性要求
 * tier > 0 的格子不能是控制器或带计数部件，于是它初始化 {@code portOwners} 时自己抛异常。
 * 这是 Add 3.6.2 的调色板配置问题，不是本模组造成的。
 *
 * <h2>修法</h2>
 * <p>拦截分类器 {@code tiers$lambda$6}（返回给定格子的 structureTier），
 * 当该格是线圈符号时返回 0（永久基础格）——{@code collect()} 对 tier 0 不做计数校验，
 * 问题消失。代价：线圈不再随结构升级分级，但对玩家来说三级线圈本来就同时存在，
 * 只是不再作为"升级门槛"，不影响配方与产能。
 *
 * <p>用 {@code remap=false} + 反射读取 aisles：跨 jar 直接 {@code @Shadow} 一旦解析不到
 * 会在类加载期崩游戏，反射失败只是这次不生效（保持 Add 原行为）。
 */
@Mixin(targets = "com.gtladd.gtladditions.common.machine.multiblock.structure.layout.ExoticChemicalBlockLayout",
        remap = false)
public abstract class ExoticChemicalTierCoilMixin {

    /** 线圈符号：与 Add 调色板 ~ / * / + 对应，分别属于 tier 1/2/3。 */
    private static final String COILS = "~*+";

    @Inject(method = "tiers$lambda$6", at = @At("RETURN"), cancellable = true, remap = false)
    private static void gtlmodular$coilAsTier0(int aisle, int row, int column,
                                               CallbackInfoReturnable<Integer> cir) {
        // tier 0 本来就合法，不动
        if (cir.getReturnValue() == null || cir.getReturnValue() == 0) {
            return;
        }
        try {
            Class<?> owner = Class.forName(
                    "com.gtladd.gtladditions.common.machine.multiblock.structure.layout.ExoticChemicalBlockLayout",
                    false, ExoticChemicalTierCoilMixin.class.getClassLoader());
            Object instance = owner.getField("INSTANCE").get(null);
            java.lang.reflect.Method getAisles = owner.getDeclaredMethod("getAisles");
            getAisles.setAccessible(true);
            String[][] aisles = (String[][]) getAisles.invoke(instance);
            if (aisle < 0 || aisle >= aisles.length || row < 0 || row >= aisles[aisle].length) {
                return;
            }
            String line = aisles[aisle][row];
            if (column < 0 || column >= line.length()) {
                return;
            }
            char symbol = line.charAt(column);
            if (COILS.indexOf(symbol) >= 0) {
                cir.setReturnValue(0);
            }
        } catch (Throwable ignored) {
            // 反射拿不到时保持 Add 原样，避免引入新的崩溃
        }
    }
}
