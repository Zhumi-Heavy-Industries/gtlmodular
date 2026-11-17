package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.pattern.error.SinglePredicateError;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.NanochipBlocks;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NAC 主机的<b>纯只读诊断集</b>（02 图谱 §8 / M1 抽出的第一刀）。
 *
 * <p><b>本类持有零状态、零字段写入</b>：所有方法要么只读机器/世界/结构状态，要么只往
 * {@code GTLModular.LOGGER} 打一行既有文案。限流状态（{@code last*Tick} / {@code *DiagCount}）
 * 与任何「缓存写入」<b>刻意留在机器类</b> —— 本类不做节流，节流仍由调用方判定并传参（或原样保留在机器里）。
 *
 * <p>行为等价承诺：方法体逐字搬自 {@link NanochipAssemblyComplexMachine}，只做三处机械替换 ——
 * ① {@code this.} → {@code machine.}；② 原先读机器私有字段的两处（{@code cachedModuleSlots} /
 * {@code lastMatchedModuleSlots}）改为由调用方作为 {@code slots} 参数传入；③ 原先读 {@code modules.size()}
 * 的两处改为 {@code machine.getModuleSet().size()}（同一集合，公开只读口）。
 * 日志文本、级别、次数、调用顺序一律未改。
 *
 * <p><b>未搬入</b>（按任务铁律「有字段写入的别搬」）：
 * {@code logUnformedAudit}、{@code logCalibrationRecord}、{@code logUnlimitedEnergyAssist}、
 * {@code logCraftedRecipe / noteNoFit / noteHostPrechargeWaiting}、{@code diagnoseLoadPartsNotReady}
 * —— 它们写限流/计数状态字段，继续留在机器类。
 */
final class NanochipDiagnostics {

    /** 见 {@link #describeThrowable}：诊断行里最多打几帧栈（≥3 才够"栈顶若干帧"）。 */
    private static final int DESCRIBE_THROWABLE_FRAMES = 4;

    private NanochipDiagnostics() {}

    /**
     * 真正把"缺件清单"写出去（{@code debugOnly=true} ⇒ 全部降到 debug，内容一字不变）。
     *
     * <p>拆成独立方法是为了让 INFO / debug 两条路<b>共用同一份文案</b>：
     * 降噪只改"多久打一次 / 打不打"，<b>不改清单内容</b>（队长要看的就是这份清单）。
     */
    static void logUnformedAuditBody(NanochipAssemblyComplexMachine machine, List<String> audit, String fSlots, boolean debugOnly) {
        if (audit.isEmpty()) {
            if (debugOnly) {
                GTLModular.LOGGER.debug("[gtlmodular] NAC \u5c1a\u672a\u6210\u5f62 @{}\uff1a\u552f\u4e00\u65b9\u5757\u683c\uff08\u7f51\u683c\u673a\u58f3/\u5f3a\u5316\u673a\u58f3/\u590d\u5408\u4f53\u73bb\u7483/\u8ba1\u7b97\u77e9\u9635\u673a\u58f3/\u9632\u706b\u5899\u6295\u5f71\uff09**\u5168\u90e8\u5c31\u4f4d** \u21d2 \u7f3a\u53e3\u5728\u300c\u591a\u89e3\u683c\u300d\uff08H \u4ed3\u4f4d / I \u53ef\u5ffd\u7565\u4f4d / F \u6a21\u5757\u4f4d / \u7a7a\u683c\uff09\u6216\u6570\u91cf\u7c7b\u9650\u5236\u4e0a\uff1b{}\uff1bGTCEu \u9010\u683c\u539f\u56e0\uff1a{}", new Object[]{machine.getPos(), fSlots, String.valueOf(describeStructureFailure(machine.getMultiblockState()))});
            } else {
                GTLModular.LOGGER.info("[gtlmodular] NAC \u5c1a\u672a\u6210\u5f62 @{}\uff1a\u552f\u4e00\u65b9\u5757\u683c\uff08\u7f51\u683c\u673a\u58f3/\u5f3a\u5316\u673a\u58f3/\u590d\u5408\u4f53\u73bb\u7483/\u8ba1\u7b97\u77e9\u9635\u673a\u58f3/\u9632\u706b\u5899\u6295\u5f71\uff09**\u5168\u90e8\u5c31\u4f4d** \u21d2 \u7f3a\u53e3\u5728\u300c\u591a\u89e3\u683c\u300d\uff08H \u4ed3\u4f4d / I \u53ef\u5ffd\u7565\u4f4d / F \u6a21\u5757\u4f4d / \u7a7a\u683c\uff09\u6216\u6570\u91cf\u7c7b\u9650\u5236\u4e0a\uff1b{}\uff1bGTCEu \u9010\u683c\u539f\u56e0\uff1a{}", new Object[]{machine.getPos(), fSlots, String.valueOf(describeStructureFailure(machine.getMultiblockState()))});
            }
        } else if (debugOnly) {
            GTLModular.LOGGER.debug("[gtlmodular] NAC \u7f3a\u4ef6\u6e05\u5355 @{}\uff08{} \u6761\uff1b{}\uff09\uff1a", new Object[]{machine.getPos(), audit.size(), fSlots});
            for (String line : audit) {
                GTLModular.LOGGER.debug("[gtlmodular]   \u00b7 {}", (Object)line);
            }
        } else {
            GTLModular.LOGGER.info("[gtlmodular] NAC \u7f3a\u4ef6\u6e05\u5355 @{}\uff08{} \u6761\uff1b{}\uff09\uff1a", new Object[]{machine.getPos(), audit.size(), fSlots});
            for (String line : audit) {
                GTLModular.LOGGER.info("[gtlmodular]   \u00b7 {}", (Object)line);
            }
        }
        logFailedPredicate(machine, debugOnly);
    }

