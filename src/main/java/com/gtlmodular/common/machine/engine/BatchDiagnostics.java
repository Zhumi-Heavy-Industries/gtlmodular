package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtlmodular.GTLModular;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 通用独立线程引擎 · <b>诊断</b>。
 *
 * <p>把原 {@link ThreadedRecipeLogic} 里散落的「为什么没配方」诊断状态与文本全部收在这里：
 * 最近一次真实拒绝原因、逐条扫描明细、缺料探测、产物摘要。调度（{@link BatchFinder}）与
 * IO（{@link BatchIo}）只调用本类，<b>本类不反向依赖它们</b>。
 *
 * <p>行为与原实现逐字一致（同一批字段、同一批日志文本、同一套限频规则），只是搬家。
 */
final class BatchDiagnostics {

    /** 诊断行正文里的默认值（还没试过任何配方时显示这个）。 */
    private static final String NOT_TRIED = "（还没试）";

    private final IRecipeLogicMachine machine;

    private int diagLines;
    private int diagCandidates;
    /**
     * 上一次<b>真实</b>拒绝原因：
     * <ul>
     *   <li>扫描冷却（{@link BatchFinder#IDLE_COOLDOWN_TICKS}）<b>不再覆盖</b>它 —— 旧版每 100 tick
     *       打诊断时恰好常落在冷却里，日志只会写「扫描冷却中（剩 N tick）」，看不到真正原因；</li>
     *   <li>原因变化时把 {@link #diagLines} 归零 ⇒ 同一个原因最多 12 行，换原因重新计数，
     *       长时间空转也不会刷屏、也不会「前 12 行之后永远没日志」。</li>
     * </ul>
     */
    private String diagReject = NOT_TRIED;
    private String diagLastLoggedReason;

    /** 上一次被判「物品/流体不匹配」的那条配方（诊断行据此算出缺的是物品还是流体）。 */
    private GTRecipe diagRecipe;

    /** 上一次失败的附加原因（每 tick 输入 / 配方条件的 {@code reason()} 文本）。 */
    private String diagExtra;

    /** 最后一次真正扫过的配方表 id（跨表时日志里能看到「这次扫的是哪张表」）。 */
    private String diagScannedType;

    /** 本次 {@link BatchFinder#findBatchForType} 是否真的扫了（冷却/跳过时为 false，不参与多表原因汇总）。 */
    private boolean diagScannedThisCall;

    /** 一条候选的失败记录（明细落盘用：原因 + 配方对象，配方对象只用来补「缺什么」）。 */
    private record ScanReject(String reason, GTRecipe recipe) {}

    /**
     * 逐条扫描明细。每张表先按 {@link BatchFinder#SCAN_BUDGET} 条分片轮扫；等整张表被覆盖完且
     * 一条都没跑起来时，把「每条候选为什么失败」一次性打到日志（每张表每次世界加载只打一次，≤40 行）。
     */
    private final Map<GTRecipeType, Map<String, ScanReject>> scanRejectsByType = new IdentityHashMap<>();

    /** 已经打过一次性明细的配方表（避免刷屏）。 */
    private final Set<GTRecipeType> scanDumpDone =
            Collections.newSetFromMap(new IdentityHashMap<>());

    BatchDiagnostics(IRecipeLogicMachine machine) {
        this.machine = machine;
    }

    // ---------------- 供 BatchFinder 驱动的计数/状态 ----------------

    /** 本次单表搜索是否真的扫了。 */
    boolean scannedThisCall() {
        return diagScannedThisCall;
    }

    /** 一轮跨表搜索开始时清标记（每张表各自再置位）。 */
    void forgetScannedThisCall() {
        diagScannedThisCall = false;
    }

    /** 开始扫某张表：记表名、清候选计数与上一条配方/附加原因。 */
    void startTableScan(GTRecipeType type) {
        diagScannedType = String.valueOf(type.registryName);
        diagScannedThisCall = true;
        diagCandidates = 0;
        diagRecipe = null;
        diagExtra = null;
    }

    /** 当前这轮单表搜索试过的候选数。 */
    int candidates() {
        return diagCandidates;
    }

    /** 跨表汇总时把候选数改写成各表之和。 */
    void setCandidates(int total) {
        diagCandidates = total;
    }

    /** 试过一条候选。 */
    void addCandidate() {
        diagCandidates++;
    }

    /** 当前扫的表名（日志用）。 */
    String scannedType() {
        return diagScannedType;
    }

    /**
     * 只清「配方 / 附加原因」，<b>不动</b> {@link #diagReject}。
     *
     * <p>跨表汇总时每张表定稿后调用；旧实现就是两句 {@code diagRecipe = null; diagExtra = null;}
     * —— 换成 {@link #reject} 会连真实原因一起抹掉，行为就不一致了。
     */
    void clearRecipeContext() {
        diagRecipe = null;
        diagExtra = null;
    }

    /** 清掉「最近一次真实原因」的限频计数（{@code pokeScan} 时调用）。 */
    void onPoke() {
        diagLastLoggedReason = null;
        diagLines = 0;
    }

