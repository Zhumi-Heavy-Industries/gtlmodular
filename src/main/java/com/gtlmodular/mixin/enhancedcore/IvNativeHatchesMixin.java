package com.gtlmodular.mixin.enhancedcore;

import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 闸门①（结构侧，覆盖 10 台 {@code gtceu:} 原生 IV 机器）——让「超级猪咪样板总成」也能进
 * {@code #} 仓室位。
 *
 * <h2>⚠ 当前状态：不在任何已注册的混入配置里（0.3.105）</h2>
 * <p>本文件所在的 {@code gtlmodular.enhancedcore.mixins.json} <b>没有</b>被注册：
 * <ul>
 *   <li>{@code META-INF/mods.toml} 只有 {@code [[mixins]] config="gtlmodular.mixins.json"}；</li>
 *   <li>{@code build.gradle} 的 {@code MixinConfigs} 只写了 {@code gtlmodular.mixins.json}；</li>
 *   <li>{@code GTLModular.registerEnhancedcoreMixins()} 已被注释停用
 *       （{@code GTLModular.java:87}）。</li>
 * </ul>
 * ⇒ 本补丁<b>今天完全没生效</b>。0.3.105 审查把它的 {@code public static } 字段问题一并修掉，
 * 以便日后真要启用时不会一开就崩。
 *
 * <h2>目标（2026-09-27 用 javap 级反汇编实测，2.9.1 的 class 文件）</h2>
 * <pre>
 * com.gtl.enhancedcore.common.recipe.iv.IvNativeHatches   // public final class, major 65
 *   private final TraceabilityPredicate buffer;           // 字段
 *   public IvNativeHatches()                              // &lt;init&gt;()V, 60 字节
 *      0: aload_0  1: invokespecial Object.&lt;init&gt;()V
 *      4: aload_0  5: new IdentityHashMap  12: putfield changed:Ljava/util/Map;
 *     15: aload_0  16: iconst_1  17: anewarray Block
 *     22: getstatic ForgeRegistries.BLOCKS
 *     25: new ResourceLocation 29: ldc "gtladditions" 31: ldc "me_super_pattern_buffer"
 *     36: invokeinterface IForgeRegistry.getValue
 *     41: checkcast Block  44: aastore
 *     45: invokestatic  Predicates.blocks([Lnet/minecraft/world/level/block/Block;)Lcom/gregtechceu/gtceu/api/pattern/TraceabilityPredicate;
 *     48: iconst_1  49: invokevirtual TraceabilityPredicate.setMinGlobalLimited(I)
 *     52: iconst_1  53: invokevirtual TraceabilityPredicate.setPreviewCount(I)
 *     56: putfield buffer:Lcom/gregtechceu/gtceu/api/pattern/TraceabilityPredicate;
 *     59: return
 * </pre>
 * 该类没有 {@code @Final}（access_flags 的 ACC_FINAL=0x10 位为 0），但为了让 ASM 允许
 * {@code putfield}，仍然加 {@code @Mutable}。
 *
 * <h2>注入点</h2>
 * <p>在构造器 TAIL 处把 {@code buffer} 字段整体换成
 * {@code Predicates.blocks(gtladditions 原生, 我们的超级猪咪样板总成)}，
 * 并<b>原样保留</b> {@code setMinGlobalLimited(1)} / {@code setPreviewCount(1)}。
 * {@code restrict(...)} 与 {@code lambda$restrict$0} 里 {@code copy.or(this.buffer)} 的语义
 * 因此自动覆盖两边 ⇒ 原本能用的机器<b>只会更宽松，不会失效</b>。
 *
 * <h2>0.3.105 审查修正</h2>
 * <ol>
 *   <li><b>没有 static 字段</b> ⇒ 不触发 {@code contains non-private static field}（已复核：本类唯一
 *       字段是 {@code @Shadow} 的实例字段 {@code buffer}，带 {@code @Shadow} 注解 ⇒ 不会被当成
 *       "要加进目标类的新字段"）。</li>
 *   <li><b>未使用 {@code @Overwrite}</b>（高危）：只有一条 {@code @Inject(method = "&lt;init&gt;", at = TAIL)}
 *       + {@code require = 0}。</li>
 *   <li><b>缺省构造器</b>：本类不写显式构造器/静态初始化块（混入类不应有构造器；
 *       静态初始化也不保证按预期执行）。</li>
 * </ol>
 *
 * <h2>影响面与回退</h2>
 * <p>只改 {@code IvNativeHatches} 这一个谓词对象 ⇒ 影响面就是"哪些方块能占 {@code #} 仓室位"，
 * 且只会更宽松。任一方块未注册 / 注册表不可用 / 出现任何 Throwable ⇒ 保持原字段不变
 * （完全退回 2.9.1 原行为），不会因为这条兼容注入让游戏崩。
 * <p>本补丁<b>没有独立开关</b>：回退方式 = 把它从混入配置里去掉（{@code require = 0} ⇒
 * 去掉后不会崩），或让 {@code gtlmodular} 的 {@code super_me_pattern_assembly} 方块不注册。
 */
@Mixin(targets = "com.gtl.enhancedcore.common.recipe.iv.IvNativeHatches", remap = false)
public abstract class IvNativeHatchesMixin {

    /** 目标字段：{@code private final TraceabilityPredicate buffer;}（私有无 setter ⇒ 必须 @Shadow）。 */
    @Shadow
    @Final
    @Mutable
    private TraceabilityPredicate buffer;

    @Inject(method = "<init>", at = @At("TAIL"), require = 0, remap = false)
    private void gtlmodular$acceptSuperZhuMiAssembly(CallbackInfo ci) {
        try {
            Block theirs = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation("gtladditions", "me_super_pattern_buffer"));
            Block ours = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation("gtlmodular", "super_me_pattern_assembly"));
            // 任一方块查不到就一个字都不改 ⇒ 保留原行为（原来能用的机器绝不失效）
            if (theirs == null || ours == null) {
                return;
            }
            this.buffer = Predicates.blocks(theirs, ours)
                    .setMinGlobalLimited(1)
                    .setPreviewCount(1);
        } catch (Throwable ignored) {
            // 绝不因为这条兼容注入让游戏崩；失败即退回原字段
        }
    }
}