    /**
     * <b>F 位（模块位）内容清点</b>：模块控制器共几台、网格接口机壳几个、其它几个。
     *
     * <p>★ 2026-09-19（队长原话：「可是我压根就没放纳米芯片组装矩阵呀」）—— 这条正是为此加的。
     * 图案里那条判据是「<b>14 种模块控制器合计 ≤ 15 台</b>」（{@code NanochipComplexPattern} 的
     * {@code blocks(accepted).setMaxGlobalLimited(15)}），而它的失败文案在 GTCEu 里
     * <b>只有「最多：%d个」，不带方块名</b>（jar lang {@code gtceu.multiblock.pattern.error.limited.0}）。
     * GTLsupb 补上去的那个方块名取的是<b>该判据的第一个候选</b>，而我们候选表的首项就是
     * {@link NanochipModuleKind#ASSEMBLY_MATRIX}（枚举首项）⇒ 玩家会看到"纳米芯片组装矩阵最多：15个"，
     * 于是以为"我没放组装矩阵却说我放了"。⇒ 把**真实台数**直接数出来，不再让人猜。
     *
     * <p>线程/时机：只读世界方块状态，可在 {@code onStructureFormed}（GTCEu 异步线程回调）与
     * {@code tickSync}（服务端线程）两侧调用；{@link #cachedModuleSlots} 在失效时**故意保留**
     * （见该字段注释），所以未成形时也能清点。
     */
    static String summarizeFSlotContent(NanochipAssemblyComplexMachine machine, List<BlockPos> slots) {
        Level level = machine.getLevel();
        int controllers = 0;
        int casings = 0;
        int others = 0;
        if (level != null) {
            for (BlockPos pos : slots) {
                if (!level.isLoaded(pos)) {
                    ++others;
                    continue;
                }
                Block block = level.getBlockState(pos).getBlock();
                if (isModuleControllerBlock(block)) {
                    ++controllers;
                    continue;
                }
                if (block == NanochipBlocks.MESH_INTERFACE_CASING.get()) {
                    ++casings;
                    continue;
                }
                ++others;
            }
        }
        return "F \u4f4d " + slots.size() + " \u683c\uff1a\u6a21\u5757\u63a7\u5236\u5668 " + controllers + " \u53f0\uff08\u4e0a\u9650 " + machine.getMaxModuleCount() + "\uff09/ \u7f51\u683c\u63a5\u53e3\u673a\u58f3 " + casings + " \u4e2a / \u5176\u5b83 " + others;
    }