    /** 结构失形/读档：本世界的扫描明细全部作废。 */
    void resetForReload() {
        scanRejectsByType.clear();
        scanDumpDone.clear();
    }

    // ---------------- 拒绝原因 ----------------

    /**
     * 记录一次<b>真实</b>拒绝：{@code reason} 进诊断行，{@code recipe}/{@code extra} 用来回答「缺什么」。
     *
     * <p>为什么单独存一份：扫描冷却（每轮空转 {@link BatchFinder#IDLE_COOLDOWN_TICKS} tick）期间
     * {@code findBatch} 会直接返回，旧版把原因改写成「扫描冷却中」⇒ 日志永远看不到真正的拒绝原因。
     */
    void reject(String reason, GTRecipe recipe, String extra) {
        this.diagReject = reason;
        this.diagRecipe = recipe;
        this.diagExtra = extra;
    }

    /**
     * 扫描期拒绝：既更新「最近一次真实原因」，也把这条候选记进本表的逐条明细
     * （等整张表覆盖完仍无配方时一次性打日志，见 {@link #dumpScanDetail}）。
     *
     * <p>{@code extra} 有值（每 tick 输入 / 配方条件）时，不把配方塞进 {@code diagRecipe}
     * —— 那样诊断行会去做输入探测，答非所问；明细里仍按配方 id 记录。
     */
    void noteScanReject(GTRecipeType type, GTRecipe recipe, String reason, String extra) {
        reject(reason, extra == null ? recipe : null, extra);
        if (type != null && recipe != null && recipe.getId() != null) {
            scanRejectsByType
                    .computeIfAbsent(type, t -> new LinkedHashMap<>())
                    .put(recipe.getId().toString(), new ScanReject(reason, recipe));
        }
    }

    /**
     * 一次性扫描明细：整张表覆盖完还是一条配方都没跑起来时，把每条候选的失败原因打进日志。
     *
     * <p>作用：跨表诊断里每张表正文只保留「最后一条」候选的原因，而你关心的那条
     * （例如 {@code enderium_plasma}）可能排在扫描窗口中间 —— 明细能逐条给出「原料不足 / 缺什么 /
     * 每 tick 输入 / 条件不满足」，不用再猜。每张表每次世界加载只打一次，最多 400 行（2026-11-10 从 40 提到 400：40 行会把玩家关心的那条截掉）。
     */
    void dumpScanDetail(GTRecipeType type) {
        Map<String, ScanReject> rejects = scanRejectsByType.get(type);
        if (type == null || rejects == null || rejects.isEmpty() || !scanDumpDone.add(type)) {
            return;
        }
        GTLModular.LOGGER.info("[gtlmodular] 线程引擎扫描明细 {} @{}（整轮覆盖 {} 条，全部失败；每表仅首次打印）",
                type.registryName, machine.self().getPos(), rejects.size());
        int shown = 0;
        for (Map.Entry<String, ScanReject> entry : rejects.entrySet()) {
            if (shown >= 400) {
                GTLModular.LOGGER.info("[gtlmodular]   …（还有 {} 条未列出）", rejects.size() - 400);
                break;
            }
            shown++;
            ScanReject r = entry.getValue();
            String hint = "";
            if (r.reason() != null
                    && (r.reason().contains("原料不足") || r.reason().contains("物品/流体不匹配"))) {
                String missing = missingHint(r.recipe());
                if (missing != null && !missing.isEmpty()) {
                    hint = "｜缺=" + missing;
                }
            }
            GTLModular.LOGGER.info("[gtlmodular]   {} → {}{}", entry.getKey(), r.reason(), hint);
        }
        // 明细只打一次；清掉本表累积，避免换料后一直占内存。
        scanRejectsByType.remove(type);
    }

    /** 诊断行正文：真实原因 +（输入不匹配时）配方 id + 缺物品还是缺流体。 */
    String detail() {
        StringBuilder sb = new StringBuilder(diagReject == null ? "?" : diagReject);
        if (diagRecipe != null) {
            sb.append("｜配方=").append(diagRecipe.getId());
            String hint = missingHint(diagRecipe);
            if (hint != null && !hint.isEmpty()) {
                sb.append("｜缺=").append(hint);
            }
        } else if (diagExtra != null && !diagExtra.isEmpty()) {
            sb.append("｜").append(diagExtra);
        }
        return sb.toString();
    }

    /**
     * 空转时每 100 tick 打一行「为什么没配方」。
     *
     * <p>每个不同的真实原因最多 12 行；原因不变不重复刷，换原因重新计数；
     * 扫描冷却不再覆盖原因（见 {@link #diagReject} 的注释）。
     */
    void logIdle(boolean crossTable, int engineSize) {
        if (engineSize != 0 || machine.self().getOffsetTimer() % 100L != 0L) {
            return;
        }
        if (!Objects.equals(diagReject, diagLastLoggedReason)) {
            diagLastLoggedReason = diagReject;
            diagLines = 0;
        }
        if (diagLines >= 12) {
            return;
        }
        diagLines++;
        GTLModular.LOGGER.info("[gtlmodular] 线程引擎诊断 {} @{}：成型={} 跨表={} 扫描={} 候选={} 拒绝={}",
                machine.getRecipeType().registryName, machine.self().getPos(),
                machine.self() instanceof IMultiController c ? c.isFormed() : true,
                crossTable, diagScannedType, diagCandidates, detail());
    }

