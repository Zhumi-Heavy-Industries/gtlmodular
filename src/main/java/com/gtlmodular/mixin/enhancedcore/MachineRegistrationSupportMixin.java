package com.gtlmodular.mixin.enhancedcore;

import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 闸门②（结构侧，覆盖 2.9.1 新机器「通用联合工厂」
 * {@code gtl_enhancedcore:universal_joint_factory}）。
 *
 * <h2>⚠ 当前状态：不在任何已注册的混入配置里（0.3.105）</h2>
 * <p>与 {@link IvNativeHatchesMixin} 同因：{@code gtlmodular.enhancedcore.mixins.json} 未被注册
 * （{@code mods.toml} 只有 {@code gtlmodular.mixins.json}，{@code MixinConfigs} 同样只有它，
 * {@code GTLModular.registerEnhancedcoreMixins()} 已停用）⇒ <b>今天完全没生效</b>。
 * 本文件已按 Mixin 硬性约束复核并修正，日后真的注册时不会一开就崩。
 *
 * <h2>目标（2026-09-27 javap 级反汇编实测，2.9.1）</h2>
 * <pre>
 * com.gtl.enhancedcore.common.registration.MachineRegistrationSupport
 *   public static final TraceabilityPredicate requiredBlock(String)   // access_flags = 0x0019
 *     Code: maxStack=4 maxLocals=1 len=15
 *       0: iconst_1   1: anewarray Block
 *       4: dup        5: iconst_0
 *       6: aload_0
 *       7: invokestatic MachineRegistrationSupport.requiredBlockBlock:(Ljava/lang/String;)Lnet/minecraft/world/level/block/Block;
 *      10: aastore
 *      11: invokestatic Predicates.blocks([Lnet/minecraft/world/level/block/Block;)Lcom/gregtechceu/gtceu/api/pattern/TraceabilityPredicate;
 *      14: areturn
 * </pre>
 * <p>调用点：{@code IntegratedFactoryStructure.create(...)} 里
 * {@code ldc "gtladditions:me_super_pattern_buffer" → invokestatic requiredBlock(String)}
 * → 结果再 {@code setMinGlobalLimited(1) / setMaxGlobalLimited(30) / setPreviewCount(1)}
 * 并与维护仓、能源仓谓词 {@code or(...)}。
 *
 * <h2>注入点</h2>
 * <p>{@code @At("RETURN")} + {@code cancellable}：只在参数正好等于
 * {@code "gtladditions:me_super_pattern_buffer"} 时替换返回值；调用方后面的
 * {@code setMinGlobalLimited(1)/setMaxGlobalLimited(30)} 依旧生效 ⇒ 合计允许 1~30 个总成。
 * 其它的 {@code requiredBlock(...)} 调用（别的方块名）原样不动。
 *
 * <h2>0.3.105 审查修正</h2>
 * <ol>
 *   <li><b>没有 static 字段</b> ⇒ 不触发 {@code contains non-private static field}（已复核：本类零字段）。</li>
 *   <li><b>处理器必须是 static</b>：目标 {@code requiredBlock(String)} 的 access_flags = {@code 0x0019}
 *       = {@code ACC_PUBLIC | ACC_STATIC | ACC_FINAL} ⇒ 是 <b>static</b> 方法 ⇒ 处理器
 *       {@code gtlmodular$acceptSuperZhuMiAssembly} 也写成 {@code static}（本文件已如此，保持不变；
 *       写成实例方法会抛 {@code STATIC modifier ... does not match the target}）。</li>
 *   <li><b>未使用 {@code @Overwrite}</b>（高危）；{@code require = 0} ⇒ 方法签名一旦变化就静默跳过
 *       （该方法在 2.9.1 才新增，旧版 Enhancedcore 没有它 ⇒ 不能因为注入失败崩游戏）。</li>
 * </ol>
 *
 * <h2>影响面与回退</h2>
 * <p>只影响"通用联合工厂"结构里那一个方块谓词 ⇒ 只让我们的总成也能进那个位点，
 * 且只会更宽松。任一方块缺失 ⇒ 不改返回值，调用方拿到原谓词。
 * <p>本补丁<b>没有独立开关</b>：回退方式 = 把它从混入配置里去掉（{@code require = 0} ⇒ 不会崩）。
 */
@Mixin(targets = "com.gtl.enhancedcore.common.registration.MachineRegistrationSupport", remap = false)
public abstract class MachineRegistrationSupportMixin {

    @Inject(method = "requiredBlock", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void gtlmodular$acceptSuperZhuMiAssembly(
            String name, CallbackInfoReturnable<TraceabilityPredicate> cir) {
        try {
            if (!"gtladditions:me_super_pattern_buffer".equals(name)) {
                return;
            }
            Block theirs = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation("gtladditions", "me_super_pattern_buffer"));
            Block ours = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation("gtlmodular", "super_me_pattern_assembly"));
            // 任一方块缺失 ⇒ 不改返回值，调用方拿到原谓词
            if (theirs == null || ours == null) {
                return;
            }
            cir.setReturnValue(Predicates.blocks(theirs, ours));
        } catch (Throwable ignored) {
            // 保留原返回值
        }
    }
}