    /** 该方块是不是 14 种模块控制器之一（按注册名判，避免依赖 GTCEu 的 definition API 细节）。 */
    static boolean isModuleControllerBlock(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null || !"gtlmodular".equals(id.getNamespace())) {
            return false;
        }
        for (NanochipModuleKind kind : NanochipModuleKind.values()) {
            if (!kind.machineId().equals(id.getPath())) continue;
            return true;
        }
        return false;
    }

    /**
     * ★ 2026-09-19（队长：「我只放了 4 台模块，但是一将模块的主机替换成纳米网络芯片机械方块它就能成型」）：
     * <b>把 GTCEu 失败的那条判据连同它的计数一起打出来</b>。
     *
     * <h3>为什么必须打这张表</h3>
     * GTCEu 的数量类错误只给"最多：%d个"（jar lang {@code gtceu.multiblock.pattern.error.limited.0}，
     * <b>不带方块名</b>），方块名是 GTLsupb 补的、取的是<b>该判据第一个候选</b>；而方块与计数都藏在
     * {@code MultiblockState} 里 ⇒ 不上这张表就只能靠猜（本轮已经猜错过一次：
     * 我按"模块控制器合计 ≤15 台"去解释，可队长只有 4 台 —— 4 ≤ 15，这条解释<b>不成立</b>）。
     *
     * <h3>取什么</h3>
     * 字段可见性已按 gtceu-1.20.1-1.4.4 字节码核对：
     * <ul>
     *   <li>{@code MultiblockState.error} / {@code getGlobalCount()} 都是 public；</li>
     *   <li>{@code SinglePredicateError.predicate}（public final SimplePredicate）与 {@code .type}
     *       ⇒ 能指名道姓说出"是哪条判据"；</li>
     *   <li>{@code SimplePredicate.maxCount / minCount / maxLayerCount / minLayerCount / candidates} 都是 public
     *       ⇒ 上限值与候选方块名都能读出来。</li>
     * </ul>
     * 诊断只读、全程 try/catch：任何异常都吞掉，绝不拖垮结构检查本身。
     */
    static void logFailedPredicate(NanochipAssemblyComplexMachine machine) {
        logFailedPredicate(machine, false);
    }

    /**
     * @param debugOnly ★ 2026-09-21（队长：「…要好久，尤其是主机」+ 日志每分钟 90 条）：
     *                  这一组"失败判据 / 全局计数表"是 {@link #logUnformedAudit()} 的一部分，
     *                  当缺件清单被判为"结论与原因都没变、已折叠"时，它也必须一起降到 debug ——
     *                  否则上面刚省下的量会被这几条 INFO 又打回去（漏一处等于没降）。
     *                  内容一字不变，只换输出级别。
     */
    static void logFailedPredicate(NanochipAssemblyComplexMachine machine, boolean debugOnly) {
        try {
            MultiblockState state = machine.getMultiblockState();
            if (state == null) {
                return;
            }
            PatternError error = state.error;
            if (error == null || error == MultiblockState.UNLOAD_ERROR || error == MultiblockState.UNINIT_ERROR) {
                return;
            }
            Map<SimplePredicate, Integer> counts = state.getGlobalCount();
            if (error instanceof SinglePredicateError) {
                SinglePredicateError single = (SinglePredicateError)error;
                SimplePredicate p = single.predicate;
                if (debugOnly) {
                    GTLModular.LOGGER.debug("[gtlmodular] NAC \u5931\u8d25\u5224\u636e @{}\uff1atype={}\uff080..3 = \u6570\u91cf\u7c7b\uff09\uff1b\u8be5\u5224\u636e\u8ba1\u6570={} / \u4e0a\u9650 max={} min={} maxLayer={} minLayer={}\uff1b\u5019\u9009\u65b9\u5757={}", new Object[]{machine.getPos(), single.type, countOf(counts, p), p.maxCount, p.minCount, p.maxLayerCount, p.minLayerCount, candidateNames(p)});
                } else {
                    GTLModular.LOGGER.info("[gtlmodular] NAC \u5931\u8d25\u5224\u636e @{}\uff1atype={}\uff080..3 = \u6570\u91cf\u7c7b\uff09\uff1b\u8be5\u5224\u636e\u8ba1\u6570={} / \u4e0a\u9650 max={} min={} maxLayer={} minLayer={}\uff1b\u5019\u9009\u65b9\u5757={}", new Object[]{machine.getPos(), single.type, countOf(counts, p), p.maxCount, p.minCount, p.maxLayerCount, p.minLayerCount, candidateNames(p)});
                }
            } else if (debugOnly) {
                GTLModular.LOGGER.debug("[gtlmodular] NAC \u5931\u8d25\u5224\u636e @{}\uff1a\u975e\u5355\u5224\u636e\u9519\u8bef\uff08{}\uff09", (Object)machine.getPos(), (Object)error.getClass().getSimpleName());
            } else {
                GTLModular.LOGGER.info("[gtlmodular] NAC \u5931\u8d25\u5224\u636e @{}\uff1a\u975e\u5355\u5224\u636e\u9519\u8bef\uff08{}\uff09", (Object)machine.getPos(), (Object)error.getClass().getSimpleName());
            }
            if (counts != null && !counts.isEmpty()) {
                if (debugOnly) {
                    GTLModular.LOGGER.debug("[gtlmodular] NAC \u5168\u5c40\u8ba1\u6570\u8868 @{}\uff08{} \u6761\uff09\uff1a", (Object)machine.getPos(), (Object)counts.size());
                } else {
                    GTLModular.LOGGER.info("[gtlmodular] NAC \u5168\u5c40\u8ba1\u6570\u8868 @{}\uff08{} \u6761\uff09\uff1a", (Object)machine.getPos(), (Object)counts.size());
                }
                for (Map.Entry<SimplePredicate, Integer> e : counts.entrySet()) {
                    SimplePredicate p = (SimplePredicate)e.getKey();
                    if (debugOnly) {
                        GTLModular.LOGGER.debug("[gtlmodular]   \u00b7 \u5019\u9009={} \u8ba1\u6570={} max={} min={}", new Object[]{candidateNames(p), e.getValue(), p.maxCount, p.minCount});
                        continue;
                    }
                    GTLModular.LOGGER.info("[gtlmodular]   \u00b7 \u5019\u9009={} \u8ba1\u6570={} max={} min={}", new Object[]{candidateNames(p), e.getValue(), p.maxCount, p.minCount});
                }
            }
        }
        catch (RuntimeException runtimeException) {
            // empty catch block
        }
    }

    /** 全局计数表里该判据的计数（拿不到返回 -1）。 */
    private static int countOf(Map<SimplePredicate, Integer> counts, SimplePredicate p) {
        if (counts == null) {
            return -1;
        }
        Integer v = counts.get(p);
        return v == null ? -1 : v;
    }

    /** 判据候选方块名（最多列 6 个，按注册名；拿不到就写"拿不到"）。 */
    private static String candidateNames(SimplePredicate p) {
        try {
            BlockInfo[] infos = (BlockInfo[])p.candidates.get();
            if (infos == null || infos.length == 0) {
                return "\u5019\u9009\u8868\u4e3a\u7a7a";
            }
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < Math.min(6, infos.length); ++i) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(BuiltInRegistries.BLOCK.getKey(infos[i].getBlockState().getBlock()));
            }
            if (infos.length > 6) {
                sb.append(", \u2026\u5171 ").append(infos.length).append(" \u4e2a");
            }
            return sb.append(']').toString();
        }
        catch (RuntimeException e) {
            return "\u5019\u9009\u8868\u8bfb\u4e0d\u51fa\u6765\uff1a" + e.getClass().getSimpleName();
        }
    }

    /**
     * 把物品内容渲染成「名称×数量 + 名称×数量」的可读串（诊断用）。
     *
     * <p>★ 2026-10-31【M3】：判据从「只认 {@code ItemStack}」改成复用
     * {@link #firstItemStackOf(Object)} / {@link #producedCountOf(Object)} 的多路兜底 ——
     * 否则本包（gtlcore 1.2.3.2）物品产出是 {@code Ingredient}，{@link #logCraftedRecipe} 那行
     * 「主机完成配方 …：输入 {} → 输出 {}」会**恒显示"（无物品）"**，日志等于没有信息
     * （修前它正是本次根因的判据，见 {@code _census/纳米校准_诊断.md} E5）。
     *
     * <p>识别不出时照实写「（未识别物品×N）」而不是静默漏掉 —— 日志里不能出现"少了一截"的假象。
     */
    static String renderItemContents(List<Content> contents) {
        StringBuilder sb = new StringBuilder();
        for (Content c : contents) {
            ItemStack stack = NanochipAssemblyComplexMachine.firstItemStackOf(c.content);
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            if (stack == null || stack.isEmpty()) {
                sb.append("\uff08\u672a\u8bc6\u522b\u7269\u54c1\u00d7").append(NanochipAssemblyComplexMachine.producedCountOf(c.content)).append('\uff09');
                continue;
            }
            sb.append(stack.getHoverName().getString()).append('\u00d7').append(NanochipAssemblyComplexMachine.producedCountOf(c.content));
        }
        return sb.length() == 0 ? "\uff08\u65e0\u7269\u54c1\uff09" : sb.toString();
    }

    static void logModuleLinkChange(NanochipAssemblyComplexMachine machine, String sign, IModularMachineModule<NanochipAssemblyComplexMachine, ?> module, int recomputedCount, int slotCount) {
        MetaMachine meta;
        String string;
        if (module instanceof NanochipModuleMachine) {
            NanochipModuleMachine nanochipModule = (NanochipModuleMachine)module;
            string = nanochipModule.getModuleKind().nameZh();
        } else {
            string = module.getClass().getSimpleName();
        }
        String kind = string;
        MetaMachine linkMachine = module instanceof MetaMachine ? (meta = (MetaMachine)module) : null;
        GTLModular.LOGGER.info("[gtlmodular] NAC @{} \u5df2\u8fde\u63a5\u6a21\u5757 {}\u300c{}\u300d@{}\uff08F \u4f4d\u4e0a\u6210\u5f62\u6a21\u5757 {}/{}\uff0c\u672c\u6b21\u64cd\u4f5c\u540e\u5df2\u8fde\u63a5 {} \u4e2a\uff09", new Object[]{machine.getPos(), sign, kind, linkMachine == null ? "\u672a\u77e5\u5750\u6807" : linkMachine.getPos().toShortString(), recomputedCount, slotCount, machine.getModuleSet().size()});
    }

    /**
     * ★★ 0.3.131【① 让异常带栈】★★ 把一个抓到的异常压成**一行可读**的
     * 「类名 / message / 栈顶 {@value #DESCRIBE_THROWABLE_FRAMES} 帧（类.方法:行号）」。
     *
     * <p><b>为什么需要它</b>：实机诊断行只给了 `error=读状态时抛 NullPointerException`——
     * 类名有了、**抛点没有**。而这一类 NPE 的类名恒为 {@code NullPointerException}，
     * 没有栈就等于没有证据（上一轮就是靠"类名 + 一句猜测"把守卫写成了死代码）。
     * 本方法让栈顶落到**既有那一行**里（不新增日志行），而调用方（
     * {@link #diagnoseLoadPartsNotReady}）的栈顶第 2 帧必然是我们自己的类 ⇒ 一眼可钉死。
     *
     * <p><b>为什么不用 {@code LOGGER.warn(..., e)}</b>：那会新增一条日志行（本题要求"不新增日志行"）；
     * 而 {@code describeThrowable} 是**纯读**（只读异常的 class / message / stackTrace），
     * 零副作用、不可能再抛（{@code getStackTrace()} 对未 fillInStackTrace 的异常返回空数组，
     * 那一支也已显式兜住）。
     */
    static String describeThrowable(Throwable t) {
        StackTraceElement[] frames;
        if (t == null) {
            return "?\uff08\u5f02\u5e38\u5bf9\u8c61\u4e3a null\uff09";
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append(t.getClass().getName());
        String message = t.getMessage();
        if (message != null && !message.isEmpty()) {
            sb.append(": ").append(message);
        }
        try {
            frames = t.getStackTrace();
        }
        catch (RuntimeException e) {
            frames = null;
        }
        sb.append(" @\u6808\u9876[");
        if (frames == null || frames.length == 0) {
            sb.append("\u65e0\u6808\uff08\u8be5\u5f02\u5e38\u672a fillInStackTrace / \u53d6\u6808\u5931\u8d25\uff09");
        } else {
            int printed = 0;
            for (int i = 0; i < frames.length && printed < 4; ++printed, ++i) {
                if (printed > 0) {
                    sb.append(" < ");
                }
                sb.append(frames[i].getClassName()).append('.').append(frames[i].getMethodName()).append(':').append(frames[i].getLineNumber());
            }
        }
        sb.append(']');
        return sb.toString();
    }

    /** 结构失效：打一条"首个不符处 + 该格期望什么"；拿不到逐格原因时退化成摘要并说明原因。 */
    static void logStructureFailureDetail(NanochipAssemblyComplexMachine machine, List<BlockPos> slots) {
        String detail = describeStructureFailure(machine.getMultiblockState());
        if (detail != null) {
            GTLModular.LOGGER.info("[gtlmodular] NAC \u7ed3\u6784\u5931\u6548\u5b9a\u4f4d @{}\uff1a{}", (Object)machine.getPos(), (Object)detail);
            return;
        }
        GTLModular.LOGGER.info("[gtlmodular] NAC \u7ed3\u6784\u5931\u6548\u6458\u8981 @{}\uff1aGTCEu \u672c\u6b21\u6ca1\u6709\u7559\u4e0b\u9010\u683c\u539f\u56e0\uff08MultiblockState.error == null\uff09\u2014\u2014 \u539f\u56e0\uff1aonStructureInvalid() \u4e0d\u5e26\u53c2\u6570\uff0c\u9010\u683c\u539f\u56e0\u53ea\u80fd\u4ece MultiblockState.error \u8bfb\uff0c\u800c GTCEu \u53ea\u5728\u300c\u6570\u91cf\u7c7b / \u88ab\u5378\u8f7d\u300d\u5931\u8d25\u8def\u5f84\u4e0a\u5199\u5b83\uff0c\u5355\u683c\u4e0d\u7b26\u5e38\u5e38\u76f4\u63a5 return false\uff1b\u21d2 \u9000\u5316\u6458\u8981\uff1a\u5f53\u524d\u5df2\u8fde\u63a5\u6a21\u5757 {} \u4e2a\uff1b{}", new Object[]{machine.getPos(), machine.getModuleSet().size(), summarizeModuleSlots(machine, slots)});
    }

    /**
     * 把 GTCEu 结构检查的失败原因翻成一行可读诊断（"首个不符处 + 该格期望什么"）。
     *
     * <p>数据来源（<b>已按 {@code gtceu-1.20.1-1.4.4} 字节码核对，不是猜的</b>）：
     * {@code BlockPattern} 判定失败时会把 {@code SinglePredicateError} / {@code PatternStringError} /
     * 基础 {@code PatternError} 写进 {@code MultiblockState.error}（{@code setError} 顺带把 state 挂到
     * error 上），而 {@code PatternError.getPos()} = 该 state 当时正在检查的那一格、
     * {@code getErrorInfo()} = 期望文案、{@code getCandidates()} = 该格允许放的方块/物品。
     * {@code onStructureInvalid()} 本身不带任何参数，所以这是<b>唯一</b>能拿到逐格原因的途径。
     *
     * <h4>诚实边界（哪些情况它给不出"那一格"）</h4>
     * <ul>
     *   <li>数量类失败（{@code SinglePredicateError.type} 0/1/2/3 = 全局上限 / 全局下限 / 每层上限 /
     *       每层下限，例：NAC 的"能源仓最多 2 个"是<b>全局</b>限制）没有"某一格"的概念，
     *       此时 {@code getPos()} 只是"最后被检查的那一格"，返回值里会标注；</li>
     *   <li>{@code MultiblockState.UNLOAD_ERROR} / {@code UNINIT_ERROR} 这两个静态错误对象没有挂 state
     *       （直接 {@code getPos()} 会 NPE），单独识别并说明"不是真的结构错"；</li>
     *   <li>完全不写 error 的失败路径 ⇒ 返回 {@code null}，调用方改用摘要；</li>
     *   <li>本方法<b>只读</b>，任何异常都被吞掉（返回 null）—— 诊断绝不能拖垮结构检查本身。</li>
     * </ul>
     *
     * @param state 该多方块的 {@code getMultiblockState()}（可为 null）
     * @return 一行诊断；拿不到逐格原因时返回 {@code null}
     */
    static String describeStructureFailure(MultiblockState state) {
        if (state == null) {
            return null;
        }
        PatternError error = state.error;
        if (error == null) {
            return null;
        }
        if (error == MultiblockState.UNLOAD_ERROR) {
            return "GTCEu \u8bb0\u5f55\u7684\u539f\u56e0\u662f\u300c\u65b9\u5757/\u96f6\u4ef6\u88ab\u5378\u8f7d\u300dMultiblockState.UNLOAD_ERROR\u2014\u2014 \u4e0d\u662f\u771f\u7684\u7ed3\u6784\u9519\uff0c\u7b49\u533a\u5757/\u96f6\u4ef6\u91cd\u65b0\u52a0\u8f7d\u540e\u4f1a\u81ea\u52a8\u590d\u67e5";
        }
        if (error == MultiblockState.UNINIT_ERROR) {
            return "GTCEu \u8bb0\u5f55\u7684\u539f\u56e0\u662f\u300c\u7ed3\u6784\u72b6\u6001\u5c1a\u672a\u521d\u59cb\u5316\u300dMultiblockState.UNINIT_ERROR \u2014\u2014 \u4e0d\u662f\u771f\u7684\u7ed3\u6784\u9519";
        }
        try {
            String what;
            BlockPos bad = error.getPos();
            Component expected = error.getErrorInfo();
            int type = -1;
            if (error instanceof SinglePredicateError) {
                SinglePredicateError single = (SinglePredicateError)error;
                type = single.type;
                what = switch (single.type) {
                    case 0 -> "\u8be5\u65b9\u5757/\u4ed3\u4f4d\u7684\u3010\u5168\u5c40\u6570\u91cf\u8d85\u8fc7\u4e0a\u9650\u3011\uff1b\u4f8b\uff1a\u80fd\u6e90\u4ed3\u6700\u591a 2 \u4e2a";
                    case 1 -> "\u8be5\u65b9\u5757/\u4ed3\u4f4d\u7684\u3010\u5168\u5c40\u6570\u91cf\u4e0d\u8db3\u3011\uff1b\u4f8b\uff1a\u81f3\u5c11 1 \u4e2a\u80fd\u6e90\u4ed3";
                    case 2 -> "\u8be5\u65b9\u5757/\u4ed3\u4f4d\u7684\u3010\u6bcf\u5c42\u6570\u91cf\u8d85\u8fc7\u4e0a\u9650\u3011";
                    case 3 -> "\u8be5\u65b9\u5757/\u4ed3\u4f4d\u7684\u3010\u6bcf\u5c42\u6570\u91cf\u4e0d\u8db3\u3011";
                    default -> "\u8be5\u683c\u5224\u5b9a\u4e0d\u7b26\uff1a\u5355\u683c\u6ca1\u901a\u8fc7";
                };
            } else {
                what = "\u8be5\u683c\u5224\u5b9a\u4e0d\u7b26\uff1a\u901a\u7528\u7ed3\u6784\u9519\u8bef";
            }
            boolean countType = type >= 0 && type <= 3;
            return String.format(Locale.ROOT, "%s\uff1bGTCEu \u8bb0\u5f55\u7684\u4f4d\u7f6e %s%s\uff1b\u8be5\u683c\u5b9e\u9645=%s\uff1bGTCEu \u671f\u671b\u6587\u6848=\u300c%s\u300d\uff1b\u5141\u8bb8\u653e\u7684\u65b9\u5757/\u7269\u54c1=%s%s", what, bad == null ? "\u672a\u7ed9\u51fa" : bad.toShortString(), countType ? "\u6570\u91cf\u7c7b\u9519\u8bef\uff1a\u8fd9\u53ea\u662f\u300c\u6700\u540e\u88ab\u68c0\u67e5\u7684\u90a3\u4e00\u683c\u300d\uff0c\u4e0d\u662f\u300c\u67d0\u4e00\u683c\u653e\u9519\u4e86\u300d" : "", bad == null ? "\u672a\u77e5" : describeSlotContent(state.getWorld(), bad), expected == null ? "\u65e0" : expected.getString(), describeCandidates(error), type < 0 ? "" : "\uff1bGTCEu \u9519\u8bef\u7c7b\u578b=" + error.getClass().getSimpleName() + "(type=" + type + ")");
        }
        catch (RuntimeException e) {
            return null;
        }
    }

    /** 逐个 F 位打印"这一格现在站的是什么"（结构失效诊断用；最多 6 格 + 省略数）。 */
    static String summarizeModuleSlots(NanochipAssemblyComplexMachine machine, List<BlockPos> slots) {
        Level level = machine.getLevel();
        if (level == null || slots.isEmpty()) {
            return "\u7f13\u5b58\u91cc\u6ca1\u6709 F \u4f4d\u5750\u6807\uff08\u672c\u673a\u8fd9\u6b21\u4f1a\u8bdd\u8fd8\u6ca1\u6709\u6210\u529f\u5339\u914d\u8fc7\u4e00\u6b21\uff09";
        }
        int formed = 0;
        for (BlockPos pos : slots) {
            NanochipModuleMachine module;
            MetaMachine metaMachine = MetaMachine.getMachine((BlockGetter)level, (BlockPos)pos);
            if (!(metaMachine instanceof NanochipModuleMachine) || !(module = (NanochipModuleMachine)metaMachine).isFormed()) continue;
            ++formed;
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (BlockPos pos : slots) {
            if (shown >= 6) break;
            if (shown > 0) {
                sb.append(" | ");
            }
            sb.append(pos.toShortString()).append('=').append(describeSlotContent(level, pos));
            ++shown;
        }
        if (slots.size() > shown) {
            sb.append(" | \u2026\u53e6 ").append(slots.size() - shown).append(" \u683c");
        }
        return "\u4e0a\u6b21\u6210\u529f\u5339\u914d\u7684 F \u4f4d " + slots.size() + " \u4e2a\uff0c\u5176\u4e2d\u5df2\u6210\u5f62\u6a21\u5757 " + formed + " \u4e2a\uff1a" + String.valueOf(sb);
    }

    /** 一格上现在是什么（模块/NAC 模块机 → 种类；否则方块 id）。 */
    private static String describeSlotContent(Level level, BlockPos pos) {
        MetaMachine machine;
        MetaMachine metaMachine = machine = level == null ? null : MetaMachine.getMachine((BlockGetter)level, (BlockPos)pos);
        if (machine instanceof NanochipModuleMachine) {
            NanochipModuleMachine module = (NanochipModuleMachine)machine;
            return module.getModuleKind().nameZh() + (module.isFormed() ? " \u00b7 \u5df2\u6210\u5f62" : " \u00b7 \u672a\u6210\u5f62");
        }
        ResourceLocation id = level == null ? null : BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        String base = id == null ? "\u53d6\u4e0d\u5230\u65b9\u5757 id" : id.toString();
        return machine == null ? base : base + " \u00b7 " + machine.getClass().getSimpleName();
    }

    /** GTCEu 给出的"允许放什么"（每类只取第一个，最多 6 类；拿不到就说明拿不到）。 */
    private static String describeCandidates(PatternError error) {
        List candidates = error.getCandidates();
        if (candidates == null || candidates.isEmpty()) {
            return "GTCEu \u672a\u7ed9\u51fa\u5019\u9009\u8868";
        }
        StringBuilder sb = new StringBuilder();
        int groups = 0;
        for (List<?> group : (List<List<?>>)candidates) {
            ItemStack first;
            if (group == null || group.isEmpty()) continue;
            if (groups >= 6) {
                sb.append(" / \u2026");
                break;
            }
            if (groups > 0) {
                sb.append(" / ");
            }
            ResourceLocation id = (first = (ItemStack)group.get(0)).isEmpty() ? null : BuiltInRegistries.ITEM.getKey(first.getItem());
            sb.append(id == null ? "\u7a7a" : id.toString());
            if (group.size() > 1) {
                sb.append("(+").append(group.size() - 1).append(')');
            }
            ++groups;
        }
        return sb.length() == 0 ? "GTCEu \u672a\u7ed9\u51fa\u5019\u9009\u8868" : sb.toString();
    }
}