    // ---------------- 缺料探测 ----------------

    /**
     * 「缺什么」探测：用 GTCEu 原版 {@code matchRecipeContents} 再判一次输入，
     * 失败时取下它给的原因（格式 {@code 输入不足: item/fluid}，见 GTCEu 的
     * {@code gtceu.recipe_logic.insufficient_in} 文案）。
     *
     * <p>只在打诊断行时调用（每 100 tick 最多一次，且同一原因最多 12 行），不在找配方的热路径里。
     */
    String missingHint(GTRecipe recipe) {
        // 先给缺哪些物品/流体、各缺多少（id + 数量），看不到缺什么就只能猜。
        String detailed = describeMissingInputs(recipe);
        if (!detailed.isEmpty()) {
            return detailed;
        }
        try {
            GTRecipe.ActionResult result = recipe.matchRecipeContents(
                    IO.IN, machine, recipe.inputs, false);
            if (result.isSuccess()) {
                // 原版能过、gtlcore 通道过不去 ⇒ 多半是 ME 样板/库存总成这类自定义通道
                return "原版匹配通过（卡在 gtlcore 的 ME/样板通道）";
            }
            String text = BatchSupport.reasonText(result);
            return text == null || text.isEmpty() ? "未知（原版匹配失败但不给原因）" : text;
        } catch (Throwable t) {
            return "探测异常:" + t.getClass().getSimpleName();
        }
    }

    /**
     * <b>缺料明细</b> —— 把这条配方缺哪些物品/流体、各缺多少（id×数量）列出来。
     *
     * <p>做法：拿 GTCEu 的 IN handler 链做一次 <b>simulate</b> —— 每个 handler 依次尝试「吃掉」输入，
     * 最后剩下的就是<b>整机（含 ME 样板仓）拿不出来的部分</b>。只在打诊断时调用，不在找配方热路径。
     */
    private String describeMissingInputs(GTRecipe recipe) {
        try {
            List<String> missing = new ArrayList<>();
            collectMissing(recipe, ItemRecipeCapability.CAP, missing);
            collectMissing(recipe, FluidRecipeCapability.CAP, missing);
            if (missing.isEmpty()) {
                return "";
            }
            int limit = 8;
            if (missing.size() <= limit) {
                return String.join(" ", missing);
            }
            return String.join(" ", missing.subList(0, limit)) + " …(共 " + missing.size() + " 种)";
        } catch (Throwable t) {
            return "";
        }
    }

    private <T> void collectMissing(GTRecipe recipe, RecipeCapability<T> cap, List<String> out) {
        List<T> inputs = RecipeHelper.getInputContents(recipe, cap);
        if (inputs.isEmpty()) {
            return;
        }
        List<T> left = new ArrayList<>(inputs);
        List<IRecipeHandler<?>> handlers = machine.getCapabilitiesProxy().get(IO.IN, cap);
        if (handlers != null) {
            for (IRecipeHandler<?> handler : handlers) {
                if (left.isEmpty()) {
                    break;
                }
                @SuppressWarnings("unchecked")
                IRecipeHandler<T> typed = (IRecipeHandler<T>) handler;
                List<T> next = typed.handleRecipe(IO.IN, recipe, left, null, true);
                if (next != null) {
                    left = next;
                }
            }
        }
        for (T inner : left) {
            out.add(describeInput(inner));
        }
    }

    /** 把一个配方输入内容写成「物品:id×N」/「流体:id×NmB」。 */
    private static String describeInput(Object inner) {
        try {
            if (inner instanceof net.minecraft.world.item.crafting.Ingredient ingredient) {
                net.minecraft.world.item.ItemStack[] stacks = ingredient.getItems();
                String id = stacks.length == 0 ? "?"
                        : net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stacks[0].getItem()).toString();
                long count = ingredient instanceof com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient sized
                        ? sized.getAmount()
                        : (stacks.length == 0 ? 1L : stacks[0].getCount());
                return "物品:" + id + "×" + count;
            }
            if (inner instanceof com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient fluid) {
                com.lowdragmc.lowdraglib.side.fluid.FluidStack[] stacks = fluid.getStacks();
                String id = stacks.length == 0 ? "?"
                        : net.minecraftforge.registries.ForgeRegistries.FLUIDS
                        .getKey(stacks[0].getFluid()).toString();
                return "流体:" + id + "×" + fluid.getAmount() + "mB";
            }
            return String.valueOf(inner);
        } catch (Throwable t) {
            return String.valueOf(inner);
        }
    }
}
