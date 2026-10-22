package com.gtlmodular.common.machine.independent.control;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.machine.independent.adapter.IndependentRecipeAdapters;
import com.gtlmodular.common.machine.independent.core.*;
import com.gtlmodular.common.machine.independent.io.BatchContents;
import com.gtlmodular.common.machine.independent.io.IndependentRecipeIo;
import com.gtlmodular.common.machine.independent.persistence.*;
import com.gtlmodular.common.machine.independent.runtime.GtIndependentBatchHost;
import com.gtlmodular.common.machine.independent.runtime.IndependentBatchPolicy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class IndependentMachineController {
    private final RecipeLogic logic;
    private final IndependentLogicAccess access;
    private final IndependentBatchNbt archive = new IndependentBatchNbt();
    private final IndependentBatchOwnership ownership;
    private IndependentBatchSession<IndependentRecipePayload, PreservedBatchData<Tag>> session;
    private IndependentRecipeAdapters.Selection selection;
    private GtIndependentBatchHost host;
    private IndependentBatchPolicy<IndependentRecipePayload> policy;
    private String reason;
    private String loggedReason;
    private final IndependentTickGuard tickGuard = new IndependentTickGuard();
    private boolean detached;

    public IndependentMachineController(RecipeLogic logic) {
        this.logic = logic;
        this.access = (IndependentLogicAccess) logic;
        this.ownership = new IndependentBatchOwnership(logic.getMachine());
    }

    public boolean ownsWork() { return session != null && session.hasOwnedWork(); }

    public String testBatchInfo() {
        if (session == null) return "no-session";
        StringBuilder out = new StringBuilder();
        for (var batch : session.batches()) {
            String wait = host == null ? null : host.runtime().waitReason(batch.payload());
            out.append(batch.payload().recipe().id)
                    .append(" phase=").append(batch.phase())
                    .append(" progress=").append(batch.progress()).append('/').append(batch.duration())
                    .append(" stage=").append(batch.payload().stage())
                    .append(" failure=").append(batch.failure())
                    .append(" wait=").append(wait)
                    .append(';');
        }
        return out.toString();
    }
    public boolean detached() { return detached; }
    public void detach() { detached = true; }
    public UUID ownershipToken() { return ownership.token(); }
    public void placed(ItemStack item) { ownership.placed(item); }

    public boolean resolveOwnership() {
        if (!ownsWork()) return true;
        var result = ownership.resolve();
        if (result == IndependentBatchOwnership.Result.DUPLICATE) {
            // 只有全局账本已证明是副本时才清理；真实所有者仍保留已扣料记录。
            GTLModular.LOGGER.warn("[independent ownership] rejected copied batches at {} {}",
                    logic.getMachine().getLevel().dimension().location(), logic.getMachine().getPos());
            session = new IndependentBatchSession<>();
            host = null;
            policy = null;
            access.gtlmodular$clearUpstreamCache();
            logic.getMachine().markDirty();
        }
        return result == IndependentBatchOwnership.Result.READY;
    }

    public boolean blocksExternal() {
        if (ownsWork()) return true;
        var level = logic.getMachine().getLevel();
        // 最后一笔结清也仍占有本 tick；另一方的 HEAD 可能比本方回调更早执行。
        return level != null && tickGuard.repeated(level.getGameTime()) && tickGuard.cancelRepeated();
    }

    private IndependentRecipeAdapters.Selection selection() {
        if (selection == null) selection = IndependentRecipeAdapters.select(logic, this::activeRecipeCounts);
        return selection;
    }

    private Map<ResourceLocation, Integer> activeRecipeCounts() {
        if (session == null) return Map.of();
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        for (var batch : session.batches()) {
            counts.merge(batch.payload().recipe().id, 1, Integer::sum);
        }
        return counts;
    }

    /** 等待原因变化时写一次服务端日志：玩家面板上看到的那句话，得能在日志里对上号。 */
    private void reportWait(String next) {
        if (next == null) {
            loggedReason = null;
            return;
        }
        if (next.equals(loggedReason)) return;
        loggedReason = next;
        var level = logic.getMachine().getLevel();
        GTLModular.LOGGER.info("[independent] 等待 {} {}: {}",
                level == null ? "?" : level.dimension().location(), logic.getMachine().getPos(), next);
    }

    private String unsupportedReason() {
        if (!selection().supported()) return selection().unsupportedReason();
        if (logic.needFuel()) return "燃料配方需要专用适配";
        try {
            new IndependentRecipeIo(logic.machine).requireSupportedMachine();
            return null;
        } catch (IllegalArgumentException failure) {
            return failure.getMessage();
        }
    }

    private IndependentHandoff handoff(boolean supported) {
        boolean eligible = IndependentMachineRegistry.eligible(logic);
        if (!eligible && !ownsWork()) return IndependentHandoff.decide(false, false, false, false, false, false, false);
        var external = IndependentExternalScheduler.inspect(logic);
        boolean multiple = !(logic instanceof MutableRecipesLogic<?> mutable) || mutable.isMultipleRecipeMode();
        boolean upstreamPending = !ownsWork() && logic.getLastRecipe() != null && logic.getDuration() > 0;
        return IndependentHandoff.decide(ownsWork(), eligible, supported, multiple,
                upstreamPending, external.requested(), external.owned());
    }

    public boolean blockUpstreamStarts() {
        if (tickGuard.executing() || ownsWork()) return true;
        // 已被引擎接管的机器即使本 tick 因输出/电量不足没开批，也不能让父类旧合批先扣料。
        if (IndependentMachineRegistry.eligible(logic) && selection().supported()) return true;
        var level = logic.getMachine().getLevel();
        // 回退后，同一 tick 的合批、缓存重试和 setup 必须沿用放行决定。
        return level != null && tickGuard.repeated(level.getGameTime()) && tickGuard.blocksStarts();
    }

    public void configurationChanged() {
        if (selection != null && selection.supported()) selection.adapter().invalidateCandidates();
        if (ownsWork() && !handoff(unsupportedReason() == null).admit()) requestDrain();
    }

    private void requestDrain() {
        if (ownsWork() && !session.drainRequested()) {
            session.requestDrain();
            logic.getMachine().markDirty();
        }
    }

    public boolean tick() {
        if (detached) return true;
        if (!resolveOwnership()) return true;
        long now = logic.getMachine().getLevel().getGameTime();
        if (tickGuard.repeated(now)) return tickGuard.cancelRepeated();
        if (!ownsWork() && !IndependentMachineRegistry.eligible(logic)) {
            reason = null;
            return false;
        }
        tickGuard.begin(now);
        String unsupported = unsupportedReason();
        var decision = handoff(unsupported == null);
        tickGuard.blockStarts(decision.blockUpstreamStarts());
        if (!decision.runIndependent()) {
            reason = unsupported != null ? unsupported
                    : decision.blockUpstreamStarts() ? "等待上游已扣料批次完成" : "等待其他调度器交还机器";
            reportWait(reason);
            return false;
        }
        tickGuard.take();
        if (ownsWork() && !decision.admit()) requestDrain();
        boolean parentConflict = ownsWork() && logic.getLastRecipe() != null && logic.getDuration() > 0;
        if (!selection().supported() || decision.externalConflict() || parentConflict || logic.needFuel()) {
            reason = parentConflict ? "存档同时含上游未结清配方，保留两份记录并暂停"
                    : decision.externalConflict() ? "检测到另一调度器的所有权，保留本方批次并暂停"
                    : logic.needFuel() ? "燃料配方需要专用适配" : selection().unsupportedReason();
            reportWait(reason);
            return true;
        }
        if (session == null) session = new IndependentBatchSession<>();
        if (host == null) {
            host = new GtIndependentBatchHost(logic, selection().adapter(), () -> session.batches());
            policy = new IndependentBatchPolicy<>(host);
        }
        boolean formed = logic.getMachine() instanceof WorkableElectricMultiblockMachine multiblock
                && multiblock.isFormed() && logic.machine.isRecipeLogicAvailable();
        boolean paused = logic.isSuspend() || !logic.machine.isWorkingEnabled();
        boolean hadWork = ownsWork();
        tickGuard.enter();
        try {
            policy.beginTick();
            var adapter = selection().adapter();
            var result = session.tick(new IndependentSessionControl(decision.admit(), false, paused, formed,
                    Math.max(0, adapter.threads()), adapter.totalBudget()), policy);
            host.finishAdmissions(session::updateUnstartedDuration);
            reason = !formed ? "结构未成形，保留批次" : paused ? "机器已暂停，保留批次" : host.runtime().admissionReason();
            if (!hadWork && !ownsWork()) {
                tickGuard.passThrough();
                return false;
            }
            // 只有真正接下已扣料批次才清缓存；无候选或准备失败仍由上游继续尝试。
            if (!hadWork) access.gtlmodular$clearUpstreamCache();
            if (!logic.isSuspend()) {
                logic.setStatus(ownsWork() ? result.progressed() ? RecipeLogic.Status.WORKING : RecipeLogic.Status.WAITING
                        : RecipeLogic.Status.IDLE);
            }
            if (hadWork && !ownsWork()) {
                ownership.complete();
                access.gtlmodular$clearUpstreamCache();
            }
            return true;
        } catch (RuntimeException failure) {
            // 会话已保存 IO 异常的阶段；已取得所有权的批次不能交回上游。
            reason = failure.getClass().getSimpleName() + ": " + failure.getMessage();
            if (ownsWork()) requestDrain();
            if (hadWork || ownsWork()) return true;
            tickGuard.passThrough();
            return false;
        } finally {
            tickGuard.exit();
            if (hadWork || ownsWork()) logic.getMachine().markDirty();
            logic.updateTickSubscription();
        }
    }

    public void invalidateRuntime() {
        host = null;
        policy = null;
        reason = "生命周期变化，保留批次并等待机器就绪";
        logic.updateTickSubscription();
    }

    public void load(CompoundTag tag) {
        if (!tag.contains(IndependentBatchPersistence.ROOT_KEY)) return;
        // 读档可能早于首个服务端 tick；排空必须由 tick 使用已发布的配置决定。
        session = archive.load(tag);
        ownership.load(tag);
        detached = false;
        host = null;
        policy = null;
    }

    public void save(CompoundTag tag, boolean forDrop) {
        archive.save(tag, session, forDrop);
        if (ownsWork()) ownership.save(tag);
        else if (session != null) tag.remove(IndependentBatchOwnership.TRANSFER);
    }

    public List<Component> recoveryStatus() {
        var text = new ArrayList<>(display());
        if (session != null) {
            session.failedBatches().forEach((token, batch) -> text.add(Component.literal(
                    token + " | " + batch.payload().recipe().id + " | " + batch.phase() + " | "
                            + batch.payload().stage() + " | " + batch.failure())));
            session.retainedRecoveryRecords().forEach((token, raw) -> text.add(Component.literal(
                    token + " | 保留原文（只能人工结清） | " + raw.reason())));
        }
        text.add(Component.literal("retry 需先核实存档余量与实际库存相符；输入/收尾钩子结果不明时只可人工结清。"));
        text.add(Component.literal("settle 仅确认管理员已在外部完成核账补偿，移除该记录，不自动返料或补发产物。"));
        return text;
    }

    public void recover(UUID token, boolean settled, String operator) {
        if (detached || tickGuard.executing() || session == null) throw new IllegalArgumentException("机器尚不可恢复");
        if (!resolveOwnership()) throw new IllegalArgumentException("批次所有权尚未确认；请重新查看 status");
        var batch = session.failedBatches().get(token);
        var raw = session.retainedRecoveryRecords().get(token);
        if (batch == null && raw == null) throw new IllegalArgumentException("令牌已失效；请重新查看 status");
        if (!settled) {
            if (batch == null) throw new IllegalArgumentException("无法解码的原文不能重试，请核账后人工结清");
            batch.payload().stage().requireRetryable();
            new IndependentRecipePayloadCodec().validate(batch);
        }
        GTLModular.LOGGER.warn("[independent recovery] operator={} dimension={} pos={} action={} token={} batch={} retained={}",
                operator, logic.getMachine().getLevel().dimension().location(), logic.getMachine().getPos(),
                settled ? "externally-settled" : "retry-confirmed-remainders", token, batch, raw);
        if (settled) session.acknowledgeSettlement(token);
        else session.retry(token);
        if (!ownsWork()) ownership.complete();
        logic.getMachine().markDirty();
        logic.updateTickSubscription();
    }

    /** 显示层读取机器本体（线圈减免等），不暴露可写状态。 */
    public Object machine() {
        return logic.getMachine();
    }

    /** 一条线程的显示快照：给 Jade 客户端画行、条和产物图标。 */
    public record ThreadView(int label, long euPerTick, int duration, int progress, long parallels,
                             String waitReason, String outputId, long outputCount, ResourceLocation recipeId) {}

    public int limit() {
        try {
            var supported = selection();
            return supported.supported() ? Math.max(1, supported.adapter().threads()) : 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public List<ThreadView> threads() {
        if (session == null) return List.of();
        var out = new ArrayList<ThreadView>();
        int label = 0;
        for (var batch : session.batches()) {
            if (++label > 8) break;
            var recipe = batch.payload().recipe();
            long eu = 0L;
            long parallels = 1L;
            try {
                eu = com.gregtechceu.gtceu.api.recipe.RecipeHelper.getInputEUt(recipe);
                parallels = Math.max(1L, recipe.parallels);
            } catch (RuntimeException ignored) {
            }
            String outputId = null;
            long outputCount = 0L;
            try {
                var items = recipe.outputs.getOrDefault(ItemRecipeCapability.CAP, List.of());
                if (!items.isEmpty()) {
                    ItemStack stack = stackOf(items.get(0).content);
                    if (!stack.isEmpty()) {
                        outputId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        outputCount = BatchContents.amount(ItemRecipeCapability.CAP, items.get(0).content);
                    }
                }
            } catch (RuntimeException ignored) {
            }
            String wait = batch.failure();
            if (wait == null && host != null) wait = host.runtime().waitReason(batch.payload());
            out.add(new ThreadView(label, eu, batch.duration(), batch.progress(), parallels, wait,
                    outputId, outputCount, recipe.id));
        }
        return out;
    }

    /** 产物内容可能是 ItemStack、SizedIngredient 或 gtlcore 的 LongIngredient，统一取一个图标样本。 */
    private static ItemStack stackOf(Object content) {
        if (content instanceof ItemStack stack) return stack;
        if (content instanceof net.minecraft.world.item.crafting.Ingredient ingredient) {
            var items = ingredient.getItems();
            if (items.length > 0) return items[0];
        }
        return ItemStack.EMPTY;
    }

    /** 进度条数据：Jade 客户端用 NAC 的 threads.bar 文案画真进度条。 */
    public record ProgressBar(int progress, int duration) {}

    public List<ProgressBar> progressBars() {
        if (session == null) return List.of();
        var out = new ArrayList<ProgressBar>();
        int shown = 0;
        for (var batch : session.batches()) {
            if (++shown > 8) break;
            out.add(new ProgressBar(batch.progress(), batch.duration()));
        }
        return out;
    }

    /** 工具提示里画物品图标用的数据行：线程号、产物、数量。 */
    public record ProductRow(int thread, ItemStack stack, long amount) {}

    public List<ProductRow> products() {
        if (session == null) return List.of();
        var batches = session.batches();
        var out = new ArrayList<ProductRow>();
        int shown = 0;
        for (var batch : batches) {
            if (++shown > 8) break;
            try {
                var items = batch.payload().recipe().outputs.getOrDefault(ItemRecipeCapability.CAP, List.of());
                if (!items.isEmpty() && items.get(0).content instanceof ItemStack stack && !stack.isEmpty()) {
                    out.add(new ProductRow(shown, stack.copy(),
                            BatchContents.amount(ItemRecipeCapability.CAP, items.get(0).content)));
                }
            } catch (RuntimeException ignored) {
            }
        }
        return out;
    }

    private static Component seconds(int ticks) {
        return Component.literal(java.math.BigDecimal.valueOf(ticks)
                        .divide(java.math.BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString())
                .withStyle(ChatFormatting.AQUA);
    }

    private static Component number(long value) {
        return Component.literal(com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(value))
                .withStyle(ChatFormatting.AQUA);
    }

    private long adapterThreads() {
        try {
            var supported = selection();
            return supported.supported() ? Math.max(1L, supported.adapter().threads()) : 0L;
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    /** 产物名 + 数量（已按并行缩放），比贴一长串配方 id 好读。 */
    private static void appendProduct(net.minecraft.network.chat.MutableComponent line,
                                      com.gregtechceu.gtceu.api.recipe.GTRecipe recipe) {
        try {
            var items = recipe.outputs.getOrDefault(ItemRecipeCapability.CAP, List.of());
            if (!items.isEmpty()) {
                var content = items.get(0);
                long amount = BatchContents.amount(ItemRecipeCapability.CAP, content.content);
                Component name = content.content instanceof ItemStack stack
                        ? stack.getHoverName() : Component.literal("物品");
                line.append(name.copy().withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" ×" + com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(amount))
                                .withStyle(ChatFormatting.GREEN));
                return;
            }
            var fluids = recipe.outputs.getOrDefault(FluidRecipeCapability.CAP, List.of());
            if (!fluids.isEmpty()) {
                var content = fluids.get(0);
                long amount = BatchContents.amount(FluidRecipeCapability.CAP, content.content);
                String name = content.content instanceof com.lowdragmc.lowdraglib.side.fluid.FluidStack stack
                        ? stack.getDisplayName().getString() : "流体";
                line.append(Component.literal(name).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" ×" + com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(amount))
                                .withStyle(ChatFormatting.AQUA));
            }
        } catch (RuntimeException ignored) {
            line.append(Component.literal("产物").withStyle(ChatFormatting.GRAY));
        }
    }

    public List<Component> display() {
        if (!ownsWork() && !IndependentMachineRegistry.eligible(logic)) return List.of();
        // 没接管、又适配不了这族逻辑/仓室时，机器会继续走上游逻辑，不该显示「等待」误导玩家。
        if (!ownsWork() && !selection().supported()) return List.of();
        List<Component> text = new ArrayList<>();
        var batches = session == null ? List.<IndependentSessionSnapshot.Batch<IndependentRecipePayload>>of() : session.batches();
        long pending = batches.stream().filter(batch -> batch.phase() == IndependentSessionSnapshot.Phase.PENDING_SETTLEMENT).count();
        long totalRuns = 0L;
        for (var batch : batches) {
            try {
                totalRuns += Math.max(1L, batch.payload().recipe().parallels);
            } catch (RuntimeException ignored) {
            }
        }
        text.add(Component.translatable("gtlmodular.nanochip.threads.status",
                number(adapterThreads()), number(batches.size())));
        if (!batches.isEmpty()) {
            text.add(Component.translatable("gtlmodular.nanochip.threads.runs", number(totalRuns)));
            java.util.Set<Object> distinct = new java.util.HashSet<>();
            for (var batch : batches) distinct.add(batch.payload().recipe().id);
            text.add(Component.translatable("gtlmodular.nanochip.jade.distinct_recipes",
                    number(batches.size()), number(distinct.size())));
            text.add(Component.translatable("gtlmodular.nanochip.threads.machine_equiv",
                    number(batches.size()), number(batches.size()), number(totalRuns)));
        }
        if (session != null && session.retainedRecords().size() > 0) {
            text.add(Component.translatable("gtlmodular.independent.summary", batches.size() - pending, pending,
                    session == null ? 0 : session.retainedRecords().size()));
        }
        if (session != null && session.drainRequested()) text.add(Component.translatable("gtlmodular.independent.draining"));
        if (reason != null) text.add(Component.translatable("gtlmodular.independent.wait", reason));
        if (session != null) for (var retained : session.retainedRecords()) {
            text.add(Component.translatable("gtlmodular.independent.wait", retained.reason()));
            if (text.size() >= 8) break;
        }
        int shown = 0;
        for (var batch : batches) {
            if (++shown > 8) break;
            String wait = batch.failure();
            if (wait == null && host != null) wait = host.runtime().waitReason(batch.payload());
            var recipe = batch.payload().recipe();
            long euPerTick = 0L;
            long parallels = 1L;
            try {
                euPerTick = com.gregtechceu.gtceu.api.recipe.RecipeHelper.getInputEUt(recipe);
                parallels = Math.max(1L, recipe.parallels);
            } catch (RuntimeException ignored) {
            }
            text.add(Component.translatable("gtlmodular.nanochip.thread_details.row",
                    number(shown), number(euPerTick), number(parallels)));
            text.add(Component.translatable("gtlmodular.nanochip.thread_details.timing",
                    number(batch.duration()), seconds(batch.duration()),
                    number(batch.progress()), number(batch.duration())));
            text.add(wait == null
                    ? Component.translatable("gtlmodular.nanochip.thread_details.working")
                    : Component.translatable("gtlmodular.nanochip.thread_details.waiting", Component.literal(wait)));
        }
        if (batches.size() > 8) text.add(Component.translatable("gtlmodular.independent.more", batches.size() - 8));
        if (host != null) {
            String stats = host.statsSummary();
            if (!stats.isEmpty()) text.add(Component.literal(stats));
        }
        return List.copyOf(text);
    }
}
