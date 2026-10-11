package com.gtlmodular.mixin.gtlcore;

import com.gtlmodular.common.machine.PatternSlotIsolationGuard;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B 层（甲案·严格逐槽隔离，用户明确要的功能）：在 gtlcore 样板仓的 ME 输入/流体处理器上
 * 注入「归属槽」守卫。机理与设计见 {@code _census/严格逐槽隔离_方案.md}；
 * 判据与豁免都在 {@link PatternSlotIsolationGuard} 里（那里是<b>普通类</b>，
 * 不受 Mixin 的静态字段约束，所以总开关 {@code STRICT_SLOT_ISOLATION} 留在它身上）。
 *
 * <h2>到底干什么</h2>
 * <p>GTCEu 的配方层「只要能匹配就做」，而 gtlcore 的样板仓默认把<b>所有已激活槽</b>的内容
 * 一起拿去匹配 ⇒ A 配方的产物能与 B 配方的需求拼上时就会<b>跨槽串配方</b>。
 * 本补丁在「ME 物品输入处理器 / ME 流体输入处理器」的配方匹配入口
 * （{@code meHandleRecipeInner}）上加一道闸：若这条配方的归属槽<b>不在参与集</b>里就拒绝
 * （{@code cir.setReturnValue(false)}）⇒ 只有「该配方自己的那个槽」参与匹配。
 *
 * <h2>影响面（重要：注入点是 gtlcore 的公共类，不是我们的类）</h2>
 * <ul>
 *   <li>注入点 {@code MEPatternBufferRecipeHandlerTraitBase$MEItemInputHandlerBase} /
 *       {@code $MEFluidHandlerBase} 属于 <b>gtlcore</b> ⇒ 这两条钩子对<b>所有</b>用样板仓的机器生效，
 *       包括别的模组的机器。真正「只影响我们」的收窄发生在
 *       {@link PatternSlotIsolationGuard#rejects(Object, GTRecipe)} 里：
 *       {@code !(ownerMachine instanceof MoltenPatternAssemblyPartMachine) ⇒ return false}（一律放行）。
 *       ⇒ 语义上只影响我们的机器，但<b>每条配方匹配都要过一次我们的判定</b>（有一次 try/catch 与
 *       一次 {@code instanceof}），这是本补丁的固定开销。</li>
 *   <li>开关：{@code PatternSlotIsolationGuard.STRICT_SLOT_ISOLATION}（{@link PatternSlotIsolationGuard}，
 *       默认 {@code true}）。置 {@code false} ⇒ {@code rejects(...)} 第一句就返回 false ⇒
 *       与"没有本补丁"逐字等价。</li>
 *   <li>{@code require = 0}：目标方法一旦改名/消失 ⇒ 静默跳过，不会崩加载期。</li>
 * </ul>
 *
 * <h2>0.3.105 审查修正</h2>
 * <ol>
 *   <li><b>注入处理器必须是 static</b>（原版写成了实例方法 ⇒ 会崩）。取证：
 *       目标 {@code meHandleRecipeInner} 是
 *       <b>{@code public boolean meHandleRecipeInner(GTRecipe, Object2LongMap, boolean, int)}</b>、
 *       <b>非 static</b> 的实例方法，见
 *       {@code _census\_javap_simple_handlerbase.txt:146}、{@code _census\_javap_simple_fluidhandlerbase.txt:130}
 *       —— 这两份是 javap 反汇编实证。
 *       <br>⚠ 文档里 {@code _census\enhancedcore-src\docs\IV代码冲突检测-20260919.md:28} 与
 *       {@code ...\history\20260917-before-refactor\API标准.md:226} 曾把它记成
 *       {@code (GTRecipe, Object2LongMap, boolean, int)}「无 static」—— 那是<b>对的</b>；
 *       早期报告里"public 签名 (GTRecipe, Object2LongMap, boolean, int)"如果被读成 static 就会写错，
 *       本文件以 javap 为准：<b>实例方法 ⇒ 处理器也必须是实例方法</b>。
 *       <br>（Mixin 会比对 {@code ACC_STATIC} 标志；不匹配抛
 *       {@code InvalidMixinException: STATIC modifier of @Inject method ... does not match the target}。）
 *       ⇒ 本次修正：处理器保持<b>实例方法</b>（不写 static），与目标一致。</li>
 *   <li><b>没有 static 字段</b> ⇒ 本文件本来就不会触发
 *       {@code contains non-private static field}。已复核：本类零 static 成员。</li>
 *   <li><b>{@code getMachine()} 已核对</b>：目标处理器类确有 {@code public ... getMachine()}，
 *       判据见 {@code _census\enhancedcore-src\docs\IV代码冲突检测-20260919.md:28}
 *       （上游自己的两个 Guard Mixin 用同一签名）+ 同仓 {@code _备份_精简前_20260919} 的
 *       {@code MoltenPatternAssemblyPartMachine.java:1022/1043} 覆写签名。保留 abstract 声明
 *       （不外加 {@code @Shadow}）—— 目标是 public 方法，抽象声明即可解析。</li>
 *   <li>未使用 {@code @Overwrite}（高危）：本补丁只用 {@code @Inject HEAD + cancellable}。</li>
 * </ol>
 */
@Mixin(targets = {
        "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferRecipeHandlerTraitBase$MEItemInputHandlerBase",
        "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferRecipeHandlerTraitBase$MEFluidHandlerBase"
}, remap = false)
public abstract class PatternBufferSlotIsolationMixin {

    /** 目标处理器的公共取机器方法（上游 Guard Mixin 同款声明）。 */
    public abstract org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase getMachine();

    /**
     * 目标签名：{@code public boolean meHandleRecipeInner(GTRecipe, Object2LongMap, boolean, int)}
     * （{@code require = 0} ⇒ 名字或参数变了也只是不织入，不会崩加载期）。
     */
    @Inject(method = "meHandleRecipeInner", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void gtlmodular$rejectCrossSlot(GTRecipe recipe, Object2LongMap<?> contents,
                                            boolean simulate, int[] activeSlots,
                                            CallbackInfoReturnable<Boolean> cir) {
        try {
            if (PatternSlotIsolationGuard.rejects(getMachine(), recipe)) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {
            // 判据内部已经全吞异常；这里再兜一层 ⇒ 绝不因为隔离让机器停摆
        }
    }
}
